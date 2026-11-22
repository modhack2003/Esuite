# ESuite 1.0.5 browser refresh

1.0.4 initialized Browser only after a saved project was selected. It also disabled the entire tab when the system WebView lacked multi-profile support. The 1.0.5 design opens directly to a standalone HTTPS browser that uses Android WebView's app-private default profile on every supported provider.

- The address bar opens an HTTPS URL or sends an explicit search to DuckDuckGo. Navigation and an on-device developer tools panel sit beside the real page viewport.
- The inspector presents page and element details, cookie names and supported flags, storage key metadata, console output and same-origin request metadata. It uses the current page and an in-memory buffer. Loading shows a skeleton and progress.
- Saved traffic, API requests and findings remain available by site. The internal vault records and API origin checks stay compatible with 1.0.4. The app opens API Lab from an explicitly selected HTTPS URL.
- Settings provides appearance, browsing and saved-data controls, version, WebView details and credits.

Design review used the existing ESuite brand and native Compose implementation. The repository's design-service authentication instruction keeps the implementation local. Production delivery requires the exact-source device matrix and a production-signed upgrade from 1.0.4 with retained encrypted evidence.
