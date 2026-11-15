# ESuite — next-agent handoff

Read this file first, then `AGENTS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md` and the relevant source. Continue the implementation; do not restart branding or design.

## Product contract

- Native Android, unrooted phone, no computer during use. Runtime processing and saved evidence remain on-device.
- User authorized incremental implementation and pushes to `modhack2003/Esuite/main`. Make coherent commits and keep this checklist current.
- HTTPS-only runtime: automatic approval review rejected global cleartext HTTP. Do not reintroduce globally permitted cleartext or trust-all TLS. HTTP MockWebServer fixtures are JVM-only.
- No analytics, hosted AI, remote workers, automatic evidence uploads or external callback collectors. The design services received synthetic data only.
- The React prototype is a synthetic design reference. Native features must be assessed from Kotlin source and tests.

## Current production checkpoint — 1.0.1/code 4

[Stable 1.0.1](https://github.com/modhack2003/Esuite/releases/tag/v1.0.1) is published from `2e9910360de64a0f21bc7f5830e3717e074b1215`. [Full CI](https://github.com/modhack2003/Esuite/actions/runs/43672555295) passed all ten OS jobs; independently downloaded XML confirms 23 JVM passes and 50 native cases (38 passes, 12 explicit profile-capability skips, zero failures/errors). Complete browser UI flows passed on compatible API 32/35/36/37.0 providers; API 28/29/30/31/33/34 each skipped two profile-dependent cases and passed API/persistence/Keystore checks.

[Signed release workflow](https://github.com/modhack2003/Esuite/actions/runs/43673437159) passed certificate/integrity/metadata/16 KiB checks and actual signed-APK UI/upgrade gates on API 28/35/37.0: 14 passes, one browser-capability skip, no failures. The same encrypted observation is read after a v1.0.0 process restart and again after installing the update without clearing data. Public APK/checksums/metadata/notes were independently downloaded and matched the verified signing artifact/certificate/source.

APK: ESuite-1.0.1.apk, 2,473,537 bytes, SHA-256 `b3ec925bb4d73f477947dc761a5c960add671c7dec53cc9c4b4ecb11587ba8d0`. The durable production certificate is unchanged and recorded in RELEASING.md. Later documentation-only commits do not change this released source. Never replace published 1.0.0 or 1.0.1 assets.

Fixed and regression-covered: initial main-frame POST rejection, same-origin fragments, HTML replacement selection, CSS outline restoration, native cookie read/reveal/HttpOnly transfer and A/B isolation, bounded/accessibly exposed output, narrow-screen tabs, per-tool scrolling, and actual failed API response/identity selection. Source also handles cookie flushing, provider diagnostics and dead-renderer cleanup/restart; deliberate renderer-crash/OEM/accessibility coverage remains open.

Harness lessons: a lookup may scroll; wait AFTER it, verify stable usable bounds and reacquire before touching. Direct scroll semantics on Text hid its accessible text; scroll a separate container. NativeUiDriver is shared and has NO @Before data reset. Only ProductionUiTest clears disposable app data; NEVER add a reset to the base or upgrade suite. UpgradeUiTest must scroll to the saved note and prove baseline durability before updating. Its center-swipe compatibility flag is for v1.0.0's old padding/viewport layout. Per-failure and before/after screenshots/trees contain synthetic data and are CI artifacts. AVD heap is explicitly 512 MiB after a Google-services update crashed system_server at 16 MiB. Never hide failures by retrying non-idempotent network sends.

The unreleased follow-up below updates the pinned upgrade baseline to 1.0.1 and removes the legacy-scroll flag. Follow CAP-01 onward below. Physical/OEM/TalkBack/font scaling, renderer crash, in-flight identity switching, richer auth/export/delete, migrations and performance remain open. Keep all unchecked scope and acceptance gates below.

## Unreleased follow-up — task controls and bounded inspector output

- Four payload lists are exposed in native Tools, with exact P1–P4 positions, missing-list errors, sparse-list support and the existing 50-request/rate/cancel bounds. Broadcast explicitly uses P1. Preview clears when inputs or the API draft change. Filters, pause/checkpoints and per-step extraction remain RUN-01/RUN-02 work.
- Inspector selection reports HTML length/limit/truncation; evaluation output puts a truncation notice before its 24,000-character prefix. JVM four-list regression cases, page-side large-selection assertions and actual optimized-APK control assertions were added.
- Future signed upgrade checks now pin the published 1.0.1 URL/SHA and use its normal scroll viewport. The obsolete v1.0.0 driver flag is removed. This does not constitute a new signed upgrade run or a new release.
- Verification: local Android checks started; CI result pending. Production remains 1.0.1. Next: obtain passing exact-source CI/device results, then continue CAP-01 license/build selection. Physical/OEM/accessibility and all unchecked scope below remain open.

## CAP-01 candidate build gate (unreleased)

The recursive forwarding candidate and transitive license/build review is recorded in `native/README.md`; exact revisions and license hashes are in `native/forwarder-lock.json`. `tools/verify_forwarder.py` checks API 28 builds across four ABIs, 16 KiB ELF alignment and equality of two clean stripped builds. Direct Linux build pending; the connected GitHub credential cannot publish a new workflow because it lacks workflow scope. Nothing is packaged into the app. CAP-01 remains open until actual build evidence and a complete distributable notice inventory are recorded; CAP-02/CAP-03 runtime transport/forwarding are untouched.

## Implemented increments

Earlier production baseline: `98425c9e4bcc7ace1a56604b0d466859db3a8e5b`, ESuite **1.0.0/code 3**. [Full CI](https://github.com/modhack2003/Esuite/actions/runs/43623409210) passed all jobs; downloaded XML confirms 23 JVM passes and 30 native cases (24 passed, six unsupported-WebView-profile skips, zero failures/errors). [Signed release workflow](https://github.com/modhack2003/Esuite/actions/runs/43623797877) passed certificate/integrity/metadata checks and installed/relaunched the exact production-signed APK on API 28, 35 and 37.0. [Stable release](https://github.com/modhack2003/Esuite/releases/tag/v1.0.0) is published. Public download checksums/certificate/source metadata were independently verified. Later documentation-only commits do not change the released application source.

| Part | Working behavior | Validation reference |
| --- | --- | --- |
| 1 — core | Exact-origin scope, real cancellable HTTP engine, no redirects/retries, independent project/identity cookie jars, capped text/binary response capture, passive observations and comparison | https://github.com/modhack2003/Esuite/actions/runs/43580598882 |
| 2 — browser / API UI | Compatible isolated WebView profiles, native cookie read/set/clear/explicit transfer, touch element selection, local HTML/CSS edits, console, storage, request metadata history, native API execution and A/B comparison | https://github.com/modhack2003/Esuite/actions/runs/43581714048 |
| 3 — local evidence | Keystore-backed AES-256-GCM workspace, AtomicFile, request collections, configured identity headers, observations, sanitized local export, project/profile deletion | https://github.com/modhack2003/Esuite/actions/runs/43582195830 |
| 4 — tasks / imports | Four payload substitution modes, bounded/cancellable execution, saved-request sequences, first-response JSON token extraction, cURL/raw HTTP/OpenAPI 3 JSON/textual HAR imports, offline AWS JSON review | https://github.com/modhack2003/Esuite/actions/runs/43583424635 |
| Native boundary tests | Android 15 emulator: actual Keystore round-trip and tamper rejection; separate WebView cookies including HttpOnly/native visibility; page inspector execution. Two tests passed, none skipped | https://github.com/modhack2003/Esuite/actions/runs/43583424635 |
| Storage hardening | AtomicFile backup recovery, missing-key failure without replacement, durable-data admission budgets and collection/observation removal | Passed including native backup recovery: https://github.com/modhack2003/Esuite/actions/runs/43584287247 |

## Release engineering — first production-signed APK delivered

- Build: min API 28 / target 37 / compile 37.0, AGP 9.2.1, Gradle 9.4.1/checksum, JDK 17, Kotlin 2.2.10. Optimized release is non-debuggable; no debug-key signing fallback. Version intent 1.0.0/code 3.
- Official Command-line Tools 22.0/build 15859902 and setup-android v4 fixed API 37's AVD metadata/SurfaceFlinger failure. API 37 uses software rendering; emulator data 8 GiB / RAM 4 GiB. All ten OS jobs passed at the checkpoint above.
- Two platform tests run in internal debug fixtures, followed by the actual optimized APK with a disposable CI signer and an independent ui-tests APK. UI Automator creates a scoped project, saves an observation, waits for encrypted save completion, force-stops/reopens the process and verifies evidence. No app-class dependency or broad production R8 keep rules are needed. Do not resurrect the obsolete minified-instrumentation/.validation setup.
- Six native browser tests skipped where the system WebView did not advertise MULTI_PROFILE (API 28/29/30/31/33/34). API 32/35/36/37 profile/cookie/inspector tests passed. Every OS passed Keystore/tamper and optimized-APK process-restart UI checks. Browser capability depends on provider version, separately from OS support.
- Source/guard checkpoint includes 23 JVM tests and six release-guard scenarios (valid signed/pinned package accepted; wrong signer/version, unsigned/debug/tampered packages rejected). Guards check signature, certificate pin, package/version/SDK, non-debuggable state, ZIP alignment and 64-bit ELF 16 KiB alignment. Raw reports/screenshots remain CI artifacts with finite retention.
- Owner authenticated GitHub CLI through Remote Desktop Commander. Confirmed no existing production key configuration/releases, generated a durable RSA-3072 PKCS12 signer, verified a local recovery copy, and configured all four repository signing secrets plus public certificate variable. Never record private-key paths/passwords/device identifiers in this public handoff. The public fingerprint is in docs/RELEASING.md. Reuse the signer for every update.
- Every runner is pinned to Ubuntu 24.04 after the actual ubuntu26.04 Playwright failure. Passwords are scoped to restore/Gradle steps after SDK/tool setup. Native screenshot review found black system icons over the always-dark theme; explicit light icons now passed full CI and signed-APK checks.
- Release workflow requires passing exact-commit main-push CI, rebuilds/signs using the durable private key, verifies the certificate and APK, installs/relaunches the exact signed APK on API 28/35/37.0, then publishes a normal stable GitHub Release with checksums/build-info/notes. Existing published versions are immutable. Internal debug/test/CI-signed artifacts are never production assets.
- API 37 tooling issue resolved: emulator-runner selected the old preinstalled cmdline-tools/latest (12.0) despite setup-android installing 22.0. tools/pin_android_tools.py preserves the old directory in RUNNER_TEMP and points latest to verified 22.0 on disposable CI runners only. Full matrix and signed-APK checks passed with this fix.
- Production artifact: ESuite-1.0.0.apk, 2,457,153 bytes, SHA-256 `020ff3d307ded1484393075fd1688cd9b9099726c4928fff34695e43c486952e`; public certificate pin in docs/RELEASING.md. Private recovery material stays on the owner's authorized device with owner-only permissions; a verified local keystore copy exists. Owner must retain an offline keystore backup and its password separately. The subsequent 1.0.1 checkpoint above now verifies cross-version continuity from this baseline.
- The current 1.0.1 audit/upgrade checkpoint is above. Next implementation work follows CAP-01 onward below. Physical/OEM/accessibility verification and broader data lifecycle/UI/API flows remain open. For the next release increment both versionName/versionCode, update notes, retain the same signer and run all gates; never replace published v1.0.0 assets. Full proxy/VPN/TLS interception and provider-account scope below is not complete.

## Next tasks in dependency order

| ID | Priority | Task | Acceptance gate |
| --- | --- | --- | --- |
| CAP-01 | P0 | Select a permissively licensed Android userspace forwarding stack. Inspect actual transitive licenses and pin upstream revisions. `heiher/sockstun` is only a candidate, not an integrated dependency | Reproducible Android build; license inventory; no silent MIT-to-GPL change |
| CAP-02 | P0 | Implement loopback proxy transport, scoped routing and actual request/response provenance | Controlled tests for redirects, uploads, duplicate headers, binary bodies, compression and cancellation; distinguish normalized messages from wire bytes |
| CAP-03 | P0 | Implement selected-app VpnService with userspace TCP/UDP/DNS and IPv4/IPv6 forwarding, foreground status, consent, conflict handling and immediate stop | Real unrooted device tests; preserve connectivity; no empty TUN service that blackholes traffic; socket protection prevents loops |
| CAP-04 | P0 | Add local CA generation/protection, explicit installation/trust flow and scoped TLS interception where supported | Upstream certificate validation; per-install key; pinned/non-trusting apps retain honest metadata-only/failure states |
| UI-01 | P1 | Replace long native tool pages with editor tabs, drill-down screens, lazy history rendering and landscape comparison | Portrait/landscape, font scaling, TalkBack, soft keyboard and full browser-to-finding flow on a physical phone |
| UI-02 | P1 | Add native interaction tests for project creation, cookie-to-API transfer, auth configuration, findings and export/delete flows | Exercise actual Compose controls, not only helper APIs; account-switch body/header behavior is explicit |
| DATA-01 | P1 | Expand Android/OEM backup, profile-deletion, process-death, interrupted-save and export-provider coverage | Recovery preserves prior data; failures never silently reset state; no private evidence leaves to remote document providers |
| DATA-02 | P1 | Move large import/budget validation off UI thread with version-safe state updates; add migrations | Bounded memory, no lost concurrent captures, unsupported versions fail closed |
| RUN-01 | P1 | Four-list task UI, configurable result match/extract filters, pause/checkpoint controls | Respect budgets/cancellation; never replay uncertain non-idempotent sends automatically |
| RUN-02 | P1 | Per-step macro extraction, CSRF refresh and explicit interactive login handoffs | No unattended MFA/passkey/CAPTCHA promise; session-isolation tests |
| AUTH-01 | P1 | Explicit multi-origin login allowlists, preserving per-origin credential boundaries | Cross-origin redirects never forward credentials without an approved scope/configuration; auth fixtures |
| API-01 | P1 | Multipart/file upload, YAML/OpenAPI references/schema scaffolds, more cURL syntax and HAR variants | Explicit supported subset; parser never invokes shell; imports do not execute requests |
| API-02 | P1 | Supported WebSocket/SSE/gRPC workflows and protocol matrix | No claim that an HTTP body cap equals complete streaming capture |
| SCAN-01 | P1 | Controlled crawler and selected active checks with baselines and reproducible evidence | Scope/rate/cancel controls; test fixtures; no reflection/timing/error-only confirmed-vulnerability claims |
| CLOUD-01 | P2 | Direct AWS/Azure/GCP account connectors, imported policy variants and effective-permission context | Scoped credentials, local analysis, provider fixtures and accurate resource attribution |
| EXT-01 | P2 | Declarative local rule packs, later a bounded script/plugin interface | Enforced resource/access limits; no assumption of unchanged BApp/BCheck compatibility |
| REPORT-01 | P2 | Rich local report formats, explicit evidence selection, manual verification/retest state and metadata redaction | Preview every export; arbitrary secrets cannot be guessed; expired evidence attachments are labelled |

## Current runtime limits and semantics

- API requests are limited to 256 KiB bodies and 64 KiB total headers; response capture is capped at 256 KiB and explicitly labelled when truncated or Base64 encoded.
- Exact scheme/host/port scope. Android UI projects require HTTPS; API redirects and automatic retries are disabled.
- Identity switches, replay and comparison strip observed/known/configured authentication headers. Body credentials remain unchanged and require review. Explicit manual cookies override the selected jar.
- API cookie jars are memory-only. The browser’s explicit To API jar action replaces the current identity jar with cookies matching the selected URL; metadata-supported transfers preserve domain/path/Secure/HttpOnly. This supports real A/B comparison after signing into each isolated browser profile. Without cookie metadata support, fallback transfer uses host/root-path defaults and says so. Configured auth headers, saved requests and evidence are encrypted. WebView cookies/cache are separate platform-managed app-private profile data, outside the workspace cipher.
- History is capped at 100 exchanges. Durable collections/config/notes have a 16 MiB admission budget; the whole encrypted vault is capped at 32 MiB. History may be pruned to fit. Saved collections and observations are rejected at capacity rather than silently dropped.
- Workspace write failures preserve the previous AtomicFile. Unknown/missing keys and corrupt files do not trigger plaintext fallback or automatic replacement. Native backup recovery is part of the latest emulator test.
- Sanitized exports omit bodies, paths, query values, headers, identity config and finding free text. The origin/generated IDs remain. Export requires preview and a known on-device provider. Rich raw evidence export is not implemented.
- Inspector snapshots are capped at 16,000 selected-HTML characters and 24,000 displayed evaluation characters; selection metadata and a display prefix explicitly identify truncation.
- Browser history is **URL/method/request-header metadata only**. No browser response/body capture is claimed. Unsupported profile features disable this browser rather than sharing test identities silently.
- Browser navigation is scoped; third-party subresources can load normally but are not recorded. No native JavaScript bridge is exposed. TLS errors are cancelled.
- Parameter runs: up to 50 sequential requests, at least one second apart, stop on 429/503 or transport failure. UI exposes four payload lists with matching substitution positions. No automatic retry/resume.
- Sequences: up to 10 saved requests; optional first-response JSON Pointer extraction into `{{token}}`; stop on non-2xx. URL tokens are encoded, header/body substitutions are literal.
- cURL import is a conservative parser, not shell execution. OpenAPI supports JSON 3.x; HAR import supports textual request drafts and filters exact origin.
- AWS review analyzes supplied JSON structurally; it does not contact an account or fully evaluate conditions, SCPs or effective privileges. JWT claims remain unverified; token statistics do not prove cryptographic randomness.

## Handoff / verification procedure

1. Read the latest remote HEAD and CI before editing; preserve any concurrent user changes.
2. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease`; device matrix uses tools/instrument_apk.py with the internal CI bundle and independent ui-tests APK. Read docs/RELEASING.md for artifact/signing boundaries.
3. Run the existing web concept checks when changing its source or inspector scripts. Use no captured credentials in fixtures.
4. Update this file, roadmap, capability claims and verification record in each implemented increment. Name unfinished items and actual blockers honestly.
5. Do not claim a production-ready suite: full proxy/VPN/TLS interception, broad active scanning, provider-account audits and physical/OEM coverage remain incomplete.

Follow-up harness correction: return to Elements after testing Console truncation before applying CSS. The native build fetches the exact pinned commit explicitly (it is not necessarily in the default branch history) and validates the installed NDK revision. New verification pending.

Device diagnostics on the first follow-up run found that Tools is entered from Home, not a bottom navigation item. The harness now uses the real Home → Tools control, which has a stable test tag. The isolated pinned candidate passed API 28 builds on all four ABIs, two equal clean stripped builds and 16 KiB ELF LOAD alignment on the connected Linux device; distribution notices and runtime forwarding remain pending.

The four-list native preview fixture now puts markers in the GET query. A GET body correctly fails the existing transport validator; the previous fixture accidentally exercised that rejection instead of its intended valid-plan count. No validation was weakened.
