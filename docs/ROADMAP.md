# ESuite delivery checklist

All unchecked items are planned. A native build or a UI preview does not establish capture functionality.

Current implementation and next-agent entry point: [Agent handoff](AGENT_HANDOFF.md). Part 1 API engine tests passed CI. Part 2 adds live browser and native API wiring; CI/device verification is tracked in the handoff. Do not infer native networking capability from the still-synthetic React concept.

## Foundation delivered

- [x] ESuite name, original vector identity and repository banner.
- [x] Product plan retaining phone-only, unrooted and local-processing constraints.
- [x] UI specification with navigation, flows and state definitions.
- [x] Interactive React design study with synthetic data.
- [x] Kotlin/Compose starter with Home, Browser concept, Traffic fixtures, API concept and Tools.
- [x] Local Base64, form URL and SHA-256 utilities with unit tests.
- [x] Gradle wrapper, CI definition and contributor guidance.
- [x] Canva concept board and MagicPath interaction design.
- [ ] Superdesign output (connection unavailable; brief prepared).

## M0 — prove the capture design

- [ ] Browser proxy routing and complete request/response fixtures.
- [ ] Native cookie metadata and feature support matrix.
- [ ] Separate account profiles / supported fallback behavior.
- [ ] Rootless selected-app VPN forwarding and immediate cancellation.
- [ ] Local CA trust, upstream validation and honest pinning/trust failures.
- [ ] HTTP/1.1, HTTP/2, redirects, uploads, streaming and WebSocket coverage matrix.

## M1 — manual testing workbench

- [ ] Live browser DOM/CSS inspector, console and storage views.
- [ ] Scoped Proxy intercept/edit/forward/drop and history.
- [x] Real API execution / Repeater and per-identity cookie jars (JVM integration tests passed).
- [ ] Original/modified message provenance, Decoder and Comparer.
- [ ] Site map, local findings, redaction and encrypted persistence.

## M2 — version 1 completion

- [ ] App capture integrated into the UI with visible foreground state.
- [ ] Bounded parameter runner and passive checks.
- [ ] Resource caps, retention and explicit export.
- [ ] Physical-device functional, privacy and accessibility verification.

## M3 — automation expansion

- [ ] Intruder-style substitution modes and result extraction.
- [ ] Session macros, CSRF/nonces and token refresh.
- [ ] Controlled crawler and selected active audit checks.
- [ ] WebSocket replay, Sequencer and GraphQL workflows.

## M4 — advanced / cloud

- [ ] DOM instrumentation and validated raw protocol tooling.
- [ ] Local extension/rule interface with validated execution bounds.
- [ ] Cloud configuration imports and direct provider audits.
- [ ] Reachable-lab callback listener; no hosted OAST in local-only mode.

Detailed capabilities and acceptance criteria: [Product plan](PRODUCT_PLAN.md).
