package com.gothwad.tvbrowser.activity.main

import android.content.Intent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.lifecycle.lifecycleScope
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.downloads.DownloadsActivity
import com.gothwad.tvbrowser.activity.history.HistoryActivity
import com.gothwad.tvbrowser.activity.main.dialogs.favorites.FavoriteEditorDialog
import com.gothwad.tvbrowser.activity.main.dialogs.favorites.FavoritesDialog
import com.gothwad.tvbrowser.activity.main.dialogs.settings.SettingsDialog
import com.gothwad.tvbrowser.model.FavoriteItem
import com.gothwad.tvbrowser.model.WebTabState
import com.gothwad.tvbrowser.utils.Utils
import com.gothwad.tvbrowser.utils.VoiceSearchHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MainActivity.showMenuOverlay() {
    vb.vActionBar.dismissExtendedAddressBarMode()
    vb.vActionBar.setHeaderToggleIcon(true)
    vb.rlActionBar.visibility = View.VISIBLE
    vb.rlActionBar.bringToFront()
    val headerHeight = if (vb.rlActionBar.height > 0) vb.rlActionBar.height.toFloat() else (83 * resources.displayMetrics.density)
    val startY = -headerHeight
    vb.rlActionBar.translationY = startY
    vb.rlActionBar.alpha = 0f
    vb.rlActionBar.animate()
        .translationY(0f)
        .alpha(1f)
        .setDuration(250)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction {
            vb.ibHome.requestFocus()
        }
        .start()

    vb.flWebViewContainer.animate()
        .translationY(headerHeight)
        .setDuration(250)
        .setInterpolator(DecelerateInterpolator())
        .start()

    vb.vNativeHome.animate()
        .translationY(headerHeight)
        .setDuration(250)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

internal fun MainActivity.hideMenuOverlay(hideBottomButtons: Boolean = true) {
    val isHome = vb.vNativeHome.visibility == View.VISIBLE ||
            tabsModel.currentTab.value?.url == settingsModel.homePage ||
            tabsModel.currentTab.value?.url == Config.HOME_PAGE_URL ||
            tabsModel.currentTab.value?.url == Config.HOME_URL_ALIAS ||
            tabsModel.currentTab.value?.url.isNullOrEmpty()
    if (isHome) {
        return
    }

    vb.vActionBar.setHeaderToggleIcon(false)
    if (vb.rlActionBar.visibility == View.INVISIBLE) {
        return
    }

    val headerHeight = if (vb.rlActionBar.height > 0) vb.rlActionBar.height.toFloat() else (83 * resources.displayMetrics.density)
    val targetY = -headerHeight
    vb.rlActionBar.animate()
        .translationY(targetY)
        .alpha(0f)
        .setDuration(250)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction {
            vb.rlActionBar.visibility = View.INVISIBLE
            syncTabWithTitles()
            vb.flWebViewContainer.visibility = View.VISIBLE
            tabsModel.currentTab.value?.webEngine?.getView()?.requestFocus()
        }
        .start()

    vb.flWebViewContainer.animate()
        .translationY(0f)
        .setDuration(250)
        .setInterpolator(DecelerateInterpolator())
        .start()

    vb.vNativeHome.animate()
        .translationY(0f)
        .setDuration(250)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

internal fun MainActivity.toggleMenu() {
    if (vb.rlActionBar.visibility == View.INVISIBLE) {
        showMenuOverlay()
    } else {
        hideMenuOverlay()
    }
}

internal fun MainActivity.hideBottomPanel() {
    if (vb.llBottomPanel.visibility != View.VISIBLE) return
    vb.llBottomPanel.animate()
        .setDuration(300)
        .setInterpolator(AccelerateInterpolator())
        .translationY(vb.llBottomPanel.height.toFloat())
        .withEndAction {
            vb.llBottomPanel.translationY = 0f
            vb.llBottomPanel.visibility = View.INVISIBLE
        }
        .start()
}

internal suspend fun MainActivity.displayThumbnail(currentTab: WebTabState?) {
    if (currentTab != null) {
        if (tabsModel.currentTab.value != currentTab) return
        vb.llMiniaturePlaceholder.visibility = View.INVISIBLE
        vb.ivMiniatures.visibility = View.VISIBLE
        if (currentTab.thumbnail != null) {
            vb.ivMiniatures.setImageBitmap(currentTab.thumbnail)
        } else if (currentTab.thumbnailHash != null) {
            withContext(Dispatchers.IO) {
                val thumbnail = currentTab.loadThumbnail()
                withContext(Dispatchers.Main) {
                    if (thumbnail != null) {
                        vb.ivMiniatures.setImageBitmap(currentTab.thumbnail)
                    } else {
                        vb.ivMiniatures.setImageResource(0)
                    }
                }
            }
        } else {
            vb.ivMiniatures.setImageResource(0)
        }
    } else {
        vb.llMiniaturePlaceholder.visibility = View.VISIBLE
        vb.ivMiniatures.setImageResource(0)
        vb.ivMiniatures.visibility = View.INVISIBLE
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
    val currentTab = tabsModel.currentTab.value
    val currentPageTitle = currentTab?.title ?: ""
    val currentPageUrl = currentTab?.url ?: ""

    FavoritesDialog(this, lifecycleScope, object : FavoritesDialog.Callback {
        override fun onFavoriteChoosen(item: FavoriteItem?) {
            navigate(item!!.url!!)
        }
    }, currentPageTitle, currentPageUrl).show()
    hideMenuOverlay()
}

internal fun MainActivity.showHistoryActivity() {
    startActivityForResult(
        Intent(this, HistoryActivity::class.java),
        MainActivity.REQUEST_CODE_HISTORY_ACTIVITY
    )
    hideMenuOverlay()
}

internal fun MainActivity.showSettingsDialog() {
    SettingsDialog(this, settingsModel).show()
}

internal fun MainActivity.showChromeMenu() {
    val anchorView = vb.vActionBar.findViewById<View>(R.id.ibSettings) ?: vb.vActionBar
    com.gothwad.tvbrowser.activity.main.dialogs.ChromeMenuPopup(this).show(anchorView)
}

internal fun MainActivity.showDownloadsActivity() {
    startActivity(Intent(this, DownloadsActivity::class.java))
}

internal fun MainActivity.showClipboardActivity() {
    startActivityForResult(
        Intent(this, com.gothwad.tvbrowser.activity.clipboard.ClipboardActivity::class.java),
        MainActivity.REQUEST_CODE_CLIPBOARD_ACTIVITY
    )
    hideMenuOverlay()
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
                    hideBottomPanel()
                    tabsModel.currentTab.value?.webEngine?.getView()?.requestFocus()
                }
                true
            }
            else -> false
        }
    }
