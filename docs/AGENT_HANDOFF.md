# ESuite implementation handoff

## Active increment

Part 1 is implemented and passed CI: https://github.com/modhack2003/Esuite/actions/runs/43580598882. Part 2 passed CI: https://github.com/modhack2003/Esuite/actions/runs/43581714048. Part 3 passed CI: https://github.com/modhack2003/Esuite/actions/runs/43582195830. Part 4 adds bounded tasks and imports. Browser functionality still needs physical-device verification. Do not mark an engine delivered solely because its UI exists.

## Permanent requirements

Unrooted Android; no computer at runtime; processing/evidence local. Continue pushing coherent tested increments to `modhack2003/Esuite`. Preserve visual identity. No further Superdesign login attempts are required.

## Parts and acceptance gates

| Part | Work | Status | Acceptance |
| --- | --- | --- | --- |
| 1 | Project scope, HTTP replay, cancellation, per-project/identity cookie jars, binary/truncated body provenance, pure comparison/passive checks | Complete; CI passed | MockWebServer tests: scope escape rejection, redirects not followed, cookie isolation, cancellation and body caps |
| 2 | Real native browser, cookie tools, DOM/CSS, console, storage, browser metadata history, API editor UI | Complete; CI passed | Build/lint; no dangerous JS bridge; feature detection; explicit missing-body labels; on-device gates tracked |
| 3 | Encrypted workspace, findings/export, saved requests and local analysis UI | Complete; CI passed | AES-GCM tamper/round-trip tests; no plaintext evidence on disk; redacted export defaults |
| 4 | Parameter runner, macros, cloud config review and token tools | Implemented; CI pending | Bounded/cancellable tasks, exact scope, no automatic mutation retries, fixture-based checks |
| 5 | Local proxy and rootless selected-app forwarding | Queued | Select licensed stack, implement actual forwarding; no empty VPN that blackholes traffic |
| 6 | TLS interception, advanced protocols/scanner/extensions | Queued | Protocol matrix and physical-device tests; no pinning bypass or blanket decryption claim |

## How to continue

Read `AGENTS.md` and inspect actual source plus the latest CI run. Before implementing Part 5 inspect third-party licenses and upstream pinned revision; a permissive Android tun2socks candidate is `heiher/sockstun` / its actual dependencies, but no component has been selected or integrated yet. Never silently change repository licensing.

Update this file with each pushed part: changed paths, working behavior, CI URL, known gaps and next action. Keep `docs/ROADMAP.md` and README capability claims aligned with the native app. The React concept stays synthetic and should not be rewritten to claim native completion.

## Runtime transport decision

Automatic approval review rejected global cleartext HTTP. Part 2 uses HTTPS-only Android configuration and rejects HTTP project origins in its UI. MockWebServer HTTP runs only in JVM tests; do not reintroduce globally permitted cleartext.

## Part 3 storage behavior

Workspace evidence, saved requests and configured identity headers are encrypted with AES-256-GCM using an Android Keystore key, stored via AtomicFile under noBackupFilesDir. API cookie jars remain memory-only. WebView cookies/cache use separate app-private profiles managed by the platform and are not encrypted by the workspace cipher. Sanitized exports omit bodies, paths, query values, headers, identity config and finding free text; they require a preview and a known local device-storage provider. No raw evidence upload occurs.

## Part 4 task behavior

The parameter runner exposes two payload lists in UI, four supported substitution modes, a 50-request engine cap, sequential execution and at least one second between requests. Stop cancels the underlying call; HTTP 429/503 stops the run with no retry. Saved-request sequences have at most 10 steps, fail on non-2xx responses, and can extract one token from the first response using a JSON Pointer. cURL is parsed as data, not executed. OpenAPI 3 JSON and textual HAR imports become reviewed collection drafts, never automatic requests. AWS JSON review is offline structural analysis, not an authenticated cloud audit or effective-permissions verdict.
