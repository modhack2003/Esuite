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
- [ ] Superdesign output (deferred at user request; existing brief retained).

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
- [x] Pin the next upgrade baseline URL/SHA to 1.0.1 and remove the old-baseline scroll flag (signed execution pending next release).
- [ ] Extend UI coverage for renderer termination, in-flight identity changes, rich auth/export/delete flows.
- [x] Expose selected-HTML truncation metadata and a visible evaluation-output truncation notice (passed in full CI 43682611496).

- [x] Pin the forwarding candidate and transitive license hashes; add isolated API 28/four-ABI reproducibility gate.
- [x] Pass isolated candidate API 28 native builds for four ABIs, equal clean rebuilds and 16 KiB ELF alignment (direct Linux evidence in native/README.md).
- [ ] Finish distributable per-file third-party notices before native integration (CAP-01).

- [x] Fix Android ICU payload-marker initialization and verify actual four-list controls across API 28–37.0 (full CI 43682611496; 60 native cases, 12 profile-capability skips).
