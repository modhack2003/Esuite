# Bugfix Requirements Document

## Introduction

ESuite is an unrooted, phone-only, local-first security workbench. There is no backend, no telemetry, and no cloud sync, so the Keystore-backed AES-GCM encrypted workspace on the device is the only copy of the user's security-testing evidence (projects, findings, saved requests, identity configs). If that workspace is lost or becomes unreadable, the evidence is gone with no recovery.

This bugfix addresses two related defects that share one bug condition: **persisted workspace evidence is lost or becomes unrecoverable.**

- **Defect 1 — Process-death autosave gap.** In `AppViewModel.kt`, persistence runs through `state.map { it.workspace }.distinctUntilChanged().drop(1).debounce(500).collect { … vault.save(…) }`. Durable mutations (adding a `Finding`, saving a request, configuring identity headers, importing a collection) are only written to disk 500 ms after the last change, and nothing flushes on lifecycle/ViewModel stop. If the Android process is killed inside that debounce window (backgrounding, OS memory pressure, task swipe), the most recent durable edit is lost even though the user already saw a success-style status message.
- **Defect 2 — No workspace schema migration.** In `WorkspaceVault.kt`, `load()` hard-requires `require(it.version == 1)` and `Workspace(version = 1)` is fixed in `Models.kt`, with no migration branch. The first time the persisted `Workspace` model changes (new field, renamed field, bumped version), every existing encrypted vault fails to load with "Unsupported workspace version"; because there is no plaintext fallback by design, the user permanently loses all projects, findings, saved requests and identity configs on app update.

The fix must preserve ESuite's hard contract: no plaintext fallback, no automatic key replacement, Keystore-backed AES-GCM only, existing fail-closed guarantees intact, no reintroduction of cleartext HTTP / trust-all TLS / telemetry / network egress, and the current bounded-storage model (32 MiB vault cap, 16 MiB durable budget, history pruning) respected by both the autosave and migration changes.

## Bug Analysis

### Current Behavior (Defect)

The bug condition C(X) holds when a persisted-evidence operation loses data: a durable edit made shortly before process death, or a vault whose schema version is not exactly 1.

1.1 WHEN a durable mutation (finding added, request saved, identity headers configured, collection imported) is made and the process is killed within the 500 ms debounce window THEN the system loses the most recent durable edit even though a success-style status message was already shown to the user
1.2 WHEN the app lifecycle stops (backgrounding, task swipe, ViewModel cleared) with durable edits still pending in the debounce window THEN the system does not flush those edits to the encrypted file before the process can be reclaimed
1.3 WHEN a persisted vault has a schema version that is not equal to 1 (any future `Workspace` model change or version bump) THEN the system rejects the load with "Unsupported workspace version"
1.4 WHEN `load()` rejects a vault as an unsupported version THEN the user permanently loses all projects, findings, saved requests and identity configs, because there is no migration path and no plaintext fallback

### Expected Behavior (Correct)

Each defect clause has a matching corrected clause for the same condition.

2.1 WHEN a durable mutation is made and the process is killed within what was previously the debounce window THEN the system SHALL have already persisted that edit to the encrypted file (via write-through for durable data or a guaranteed flush on lifecycle/ViewModel stop) so that it survives process death
2.2 WHEN the app lifecycle stops with durable edits still pending THEN the system SHALL flush those pending durable edits to the encrypted file before the process can be reclaimed
2.3 WHEN a persisted vault has a known older schema version THEN the system SHALL apply explicit forward-only migrations to upgrade it in place and load it without data loss
2.4 WHEN a persisted vault's data is genuinely unsupported or corrupt with no known migration path THEN the system SHALL fail closed and surface the error WITHOUT overwriting, truncating, or deleting the existing encrypted file

### Unchanged Behavior (Regression Prevention)

For all inputs that do not trigger the bug condition, the fixed code must behave identically to the current code.

3.1 WHEN the encryption key is missing or invalidated, or the stored envelope is corrupt THEN the system SHALL CONTINUE TO fail closed without generating a replacement key and without creating any plaintext fallback
3.2 WHEN a save write fails midway THEN the system SHALL CONTINUE TO retain the previous encrypted `AtomicFile` state via `failWrite`
3.3 WHEN bounded-capture history grows THEN the system SHALL CONTINUE TO treat non-durable history writes as debounced and apply the 32 MiB vault cap, 16 MiB durable budget, and history pruning
3.4 WHEN persisting, flushing, or migrating a workspace THEN the system SHALL CONTINUE TO use Keystore-backed AES-GCM only, with no cleartext HTTP, no trust-all TLS, no telemetry, and no network egress
3.5 WHEN a current-version vault is loaded and needs no migration THEN the system SHALL CONTINUE TO load its projects, exchanges, findings, saved requests and identity configs unchanged and enforce the existing record-count limits
3.6 WHEN there is no existing workspace file THEN the system SHALL CONTINUE TO return a fresh empty `Workspace` rather than failing
