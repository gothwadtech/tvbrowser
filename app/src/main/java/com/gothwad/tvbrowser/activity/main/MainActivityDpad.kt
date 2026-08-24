package com.gothwad.tvbrowser.activity.main

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.singleton.shortcuts.ShortcutMgr

fun MainActivity.handleDpadKey(keyCode: Int): Boolean {
    val isNativeHomeVisible = vb.vNativeHome.visibility == View.VISIBLE

    if (isNativeHomeVisible) {
        val focus = currentFocus
        val inToolbar = focus != null && isToolbarView(focus)

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (focus == null) {
                    vb.vNativeHome.catchFocus()
                    return true
                }
                if (!inToolbar) {
                    val next = focus.focusSearch(View.FOCUS_UP)
                    if (next == null || isToolbarView(next)) {
                        vb.ibHome.requestFocus()
                        return true
                    }
                }
                return false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (focus == null || inToolbar) {
                    vb.vNativeHome.catchFocus()
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (focus == null) {
                    vb.vNativeHome.catchFocus()
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (focus != null) {
                    focus.performClick()
                    return true
                }
                return false
            }
        }
    } else {
        // Web page is showing
        val focus = currentFocus
        val inToolbar = focus != null && isToolbarView(focus)

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (inToolbar) {
                    val next = focus?.focusSearch(View.FOCUS_UP)
                    if (next != null && next != focus) {
                        next.requestFocus()
                    }
                    return true
                } else {
                    sendDpadToCursor(keyCode)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (inToolbar) {
                    hideMenuOverlay(false)
                    vb.flWebViewContainer.requestFocus()
                    return true
                } else {
                    sendDpadToCursor(keyCode)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (inToolbar) {
                    val next = focus?.focusSearch(View.FOCUS_LEFT)
                    if (next != null && next != focus) {
                        next.requestFocus()
                    }
                    return true
                } else {
                    sendDpadToCursor(keyCode)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (inToolbar) {
                    val next = focus?.focusSearch(View.FOCUS_RIGHT)
                    if (next != null && next != focus) {
                        next.requestFocus()
                    }
                    return true
                } else {
                    sendDpadToCursor(keyCode)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (inToolbar) {
                    focus?.performClick()
                    return true
                } else {
                    sendDpadToCursor(keyCode)
                    return true
                }
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
            backNavigationEventsAdapter.dispatchKeyEvent(event)
            val keyCode = if (event.keyCode != 0) event.keyCode else event.scanCode
            val keyCodeBackNavigation = keyCode == KeyEvent.KEYCODE_ESCAPE ||
                    keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK
            val shortcutMgr = ShortcutMgr.getInstance()
            val currentTab = tabsModel.currentTab.value
            if (!keyCodeBackNavigation && shortcutMgr.handle(event, this@setupWindowCallbacks, currentTab)) {
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (handleDpadKey(keyCode)) {
                            return true
                        }
                    }
                }
            }
            return localCallback.dispatchKeyEvent(event)
        }

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
            if (backNavigationEventsAdapter.dispatchGenericMotionEvent(event)) {
                return true
            }
            return localCallback.dispatchGenericMotionEvent(event)
        }
    }
}
