# Focused Goldfish implementation-readiness review

Review window: 2026-11-15 09:27–09:30 UTC; snapshot checkpoint: **2026-11-15T09:30:29.9381719Z**. Local HEAD read during the review: `e9530076196bdac3d973aad179563464b79ae6a1`. Kiro was actively editing; this is a bounded snapshot review, not an assertion about subsequent changes. Only this report was written. No source/spec/checklist changes, build, device execution, git mutation, or full five-stage EGM review was performed.

Applied the Implementation Readiness Goldfish persona and implementation-readiness standard from the installed EGM 0.1.1 package. The standard requires concrete file boundaries, executable steps, and specific tests without unresolved architectural choices. Verdict: **not ready to execute verbatim**. The runtime defects have concrete source evidence, but several prescribed fixes/tests do not establish their stated behavior. The browser root-cause claim remains a hypothesis, not a proven source defect.

## Read inventory and CI boundary

Read in full:

- `AGENTS.md`, `docs/AGENT_HANDOFF.md`, `docs/ROADMAP.md`, `docs/PRODUCT_PLAN.md`.
- `.kiro/specs/browser-black-screen/{bugfix,design,tasks}.md` and `.kiro/specs/runtime-robustness-fixes/{bugfix,design,tasks}.md`.
- `app/src/main/java/dev/esuite/app/MainActivity.kt`, `browser/BrowserScreen.kt`, `transport/ApiEngine.kt`, `storage/WorkspaceVault.kt`, `AppViewModel.kt`, `core/ApiImports.kt`, and `core/WorkspaceBudget.kt`.
- `app/src/androidTest/java/dev/esuite/app/DeviceBoundaryTest.kt`, `app/src/test/java/dev/esuite/app/transport/ApiEngineTest.kt`, `app/src/test/java/dev/esuite/app/storage/WorkspacePreservationTest.kt`, `ui-tests/src/main/java/dev/esuite/uitests/ProductionUiTest.java`, and `ui-tests/src/main/java/dev/esuite/uitests/NativeUiDriver.java`.

Read relevant portions of `core/Models.kt` (exchange/workspace/limits), `tools/instrument_apk.py` (suite invocation and disposable-device boundary), and CI command references. Read persona `C:/Users/bikra/.codex/plugins/cache/egm/egm/0.1.1/agents/implementation_readiness.md` and standard `.../resources/document_standards/implementation-readiness-standard.md`.

Latest remote actions were read through the connected GitHub fetch tool. Latest **Build and verify** run [43682611496](https://github.com/modhack2003/Esuite/actions/runs/43682611496) completed successfully at `2026-11-15T06:03:08Z`, source `2edb9f75ad27f612d69f06816553576206014fa5`. The newer release workflow run [43683684385](https://github.com/modhack2003/Esuite/actions/runs/43683684385) reports success at `06:03:17Z` for the same source; that status alone is not a new publication claim. Neither validates the changing local hardening implementation or the six reviewed spec files. CI test counts in the handoff are historical, not results produced by this review.

## Findings

### F1 — Major: causal certainty and a compulsory failure substitute for browser diagnosis

**Type:** plan/evidence defect; black rendering was not reproduced in this review.

**Scope:** browser `bugfix.md:15–18`; `design.md:7–15,92–103`; `tasks.md:60,71–83,160`.

The design promotes scroll nesting and missing background to the structural cause, describes a particular SurfaceView/TextureView mechanism, and rules other causes out from code alone. Source establishes an outer `verticalScroll` (`MainActivity.kt:70`) and an explicitly **340 dp** AndroidView (`BrowserScreen.kt:79`); those facts do not establish that the loaded page fails to composite. The design itself says to re-hypothesize if reproduction fails (`design.md:157`), while tasks require a deterministic failure before allowing progress. That contradiction encourages manufacturing the expected result or blocking a useful investigation on a provider where the hypothesis does not reproduce.

The test inventory is also understated: `DeviceBoundaryTest.kt:199–238` is detached, but `ProductionUiTest.java:48–93` drives the actual hosted browser, real HTTPS, a WebView button click, edits, storage, cookies, and identity switching. Its missing pixel assertion is a real gap; “existing tests exercise detached WebViews only” is inaccurate.

**Acceptance / next step:** amend the plan to distinguish the reported symptom, observed reproduction, and candidate mechanisms. Capture actual hosted WebView bounds, screenshot, provider/version, OS, orientation, keyboard/font configuration, loaded-page/JS state, navigation/TLS/renderer outcomes, and rendering mode. Compare controlled hosting/background changes independently. Record non-reproduction honestly and investigate alternatives; do not require a predetermined failing test or discard the existing hosted UI coverage. Preserve the physical-device gate as explicitly pending until executed.

### F2 — Blocker: the visual oracle can pass without page rendering, and the rerun is contradictory

**Type:** proposed regression-test defect.

**Scope:** browser `design.md:140–147,175–183,222–224`; `tasks.md:76–81,120,128–139`.

The permanent oracle is “WebView region is not uniformly Midnight.” The proposed explicit white background satisfies it even when the page never paints. Border/control pixels, a loading/error surface, or a wrong crop can also pass it. It therefore cannot prove the primary requirement that the loaded page is visible.

Task 1 constructs its own nested-scroll/no-background WebView; task 3.4 demands that **that same unchanged test** pass after editing production composables. A separately hardcoded old hierarchy does not acquire the production fix. Task 3.3 then adds another standalone representation of the fixed hierarchy, which could pass while the actual app remains broken. The exact attached host, trusted fixture setup, paint readiness, sampling method, timeout, and test filename are left open.

**Acceptance / next step:** specify one regression through the real `MainActivity`/`BrowserScreen` hosting, reused before/after. Define a controlled, distinguishable page pattern or marker and assert its visible pixels inside stable, positive WebView bounds after a documented render-ready signal. Exclude app chrome and compare against **both** host and explicit WebView blank backgrounds. Include negative controls showing the oracle fails for an attached but unpainted/blank WebView and for the wrong crop. Keep helper-only tests as supporting evidence. Specify the fixture and screenshot API so debug testing does not weaken production TLS/scope. A provider skip cannot close the visual gate.

### F3 — Major: removing outer scroll has no usable overflow/keyboard/font policy

**Type:** layout implementation risk, not a verified regression in current code.

**Scope:** browser `design.md:130–141`; `tasks.md:106–125`; source `MainActivity.kt:70–89`, `BrowserScreen.kt:67–79,122–165`; product `PRODUCT_PLAN.md:191`.

“Fixed or weighted height” does not decide how the fixed header, provider/status text, URL/supporting text, Go/Stop/Back, WebView, tabs, editors, action rows, console log, and output fit in the remaining viewport. Only the final output box scrolls today. Giving the WebView weight alone cannot make the other controls fit; giving it fixed height can push the tools below a non-scrolling root. IME padding reduces available height further. Font scaling, landscape, multiline status/provider strings, and long console output can leave controls clipped or the WebView with no useful area. The stated “other screens byte-for-byte preserved” is also incompatible with moving their previously scrolling header to a fixed section without checking the resulting viewport.

**Acceptance / next step:** choose and document exact layout regions, ownership of scrolling, minimum usable WebView bounds, and overflow/focus behavior. Make inspector controls/editors/output reachable without placing a vertical-scroll ancestor around the WebView. Verify all four tabs, renderer restart, URL/Go and Stop/Back with the IME open/closed, narrow portrait and landscape, enlarged fonts, and long provider/status/console content. Assert usable visible touch bounds and in-page scroll separately from inspector scrolling; do not treat node presence as visibility. Check the existing optimized-APK flow too: `NativeUiDriver.java:47–58,102–105` currently searches by swiping the outer screen and will need an explicitly chosen way to reach any new inner viewport.

### F4 — Major: global IGNORE silently removes server-supplied malformed data

**Type:** proposed decode behavior regression; the existing split-tail replacement has source evidence.

**Scope:** runtime `design.md:133–135,170–174,278–284`; `tasks.md:124–131`; source `ApiEngine.kt:54–65`.

The cap/read/truncated logic is real and direct `String(bytes, charset)` can replace an incomplete final sequence. But `onMalformedInput(IGNORE)` and `onUnmappableCharacter(IGNORE)` apply to the **entire** captured buffer, not only its incomplete cap tail. A truncated response containing `A`, an invalid UTF-8 byte, then valid `B...` will silently lose the invalid interior byte instead of retaining the old replacement behavior. This changes non-bug inputs too: a response can be truncated at a complete character boundary while containing earlier malformed data. It is inaccurate to explain every ignored byte as an artifact of the cap.

“Any string over any supported charset equals a prefix of the original” is also too broad when the input string is not representable by that charset; a legitimate U+FFFD in server text must not be banned by the oracle.

**Acceptance / next step:** define a tail-only decoding policy that preserves the existing malformed/unmappable handling for complete/interior input and omits only the incomplete final sequence caused by truncation. One implementable direction is incremental decoder use with non-final input so incomplete trailing bytes remain unconsumed, while interior errors retain the existing replacement policy; document charset-specific handling before implementation. Test every split offset for UTF-8 and at least Shift_JIS/UTF-16, a complete boundary, interior malformed data plus a split tail, an actual server-sent U+FFFD, ASCII, exact-cap EOF, and binary/non-truncated preservation. Restrict generated prefix assertions to representable/round-trippable strings.

### F5 — Blocker: the import fix explicitly puts the expensive budget encode back on main

**Type:** concrete contradiction in the prescribed fix; current main-thread work is confirmed.

**Scope:** runtime `design.md:195–196`; `tasks.md:181–189`; source `AppViewModel.kt:208–224`, `WorkspaceBudget.kt:8–10`.

The plan validates a snapshot on Default, then calls `WorkspaceBudget.requireDurableCapacity(next)` again inside the main-thread `mutable.update`. That method always serializes the entire durable workspace and allocates UTF-8 bytes. Excluding exchanges reduces the input; it does **not** cache the result or avoid the second encode when captures occur. Consequently the proposed fix still blocks main for up to the durable budget and contradicts requirements 2.5/2.6. The current `mergeSaved` already reads `it.workspace` inside an atomic update (`AppViewModel.kt:219–222`); describing it as a stale-snapshot merge is inaccurate. The risk is introduced when validation becomes asynchronous.

**Acceptance / next step:** choose an off-main validation/commit protocol: capture a durable revision (or equivalent relevant-fields snapshot), build and validate the candidate off main, then perform only a cheap current-revision/project check and atomic apply on main. If durable state changed, rebuild/revalidate off main before retrying; preserve current capture history when committing. Specify fairness/cancellation so simultaneous edits cannot create an unbounded retry loop. Test thread identity for **every** parse and budget encode, not only the initial check; use controlled dispatcher/latch interleavings and a main-thread heartbeat rather than a device-speed timing threshold. Two concurrent imports and a concurrent saved-request/finding/config edit must never exceed count/byte budgets or lose either accepted edit/captures. Use the actual `AppState.workspace` nesting; the task's literal `current.savedRequests` is not an AppState field.

### F6 — Major: asynchronous imports need project ownership and workspace-generation rules

**Type:** new race risk created by the plan; not an assertion that current synchronous import already races this way.

**Scope:** runtime `design.md:194–198`; `tasks.md:181–196`; source `AppViewModel.kt:38–46,66–72,208–224,266–274`; `ApiImports.kt:58,78`.

An import captures project `p` before parsing. Once parsing is suspended on Default, the user can delete that project, switch projects, erase/reload the vault, or start another import. The plan only rereads current lists/counts. Parsed requests still carry `p.id`; without an ownership/generation check they can reappear as orphaned data after deletion, or be applied to a newly opened/erased workspace. A switch also needs an explicit policy: retain the original project's ownership or cancel, rather than allowing implementation accident to decide. The captured project scope must never be replaced by the new active origin.

**Acceptance / next step:** define the captured project ID/origin and workspace generation in the import operation; reject/cancel a result after deletion/erase/reload, and document switch behavior. Preserve `CancellationException` instead of treating cancellation as an ordinary import failure. Add deterministic tests pausing parsing while delete, switch, erase/reload, and a competing import occur. Assert no orphan saved requests, no resurrection of erased data, unchanged exact-origin attribution, global count/durable limits, no request execution, and persistence only of the accepted merge. Align preservation expectations with the real parser: HAR **filters** out-of-scope entries (`ApiImports.kt:73`); it does not reject an entire mixed-origin HAR with an error as some task wording implies.

### F7 — Major: reconcile lacks the saved snapshot and overstates serialization/equality

**Type:** current prune divergence confirmed; proposed concurrency contract incomplete.

**Scope:** runtime `design.md:180–192`; `tasks.md:148–170`; source `WorkspaceVault.kt:46–56`, `AppViewModel.kt:49–63,106–107`.

Returning the successfully written `bounded` workspace is appropriate. However `reconcileExchanges(persisted)` has no saved-snapshot parameter even though its algorithm needs `<save snapshot>`. Computing dropped IDs from current state minus persisted IDs would delete captures added while the save ran. The snapshot must be the exact value supplied to that vault write, not a separately read current value.

Single-parallelism storage I/O serializes vault calls, not the complete sequence of “choose snapshot → write → resume on main → reconcile.” Other saves and state changes can run between these steps; `observeBrowser` can update from WebView interception callbacks. The plan also requires exact memory/disk equality immediately after each save while explicitly preserving newer, not-yet-persisted captures. Those two assertions cannot both hold under concurrent recording. Its follow-up-write convergence proof assumes no further captures/changes and should state that boundary.

**Acceptance / next step:** make the result carry its input snapshot or explicit dropped-ID set plus workspace generation/write ordering as needed; define reconciliation against that snapshot only. Apply only after successful persistence and never replace durable lists. State the consistency model: remove IDs dropped by that save, preserve post-snapshot captures, and require memory/disk equality after writes quiesce. Test paused writes with new capture, clear history, durable edit, overlapping save paths, erase/reload, and failure. Check latest disk state and memory after draining work, no stale-generation effect, no durable loss, and a bounded number of follow-up writes for a fixed input. Specify the exact pure helper location/signature so tests exercise production logic rather than a copied halving algorithm.

### F8 — Major: runnable verification boundaries and file allowlist are incomplete

**Type:** implementation-readiness/test-plan gap.

**Scope:** browser `design.md:144`, `tasks.md:76,90,149–153`; runtime `tasks.md:88,113,205–219`; `AGENTS.md:11`.

New test files are specified as directories, and important test seams are unspecified. Runtime task 2 asks a pre-fix JVM test to invoke an intended helper that does not yet exist; task 4 asks preservation tests on that same missing helper to pass on unfixed code. A compile error is not behavioral evidence of memory/disk divergence, and it prevents observing the preservation suite. Browser tests also lack a specified activity/fixture seam (F2).

Both checkpoint commands omit `assembleRelease`, and browser/runtime checkpoints do not explicitly require the repository's independent optimized-APK UI tests across API 28–37.0. `connectedDebugAndroidTest` exercises the internal bundle; it does not replace `ProductionUiTest`. The existing UI suite has a deliberate disposable-emulator reset (`ProductionUiTest.java:19–22`), so it must not be reused unchanged on a personal physical phone. The physical gate needs a separate, non-destructive manual/fixture procedure.

**Acceptance / next step:** enumerate exact created/modified test and helper paths; define a compiling production seam for pre-fix characterization, or separate extraction from behavioral change without claiming a missing-symbol failure proves a runtime bug. Retain meaningful existing tests. Require the full Android build/lint command from AGENTS, internal tests and independent optimized-APK UI matrix with explicit capability skips, plus the separate physical browser check. Record actual outcomes per source/provider and leave unavailable gates unchecked. Do not expand this patch into unrelated visual redesign or release publication work.

## Snapshot identity

SHA-256 values below were checked at 09:29:30 and rechecked for target specs/main runtime sources at 09:30:29 UTC. Target hashes were unchanged across those checkpoints. Reads were not an atomic repository snapshot; source was changing earlier in the review (the final AppViewModel tail has durable delete writes and `onCleared` flush). These findings use the final seams checked above, not a claim that unrelated concurrent hardening is finished.

| File | SHA-256 |
| --- | --- |
| `.kiro/specs/browser-black-screen/bugfix.md` | `46BA86EECA1458A5ED0C8C093E7A31EA5732D39D007D96D4F9D036466579528C` |
| `.kiro/specs/browser-black-screen/design.md` | `AE9D5292D160C33CC5143EE85EF7C115D1D76B94B78B461175C2E60659456391` |
| `.kiro/specs/browser-black-screen/tasks.md` | `1E2B824BB7D9F6D788F5D75DAC057C5FC6D3F94AC6816795333882E9D5C68E4F` |
| `.kiro/specs/runtime-robustness-fixes/bugfix.md` | `6D575072B0B1F7CE7F6EEB3086682100C002177553A718B6AE237B37A827D90D` |
| `.kiro/specs/runtime-robustness-fixes/design.md` | `1D635BD865AFDBEDFCFF7698EB1C0B96E95AB6330D1B6311A44776224A2395EB` |
| `.kiro/specs/runtime-robustness-fixes/tasks.md` | `4987313DD6E4ACD2FA7DE70DEB77DFBE3EFDBC05A11354003CE7D6257C9F067F` |
| `app/src/main/java/dev/esuite/app/MainActivity.kt` | `ED6BBB5957AC1A5F9BF0AE178485DA8035D84A9803B56E7B2F16111537FA4259` |
| `app/src/main/java/dev/esuite/app/browser/BrowserScreen.kt` | `22A21AC26B6C3081F4BD4A0769250BD27127F2EA210F4901C1AE3B993FC3A5B9` |
| `app/src/main/java/dev/esuite/app/AppViewModel.kt` | `C5E545B09C770E35041A298785BA0875774AEB50008C518EE88743B8A27EE464` |
| `app/src/main/java/dev/esuite/app/storage/WorkspaceVault.kt` | `67FFBCBD40E629163C443DBCCF4206B379468CD461151A6B197C283F1799668B` |
| `app/src/main/java/dev/esuite/app/transport/ApiEngine.kt` | `BB9CC75CEBF8B066407619520B191EFB5E0ABDD87CAA76079A5FF6D909E4D535` |
| `app/src/main/java/dev/esuite/app/core/ApiImports.kt` | `4ED9E213B15C1AE3B150B7059781542DF54A69418A3BC053E6B20B8196FC13B0` |
| `app/src/main/java/dev/esuite/app/core/WorkspaceBudget.kt` | `26E219C363DD98317628CFA3417374EFC6946F745E9A3A6A020D0064696144CC` |
| `app/src/androidTest/java/dev/esuite/app/DeviceBoundaryTest.kt` | `B840D1122BA126363656D86E2995B47CA907432CDAB352BE3EBE3E87B9D7D90D` |
| `app/src/test/java/dev/esuite/app/transport/ApiEngineTest.kt` | `A8A6B80054580ABBE19A03F9BECE26439CAC866398B160244ECC9A9FBFCA2332` |
| `app/src/test/java/dev/esuite/app/storage/WorkspacePreservationTest.kt` | `0B71B7E75B6DEF755BF4769929AADA98CE794FDE8793BCE9BDCC29C9A20629AD` |
| `ui-tests/src/main/java/dev/esuite/uitests/ProductionUiTest.java` | `68D3EBE69122FAC0DB60B18EE644E4FCFF1A28E7997EFFA1B57B3CF42F28BC10` |
| `ui-tests/src/main/java/dev/esuite/uitests/NativeUiDriver.java` | `312CE38E792A84EF92F6A65530774B3E93950E42D8CA5C8C0CAA98031C2D1432` |

No finding establishes complete network interception, TLS-pinning bypass, or physical-device verification. Existing exact-origin/identity isolation, HTTPS-only trust validation, local-only processing, encryption, MIT licensing, and unfinished product scope remain constraints throughout these changes.
