# ESuite: Android pentesting workbench

Planning baseline: 14 November 2026. Revision 2 adds a broad Burp-inspired feature roadmap. Working name, subject to change. This is a proposed specification, not a claim of implemented or tested functionality.

## Confirmed requirements

- Run on an unrooted Android phone without a computer or remote worker at runtime.
- Prioritize browser inspection, cookies, browser traffic, API editing, test-account comparison, and capture from selected other apps.
- Keep captured traffic, credentials, processing, and reports on the device. No hosted AI, telemetry, automatic uploads, sync, or cloud backup of project data.
- Testing necessarily sends requests and selected authentication material to the intended target. Local-only means no intermediary collection or processing service; it does not mean targets can be tested without networking.
- Cloud assessment remains in the roadmap; direct provider API calls can be made from the phone later, with findings retained locally.

## Commercial scope clarification — 16 November 2026

The owner confirmed a commercial Android app with evidence kept local. Mandatory cloud accounts, hosted evidence, synchronization and telemetry are outside this request. The first paid scope should be defined around the implemented manual browser/API workbench; unavailable interception/scanning capabilities must remain visibly planned. [The physical Vivo review](reviews/vivo-1.0.2-production-readiness.md) lists current correctness defects, UI improvements, local reporting/recovery needs and commercial release gates. Distribution, pricing/entitlements, offline guarantees and support remain explicit decisions; no payment/backend integration is implied by this clarification. Original capability scope and unfinished milestones below remain intact.

## Product workflow

Create project and target scope → browse or select an app to capture → inspect request → send a copy to the API editor → choose a test identity → compare results → save evidence.

Primary navigation: Projects, Browser, Traffic, API Lab, Findings. Cookie and storage panels live inside Browser; app capture configuration lives inside Traffic.

## Version 1 scope

| Module | Included behavior | Acceptance evidence |
| --- | --- | --- |
| Testing browser | Tabs, address bar, touch element selection, DOM/CSS edits, console, localStorage and sessionStorage | Inspect a controlled page and verify changes and console output |
| Cookies | Inspect native cookie values and supported metadata, add/edit/delete, explicit copy to a selected API identity | Observe login cookie creation, mutation, logout and HttpOnly metadata |
| Browser traffic | Searchable history, headers, supported request/response bodies, redirects and timing, capture-to-editor | Capture form and JSON POST requests, redirects, duplicate Set-Cookie headers, and binary responses |
| API Lab / Repeater | HTTP editor, JSON/form/raw bodies, authentication, collections, cURL import/export, history and response diff | Reproduce the captured request and compare status, headers and body |
| Manual testing utilities | Intercept/edit/forward/drop, request and response rules, Decoder, Comparer, bookmarks and basic target map | Verify scoped interception and rule changes against a controlled endpoint |
| Parameter runner | Explicit payload positions, bounded wordlists, result filters and response extraction | Verify substitution, request limits, cancellation and restart without duplicate state-changing requests |
| Passive checks | Analyze already captured traffic for supported configuration and disclosure findings | Produce evidence-backed findings without sending extra audit requests |
| Identities | Separate API cookie jars and tokens; separate browser profiles when supported | Prove account A credentials do not leak into account B requests or storage |
| Other-app capture | User-selected apps through local VPN, connection metadata, supported cleartext HTTP, conditional HTTPS inspection | Test a controlled cleartext app, a CA-trusting TLS app, and a pinned/non-trusting app |
| Findings | Notes, request/response references, redacted report preview and local export | Produce a reproducible finding with secrets removed from its export |

Browser profile support must be checked at runtime. On unsupported WebView versions, show the limitation; do not present two ordinary tabs as independent identities. API identity isolation remains available independently.

## Capture architecture

1. Kotlin and Jetpack Compose provide the Android UI. WebView provides the initial testing browser.
2. Browser traffic is routed through an on-device proxy where supported. WebView ProxyController is a candidate integration and needs feature detection. Native cookie APIs supply cookie information.
3. A local interception CA is generated per installation. Protect its private key locally. The testing browser trusts that CA for explicit scoped interception; upstream certificates are still validated. Do not ignore all TLS errors.
4. Other-app traffic uses Android VpnService, a local userspace transport implementation, and the capture/proxy engine. Forwarding remains on-device and directly to the target. Exclude the engine's own outgoing sockets from VPN routing to prevent loops.
5. A native HTTP client powers the API editor using independent cookie jars, authentication state, and redirect rules. Authentication is not automatically forwarded across target origins.
6. A shared traffic model connects captures, edits, comparison results, and findings. Record origin, identity, timestamps, capture completeness and protocol limitations.

WebView interception callbacks alone are not a complete capture engine: request bodies are not exposed by WebResourceRequest, and interception has coverage limitations. A JavaScript fetch/XHR wrapper may add context, but cannot be the sole source of truth.

Version 1 does not promise full desktop JavaScript stepping, complete browser protocol parity, or custom Chromium maintenance. Those require separate feasibility decisions.

## Honest support boundaries

| Traffic or data | Expected support |
| --- | --- |
| App's own browser cookie store | Native inspection, subject to platform feature support |
| Existing Chrome or another app's private cookie database | No direct access through normal unrooted app permissions |
| Cleartext HTTP routed through capture | Inspect supported messages; do not imply every protocol is decoded |
| HTTPS from our testing browser | Planned inspection through its explicitly configured local trust path |
| HTTPS from another app accepting our CA | Conditional inspection after user setup and successful protocol support checks |
| Pinned HTTPS or app rejecting our CA | Metadata where observable; contents remain unavailable. Report failure/limitations clearly |
| QUIC/HTTP3, custom protocols, encrypted DNS/ECH | No initial full-content guarantee; address/IP/volume metadata can be more reliable than hostname attribution |
| Simultaneous third-party VPN | Android normally permits one active VpnService per user/profile; explain the conflict before capture |

Some compatibility configurations can cause an app to fall back from QUIC to TCP, but this is not guaranteed and must not be silently represented as complete capture. Preserve app connectivity or clearly identify blocking/interception failure.

## Local data protection

- No mandatory account or backend. No automatic crash-report uploads containing traffic.
- Keep projects in app-private storage. Encrypt persisted credentials and sensitive capture content using reviewed encryption and Android Keystore-protected keys.
- Review browser cookie/cache persistence separately; database encryption does not automatically encrypt WebView's files. Define retention and clear sessions at project closure where configured.
- Exclude traffic, browser data, credentials, and CA private keys from backup/device-transfer mechanisms as supported; validate behavior on supported Android versions.
- Make exports a user-triggered action using local file selection. Warn if the user chooses a provider-backed destination that would leave the phone. Default to redacted exports.
- Explicit reveal/copy of secrets; no automatic clipboard copying. Optional app lock and screen protection.
- Capture size caps, retention controls, full project deletion, visible foreground capture notification and immediate stop.
- Bind proxy/control interfaces locally; no publicly exposed debugging endpoint. Keep untrusted page content separate from privileged app operations and avoid a broadly exposed native JavaScript bridge.

## Delivery milestones

- [ ] M0: Prove local browser capture, cookie inspection, independent sessions and rootless VPN forwarding on a physical unrooted device.
- [ ] M0: Publish a support matrix for HTTP/1.1, HTTP/2, WebSockets, redirects, TLS trust/pinning failures and QUIC behavior. Unsupported paths are visible rather than silently omitted.
- [ ] M1: Deliver Browser, basic cookie/storage tools, traffic history and capture-to-editor workflow.
- [ ] M2: Deliver API collections, authentication, cURL import/export, two-identity comparison and local findings.
- [ ] M3: Integrate selected-app VPN capture with metadata and conditional TLS inspection. This is part of the version 1 goal, not an uncommitted future feature.
- [ ] M4: Complete offline storage review, backup exclusions, redaction, deletion, performance and interrupted-capture recovery checks; package a standalone APK.
- [ ] V1.1: HAR/OpenAPI import, request chaining, controlled parameter fuzzing, WebSocket inspection, saved retests and GraphQL tools.
- [ ] V2: Cloud configuration imports and direct account audits, starting with one provider. All processing and results remain local.

## Release verification

The release must demonstrate a login → cookie inspection → request capture → replay as account A → replay as account B → comparison → redacted finding workflow on the phone alone. Use owned test identities and controlled test targets.

Compare capture results with known server logs for controlled fixtures, rather than trusting the UI alone. Include multipart uploads, gzip, streaming, multiple cookies, expired credentials, third-party resources, service workers and background/foreground transitions.

Verify that local-only behavior holds with network inspection: no analytics, remote processing or automatic uploads. Test data deletion, backup rules, capture cancellation, bounded storage and account isolation. Establish minimum Android and WebView versions from prototype results before publishing compatibility claims.

## Burp-inspired capability roadmap

Goal: reproduce useful testing workflows in an independent native Android implementation. Burp is the capability reference; neither its engine nor desktop extension compatibility is assumed. No feature below overrides the unrooted, phone-only, local-data requirements.

Delivery labels: Core = version 1 target; Expansion = subsequent releases after capture correctness; Research = feasibility-gated advanced work; Restricted = cannot provide full equivalence under current requirements.

| Burp workflow | ESuite implementation target | Delivery |
| --- | --- | --- |
| Proxy | Intercept requests and responses; edit/forward/drop; ordered match/replace rules; HTTP history; scope filters; CA management | Core |
| Target | Observed site map, host/path/parameter inventory, include/exclude scope, annotations; later controlled discovery | Core / Expansion |
| Repeater | Named tabs, editable requests, history, redirects, identity selection and response comparison; later request groups | Core / Expansion |
| Intruder | Mark insertion positions, substitute wordlists/generated values, process payloads, extract results, bound rate/concurrency | Core / Expansion |
| Decoder | Chained URL, Base64, hex and HTML decoding/encoding; hashes, compression helpers and JWT parsing | Core |
| Comparer | Text and byte differences, JSON comparison, normalized dynamic fields, request/response and identity comparisons | Core |
| Organizer | Bookmarks, labels, notes, linked evidence, reproducible steps and local findings reports | Core |
| Logger / Dashboard | All-tool request log, task queue, progress, issues, errors, resource consumption and stop control | Core / Expansion |
| Passive Scanner | Inspect existing messages without generating audit requests | Core |
| Active Scanner | Selected insertion-point tests, crawler-driven audits, custom checks and confirmation workflows | Expansion |
| Session handling | Separate identities, cookie rules, expiry checks, refresh flows, recorded request macros and token extraction | Core / Expansion |
| WebSockets | Message history, format decoding, interception, editing and replay where supported | Expansion |
| Sequencer | Token sampling, duplicate/frequency analysis, bit/character distributions and documented statistical tests | Expansion |
| DOM Invader-style assistance | Browser source/sink observations, canary tracing and postMessage investigation | Research |
| Extensions / BChecks-style rules | Local rule packs, extractors, checks and later a bounded script interface | Expansion / Research |
| Collaborator / OAST | Direct callback listener on the phone only when the target can reach it; no hosted relay | Restricted |
| Advanced protocol testing | Connection reuse controls, raw HTTP experiments, timing/race coordination and protocol discrepancy investigation | Research |

### Proxy, shared editor and Target details

- Hold request or response messages using method, host, path, status, content-type or text rules. Distinguish interception from an ordinary history view. Show queue age and explicitly configurable timeout behavior so paused traffic cannot remain invisible.
- Edit headers, cookies, query parameters, JSON, forms and supported binary bodies. Show raw, formatted and hex views plus a structured inspector. Retain original and edited copies and the exact applied rules.
- Support literal and regex match/replace, rule preview and ordering, header insertion/removal and body changes. Correctly update supported framing/compression rather than silently corrupting a request.
- Maintain observed host/path trees, endpoints, content types, parameter locations and authentication requirements. Discover links/forms/scripts and import OpenAPI later; browser observation alone cannot reveal all server endpoints.
- One-tap Send to Repeater, Parameter Runner, Comparer, Decoder or Findings throughout the app. Keep the originating capture and test identity attached.
- Render captured HTML only in an isolated preview without automatic network access or page access to privileged app APIs. Label rendered previews separately from actual target behavior.

### Repeater and Intruder-style parameter runner

- Repeater provides named tabs, history navigation, timing and body size, redirect policy, identity selection, response search and sequential request groups. Later add supported grouped parallel requests for controlled race testing.
- Parameter substitution modes: one position at a time; one value across multiple positions; paired lists; and bounded combinations across lists. Show the generated request count before a run.
- Input sources: local wordlists, numbers, dates, random markers and values extracted from previous responses. Include encoding, case conversion, prefix/suffix and replace transformations.
- Extract results using text/regex/JSON paths. Sort by status, length and timing; flag deviations from a repeated baseline and exclude unstable response fields. Difference alone is a lead, not proof of an access-control flaw.
- Separate connection concurrency, request rate, per-host budgets and retry behavior. Pause, cancel, checkpoint progress and recover after Android background restrictions. Do not automatically retry non-idempotent requests after an uncertain send.
- API expansion: GraphQL editing, OpenAPI-driven parameter locations, multipart support, token-aware request chains and schema checks. Add protobuf/gRPC inspection only after transport and schema support are validated.

### Session handling and authorization testing

- Named identities with independent API cookie jars and token stores. Explicit browser-to-identity transfer with clear origin scope; no global shared Authorization header.
- Build an account-by-endpoint matrix for anonymous, account A, account B and any provided role identities. Mask dynamic fields for comparison, record expected behavior, and require evidence before classifying BOLA/IDOR or role failures.
- Record login/request macros; extract cookies, CSRF tokens, nonces and response values; inject them into subsequent requests. Detect expired sessions and show why a refresh or login failed.
- Detect unexpected cookie rotation and token placement. JWT tools decode claims, check timestamps and verify signatures only with appropriate supported algorithms and supplied/trusted key material. Decoded data is never labeled verified.
- Interactive authentication can require a human step. Do not promise unattended replay of MFA, passkeys or CAPTCHA challenges.

### Scanner and discovery roadmap

- Passive phase: cookie flags, relevant response security headers, mixed-content observations, verbose errors, debug disclosures, cache directives, potentially sensitive responses and possible secrets in loaded assets. Mark candidates and context-sensitive recommendations accurately.
- Discovery phase: bounded link/form traversal, JavaScript URL references, endpoint deduplication, API specification imports, per-host scope and configurable crawl depth. Separate observation from additional discovery requests. Exclude configured logout and state-changing flows from automated navigation.
- Active phase: individually selectable checks for reflected input/XSS candidates, SQL injection indicators, path traversal, open redirects, CORS behavior, supported method handling, authentication/authorization differences and cache behavior. Extend later to carefully scoped upload and server-side injection tests with suitable validation.
- Every check declares its inputs, requests, expected signal, confidence, reproduction steps and limitations. Use repeated baselines and controls; do not equate a reflected string, error code or timing spike with confirmed exploitation.
- DOM XSS requires browser-context validation. Blind SSRF/XXE and other callback-dependent checks cannot claim confirmation without a reachable callback receiver. No external OAST is enabled in this design.
- Support selected-request audits as well as project tasks. Store task configuration, exact test mutations, captures, outcomes, check version, pause/cancel state and skipped/unsupported checks.
- Scale with the phone: explicit request/storage caps, foreground work status, thermal and battery awareness, and resumable queues. Broad active coverage follows validated manual tooling; there is no initial Burp Scanner parity claim.

### Utilities, evidence and extensibility

- Decoder: chained transforms with intermediate views, reversible operations, byte-safe inputs and export. Hashing is one-way; the UI must not offer fictional hash decryption.
- Comparer: structured JSON, text and bytes; compare status, headers, body and timing while documenting ignored fields.
- Sequencer: sampling definitions, independent-session considerations, sample counts and reproducible tests. Statistical results are evidence about a sample, not proof of cryptographic security.
- Logger: trace traffic from Browser, Repeater, Parameter Runner, Scanner and macros with tool/identity tags, original-versus-edited messages and redacted views. Bound retention and index size.
- Findings: confidence and severity, affected endpoints, manual reproduction, request/response attachments, before/after comparison, remediation and retest state. Export Markdown/HTML/JSON locally, with PDF reporting as an expansion.
- Rule packs: local import/export for match rules, extractors, wordlists and scan-check definitions. Default to a declarative format. A later script engine needs explicit host APIs and bounds for time, memory, files and target access; isolation must be technically validated.
- Existing Burp BApps, Montoya extensions, BChecks and Bambdas are not assumed to run unchanged. Build an Android extension interface first; assess individual format importers separately. No hosted extension execution or AI traffic upload.

### Protocol and callback boundaries

- Standard HTTP clients can normalize headers, framing and connections. Preserve captured bytes where possible and label reconstructed messages. Raw HTTP, request-smuggling research and precise race timing require a separate transport path and test suite before being advertised.
- HTTP/2 streams, WebSocket frames, server-sent events, HTTP/3 and custom protocols each need explicit support status. Display truncation, dropped traffic and unsupported decoding.
- A Burp Collaborator-equivalent service normally receives out-of-band interactions on an externally reachable server. This conflicts with the no-server/no-upload design. A phone listener can support a reachable lab network, but mobile NAT, carrier restrictions and background lifecycle prevent a universal equivalent. DNS or SMTP collection is not implied by a local HTTP listener.
- No certificate-pinning bypass, cross-app private database access, remote browser worker, third-party VPN chaining, or blanket HTTPS decryption is part of the unrooted baseline.

## Revised delivery sequence

This sequence refines the earlier M0–M4 checklist. Core features are version 1 targets, implemented in these dependency stages rather than treated as equally finished.

1. Foundation: M0 capture feasibility, local storage model, shared message editor, scoped transport, identity separation and protocol support matrix.
2. Manual workbench: Browser DevTools, cookies/storage, Proxy interception, Repeater, Decoder, Comparer, basic site map, Logger and Findings.
3. Version 1 completion: selected-app VPN capture, bounded parameter runner, passive checks, rule preview/replacement, local export and resource controls.
4. Automation expansion: full substitution modes, discovery crawler, macros/token refresh, WebSockets, Sequencer and selected active checks.
5. Advanced expansion: browser DOM instrumentation, validated protocol experiments, custom extension interface, broader scanner coverage and cloud audits. Direct cloud API calls and configuration analysis remain on-device.

## Additional acceptance gates

- [ ] Interception: verify request/response forwarding, editing, dropping, timeout behavior, queue cancellation and unaffected out-of-scope traffic.
- [ ] Payload runner: verify position selection, substitution modes, transformations, count estimation, extraction, rate bounds and cancellation.
- [ ] Sessions: verify no account leakage through browser profiles, redirects, refresh macros, exports or logs.
- [ ] Scanner: passive checks generate no extra audit requests; active checks respect scope and budgets, include controls, expose unsupported checks and generate reproducible evidence.
- [ ] Protocols: compare controlled server logs with captures; validate framing, binary data, compression, duplicate headers, streaming and concurrent requests.
- [ ] Privacy: no automatic callback service, analytics, cloud extension, hosted AI or remote processing is introduced by optional modules.
- [ ] Phone UX: verify the complete workflow using touch and the soft keyboard in portrait and landscape, with clear pause/stop access.

## Burp capability references

Official references were used to identify workflows. The implementation details, staging and Android constraints above are proposed product decisions, not claims about Burp compatibility.

- Tool overview: https://portswigger.net/burp/documentation/desktop/tools
- Proxy: https://portswigger.net/burp/documentation/desktop/tools/proxy
- Shared message editor: https://portswigger.net/burp/documentation/desktop/tools/message-editor
- Repeater: https://portswigger.net/burp/documentation/desktop/tools/repeater
- Intruder configuration: https://portswigger.net/burp/documentation/desktop/tools/intruder/configure-attack
- Scanner: https://portswigger.net/burp/documentation/scanner
- Passive and active auditing: https://portswigger.net/burp/documentation/scanner/auditing
- Sequencer: https://portswigger.net/burp/documentation/desktop/tools/sequencer
- Session handling/macros: https://portswigger.net/burp/documentation/desktop/settings/sessions
- Collaborator: https://portswigger.net/burp/documentation/desktop/tools/collaborator
- Extensions: https://portswigger.net/burp/documentation/desktop/extend-burp/extensions

## Official technical references

- Android CookieManager: https://developer.android.com/reference/android/webkit/CookieManager
- AndroidX cookie metadata: https://developer.android.com/reference/androidx/webkit/CookieManagerCompat
- WebView request interface: https://developer.android.com/reference/android/webkit/WebResourceRequest
- WebView interception: https://developer.android.com/reference/kotlin/android/webkit/WebViewClient
- WebView proxy integration: https://developer.android.com/reference/androidx/webkit/ProxyController
- WebView profiles: https://developer.android.com/reference/androidx/webkit/WebViewCompat
- Android VPN behavior: https://developer.android.com/develop/connectivity/vpn
- Android certificate trust configuration: https://developer.android.com/privacy-and-security/security-config
