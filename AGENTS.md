# ESuite agent handoff

Read `docs/AGENT_HANDOFF.md`, `docs/ROADMAP.md`, and the latest CI result before editing. The full requested scope remains in `docs/PRODUCT_PLAN.md`.

- Product: unrooted Android, no computer during use, local processing and storage. No telemetry, hosted AI or automatic evidence uploads.
- Continue implementation rather than redesigning the completed visual concept. Do not invoke design-service authentication unless the user requests it.
- Never mark fixture UI, metadata-only observations or reconstructed requests as complete network interception. Do not claim TLS-pinning bypass.
- Keep exact project origin scope and separate identity cookie jars. Do not add trust-all TLS or automatic cross-origin credential forwarding.
- Make small coherent commits, run meaningful tests, and update the handoff/task checklist in each part. Push completed parts when authorized by the user; that authorization has been given for this repository.
- Preserve MIT licensing and verify third-party component licenses before integrating a VPN/native stack.
- Android verification: `./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease`. Device checks use the internal test bundle and independent `ui-tests` APK on API 28–37.0. Read `docs/RELEASING.md` before release work; never publish an unsigned/debug/CI-signed APK or change the durable production signer. Device verification is distinct from a successful build.
- Web design concept verification: `cd design/prototype && npm ci && npm run typecheck && npm run build`; `python tools/verify_ui.py`.
- Do not commit tokens, private keys, captures, device data, SDK paths or generated APKs. The design prototype remains a synthetic reference, separate from native runtime capability.
- Local Java/Chromium sockets can be restricted in this environment. Use the repository CI for checks when necessary, and record actual evidence rather than inventing a pass.

Keep unfinished work explicit, with acceptance criteria and the next concrete step. Never erase unchecked scope merely because an increment ships.
