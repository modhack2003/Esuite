# ESuite 1.0.4

- Open the active project from a streamlined Home. Switch projects from one menu and reach Browser, API Lab, Traffic and Findings directly.
- API Lab separates Request, Response, Saved and Session. Sending opens the response, where headers and body are presented separately. Large bodies open with a bounded preview and can be expanded.
- Search saved observations by title or note. Open one to review attached evidence and preview a redacted local report.
- Tools groups text transformations, imports and offline reviews into separate views. Calculations, report preparation and durable workspace size checks run off the UI thread. Repeated saves of an already committed snapshot skip a vault rewrite.
- Browser keeps its adaptive viewport and identity-specific drafts. Traffic displays the recorded exchange and its capture limits. The launch screen now uses the app's dark theme.

This version supports manual scoped HTTPS inspection and offline review. Workspace data stays on the device. Android 9–17 is the supported build range, and separate browser profiles depend on the installed WebView provider.

The published upgrade fixture is 1.0.2. The release process verifies the existing signer and encrypted-data retention before publication.
