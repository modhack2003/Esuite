# ESuite 1.0.0 — on-device web and API workbench

Native Android 9–17 application. No root or computer is required during use. Explicit target requests and web browsing communicate directly with the chosen services; workspace processing stays on the phone. No telemetry or hosted analysis.

## Included

- Scoped HTTPS browser with independent A/B profiles when supported by the installed System WebView, native cookie inspection and explicit transfer into API identity jars.
- Touch DOM/CSS inspection, page console and local/session storage tools.
- Cancellable API requests, independent identities, response comparison, bounded parameter runs and saved-request sequences.
- Raw HTTP, conservative cURL, OpenAPI 3 JSON and textual HAR imports; local decoder/JWT/token statistics tools; supplied AWS JSON structural review.
- Keystore-backed encrypted workspace, observations/collections and previewed sanitized local exports.

## Boundaries

Browser history contains request metadata, not complete intercepted messages. Full browser proxy/VPN capture, local CA interception, WebSocket replay, active scanner parity and direct cloud-account audits are not included. WebView multi-profile/cookie metadata support depends on the installed provider; an unsupported browser capability is disabled or explicitly labelled. API body capture is capped at 256 KiB. Targets require HTTPS with system trust validation. API redirects/retries are off.

The compatibility matrix and exact build/signing metadata accompany the release. Emulator results do not establish every OEM, physical-device accessibility or export-provider behavior. The remaining scope and acceptance criteria remain tracked in docs/AGENT_HANDOFF.md.

Download the APK and SHA256SUMS. Verify build-info.json against the published certificate fingerprint before installing. A previous debug build uses a different signer; use its explicit export controls before replacing it. The production signing identity must be retained for subsequent updates.
