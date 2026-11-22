# ESuite 1.0.5 browser refresh

1.0.4 initialized Browser only after a saved project was selected. It also disabled the entire tab when the system WebView lacked multi-profile support. The 1.0.5 design opens directly to a standalone HTTPS browser that uses Android WebView's app-private default profile on every supported provider.

- The address bar opens an HTTPS URL or sends an explicit search to DuckDuckGo. Navigation and an on-device developer tools panel sit beside the real page viewport.
- The inspector presents page and element details, cookie names and supported flags, storage key metadata, console output and same-origin request metadata. It uses the current page and an in-memory buffer. Loading shows a skeleton and progress.
- Saved traffic, API requests and findings remain available by site. The internal vault records and API origin checks stay compatible with 1.0.4. The app opens API Lab from an explicitly selected HTTPS URL.
- Existing website sign-ins stay in their legacy WebView profiles. Sites opened in the standalone Browser use its own profile and may prompt for sign-in again.
- Settings provides appearance, browsing and saved-data controls, version, WebView details and credits.

Design review used the existing ESuite brand and native Compose implementation. The repository's design-service authentication instruction keeps the implementation local. Production delivery requires the exact-source device matrix and a production-signed upgrade from 1.0.4 with retained encrypted evidence.

## Verification

- The local debug and release variants each passed 79 JVM tests. Lint, optimized assembly and release guard fixtures passed. Chromium checked the document outline and storage metadata against a local fixture.
- Exact source `d8489f709d5951d8c0f6025deb8e9d2e0e26f2e9` passed all ten Android device jobs with 186 passes, 24 legacy-profile capability skips and zero failures. The new Browser flow passed on every tested API, including API 28. [Exact-source device run](https://github.com/modhack2003/Esuite/actions/runs/45060829736)
- The production-signed build passed all 18 checks across API 28, 35 and 37.0, including standalone Browser and in-place upgrade from signed 1.0.4 with encrypted evidence retained. [Signed release run](https://github.com/modhack2003/Esuite/actions/runs/45061731499)
- The public 2,786,393-byte APK matched the signed workflow artifact byte for byte. The public checksums, production certificate, version code 8, source metadata and 16 KiB APK alignment were verified. [Published APK and checksums](https://github.com/modhack2003/Esuite/releases/tag/v1.0.5)

APK SHA-256 `092fb8a15b60fd34d9cc6762c0d070cf8f4313712de112ca5477ac61a9de05e4`.
