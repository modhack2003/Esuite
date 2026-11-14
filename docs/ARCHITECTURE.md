# ESuite architecture

## Current boundary

`app/` now has a scoped OkHttp engine with isolated identity sessions and a live native WebView inspector. `design/prototype/` remains the synthetic web interaction study. Native browser callbacks record request metadata only; no proxy or VPN interception engine exists yet.

## Proposed module boundaries

| Module | Responsibility | Present state |
| --- | --- | --- |
| app | Navigation, Compose screens, lifecycle | Starter implemented |
| core | Byte transformations and shared data contracts | Codec implemented |
| browser | WebView, native cookies, inspection and profiles | Implemented; device checks pending |
| transport | Scoped API requests, TLS validation, identity jars | Implemented; JVM integration tests passed |
| proxy | HTTP interception and scoped modification | Planned |
| capture | VpnService, selected apps, userspace forwarding | Planned |
| analysis | Passive observations and structured body comparison | Implemented; task scheduling planned |
| storage | Local encryption, retention, redaction and export | Planned |
| cloud | Direct account audits and imported configurations | Planned |

Start modular boundaries inside the app; split Gradle modules as implementations justify them. Do not create empty modules pretending to be engines.

## Data flow

Browser/app capture or API execution → message record with provenance and completeness → inspector/replay/comparison → finding evidence → explicit local export.

Every message needs project scope, originating tool, identity reference, observed timestamps, original/modified bytes where available, protocol version, capture gaps and redaction status. Reconstructed HTTP must be distinguishable from captured bytes. Secrets must not become global headers or logs.

## First technical milestone

Prove browser proxy routing, cookie metadata support, separate profiles, rootless VPN forwarding, local CA trust and protocol coverage on physical unrooted Android devices. Include pinned and non-trusting fixture apps to validate honest failure states. Do not integrate active scanners before capture provenance, cancellation and request limits work.

## Runtime privacy

Shipping design: no backend, telemetry or remote AI; local project storage with Keystore-protected keys; per-project retention; local CA private key protection; explicit redacted exports; backup exclusions; loopback-bound control surfaces. These storage and capture mechanisms are roadmap requirements, not current working security claims.

Present native app: explicit INTERNET permission and exact-origin HTTP execution. Project/evidence state currently remains in memory. WebView manages its own app-private profile cookie/cache data, separate from the planned encrypted project vault. Backups are excluded; platform behavior still needs device verification.
