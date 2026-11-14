# Contributing to ESuite

Start with [the roadmap](docs/ROADMAP.md), [architecture](docs/ARCHITECTURE.md) and [UI specification](docs/UI_SPEC.md). Scope a contribution around one working user flow and state its limitations plainly.

## Development

Native: JDK 17 (CI baseline), Android SDK 35 and the committed Gradle wrapper. Run `./gradlew testDebugUnitTest lintDebug assembleDebug`. The Gradle distribution checksum is pinned. Development needs an Android build environment; the intended app runtime does not need a computer.

UI concept: Node 20+, then `cd design/prototype`, `npm ci`, `npm run typecheck`, `npm run build`. The build refreshes `docs/ui/index.html`. Open that file locally; no backend is required. The fixture notes reset on reload.

## Contribution expectations

- Keep synthetic fixtures under reserved `.test` domains. Never commit cookies, tokens, captures, private keys or real target evidence.
- Add meaningful tests for transformations, parsing, scope enforcement and state transitions. Do not treat screenshot checks as transport verification.
- Preserve honest demo/live distinctions and account isolation. Document unsupported protocol behavior.
- No automatic uploads, remote AI, external callbacks or analytics. Changes to runtime data flows must be explicit and documented.
- Preserve the existing MIT license. Identify provenance and licenses for third-party components.
- Include what changed, why, relevant verification, and any actual limitations in the PR description.
