# ESuite 1.0.2 — browser viewport and evidence reliability

Install over production 1.0.1 to retain the encrypted workspace. This update uses the existing production signing certificate.

## Fixed

- The browser page has a bounded viewport outside the inspector's scrolling container. Page and inspector tabs retain the same isolated WebView, explicitly hidden and clipped outside Page; Page displays the actual website, while Elements, Cookies, Storage and Console have their own reachable scrolling area. An explicit readable blank background is paired with a page-pattern screenshot regression that a blank view cannot pass.
- Encrypted-save status acknowledges the actual committed workspace snapshot. Durable edits queue a save immediately, pending/failed edits stay visible as unsaved, and the UI cannot mistake an older project's save for a newer finding's commit. Background save is best effort; arbitrary process death before the saved indicator can still lose pending edits.
- Deployed version-1 workspace documents migrate through the version-2 schema while retaining all evidence and identity configuration. Unknown newer or corrupt documents fail closed without replacing the encrypted file or key.
- Loss/invalidation of the Keystore key during an open session now rejects a save without generating a replacement key or overwriting the existing vault.
- History pruning returns the snapshot actually persisted. Reconciliation removes only the pruned snapshot IDs and preserves captures/edits arriving while storage is busy. Oversized durable data fails before a vault write begins. History retention adapts to the device heap within the existing 32 MiB maximum; only exchange history is automatically pruned. Notes, collections and identity configuration are rejected at capacity instead of being removed. Streaming size checks and exact byte allocation avoid constructing an oversized whole-vault text buffer; ciphertext reading and commit verification avoid redundant large copies.
- OpenAPI/HAR parsing and import budget encoding run off the UI thread. Prepared imports commit only against the validated durable state and originating project/workspace; conflicts are revalidated off main with bounded retries. Imports never execute target requests.
- Truncated text omits an incomplete final character without silently dropping malformed interior server bytes. UTF-8, UTF-16 and Shift_JIS boundaries are regression-tested; binary capture and existing caps are retained.
- Large encrypted workspaces use bounded cipher updates to avoid an Android 9 Keystore output-loss bug. Exact output lengths are checked before saving; the existing encrypted format and key remain compatible.
- Four-list native parameter controls, Android ICU marker parsing and explicit inspector truncation indicators are included from the verified unreleased follow-up.

## Verification gates

Publication requires the exact-source JVM/lint/debug/release build, page-side inspector checks and the API 28–37.0 emulator matrix. The independent UI harness verifies real HTTPS/browser controls, visible synthetic page pixels, API/profile isolation, committed-save acknowledgement and process restart. The exact production-signed APK additionally passes API 28/35/37.0 install-over-1.0.1 encrypted-evidence retention checks. Unsupported WebView profile capabilities are explicit skips.

## Remaining boundaries

No root or computer is required during use. Processing/storage stays on device, with normal HTTPS system trust and no telemetry. Browser history remains request metadata only. Full proxy/VPN/TLS interception, cloud-account auditing, physical/OEM/accessibility validation and broader lifecycle/export checks remain tracked in docs/AGENT_HANDOFF.md. A browser fixture pass does not establish rendering compatibility with every website/provider. No TLS-pinning bypass is claimed.

Verify the APK with SHA256SUMS and build-info.json against the durable public certificate in docs/RELEASING.md. Internal/debug/CI-signed APKs are not production updates.
