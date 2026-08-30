# AI Studio Prompt — Gothwad TV Browser: Native Zoom, Remove Forced Dark Mode, Native Smooth Scrolling

You are working on the Gothwad TV Browser Android TV project (WebView-based). The goal of this task is simple: **websites must render and behave exactly as they do in stock Chrome/WebView — no custom CSS hacks, no forced color/theme changes, no custom scroll/zoom logic that diverges from the native WebView/Chromium engine's own behavior.** Implement the following fixes completely and correctly. Do not leave partial implementations or TODOs. Do not break existing functionality that already works correctly (ad blocking, `onRenderProcessGone` crash recovery, DPAD navigation, popup blocking, clipboard manager, shortcuts, mouse input) — re-verify these still work after your changes.

## Task 1 — Replace the custom CSS `zoom` hack with WebView's native zoom API

`WebViewEx.kt`'s `applyWebPageZoom()` currently injects custom JavaScript/CSS into every page (`html { zoom: $scale; width: ...%; min-width: ...%; }` via `WebViewCompat.addDocumentStartJavaScript` and `evaluateJavascript`) to implement a TV-remote-controlled zoom-in/zoom-out feature. This causes two confirmed bugs:
1. It forces a browser layout reflow on every page (unlike Chrome's native zoom, which is a GPU-level render-scale that never touches page layout), causing scrolling and zooming to feel less smooth than stock Chrome.
2. It breaks the coordinate calculation of `position: fixed` / `position: absolute` elements on some sites (confirmed reproducible on google.com: the account/apps-grid dropdown card renders in the wrong screen position when this zoom script is active, and renders correctly when zoom is reset to 100%, proving the CSS zoom injection is the cause).

Fix this by using WebView's already-enabled native zoom capability instead of any custom CSS/JS injection:

1. Remove the `applyWebPageZoom()` CSS/JS injection entirely — delete the `zoomScript` string, all calls to `WebViewCompat.addDocumentStartJavaScript`/`evaluateJavascript` for zoom, and the `documentStartZoomScriptRef` field.
2. The WebView already has `setSupportZoom(true)` and `builtInZoomControls = true` enabled (confirmed in `WebViewEx.kt` init block) — this is the same native, GPU-accelerated, layout-untouched zoom mechanism Chrome itself uses. Wire the app's TV-remote zoom-in/zoom-out UI controls (wherever they currently call `applyWebPageZoom()`) to instead call WebView's native `zoomIn()` / `zoomOut()` methods, or `zoomBy(factor)` for a specific target scale level.
3. Keep `settings.textZoom` usage only for actual font-size/accessibility text scaling if that is a separate, intentionally distinct feature from page zoom — do not conflate the two. If `textZoom` was only ever being driven by the same "page zoom" feature being removed here, remove that coupling too and leave `textZoom` at 100 (default) unless there's a separate accessibility text-size setting for it.
4. Confirm zoom state (current zoom level) is tracked per-tab correctly using WebView's own zoom APIs (`canZoomIn()`/`canZoomOut()` to enable/disable the remote's zoom buttons appropriately at min/max zoom), not any custom state variable tied to the old CSS approach.
5. Verify after this change: zooming in/out on any website (including google.com with its account dropdown open) keeps all dropdowns, fixed-position elements, and page layout in their correct, original positions, exactly as they'd appear in stock Chrome at the same zoom level.

## Task 2 — Remove forced/algorithmic dark mode so websites always show their true original colors

`WebViewEx.kt` currently applies Chromium's "Algorithmic Darkening" / "Force Dark" feature whenever the device's system UI is in night/dark mode:
```kotlin
val allowDarkening = config.webviewUseAlgorithmicDarkeningWithDarkUiMode  // defaults to true
WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, uiNightMode == NIGHT_YES && allowDarkening)
// or, on older API levels: WebSettingsCompat.setForceDark(...)
```
This causes every website to have its colors automatically inverted/darkened by Chromium's heuristic dark-mode algorithm whenever the TV's system theme is set to dark (which is the default on many custom Android TV OS builds). This is confirmed as the cause of websites (e.g. google.com) rendering with wrong/inverted colors — dark navy backgrounds, discolored icons/images — instead of their true original appearance.

The user wants websites to render with their exact original colors always, matching stock Chrome/WebView behavior with no forced theme applied — regardless of the TV's system dark/light mode setting.

1. Remove the algorithmic-darkening / force-dark logic entirely from `WebViewEx.kt`'s init block — do not call `WebSettingsCompat.setAlgorithmicDarkeningAllowed()` or `WebSettingsCompat.setForceDark()` with any value that would alter page colors. Websites should render exactly as their own CSS/HTML specifies, with no engine-level color modification, matching how stock Chrome behaves by default (Chrome does not force-invert page colors based on system theme either — pages control their own light/dark appearance via their own CSS, e.g. `prefers-color-scheme` media query, which WebView already respects natively without any extra code).
3. Remove the `webviewUseAlgorithmicDarkeningWithDarkUiMode` config flag and any related Settings UI toggle for it, since this entire forced-darkening mechanism is being removed — it should not be a user-facing option anymore, it should simply never happen.
4. Do not replace this with any other custom color/theme injection (no custom "dark mode" CSS injected into pages either) — the requirement is zero engine-level or app-level color manipulation of website content. The browser's own native UI chrome (toolbar, tabs, settings screens) can keep its own app theme — this task is only about not altering rendered website page content colors.

## Task 3 — Ensure scrolling matches native Chrome/WebView smoothness

Audit the codebase for anything that could make scrolling less smooth than stock WebView/Chrome, and fix or remove it:

1. Confirm hardware acceleration remains enabled for the WebView and its container views (`android:hardwareAccelerated="true"` in the manifest, and no `View.LAYER_TYPE_SOFTWARE` set anywhere on the WebView or its parent `CursorLayout`/`flWebViewContainer`).
2. Review `CursorLayout.dispatchTouchEvent()` and `dispatchGenericMotionEvent()` (in `app/common/src/main/java/com/gothwad/tvbrowser/widgets/cursor/CursorLayout.kt`): confirm that for normal touch-scroll/fling gestures, these overrides add no meaningful per-event overhead before delegating to `super.dispatchTouchEvent(ev)` — the `cursorDrawerDelegate.hideCursor()` call on every touch event should be cheap (it currently just flips a couple of fields and calls `postInvalidate()`, not a full redraw) — confirm this remains true and doesn't regress into doing per-scroll-frame heavy work.
3. Ensure no JavaScript is injected on every page (`generic_injects.js` or elsewhere) that attaches expensive `scroll` or `touchmove` event listeners which could cause jank by running on every scroll frame — audit this file specifically and remove/optimize anything that listens to high-frequency scroll/touch events unnecessarily.
4. Confirm `WebView.setOverScrollMode()` and edge-effect/fling behavior are left at their WebView defaults (do not disable overscroll glow/fling unless that was an explicit, separate design decision unrelated to this task).
5. Do not introduce any new custom touch-interception, gesture-detection, or scroll-listener layer as part of this task — the goal is parity with stock WebView scroll behavior, achieved by removing interference, not by adding new custom scroll-handling code.

## Verification checklist (must all pass before you consider this done)

- Zooming in/out via the TV remote's zoom controls on any website feels as smooth as pinch-zooming in stock Chrome, and never displaces dropdowns, menus, or fixed-position page elements from their correct location.
- Opening google.com (or any site) shows its true, unmodified original colors regardless of whether the TV's system theme is set to light or dark mode.
- No Settings toggle for "algorithmic darkening" / forced dark mode remains anywhere in the app.
- Scrolling on a long webpage feels smooth and matches stock Chrome/WebView behavior, with no visible jank introduced by app-specific code.
- Existing ad blocking, `onRenderProcessGone` crash recovery, DPAD navigation, clipboard manager, shortcut recording, and native mouse input all continue to work exactly as before.
- No new custom CSS, JavaScript-based theme/color injection, or custom scroll-handling logic exists anywhere in the codebase as a result of this task — the explicit goal is removing app-specific website rendering behavior, not replacing it with different app-specific behavior.

Implement all three tasks fully in this session. Do not summarize what you would do — actually write and wire up the code.
