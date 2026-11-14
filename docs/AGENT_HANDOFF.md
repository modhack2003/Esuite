# ESuite — next-agent handoff

Read this file first, then `AGENTS.md`, `docs/ROADMAP.md`, `docs/ARCHITECTURE.md` and the relevant source. Continue the implementation; do not restart branding or design.

## Product contract

- Native Android, unrooted phone, no computer during use. Runtime processing and saved evidence remain on-device.
- User authorized incremental implementation and pushes to `modhack2003/Esuite/main`. Make coherent commits and keep this checklist current.
- HTTPS-only runtime: automatic approval review rejected global cleartext HTTP. Do not reintroduce globally permitted cleartext or trust-all TLS. HTTP MockWebServer fixtures are JVM-only.
- No analytics, hosted AI, remote workers, automatic evidence uploads or external callback collectors. The design services received synthetic data only.
- The React prototype is a synthetic design reference. Native features must be assessed from Kotlin source and tests.

## Implemented increments

Latest tested application source: `6fc8be239dd7f1a7d5fc0ac665e22e3e6ca6a1bf`. Final run passed all jobs: https://github.com/modhack2003/Esuite/actions/runs/43584287247. Reports contain 23 JVM tests and 2 Android tests, with zero failures/errors/skips. Later documentation-only commits do not change that tested source.

| Part | Working behavior | Validation reference |
| --- | --- | --- |
| 1 — core | Exact-origin scope, real cancellable HTTP engine, no redirects/retries, independent project/identity cookie jars, capped text/binary response capture, passive observations and comparison | https://github.com/modhack2003/Esuite/actions/runs/43580598882 |
| 2 — browser / API UI | Compatible isolated WebView profiles, native cookie read/set/clear/explicit transfer, touch element selection, local HTML/CSS edits, console, storage, request metadata history, native API execution and A/B comparison | https://github.com/modhack2003/Esuite/actions/runs/43581714048 |
| 3 — local evidence | Keystore-backed AES-256-GCM workspace, AtomicFile, request collections, configured identity headers, observations, sanitized local export, project/profile deletion | https://github.com/modhack2003/Esuite/actions/runs/43582195830 |
| 4 — tasks / imports | Four payload substitution modes, bounded/cancellable execution, saved-request sequences, first-response JSON token extraction, cURL/raw HTTP/OpenAPI 3 JSON/textual HAR imports, offline AWS JSON review | https://github.com/modhack2003/Esuite/actions/runs/43583424635 |
| Native boundary tests | Android 15 emulator: actual Keystore round-trip and tamper rejection; separate WebView cookies including HttpOnly/native visibility; page inspector execution. Two tests passed, none skipped | https://github.com/modhack2003/Esuite/actions/runs/43583424635 |
| Storage hardening | AtomicFile backup recovery, missing-key failure without replacement, durable-data admission budgets and collection/observation removal | Passed including native backup recovery: https://github.com/modhack2003/Esuite/actions/runs/43584287247 |

## Release engineering increment (verification pending)

User now requests Android 9–17 and a signed production APK through GitHub Actions / Releases.
- Migrated to AGP 9.2.1 / Gradle 9.4.1 / built-in Kotlin 2.2.10; compile SDK 37.0, target 37, minimum 28, no maxSdk.
- Release uses R8/resource shrinking and no debug signing fallback. Optimized `.validation` variant is CI-only, never distributable as production.
- Added native project → observation → activity recreation UI test; expanded emulator matrix API 28–37.0 including Android 12L and 16 KiB API 36/37 images. Results still pending; old 23+2 count is historical.
- GitHub connector can write code but cannot administer Actions secrets. Local gh has no login and browser is signed out. A durable production signer remains a concrete publication blocker. Do not generate a new signing identity each CI run or publish its private material.
- Added executable release-guard integration checks with a disposable signer: accepts correctly pinned optimized package, rejects wrong pin/version, unsigned/debug/tampered APK. Added native screenshots as CI artifacts. Workflow syntax checked locally with checksum-verified actionlint 1.7.12; signing temp-path context corrected. Integration/emulator results pending.
- Added `.github/workflows/release.yml`: exact successful main-push CI gate → private durable signer → certificate/metadata/alignment verification → exact signed APK smoke tests → immutable stable GitHub release. Missing secrets fail closed; no APK has been published yet.
- Optimized production and validation APK assembly succeeded in run 43586465067, but the minified instrumentation APK exposed missing AndroidX test annotation classes. Added the concrete error-prone annotation dependency instead of suppressing R8 errors. Device jobs now reuse the exact validation APK/test APK built once; tools/instrument_apk.py checks runner completion and preserves JUnit pass/fail/skip evidence. The APK installer explicitly permits the test-only instrumentation package (`adb install -t`); it is never a published asset. Rerun pending.
- Follow-up R8 check found `javax.lang.model.element.Modifier` referenced only by Error Prone compiler meta-annotations. Added a narrowly scoped test-APK-only warning rule; production R8 rules remain strict. Run 43587308067 assembled app APKs but failed instrumentation shrink; it is not a compatibility pass. Secure browser sign-in was subsequently blocked by automatic approval-review usage limit, not a security verdict; no credentials were submitted.
- Run 43603063515 passed app/test APK compilation but the publication fixture exposed a too-narrow v2-only check: Android 9 supports v3, and apksigner selects v3 for this baseline. Require verified v2 OR v3 while retaining apksigner full validation and certificate pin. Added native ELF 16 KiB segment checks (existing graphics dependency manually verified), in addition to ZIP alignment. Expanded matrix concurrency to ten to test one artifact across all versions promptly. New run pending.
- Run 43603646680 compiled/linted all variants; publication fixture then caught AAPT2 changing `sdkVersion` output to `minSdkVersion`. Verified the upstream AAPT2 source and accept either exact field while still requiring minimum 28 / target 37. Metadata failures now show safe package/SDK diagnostics; unsigned validation artifacts and all lint reports are retained even on subsequent guard failure. Native matrix has not run yet; next run pending.
- Run 43604301147: build/release lint passed; downloaded XML confirms 23 JVM tests with zero failures/errors/skips. All publication guard fixtures passed; optimized production APK is 2,448,961 bytes, package dev.esuite.app, version 1.0.0/code 3, min 28/target 37, non-debuggable; current ARM64/x86_64 ELF alignment checked. First device jobs exposed a test-runner process crash on API 35/36 and API 37 installation failure. Preserved the reflective instrumentation harness only (separate test APK; production/validation app R8 remains enabled) and added crash/package/screenshot diagnostics including installer stdout. Compatibility remains unverified until rerun passes.
- First migrated CI run https://github.com/modhack2003/Esuite/actions/runs/43586091774 failed before assembly because AGP 9 only creates JVM tasks for the tested build type by default. Explicitly enabled JVM tasks for all build types; rerun pending. Do not treat this run as compatibility evidence.
- `docs/RELEASING.md` has exact signing secret/variable names, owner setup and publication steps. Next: run/fix migrated CI, configure signing secrets with account owner access, verify certificate/release download/upgrade. Preserve all unchecked product tasks below.

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
- Browser history is **URL/method/request-header metadata only**. No browser response/body capture is claimed. Unsupported profile features disable this browser rather than sharing test identities silently.
- Browser navigation is scoped; third-party subresources can load normally but are not recorded. No native JavaScript bridge is exposed. TLS errors are cancelled.
- Parameter runs: up to 50 sequential requests, at least one second apart, stop on 429/503 or transport failure. UI exposes two payload lists; engine supports four substitution positions. No automatic retry/resume.
- Sequences: up to 10 saved requests; optional first-response JSON Pointer extraction into `{{token}}`; stop on non-2xx. URL tokens are encoded, header/body substitutions are literal.
- cURL import is a conservative parser, not shell execution. OpenAPI supports JSON 3.x; HAR import supports textual request drafts and filters exact origin.
- AWS review analyzes supplied JSON structurally; it does not contact an account or fully evaluate conditions, SCPs or effective privileges. JWT claims remain unverified; token statistics do not prove cryptographic randomness.

## Handoff / verification procedure

1. Read the latest remote HEAD and CI before editing; preserve any concurrent user changes.
2. Run `./gradlew testDebugUnitTest lintDebug assembleDebug`; native boundary tests use `./gradlew connectedDebugAndroidTest` with Android 15 emulator. The workflow uploads APK and all reports.
3. Run the existing web concept checks when changing its source or inspector scripts. Use no captured credentials in fixtures.
4. Update this file, roadmap, capability claims and verification record in each implemented increment. Name unfinished items and actual blockers honestly.
5. Do not claim a production-ready suite: full proxy/VPN/TLS interception, broad active scanning, provider-account audits and physical/OEM coverage remain incomplete.
