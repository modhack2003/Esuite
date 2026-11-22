# ESuite 1.0.3 — local evidence and native usability

## Changes

- Traffic opens exchange details immediately and returns to the same list position. Details show the originating project, identity, source and capture limits. History clearing prompts for confirmation and retains evidence saved with observations.
- Browser tabs expose selected state and scroll at large font sizes. Short windows use compact address and navigation controls. Workspace status and the operative browser address are reachable from the browser. Address, script, page-edit and cookie text stay in their respective in-memory editors.
- Storage previews show how many keys were returned or omitted and flag shortened fields, including values that exceed the total preview budget.
- Findings can preview and save a report for one observation to a device document provider. The report contains the origin, timestamps, generated IDs and allowlisted evidence metadata. Report data is visible before file selection.
- ESuite remembers the last available project by ID. Fresh launches open Home with identity A. An in-flight manual API request exposes its origin context and a shared Cancel action across modules.
- The encrypted workspace retains bounded finding evidence snapshots. Navigation preserves technical and finding drafts in memory. Loaded WebView profiles are queued for cleanup when a provider requires a process restart.

## Release requirements

Publication requires exact-source Android build, lint and device results, plus install-over-1.0.2 validation using the production signing certificate. Android 9–17 remains the target range. Some WebView providers lack isolated profiles, and the corresponding device tests record capability skips.

The optimized app supports scoped manual HTTPS and offline review. Browser history consists of request metadata. The vault key lives on the device, so report export is the available way to retain a summary outside ESuite. The [1.0.3 plan](https://github.com/modhack2003/Esuite/blob/main/docs/RELEASE_1.0.3_PLAN.md) tracks the remaining physical-device and recovery work.
