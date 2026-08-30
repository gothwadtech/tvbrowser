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
import com.gothwad.tvbrowser.activity.main.dialogs.BrowserSidebarPopup
import com.gothwad.tvbrowser.activity.main.dialogs.ClipboardSidebarPopup
import com.gothwad.tvbrowser.activity.main.dialogs.DownloadsSidebarPopup
import com.gothwad.tvbrowser.activity.main.dialogs.FavoritesSidebarPopup
import com.gothwad.tvbrowser.activity.main.dialogs.FileManagerSidebarPopup
import com.gothwad.tvbrowser.activity.main.dialogs.HistorySidebarPopup
import com.gothwad.tvbrowser.activity.main.dialogs.NotesSidebarPopup
import com.gothwad.tvbrowser.activity.main.dialogs.ShortcutDialog
import com.gothwad.tvbrowser.activity.main.dialogs.favorites.FavoriteEditorDialog
import com.gothwad.tvbrowser.model.FavoriteItem
import com.gothwad.tvbrowser.settings.SettingsDialog
import com.gothwad.tvbrowser.singleton.shortcuts.Shortcut
import com.gothwad.tvbrowser.utils.Utils
import com.gothwad.tvbrowser.utils.VoiceSearchHelper
import com.gothwad.tvbrowser.webengine.WebEngineFactory

internal fun MainActivity.showMenuOverlay() {
    if (isFullscreen) return
    vb.vActionBar.dismissExtendedAddressBarMode()
    vb.rlActionBar.visibility = View.VISIBLE
    vb.rlActionBar.translationY = 0f
}

internal fun MainActivity.hideMenuOverlay(hideBottomButtons: Boolean = true) {
    if (isFullscreen) {
        vb.rlActionBar.visibility = View.GONE
    } else {
        vb.rlActionBar.visibility = View.VISIBLE
        vb.rlActionBar.translationY = 0f
    }
}

internal fun MainActivity.toggleMenu() {
    if (isFullscreen) return
    vb.rlActionBar.visibility = View.VISIBLE
    vb.rlActionBar.translationY = 0f
}

internal fun MainActivity.setupHeaderClickListeners(incognitoMode: Boolean) {
    vb.ibMenu.setOnClickListener { showBrowserSidebar(vb.ibMenu) }
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
    vb.tvTabCountBadge.setOnClickListener { showTabsRowDialog() }
    vb.ibNotes.setOnClickListener { showNotes(vb.ibNotes) }
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
    vb.ibDownloads.setOnClickListener { showDownloads(vb.ibDownloads) }
    vb.ibFileManager.setOnClickListener { showFileManager(vb.ibFileManager) }
    vb.ibBookmarks.setOnClickListener { showFavoritesDialog(vb.ibBookmarks) }
    vb.ibIncognito.setOnClickListener { toggleIncognitoMode(true) }
    vb.ibSettings.setOnClickListener { showSettingsDialog() }

    if (incognitoMode) {
        vb.rlActionBar.setBackgroundColor(Color.parseColor("#1F1F1F"))
        vb.ibIncognito.imageTintList = ColorStateList.valueOf(Color.parseColor("#0494F4"))
    }

    vb.vActionBar.callback = this

    listOf(
        vb.ibTopNewTab,
        vb.ibMenu, vb.ibHome, vb.ibBack, vb.ibForward, vb.ibRefresh,
        vb.ibNewTab, vb.flTabsSwitcher, vb.ibNotes, vb.ibDownloads,
        vb.ibFileManager, vb.ibBookmarks, vb.ibIncognito, vb.ibSettings
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

internal fun MainActivity.showFavoritesDialog(anchorView: View? = null) {
    FavoritesSidebarPopup(this) { item ->
        item.url?.let { navigate(it) }
    }.show(anchorView ?: vb.ibBookmarks)
    hideMenuOverlay()
}

internal fun MainActivity.showFavorites(anchorView: View? = null) {
    showFavoritesDialog(anchorView)
}

internal fun MainActivity.showHistoryActivity(anchorView: View? = null) {
    HistorySidebarPopup(this) { item ->
        item.url?.let { navigate(it) }
    }.show(anchorView ?: vb.ibMenu)
    hideMenuOverlay()
}

internal fun MainActivity.showSettingsDialog(anchorView: View? = null) {
    SettingsDialog(this, settingsModel).show(anchorView ?: vb.ibSettings)
}

internal fun MainActivity.showBrowserSidebar(anchorView: View? = null) {
    BrowserSidebarPopup(this).show(anchorView ?: vb.ibMenu)
}

internal fun MainActivity.showDownloads(anchorView: View? = null) {
    DownloadsSidebarPopup(this).show(anchorView ?: vb.ibDownloads)
}

internal fun MainActivity.showDownloadsActivity(anchorView: View? = null) {
    showDownloads(anchorView)
}

internal fun MainActivity.showFileManager(anchorView: View? = null) {
    FileManagerSidebarPopup(this).show(anchorView ?: vb.ibFileManager)
}

internal fun MainActivity.showNotes(anchorView: View? = null) {
    NotesSidebarPopup(this).show(anchorView ?: vb.ibNotes)
}

internal fun MainActivity.showClipboardActivity(anchorView: View? = null) {
    ClipboardSidebarPopup(this).show(anchorView)
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

internal fun MainActivity.setupDragAndDropListener() {
    val dragListener = View.OnDragListener { v, event ->
        when (event.action) {
            android.view.DragEvent.ACTION_DRAG_STARTED -> true
            android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                v.alpha = 0.95f
                true
            }
            android.view.DragEvent.ACTION_DRAG_EXITED,
            android.view.DragEvent.ACTION_DRAG_ENDED -> {
                v.alpha = 1.0f
                true
            }
            android.view.DragEvent.ACTION_DROP -> {
                v.alpha = 1.0f
                val clipData = event.clipData
                val localFile = event.localState as? java.io.File
                if (localFile != null) {
                    openFileInApp(localFile)
                    true
                } else if (clipData != null && clipData.itemCount > 0) {
                    val item = clipData.getItemAt(0)
                    val uri = item.uri
                    val text = item.text?.toString()
                    if (uri != null && uri.scheme == "file") {
                        val file = java.io.File(uri.path ?: "")
                        if (file.exists()) {
                            openFileInApp(file)
                        } else {
                            navigate(uri.toString())
                        }
                    } else if (uri != null) {
                        navigate(uri.toString())
                    } else if (!text.isNullOrEmpty()) {
                        val file = java.io.File(text)
                        if (file.exists()) {
                            openFileInApp(file)
                        } else {
                            navigate(text)
                        }
                    }
                    true
                } else {
                    false
                }
            }
            else -> true
        }
    }

    vb.flWebViewContainer.setOnDragListener(dragListener)
    vb.vNativeHome.setOnDragListener(dragListener)
}

