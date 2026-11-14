# Use ESuite on Android 9–17

The app is configured for an unrooted Android 9–17 phone. Runtime needs no computer. Production distribution is a privately signed, optimized APK in GitHub Releases, with SHA256SUMS and build-info.json. Publication is currently blocked on signing-secret setup and completion of the migrated compatibility gates; see [Release delivery](RELEASING.md). CI debug/validation artifacts are for verification and must not be presented as production downloads.

## Start an assessment

1. Open Home and create a project with a name and exact **HTTPS origin** such as `https://app.example.test`. Enter no URL path, query or credentials. No request is sent merely by creating a project.
2. Choose identity A or B. Each project/identity has independent browser and API state. The browser requires the WebView multi-profile feature; update Android System WebView if the app reports it unavailable.
3. Open Browser, enter an in-scope URL and tap Go. Cross-origin top-level login redirects are blocked in this version; use an in-scope test login flow. Explicit multi-origin scope is future work.

## Inspect a page and its session

- Elements: Select element, tap the page, then Read selection. Apply HTML/CSS changes locally; they do not change server data or prove a vulnerability.
- Cookies: Read native cookies. Values are hidden until explicitly revealed. Attribute visibility depends on WebView feature support. Set accepts a normal Set-Cookie value including attributes.
- To API jar: replaces the current identity's memory-only API cookie jar with browser cookies matching this URL. Supported metadata preserves domain/path/Secure/HttpOnly. The UI discloses narrower feature support/fallback behavior. No request runs during transfer.
- Storage: explicitly read or clear this page's localStorage/sessionStorage.
- Console: evaluate JavaScript in the current page and read page console messages. Page code gets no native bridge or vault access.

Third-party subresources can load normally but are not recorded outside scope. TLS errors are cancelled. Existing Chrome cookies are not read.

## Replay and compare APIs

1. Open API Lab and review method, URL, headers and body. HTTPS scheme/host/port must match the project.
2. Send executes one request. Cancel stops the active call. Redirects and automatic retries are disabled. Captured response bodies are capped and labelled if truncated or Base64 encoded.
3. For account comparison, sign in as A in its browser profile and transfer cookies, then repeat for B. Alternatively configure each identity's own Authorization/API-key headers in API Lab.
4. Send as A then B makes two explicit requests. Known/configured auth headers from the draft are stripped so each identity's own configuration/jar applies. Request bodies are unchanged: review any embedded credentials before comparing. A differing response alone is not proof of broken authorization.
5. Traffic contains actual API responses plus separately labelled browser request metadata. Browser callbacks do not provide request bodies or response messages. Review missing content before replaying metadata as a new request.

Configured Cookie headers override jar-generated cookies. Avoid conflicting manual Cookie configuration when using transferred browser jars. API jars are memory-only; configured identity headers and saved request drafts persist encrypted.

## Save evidence locally

- Save API drafts to the collection by name. Requests are not executed by saving/loading them.
- Select an exchange and open Findings to review passive observations or write a manual note. Observations are not automatically verified vulnerabilities.
- Home contains Save now, sanitized export preview and deletion controls. The workspace autosaves after a short debounce. A failed save retains the previous encrypted file and reports the failure.
- Sanitized export omits bodies, paths, queries, headers, identity configuration and finding free text. Origin/generated IDs remain visible. Review the preview and choose recognized on-device storage. Remote document providers are not used.
- Deleting a project attempts to clear its separate browser profiles; if the platform reports a loaded profile, the project is retained and an error shown. Erasing the vault deletes its encryption key; browser profile data is separate and must be cleared using project controls.

## Run bounded tasks

Tools exposes the local decoder/JWT/sample/configuration tools and import actions. cURL is parsed as data, never executed. OpenAPI 3 JSON and textual HAR imports become editable collection drafts.

The parameter runner uses the current API draft with `{{P1}}`/`{{P2}}` markers. Preview first, then explicitly start. Runs have a 50-request cap, sequential execution, at least one second between requests, cancellation and no retries. HTTP 429/503 stops the run.

Saved-request sequences accept exact collection names in order, up to 10 requests. An optional JSON Pointer can extract a token from the first response into later `{{token}}` positions. Non-2xx responses stop the sequence. This does not automate MFA/passkeys/CAPTCHA.

Offline AWS JSON review does not contact a provider account or determine effective privileges. JWT decoding is not signature verification; descriptive token statistics do not prove cryptographic unpredictability.

## Unfinished capabilities

Full browser message interception, device-wide VPN capture, CA/TLS interception, WebSockets/streaming, broad active scanning, authenticated cloud connectors and extensions remain on the [roadmap](ROADMAP.md). Android emulator tests do not certify all phones, Android versions, accessibility flows or OEM backup behavior.
