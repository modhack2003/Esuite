# Bugfix Requirements Document

## Introduction

In ESuite 1.0.1 the live Browser screen's WebView renders as a solid black, non-painting surface instead of the loaded in-scope page. The WebView is functionally alive (JavaScript, cookies, and inspection still work), but nothing is painted on screen, so the user sees a black rectangle where the target page should be. The defect blocks the core browsing-and-inspection workflow and is not caught by the current test suite, which exercises detached, off-screen WebViews through JavaScript and cookie APIs only and makes no visual assertion.

The bug condition C(X) is: the Browser WebView is displayed but does not paint the loaded in-scope page, presenting a black surface. Non-buggy inputs (¬C(X)) are every other Browser behavior — scope enforcement, TLS handling, profile isolation, and the inspection tools — which must be preserved exactly.

## Bug Analysis

### Current Behavior (Defect)

What currently happens when the Browser screen loads an in-scope page.

1.1 WHEN an in-scope HTTPS page is loaded in the Browser screen THEN the system renders the WebView as a solid black, non-painting surface instead of the loaded page
1.2 WHEN the Browser screen is displayed THEN the system hosts the fixed-height WebView inside the app-wide vertical-scroll container, so the WebView surface fails to composite and scroll/touch conflicts occur
1.3 WHEN the WebView surface fails to paint THEN the system shows the near-black window color through it because the WebView has no explicit background
1.4 WHEN the existing tests run against the Browser WebView THEN the system reports success because the WebView is exercised detached and off-screen through JavaScript and cookie APIs with no visual assertion, so a black but functionally-alive surface goes undetected

### Expected Behavior (Correct)

What should happen instead when the Browser screen loads an in-scope page.

2.1 WHEN an in-scope HTTPS page is loaded in the Browser screen THEN the system SHALL paint and display that page rather than a black surface
2.2 WHEN the Browser screen is displayed THEN the system SHALL host the WebView in its own bounded, non-scrolling layout so the WebView is not nested inside a vertical-scroll container
2.3 WHEN the WebView surface does not paint THEN the system SHALL present an explicit WebView background so an unpainted surface does not fall through to black
2.4 WHEN an in-scope page is loaded and displayed THEN the system SHALL allow scroll and touch interaction within the page
2.5 WHEN automated tests run against the hosted Browser WebView THEN the system SHALL include a visual/pixel or screenshot assertion that fails when the WebView surface is black, so this defect cannot silently regress on either a physical device or the software-rendered emulator

### Unchanged Behavior (Regression Prevention)

Existing Browser behavior that must be preserved for inputs that do not trigger the bug.

3.1 WHEN a navigation targets a URL outside the project's exact origin THEN the system SHALL CONTINUE TO block it via shouldOverrideUrlLoading, shouldInterceptRequest, and onPageStarted
3.2 WHEN a TLS or certificate error occurs THEN the system SHALL CONTINUE TO cancel the connection and never ignore the error
3.3 WHEN the WebView provider does not support MULTI_PROFILE THEN the system SHALL CONTINUE TO gate the browser and show the unsupported-provider message
3.4 WHEN the inspector tools are used THEN the system SHALL CONTINUE TO provide element selection, local HTML and CSS edits, native cookie access, storage inspection, and console evaluation
3.5 WHEN the WebView renderer process is lost THEN the system SHALL CONTINUE TO handle it via onRenderProcessGone and offer the Restart browser renderer action
3.6 WHEN an in-scope request is observed during browsing THEN the system SHALL CONTINUE TO record metadata-only browser observations
3.7 WHEN a page runs in the WebView THEN the system SHALL CONTINUE TO operate with no native JavaScript bridge
3.8 WHEN the active identity changes THEN the system SHALL CONTINUE TO isolate cookies and storage per-identity WebView profile
3.9 WHEN any screen other than the Browser is displayed THEN the system SHALL CONTINUE TO use the existing dark theme
3.10 WHEN the Browser runs THEN the system SHALL CONTINUE TO enforce HTTPS-only with no cleartext HTTP, no trust-all TLS, and no reintroduced JavaScript bridge
