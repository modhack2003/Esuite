# Workspace Persistence Hardening Bugfix Design

## Overview

ESuite is an unrooted, phone-only, local-first security workbench with no backend, telemetry, or cloud sync. The Keystore-backed AES-GCM vault at `noBackupFilesDir/workspace.esv` is the only copy of the user's evidence (projects, findings, saved requests, identity configs). Two defects share one bug condition — **persisted workspace evidence is lost or becomes unrecoverable** — and both are addressed here.

- **Defect 1 (process-death autosave gap).** In `AppViewModel.loadWorkspace()`, persistence is a single reactive collector: `state.map { it.workspace }.distinctUntilChanged().drop(1).debounce(500).collect { … vault.save(state.value.workspace) }`. Every durable mutation (adding a `Finding`, saving a request, configuring identity headers, importing a collection) waits 500 ms behind the debounce, and nothing flushes when the process stops. `onCleared()` only calls `engine.close()`. If the process is reclaimed inside the debounce window, the most recent durable edit is lost even though the user already saw a success-style status message. (`FindingsScreen`'s save button is the lone path that already calls `model.saveNow()`; every other durable path relies on the debounce.)
- **Defect 2 (no schema migration).** `WorkspaceVault.load()` does `require(it.version == 1)` and `Models.Workspace(version = 1)` is fixed, with no migration branch. The first time the persisted model changes, every existing vault fails to load with "Unsupported workspace version"; with no plaintext fallback by design, the user permanently loses everything.

The fix is two targeted, independent changes plus a version bump:

1. **Durable write-through + lifecycle flush.** Durable mutations persist immediately (write-through) on the existing single-threaded `storageDispatcher`; bounded capture history (`exchanges`) keeps debouncing. A process-lifecycle `ON_STOP` observer flushes any pending durable state before the process can be reclaimed.
2. **Versioned decode + forward-only migration.** `load()` decodes decrypted bytes to a `JsonElement`, reads the integer `version`, applies ordered forward-only migrations up to `CURRENT_VERSION`, then deserializes. Unknown-newer versions, gaps with no migration path, and corrupt bytes all fail closed without touching the existing file.

Every existing hard contract is preserved: no plaintext fallback, no automatic key replacement, Keystore AES-GCM only, `AtomicFile` `failWrite` retention, fail-closed on missing key / corrupt envelope, the 32 MiB vault cap / 16 MiB durable budget / history pruning, and no cleartext HTTP / trust-all TLS / telemetry / network egress.

## Glossary

- **Bug_Condition (C)**: A persisted-evidence operation that loses data — a durable edit made shortly before process death, or a load of a vault whose schema version is not exactly `1` (which the current code rejects outright).
- **Property (P)**: The desired behavior under C — durable edits are already on disk before the process dies, and a vault with a known older schema version is migrated in place and loaded with no data loss.
- **Preservation**: Behavior for inputs where C does NOT hold (current-version loads, history-only writes, missing-key/corrupt loads, no-file startup) must be byte-for-byte and semantically identical to the current code.
- **Durable mutation**: A change to `projects`, `findings`, `savedRequests`, or `identityConfigs` — the evidence that has no other copy. Produced by `createProject`, `addFinding`, `saveDraft`/`mergeSaved`, `configureIdentity`, `importOpenApi`/`importHar`, `deleteProject`, `deleteSavedRequest`, `deleteFinding`, `clearSessions`.
- **History (capture) write**: A change to `exchanges` only — bounded, auto-pruned, reproducible by re-running a request. Produced by `record()` and `clearHistory()`.
- **storageDispatcher**: `Dispatchers.IO.limitedParallelism(1)` in `AppViewModel` — the single-threaded confinement all vault I/O already runs on, serializing writes against the debounce collector.
- **CURRENT_VERSION**: The workspace schema version this build writes and migrates toward (bumped from `1` to `2` as part of this fix).
- **Migration**: A pure `JsonObject -> JsonObject` transform that upgrades a workspace document from version N to N+1, applied before deserialization into `Workspace`.
- **Vault envelope**: `VaultCipher` output — `ESV1` magic + 12-byte GCM nonce + AES-GCM ciphertext with the magic as AAD; `open()` validates magic and size.

## Bug Details

### Bug Condition

The bug manifests in two ways under one condition: persisted evidence is lost or cannot be read back. Either (a) a durable mutation is accepted (and acknowledged with a success-style message) but only queued behind the 500 ms debounce with no lifecycle flush, so process death inside that window drops it; or (b) `load()` receives a decrypted workspace whose `version` field is not exactly `1`, which the current `require(it.version == 1)` rejects, destroying access to all evidence because there is no migration path and no plaintext fallback.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input — either a PersistEvent(kind, workspace, processStoppedWithinWindow)
                 or a LoadEvent(envelopeVersion, decodable)
  OUTPUT: boolean

  // Defect 1: durable edit not yet on disk when the process stops
  IF input IS PersistEvent THEN
    RETURN input.kind == DURABLE
           AND input.processStoppedWithinWindow == true
           AND NOT alreadyPersisted(input.workspace)   // not yet written at stop time
  END IF

  // Defect 2: a vault that SHOULD load but the current code rejects on version
  IF input IS LoadEvent THEN
    RETURN input.decodable == true
           AND input.envelopeVersion != 1
           AND input.envelopeVersion IN KNOWN_MIGRATABLE_VERSIONS   // has a forward path to CURRENT_VERSION
  END IF

  RETURN false
END FUNCTION
```

### Examples

- **Finding then task-swipe.** User adds a finding on the Findings screen flow that does not flush (or any durable edit from another screen), sees "Observation added", backgrounds the app; Android reclaims the process within 500 ms. Expected: finding is on disk. Actual: finding is gone on next launch.
- **Import collection then OS kill.** User imports a HAR/OpenAPI collection (`importHar`/`importOpenApi` → `mergeSaved`), sees "Imported N drafts"; memory pressure kills the process before the debounce fires. Expected: collection persisted. Actual: collection lost.
- **Identity headers then crash.** User saves identity-B auth headers (`configureIdentity`), sees "Identity headers saved…"; process dies. Expected: headers persisted. Actual: headers lost.
- **App update bumps schema.** A later build writes `version = 2`; an older build — or `load()` before this fix — hits `require(it.version == 1)` and throws "Unsupported workspace version". Expected (post-fix): a `1`-version vault migrates to `2` and loads unchanged. Actual (pre-fix): vault is unreadable; all projects/findings/saved requests/identity configs are lost.
- **Edge case — genuinely newer/corrupt vault.** A vault written by a strictly newer build (`version` > `CURRENT_VERSION`), or with undecodable/truncated bytes. This is NOT the bug condition: it must fail closed, surface the error, and leave the file byte-identical.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Fail-closed load when the encryption key is missing/invalidated (`key(create = false)` throws) or the envelope is corrupt (`VaultCipher.open` throws) — no replacement key, no plaintext fallback.
- A mid-write failure retains the previous encrypted file through `AtomicFile.failWrite(stream)`.
- Bounded capture history (`exchanges`) stays debounced and is subject to the 32 MiB `VAULT_BYTES` cap, the 16 MiB durable budget (`WorkspaceBudget.requireDurableCapacity`), and `save()`'s exchange-halving prune.
- All persistence/flush/migration continues to use Keystore-backed AES-GCM only — no cleartext HTTP, no trust-all TLS, no telemetry, no network egress.
- A current-version vault that needs no migration loads its projects/exchanges/findings/saved requests/identity configs unchanged, enforcing the existing record-count limits.
- With no existing workspace file (and no `.bak`), `load()` returns a fresh empty `Workspace`.

**Scope:**
All inputs that do NOT trigger the bug condition are completely unaffected:
- History-only (`exchanges`) writes — still debounced, never forced write-through.
- Loads of a current-version, unmodified vault.
- Loads where the key is missing/invalidated or the envelope is corrupt/tampered (must still throw, file untouched).
- Loads where there is no file at all (fresh empty workspace).
- Loads of a strictly-newer-than-`CURRENT_VERSION` vault (must fail closed, file untouched).

The expected correct behavior under the bug condition is defined in the Correctness Properties section (Properties 1 and 2).

## Hypothesized Root Cause

Based on the code, the causes are concrete rather than hypothetical:

1. **Debounce-only persistence with no durable write-through.** `loadWorkspace()` wires every workspace change — durable or history — through one `.debounce(500)` collector. There is no separate path that writes durable edits immediately, so acknowledged durable edits sit in memory for up to 500 ms (longer if changes keep arriving).

2. **No lifecycle flush.** `AppViewModel.onCleared()` calls only `engine.close()`. `MainActivity` has no `onStop`/lifecycle hook into the model. Nothing guarantees a flush when the app is backgrounded, task-swiped, or the ViewModel is cleared, so the debounce window is never force-drained before the process can be reclaimed.

3. **Hard version equality with no migration branch.** `WorkspaceVault.load()` deserializes straight into `Workspace` and asserts `require(it.version == 1)`. `Models.Workspace` pins `version = 1`. There is no decode-to-`JsonElement`-first step, no version switch, and no migration registry, so any version other than `1` is fatal.

4. **Version bump is unreleasable without migration.** Because (3) exists, bumping the model's `version` for any future schema change would brick every installed vault on update — the migration path must land together with (and before) any version bump.

## Correctness Properties

Property 1: Bug Condition - Durable Edits Survive Process Death

_For any_ input where the bug condition holds for a durable mutation (`isBugCondition` returns true for a `PersistEvent` with `kind == DURABLE`), the fixed code SHALL have already written that durable state to the encrypted file — via immediate write-through on `storageDispatcher` and/or a guaranteed `ON_STOP` flush — so that reopening the vault after simulated process death returns a `Workspace` containing the edit, while preserving the "save failed → keep memory, previous file retained" semantics.

**Validates: Requirements 2.1, 2.2**

Property 2: Bug Condition - Older-Version Vaults Migrate and Load

_For any_ input where the bug condition holds for a load (`isBugCondition` returns true for a `LoadEvent` with a known migratable `version != 1`), the fixed `load()` SHALL apply ordered forward-only migrations to reach `CURRENT_VERSION` and return the fully populated `Workspace` with no data loss, enforcing the existing record-count limits.

**Validates: Requirements 2.3**

Property 3: Preservation - Non-Durable and Non-Bug Inputs Unchanged

_For any_ input where the bug condition does NOT hold — a history-only (`exchanges`) write, a current-version load needing no migration, a missing-key or corrupt or tampered envelope, a strictly-newer-than-`CURRENT_VERSION` vault, or no file at all — the fixed code SHALL produce the same observable result as the original code: history stays debounced under the budget/prune rules; current-version loads return unchanged content under the record limits; missing-key/corrupt/newer loads fail closed and leave the encrypted file byte-identical; and no-file startup returns a fresh empty `Workspace`.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Required

Four files, each change surgical and independent.

**File**: `app/src/main/java/dev/esuite/app/AppViewModel.kt`

**Function**: new `persistDurable()` helper; call sites in durable mutations; new lifecycle flush entry point; unchanged debounce collector for history.

**Specific Changes**:
1. **Add a durable write-through helper.** Introduce a private `persistDurable()` (e.g. `fun persistDurable()` that launches on `viewModelScope` and does `withContext(storageDispatcher) { vault.save(state.value.workspace) }`, reusing `saveNow()`'s existing success/failure messaging and the "previous file retained" semantics). It must run on the same `storageDispatcher`, so it serializes with the debounce collector's saves — no second writer, no race.
2. **Call write-through from every durable mutation.** After the `mutable.update { … }` in `createProject`, `addFinding`, `saveDraft`/`mergeSaved`, `configureIdentity`, `importOpenApi`/`importHar`, `deleteProject`, `deleteSavedRequest`, `deleteFinding`, and `clearSessions`, trigger `persistDurable()`. Keep `record()`/`clearHistory()` (history only) on the debounce path untouched. Deduplicate the Findings screen's existing explicit `saveNow()` so a single finding save does not double-write.
3. **Keep the debounce collector for history.** The existing `state.map { it.workspace }.distinctUntilChanged().drop(1).debounce(500).collect { … }` remains as the capture-history writer. Because durable writes now happen eagerly and share the dispatcher, the debounced save that trails a durable edit is a harmless idempotent re-write of the same bytes; optionally narrow the debounced map to history-relevant fields to avoid it, but correctness does not depend on that.
4. **Add a flush entry point for lifecycle.** Expose a `flush()` (or reuse `saveNow()`) intended to be called from process `ON_STOP`. It must be safe to call when `ready == false` (no-op if nothing is loaded) and must not throw into the lifecycle callback.
5. **Guarantee a stop-time flush.** Either register a `ProcessLifecycleOwner.get().lifecycle` `DefaultLifecycleObserver.onStop` in the ViewModel `init` (removed in `onCleared`) that calls `flush()`, or drive it from `MainActivity.onStop` (Change set below). `onCleared()` keeps `engine.close()` and additionally requests a best-effort flush. Preserve all failure messaging.

**File**: `app/src/main/java/dev/esuite/app/MainActivity.kt`

**Function**: `MainActivity` lifecycle.

**Specific Changes**:
1. **Flush on stop (if not using ProcessLifecycleOwner).** Hold the same `AppViewModel` instance Compose uses and override `onStop()` to call `model.flush()`/`model.saveNow()` before `super.onStop()`. This is the simplest guaranteed drain of the debounce window on backgrounding/task-swipe. Pick exactly one of this or the ViewModel-side `ProcessLifecycleOwner` observer to avoid double-wiring.

**File**: `app/src/main/java/dev/esuite/app/storage/WorkspaceVault.kt`

**Function**: `load()`; new private `migrate()` / migration sequence.

**Specific Changes**:
1. **Decode to JsonElement first.** In `load()`, after `VaultCipher.open(...)`, parse the UTF-8 bytes with `Json.parseToJsonElement(...)` into a `JsonObject` instead of deserializing straight into `Workspace`. Undecodable/corrupt bytes throw here → fail closed, file untouched (same as today).
2. **Read the version and migrate forward.** Read the integer `version` (default/validate as needed). If `version == CURRENT_VERSION`, deserialize as today. If `version` is a known older value, apply the ordered migration sequence `v1→v2→…→CURRENT_VERSION` (pure `JsonObject` transforms from `Models`), then deserialize.
3. **Fail closed on unknown/newer versions.** If `version > CURRENT_VERSION`, or a required migration step is missing, throw with a clear message (e.g. "Unsupported workspace version") WITHOUT writing, truncating, or deleting the file. `load()` performs no writes, so the file is inherently untouched on any throw.
4. **Keep all existing load guarantees.** Preserve `key(create = false)` (no replacement key), the `.bak` legacy-recovery branch, the `VAULT_BYTES + 32` size check, and the post-deserialize record-count and HTTPS-origin limit checks — these run on the migrated object so limits still hold.
5. **Do not change `save()` / `erase()` semantics.** `save()` continues to write `CURRENT_VERSION`, halve exchanges to fit `VAULT_BYTES`, and use `AtomicFile` `startWrite`/`finishWrite`/`failWrite`. `erase()` is unchanged.

**File**: `app/src/main/java/dev/esuite/app/core/Models.kt`

**Function**: `Workspace` default version; `CURRENT_VERSION` constant; migration definitions.

**Specific Changes**:
1. **Introduce `CURRENT_VERSION`.** Add a single source of truth (e.g. in `Limits` or a `WorkspaceSchema` object) set to `2`, and change `Workspace(version = …)` to default to `CURRENT_VERSION` so new/saved vaults carry the current version.
2. **Define the migration registry.** Add an ordered list/map of migrations keyed by source version, each a pure `JsonObject -> JsonObject`. Include a `v1→2` migration (a no-op shape upgrade is acceptable — it simply sets `version = 2`) so existing 1.0.x vaults load. This makes the version bump releasable and gives a template for future schema changes.
3. **Keep serialization compatible.** Any new/renamed field introduced at v2 must have a defined migration mapping and sensible defaults so a migrated v1 document deserializes cleanly under the existing `Json { encodeDefaults = true }` and record limits.

## Testing Strategy

### Validation Approach

Two phases: first surface counterexamples on the UNFIXED code (durable edit lost after simulated process death; a `version != 1` vault rejected), then verify the fix persists durable edits before stop, migrates older vaults losslessly, and leaves all non-bug behavior — including fail-closed-file-untouched — unchanged. Keystore-dependent paths run as instrumented tests (reusing `DeviceBoundaryTest`'s pattern); migration-decode and write-through logic that can be isolated from the Keystore run as JVM unit/property tests.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate both defects BEFORE implementing the fix, and confirm the root-cause analysis. If a test unexpectedly passes on unfixed code, re-hypothesize.

**Test Plan**: For Defect 1, drive a durable mutation through `AppViewModel`, then simulate process death by constructing a fresh `WorkspaceVault`/`AppViewModel` without waiting out the 500 ms debounce and without a flush, and assert the edit is absent on reload (demonstrates the gap). For Defect 2, hand-craft a decrypted workspace JSON with `version = 2`, seal it with the vault key, and call `load()` to observe the "Unsupported workspace version" rejection.

**Test Cases**:
1. **Durable-edit-before-stop (unfixed)**: add a finding/saved request, reopen the vault immediately — edit missing (will fail on unfixed code once the fix's "should be present" assertion is applied).
2. **Lifecycle flush absent (unfixed)**: invoke the intended stop/flush entry point — it does not exist / does not write on unfixed code.
3. **Version-2 vault rejected (unfixed)**: seal a `version = 2` document and `load()` — throws "Unsupported workspace version" (will fail on unfixed code once migration is expected to succeed).
4. **Edge case — newer/corrupt vault**: seal a `version = 999` document and a byte-flipped envelope and `load()` — must throw and leave the file byte-identical (should hold on unfixed code too; this is preservation, not the bug).

**Expected Counterexamples**:
- A durable edit acknowledged to the user but absent after immediate reopen.
- A `version = 2` vault that fails to load even though its data is fully valid.
- Possible causes: debounce-only persistence, no lifecycle flush, hard `require(it.version == 1)` with no migration.

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  IF input IS PersistEvent THEN
    flushOrWriteThrough(input.workspace)
    reopened := WorkspaceVault.load()
    ASSERT reopened CONTAINS input.workspace.durableData
  ELSE IF input IS LoadEvent THEN
    result := WorkspaceVault.load()           // envelope at input.envelopeVersion
    ASSERT result == expectedMigratedWorkspace(input)
    ASSERT recordLimitsEnforced(result)
  END IF
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalBehavior(input) == fixedBehavior(input)
  // history-only writes stay debounced + budgeted + pruned
  // current-version load returns unchanged content under record limits
  // missing-key / corrupt / newer-version load throws AND file bytes are unchanged
  // no-file startup returns fresh empty Workspace
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation because it generates many inputs across the domain, catches edge cases manual tests miss, and gives strong guarantees that non-bug behavior is unchanged. The round-trip-through-migration and fail-closed-file-untouched properties are especially suited to generated inputs.

**Test Plan**: Observe current behavior on unfixed code for history-only writes, current-version loads, corrupt/tampered loads, and no-file startup, then write property/unit tests capturing it and confirm it still holds after the fix. Reuse `DeviceBoundaryTest.androidKeystoreWorkspaceRoundTripAndTamperFailure`'s tamper-and-assert-bytes-unchanged pattern for the fail-closed cases.

**Test Cases**:
1. **History stays debounced/budgeted**: a stream of `exchanges`-only changes is not written through eagerly and still obeys the 32 MiB cap, 16 MiB durable budget, and exchange-halving prune.
2. **Current-version round-trip**: save then load a `CURRENT_VERSION` workspace — content identical, record limits enforced (same as `DeviceBoundaryTest` round-trip).
3. **Fail-closed, file untouched**: missing/invalidated key, byte-flipped envelope, and `version > CURRENT_VERSION` each throw on `load()` and leave the file byte-identical (`assertArrayEquals`).
4. **No-file startup**: with no `.esv` and no `.bak`, `load()` returns a fresh empty `Workspace`.

### Unit Tests

- Versioned decode: `version == CURRENT_VERSION` deserializes directly; a known older version routes through the migration sequence; `version > CURRENT_VERSION` throws.
- Migration registry: `v1→2` migration maps a valid v1 `JsonObject` to a valid v2 one with defaults populated and record limits intact.
- Corrupt/undecodable JSON after decrypt throws (fail closed) without writing.
- Durable write-through helper triggers a `storageDispatcher` save and preserves `saveNow()` success/failure messaging; flush entry point is a safe no-op when `ready == false`.

### Property-Based Tests (JVM)

- **Migration round-trip**: for generated valid v1 workspaces, sealing a v1 document and loading it yields the expected migrated `Workspace` (equivalent to the v2 shape) with no data loss — isolated from the Keystore by testing the decode+migrate function against a provided key (as `VaultCipherTest` injects a `SecretKeySpec`).
- **Preservation of non-durable writes**: for generated sequences of history-only changes, durable write-through is not invoked and budget/prune invariants hold.
- **Fail-closed invariance**: for generated envelopes (newer version, random corruption), `load()` throws and the input bytes are unchanged.

### Integration / Instrumented Tests (Keystore)

- **Durable edit survives simulated process death** (instrumented): perform a durable mutation, invoke flush/write-through, construct a fresh `WorkspaceVault`, assert the edit is present — extends `DeviceBoundaryTest`'s Keystore round-trip.
- **Older-version vault migrates on device** (instrumented): seal a `version = 1` document with the real Keystore key, `load()`, assert full content and `CURRENT_VERSION` after save.
- **Lifecycle flush** (instrumented or ViewModel test): simulate `ON_STOP`/`onStop` and assert the pending durable state is written before the process would be reclaimed.
- **Tamper/newer-version fail-closed on device** (instrumented): reuse the existing byte-flip assertion plus a `version > CURRENT_VERSION` case; both throw and leave the file byte-identical.
