# ESuite 1.0.1 — browser and API reliability fixes

Install this update over the production 1.0.0 APK to retain your encrypted workspace. The production signing certificate is unchanged.

## Fixed

- Cross-origin form POST navigation now receives a blocked response, covering the WebView callback gap for POST. Early main-frame navigation also stops outside-origin pages. Third-party subresources can still load; this is not a network firewall or full interception engine.
- HTML replacement retargets the selected element so further inspection and CSS edits operate on the replacement. CSS changes preserve the intended outline when selecting another element.
- Same-origin URL fragments remain usable by navigation, inspection and cookie controls.
- Native cookie edits and profile disposal flush cookie storage. Switching tools resets the page scroll position, browser actions dismiss the keyboard, and the installed WebView provider/version is visible.
- A stopped renderer reports recovery instructions instead of terminating the app.
- Failed API sends replace the previous response display and retain the actual attempted comparison identity in history.

## Verification gates

Publication requires unit/lint/optimized-build checks, expanded inspector regressions, and Android API 28–37.0 emulator verification. The independent UI harness drives real HTTPS loading, touch selection, HTML/CSS edits, storage, hidden/revealed HttpOnly cookies, API transfer and A/B isolation on compatible WebViews. API controls exercise HTTPS sends, comparison, collection saving and scope rejection after a success.

The exact production-signed APK also passes these UI checks on API 28/35/37.0, plus an install-over-1.0.0 test preserving encrypted evidence. Unsupported installed WebViews explicitly skip browser cases; API and persistence checks still run. Raw results are attached to the source commit's Actions runs. These checks do not establish perfection across every website, provider or physical phone.

## Remaining boundaries

Unrooted Android 9–17, HTTPS with system trust, on-device storage and no telemetry. The browser requires an installed WebView with isolated-profile support; some emulator providers do not provide it. Browser history is request metadata only. Full proxy/VPN/TLS interception, direct cloud account audits, WebSocket replay and active scanner parity remain unfinished. Physical/OEM/accessibility and broader export/data-lifecycle checks remain tracked in docs/AGENT_HANDOFF.md.

Download the APK and verify SHA256SUMS and build-info.json against the production certificate in docs/RELEASING.md. Never install internal debug or CI-signed test artifacts as updates.
