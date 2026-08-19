package com.gothwad.tvbrowser.activity.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Process
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.gothwad.tvbrowser.AppContext
import com.gothwad.tvbrowser.BrowserApp
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.IncognitoModeMainActivity
import com.gothwad.tvbrowser.activity.history.HistoryActivity
import com.gothwad.tvbrowser.activity.main.dialogs.tabs.TabsGridDialog
import com.gothwad.tvbrowser.activity.main.view.ActionBar
import com.gothwad.tvbrowser.databinding.ActivityMainBinding
import com.gothwad.tvbrowser.model.Download
import com.gothwad.tvbrowser.model.WebTabState
import com.gothwad.tvbrowser.service.downloads.DownloadService
import com.gothwad.tvbrowser.utils.BackNavigationEventsAdapter
import com.gothwad.tvbrowser.utils.BaseAnimationListener
import com.gothwad.tvbrowser.utils.VoiceSearchHelper
import com.gothwad.tvbrowser.utils.activemodel.ActiveModelsRepository
import com.gothwad.tvbrowser.webengine.WebEngineFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

open class MainActivity : AppCompatActivity(), ActionBar.Callback {
    companion object {
        private val TAG = MainActivity::class.java.simpleName
        const val VOICE_SEARCH_REQUEST_CODE = 10001
        const val MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS_ACCESS = 10003
        const val MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS = 10004
        const val PICK_FILE_REQUEST_CODE = 10005
        const val REQUEST_CODE_HISTORY_ACTIVITY = 10006
        const val REQUEST_CODE_UNKNOWN_APP_SOURCES = 10007
        const val KEY_PROCESS_ID_TO_KILL = "proc_id_to_kill"
        const val MY_PERMISSIONS_REQUEST_VOICE_SEARCH_PERMISSIONS = 10008
        const val REQUEST_CODE_CLIPBOARD_ACTIVITY = 10010
        private const val COMMON_REQUESTS_START_CODE = 10100
    }

    internal lateinit var vb: ActivityMainBinding
    internal lateinit var viewModel: MainActivityViewModel
    internal lateinit var tabsModel: TabsModel
    internal lateinit var settingsModel: SettingsModel
    internal lateinit var adblockModel: AdblockModel
    internal lateinit var autoUpdateModel: AutoUpdateModel
    internal lateinit var uiHandler: Handler
    internal var isFullscreen: Boolean = false
    internal lateinit var prefs: SharedPreferences
    internal val config = AppContext.provideConfig()
    internal val voiceSearchHelper = VoiceSearchHelper(this, VOICE_SEARCH_REQUEST_CODE, MY_PERMISSIONS_REQUEST_VOICE_SEARCH_PERMISSIONS)
    internal var lastCommonRequestsCode = COMMON_REQUESTS_START_CODE
    internal var downloadService: DownloadService? = null
    internal var downloadIntent: Download? = null
    var openUrlInExternalAppDialog: AlertDialog? = null
    internal var linkActionsMenu: PopupMenu? = null

    internal val progressBarHideRunnable = Runnable {
        val anim = AnimationUtils.loadAnimation(this@MainActivity, android.R.anim.fade_out)
        anim.setAnimationListener(object : BaseAnimationListener() {
            override fun onAnimationEnd(animation: Animation) {
                vb.progressBar.visibility = View.GONE
            }
        })
        vb.progressBar.startAnimation(anim)
    }

    private val mConnectivityChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting
            val tab = tabsModel.currentTab.value ?: return
            tab.webEngine.setNetworkAvailable(isConnected)
        }
    }

    private val displayThumbnailRunnable = object : Runnable {
        var tabState: WebTabState? = null
        override fun run() {
            tabState?.let {
                lifecycleScope.launch(Dispatchers.Main) {
                    displayThumbnail(it)
                }
            }
        }
    }

    internal val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            backNavigationEventsAdapter.dispatchSystemBackNavigationEvent()
        }
    }

    internal val backNavigationEventsAdapter = BackNavigationEventsAdapter(
        onEmulatedBackEvent = {
            if (!hideSoftwareKeyboardIfVisible()) {
                handleBackNavigation()
            }
        }
    )

    internal val downloadServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as? DownloadService.Binder
            if (binder == null) {
                Log.e(TAG, "Download service connection failed")
                uiHandler.postDelayed({
                    bindService(Intent(this@MainActivity, DownloadService::class.java), this, Context.BIND_AUTO_CREATE)
                }, 1000)
                return
            }
            downloadService = binder.service
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            downloadService = null
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incognitoMode = config.incognitoMode
        Log.d(TAG, "onCreate incognitoMode: $incognitoMode")
        if (incognitoMode xor (this is IncognitoModeMainActivity)) {
            switchProcess(incognitoMode, intent?.extras)
            finish()
            return
        }
        val pidToKill = intent?.getIntExtra(KEY_PROCESS_ID_TO_KILL, -1) ?: -1
        if (pidToKill != -1) {
            Process.killProcess(pidToKill)
        }

        viewModel = ActiveModelsRepository.get(MainActivityViewModel::class, this)
        if (incognitoMode) {
            viewModel.prepareSwitchToIncognito()
        }
        settingsModel = ActiveModelsRepository.get(SettingsModel::class, this)
        adblockModel = ActiveModelsRepository.get(AdblockModel::class, this)
        tabsModel = ActiveModelsRepository.get(TabsModel::class, this)
        autoUpdateModel = ActiveModelsRepository.get(AutoUpdateModel::class, this)
        uiHandler = Handler()
        prefs = getSharedPreferences(BrowserApp.MAIN_PREFS_NAME, Context.MODE_PRIVATE)
        applyScreenOrientation()
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        vb.ivMiniatures.visibility = View.INVISIBLE
        vb.llBottomPanel.visibility = View.GONE
        vb.rlActionBar.visibility = View.VISIBLE
        vb.rlActionBar.alpha = 1f
        vb.rlActionBar.translationY = 0f
        vb.rlActionBar.post {
            if (vb.rlActionBar.visibility == View.VISIBLE) {
                val headerHeight = vb.rlActionBar.height.toFloat()
                vb.flWebViewContainer.translationY = headerHeight
                vb.vNativeHome.translationY = headerHeight
            }
        }
        vb.vNativeHome.onNavigateUrl = { url -> navigate(url) }
        vb.progressBar.visibility = View.GONE

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
        vb.flTabsSwitcher.setOnClickListener {
            showTabsGrid()
        }
        vb.ibBack.setOnClickListener { navigateBack() }
        vb.ibForward.setOnClickListener {
            val tab = tabsModel.currentTab.value ?: return@setOnClickListener
            if (tab.webEngine.canGoForward()) {
                tab.webEngine.goForward()
            }
        }
        vb.ibRefresh.setOnClickListener { refresh() }
        vb.ibDownloads.setOnClickListener { showDownloads() }
        vb.ibBookmarks.setOnClickListener { showFavorites() }
        vb.ibSettings.setOnClickListener { showChromeMenu() }

        vb.vActionBar.callback = this

        listOf(vb.ibHome, vb.ibNewTab, vb.flTabsSwitcher, vb.ibBack, vb.ibForward, vb.ibRefresh, vb.ibDownloads, vb.ibBookmarks, vb.ibSettings).forEach {
            it.setOnTouchListener(bottomButtonsOnTouchListener)
            it.onFocusChangeListener = bottomButtonsFocusListener
            it.setOnKeyListener(bottomButtonsKeyListener)
        }

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
            Log.i(TAG, "homePageLinks updated")
            val currentUrl = tabsModel.currentTab.value?.url ?: return@subscribe
            if (Config.HOME_PAGE_URL == currentUrl) {
                tabsModel.currentTab.value?.webEngine?.reload()
            }
        }

        tabsModel.currentTab.subscribe(this) {
            vb.vActionBar.setAddressBoxText(it?.url ?: "")
            it?.let { onWebViewUpdated(it) }
        }

        tabsModel.tabsStates.subscribe(this, false) {
            updateTabCountBadge()
            tabsGridDialog?.refreshData()
            if (it.isEmpty()) {
                vb.flWebViewContainer.removeAllViews()
            }
        }
        updateTabCountBadge()

        onBackPressedDispatcher.addCallback(onBackPressedCallback)

        applySoftInputMode()
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (config.disableVirtualKeyboard && newFocus != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(newFocus.windowToken, 0)
            }
        }

        loadState()
    }

    internal fun applySoftInputMode() {
        if (config.disableVirtualKeyboard) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            val currentFocusView = currentFocus ?: vb.flWebViewContainer
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(currentFocusView.windowToken, 0)
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED)
        }
    }

    override fun initiateVoiceSearch() {
        initiateVoiceSearchInternal()
    }

    override fun closeWindow() {
        Log.d(TAG, "closeWindow")
        lifecycleScope.launch {
            if (config.incognitoMode) {
                toggleIncognitoMode(false).join()
            }
            finish()
        }
    }

    override fun showDownloads() {
        showDownloadsActivity()
    }

    fun showClipboard() {
        showClipboardActivity()
    }

    fun showNotes() {
        startActivity(Intent(this, com.gothwad.tvbrowser.activity.notes.NotesActivity::class.java))
    }

    fun showFileManager() {
        startActivity(Intent(this, com.gothwad.tvbrowser.activity.filemanager.FileManagerActivity::class.java))
    }

    var tabsGridDialog: TabsGridDialog? = null

    fun showTabsGrid() {
        if (tabsGridDialog?.isShowing == true) return
        tabsGridDialog = TabsGridDialog(
            activity = this,
            onTabSelected = { tab ->
                switchToTab(tab)
            },
            onNewTabRequested = {
                openInNewTab(settingsModel.homePage, tabsModel.tabsStates.size, needToHideMenuOverlay = false, navigateImmediately = true)
            },
            onCloseTabRequested = { tab ->
                closeTab(tab)
            },
            onCloseAllTabsRequested = {
                closeAllTabs()
            }
        ).apply {
            show()
        }
    }

    fun updateTabCountBadge() {
        val count = tabsModel.tabsStates.size
        vb.tvTabCountBadge.text = if (count > 0) count.toString() else "1"
    }

    fun closeAllTabs() {
        val tabsToClose = tabsModel.tabsStates.toList()
        for (tab in tabsToClose) {
            closeTab(tab)
        }
        openInNewTab(settingsModel.homePage, 0, needToHideMenuOverlay = false, navigateImmediately = true)
    }

    override fun showHistory() {
        showHistoryActivity()
    }

    override fun showFavorites() {
        showFavoritesDialog()
    }

    override fun showSettings() {
        showChromeMenu()
    }

    override fun onExtendedAddressBarMode() {
        vb.llBottomPanel.visibility = View.INVISIBLE
    }

    override fun onUrlInputDone() {}

    override fun toggleHeader() {
        toggleMenu()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        if (::tabsModel.isInitialized) {
            tabsModel.onDetachActivity()
        }
        super.onDestroy()
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.data != null) {
            handleIntent(intent)
        }
    }

    override fun onTrimMemory(level: Int) {
        if (::tabsModel.isInitialized) {
            for (tab in tabsModel.tabsStates) {
                if (!tab.selected) {
                    tab.trimMemory()
                }
            }
            if (level >= TRIM_MEMORY_RUNNING_LOW || level >= TRIM_MEMORY_MODERATE) {
                com.gothwad.tvbrowser.singleton.FaviconsPool.clear()
            }
        }
        super.onTrimMemory(level)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (voiceSearchHelper.processPermissionsResult(requestCode, permissions, grantResults)) {
            return
        }
        if (tabsModel.currentTab.value?.webEngine?.onPermissionsResult(requestCode, permissions, grantResults) == true) return
        if (grantResults.isEmpty()) return
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_EXTERNAL_STORAGE_ACCESS,
            MY_PERMISSIONS_REQUEST_POST_NOTIFICATIONS_ACCESS -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startDownload()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (voiceSearchHelper.processActivityResult(requestCode, resultCode, data)) {
            return
        }
        when (requestCode) {
            PICK_FILE_REQUEST_CODE -> {
                tabsModel.currentTab.value?.webEngine?.onFilePicked(resultCode, data)
            }
            REQUEST_CODE_HISTORY_ACTIVITY -> if (resultCode == Activity.RESULT_OK) {
                val url = data?.getStringExtra(HistoryActivity.KEY_URL)
                if (url != null) {
                    navigate(url)
                }
                hideMenuOverlay()
            }
            REQUEST_CODE_CLIPBOARD_ACTIVITY -> if (resultCode == Activity.RESULT_OK) {
                val url = data?.getStringExtra(com.gothwad.tvbrowser.activity.clipboard.ClipboardActivity.KEY_URL_TO_OPEN)
                if (url != null) {
                    navigate(url)
                }
                hideMenuOverlay()
            }
            REQUEST_CODE_UNKNOWN_APP_SOURCES -> if (autoUpdateModel.needToShowUpdateDlgAgain) {
                autoUpdateModel.showUpdateDialogIfNeeded(this)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onStart() {
        super.onStart()
        if (com.gothwad.tvbrowser.singleton.AppLockManager.requiresUnlock(this)) {
            startActivity(Intent(this, com.gothwad.tvbrowser.activity.lock.AppLockActivity::class.java))
        }
        bindService(Intent(this, DownloadService::class.java), downloadServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        unbindService(downloadServiceConnection)
        downloadService = null
    }

    override fun onResume() {
        super.onResume()
        applySoftInputMode()
        val intentFilter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
        registerReceiver(mConnectivityChangeReceiver, intentFilter)
        tabsModel.currentTab.value?.webEngine?.onResume()
    }

    override fun onPause() {
        unregisterReceiver(mConnectivityChangeReceiver)
        tabsModel.currentTab.value?.apply {
            webEngine.onPause()
            onPause()
            runBlocking { tabsModel.saveTab(this@apply) }
        }
        super.onPause()
    }

    fun navigate(url: String) = navigateInternal(url)
    fun navigateBack(goHomeIfNoHistory: Boolean = false) = navigateBackInternal(goHomeIfNoHistory)
    fun refresh() = refreshInternal()
    fun applyScreenOrientation() = applyScreenOrientationInternal()
    suspend fun showPopupBlockOptions() = showPopupBlockOptionsInternal()

    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null) {
            val cfg = AppContext.provideConfig()
            val scale = cfg.uiScalePercent / 100f
            if (scale != 1.0f) {
                val overrideConfig = android.content.res.Configuration(newBase.resources.configuration)
                overrideConfig.densityDpi = (newBase.resources.displayMetrics.densityDpi * scale).toInt()
                val scaledContext = newBase.createConfigurationContext(overrideConfig)
                super.attachBaseContext(scaledContext)
                return
            }
        }
        super.attachBaseContext(newBase)
    }

    fun applyUiScale() {
        recreate()
    }

    fun applyWebPageZoom(percent: Int) {
        config.webPageZoomPercent = percent
        tabsModel.tabsStates.forEach { tabState ->
            tabState.webEngine.setPageZoom(percent)
        }
    }

    fun zoomWebIn() {
        val currentTab = tabsModel.currentTab.value
        currentTab?.webEngine?.zoomIn()
    }

    fun zoomWebOut() {
        val currentTab = tabsModel.currentTab.value
        currentTab?.webEngine?.zoomOut()
    }

    override fun search(aText: String) {
        var text = aText
        val trimmedLowercased = text.trim { it <= ' ' }.lowercase()
        if (android.util.Patterns.WEB_URL.matcher(text).matches() || trimmedLowercased.startsWith("http://") || trimmedLowercased.startsWith("https://")) {
            if (!text.lowercase().contains("://")) {
                text = "https://$text"
            }
            navigate(text)
        } else {
            var query: String? = null
            try {
                query = java.net.URLEncoder.encode(text, "utf-8")
            } catch (e1: java.io.UnsupportedEncodingException) {
                e1.printStackTrace()
                com.gothwad.tvbrowser.utils.Utils.showToast(this, R.string.error)
                return
            }
            val searchUrl = config.searchEngineURL.value.replace("[query]", query)
            navigate(searchUrl)
        }
    }

    override fun toggleIncognitoMode() {
        toggleIncognitoMode(true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val localCallback = window.callback
        window.callback = object : Window.Callback by localCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                backNavigationEventsAdapter.dispatchKeyEvent(event)
                val keyCode = if (event.keyCode != 0) event.keyCode else event.scanCode
                val keyCodeBackNavigation = keyCode == KeyEvent.KEYCODE_ESCAPE ||
                        keyCode == KeyEvent.KEYCODE_BUTTON_B || keyCode == KeyEvent.KEYCODE_BACK
                val shortcutMgr = com.gothwad.tvbrowser.singleton.shortcuts.ShortcutMgr.getInstance()
                val currentTab = tabsModel.currentTab.value
                if (!keyCodeBackNavigation && shortcutMgr.handle(event, this@MainActivity, currentTab)) {
                    return true
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
}
