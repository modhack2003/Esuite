# Use ESuite on Android 9–17

Download the production-signed APK from [GitHub Releases](https://github.com/modhack2003/Esuite/releases/latest), then follow Android’s installation prompt. Install over the existing production app to retain the encrypted workspace. The release includes checksums and build information. See the [1.0.4 release record](RELEASE_1.0.4.md) for the version tested.

## Start an assessment

1. Open Home and create a project with a name and exact **HTTPS origin** such as `https://app.example.test`. Enter no URL path, query or credentials. Use Projects to switch workspaces or create another. Creating a project only updates the local workspace.
2. Choose identity A or B. Each project/identity has independent browser and API state. The browser requires the WebView multi-profile feature; check the displayed provider/version and update Android System WebView if the app reports it unavailable. Unsupported providers disable Browser while API Lab remains available.
3. Open Browser, enter an in-scope URL and tap Go. Cross-origin top-level login redirects are blocked in this version; use an in-scope test login flow. Explicit multi-origin scope is future work.

## Inspect a page and its session

- Elements: Select element, tap the page, then Read selection. Apply HTML/CSS changes to the live DOM; they do not edit server source or prove a vulnerability. Page scripts/resource loads retain normal network behavior.
- Cookies: Read native cookies. Values are hidden until explicitly revealed. Attribute visibility depends on WebView feature support. Set accepts a normal Set-Cookie value including attributes.
- To API jar: replaces the current identity's memory-only API cookie jar with browser cookies matching this URL. Supported metadata preserves domain/path/Secure/HttpOnly. The UI discloses narrower feature support/fallback behavior. No request runs during transfer.
- Storage: explicitly read or clear this page's localStorage/sessionStorage.
- Console: evaluate JavaScript in the current page and read page console messages. Page code gets no native bridge or vault access.

Inspection snapshots are bounded (16,000 characters of selected HTML; 24,000 characters of displayed evaluation output), so large pages/storage snapshots can be partial. Leaving Browser destroys the current page instance; return and press Go to reload. Isolated profile cookies are managed separately and follow their expiry/session rules; page edits and console state are ephemeral.

Third-party subresources can load normally but are not recorded outside scope. Navigation checks are not a complete network firewall. TLS errors are cancelled. Existing Chrome cookies are not read.

## Replay and compare APIs

1. Open **Request** in API Lab and review method, URL, headers and body. HTTPS scheme/host/port must match the project.
2. Send request executes one request and opens **Response**. Cancel stops the active call. Redirects and automatic retries are disabled. Captured response bodies are capped and labelled if truncated or Base64 encoded.
3. For account comparison, sign in as A in its browser profile and transfer cookies, then repeat for B. Alternatively configure each identity’s own headers under **Session** in API Lab.
4. Compare A and B sends two explicit requests. Known/configured auth headers from the draft are stripped so each identity's own configuration/jar applies. Request bodies are unchanged: review any embedded credentials before comparing. A differing response alone is not proof of broken authorization.
5. Traffic contains actual API responses plus separately labelled browser request metadata. Browser callbacks do not provide request bodies or response messages. Review missing content before replaying metadata as a new request.

Configured Cookie headers override jar-generated cookies. Avoid conflicting manual Cookie configuration when using transferred browser jars. API jars are memory-only; configured identity headers and saved request drafts persist encrypted.

## Save evidence locally

- Use **Saved** in API Lab to name the current draft or load a saved request for review.
- Select an exchange and open Findings to review passive observations or write a manual note. Observations are not automatically verified vulnerabilities.
- Open workspace settings from Home to reach Save now, report preview and deletion controls. The workspace autosaves after a short debounce. A failed save retains the previous encrypted file and reports the failure.
- Sanitized export omits bodies, paths, queries, headers, identity configuration and finding free text. Origin/generated IDs remain visible. Review the preview and choose recognized on-device storage. Remote document providers are not used.
- Deleting a project removes its workspace data and requests cleanup of its separate browser profiles. ESuite reports any profile cleanup that requires a restart. Erasing the vault removes the workspace key and requests cleanup of ESuite-owned profiles.

## Use local tools

- **Encode** includes Base64, URL form encoding and SHA-256.
- **Import** reads cURL, OpenAPI 3 JSON and HAR data into draft requests for manual review.
- **Review** includes unverified JWT claim decoding, token sample statistics and offline AWS JSON review.

## Unfinished capabilities

Full browser message interception, device-wide VPN capture, CA/TLS interception, WebSockets/streaming, broad active scanning, authenticated cloud connectors and extensions remain on the [roadmap](ROADMAP.md). Android emulator tests do not certify all phones, Android versions, accessibility flows or OEM backup behavior.
