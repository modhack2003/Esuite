# ESuite 1.0.6 browser tools

The 1.0.5 inspector deliberately hid the native WebView when it opened. Standalone browsing also left `useWideViewPort` disabled, so pages had no way to use their declared mobile viewport through that setting. The Chrome comparison of the reported portfolio URL showed its experience chooser and also reproduced React hydration error #418 in desktop and mobile Chromium.

1. Keep the same WebView attached while opening Developer Tools or general Tools. Dock below the page in portrait and beside it in wide or short landscape layouts. Preserve separate split sizes and keep a usable part of the page visible.
2. Expand current-page inspection with element selection and editing, sources, resource timing, navigation performance, viewport measurements, security context and basic document semantics. Support explicit JavaScript expressions and script bodies in Console, including Promise results and separate warning and error labels. Debugger breakpoints, CPU/heap profiling and full request/response recording require different browser-engine integrations.
3. Honor mobile viewport declarations, enable pinch zoom, reveal committed pages while other resources load and track same-document navigation. A rejected address or link leaves the live page available.
4. Apply a charcoal and soft-blue visual system with Inter, a branded launch state, compact toolbar and reusable dock, tab, console and navigation components. Inter is distributed under its [SIL Open Font License](../app/src/main/res/raw/inter_ofl.txt). The font is the upstream file from [Google Fonts](https://github.com/google/fonts/tree/0b58fb370093f9a9f4ff785d94405710b79de67c/ofl/inter).

## Verification

- Check the release APK’s live viewport and drag gesture, console results and errors, utility dock, mobile viewport, saved data and same-signer upgrade from 1.0.5.
