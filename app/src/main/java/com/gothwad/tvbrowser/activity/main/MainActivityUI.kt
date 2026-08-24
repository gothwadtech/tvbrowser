package com.gothwad.tvbrowser.activity.main

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.downloads.DownloadsActivity
import com.gothwad.tvbrowser.activity.history.HistoryActivity
import com.gothwad.tvbrowser.activity.main.dialogs.ChromeMenuPopup
import com.gothwad.tvbrowser.activity.main.dialogs.ShortcutDialog
import com.gothwad.tvbrowser.activity.main.dialogs.favorites.FavoriteEditorDialog
import com.gothwad.tvbrowser.activity.main.dialogs.favorites.FavoritesDialog
import com.gothwad.tvbrowser.filemanager.FileManagerActivity
import com.gothwad.tvbrowser.model.FavoriteItem
import com.gothwad.tvbrowser.notes.NotesActivity
import com.gothwad.tvbrowser.notes.clipboard.ClipboardActivity
import com.gothwad.tvbrowser.settings.SettingsDialog
import com.gothwad.tvbrowser.singleton.shortcuts.Shortcut
import com.gothwad.tvbrowser.utils.Utils
import com.gothwad.tvbrowser.utils.VoiceSearchHelper
import com.gothwad.tvbrowser.webengine.WebEngineFactory

internal fun MainActivity.showMenuOverlay() {
    if (isFullscreen) return
    vb.vActionBar.dismissExtendedAddressBarMode()
    vb.rlActionBar.visibility = View.VISIBLE
    vb.rlActionBar.animate()
        .translationY(0f)
        .setDuration(220)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

internal fun MainActivity.hideMenuOverlay(hideBottomButtons: Boolean = true) {
    if (isFullscreen || vb.vNativeHome.isVisible || vb.rlActionBar.visibility != View.VISIBLE) return
    val hideHeight = if (vb.rlActionBar.height > 0) vb.rlActionBar.height.toFloat() else Utils.D2P(this, 50f)
    vb.rlActionBar.animate()
        .translationY(-hideHeight)
        .setDuration(220)
        .setInterpolator(AccelerateInterpolator())
        .start()
}

internal fun MainActivity.toggleMenu() {
    if (isFullscreen) return
    if (vb.rlActionBar.translationY < 0f) {
        showMenuOverlay()
    } else {
        hideMenuOverlay()
    }
}

internal fun MainActivity.setupHeaderClickListeners(incognitoMode: Boolean) {
    vb.ibMenu.setOnClickListener { showChromeMenu(vb.ibMenu) }
    vb.ibHome.setOnClickListener {
        if (vb.vNativeHome.visibility == View.VISIBLE) {
            vb.vNativeHome.scrollToTop()
        } else {
            showHomeScreen()
        }
    }
    vb.ibNewTab.setOnClickListener {
        openInNewTab(settingsModel.homePage, tabsModel.tabsStates.size, needToHideMenuOverlay = false, navigateImmediately = true)
    }
    vb.flTabsSwitcher.setOnClickListener { showTabsRowDialog() }
    vb.ibNotes.setOnClickListener { startActivity(Intent(this, NotesActivity::class.java)) }
    vb.ibBack.setOnClickListener { navigateBack() }
    vb.ibForward.setOnClickListener {
        val tab = tabsModel.currentTab.value ?: return@setOnClickListener
        val isHome = vb.vNativeHome.isVisible || tab.url == Config.HOME_URL_ALIAS || tab.url.isEmpty()
        if (isHome && !tab.lastUrlBeforeHome.isNullOrEmpty()) {
            val restoreUrl = tab.lastUrlBeforeHome!!
            tab.lastUrlBeforeHome = null
            navigate(restoreUrl)
        } else if (tab.webEngine.canGoForward()) {
            tab.webEngine.goForward()
        }
    }
    vb.ibRefresh.setOnClickListener { refresh() }
    vb.ibDownloads.setOnClickListener { showDownloads() }
    vb.ibFileManager.setOnClickListener { startActivity(Intent(this, FileManagerActivity::class.java)) }
    vb.ibBookmarks.setOnClickListener { showFavorites() }
    vb.ibIncognito.setOnClickListener { toggleIncognitoMode(true) }
    vb.ibMore.setOnClickListener { showChromeMenu(vb.ibMore) }
    vb.ibSettings.setOnClickListener { showSettingsDialog() }

    if (incognitoMode) {
        vb.rlActionBar.setBackgroundColor(Color.parseColor("#1F1F1F"))
        vb.ibIncognito.imageTintList = ColorStateList.valueOf(Color.parseColor("#0494F4"))
    }

    vb.vActionBar.callback = this

    listOf(
        vb.ibMenu, vb.ibHome, vb.ibBack, vb.ibForward, vb.ibRefresh,
        vb.ibNewTab, vb.flTabsSwitcher, vb.ibNotes, vb.ibDownloads,
        vb.ibFileManager, vb.ibBookmarks, vb.ibIncognito, vb.ibMore, vb.ibSettings
    ).forEach {
        it.isFocusable = true
        it.isFocusableInTouchMode = true
        it.setOnTouchListener(bottomButtonsOnTouchListener)
        it.onFocusChangeListener = bottomButtonsFocusListener
        it.setOnKeyListener(bottomButtonsKeyListener)
    }
}

internal fun MainActivity.setupSettingsSubscriptions() {
    config.userAgentString.subscribe(this.lifecycle, false) {
        for (tab in tabsModel.tabsStates) {
            tab.webEngine.userAgentString = it
        }
    }

    config.theme.subscribe(this.lifecycle, false) {
        when (it) {
            Config.Theme.BLACK_AMOLED,
            Config.Theme.BLACK_CHARCOAL,
            Config.Theme.BLACK_MIDNIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            Config.Theme.WHITE_PURE,
            Config.Theme.WHITE_WARM,
            Config.Theme.WHITE_COOL -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        WebEngineFactory.onThemeSettingUpdated(it)
    }

    settingsModel.keepScreenOn.subscribe(this.lifecycle) {
        if (it) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    viewModel.homePageLinks.subscribe(this) {
        val currentUrl = tabsModel.currentTab.value?.url ?: return@subscribe
        if (Config.HOME_PAGE_URL == currentUrl) {
            tabsModel.currentTab.value?.webEngine?.reload()
        }
    }
}

internal fun MainActivity.initiateVoiceSearchInternal() {
    hideMenuOverlay()
    voiceSearchHelper.initiateVoiceSearch(object : VoiceSearchHelper.Callback {
        override fun onResult(text: String?) {
            if (text == null) {
                Utils.showToast(this@initiateVoiceSearchInternal, getString(R.string.can_not_recognize))
                return
            }
            search(text)
            hideMenuOverlay()
        }
    })
}

internal fun MainActivity.onEditHomePageBookmark(favoriteItem: FavoriteItem) {
    FavoriteEditorDialog(this, object : FavoriteEditorDialog.Callback {
        override fun onDone(item: FavoriteItem) {
            viewModel.onHomePageLinkEdited(item)
        }
    }, favoriteItem).show()
}

internal fun MainActivity.showFavoritesDialog() {
    val tab = tabsModel.currentTab.value
    FavoritesDialog(
        this,
        lifecycleScope,
        object : FavoritesDialog.Callback {
            override fun onFavoriteChoosen(item: FavoriteItem?) {
                item?.url?.let { navigate(it) }
            }
        },
        tab?.title,
        tab?.url
    ).show()
    hideMenuOverlay()
}

internal fun MainActivity.showHistoryActivity() {
    startActivityForResult(Intent(this, HistoryActivity::class.java), MainActivity.REQUEST_CODE_HISTORY_ACTIVITY)
    hideMenuOverlay()
}

internal fun MainActivity.showSettingsDialog() {
    SettingsDialog(this, settingsModel).show()
}

internal fun MainActivity.showChromeMenu(anchorView: View? = null) {
    ChromeMenuPopup(this).show(anchorView ?: vb.ibMore)
}

internal fun MainActivity.showDownloadsActivity() {
    startActivity(Intent(this, DownloadsActivity::class.java))
}

internal fun MainActivity.showClipboardActivity() {
    startActivityForResult(Intent(this, ClipboardActivity::class.java), MainActivity.REQUEST_CODE_CLIPBOARD_ACTIVITY)
    hideMenuOverlay()
}

fun MainActivity.showShortcutDialog(shortcut: Shortcut) {
    ShortcutDialog(this, shortcut).show()
}

internal val MainActivity.bottomButtonsOnTouchListener
    get() = View.OnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> true
            MotionEvent.ACTION_UP -> {
                hideMenuOverlay(false)
                v.performClick()
                true
            }
            else -> false
        }
    }

internal val MainActivity.bottomButtonsFocusListener
    get() = View.OnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            hideMenuOverlay(false)
        }
    }

internal val MainActivity.bottomButtonsKeyListener
    get() = View.OnKeyListener { _, _, keyEvent ->
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (keyEvent.action == KeyEvent.ACTION_UP) {
                    tabsModel.currentTab.value?.webEngine?.getView()?.requestFocus()
                }
                true
            }
            else -> false
        }
    }
