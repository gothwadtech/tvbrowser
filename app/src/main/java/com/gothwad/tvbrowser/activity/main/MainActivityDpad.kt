package com.gothwad.tvbrowser.activity.main

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.core.view.isVisible
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.singleton.shortcuts.ShortcutMgr

fun MainActivity.getHeaderFocusableViews(): List<View> {
    val list = mutableListOf<View>()
    if (vb.ibMenu.isShown && vb.ibMenu.isFocusable) list.add(vb.ibMenu)
    if (vb.ibHome.isShown && vb.ibHome.isFocusable) list.add(vb.ibHome)
    if (vb.ibBack.isShown && vb.ibBack.isFocusable) list.add(vb.ibBack)
    if (vb.ibForward.isShown && vb.ibForward.isFocusable) list.add(vb.ibForward)
    if (vb.ibRefresh.isShown && vb.ibRefresh.isFocusable) list.add(vb.ibRefresh)
    val etUrl = vb.vActionBar.getUrlEditText()
    if (etUrl.isShown && etUrl.isFocusable) list.add(etUrl)
    val ibVoice = vb.vActionBar.getVoiceSearchButton()
    if (ibVoice.isShown && ibVoice.isFocusable && ibVoice.visibility == View.VISIBLE) list.add(ibVoice)
    if (vb.ibNewTab.isShown && vb.ibNewTab.isFocusable) list.add(vb.ibNewTab)
    if (vb.flTabsSwitcher.isShown && vb.flTabsSwitcher.isFocusable) list.add(vb.flTabsSwitcher)
    if (vb.ibNotes.isShown && vb.ibNotes.isFocusable) list.add(vb.ibNotes)
    if (vb.ibDownloads.isShown && vb.ibDownloads.isFocusable) list.add(vb.ibDownloads)
    if (vb.ibFileManager.isShown && vb.ibFileManager.isFocusable) list.add(vb.ibFileManager)
    if (vb.ibBookmarks.isShown && vb.ibBookmarks.isFocusable) list.add(vb.ibBookmarks)
    if (vb.ibIncognito.isShown && vb.ibIncognito.isFocusable) list.add(vb.ibIncognito)
    if (vb.ibMore.isShown && vb.ibMore.isFocusable) list.add(vb.ibMore)
    if (vb.ibSettings.isShown && vb.ibSettings.isFocusable) list.add(vb.ibSettings)
    return list
}

fun MainActivity.handleDpadKey(keyCode: Int): Boolean {
    val isNativeHomeVisible = vb.vNativeHome.isVisible
    val focus = currentFocus
    val inToolbar = focus != null && isToolbarView(focus)

    if (inToolbar) {
        val headerViews = getHeaderFocusableViews()
        val currentIndex = headerViews.indexOfFirst { it == focus || isDescendantOrSelf(focus, it) }

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
                // Header is already at top edge
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
                    sendDpadToCursor(keyCode)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (focus != null) {
                    focus.performClick()
                    return true
                }
                return false
            }
        }
        return false
    }

    if (isNativeHomeVisible) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (focus == null) {
                    vb.vNativeHome.catchFocus()
                    return true
                }
                val next = focus.focusSearch(View.FOCUS_UP)
                if (next == null || isToolbarView(next) || !isDescendantOrSelf(next, vb.vNativeHome)) {
                    val pos = vb.vNativeHome.getFocusedShortcutPosition()
                    val col = if (pos >= 0) pos % 5 else 0
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
                return false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (focus == null) {
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
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                sendDpadToCursor(keyCode)
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
