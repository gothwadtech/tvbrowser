package com.gothwad.tvbrowser.activity.main

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.singleton.shortcuts.ShortcutMgr
import com.gothwad.tvbrowser.utils.HardwareInputManager

fun MainActivity.getHeaderFocusableViews(): List<View> {
    val list = mutableListOf<View>()
    // Menu and Home are always focusable in toolbar
    if (vb.ibMenu.isShown && vb.ibMenu.visibility == View.VISIBLE) list.add(vb.ibMenu)
    if (vb.ibHome.isShown && vb.ibHome.visibility == View.VISIBLE) list.add(vb.ibHome)
    // Only include Back/Forward if enabled, or if disabled allow navigation to pass through
    if (vb.ibBack.isShown && vb.ibBack.visibility == View.VISIBLE && vb.ibBack.isEnabled) list.add(vb.ibBack)
    if (vb.ibForward.isShown && vb.ibForward.visibility == View.VISIBLE && vb.ibForward.isEnabled) list.add(vb.ibForward)
    if (vb.ibRefresh.isShown && vb.ibRefresh.visibility == View.VISIBLE) list.add(vb.ibRefresh)
    val etUrl = vb.vActionBar.getUrlEditText()
    if (etUrl.isShown && etUrl.visibility == View.VISIBLE) list.add(etUrl)
    val ibVoice = vb.vActionBar.getVoiceSearchButton()
    if (ibVoice.isShown && ibVoice.visibility == View.VISIBLE) list.add(ibVoice)
    if (vb.ibNewTab.isShown && vb.ibNewTab.visibility == View.VISIBLE) list.add(vb.ibNewTab)
    if (vb.flTabsSwitcher.isShown && vb.flTabsSwitcher.visibility == View.VISIBLE) list.add(vb.flTabsSwitcher)
    if (vb.ibNotes.isShown && vb.ibNotes.visibility == View.VISIBLE) list.add(vb.ibNotes)
    if (vb.ibDownloads.isShown && vb.ibDownloads.visibility == View.VISIBLE) list.add(vb.ibDownloads)
    if (vb.ibFileManager.isShown && vb.ibFileManager.visibility == View.VISIBLE) list.add(vb.ibFileManager)
    if (vb.ibBookmarks.isShown && vb.ibBookmarks.visibility == View.VISIBLE) list.add(vb.ibBookmarks)
    if (vb.ibIncognito.isShown && vb.ibIncognito.visibility == View.VISIBLE) list.add(vb.ibIncognito)
    if (vb.ibMore.isShown && vb.ibMore.visibility == View.VISIBLE) list.add(vb.ibMore)
    if (vb.ibSettings.isShown && vb.ibSettings.visibility == View.VISIBLE) list.add(vb.ibSettings)
    return list
}

fun MainActivity.isTopTabBarView(view: View): Boolean {
    var p: Any? = view
    while (p is View) {
        if (p.id == R.id.llTopTabBar) return true
        p = p.parent
    }
    return false
}

fun MainActivity.handleDpadEvent(event: KeyEvent): Boolean {
    val keyCode = if (event.keyCode != 0) event.keyCode else event.scanCode
    val isNativeHomeVisible = vb.vNativeHome.isVisible
    val focus = currentFocus

    // 1. Top Tab Bar D-Pad navigation
    if (focus != null && isTopTabBarView(focus)) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    vb.ibHome.requestFocus()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (focus == vb.ibTopNewTab) {
                        return true
                    }
                    val next = focus.focusSearch(View.FOCUS_RIGHT)
                    if (next != null && (isTopTabBarView(next) || next == vb.ibTopNewTab)) {
                        next.requestFocus()
                    } else if (vb.ibTopNewTab.isVisible) {
                        vb.ibTopNewTab.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val next = focus.focusSearch(View.FOCUS_LEFT)
                    if (next != null && isTopTabBarView(next)) {
                        next.requestFocus()
                    } else if (focus == vb.ibTopNewTab && vb.rvTopTabs.childCount > 0) {
                        val lastChild = vb.rvTopTabs.getChildAt(vb.rvTopTabs.childCount - 1)
                        lastChild?.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                    focus.performClick()
                    return true
                }
            }
        }
        return false
    }

    // 2. Toolbar D-Pad navigation
    val inToolbar = focus != null && isToolbarView(focus)
    if (inToolbar) {
        val headerViews = getHeaderFocusableViews()
        val currentIndex = headerViews.indexOfFirst { it == focus || isDescendantOrSelf(focus, it) }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (currentIndex > 0) {
                        headerViews[currentIndex - 1].requestFocus()
                    } else if (currentIndex == -1 && headerViews.isNotEmpty()) {
                        headerViews.first().requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (currentIndex in 0 until headerViews.size - 1) {
                        headerViews[currentIndex + 1].requestFocus()
                    } else if (currentIndex == -1 && headerViews.isNotEmpty()) {
                        headerViews.first().requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (vb.llTopTabBar.isVisible && vb.rvTopTabs.childCount > 0) {
                        val currentTab = tabsModel.currentTab.value
                        var targetView: View? = null
                        for (i in 0 until vb.rvTopTabs.childCount) {
                            val child = vb.rvTopTabs.getChildAt(i)
                            if (child.tag == currentTab) {
                                targetView = child
                                break
                            }
                        }
                        if (targetView == null) {
                            targetView = vb.rvTopTabs.getChildAt(0)
                        }
                        targetView?.requestFocus()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isNativeHomeVisible) {
                        val total = headerViews.size.coerceAtLeast(1)
                        val ratio = if (currentIndex >= 0) currentIndex.toFloat() / total else 0f
                        val targetCol = (ratio * 5).toInt().coerceIn(0, 4)
                        vb.vNativeHome.focusShortcutAtColumn(targetCol)
                    } else {
                        vb.flWebViewContainer.requestFocus()
                        if (config.enableVirtualCursor) {
                            vb.flWebViewContainer.cursorDrawerDelegate.dispatchKeyEvent(event)
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                    focus?.performClick()
                    return true
                }
            }
        }
        return false
    }

    // 3. Native Home View D-Pad navigation
    if (isNativeHomeVisible) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (focus == null) {
                        vb.vNativeHome.catchFocus()
                        return true
                    }
                    val canMoveUp = vb.vNativeHome.navigateFocus(KeyEvent.KEYCODE_DPAD_UP)
                    if (!canMoveUp) {
                        val col = vb.vNativeHome.getFocusedShortcutColumn()
                        when (col) {
                            0 -> vb.ibHome.requestFocus()
                            1 -> vb.vActionBar.getUrlEditText().requestFocus()
                            2 -> vb.flTabsSwitcher.requestFocus()
                            3 -> vb.ibDownloads.requestFocus()
                            4 -> vb.ibBookmarks.requestFocus()
                            else -> vb.ibHome.requestFocus()
                        }
                        return true
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (focus == null) {
                        vb.vNativeHome.catchFocus()
                        return true
                    }
                    vb.vNativeHome.navigateFocus(KeyEvent.KEYCODE_DPAD_DOWN)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (focus == null) {
                        vb.vNativeHome.catchFocus()
                        return true
                    }
                    vb.vNativeHome.navigateFocus(KeyEvent.KEYCODE_DPAD_LEFT)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (focus == null) {
                        vb.vNativeHome.catchFocus()
                        return true
                    }
                    vb.vNativeHome.navigateFocus(KeyEvent.KEYCODE_DPAD_RIGHT)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                    focus?.performClick()
                    return true
                }
            }
        }
        return false
    } else {
        // 4. Web Page Active
        if (config.enableVirtualCursor) {
            // If cursor is at the very top edge and user presses DPAD UP, navigate into Toolbar
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP && vb.flWebViewContainer.cursorDrawerDelegate.isCursorNearTop()) {
                vb.ibHome.requestFocus()
                return true
            }
            // Dispatch live stream (DOWN & UP) to virtual cursor
            if (vb.flWebViewContainer.cursorDrawerDelegate.dispatchKeyEvent(event)) {
                return true
            }
        }
    }
    return false
}

fun MainActivity.isToolbarView(view: View): Boolean {
    var p: Any? = view
    while (p is View) {
        if (p.id == R.id.rlActionBar || p.id == R.id.llBottomPanel) {
            return true
        }
        p = p.parent
    }
    return false
}

fun MainActivity.isDescendantOrSelf(view: View, ancestor: View): Boolean {
    var p: Any? = view
    while (p is View) {
        if (p == ancestor) return true
        p = p.parent
    }
    return false
}

fun MainActivity.sendDpadToCursor(keyCode: Int) {
    val downTime = SystemClock.uptimeMillis()
    val eventTime = SystemClock.uptimeMillis()
    val downEvent = KeyEvent(
        downTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
        KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD
    )
    val upEvent = KeyEvent(
        downTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0, 0,
        KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD
    )
    vb.flWebViewContainer.cursorDrawerDelegate.dispatchKeyEvent(downEvent)
    vb.flWebViewContainer.cursorDrawerDelegate.dispatchKeyEvent(upEvent)
}

fun MainActivity.setupWindowCallbacks() {
    val localCallback = window.callback
    window.callback = object : Window.Callback by localCallback {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val hwInput = HardwareInputManager.getInstance(this@setupWindowCallbacks)
            if (hwInput.isDeviceBlocked(event)) {
                return true // Consume and discard blocked input device events
            }

            // Actively enforce soft keyboard suppression when typing with physical keyboard
            if (config.disableVirtualKeyboard) {
                try {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    val focused = currentFocus ?: window.decorView
                    imm?.hideSoftInputFromWindow(focused.windowToken, 0)
                } catch (e: Exception) {
                    // ignore
                }
            }

            backNavigationEventsAdapter.dispatchKeyEvent(event)
            val keyCode = if (event.keyCode != 0) event.keyCode else event.scanCode
            val keyCodeBackNavigation = keyCode == KeyEvent.KEYCODE_ESCAPE ||
                    keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK
            val shortcutMgr = ShortcutMgr.getInstance()
            val currentTab = tabsModel.currentTab.value
            if (!keyCodeBackNavigation && shortcutMgr.handle(event, this@setupWindowCallbacks, currentTab)) {
                return true
            }

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP_LEFT,
                KeyEvent.KEYCODE_DPAD_UP_RIGHT,
                KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
                KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if (handleDpadEvent(event)) {
                        return true
                    }
                }
            }

            return localCallback.dispatchKeyEvent(event)
        }

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            val hwInput = HardwareInputManager.getInstance(this@setupWindowCallbacks)
            if (hwInput.isDeviceBlocked(event)) {
                return true // Drop blocked hardware input device events
            }

            if (backNavigationEventsAdapter.dispatchGenericMotionEvent(event)) {
                return true
            }
            return localCallback.dispatchGenericMotionEvent(event)
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            val hwInput = HardwareInputManager.getInstance(this@setupWindowCallbacks)
            if (hwInput.isDeviceBlocked(event)) {
                return true // Drop blocked hardware input device events
            }
            return localCallback.dispatchTouchEvent(event)
        }
    }
}
