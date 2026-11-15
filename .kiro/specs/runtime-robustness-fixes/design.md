# Runtime Robustness Fixes Bugfix Design

## Overview

This patch (1.0.2) bundles three independent runtime-robustness defects under one umbrella bug condition: ESuite mishandles a bounded-resource edge case and produces corrupted output, memory/disk divergence, or main-thread blocking. Each fix is intended to be minimal, reversible, and layered on top of the workspace-persistence-hardening change (durable write-through via `persistDurable()`, a retained debounce collector for capture history, and a versioned/migrating `load()`) without regressing it.

- **Defect 1 — UTF-8 response-boundary corruption** (`transport/ApiEngine.kt`, `onResponse`). The 256 KiB (`Limits.RESPONSE_BYTES`) byte cap can fall inside a multi-byte character. The textual path decodes the truncated bytes with `String(bytes, charset)`, which turns the straddling trailing bytes into a replacement/garbage character that was never in the real response. Fix: on the `textual && truncated` path only, decode with a `CharsetDecoder` set to `CodingErrorAction.IGNORE`, dropping the incomplete trailing sequence; the non-truncated and non-textual (base64) paths stay byte-for-byte identical, and the `truncated` flag is still set.

- **Defect 2 — in-memory vs on-disk history divergence** (`storage/WorkspaceVault.kt`, `AppViewModel.kt`). `save()` halves `exchanges` until the encoded workspace fits `VAULT_BYTES` and writes the pruned copy, but the in-memory `AppViewModel` workspace keeps the full list, so the UI shows capture history that was never persisted and vanishes on restart. Fix: `save()` returns the actually-persisted (bounded) `Workspace`, and the AppViewModel save path reconciles the in-memory `exchanges` to match what was written — trimming only `exchanges`, never durable data, applied back on the main thread, composing with (not fighting) the debounce collector and the hardening write-through.

- **Defect 3 — large imports block the UI thread** (`AppViewModel.kt` `importOpenApi`/`importHar`, `core/WorkspaceBudget.kt`, `core/ApiImports.kt`). OpenAPI/HAR parsing (`ApiImports.openApi`/`har`) and durable-budget validation (`WorkspaceBudget.requireDurableCapacity`, a full `Json.encodeToString`) run synchronously inside `mutable.update { }` on the main thread and can ANR on large input. Fix: run parse + budget validation off the main thread, then apply a version-safe `mutable.update { }` that re-reads the current workspace so concurrent captures are not lost and re-checks the count/durable budgets at merge time, then persists through the durable write-through path.

No change is intended to scope enforcement, the HTTPS-only runtime, redirect/retry suppression, encryption or fail-closed semantics, the resource budgets, or the no-network / no-telemetry contract.

## Glossary

- **Bug_Condition (C)**: A bounded-resource edge case where ESuite corrupts output, diverges memory from disk, or blocks the main thread — a textual response truncated mid multi-byte character, a `save()` that prunes `exchanges` while memory keeps the full list, or an OpenAPI/HAR import whose parse and durable-budget encode run on the main thread.
- **Property (P)**: The desired behavior under C — truncated text decodes to valid characters with the incomplete trailing sequence dropped; in-memory `exchanges` equal what was persisted; import parsing and budget validation run off the main thread and apply a version-safe state update.
- **Preservation**: Behavior for inputs where C does NOT hold — non-truncated or non-textual responses, saves that fit the budget, and non-import flows — must be observably identical to the current code.
- **`RESPONSE_BYTES` cap / `StoredBody`**: `Limits.RESPONSE_BYTES` (256 KiB) bounds a captured body; `StoredBody(value, encoding, truncated)` stores the decoded text (`utf8`) or base64 (`base64`) plus the truncation flag.
- **Lenient decode**: A `java.nio.charset.CharsetDecoder` configured with `onMalformedInput(CodingErrorAction.IGNORE)` and `onUnmappableCharacter(CodingErrorAction.IGNORE)`, decoding a `ByteBuffer` so an incomplete trailing multi-byte sequence is dropped rather than rendered as U+FFFD.
- **Exchange-halving prune**: `WorkspaceVault.save()`'s `while` loop that repeatedly does `exchanges.take(size / 2)` until the encoded workspace fits `VAULT_BYTES`.
- **Reconcile**: An in-memory, main-thread trim that removes exactly the `exchanges` the prune dropped on disk (matched by id), leaving durable lists and any newer captures untouched.
- **`persistDurable()` / durable write-through**: The workspace-persistence-hardening helper that writes durable mutations immediately on `storageDispatcher`, reusing `saveNow()`-style success/failure messaging and the "previous file retained" semantics.
- **Debounce collector**: The hardening-retained `state.map { it.workspace }.distinctUntilChanged().drop(1).debounce(500).collect { … vault.save(…) }` that writes capture history.
- **storageDispatcher**: `Dispatchers.IO.limitedParallelism(1)` in `AppViewModel` — the single-threaded confinement that serializes all vault I/O.
- **`requireDurableCapacity` / durable budget**: `WorkspaceBudget.requireDurableCapacity(workspace)` encodes `workspace.copy(exchanges = emptyList())` and requires it `<= VAULT_BYTES / 2` (16 MiB); it ignores `exchanges`.
- **Version-safe update**: A `mutable.update { current -> … }` that reads the *current* workspace inside the lambda (not a stale snapshot), so concurrent captures and durable edits are not clobbered.

## Bug Details

### Bug Condition

The bug manifests in three ways under one condition: a bounded-resource edge case produces corrupted output, memory/disk divergence, or main-thread blocking.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input — one of
           ResponseCapture(bytes, byteCount, textual, charset)
         | SaveEvent(workspace)                       // workspace = in-memory AppViewModel state
         | ImportEvent(kind, inputText)               // kind in { OPENAPI, HAR }
  OUTPUT: boolean

  // Defect 1: a textual response truncated at the byte cap, inside a multi-byte character
  IF input IS ResponseCapture THEN
    RETURN input.textual == true
           AND input.byteCount > Limits.RESPONSE_BYTES
           AND splitsMultiByteChar(input.bytes, Limits.RESPONSE_BYTES, input.charset)
           // i.e. String(bytes[0..cap], charset) ends in a replacement/garbage char
  END IF

  // Defect 2: save prunes exchanges to fit, but the in-memory list is left unpruned
  IF input IS SaveEvent THEN
    bounded := pruneExchangesToFit(input.workspace, Limits.VAULT_BYTES)   // the save() while-loop
    RETURN bounded.exchanges.size < input.workspace.exchanges.size
           AND inMemoryExchanges(input.workspace) != bounded.exchanges     // memory != disk
  END IF

  // Defect 3: OpenAPI/HAR parse + durable-budget encode run on the main thread
  IF input IS ImportEvent THEN
    RETURN input.kind IN { OPENAPI, HAR }
           AND runsOnMainThread(parse(input.inputText))
           AND runsOnMainThread(requireDurableCapacity(mergeCandidate(input)))
  END IF

  RETURN false
END FUNCTION
```

The desired behavior under C is formalized as:
```
FUNCTION expectedBehavior(input, result)
  IF input IS ResponseCapture THEN
    RETURN isValidText(result.body, input.charset)   // no trailing replacement/garbage char
           AND result.truncated == true              // flag still set
  END IF
  IF input IS SaveEvent THEN
    RETURN persistedExchanges(result) == inMemoryExchanges(result)   // memory == disk
           AND durableLists(result) UNCHANGED
  END IF
  IF input IS ImportEvent THEN
    RETURN NOT ranOnMainThread(result.parseAndBudgetCheck)
           AND result.appliedVersionSafeMerge == true
           AND result.countLimit <= 100 AND result.durableBytes <= Limits.VAULT_BYTES / 2
  END IF
END FUNCTION
```

### Examples

- **Defect 1 — JSON ending in an emoji.** A response whose byte 262144 (the cap) lands in the middle of a 4-byte `😀` (or any multi-byte char). Expected: stored text ends at the last complete character, `truncated = true`. Actual: `String(bytes, UTF_8)` appends U+FFFD / garbage for the split sequence.
- **Defect 1 — non-UTF-8 charset.** `Content-Type: text/html; charset=Shift_JIS` truncated mid double-byte character. Expected: incomplete trailing byte dropped. Actual: replacement char injected.
- **Defect 1 — edge case (preserved).** An ASCII body of `"a".repeat(RESPONSE_BYTES + 10)` (existing `ApiEngineTest.explicitCookieWinsAndResponseCapsAreHonest`): every byte is a complete character, so the lenient decode drops nothing and the stored length is still exactly `RESPONSE_BYTES` — unchanged.
- **Defect 2 — history over 32 MiB.** Enough captures to push the encoded workspace past `VAULT_BYTES`; `save()` halves `exchanges` and writes the prefix. Expected: the Traffic list shows exactly the persisted prefix. Actual: the list shows the full in-memory list, including entries that vanish on the next `load()`.
- **Defect 2 — edge case (preserved).** Exchanges that fit the budget: no prune, `save()` returns the same list, reconcile is a no-op, nothing in memory changes.
- **Defect 3 — 2 MiB OpenAPI spec.** Importing a near-limit spec parses JSON and encodes the durable workspace on the main thread; the UI freezes and can ANR. Expected: parse + budget validation run off-thread, UI stays responsive, drafts merged version-safely. Actual: main thread blocked for the duration.
- **Defect 3 — edge case (preserved).** An over-limit or out-of-scope import still fails with the same message (`"Collection limit is 100 requests. Remove saved items before importing more."`, `"Saved collections/notes exceed the 16 MiB durable-data budget. Remove saved items before adding more."`, `"Import failed"` / `"HAR import failed"`) and runs no network request.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- A textual response that fits within the 256 KiB cap (not truncated) decodes and stores its full body exactly as today via `String(bytes, charset)` — the lenient decoder is gated on `truncated`.
- A non-textual response is still stored as base64 and still enforces the `Limits.RESPONSE_BYTES` cap; the `truncated` flag is still set whenever `buffer.size > Limits.RESPONSE_BYTES`.
- `save()`'s on-disk behavior is byte-identical: it still writes `CURRENT_VERSION`, still halves `exchanges` to fit `VAULT_BYTES`, and still uses `AtomicFile` `startWrite`/`finishWrite`/`failWrite`. The `Limits.HISTORY` count cap (enforced in `record()` and `load()`) and the 32 MiB `VAULT_BYTES` behavior are unchanged.
- Capture-history writes stay on the debounce collector; durable mutations keep using `persistDurable()`; encryption, fail-closed load, missing-key behavior, and the versioned migration from workspace-persistence-hardening are untouched.
- Every import of any size is still bounded to at most 100 saved requests and the 16 MiB durable budget, still rejects out-of-budget / over-limit / out-of-scope imports with the same messages, and executes no network request. Scope enforcement, HTTPS-only runtime, and the no-telemetry contract are unchanged. `importCurl` (single draft, no large parse or budget encode) is out of scope and unchanged.

**Scope:**
All inputs that do NOT trigger the bug condition are completely unaffected:
- Responses that fit the cap, and all non-textual (base64) responses.
- Saves whose encoded workspace already fits `VAULT_BYTES` (no prune → no reconcile).
- Non-import flows and `importCurl`.

The expected correct behavior under the bug condition is defined in the Correctness Properties section (Properties 1–3); Property 4 captures preservation.

## Hypothesized Root Cause

Based on the code, the causes are concrete rather than hypothetical:

1. **Direct string construction over truncated bytes (Defect 1).** In `ApiEngine.onResponse`, `StoredBody(if (textual) String(bytes, media?.charset(UTF_8) ?: UTF_8) else base64, …)` decodes the whole truncated array in one shot. `String(ByteArray, Charset)` substitutes U+FFFD for malformed input, so a multi-byte character split by the byte cap becomes a replacement/garbage character. The decode has no notion that the tail was cut by our own cap.

2. **`save()` prunes a local copy and discards it (Defect 2).** `WorkspaceVault.save()` mutates a local `bounded` via the exchange-halving loop, seals and writes `bounded`, and returns `Unit`. The caller (`AppViewModel` debounce collector / `saveNow()` / `persistDurable()`) passed `state.value.workspace` and never learns that `exchanges` were trimmed, so `state.workspace.exchanges` stays longer than what is on disk until the next `load()`.

3. **Synchronous parse and budget encode on the main thread (Defect 3).** `importOpenApi`/`importHar` call `ApiImports.openApi`/`har` (which run `Json.parseToJsonElement` over up to 2 MiB and build up to 100/200 objects) directly in the `runCatching` body on the calling (main) thread, then `mergeSaved` runs `WorkspaceBudget.requireDurableCapacity(next)` — a full `Json.encodeToString` of the durable workspace — inside `mutable.update { }`, also on the main thread. Neither hops off the main thread, so large input blocks the UI.

## Correctness Properties

Property 1: Bug Condition - Truncated Text Decodes Leniently

_For any_ input where the bug condition holds for a textual response truncated inside a multi-byte character (`isBugCondition` returns true for a `ResponseCapture`), the fixed `onResponse` SHALL decode the capped bytes with a lenient `CharsetDecoder` (`CodingErrorAction.IGNORE`) so the incomplete trailing multi-byte sequence is dropped and the stored `StoredBody.value` is valid text up to the last complete character, while still setting `truncated = true`. The trade-off is explicit: dropping the partial trailing character is correct for a capture that WE truncated at the byte cap (those tail bytes are an artifact of the cap, not real content), whereas emitting U+FFFD would inject a character the server never sent.

**PBT candidate:** for any string over any supported charset, encoding it and truncating at any byte offset, the lenient decode yields valid text with no trailing replacement character and equals a prefix of the original up to a complete-character boundary.

**Validates: Requirements 2.1, 2.2**

Property 2: Bug Condition - In-Memory History Equals Persisted History

_For any_ input where the bug condition holds for a save that prunes `exchanges` (`isBugCondition` returns true for a `SaveEvent`), the fixed save path SHALL return the actually-persisted bounded `Workspace` and reconcile the in-memory `exchanges` to match it exactly (removing only the pruned entries, by id), leaving `projects`, `findings`, `savedRequests`, and `identityConfigs` untouched, so that after a save the in-memory capture history equals what is on disk without altering the `Limits.HISTORY` count cap or the 32 MiB `VAULT_BYTES` behavior.

**PBT candidate:** for any workspace whose encoded size exceeds `VAULT_BYTES`, after `save()` + reconcile, the in-memory `exchanges` equal the persisted `exchanges`, the in-memory `exchanges` are a subset (by id) of the pre-save list, and all durable lists are unchanged.

**Validates: Requirements 2.3, 2.4**

Property 3: Bug Condition - Imports Parse Off-Thread with a Version-Safe Merge

_For any_ input where the bug condition holds for an OpenAPI/HAR import (`isBugCondition` returns true for an `ImportEvent`), the fixed `importOpenApi`/`importHar` SHALL perform `ApiImports` parsing and `WorkspaceBudget.requireDurableCapacity` validation off the main thread, then apply a version-safe `mutable.update { }` that re-reads the current workspace (preserving concurrent captures) and re-checks the 100-request and 16 MiB budgets at merge time, and SHALL persist the merged durable state through the write-through path — without executing any network request and without blocking the UI.

**Validates: Requirements 2.5, 2.6**

Property 4: Preservation - Non-Bug Inputs Unchanged

_For any_ input where the bug condition does NOT hold — a textual response within the cap, a non-textual (base64) response, a save whose workspace already fits `VAULT_BYTES`, a non-import flow, or `importCurl` — the fixed code SHALL produce the same observable result as the original code: full-body decode via `String(bytes, charset)`; base64 storage with the cap and `truncated` flag enforced; no prune and therefore no reconcile; and all import budgets, error messages, scope/HTTPS enforcement, encryption/fail-closed semantics, and the no-network / no-telemetry contract preserved, including the workspace-persistence-hardening write-through and migration.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Required

Three independent, surgical changes across four files. Each is reversible on its own.

**File**: `app/src/main/java/dev/esuite/app/transport/ApiEngine.kt`

**Function**: `execute` → `onResponse` body-decoding step.

**Specific Changes (Defect 1)**:
1. **Gate a lenient decode on truncation.** Keep the current computation of `bytes`, `truncated`, `media`, and `textual`. Replace the textual branch of `StoredBody(...)` so that:
   - when `textual && !truncated`: decode exactly as today — `String(bytes, charset)` (byte-for-byte preservation of the non-truncated path);
   - when `textual && truncated`: decode `ByteBuffer.wrap(bytes)` with a `CharsetDecoder` obtained from the resolved charset (`media?.charset(Charsets.UTF_8) ?: Charsets.UTF_8`) and configured `onMalformedInput(CodingErrorAction.IGNORE)` + `onUnmappableCharacter(CodingErrorAction.IGNORE)`, then `.toString()` the resulting `CharBuffer`. This drops the incomplete trailing multi-byte sequence at the cap.
2. **Leave everything else untouched.** The non-textual branch still emits `Base64.getEncoder().encodeToString(bytes)` with encoding `"base64"`; `truncated` is still passed through unchanged; the read loop, cap arithmetic, and `Exchange` construction are not modified.
3. **Trade-off note (in code comment).** Document why IGNORE (drop the partial tail) is chosen over the JVM default REPLACE (U+FFFD): the tail bytes exist only because we capped the stream, so dropping them yields faithful text up to the truncation point rather than a fake character.

**File**: `app/src/main/java/dev/esuite/app/storage/WorkspaceVault.kt`

**Function**: `save()`; optional extracted pure helper `boundForPersist()`.

**Specific Changes (Defect 2)**:
1. **Return the persisted workspace.** Change `fun save(workspace: Workspace)` to `fun save(workspace: Workspace): Workspace`. The body keeps the exact exchange-halving loop, seal, and `AtomicFile` write; after a successful `finishWrite`, return `bounded` (the workspace actually encoded and written). On the `failWrite`/throw path, nothing is returned (the exception propagates as today).
2. **Extract the prune as a pure helper (for JVM testing).** Optionally move the `while` loop into `internal fun boundForPersist(workspace: Workspace): Workspace` that returns the pruned copy without any Keystore or file I/O, and have `save()` call it. This isolates the prune logic so the "persisted == in-memory" property is JVM-testable without the Android Keystore.
3. **No semantic change to disk behavior.** `VAULT_BYTES`, the halving strategy, `CURRENT_VERSION` write, and `failWrite` retention are unchanged. `load()` and `erase()` are untouched.

**File**: `app/src/main/java/dev/esuite/app/AppViewModel.kt`

**Function**: the save call-sites (debounce collector, `saveNow()`, and the hardening `persistDurable()`); a new private `reconcileExchanges()` helper; `importOpenApi`/`importHar`/`mergeSaved`.

**Specific Changes (Defect 2)**:
1. **Capture the persisted result at every save call-site.** Wherever the code calls `vault.save(state.value.workspace)` (the debounce collector, `saveNow()`, and `persistDurable()`), capture the returned `Workspace` and route it through one reconciliation helper. Centralize so all three paths behave identically.
2. **Reconcile only `exchanges`, by id, on the main thread.** Add `private fun reconcileExchanges(persisted: Workspace)` that, when `persisted.exchanges.size < <snapshot>.exchanges.size`, computes the dropped ids and applies `mutable.update { it.copy(workspace = it.workspace.copy(exchanges = it.workspace.exchanges.filterNot { e -> e.id in droppedIds })) }`. Filtering by id (rather than replacing the list) removes exactly what the prune dropped while preserving any captures recorded after the save snapshot and never touching durable lists.
3. **Avoid a save/update loop.** Only call `mutable.update` when the prune actually dropped entries (`droppedIds` non-empty); a no-prune save returns the same list and performs no state change. When a prune does occur, the resulting state change retriggers the debounce collector once; that follow-up save encodes the now-smaller workspace, which fits, so it prunes nothing, reconciles nothing, and the sequence converges (each prune strictly reduces the exchange count). The reconcile shares `storageDispatcher` for the write and applies state back on the main dispatcher, so it serializes with — rather than races — the debounce collector and `persistDurable()`.

**Specific Changes (Defect 3)**:
4. **Move import parse + budget validation off the main thread.** Rewrite `importOpenApi`/`importHar` to `viewModelScope.launch { runCatching { … }.onSuccess { … }.onFailure { … } }`, where the heavy work runs in `withContext(Dispatchers.Default)` (CPU-bound parse + encode; `storageDispatcher` is also acceptable but `Default` avoids contending with vault I/O): parse via `ApiImports.openApi(text, p)` / `ApiImports.har(text, p)` and run the authoritative `WorkspaceBudget.requireDurableCapacity` against a candidate built from a workspace snapshot. This relocates both the parse and the full `encodeToString` off the UI thread.
5. **Apply a version-safe merge on the main thread.** Replace `mergeSaved`'s stale behavior with a version-safe apply that re-reads the current workspace inside `mutable.update { current -> … }`: re-check `requests.size + current.savedRequests.size <= 100` (cheap integer guard) with the exact message `"Collection limit is 100 requests. Remove saved items before importing more."`, build `next = current.copy(savedRequests = requests + current.savedRequests)`, re-run `WorkspaceBudget.requireDurableCapacity(next)` (the atomic guard; note it ignores `exchanges`, so concurrent captures never trigger a re-encode and are preserved because `current` is re-read), and set `workspace = next`. Preserve the exact failure messages (`requireDurableCapacity`'s existing text, and `"Import failed"` / `"HAR import failed"` from the `onFailure` handlers).
6. **Persist through the durable write-through path.** On a successful merge, imports are durable, so trigger `persistDurable()` (the workspace-persistence-hardening helper) after the merge instead of relying only on the debounce. This keeps imports from sitting in the debounce window and composes with the hardening lifecycle flush.
7. **Preserve the no-network guarantee.** `ApiImports` and `requireDurableCapacity` perform no I/O; running them under `withContext(Dispatchers.Default)` executes only CPU work, so the "no requests executed during import" contract is unchanged.

**File**: `app/src/main/java/dev/esuite/app/core/ApiImports.kt` and `app/src/main/java/dev/esuite/app/core/WorkspaceBudget.kt`

**Function**: `ApiImports.openApi` / `ApiImports.har`; `WorkspaceBudget.requireDurableCapacity`.

**Specific Changes (Defect 3)**:
1. **No behavioral change; invoked off-thread by the caller.** These are already pure, synchronous, allocation-only functions with no Android or network dependency. The fix is entirely in how `AppViewModel` schedules them (off the main thread). They are listed because they are the heavy work being relocated; their logic, limits (2 MiB input, ≤100/≤200 entries, scope checks), and messages stay exactly as they are. Keeping them unchanged maximizes reversibility of Defect 3.

## Testing Strategy

### Validation Approach

Two phases: first surface counterexamples on the UNFIXED code (a response truncated mid multi-byte char showing a replacement char; a pruned save leaving memory longer than disk; an import blocking the main thread), then verify the fixes decode leniently, keep memory equal to disk, and parse off-thread while preserving every non-bug behavior. Defect 1 is fully JVM-testable (`ApiEngine` has no Android dependency and `ApiEngineTest` already drives it with `MockWebServer` on the JVM). Defect 2's prune/reconcile logic is JVM-testable as pure functions; the full Keystore seal/write round-trip is instrumented (extending `DeviceBoundaryTest`). Defect 3's pure merge/budget invariants are JVM-testable; the off-thread scheduling and `AndroidViewModel` behavior are instrumented/integration.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate all three defects BEFORE implementing the fix, and confirm the root-cause analysis. If a test passes unexpectedly on unfixed code, re-hypothesize.

**Test Plan**: Drive each defect at its real boundary and assert the post-fix expectation, observing failure on unfixed code.

**Test Cases**:
1. **UTF-8 split at the cap (unfixed)**: serve a textual body whose byte at `RESPONSE_BYTES` lands inside a multi-byte character; assert the stored text ends on a complete character with no U+FFFD — fails on unfixed code (replacement char present).
2. **Pruned save divergence (unfixed)**: build a workspace large enough to force the exchange-halving prune, `save()`, then assert in-memory `exchanges` equal persisted `exchanges` — fails on unfixed code (memory longer than disk).
3. **Import blocks main thread (unfixed)**: import a near-2 MiB OpenAPI/HAR input and assert parse + budget validation do not run on the main thread — fails on unfixed code (runs inline on the caller/main thread).
4. **Edge cases (preserved)**: ASCII body truncated at the cap keeps length `RESPONSE_BYTES`; a save that fits the budget performs no prune; an over-limit import still fails with the exact message — all should already hold (preservation, not the bug).

**Expected Counterexamples**:
- A replacement/garbage character at the tail of a truncated textual body.
- A Traffic list showing capture entries that are absent from disk after a pruning save.
- A UI freeze / main-thread execution during a large import.
- Possible causes: one-shot `String(bytes, charset)` over truncated bytes; `save()` discarding the pruned copy; synchronous parse + `encodeToString` on the main thread.

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  IF input IS ResponseCapture THEN
    result := onResponse(input)
    ASSERT isValidText(result.body, input.charset) AND result.truncated == true
  ELSE IF input IS SaveEvent THEN
    persisted := vault.save(input.workspace); reconcileExchanges(persisted)
    ASSERT inMemoryExchanges() == persisted.exchanges AND durableLists UNCHANGED
  ELSE IF input IS ImportEvent THEN
    result := import(input)
    ASSERT NOT ranOnMainThread(result.parseAndBudgetCheck)
           AND result.merged AND result.countLimit <= 100 AND result.durableBytes <= VAULT_BYTES/2
  END IF
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalBehavior(input) == fixedBehavior(input)
  // textual-within-cap body decodes via String(bytes, charset) unchanged
  // non-textual body stored base64 with cap + truncated flag unchanged
  // save that fits VAULT_BYTES performs no prune and no reconcile
  // import budgets/messages/scope/no-network unchanged; importCurl unchanged
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation because it generates many inputs across the domain, catches edge cases manual tests miss, and gives strong guarantees that non-bug behavior is unchanged. The lenient-decode and persisted-equals-in-memory invariants are especially suited to generated inputs.

**Test Plan**: Observe current behavior on unfixed code for within-cap and base64 responses, fitting saves, and `importCurl`, then capture it in property/unit tests and confirm it still holds after the fix. Reuse `ApiEngineTest`'s `MockWebServer` pattern for responses and `DeviceBoundaryTest`'s Keystore round-trip for persistence.

**Test Cases**:
1. **Within-cap and base64 responses unchanged**: a textual body under the cap round-trips identically; a binary body stays base64 with the cap and `truncated` flag enforced.
2. **Fitting save unchanged**: a workspace that fits `VAULT_BYTES` produces no prune and leaves in-memory `exchanges` identical.
3. **Import guards unchanged**: over-limit, over-budget, and out-of-scope imports fail with the exact same messages and run no network request; `importCurl` is unaffected.

### Unit Tests

- **Defect 1 (JVM)**: lenient-decode helper over truncated multi-byte UTF-8 and non-UTF-8 (e.g. Shift_JIS) buffers drops the partial tail and never yields U+FFFD; non-truncated input decodes identically to `String(bytes, charset)`. Driven end-to-end through `ApiEngine` + `MockWebServer` in `ApiEngineTest`, including the existing ASCII-cap length assertion.
- **Defect 2 (JVM)**: `boundForPersist()` returns a prefix-pruned workspace; the `reconcileExchanges` id-filter removes exactly the dropped entries, preserves newer captures, and leaves durable lists untouched; a fitting workspace yields an empty dropped set (no state change).
- **Defect 3 (JVM)**: the version-safe merge enforces the 100-count and 16 MiB budgets with the exact messages against a re-read workspace, and preserves concurrent `exchanges`.

### Property-Based Tests

- **Defect 1 (JVM, strong PBT candidate)**: for any string over any supported charset, truncating its encoded bytes at any offset and running the lenient decode yields valid text with no trailing replacement character, equal to a prefix of the original up to a complete-character boundary.
- **Defect 2 (JVM, PBT candidate)**: for any workspace that exceeds `VAULT_BYTES`, after prune + reconcile the in-memory `exchanges` equal the persisted `exchanges`, are a subset by id of the original, and all durable lists are byte-identical.
- **Defect 3 (JVM)**: for generated import request lists and existing durable sets, the merge admits a result iff it is within the 100-count and 16 MiB budgets, otherwise fails with the exact message — exercising the budget boundary across many sizes.

### Integration Tests

- **Defect 1 (JVM integration)**: full `ApiEngine.execute` against `MockWebServer` serving a multi-byte body at the cap boundary, asserting the stored `StoredBody.value` and `truncated` flag.
- **Defect 2 (instrumented)**: perform captures large enough to force a prune, save through `AppViewModel` on the real Keystore vault, reopen a fresh `WorkspaceVault`, and assert the reloaded `exchanges` equal the in-memory `exchanges` after reconcile — extending `DeviceBoundaryTest`'s round-trip, and confirming no save/update loop (bounded number of writes).
- **Defect 3 (instrumented)**: import a near-2 MiB OpenAPI/HAR input through `AppViewModel`, assert the UI thread is not blocked (parse/budget run off-thread), the drafts merge version-safely without losing a concurrently recorded capture, the durable write-through persists the import, and no network request is issued; also assert over-limit/over-budget/out-of-scope imports still fail with the original messages.
