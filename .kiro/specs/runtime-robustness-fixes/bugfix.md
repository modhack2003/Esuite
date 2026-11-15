# Bugfix Requirements Document

## Introduction

This patch (1.0.2) bundles three small, independent runtime-robustness defects that share one umbrella bug condition: ESuite mishandles a bounded-resource edge case, producing corrupted output, memory/disk divergence, or main-thread blocking. Each defect is minor and low severity on its own, and each fix is intended to be minimal and reversible.

- **Defect 1 — UTF-8 response-boundary corruption** (`transport/ApiEngine.kt`): decoding a textual response that is cut at the 256 KiB byte cap can corrupt a multi-byte character that straddles the boundary.
- **Defect 2 — in-memory vs on-disk history divergence** (`storage/WorkspaceVault.kt`, `AppViewModel.kt`): when `save()` prunes capture history to fit the byte budget, the in-memory workspace keeps the full list, so the UI shows entries that were never persisted.
- **Defect 3 — large imports block the UI thread** (`AppViewModel.kt`, `core/WorkspaceBudget.kt`, `core/ApiImports.kt`): OpenAPI/HAR parsing and durable-budget validation run synchronously on the main thread and can cause an ANR on large input.

These fixes are layered on top of the workspace-persistence-hardening change (durable write-through and versioned migration touching the same `AppViewModel.kt` and `WorkspaceVault.kt`). Defects 2 and 3 interact with that work and must be applied without regressing it. No change is intended to scope enforcement, HTTPS-only runtime, redirect/retry suppression, encryption/fail-closed semantics, resource budgets, or the no-network/no-telemetry contract.

## Bug Analysis

### Current Behavior (Defect)

**Defect 1 — UTF-8 response-boundary corruption**

1.1 WHEN a textual response is captured and the 256 KiB (`Limits.RESPONSE_BYTES`) byte cap falls inside a multi-byte UTF-8 (or other multi-byte charset) character THEN the system decodes the truncated byte array directly via `String(bytes, charset)` and corrupts the straddling trailing character (replacement or garbage bytes) in the stored and displayed body.

**Defect 2 — in-memory vs on-disk history divergence**

1.2 WHEN `WorkspaceVault.save()` repeatedly halves `exchanges` to fit `VAULT_BYTES` and persists the pruned copy THEN the system leaves the in-memory `AppViewModel` workspace holding the full `exchanges` list, so disk and memory disagree until the next `load()`.

1.3 WHEN the in-memory workspace holds more exchanges than were persisted THEN the system displays capture history entries that were never written to disk and that would vanish on restart.

**Defect 3 — large imports block the UI thread**

1.4 WHEN a large OpenAPI input is imported via `importOpenApi` THEN the system runs `ApiImports.openApi` parsing and `WorkspaceBudget.requireDurableCapacity` (a full `Json.encodeToString` of the durable workspace) synchronously inside `mutable.update { }` on the main thread, blocking the UI and risking an ANR.

1.5 WHEN a large HAR input is imported via `importHar` THEN the system runs `ApiImports.har` parsing and the durable-budget encode synchronously on the main thread, blocking the UI and risking an ANR.

### Expected Behavior (Correct)

**Defect 1 — UTF-8 response-boundary corruption**

2.1 WHEN a textual response is captured and the byte cap falls inside a multi-byte character THEN the system SHALL decode leniently and drop the incomplete trailing multi-byte sequence, so the displayed text is valid up to the truncation point.

2.2 WHEN a captured body is truncated at the byte cap THEN the system SHALL CONTINUE TO set the existing `truncated` flag so truncation remains signalled to the UI.

**Defect 2 — in-memory vs on-disk history divergence**

2.3 WHEN `save()` prunes `exchanges` to fit the byte budget THEN the system SHALL update the in-memory workspace to reflect exactly what was persisted, so the UI never shows capture history that was silently dropped on write.

2.4 WHEN the in-memory workspace is reconciled after a byte-budget prune THEN the system SHALL keep memory consistent with disk without altering the `Limits.HISTORY` count cap or the 32 MiB `VAULT_BYTES` behavior.

**Defect 3 — large imports block the UI thread**

2.5 WHEN a large OpenAPI input is imported THEN the system SHALL perform `ApiImports.openApi` parsing and durable-budget validation off the main thread and apply a version-safe state update back on the main thread, without blocking the UI.

2.6 WHEN a large HAR input is imported THEN the system SHALL perform `ApiImports.har` parsing and durable-budget validation off the main thread and apply a version-safe state update back on the main thread, without blocking the UI.

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a textual response fits within the 256 KiB cap (no truncation) THEN the system SHALL CONTINUE TO decode and store the full body unchanged.

3.2 WHEN a non-textual response is captured THEN the system SHALL CONTINUE TO store it as base64 and SHALL CONTINUE TO enforce the `Limits.RESPONSE_BYTES` cap.

3.3 WHEN exchanges fit within the byte budget on save THEN the system SHALL CONTINUE TO persist them without pruning, and SHALL CONTINUE TO honor the `Limits.HISTORY` count cap and 32 MiB `VAULT_BYTES` behavior.

3.4 WHEN a save or load runs THEN the system SHALL CONTINUE TO honor encryption and fail-closed semantics and the durable write-through and versioned migration introduced by workspace-persistence-hardening.

3.5 WHEN an import of any size completes THEN the system SHALL CONTINUE TO bound results to at most 100 saved requests and the 16 MiB durable budget, and SHALL CONTINUE TO execute no network requests during import.

3.6 WHEN an import is merged into the workspace THEN the system SHALL CONTINUE TO reject out-of-budget, over-limit, or out-of-scope imports with the same error messaging, and SHALL CONTINUE TO preserve scope enforcement, HTTPS-only runtime, and the no-telemetry contract.
