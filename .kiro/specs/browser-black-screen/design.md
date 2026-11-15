# Browser Black Screen Bugfix Design

## Overview

The live Browser screen hosts an `AndroidView`-wrapped `WebView` (fixed `height(340.dp)`) that renders as a solid black, non-painting surface instead of the loaded in-scope page. The WebView is functionally alive — JavaScript evaluation, cookie access, and the inspector tools all work — but the surface never composites visibly, so the user sees a black rectangle.

The root cause is structural, not behavioral. In `MainActivity.ESuiteApp`, the entire screen body (header row, identity chips, status text, and the `when (screen)` dispatch) lives inside one scrolling container:

```
Column(Modifier.fillMaxSize().padding(inset).imePadding().verticalScroll(pageScroll).padding(horizontal = 18.dp) ...)
```

`BrowserScreen` is rendered inside that `Column`, so its `WebView` is a child of a `verticalScroll`. A `WebView` is a `SurfaceView`/`TextureView`-backed view that manages its own scrolling and compositing; nesting it inside a measure-unbounded parent scroll produces exactly this failure mode — the surface is given a scroll-driven layout pass it cannot composite against, the page never paints, and because the `WebView` has no explicit background, the near-black window color (`Midnight = 0xFF071019`) shows through. The existing test suite does not catch this because `DeviceBoundaryTest` drives a detached, off-screen `WebView(context)` purely through `evaluateJavascript` and cookie APIs and makes no visual assertion, so a black-but-alive surface passes.

The fix has three parts, in priority order: (1) stop nesting the Browser's `WebView` inside the app-wide vertical scroll — the real fix; (2) set an explicit `WebView` background as defense-in-depth so an unpainted frame never falls through to black; (3) add a visual/pixel-sampling instrumented test that fails when the hosted surface is uniformly the window color, so the defect cannot silently regress.

The change is deliberately surgical: it touches the layout dispatch in `MainActivity.kt`, the layout and a single `setBackgroundColor` call in `BrowserScreen.kt`, and adds one instrumented test. No unrelated screens, scope/TLS/profile logic, or inspector behavior is redesigned.

## Glossary

- **Bug_Condition (C)**: The Browser `WebView` is displayed (in-scope HTTPS page loaded, MULTI_PROFILE supported, renderer alive) but its surface does not paint the page and presents a uniformly near-black (window-color) rectangle.
- **Property (P)**: When the bug condition would otherwise hold, the fixed Browser paints and displays the loaded page in its own bounded, non-scrolling region, with an explicit non-black WebView background and working in-page scroll/touch.
- **Preservation**: Every non-visual Browser behavior — exact-origin scope enforcement, TLS cancellation, MULTI_PROFILE gating, inspector tools, renderer-loss handling, metadata-only observation, absence of a JS bridge, per-identity profile isolation, dark theme on other screens, HTTPS-only — must be unchanged by the fix.
- **ESuiteApp**: The root composable in `app/src/main/java/dev/esuite/app/MainActivity.kt` that builds the `Scaffold`, the outer scrolling `Column`, and the `when (screen)` dispatch to each screen.
- **pageScroll**: The `rememberScrollState()` applied as `verticalScroll(pageScroll)` on the outer `Column` in `ESuiteApp`. This scroll is the structural host of the bug.
- **BrowserScreen**: The composable in `app/src/main/java/dev/esuite/app/browser/BrowserScreen.kt` that builds the URL field, Go/Stop/Back controls, the `AndroidView`-hosted `WebView`, the tab controls, and the output `Box`.
- **AndroidView host**: The `AndroidView(modifier = Modifier.fillMaxWidth().height(340.dp), factory = { WebView(it)... })` wrapped in `key(project.id, identity)` inside `BrowserScreen`.
- **MULTI_PROFILE gate**: `WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)`; when false, `BrowserScreen` returns the unsupported-provider message and no WebView is created.
- **ScopePolicy**: `app/src/main/java/dev/esuite/app/core/ScopePolicy.kt`; `allows(url)` / `requireAllowed(url)` enforce scheme+host+port exact-origin matching.

## Bug Details

### Bug Condition

The bug manifests when the Browser screen is shown with a supported WebView provider, an in-scope HTTPS page is loaded, and the renderer is alive — yet the WebView surface does not paint the page. The WebView is either (a) nested inside the app-wide `verticalScroll` container so its surface cannot composite and nested-scroll/touch conflicts occur, or (b) left with no explicit background so an unpainted surface falls through to the near-black window color, or both.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type BrowserRenderState {
           providerSupportsMultiProfile: boolean,
           inScopePageLoaded: boolean,
           rendererAlive: boolean,
           hostedInsideParentVerticalScroll: boolean,
           webViewHasExplicitBackground: boolean,
           surfacePaintsLoadedPage: boolean
         }
  OUTPUT: boolean

  RETURN input.providerSupportsMultiProfile
         AND input.inScopePageLoaded
         AND input.rendererAlive
         AND (input.hostedInsideParentVerticalScroll
              OR NOT input.webViewHasExplicitBackground)
         AND NOT input.surfacePaintsLoadedPage
END FUNCTION
```

### Examples

- **Battle-tested path**: User creates a project with origin `https://example.test`, opens Browser, presses Go. Expected: the page renders. Actual: a solid black rectangle where the page should be, though Console/Cookies/Elements still return live data.
- **Scroll-conflict symptom**: While the black surface is shown, dragging over the WebView region fights the outer page scroll — touch is ambiguous between scrolling the app body and scrolling the page. Expected: in-page scroll/touch works within a bounded WebView region.
- **Fall-through color**: On a frame where the surface has not yet painted, the visible color is the window background `Midnight (0xFF071019)` because the WebView has no `setBackgroundColor`. Expected: an explicit readable background, never the near-black window.
- **Edge case (unsupported provider)**: MULTI_PROFILE unsupported — `BrowserScreen` returns the unsupported message and never creates a WebView. This is NOT the bug condition and must remain unchanged.
- **Edge case (renderer gone)**: `onRenderProcessGone` fired — the WebView is nulled and the Restart action is shown. This is NOT the bug condition and must remain unchanged.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Exact-origin scope enforcement via `shouldOverrideUrlLoading`, `shouldInterceptRequest`, and `onPageStarted` (Req 3.1).
- TLS/certificate errors cancelled and never ignored via `onReceivedSslError` → `handler.cancel()` (Req 3.2).
- MULTI_PROFILE gating and the unsupported-provider message (Req 3.3).
- Inspector tools: element selection, local HTML/CSS edits, native cookie access, storage inspection, console evaluation (Req 3.4).
- Renderer-loss handling via `onRenderProcessGone` and the Restart browser renderer action (Req 3.5).
- Metadata-only browser observations recorded for in-scope requests (Req 3.6).
- No native JavaScript bridge (no `addJavascriptInterface`) (Req 3.7).
- Per-identity WebView profile isolation keyed `esuite_{project.id}_{identity}` (Req 3.8).
- The existing dark theme on every non-Browser screen (Req 3.9).
- HTTPS-only runtime: no cleartext HTTP, no trust-all TLS, `MIXED_CONTENT_NEVER_ALLOW`, no reintroduced JS bridge (Req 3.10).

**Scope:**
All inputs that do NOT trigger the paint defect must be completely unaffected by this fix. This includes:
- Navigation outside the project origin (must still be blocked).
- Any TLS/certificate failure (must still cancel the connection).
- Rendering and interaction on every screen other than Browser (Home, Traffic, API Lab, Findings, Tools), which keep the existing outer scroll and dark theme.
- The unsupported-provider and renderer-gone states, which never reach a painting WebView.

**Note:** The actual expected correct behavior (the WebView paints the page in a bounded, non-scrolling, explicitly-backgrounded region with working in-page scroll/touch) is defined in the Correctness Properties section (Property 1). This section focuses on what must NOT change.

## Hypothesized Root Cause

Based on the code, the most likely causes are:

1. **WebView nested inside a parent vertical scroll (primary)**: `ESuiteApp` wraps the whole body — including the `when (screen)` dispatch that renders `BrowserScreen` — in `Column(... .verticalScroll(pageScroll) ...)`. A `WebView` is a scrolling, surface-backed view; giving it a measure-unbounded, scroll-driven parent breaks compositing and creates nested-scroll/touch conflicts. This is the structural root cause and the reason the surface never paints the page.
   - The Browser's `AndroidView` fixes height at `340.dp`, which a `verticalScroll` parent permits, but the fixed height does not resolve the compositing/nested-scroll conflict — it only guarantees a slot for it.

2. **No explicit WebView background (secondary)**: The `WebView` is created with default settings and never calls `setBackgroundColor`. When a frame has not painted, the near-black window color (`Midnight`) shows through, so the failure presents specifically as *black* rather than as a blank/white surface. Setting a background does not fix the compositing cause but prevents the black fall-through.

3. **Detection gap (why it regressed unnoticed)**: `DeviceBoundaryTest` exercises a detached `WebView(context)` off-screen through `evaluateJavascript` and cookie APIs with no attachment to a real view hierarchy and no pixel assertion. A WebView that is alive to JS but black on screen passes every existing test.

4. **Not the cause (ruled out by code)**: Algorithmic/force-dark darkening is not implicated — at `targetSdk 37` WebView algorithmic darkening is off by default and the code never enables it. TLS handling, scope checks, and profile setup are orthogonal to painting and are not the cause.

## Correctness Properties

Property 1: Bug Condition - Hosted Browser WebView Paints the Loaded Page

_For any_ input where the bug condition holds (isBugCondition returns true: supported provider, in-scope page loaded, renderer alive, and the WebView was either nested in the parent scroll or had no explicit background), the fixed Browser SHALL host the WebView in its own bounded, non-scrolling region — not nested inside the app-wide `verticalScroll` — with an explicit non-black background set via `setBackgroundColor`, such that the loaded in-scope page paints and displays rather than presenting a uniformly near-black surface, and scroll/touch interaction works within the page.

**Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**

Property 2: Preservation - Non-Visual Browser Behavior Unchanged

_For any_ input where the bug condition does NOT hold (isBugCondition returns false), the fixed code SHALL produce the same result as the original code, preserving exact-origin scope enforcement, TLS cancellation, MULTI_PROFILE gating, inspector tools, renderer-loss handling and Restart, metadata-only observations, absence of a JS bridge, per-identity profile isolation, the dark theme on other screens, and the HTTPS-only runtime.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10**

## Fix Implementation

### Changes Required

Assuming the root cause analysis is correct, the fix removes the parent-scroll nesting for the Browser screen, gives the WebView an explicit background, and adds a visual regression test.

**File 1**: `app/src/main/java/dev/esuite/app/MainActivity.kt`

**Function**: `ESuiteApp` (the `Scaffold` body / `when (screen)` dispatch)

**Specific Changes**:
1. **Do not nest the Browser inside `verticalScroll(pageScroll)`**: Split the body layout so that the Browser screen is hosted in its own non-scrolling container that fills the available content area, while all other screens keep the existing scrolling `Column`.
   - Preferred shape: keep the header row, identity chips, and status text as a fixed (non-scrolling) top section, then host the `when (screen)` dispatch in a region that fills remaining height. For `Screen.Browser`, that region does not apply `verticalScroll`; for every other screen, wrap the content in a `verticalScroll(pageScroll)` so their current behavior is byte-for-byte preserved.
   - Keep `imePadding()` applied to the hosting container so keyboard behavior for the Browser URL/edit fields is retained; the Browser's own inner panels (output `Box`, tab controls) scroll independently, so no global scroll is needed for the Browser.
   - `pageScroll` continues to be keyed `key(screen, state.projectId, state.identity)` and is only used by the non-Browser branch; the Browser branch gets a stable, non-scrolling parent.

**File 2**: `app/src/main/java/dev/esuite/app/browser/BrowserScreen.kt`

**Function**: `BrowserScreen` (root `Column` + `AndroidView` factory)

**Specific Changes**:
2. **Give the WebView a bounded, non-scrolling slot that it owns**: The `AndroidView` continues to host the WebView, but the Browser lays out so the WebView occupies a bounded region (fixed or weighted height within the non-scrolling parent) and only the inner panels (the output `Box`, already `verticalScroll`, and the tab controls) scroll independently. The WebView must never share a scroll axis with a parent `verticalScroll`.
3. **Set an explicit WebView background (defense-in-depth)**: In the `AndroidView` `factory`, call `setBackgroundColor` on the `WebView` with a readable surface color (a light/neutral surface such as white `0xFFFFFFFF`, or the theme `Raised` surface — chosen so an unpainted frame reads as a surface, never the near-black window). This is independent of WebView algorithmic darkening, which is already default-off at `targetSdk 37`. It does not fix compositing; it guarantees an unpainted frame does not fall through to black.
4. **Leave all WebClient/ChromeClient and settings logic unchanged**: `shouldOverrideUrlLoading`, `shouldInterceptRequest`, `onPageStarted`, `onPageFinished`, `onRenderProcessGone`, `onReceivedSslError`, `onReceivedError`, `MIXED_CONTENT_NEVER_ALLOW`, `safeBrowsingEnabled`, profile setup, and the absence of `addJavascriptInterface` are untouched — the fix is layout + background only.

**File 3**: `app/src/androidTest/java/dev/esuite/app/` (new or updated instrumented test)

**Specific Changes**:
5. **Add a visual regression test** that hosts the WebView in a representative attached layout matching the fixed hosting (a non-scrolling bounded parent), loads a known in-scope page with a distinctly non-background color, and samples pixels to assert the rendered region is NOT uniformly the window background color. This complements — does not replace — `DeviceBoundaryTest`'s JS/cookie coverage. See Testing Strategy for the software-emulator and physical-device considerations.

## Testing Strategy

### Validation Approach

Two phases: first surface a counterexample that demonstrates the black surface on the unfixed hosting, then verify the fix paints the page and preserves every non-visual behavior. Because the defect is visual, the decisive new test samples rendered pixels; the preservation guarantees are mostly exercisable without a visible surface and some via property-based tests over `ScopePolicy`.

### Exploratory Bug Condition Checking

**Goal**: Surface a counterexample that demonstrates the black, non-painting surface BEFORE implementing the fix, and confirm the nesting/background root cause. If a pixel-sampling test cannot reproduce a uniformly-background region under the current nested-scroll hosting, re-hypothesize.

**Test Plan**: Host a WebView in a layout that reproduces the current nesting (WebView inside a vertical-scroll parent) with no explicit background, load an in-scope page that paints a distinct color, attach to a real window, and sample pixels in the WebView region. On the UNFIXED hosting, observe either a uniformly window-colored (near-black) region or a compositing/scroll conflict.

**Test Cases**:
1. **Nested-scroll paint test**: Host the WebView inside a `verticalScroll` parent, load an in-scope colored page, assert the region is NOT uniformly background — expected to FAIL on unfixed hosting.
2. **No-background fall-through test**: With no `setBackgroundColor`, sample an early/unpainted frame and observe the near-black window color — demonstrates the black fall-through.
3. **Touch/scroll conflict test**: With the WebView nested in the parent scroll, drive a drag over the WebView region and observe the ambiguity between page scroll and app-body scroll (may fail/behave inconsistently on unfixed hosting).
4. **Edge — unsupported provider**: Assert that with MULTI_PROFILE unsupported no WebView is created and the unsupported message shows (must hold both before and after; not the bug condition).

**Expected Counterexamples**:
- The WebView region reads as uniformly the window background color (`Midnight`) despite a loaded, JS-alive page.
- Possible causes confirmed: parent vertical-scroll nesting breaking compositing, absent explicit WebView background, nested-scroll/touch conflict.

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed Browser paints the page in a bounded, non-scrolling, explicitly-backgrounded region.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := renderFixedBrowser(input)
  ASSERT result.webViewHostedOutsideParentScroll
  ASSERT result.webViewHasExplicitNonBlackBackground
  ASSERT result.sampledRegionIsNotUniformlyWindowColor
  ASSERT result.inPageScrollAndTouchWork
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed code produces the same result as the original.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalBrowserBehavior(input) = fixedBrowserBehavior(input)
END FOR
```

**Testing Approach**: Property-based testing is well suited to the scope-enforcement invariant because `ScopePolicy.allows` is a pure function over URL strings: generate many in-origin and out-of-origin URLs and assert the allow/deny decision is unchanged by the fix. Property-based testing is also useful for confirming the layout change touches only the Browser branch (other screens' content is structurally unchanged). TLS cancellation, MULTI_PROFILE gating, renderer-loss handling, and profile isolation need instrumented tests because they depend on the WebView runtime.

**Test Plan**: Observe current behavior on the UNFIXED code for out-of-scope navigation, TLS errors, unsupported provider, renderer loss, and per-identity cookie isolation, then assert the fixed code matches.

**Test Cases**:
1. **Scope preservation (PBT)**: Over generated in/out-of-origin URLs, `ScopePolicy.allows` gives the same verdict before and after; out-of-scope main-frame navigation is still blocked in `shouldOverrideUrlLoading`/`shouldInterceptRequest`/`onPageStarted`.
2. **TLS preservation (instrumented)**: An invalid certificate still results in `handler.cancel()` and the cancellation message; the error is never ignored.
3. **Provider-gate preservation (instrumented)**: With MULTI_PROFILE unsupported, the unsupported message shows and no WebView is created.
4. **Inspector preservation (instrumented)**: Element selection, local HTML/CSS edit, native cookie read, storage read, and console evaluate still return the same shapes (reuse `DeviceBoundaryTest`'s inspector/cookie assertions).
5. **Renderer-loss preservation (instrumented)**: `onRenderProcessGone` still nulls the WebView and surfaces the Restart action.
6. **Profile-isolation preservation (instrumented)**: Cookies set under identity A are not visible to identity B's profile.
7. **Theme preservation**: Non-Browser screens still render under the existing `darkColorScheme` within the outer scroll.

### Unit Tests

- `ScopePolicy.allows`/`requireAllowed` decisions for representative in-origin vs out-of-origin URLs (scheme/host/port mismatches) — unchanged by the fix.
- Layout-dispatch logic in `ESuiteApp`: the Browser branch is hosted without `verticalScroll`; every other branch keeps it (asserted structurally where feasible).

### Property-Based Tests

- Generate URLs across schemes, hosts, ports, paths, and fragments; assert `ScopePolicy.allows` verdict parity between original and fixed builds (scope enforcement invariant, Req 3.1).
- Generate (project.id, identity) pairs and assert the derived profile key `esuite_{project.id}_{identity}` and isolation expectation are unchanged (Req 3.8).

### Integration Tests

- Full Browser flow on an attached layout: create project → open Browser → Go on an in-scope page → assert the rendered WebView region is not uniformly the window color (the decisive visual regression assertion for Req 2.1/2.3/2.5).
  - **Software-rendered emulator note**: the project runs API 37 under software rendering; `WebView` can paint to a software canvas, but SurfaceView-backed paths may not be pixel-readable the same way as on hardware. The test must capture pixels from the attached `View`/window (e.g. draw the view into a `Bitmap` or use `UiAutomation`/`PixelCopy` against the hosting window) rather than reading the raw surface, and it should assert "region is not uniformly background color" rather than exact-pixel equality so it is stable under software rendering.
  - **Physical-device verification**: because software rendering can mask or alter SurfaceView compositing, the visual assertion must also be verifiable on a physical device; the test is written to run in both environments and the fix is confirmed on at least one real device.
- Switch identities and confirm per-profile cookie isolation end-to-end (Req 3.8), reusing the existing device-test cookie assertions.
- Navigate toward an out-of-scope URL in the hosted Browser and confirm it is blocked and the block message appears (Req 3.1).
