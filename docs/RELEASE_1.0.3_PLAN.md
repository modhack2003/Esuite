# ESuite 1.0.3 release plan

The latest published APK is 1.0.2 (version code 5). The current `main` baseline is `d2b5e3e`, whose Android build, design checks and API 28–37.0 matrix passed. That source includes a first correctness increment for retained evidence, navigation-safe in-memory drafts, technical keyboards, identity attribution and deferred browser profile cleanup. The November Vivo audit reproduced the remaining layout and evidence-discovery issues on the published APK. Treat that device report and CI as baseline evidence, then test this candidate separately.

[Baseline CI](https://github.com/modhack2003/Esuite/actions/runs/44534877678) · [Vivo review](reviews/vivo-1.0.2-production-readiness.md)

## Implementation order and acceptance

1. **Usable native workspace.** Use consistent cyan selection and accessible navigation. Keep project and status nearby, offer a reachable Cancel during a manual request, and make Browser controls adaptable to narrow, short, large-font and keyboard-open windows. Make Traffic open the selected exchange directly and return to the previous scroll position. Retain immutable source and capture completeness in the detail view.
2. **Predictable local state.** Remember the last selected project by opaque ID. Start at Home, start with identity A and keep pending editor text in memory. Clear the selection after vault erase and ignore stale IDs. Make Browser Storage identify bounded output, per-value truncation and total keys.
3. **Usable local work product.** Provide a selected-observation report with an exact preview and allowlisted metadata. Omit arbitrary copied payloads, credentials and free text, and write only to an approved local document provider. Keep the existing workspace summary. Make the device-local key and lack of portable recovery clear in the app.
4. **Build and verify.** Stage 1.0.3 as code 6 with the published 1.0.2 APK as the upgrade fixture. Run Android JVM cases, lint, optimized packaging, the supported device matrix and signed install-over-1.0.2 with the existing signer. Record the exact scope and environment of each result. Exercise large text, rotation, IME, TalkBack, process restart, vault retention, document writes and WebView-provider differences on the physical Vivo before a paid launch.

This candidate covers defensive local evidence, offline tools, and scoped manual browser/API work. The optimized candidate excludes automated payload and saved-request sequences.

## After 1.0.3

- Define a portable, user-initiated encrypted backup format before implementation. Validate password-based key derivation, format versioning, size limits, tamper rejection, atomic restore and a clean-device restore before offering it for real evidence. Retain an explicit recovery policy until the feature is verified.
- Add an on-device Diagnostics view for app/Android/WebView versions, vault size and save state, plus an explicit redacted support export. Expand TalkBack, OEM, document-provider and low-memory checks into a repeatable acceptance matrix.
- Benchmark cold start, low-memory migration and large-history navigation with repeatable synthetic workspaces. Add deterministic cancellation and save-interleaving coverage. Update Compose, WebView integration and HTTP dependencies in small increments with migration, device and performance checks for each. Keep a release dependency inventory and gate release claims on recorded results.
- Define the paid product's exact supported workflows, distribution, offline entitlement, support and refund terms. Maintain accurate privacy and dependency notices, signer continuity and an upgrade rehearsal for every release.
