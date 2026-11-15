# ESuite implementation verification

Released version: **1.0.1**, version code **4**, source `2e9910360de64a0f21bc7f5830e3717e074b1215`.

- [Full CI passed](https://github.com/modhack2003/Esuite/actions/runs/43672555295): build/debug and release lint, 23 JVM passes, six publication-guard scenarios, web-concept checks and expanded inspector regressions.
- Downloaded XML confirms **50 native cases: 38 passes, 12 explicit capability skips, zero failures/errors**. Every API 28–37.0 job passed. A skipped capability is not a browser pass.
- [Production release workflow passed](https://github.com/modhack2003/Esuite/actions/runs/43673437159): signer/package/version/SDK/signature/ZIP/ELF verification and exact signed-APK installation/relaunch, UI and cross-version upgrade checks on API 28/35/37.0.
- [Stable release](https://github.com/modhack2003/Esuite/releases/tag/v1.0.1) is published. Public APK/metadata/notes/checksums were independently downloaded; checksums, byte equality with the verified signing artifact, certificate and source metadata matched.

| Release property | Verified value |
| --- | --- |
| APK | ESuite-1.0.1.apk |
| Size | 2,473,537 bytes |
| Package | dev.esuite.app |
| SDK | minimum 28; target 37; no maximum |
| Debuggable | false |
| SHA-256 | `b3ec925bb4d73f477947dc761a5c960add671c7dec53cc9c4b4ecb11587ba8d0` |
| Certificate SHA-256 | `55800cce75d814b64a1f9663463da2f0521cd0da55541ff56449f5a94fb88e12` |

Later documentation-only commits do not change the tested/released application source. Published v1.0.0 assets remain immutable; the same production signer is retained.

## Unreleased follow-up validation

Four-list task inputs, sparse/missing-list and budget regressions, selected-HTML truncation metadata and evaluation truncation notices are implemented. The optimized-APK UI suite now exercises all four inputs, missing P4 validation and both inspector limits. Latest CI before editing was successful run 43672555295; new checks are pending and must not be confused with the published 1.0.1 evidence below. The future signed upgrade fixture pins 1.0.1; no subsequent signed upgrade is claimed yet.

## Android matrix — optimized APK

| Android | API | Native passes | Skips | Observation |
| --- | --- | --- | --- | --- |
| 9 | 28 | 3 | 2 | Isolated browser profiles unavailable in installed provider |
| 10 | 29 | 3 | 2 | Isolated browser profiles unavailable in installed provider |
| 11 | 30 | 3 | 2 | Isolated browser profiles unavailable in installed provider |
| 12 | 31 | 3 | 2 | Isolated browser profiles unavailable in installed provider |
| 12L | 32 | 5 | 0 | Complete browser/API/persistence checks passed |
| 13 | 33 | 3 | 2 | Isolated browser profiles unavailable in installed provider |
| 14 | 34 | 3 | 2 | Isolated browser profiles unavailable in installed provider |
| 15 | 35 | 5 | 0 | Complete browser/API/persistence checks passed |
| 16 | 36 | 5 | 0 | Complete browser/API/persistence checks passed |
| 17 | 37.0 | 5 | 0 | Complete browser/API/persistence checks passed |

Every OS passed actual Android Keystore/AtomicFile recovery/tamper boundaries, real HTTPS API sends/comparison/collection/error-state controls and project/observation encrypted-save/process-restart persistence. Two browser cases skip on unsupported providers: the internal profile boundary test and the independent optimized-APK browser UI flow.

On API 32/35/36/37.0, the actual UI harness loaded HTTPS example.com with normal system certificate validation, inspected/touched an element, replaced HTML then edited/read CSS, used same-origin anchors, read storage, set/read/hid/revealed an HttpOnly cookie, verified page JavaScript could not read it, transferred cookies to API Lab, verified A/B isolation and blocked an outside-origin form POST/navigation. Only synthetic DOM/storage/cookies are added to that public page. Browser history remains request metadata only.

## Exact production APK and upgrade continuity

| Android/API | Signed UI/upgrade passes | Skips | Result |
| --- | --- | --- | --- |
| 9 / 28 | 4 | 1 | Encrypted observation retained; API/persistence passed; browser capability unavailable |
| 15 / 35 | 5 | 0 | Encrypted observation retained; complete browser/API/persistence flow passed |
| 17 / 37.0 | 5 | 0 | Encrypted observation retained; complete browser/API/persistence flow passed |

The signed suite has **14 passes, one capability skip and no failures**. It first verifies the pinned public 1.0.0 bytes, seeds a project/observation and reads the saved note after a baseline process restart. It then installs 1.0.1 without uninstalling/clearing data and reads the same project/note in a fresh process. Screenshots/trees of both checkpoints are CI artifacts. Only after that proof does the separate fresh-fixture UI suite clear disposable emulator data. The shared NativeUiDriver has no automatic reset; ProductionUiTest owns its reset, UpgradeUiTest does not.

Publication also requires native home launch/relaunch and the release guard's certificate pin, valid signature, expected package/version/SDK, non-debuggable state, ZIP alignment and 64-bit ELF compatibility with 16 KiB pages. The guard integration accepts a correct fixture and rejects wrong-signer/version, unsigned/debug/tampered packages. Internal debug/test/CI-signed artifacts are never production downloads.

## Regression fixes and evidence limits

The patch fixes main-frame POST checks, detached selection after HTML replacement, CSS outline restoration, same-origin fragments, cookie flushing, per-tool scrolling, narrow-screen tabs, accessible bounded output, and stale successful API responses after a failed send. The touch harness waits after any scrolling lookup and requires stable usable bounds before clicking; failures are not hidden by resending network requests.

JVM tests additionally cover scope/header/body admission, actual MockWebServer replay, redirect suppression, project/identity cookies, explicit cookies, cancellation, binary/truncated responses, cookie metadata transfer, comparison/redaction, token decoding/statistics, payload modes/count caps, imports, offline AWS observations, AES-GCM tamper handling and durable-data budgets. Chromium regressions cover consumed selection clicks, picker removal, replacement retargeting, outline retention, text-only replacement and storage snapshots. The React concept remains a separate synthetic design reference.

## Remaining verification and product scope

- Emulator results do not certify every physical phone, OEM, provider update, accessibility/font-scale mode or export provider. Broader lifecycle/large-input checks remain open.
- Dead-renderer cleanup/restart has source review but has not been deliberately crash-injected by this UI suite. Account switching during in-flight tasks and broader auth/export/delete flows still need targeted UI coverage.
- WebView capability depends on the installed provider, separately from OS compatibility. Unsupported profile features disable Browser rather than silently sharing identities.
- Browser callbacks are not a complete network firewall and do not supply full request bodies/responses. Full proxy/VPN/local-CA interception, pinning bypass promises, streaming protocols and active scanner parity are not delivered.
- DOM/storage snapshots are bounded and can be partial. Browser profiles use separate platform-managed storage outside the workspace cipher; API cookie jars remain memory-only.
- Passive observations, token statistics and supplied AWS JSON review do not establish confirmed vulnerabilities or effective cloud permissions.

## Previous production baseline

[1.0.0](https://github.com/modhack2003/Esuite/releases/tag/v1.0.0) was source `98425c9e4bcc7ace1a56604b0d466859db3a8e5b`. [Its full CI](https://github.com/modhack2003/Esuite/actions/runs/43623409210) had 23 JVM passes and 30 native cases (24 passes/six profile skips); [its signed release](https://github.com/modhack2003/Esuite/actions/runs/43623797877) verified installation/relaunch on API 28/35/37.0. The 1.0.1 gate adds real browser/API UI and same-signer upgrade proof.

## Earlier checkpoints

- Core engine: https://github.com/modhack2003/Esuite/actions/runs/43580598882
- Native browser/API wiring: https://github.com/modhack2003/Esuite/actions/runs/43581714048
- Encrypted evidence: https://github.com/modhack2003/Esuite/actions/runs/43582195830
- Tasks/imports and initial native boundaries: https://github.com/modhack2003/Esuite/actions/runs/43583424635
- Crash-recovery/storage hardening: https://github.com/modhack2003/Esuite/actions/runs/43583783633

Continue from [Agent handoff](AGENT_HANDOFF.md), not from an intermediate failed CI commit or the original UI-only prototype.

## Forwarding candidate gate

Pinned recursive source/license inventory and isolated four-ABI/API 28 native build checks added; first CI execution pending. No native library has been packaged or device forwarding verified. See native/README.md for audit boundaries.
