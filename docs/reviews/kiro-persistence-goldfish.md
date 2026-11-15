# Kiro workspace persistence — focused Goldfish review

Reviewed 2026-11-15, approximately 14:56–15:02 +05:30. Result: **not ready to claim process-death durability or migration validation**. This is one Codebase Details Goldfish review against the Implementation Readiness Standard, not the five-stage EGM pipeline. Source, specifications, checkboxes and Git state were not intentionally modified; this report is the sole authored output. No compilation or tests were run by this reviewer.

Kiro was actively editing. Findings refer to the live snapshots below, not a frozen commit. In particular, the first AppViewModel read lacked mutation call sites, but the final read contained them; their temporary absence is not a finding. Task 5.2 remained pending and MainActivity had no stop hook at the final read. That unfinished hook is not presented as a completed implementation defect.

## Snapshot and verification evidence

Local HEAD read: `e9530076196bdac3d973aad179563464b79ae6a1`. This is not a hash of the uncommitted changes. A Git status attempt was stopped after it failed to return promptly; no branch, commit, reset, checkout or configuration change was made.

GitHub connector independently returned the latest three runs. Latest full **Build and verify** was [43682611496](https://github.com/modhack2003/Esuite/actions/runs/43682611496), completed successfully at 2026-11-15 06:03:08 UTC for `2edb9f75ad27f612d69f06816553576206014fa5`. The two subsequent release-workflow summaries were 43682961855 (skipped) and 43683684385 (success), for the same source. Those results predate these persistence edits and do not verify them. No release artifact or release-gate execution is inferred from a workflow summary.

Existing local `app/build/test-results/testDebugUnitTest/TEST-*.xml` files reported 33 tests, zero failures/errors/skips across seven suites when read. Their test timestamps were around 09:13 UTC and source continued changing afterward. These are inspected artifacts, not reviewer reruns, and their coverage is limited as described below. No new device result was found or claimed.

SHA-256 snapshot identifiers (uppercase hex; final mutable files checked at 15:01:32 +05:30):

| File | SHA-256 |
| --- | --- |
| `.kiro/specs/workspace-persistence-hardening/bugfix.md` | `5F50CB95585FB8A17069B2087C185A84B4113BFDF2035CA171A98157389D26AF` |
| `.kiro/specs/workspace-persistence-hardening/design.md` | `39F4E9CC787DFD28C4B117CB447675F5F5E17595813C23D23C94C96EE5EDC28D` |
| `.kiro/specs/workspace-persistence-hardening/tasks.md` | `39A272F23886213F705E7BD46F0E30D70E278C5C66937869C9E807CF84EB6997` |
| `app/src/main/java/dev/esuite/app/AppViewModel.kt` | `C5E545B09C770E35041A298785BA0875774AEB50008C518EE88743B8A27EE464` |
| `app/src/main/java/dev/esuite/app/MainActivity.kt` | `ED6BBB5957AC1A5F9BF0AE178485DA8035D84A9803B56E7B2F16111537FA4259` |
| `app/src/main/java/dev/esuite/app/core/Models.kt` | `7CF6E663282EFEE7E8FE7E6847ED3F4A6C0982F060176C86B1EDE9F274A05B30` |
| `app/src/main/java/dev/esuite/app/core/WorkspaceSchema.kt` | `96E0BA827DB2FAB6C67FB37625106F977F270071DB0DFEA0C8FF503FFC6E780E` |
| `app/src/main/java/dev/esuite/app/storage/WorkspaceVault.kt` | `67FFBCBD40E629163C443DBCCF4206B379468CD461151A6B197C283F1799668B` |
| `app/src/test/java/dev/esuite/app/storage/WorkspaceMigrationTest.kt` | `7758C8D19C7B088EB10EC01D98BCC016060FF818DE052F3C478C86BFB2CE95E3` |
| `app/src/test/java/dev/esuite/app/storage/WorkspacePreservationTest.kt` | `0B71B7E75B6DEF755BF4769929AADA98CE794FDE8793BCE9BDCC29C9A20629AD` |
| `app/src/androidTest/java/dev/esuite/app/DeviceBoundaryTest.kt` | `B840D1122BA126363656D86E2995B47CA907432CDAB352BE3EBE3E87B9D7D90D` |

## Read inventory

Read in full: the three target specifications; repository `AGENTS.md`, `docs/PRODUCT_PLAN.md`, `docs/AGENT_HANDOFF.md`, `docs/ROADMAP.md`; `Models.kt`, actual `core/WorkspaceSchema.kt`, `storage/WorkspaceVault.kt`, `AppViewModel.kt`, `core/WorkspaceBudget.kt`, `storage/VaultCipher.kt`; `WorkspaceMigrationTest.kt`, `WorkspacePreservationTest.kt`, `VaultCipherTest.kt`, and `DeviceBoundaryTest.kt` under their corresponding source sets.

Read selectively: `MainActivity.kt` activity ownership/setup and Findings controls; `ui/WorkspaceControls.kt` save references; `core/ReportExporter.kt` evidence references; `ui-tests/src/main/java/dev/esuite/uitests/ProductionUiTest.java` persistence scenario and `UpgradeUiTest.java` save/restart references; `.github/workflows/ci.yml` build/device command sections; existing JVM test-result XML. Browser cookie flushing references were searched to distinguish that separate persistence domain.

Applied persona: `C:/Users/bikra/.codex/plugins/cache/egm/egm/0.1.1/agents/codebase_details.md`. Applied standard: the same package's `resources/document_standards/implementation-readiness-standard.md`. Official Android lifecycle references are linked beside the relevant finding.

## Findings

### WPH-001 — Blocker — queued saves do not establish the promised durability boundary

**Type:** current implementation defect relative to the specification, plus planned lifecycle risk.

**Locations:** `AppViewModel.kt:59–65` (`saveNow`, `persistDurable`, `flush`), `:276–288` (`addFinding`), `:293` (`onCleared`); `bugfix.md:29–30`; `design.md:12`, `:106`, `:133–144`; `tasks.md:154–170`, `:176–188`.

`persistDurable()` launches a coroutine and returns before `withContext(storageDispatcher)` commits the encrypted file. `addFinding()` already returns true, clears the editor through the Findings button, and reports an added observation before that commit. `flush()` also returns immediately after launching. Serializing vault I/O prevents overlapping calls within this model; it does not make a mutation, acknowledgement, or lifecycle callback wait for durable storage. A process killed before the queued write runs still loses the accepted edit.

There is also a concrete acknowledgement race: a save of workspace S1 can be running while the main thread accepts S2. S1 then posts the generic “Workspace saved” message while S2 is still queued. The UI test at `ProductionUiTest.java:33–35` waits for that unqualified message before killing the process, so a preceding project save can satisfy the wait without proving the later finding was committed.

The new `onCleared()` call cannot provide even the advertised best-effort coroutine flush: AndroidX cancels `viewModelScope` before calling `onCleared`, so launching into it there schedules no useful persistence. See the official [ViewModel API](https://developer.android.com/reference/androidx/lifecycle/ViewModel). Calling the same non-awaitable method from a future `onStop` hook does not create a guaranteed drain either: stopped/cached processes can be killed and may receive no execution time, as documented in [process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle).

**Required next action:** define a committed workspace revision and an awaitable save outcome; only acknowledge that revision as saved after the actual vault commit, and keep newer edits visibly pending. Specify stop-time work as best effort unless it actually completes before callback return; do not promise arbitrary kills/crashes preserve uncommitted edits. Handle cancellation distinctly from save failures. Verify mutation-during-save, write failure, clearing, and real process termination after a revision-specific save acknowledgement using the independent UI harness. The pending lifecycle route alone cannot close this finding.

### WPH-002 — Major — the migration tests never execute the migration being shipped

**Type:** current test/validation defect and misleading completion evidence.

**Locations:** `WorkspaceSchema.kt:30`, `:39–40`, `:57–63`; `WorkspaceMigrationTest.kt:43–73`; `DeviceBoundaryTest.kt:176–195`; `tasks.md:107–112`, `:131–137`.

Both migration fixtures explicitly use `version = 2`, which equals `CURRENT_VERSION`. The `while (version < CURRENT_VERSION)` branch and the only registry entry, `1 -> stampVersion(document, 2)`, are never exercised. The JVM test would still pass if the v1 migration entry were deleted. Its copied `require(decoded.version == 1)` demonstrates an old gate rejecting future data, not a known older document migrating into this build. The instrumented variant also tests only a current-version round-trip. Thus “migrates forward” and “confirming Defect 2 is fixed” overstate the evidence even if all these tests pass.

**Required next action:** test actual deployed v1 JSON through the production `WorkspaceSchema.migrate` and Keystore-backed `WorkspaceVault.load`, assert v2 plus all projects, exchanges, findings, saved requests and identity configs, and include malformed version, missing path, current version and unknown-newer rejection. Demonstrate that removing/breaking the v1 registry entry fails the older-version case. Correct test names and completion claims; a real migration needs a regression test that crosses a schema boundary.

### WPH-003 — Major — instrumented persistence tests leave active writers behind

**Type:** current test-infrastructure race.

**Locations:** `DeviceBoundaryTest.kt:75–99`, `:111–137`, `:147–153`; `AppViewModel.kt:35`, `:48–53`; `WorkspaceVault.kt:17–18`, `:30`, `:46`, `:58`.

The new tests directly construct `AppViewModel` and never clear a `ViewModelStore`, cancel its scope, or close its engine. Their `finally` blocks erase the shared vault/key while the model's autosave collector and pending writes remain live. After a fast success, the trailing 500 ms autosave can recreate the just-erased vault/key or overwrite a subsequent test's fixture. Each polling load constructs another `WorkspaceVault`; its `@Synchronized` methods lock that new instance, not the ViewModel's vault, and each model has its own limited-parallelism dispatcher. Those guards do not serialize all of these owners.

The tests also keep the original process and model running while polling for up to 400 ms or five seconds. The flush case can pass through ordinary 500 ms autosave even if `flush()` does nothing. Neither test proves process-death or lifecycle wiring.

**Required next action:** give test models managed ownership and deterministic teardown, wait for/cancel all test writer work before erasing the fixture, and prevent concurrent owners of the shared vault. Use controlled scheduler/storage barriers for write-through and flush behavior so a debounce cannot satisfy the wrong assertion. Keep the independent process-kill scenario as a separate device gate; do not label a fresh object in the same live process “simulated process death.”

### WPH-004 — Major — schema rejection is incorrectly described as permanent destruction

**Type:** specification/root-cause defect; not a demonstrated current data-loss bug.

**Locations:** `bugfix.md:10`, `:18`, `:22–23`; `design.md:8`, `:33`, `:65`, `:98–100`; `WorkspaceVault.kt:30–44`; `Models.kt:27`.

The text says the first model change/version bump destroys access permanently or bricks every installed vault. A new optional field with a default does not inherently require a version bump. Changing the new model's default to 2 also does not rewrite a persisted explicit `version: 1`. An older application rejecting a newer schema is a safe fail-closed downgrade outcome, not evidence that the encrypted file was deleted or that the capable newer application cannot reopen it. The migration fixture's rejection is exactly that future-version case.

Migration support is useful planned update compatibility, and the current v1-to-v2 no-op registry appears structurally capable of that conversion. No destructive migration was observed. However, “upgrade in place” in `bugfix.md:31` is also ambiguous: the implementation migrates in memory and `load()` intentionally does not save the upgraded document; a later save persists it.

**Required next action:** separate the proven autosave gap from future schema-evolution hardening, describe supported old/new/downgrade behavior accurately, state when an upgraded schema reaches disk, and identify any actual model change motivating this no-op version bump. Preserve unknown-newer rejection without representing it as lost evidence.

### WPH-005 — Major — preservation completion exceeds the production paths tested

**Type:** current validation gap and checklist accuracy defect.

**Locations:** `WorkspacePreservationTest.kt:95–124`, `:137–147`, `:189–197`, `:212–229`; `tasks.md:20–40`, `:65–94`, `:139–149`; `DeviceBoundaryTest.kt:36–54`.

The preservation suite's current-version case still decodes explicit v1 directly with `Json`, without `WorkspaceSchema` or `WorkspaceVault.load`. The newer-version case executes a copied `require(decoded.version == 1)`, so it would pass even if production accepted version 999. The prune case calls `pruneLikeSave`, an exact duplicate of the implementation. The no-file case constructs `Workspace()` without opening any file. These cases can be useful primitive baselines, but cannot establish the claimed load-side regression protection, real file nonmutation, autosave debounce, or `AtomicFile.failWrite` retention. Existing on-device coverage proves a current-version round-trip, legacy backup recovery and tamper rejection; missing-key/newer-version/no-file/interrupted-save coverage is not supplied by the newly added cases.

Task 1 is checked despite its own criterion requiring an executed, documented pre-fix failure, while its execution text explicitly says the cases were not executed. Tasks 4.3/4.4 say DONE while portions of their stated acceptance remain device-gated. Deferral is disclosed in prose but the binary completion status does not preserve the acceptance boundary. The demand to rerun the same tests without adding/changing tests must not prevent correcting these coverage errors.

**Required next action:** distinguish primitive baseline tests from production-seam tests; route schema acceptance assertions through production code; test budget/prune through a shared production seam or actual vault; add failure-injection and instrumented file/key cases. Mark unexecuted acceptance criteria pending, and report JVM, device and real process-death evidence separately. Keep existing passing XML as evidence of only the paths it actually exercised.

### WPH-006 — Major — saved observations do not make their referenced captures durable

**Type:** existing evidence-retention limitation omitted/mischaracterized by this plan; not introduced by the new migration code.

**Locations:** `Models.kt:19–22` (`Finding.evidenceIds`); `AppViewModel.kt:105–106` (`record`, before later line shifts), `:280`, `:292`; `WorkspaceVault.kt:49–51`; `design.md:22–23`, `:106`; `MainActivity.kt:190`; `docs/AGENT_HANDOFF.md:86`, `:94`.

Findings retain only capture IDs. History can evict the referenced exchange after 100 records, through save-time halving, or through clear-history. A fully committed finding can therefore survive while its original request/response evidence disappears, and the UI currently prints the IDs without saying the attachments expired. The design labels history “reproducible by re-running a request,” which is unsafe as an evidence guarantee: state, response contents and timing may change, and replay can have side effects. Write-through does not repair this boundary.

**Required next action:** explicitly scope this increment's durability claim to the saved observation/configuration/collection records, keep the existing REPORT-01 expired-attachment gate open, and remove the reproducibility assertion. If full saved-evidence durability is intended for this increment, decide and budget a pinned capture/snapshot policy with eviction and deletion tests before claiming no evidence loss. This review does not require silently expanding the increment to implement that policy.

### WPH-007 — Major — the implementation plan still leaves choices and weakens the required gate

**Type:** planned implementation-readiness defect.

**Locations:** `design.md:126–164`; `tasks.md:176–179`, checkpoint 6 (starting `:198` in the final snapshot), final “Minimal and reversible” note; repository `AGENTS.md`; `.github/workflows/ci.yml:26`, `:139`.

The plan offers Activity versus ProcessLifecycleOwner integration, alternative API names, optional debounce narrowing, and “default/validate as needed” version parsing. It lacks one explicit Create/Modify file inventory; “four files” excludes the already-created `WorkspaceSchema.kt` and the test files and leaves dependency edits conditional. A fresh implementer must still make lifecycle and format decisions, contrary to the zero-decision standard. The final checkpoint lists JVM/lint/debug assembly but omits required release assembly. The repository's device gate also includes the independent optimized-APK `ui-tests` harness across API 28–37.0; `connectedDebugAndroidTest` alone does not replace it.

**Required next action:** update the plan to the chosen ownership/lifecycle/API contract and actual file boundaries, including tests; retain unresolved durability questions explicitly rather than choosing a hook and calling it guaranteed. Restore `testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease` and the established internal-bundle plus independent UI matrix. Keep any unavailable device run pending with exact next command/evidence requirements.

## Next actions

1. Resolve WPH-001's acknowledgement/commit contract before treating lifecycle wiring as a durability fix.
2. Correct the v1 migration and production-seam regression coverage, then make the instrumented test owners quiescent before cleanup.
3. Reconcile claims, file inventory and checklist gates; keep history-attachment retention and all device/OEM scope explicitly open.
4. Run the repository-required build/lint/JVM gates and independent API 28–37.0 device matrix on a frozen source revision. Record actual results, then re-review that revision rather than extrapolating from this live snapshot.

No trust-all TLS, cross-origin credential forwarding, telemetry, plaintext fallback, key-alias replacement or new third-party stack was observed in the reviewed changes. This is a bounded source review, not proof of those properties on a device or an approval to release.
