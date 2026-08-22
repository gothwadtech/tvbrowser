package com.gothwad.tvbrowser.activity.main

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.gothwad.tvbrowser.BrowserApp
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.model.Download
import com.gothwad.tvbrowser.model.FavoriteItem
import com.gothwad.tvbrowser.model.HomePageLink
import com.gothwad.tvbrowser.model.HostConfig
import com.gothwad.tvbrowser.model.WebTabState
import com.gothwad.tvbrowser.singleton.AppDatabase
import com.gothwad.tvbrowser.utils.DownloadUtils
import com.gothwad.tvbrowser.utils.Utils
import com.gothwad.tvbrowser.webengine.WebEngine
import com.gothwad.tvbrowser.webengine.WebEngineWindowProviderCallback
import com.gothwad.tvbrowser.widgets.NotificationView
import com.gothwad.tvbrowser.widgets.cursor.CursorDrawerDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.InputStream

internal class WebEngineCallback(val activity: MainActivity, val tab: WebTabState) : WebEngineWindowProviderCallback {
    override fun getActivity(): Activity = activity

    override fun onOpenInNewTabRequested(url: String, navigateImmediately: Boolean): WebEngine? {
        var index = activity.tabsModel.tabsStates.indexOf(activity.tabsModel.currentTab.value)
        index = if (index == -1) activity.tabsModel.tabsStates.size else index + 1
        return activity.openInNewTab(url, index, true, navigateImmediately)
    }

    override fun onDownloadRequested(url: String) {
        val fileName = Uri.parse(url).lastPathSegment ?: "download"
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(url))
        activity.onDownloadRequested(url, tab.url, fileName, tab.webEngine.userAgentString, mimeType)
    }

    override fun onDownloadRequested(
        url: String,
        referer: String,
        originalDownloadFileName: String?,
        userAgent: String?,
        mimeType: String?,
        operationAfterDownload: Download.OperationAfterDownload,
        base64BlobData: String?,
        stream: InputStream?,
        size: Long,
        contentDisposition: String?
    ) {
        val fileName = DownloadUtils.guessFileName(url, contentDisposition, mimeType)
        activity.onDownloadRequested(url, referer, fileName, userAgent, mimeType, operationAfterDownload, base64BlobData, stream, size)
    }

    override fun onDownloadRequested(
        url: String,
        userAgent: String?,
        contentDisposition: String,
        mimetype: String?,
        contentLength: Long
    ) {
        val fileName = DownloadUtils.guessFileName(url, contentDisposition, mimetype)
        activity.onDownloadRequested(
            url = url, referer = tab.url, originalDownloadFileName = fileName,
            userAgent = userAgent, mimeType = mimetype, size = contentLength
        )
    }

    override fun onProgressChanged(newProgress: Int) {
        activity.vb.progressBar.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activity.vb.progressBar.setProgress(newProgress, true)
        } else {
            activity.vb.progressBar.progress = newProgress
        }
        activity.uiHandler.removeCallbacks(activity.progressBarHideRunnable)
        if (newProgress == 100) {
            activity.uiHandler.postDelayed(activity.progressBarHideRunnable, 1000)
        } else {
            activity.uiHandler.postDelayed(activity.progressBarHideRunnable, 5000)
        }
    }

    override fun onReceivedTitle(title: String) {
        tab.title = title
        activity.tabsGridDialog?.refreshData()
        activity.viewModel.onTabTitleUpdated(tab)
    }

    override fun requestPermissions(array: Array<String>): Int {
        val requestCode = activity.lastCommonRequestsCode++
        activity.requestPermissions(array, requestCode)
        return requestCode
    }

    override fun onShowFileChooser(intent: Intent): Boolean {
        try {
            activity.startActivityForResult(intent, MainActivity.PICK_FILE_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            try {
                intent.type = "*/*"
                activity.startActivityForResult(intent, MainActivity.PICK_FILE_REQUEST_CODE)
            } catch (e: ActivityNotFoundException) {
                Utils.showToast(activity.applicationContext, activity.getString(R.string.err_cant_open_file_chooser))
                return false
            }
        }
        return true
    }

    override fun onReceivedIcon(icon: Bitmap) {
        activity.tabsGridDialog?.refreshData()
    }

    override fun shouldOverrideUrlLoading(url: String): Boolean {
        tab.lastLoadingUrl = url
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            Log.e("MainActivity", "shouldOverrideUrlLoading: ", e)
            return true
        }

        if (uri.scheme == null) {
            return true
        }

        if (URLUtil.isNetworkUrl(url) || uri.scheme.equals("javascript", true) ||
            uri.scheme.equals("data", true) || uri.scheme.equals("about", true) ||
            uri.scheme.equals("blob", true)
        ) {
            return false
        }

        if (uri.scheme.equals("intent", true)) {
            onOpenInExternalAppRequested(url)
            return true
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (intent.resolveActivity(BrowserApp.instance.packageManager) != null) {
                activity.runOnUiThread {
                    activity.askUserAndOpenInExternalApp(url, intent)
                }
                true
            } else {
                activity.runOnUiThread {
                    Utils.showToast(activity.applicationContext, activity.getString(R.string.err_no_app_to_handle_url))
                }
                true
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "shouldOverrideUrlLoading: ", e)
            true
        }
    }

    override fun onPageStarted(url: String?) {
        activity.vb.progressBar.visibility = View.VISIBLE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activity.vb.progressBar.setProgress(15, true)
        } else {
            activity.vb.progressBar.progress = 15
        }
        activity.onWebViewUpdated(tab)
        val webViewUrl = tab.webEngine.url
        if (webViewUrl != null) {
            tab.url = webViewUrl
        } else if (url != null) {
            tab.url = url
        }
        if (activity.tabsModel.currentTab.value == tab) {
            activity.vb.vActionBar.setAddressBoxText(tab.url)
        }
        tab.blockedAds = 0
        tab.blockedPopups = 0
        if (url != null && url != activity.settingsModel.homePage && url != Config.HOME_PAGE_URL) {
            activity.runOnUiThread {
                activity.hideMenuOverlay()
            }
        }
    }

    override fun onPageFinished(url: String?) {
        if (activity.tabsModel.currentTab.value == null) return
        activity.onWebViewUpdated(tab)

        val webViewUrl = tab.webEngine.url
        if (webViewUrl != null) {
            tab.url = webViewUrl
        } else if (url != null) {
            tab.url = url
        }
        if (activity.tabsModel.currentTab.value == tab) {
            activity.vb.vActionBar.setAddressBoxText(tab.url)
        }

        activity.tabsModel.tabsStates.onEach { if (it != tab) it.thumbnail = null }
        activity.lifecycleScope.launch {
            val newThumbnail = tab.webEngine.renderThumbnail(tab.thumbnail)
            if (newThumbnail != null) {
                tab.updateThumbnail(activity, newThumbnail)
                if (activity.vb.rlActionBar.visibility == View.VISIBLE && tab == activity.tabsModel.currentTab.value) {
                    activity.displayThumbnail(tab)
                }
            }
        }
    }

    override fun onPageCertificateError(url: String?) {
        activity.vb.vActionBar.setAddressBoxTextColor(Color.RED)
    }

    override fun isAd(url: Uri, acceptHeader: String?, baseUri: Uri): Boolean? {
        return activity.adblockModel.isAd(url, acceptHeader, baseUri)
    }

    override fun isAdBlockingEnabled(): Boolean {
        activity.tabsModel.currentTab.value?.adblock?.apply {
            return this
        }
        return activity.config.adBlockEnabled
    }

    override fun isDialogsBlockingEnabled(): Boolean {
        if (tab.url == Config.HOME_PAGE_URL) return false
        return shouldBlockNewWindow(dialog = true, userGesture = false)
    }

    override fun shouldBlockNewWindow(dialog: Boolean, userGesture: Boolean): Boolean {
        val hostConfig = runBlocking(Dispatchers.Main.immediate) { activity.tabsModel.findHostConfig(tab, false) }
        val currentBlockPopupsLevelValue = hostConfig?.popupBlockLevel ?: HostConfig.DEFAULT_BLOCK_POPUPS_VALUE
        return when (currentBlockPopupsLevelValue) {
            HostConfig.POPUP_BLOCK_NONE -> false
            HostConfig.POPUP_BLOCK_DIALOGS -> dialog
            HostConfig.POPUP_BLOCK_NEW_AUTO_OPENED_TABS -> dialog || !userGesture
            else -> true
        }
    }

    override fun onBlockedAd(uri: String) {
        if (!activity.config.adBlockEnabled) return
        tab.blockedAds++
    }

    override fun onBlockedDialog(newTab: Boolean) {
        tab.blockedPopups++
        activity.runOnUiThread {
            val msg = activity.getString(if (newTab) R.string.new_tab_blocked else R.string.popup_dialog_blocked)
            NotificationView.showBottomRight(activity.vb.rlRoot, R.drawable.ic_block_popups, msg)
        }
    }

    override fun onCreateWindow(dialog: Boolean, userGesture: Boolean): View? {
        if (shouldBlockNewWindow(dialog, userGesture)) {
            onBlockedDialog(!dialog)
            return null
        }
        val newTab = WebTabState(incognito = activity.config.incognitoMode)
        val webView = activity.createWebView(newTab) ?: return null
        val currentTab = activity.tabsModel.currentTab.value ?: return null
        val index = activity.tabsModel.tabsStates.indexOf(currentTab) + 1
        activity.tabsModel.tabsStates.add(index, newTab)
        activity.changeTab(newTab)
        return webView
    }

    override fun closeWindow(internalRepresentation: Any) {
        for (t in activity.tabsModel.tabsStates) {
            if (t.webEngine.isSameSession(internalRepresentation)) {
                activity.closeTab(t)
                break
            }
        }
    }

    override fun onScaleChanged(oldScale: Float, newScale: Float) {
        tab.scale = newScale
    }

    override fun onCopyTextToClipboardRequested(url: String) {
        val clipBoard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("URL", url)
        clipBoard.setPrimaryClip(clipData)
        try {
            com.gothwad.tvbrowser.activity.clipboard.ClipboardRepository(activity).recordCopiedText(url)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Toast.makeText(activity, activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    override fun onShareUrlRequested(url: String) {
        val share = Intent(Intent.ACTION_SEND)
        share.type = "text/plain"
        share.putExtra(Intent.EXTRA_SUBJECT, R.string.share_url)
        share.putExtra(Intent.EXTRA_TEXT, url)
        try {
            activity.startActivity(share)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOpenInExternalAppRequested(url: String) {
        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        val activityComponent = intent.resolveActivity(activity.packageManager)
        if (activityComponent != null && activityComponent.packageName == activity.packageName) {
            Toast.makeText(activity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(activity, R.string.external_app_open_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun initiateVoiceSearch() {
        activity.initiateVoiceSearch()
    }

    override fun onEditHomePageBookmarkSelected(index: Int) {
        activity.lifecycleScope.launch {
            val bookmark = activity.viewModel.homePageLinks.firstOrNull { it.order == index }
            var favoriteItem: FavoriteItem? = bookmark?.favoriteId?.let {
                AppDatabase.db.favoritesDao().getById(it)
            }

            if (favoriteItem == null) {
                favoriteItem = FavoriteItem()
                favoriteItem.title = bookmark?.title
                favoriteItem.url = bookmark?.url
                favoriteItem.order = index
                favoriteItem.homePageBookmark = true
                activity.onEditHomePageBookmark(favoriteItem)
            } else {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.bookmarks)
                    .setItems(arrayOf(activity.getString(R.string.edit), activity.getString(R.string.delete))) { _, which ->
                        when (which) {
                            0 -> activity.onEditHomePageBookmark(favoriteItem)
                            1 -> activity.viewModel.removeHomePageLink(bookmark!!)
                        }
                    }
                    .show()
            }
        }
    }

    override fun getHomePageLinks(): List<HomePageLink> = activity.viewModel.homePageLinks

    override fun onPrepareForFullscreen() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        activity.isFullscreen = true
    }

    override fun onExitFullscreen() {
        if (!activity.config.keepScreenOn) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        activity.window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        activity.isFullscreen = false
    }

    override fun onVisited(url: String) {
        val currentTab = activity.tabsModel.currentTab.value ?: return
        if (!activity.config.incognitoMode) {
            activity.viewModel.logVisitedHistory(currentTab.title, url, currentTab.faviconHash)
        }
    }

    override fun onContextMenu(
        cursorDrawer: CursorDrawerDelegate,
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int
    ) {
        activity.uiHandler.post {
            activity.vb.vCursorMenu.show(
                tab, this, cursorDrawer,
                baseUri, linkUri, srcUri,
                title, altText, textContent,
                x, y,
                activity.backNavigationEventsAdapter
            )
        }
    }

    override fun suggestActionsForLink(
        baseUri: String?,
        linkUri: String?,
        srcUri: String?,
        title: String?,
        altText: String?,
        textContent: String?,
        x: Int,
        y: Int
    ) {
        var s = linkUri ?: srcUri
        if (s != null && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        val url = s
        val isHTTPUrl = url != null && (url.startsWith("http://") || url.startsWith("https://"))
        val anchor = View(activity)
        val lp = FrameLayout.LayoutParams(1, 1)
        lp.setMargins(x, y, 0, 0)
        activity.vb.flWebViewContainer.addView(anchor, lp)
        activity.linkActionsMenu = PopupMenu(activity, anchor, Gravity.BOTTOM).also {
            it.inflate(R.menu.menu_link)
            it.menu.findItem(R.id.miOpenInNewTab).isVisible = isHTTPUrl
            it.menu.findItem(R.id.miOpenInExternalApp).isVisible = isHTTPUrl
            it.menu.findItem(R.id.miDownload).isVisible = isHTTPUrl
            it.menu.findItem(R.id.miCopyToClipboard).isVisible = url != null
            it.menu.findItem(R.id.miShare).isVisible = url != null
            it.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.miRefreshPage -> tab.webEngine.reload()
                    R.id.miOpenInNewTab -> onOpenInNewTabRequested(url!!, true)
                    R.id.miOpenInExternalApp -> onOpenInExternalAppRequested(url!!)
                    R.id.miDownload -> onDownloadRequested(url!!)
                    R.id.miCopyToClipboard -> onCopyTextToClipboardRequested(url!!)
                    R.id.miShare -> onShareUrlRequested(url!!)
                }
                true
            }

            it.setOnDismissListener {
                activity.vb.flWebViewContainer.removeView(anchor)
                activity.linkActionsMenu = null
            }
            it.show()
        }
    }

    override fun markBookmarkRecommendationAsUseful(bookmarkOrder: Int) {
        activity.viewModel.markBookmarkRecommendationAsUseful(bookmarkOrder)
    }
}
