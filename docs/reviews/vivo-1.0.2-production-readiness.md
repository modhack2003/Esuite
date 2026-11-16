# ESuite 1.0.2: Vivo test and commercial-readiness review

Review date: 16 November 2026. Product decision confirmed by the owner: **commercial Android app; evidence stays local**. This assessment does not propose hosted evidence, mandatory accounts, analytics, team sync or remote AI.

## Decision

The app is a working local web/API workbench, but it is **not ready for a paid production launch promising dependable evidence handling**. The physical test reproduced draft loss, technical-input alteration, dangling evidence references, large-text/landscape problems and a normal project-deletion failure. These remain open; no runtime fixes or replacement APK were made during this review.

The native UI follows the concept's dark/cyan direction, but **it is not the same UI**. It uses default Material controls, purple selected states, text-glyph icons and long scrolling forms where the concept uses grouped cards, compact editors, explicit selected tabs and clearer detail navigation. Continue the existing visual direction; a new brand or dashboard is unnecessary.

The current paid-product scope could be an honest on-device browser/API assessment workbench. Complete proxy/VPN interception, browser request/response bodies and broad active scanning remain separate unfinished capabilities. Selling the current subset must not imply those capabilities exist.

## Test boundary and evidence

| Item | Tested value |
| --- | --- |
| APK | Public production ESuite 1.0.2, code 5 |
| APK SHA-256 | `15096289a6c282d60035e44c3ac4a1f00cfba30a73780f1961261487aff2a1b4` |
| Released source | `2fc166dca9d2fa1637f4e8ef15e51040d3cabe5b` |
| Documentation baseline | `2ae3162298758855a1ec1bc8a435a005a7d63f6e` |
| Latest CI independently read | [43831951202, success](https://github.com/modhack2003/Esuite/actions/runs/43831951202) |
| Physical phone | Vivo I2018, Android 13 / API 33 |
| WebView | `com.google.android.webview 156.0.8078.47`; isolated Browser profiles available |
| Display | 1080 x 2400, density 440; original font scale 1.0 |
| Additional layout checks | Font scale 2.0, soft keyboard, landscape; original settings restored |
| Continuous log window | 14:49:11–15:19:37 IST; cleanup diagnostics collected afterward |
| Fixture | Dedicated QA project scoped to `https://example.com`, synthetic header/cookie/note |

The first continuous log contains 14,997 lines. App log checkpoints and the continuous capture contained no detected app fatal exception or ANR. This is bounded session evidence, not a claim that all error paths, memory conditions or OEMs are safe. A cumulative graphics snapshot reported 2,305 frames and 175 janky frames (7.59%); it includes automation, keyboard, font and rotation changes and is **not a controlled performance benchmark**.

Raw logs, screenshots, hierarchy XML/JSON, test helpers and the local comparison gallery remain under ignored `app/build/device-logs/vivo-audit-20261116-144911/`. They can contain unrelated device messages and pre-existing project context. They were not committed or uploaded to a design service. This report contains the nonsecret conclusions and fixture references only.

No uninstall, app-data clear, key deletion or destructive recovery test was run on the user's workspace. Only the QA project's history and project were deleted. Final screenshots verify that the QA project is absent and the original project is selected with its original 11 history entries. Browser deletion acknowledged success after a cold restart; on-disk forensic erasure was not verified.

## Physical test results

These are targeted manual/ADB checks, not executions of the CI instrumentation suite on the user's phone. The CI suite's destructive fixture reset was deliberately not used.

| Check | Result | Private evidence file stems |
| --- | --- | --- |
| Create scoped QA project; encrypted-save acknowledgement | Passed | `04`, `29`, `30` |
| API GET over normal HTTPS | Passed: HTTP 200; one result reported 2,474 ms | `08-api-get-result` |
| Pending API headers/body through module switch | Failed: header lost, body retained | `05`, `07`, `draft-navigation-result.json` |
| Browser page painting and dark inspector | Observed working at normal portrait size | `09`, `10` |
| Cookie read masking | Passed for synthetic cookie A | `14-cookie-A-masked` |
| Browser B isolation | Synthetic A cookie absent from B | `15-cookie-B-isolation` |
| Browser-to-API transfer | Explicit A transfer acknowledgement observed; receipt on a subsequent server request was not tested | `16-cookie-A-transfer` |
| Technical cookie/JavaScript entry | Failed: IME altered identifiers | `13`, `19`, `20` |
| Pending finding title/detail through module switch | Failed: both became blank | `23`, `24` |
| Traffic selection/detail discovery | Detail begins below rows; content/action below the fold even with five entries | `25`, `26`, `27` |
| Saved linked finding through process restart | Passed after saved acknowledgement | `28`–`32` |
| Referenced evidence after QA history clear | Failed: dangling ID still presented without missing-evidence state | `32`, `33`, `34` |
| Base64 utility | Passed: `hello` becomes `aGVsbG8=` | `35-tools-base64` |
| Large font | Failed: Browser panel labels visibly clip; navigation labels break awkwardly | `37-browser-large-font` |
| Large font with IME; normal-font landscape | No usable visible Page viewport in captured layout | `38`, `39` |
| Out-of-scope Browser URL | UI rejected `https://example.org/` under the example.com project | `41-out-of-scope-browser-rejection` |
| Sanitized export preview | Opened; correctly disclosed omitted free text/bodies/paths; cancelled before file creation | `42-sanitized-export-preview` |
| Normal QA project/profile deletion | Failed after Browser use; succeeded after cold restart without reopening Browser | `44`–`49` |

Storage large-value truncation was reviewed in source. A planned Console seed was stopped when the IME changed `localStorage`; no physical Storage truncation pass is claimed. Delayed identity completions, renderer-crash injection, TalkBack, real export-provider writes, backup restoration, long task cancellation and broad performance/privacy/OEM matrices remain untested in this session.

## Issues and acceptance criteria

Priorities describe product-release urgency, not vulnerability severity. P0 is a release blocker for the stated evidence-preservation promise. P1 should be fixed before a paid beta; P2 is the next usability/completeness increment. Status distinguishes physical reproduction from source-only risk and planned capability.

### QA-01 — P0: findings do not retain their evidence

**Physically reproduced; additional pruning paths confirmed in source.** Save a finding linked to an API exchange, wait for saved acknowledgement and restart. The note survives. Clear the QA project's history: Traffic is empty, while Findings still displays the same evidence ID without indicating it is missing (`32`–`34`).

`core/Models.kt:19–22` stores IDs rather than evidence snapshots. `AppViewModel.kt:135` caps history globally at 100, `core/WorkspacePersistence.kt:15–16` prunes it further, and `AppViewModel.kt:337` clears project history. `MainActivity.kt:214–215` displays IDs without a working attachment viewer or availability check.

**Fix/acceptance:** keep bounded durable snapshots or pin referenced exchanges independently from disposable history. Reject additions at capacity rather than silently dropping retained evidence. Warn before intentional attachment deletion and show explicit missing/expired state afterward. Verify history clear, rollover from other projects, low-heap pruning and restart; open the saved request/response from its finding.

### QA-02 — P1: pending drafts disappear during ordinary navigation

**Physically reproduced.** Headers were `X-ESuite-QA: retained-header` before visiting Browser, then empty on return; the body stayed `qa-body-pending`. Unsaved finding title and detail both became empty after visiting Traffic (`05`, `07`, `23`, `24`).

`MainActivity.kt:168,172,175–177` stores headers in screen-local `remember` while URL/body update the ViewModel. Finding drafts use screen-local state at line 203; Tools and workflow inputs have similar lifetimes at line 221 and `ui/WorkflowsScreen.kt:15–18`.

**Fix/acceptance:** retain pending fields per project/editor in appropriate in-memory state across module switches and configuration changes. Do not solve this by automatically persisting every secret input. Explicitly define process-death behavior and unsaved-change handling. Test all editor fields, finding drafts, payload lists and imports.

### QA-03 — P1: the keyboard can change technical input

**Observed on the physical entry path, independently checked by the UI reviewer.** With active Gboard and injected normal key events, `SameSite=Lax` became `Same site=Lax`; `localStorage.setItem(...)` became `local storage.setItem(...)`. The altered text was visible before execution (`13`, `19`, `20`). This is input alteration, not proof of a cookie or JavaScript engine defect.

The cookie and Console fields at `browser/BrowserScreen.kt:146,165` use default keyboard options. The inspected native technical editors specify no explicit `KeyboardOptions`.

**Fix/acceptance:** disable autocorrection and capitalization in code/header/cookie/URL/payload editors, choose suitable technical keyboard options, and use monospace presentation. Evaluate no-personalized-learning behavior for sensitive fields. Verify the actual Vivo/Gboard path, manual typing, paste and hardware keys with exact cookie attributes, JavaScript, JSON, cURL, headers and Unicode bodies; submission must preserve the entered bytes.

### QA-04 — P1: Browser does not adapt to large text or short heights

**Physically observed.** At font scale 2, Elements/Cookies/Storage/Console visibly clip (`37`). With the keyboard open there is no usable visible Page area (`38`). In normal-font landscape, Go ends at y657 and the panel row starts at y657, leaving no visible page space (`39`). This was an unloaded Page: the test does not claim that a loaded website vanished. The page node itself was absent from the hierarchy, so collapsed available height is inferred from adjacent bounds and the weighted layout.

`browser/BrowserScreen.kt:68–80,126–127` combines fixed rows with a weighted page and five equal-width nonwrapping labels. The bottom navigation also breaks labels awkwardly at doubled text size.

**Fix/acceptance:** use adaptive compact controls, a readable scrollable/adaptive panel selector and an explicit way to reveal the page when height is constrained. Preserve user text scaling. Validate portrait/landscape/IME at supported widths and font sizes, complete labels, safe insets, reachable controls and minimum 48 dp touch targets. Android's [accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/apps) provides the touch-target baseline.

### QA-05 — P1: old responses can be attributed to a new identity context

**Source-confirmed race; not physically exercised with a delayed endpoint.** `AppViewModel.kt:124–128` permits an A-to-B switch during sending; completion at lines 157–159 still assigns A's response to `selected`. The current header shows B, and `MainActivity.kt:185` does not label the response's identity. Runner/sequence completions have the same context concern at `AppViewModel.kt:188–189,220–221`.

**Fix/acceptance:** guard result selection against project/identity/generation changes or explicitly display immutable originating project/identity/request context. Do not label this as cookie leakage: none was established. Add deterministic delayed success/error/cancel and identity-switch tests, including tasks.

### QA-06 — P1: critical status and operative target context are hidden

**Physical UI plus source.** Browser inspector screenshots show project name/A/B and a prominent provider version but omit the loaded/lookup URL. Save/error status exists at the top of other long pages and is absent from live Browser (`MainActivity.kt:80–104`). Transfer failure can direct the user to workspace status that this screen does not show (`BrowserScreen.kt:155`). Deletion's actual error was only discovered by scrolling back to the top (`47`).

**Fix/acceptance:** keep a compact project/origin/identity and pending/failed-save status visible where it matters. Identify whether cookie operations use the address field or loaded page. Show local action success/failure adjacent to the action and in an accessible status region. Move provider/version detail to diagnostics rather than consuming a full primary row.

### QA-07 — P1: Traffic selection has no usable detail transition

**Physically observed with five entries; source permits 50 before details.** Selecting an exchange adds its detail below all rows, with no focus or scroll transition (`26`). `MainActivity.kt:154–159` places the detail and transfer action after `entries.take(50)`.

**Fix/acceptance:** open a detail destination/sheet or scroll/focus it immediately, preserving list position on Back. Keep source, identity, request/response completeness and truncation visible. Test selection at the beginning/end of 50 rows. Never display reconstructed or metadata-only browser requests as complete captures.

### QA-08 — P1: profile cleanup can fail after normal Browser use

**Physically reproduced twice.** Leave Browser for Home and delete the QA project. The dialog closes but the project remains; top status says `Cannot delete in-use profile ..._A` (`47`). After a cold restart, without reopening Browser, the same QA deletion succeeds (`48`). The original project is restored in `49`.

`browser/BrowserProfiles.kt:8–14` attempts synchronous deletion after view cleanup; `AppViewModel.kt:311–319` aborts project deletion on failure. Destroying WebViews alone does not cover the full SDK contract: [ProfileStore.deleteProfile](https://developer.android.com/reference/androidx/webkit/ProfileStore#deleteProfile(java.lang.String)) also rejects profiles loaded into memory through profile access APIs. The API can schedule disk cleanup asynchronously, so UI acknowledgement is not forensic erasure proof.

**Fix/acceptance:** design a supported loaded-profile cleanup/deferred-deletion lifecycle, retain pending cleanup records and offer a clear retry/restart path. Report partial failures beside the action; do not pretend deletion completed or discard the project index first. Test A/B usage, leaving Browser, deletion and cold-restart completion on this provider and the supported matrix.

**Related source-only recovery gap:** unreadable-vault recovery cannot access project deletion, while `eraseVault()`/`WorkspaceVault.erase()` delete only the encrypted vault/key (`AppViewModel.kt:92–100`, `storage/WorkspaceVault.kt:51–53`). Provide independently reachable cleanup of ESuite-owned profiles, including after corrupt/missing-key recovery. Do not delete unrelated/default profiles. This destructive recovery path was not attempted on user data.

### QA-09 — P2: Storage snapshots truncate fields without identifying them

**Source-confirmed; physical large-value test blocked by QA-03.** `browser/InspectorScripts.kt:50–53` returns at most 100 keys and 4,096 characters per value without original sizes or truncation flags. The later generic display cap cannot identify information already discarded inside the snapshot.

**Fix/acceptance:** return total/returned key counts, original value lengths and explicit per-field truncation indicators. Offer bounded selected-value retrieval where needed. Test 101 keys and values of 4,096/4,097 characters; a partial token must never look complete.

### QA-10 — P2: native selection, typography and iconography drift from the concept

**Visually confirmed.** Browser panel buttons all look selected alike and have no tab selected semantics (`BrowserScreen.kt:126–127`). The native Material color scheme leaves selected navigation/chips purple (`ui/Theme.kt:17–21`); `MainActivity.kt:50–51,76` uses Unicode/text symbols. Technical messages are proportional text. The concept has cyan active treatments, consistent vector icons and monospace content.

**Fix/acceptance:** complete the existing Material token mapping, adopt the actual brand/vector icons, use code typography and clearly selected accessible tabs. Keep compact tool grouping rather than copying the prototype's tiny text sizes. Verify native screenshots and TalkBack; a web screenshot does not establish native accessibility.

### QA-11 — P1 product gap: report export and local recovery are insufficient for paid use

**Disclosed behavior, not a secret export bug.** The export preview correctly says it omits bodies/paths/header values and finding free text (`42`). `core/ReportExporter.kt:11–17` produces a low-disclosure summary, not a reproducible assessment report. Android backup is disabled and the vault key is device-local; no portable local restore flow exists.

**Add/acceptance:** distinguish (1) sanitized summary, (2) selected evidence report with field-by-field redaction preview, and (3) optional user-triggered encrypted local backup/restore. Define password/key handling, capacity, corrupt backup handling and clean-install restoration. Alternatively disclose a deliberate no-recovery policy before users depend on the app. Keep all destinations explicitly user selected and device-local under the current contract.

### QA-12 — P1 readiness gap: running tasks lack shared progress and immediate stop

**Source/UI structure; no 50-request run was executed on the public example target.** `ui/WorkflowsScreen.kt:21–38` separates progress from Cancel by four lists and a sequence form; other modules expose no shared running-task control. This overlaps the existing UI-01 backlog.

**Fix/acceptance:** keep task origin, identity, count and Cancel visible across modules. Use a controlled endpoint to verify cancellation during long responses, switching identities and background transitions, without retries or duplicate state-changing sends. Retain current rate/count caps.

### QA-13 — P2: active context and specifications need reconciliation

**Physical/source.** Restart reopened the first existing project instead of the active QA project (`30`, `31`; `AppViewModel.kt:52–53`). Define whether to restore a nonsecret last-used project/screen or show a deliberate project picker. Do not silently restore unsaved secret drafts under another identity.

`docs/UI_SPEC.md` still says native Findings exists only in the web concept in one paragraph, while its later implementation section and shipped navigation contradict that. Define native acceptance for pending drafts, detail navigation, task controls and adaptive layout; reconcile historical starter text without removing planned scope.

## Commercial product additions and order

1. **Correctness increment:** fix QA-01/02/03/05/08 and add meaningful regression tests through actual ViewModel, provider and retained-evidence boundaries. These matter more than another dashboard. Preserve the production signer and encrypted-format migration compatibility.
2. **Native usability increment:** QA-04/06/07/10/12/13. Keep the established palette and navigation; introduce compact shared context/status, editor sections, proper detail navigation and adaptive tab controls. An About/Diagnostics area should own build/provider/support information.
3. **Deliverable work product:** QA-09/11, searchable evidence with working attachments, report preview/redaction and explicitly local recovery. Add optional app lock/screen protection and clear session/retention controls appropriate for sensitive evidence.
4. **Commercial release contract:** define the first paying audience, exact supported workbench workflow, distribution, price/entitlement model, offline behavior, updates/refunds/support, privacy statement, compatibility matrix, license notices and release rollback policy. A store-paid app and a direct-sale distribution are alternatives to evaluate, not implemented decisions. [Google Play's pricing guidance](https://support.google.com/googleplay/android-developer/answer/6334373?hl=en) documents paid-app setup. In-app purchases/subscriptions introduce separate purchase-lifecycle and entitlement responsibilities; Google's [Billing guidance](https://developer.android.com/google/play/billing) recommends secure backend verification. Do not silently add that infrastructure or send target/evidence data with purchase metadata.
5. **Privacy and support:** provide on-device diagnostics with explicit redaction/preview and user-controlled sharing; never automatic raw log/evidence upload. Prepare accurate store declarations and an in-app privacy policy against the actual data flows, including WebView and any future payment components. See the official [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en).
6. **Measured release gates:** zero unresolved P0/P1 workflow defects, exact-source CI and signed upgrades, real attachment survival and local restore tests, provider/OEM matrix, accessibility/IME/rotation coverage, bounded large-input performance and a controlled startup/rendering baseline. Android's [core app quality guidance](https://developer.android.com/docs/quality-guidelines/core-app-quality) is the external baseline; this single Vivo session does not close those gates.

Full interception remains CAP-01 onward: final license/notice clearance, actual on-device forwarding/capture, protocol support matrix, upstream TLS validation and honest unsupported pinning behavior. These can be a later paid capability only after their existing acceptance gates pass. Never represent the web concept's fixtures, browser metadata or API replay as complete interception.

## Review method and Superdesign status

Two EGM Goldfish reviewers ran with fresh conversation contexts (`fork_turns: none`), read the bundled design-critic persona and feature-spec standard, and returned source and UI findings. The parent performed the physical tests and synthesis; reviewers independently inspected selected new screenshots/JSON. This was a **focused review**, not the complete five-stage EGM protocol. Host instruction-file isolation was not separately proven.

Reading covered PRODUCT_PLAN, UI_SPEC, ROADMAP, AGENT_HANDOFF, VERIFICATION, ARCHITECTURE and release/license documents; actual MainActivity, AppViewModel, BrowserScreen/Profiles/InspectorScripts, Theme, WorkspaceControls, WorkflowsScreen, storage/persistence/models/export and transport code; and the actual React/CSS concept and reference screenshots. Source evidence and physical observations are distinguished above.

Superdesign CLI 0.14.0 authenticated successfully. `list-models` returned HTTP 502, then returned HTTP 502 on its one permitted retry. Its skill says to retry a failed command at most once and otherwise report the failure and stop. No project, draft, canvas result or design-service upload was produced. Six local `.superdesign/init/` context files were prepared from code/synthetic references only. Existing brand/reference work remains usable; future generation must resume through the Superdesign skill and be reviewed before native implementation.

## What this review does not establish

No complete security audit, network-egress privacy proof, billing integration, license/legal clearance, verified on-disk profile erasure, corrupt-vault recovery, physical TLS interception, broad OEM certification or full TalkBack pass is claimed. The signed 1.0.2 release remains immutable. The next concrete implementation is the correctness increment above, followed by a new version and the existing exact signed-APK release gates.
