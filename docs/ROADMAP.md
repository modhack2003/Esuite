# ESuite delivery checklist

All unchecked items are planned. A native build or a UI preview does not establish capture functionality.

Current implementation and next-agent entry point: [Agent handoff](AGENT_HANDOFF.md). Four functional increments are implemented and have passed the Android build/lint, JVM tests, native Android 15 boundary tests and web-concept checks. See [Verification](VERIFICATION.md) for the latest source/run and scope. Full proxy/VPN interception is not implemented. Do not infer native capability from the synthetic React concept.

## Foundation delivered

- [x] ESuite name, original vector identity and repository banner.
- [x] Product plan retaining phone-only, unrooted and local-processing constraints.
- [x] UI specification with navigation, flows and state definitions.
- [x] Interactive React design study with synthetic data.
- [x] Native Home, live Browser, observed Traffic, API Lab, Findings and Tools.
- [x] Local Base64, form URL and SHA-256 utilities with unit tests.
- [x] Gradle wrapper, CI definition and contributor guidance.
- [x] Canva concept board and MagicPath interaction design.
- [ ] Superdesign output (16 November: CLI authenticated; API returned 502 on initial attempt and retry; no draft produced; local source context prepared).

## M0 — prove the capture design

- [ ] Browser proxy routing and complete request/response fixtures.
- [x] Native cookie metadata inspection and Android 15 emulator test.
- [ ] Expand WebView feature/version/OEM compatibility matrix.
- [x] Separate WebView account profiles and per-project/identity API jars; unsupported profiles disable browser.
- [x] Explicit native browser-to-API cookie-jar transfer implemented; metadata fallback disclosed.
- [ ] Rootless selected-app VPN forwarding and immediate cancellation.
- [ ] Local CA trust, upstream validation and honest pinning/trust failures.
- [ ] HTTP/1.1, HTTP/2, redirects, uploads, streaming and WebSocket coverage matrix.

## M1 — manual testing workbench

- [x] Live browser DOM/CSS inspector, console and storage views (page-side and Android emulator helper tests passed; full physical UI flow pending).
- [ ] Scoped Proxy intercept/edit/forward/drop and history.
- [x] Real API execution / Repeater and per-identity cookie jars (JVM integration tests passed).
- [ ] Original/modified message provenance, Decoder and Comparer.
- [x] Local observations, sanitized export and encrypted workspace implementation (Part 3; CI/device gates tracked).
- [ ] Expand observed history into a dedicated site map and rich evidence report formats.

## M2 — version 1 completion

- [ ] App capture integrated into the UI with visible foreground state.
- [x] Bounded parameter runner and passive observations implemented; CI gates tracked in handoff.
- [x] Response/history limits, durable-data admission budget, encrypted retention and sanitized local export implemented.
- [ ] Complete process-death/OEM/export-provider and large-input performance coverage.
- [ ] Physical-device functional, privacy and accessibility verification.

## M3 — automation expansion

- [x] Four bounded substitution modes and four-list native UI implemented; sparse/missing-list validation and count preview included.
- [x] Add four-list native parameter inputs and count preview.
- [ ] Add configurable match/extract result filters and pause/checkpoint controls.
- [x] Saved-request sequences with first-response JSON token extraction implemented.
- [ ] Extend sequences with per-step extractors, refresh rules and interactive authentication handling.
- [ ] Controlled crawler and selected active audit checks.
- [ ] WebSocket replay, Sequencer and GraphQL workflows.

## M4 — advanced / cloud

- [ ] DOM instrumentation and validated raw protocol tooling.
- [ ] Local extension/rule interface with validated execution bounds.
- [x] Offline supplied AWS JSON structural review implemented.
- [ ] Native authenticated AWS/Azure/GCP account connectors and effective-permission analysis.
- [ ] Reachable-lab callback listener; no hosted OAST in local-only mode.

Detailed capabilities and acceptance criteria: [Product plan](PRODUCT_PLAN.md).

## Release delivery — Android 9–17

- [x] Configure API 28 minimum / 37 target and current supporting toolchain.
- [x] Add optimized non-debuggable release variant and independent CI UI harness against the optimized APK.
- [x] Add native project/observation/process-restart test and API 28–37.0 emulator matrix.
- [x] Implement signed GitHub Actions release workflow with certificate pin, integrity metadata and exact signed-APK smoke tests.
- [x] Pass build/lint/JVM/native Android 9–17 gates; per-version outcomes and capability skips recorded in VERIFICATION.md.
- [x] Configure durable private signer, verified local recovery copy and pinned public certificate; owner retains offline recovery responsibility.
- [x] Publish verified APK/checksums/metadata as stable GitHub release v1.0.0; exact signed APK installation/relaunch checks passed.
- [x] Verify 1.0.0 → 1.0.1 same-signer upgrades retain encrypted evidence on API 28/35/37.0.
- [ ] Complete physical/OEM/accessibility and remaining production-readiness checks above.

- [x] Publish 1.0.1 browser audit patch after actual optimized/signed-APK HTTPS, inspector, cookie/storage/transfer, API/error-state, POST-scope and same-signer upgrade gates pass. See VERIFICATION.md for exact provider skips.
- [x] Pin the next upgrade baseline URL/SHA to 1.0.1 and remove the old-baseline scroll flag (signed execution passed in the 1.0.2 release).
- [ ] Extend UI coverage for renderer termination, in-flight identity changes, rich auth/export/delete flows.
- [x] Expose selected-HTML truncation metadata and a visible evaluation-output truncation notice (passed in full CI 43682611496).

- [x] Pin the forwarding candidate and transitive license hashes; add isolated API 28/four-ABI reproducibility gate.
- [x] Pass isolated candidate API 28 native builds for four ABIs, equal clean rebuilds and 16 KiB ELF alignment (direct Linux evidence in native/README.md).
- [x] Generate pinned conservative source/header/build notice inventory (450 files), retaining original libyaml attribution and full Apache-2.0 terms.
- [ ] Complete final component/notice review and exact packaged-source attribution before native integration (CAP-01).

- [x] Fix Android ICU payload-marker initialization and verify actual four-list controls across API 28–37.0 (full CI 43682611496; 60 native cases, 12 profile-capability skips).

## 1.0.2 release acceptance

- [x] Implement bounded Page/inspector hosting, committed-snapshot save status, v1 migration, pruning reconciliation, off-main guarded imports and tail-only text decoding.
- [x] Add actual production-seam/platform/optimized-UI regressions, including visible page pattern and atomic-finish fault coverage.
- [x] Pass exact-source local/CI Android gates and all API 28–37.0 jobs (source 2fc166d; CI 43831951202; 52 JVM passes; 100 native cases, 88 passes/12 profile-capability skips).
- [x] Pass exact signed-APK install/upgrade-from-1.0.1 gates and publish 1.0.2 with the existing signer (release 43833769184; 14 passes/one profile-capability skip; public download independently verified).
- [ ] Extend deterministic ViewModel concurrency, physical/OEM/accessibility and broader lifecycle/export coverage. These remain separate from the candidate's fixture gates.

## Vivo audit and commercial local-app release gates

Evidence and exact acceptance: [Vivo 1.0.2 review](reviews/vivo-1.0.2-production-readiness.md). This targeted physical audit does not close the broader OEM/accessibility backlog.

- [x] QA-01: retain bounded finding evidence independently of disposable history; add attachment viewing/missing-evidence states. Source only — the real-Keystore retention, on-device v2→v3 backfill and prune-survival cases compile but have not run; the device matrix and the physical Vivo pass remain open below.
- [x] QA-02/03: preserve pending editor/finding state across navigation; prevent keyboard autocorrection of technical input. Source only — retention is covered by a UiAutomator `SET_TEXT` flow that does not exercise an IME at all, so QA-03's physical Vivo/Gboard byte-preservation pass (manual typing, paste, hardware keys, exact cookie attributes, Unicode bodies on submission) remains open below, and `IME_FLAG_NO_PERSONALIZED_LEARNING` is unreachable in this Compose version.
- [x] QA-05: guard in-flight result identity/context so a result landing after a project/identity/generation change is still recorded in Traffic but is not applied under the wrong identity, and the displayed response states its own project/identity/source. Source only — the mid-flight MockWebServer cases compile against a debug-only loopback cleartext exception and have not run; the device matrix remains open below.
- [x] QA-08: complete project deletion when a browser profile is still loaded, record it as pending with a retry path, sweep derived orphans after every successful load, and remove ESuite-owned profiles by name prefix without the vault. Source only — cross-process cold-restart completion on the physical Vivo and the supported provider matrix remains open below.
- [x] QA-04/06/07/10/13: implement adaptive native controls, accessible selection, Traffic details, Browser workspace status and local last-project selection. Candidate code and independent UI coverage are tracked in the [1.0.3 plan](RELEASE_1.0.3_PLAN.md). Physical viewport and TalkBack checks remain open.
- [x] QA-09/11: implement bounded Storage previews with counts and field truncation flags, plus selected, redacted observation reports. The app discloses device-local recovery limits.
- [ ] Finish QA-11 provider write coverage and verify portable encrypted backup/restore as a future feature.
- [ ] Verify QA-12 shared manual request cancellation through screen changes and delayed responses on the physical device. The optimized candidate exposes a shared Cancel for manual requests.
- [ ] Define paid local-app distribution, offline entitlement/update/support and privacy/license disclosures without hosted evidence or automatic diagnostics uploads.
- [ ] Pass fixes on the physical Vivo and supported providers, plus exact-source/signed upgrade gates, before claiming paid production readiness.

## 1.0.4 delivery

- [x] Streamline Home, API Lab, Findings and Tools. Move expensive admission and report work off the UI thread.
- [x] Record the exact-source Android matrix and signed upgrade results in the [1.0.4 release record](RELEASE_1.0.4.md).

## 1.0.5 browser refresh

- [x] Implement standalone browsing, in-app inspection, site-based saved data, and appearance and privacy settings.
- [x] Record native browser and signed upgrade results in the [1.0.5 notes](RELEASE_1.0.5.md).

## 1.0.6 browser tools

- [x] Dock developer and general tools alongside the live page, add drag resizing and support a keyboard shortcut.
- [x] Implement current-page JavaScript console, element selection and editing, source list, timing, viewport, security and document semantics panels.
- [x] Enable mobile viewport declarations and make the WebView visible once the page commits. Refresh app startup, palette, typography, and navigation.
- [x] Verify the browser changes across the Android matrix, the live portfolio rendering, and the upgrade from signed 1.0.5. [Release record](RELEASE_1.0.6.md)
- [ ] Evaluate native browser-engine support for debugger breakpoints, CPU and heap profiling, and richer network inspection.

## 1.0.7 inspection and response review

- [x] Add a searchable DOM tree, element selection and editing, computed styles and layout details.
- [x] Add a Network waterfall, request details, pause, type and status filters, and timing and transfer-size sorts.
- [x] Add on-device response checks for security headers, cookie attributes, cache policy and CORS. Make them accessible from Tools and API Lab.
- [x] Pass the ten-configuration Android matrix and signed upgrades from 1.0.6, then verify the published APK against the signing artifact. [Release verification](AGENT_HANDOFF.md#107-elements-network-and-response-checks)

## 1.0.8 browser input and inspection

- [x] Give website fields native touch focus and prioritize the focused pane while the keyboard is open.
- [x] Render Elements as a navigable HTML tree and edit current markup with stale-node checks.
- [x] Combine Network observations with source labels, filtering, timing, recording and same-origin log preservation.
- [x] Complete the optimized IME and browser inspection gates, signed upgrades and public APK verification. [Release verification](AGENT_HANDOFF.md#108-browser-input-and-inspection)


## 1.0.9 Activity and passive review

- [x] Add explicit Browser handoff, source-aware Activity and a Site Map grouped by complete observed paths.
- [x] Share passive review rules and attach explicit encrypted evidence from the selected exchange.
- [x] Add typed commands for Site Map, response review and finding creation.
- [x] Verify the optimized Android matrix, signed upgrades and the published APK. [Release evidence](AGENT_HANDOFF.md#109-activity-and-passive-review)
