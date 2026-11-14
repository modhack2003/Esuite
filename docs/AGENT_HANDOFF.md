# ESuite implementation handoff

## Active increment

Part 1: implement and verify scoped HTTP execution, identity cookie jars and pure analysis utilities. Native UI wiring follows in Part 2. Do not mark an engine delivered solely because its UI exists.

## Permanent requirements

Unrooted Android; no computer at runtime; processing/evidence local. Continue pushing coherent tested increments to `modhack2003/Esuite`. Preserve visual identity. No further Superdesign login attempts are required.

## Parts and acceptance gates

| Part | Work | Status | Acceptance |
| --- | --- | --- | --- |
| 1 | Project scope, HTTP replay, cancellation, per-project/identity cookie jars, binary/truncated body provenance, pure comparison/passive checks | Implementing | MockWebServer tests: scope escape rejection, redirects not followed, cookie isolation, cancellation and body caps |
| 2 | Real native browser, cookie tools, DOM/CSS, console, storage, browser metadata history, API editor UI | Next | Build/lint; no dangerous JS bridge; feature detection; explicit missing-body labels; on-device gates tracked |
| 3 | Encrypted workspace, findings/export, saved requests and local analysis UI | Queued | AES-GCM tamper/round-trip tests; no plaintext evidence on disk; redacted export defaults |
| 4 | Parameter runner, macros, cloud config review and token tools | Queued | Bounded/cancellable tasks, exact scope, no automatic mutation retries, fixture-based checks |
| 5 | Local proxy and rootless selected-app forwarding | Queued | Select licensed stack, implement actual forwarding; no empty VPN that blackholes traffic |
| 6 | TLS interception, advanced protocols/scanner/extensions | Queued | Protocol matrix and physical-device tests; no pinning bypass or blanket decryption claim |

## How to continue

Read `AGENTS.md` and inspect actual source plus the latest CI run. Before implementing Part 5 inspect third-party licenses and upstream pinned revision; a permissive Android tun2socks candidate is `heiher/sockstun` / its actual dependencies, but no component has been selected or integrated yet. Never silently change repository licensing.

Update this file with each pushed part: changed paths, working behavior, CI URL, known gaps and next action. Keep `docs/ROADMAP.md` and README capability claims aligned with the native app. The React concept stays synthetic and should not be rewritten to claim native completion.
