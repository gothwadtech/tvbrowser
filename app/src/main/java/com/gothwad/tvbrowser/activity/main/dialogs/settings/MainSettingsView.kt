package com.gothwad.tvbrowser.activity.main.dialogs.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewFeature
import com.gothwad.tvbrowser.AppContext
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.BrowserApp
import com.gothwad.tvbrowser.activity.main.AdblockModel
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.activity.main.SettingsModel
import com.gothwad.tvbrowser.databinding.ViewSettingsMainBinding
import com.gothwad.tvbrowser.utils.activemodel.ActiveModelsRepository
import com.gothwad.tvbrowser.utils.activity
import com.gothwad.tvbrowser.webengine.WebEngineFactory
import com.gothwad.tvbrowser.webengine.webview.WebViewWebEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainSettingsView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {
    private var vb = ViewSettingsMainBinding.inflate(LayoutInflater.from(getContext()), this, true)
    var settingsModel = ActiveModelsRepository.get(SettingsModel::class, activity!!)
    var adblockModel = ActiveModelsRepository.get(AdblockModel::class, activity!!)
    var config = AppContext.provideConfig()
    var onDismissDialog: (() -> Unit)? = null

    init {
        initQuickToolsUI()

        initDisplayAndZoomSettingsUI()

        initWebBrowserEngineSettingsUI()

        initHomePageAndSearchEngineConfigUI()

        initUAStringConfigUI(context)

        initAdBlockConfigUI()

        initThemeSettingsUI()

        initWebViewAlgorithmicDarkeningWithDarkUiModeUI()

        initAllowAutoplayMediaUI()

        initWebEngineDebugUI()

        initKeepScreenOnUI()

        initDisableVirtualKeyboardUI()

        initJoystickAxesNavigationUI()

        initVirtualCursorPhysicsSettingsUI()

        initAppLockSettingsUI()

        vb.btnClearWebCache.setOnClickListener {
            (activity as MainActivity).lifecycleScope.launch {
                WebEngineFactory.clearCache(context)
                Toast.makeText(context, android.R.string.ok, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initAppLockSettingsUI() {
        val isLocked = com.gothwad.tvbrowser.singleton.AppLockManager.isLockEnabled(context)
        vb.scAppLock.isChecked = isLocked
        vb.llAppLock.setOnClickListener {
            vb.scAppLock.toggle()
        }
        vb.scAppLock.setOnCheckedChangeListener { _, isChecked ->
            com.gothwad.tvbrowser.singleton.AppLockManager.setLockEnabled(context, isChecked)
            updateAppLockStatus()
        }

        vb.btnChangePin.setOnClickListener {
            showChangePinDialog()
        }

        updateAppLockStatus()
    }

    private fun updateAppLockStatus() {
        val isEnabled = com.gothwad.tvbrowser.singleton.AppLockManager.isLockEnabled(context)
        if (isEnabled) {
            vb.tvAppLockStatus.text = "PIN lock is enabled (Protected)"
            vb.tvAppLockStatus.setTextColor(0xFF38BDF8.toInt())
            vb.btnChangePin.visibility = View.VISIBLE
        } else {
            vb.tvAppLockStatus.text = "PIN lock is disabled"
            vb.tvAppLockStatus.setTextColor(0xFF94A3B8.toInt())
        }
    }

    private fun showChangePinDialog() {
        val input = EditText(context).apply {
            hint = "Enter 4-digit PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(40, 30, 40, 30)
            setTextColor(0xFFFFFFFF.toInt())
        }

        AlertDialog.Builder(context)
            .setTitle("🔒 Set 4-Digit TV PIN")
            .setView(input)
            .setPositiveButton("Save PIN") { _, _ ->
                val newPin = input.text.toString().trim()
                if (newPin.length == 4 && newPin.all { it.isDigit() }) {
                    com.gothwad.tvbrowser.singleton.AppLockManager.setPin(context, newPin)
                    Toast.makeText(context, "New PIN saved successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "PIN must be exactly 4 digits!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initWebBrowserEngineSettingsUI() {
        if (WebEngineFactory.getProviders().size == 1) {
            vb.llWebEngine.visibility = View.GONE
            return
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, Config.SupportedWebEngines)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        vb.spWebEngine.adapter = adapter

        vb.spWebEngine.setSelection(Config.SupportedWebEngines.indexOf(config.webEngine), false)

        vb.spWebEngine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (config.webEngine == Config.SupportedWebEngines[position]) return
                if (Config.SupportedWebEngines[position] == Config.ENGINE_WEB_VIEW) {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.warning)
                        .setMessage(R.string.settings_engine_change_webview_msg)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            config.webEngine = Config.SupportedWebEngines[position]
                            showRestartDialog()
                        }
                        .setNegativeButton(R.string.cancel) { _, _ ->
                            vb.spWebEngine.setSelection(Config.SupportedWebEngines.indexOf(config.webEngine), false)
                        }
                        .show()
                    return
                }
                config.webEngine = Config.SupportedWebEngines[position]
                showRestartDialog()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showRestartDialog() {
        AlertDialog.Builder(context)
            .setTitle(R.string.need_restart)
            .setMessage(R.string.need_restart_message)
            .setPositiveButton(R.string.exit) { _, _ ->
                BrowserApp.instance.needToExitProcessAfterMainActivityFinish = true
                BrowserApp.instance.needRestartMainActivityAfterExitingProcess = true
                activity!!.finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun initThemeSettingsUI() {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, context.resources.getStringArray(R.array.themes))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        vb.spTheme.adapter = adapter

        vb.spTheme.setSelection(config.theme.value.ordinal, false)

        vb.spTheme.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (config.theme.value.ordinal == position) return
                val newTheme = Config.Theme.values()[position]
                config.theme.value = newTheme
                val nightMode = when (newTheme) {
                    Config.Theme.BLACK_AMOLED,
                    Config.Theme.BLACK_CHARCOAL,
                    Config.Theme.BLACK_MIDNIGHT -> AppCompatDelegate.MODE_NIGHT_YES
                    Config.Theme.WHITE_PURE,
                    Config.Theme.WHITE_WARM,
                    Config.Theme.WHITE_COOL -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(nightMode)
                WebEngineFactory.onThemeSettingUpdated(newTheme)

                // Instantly apply theme to current activity without full app exit/restart
                onDismissDialog?.invoke()
                (activity as? AppCompatActivity)?.recreate()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun initWebViewAlgorithmicDarkeningWithDarkUiModeUI() {
        vb.scWebViewAlgorithmicDarkeningWithDarkUiMode.isChecked =
            config.webviewUseAlgorithmicDarkeningWithDarkUiMode
        vb.scWebViewAlgorithmicDarkeningWithDarkUiMode.setOnCheckedChangeListener { _, isChecked ->
            config.webviewUseAlgorithmicDarkeningWithDarkUiMode = isChecked
        }
    }

    private fun initAllowAutoplayMediaUI() {
        vb.scAllowAutoplayMedia.isChecked = config.allowAutoplayMedia
        vb.scAllowAutoplayMedia.setOnCheckedChangeListener { _, isChecked ->
            config.allowAutoplayMedia = isChecked
        }
    }

    private fun initWebEngineDebugUI() {
        vb.scWebEngineDebug.isChecked = config.webEngineDebug
        vb.scWebEngineDebug.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                config.webEngineDebug = false
                return@setOnCheckedChangeListener
            }

            AlertDialog.Builder(context)
                .setTitle(R.string.warning)
                .setMessage(R.string.web_engine_debug_warning_message)
                .setPositiveButton(R.string.ok) { _, _ ->
                    config.webEngineDebug = true
                    Toast.makeText(context, context.getString(R.string.need_restart), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    vb.scWebEngineDebug.isChecked = false
                }
                .setOnCancelListener {
                    vb.scWebEngineDebug.isChecked = false
                }
                .show()
        }
    }

    private fun initKeepScreenOnUI() {
        vb.scKeepScreenOn.isChecked = settingsModel.keepScreenOn.value

        vb.scKeepScreenOn.setOnCheckedChangeListener { buttonView, isChecked ->
            settingsModel.keepScreenOn.value = isChecked
        }
    }

    private fun initDisableVirtualKeyboardUI() {
        vb.scDisableVirtualKeyboard.isChecked = config.disableVirtualKeyboard
        vb.scDisableVirtualKeyboard.setOnCheckedChangeListener { _, isChecked ->
            config.disableVirtualKeyboard = isChecked
            (activity as? MainActivity)?.applySoftInputMode()
        }
    }

    private fun initJoystickAxesNavigationUI() {
        vb.scNavigateWithJoystickAxes.isChecked = !config.disableMotionAxesDpadNavigation
        vb.scNavigateWithJoystickAxes.setOnCheckedChangeListener { _, isChecked ->
            config.disableMotionAxesDpadNavigation = !isChecked
        }
    }

    private fun initVirtualCursorPhysicsSettingsUI() {
        vb.scEnableVirtualCursor.isChecked = config.enableVirtualCursor
        vb.llVirtualCursorDetails.visibility = if (config.enableVirtualCursor) VISIBLE else GONE
        vb.scEnableVirtualCursor.setOnCheckedChangeListener { _, isChecked ->
            config.enableVirtualCursor = isChecked
            vb.llVirtualCursorDetails.visibility = if (isChecked) VISIBLE else GONE
        }

        // Style spinner
        vb.spCursorStyle.setSelection(config.cursorStyle.coerceIn(0, 4))
        vb.spCursorStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                config.cursorStyle = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Size seekbar
        val minSize = Config.CURSOR_SIZE_PERCENT_MIN
        val maxSize = Config.CURSOR_SIZE_PERCENT_MAX
        vb.sbCursorSize.max = maxSize - minSize
        vb.sbCursorSize.progress = config.cursorSizePercent - minSize
        vb.tvCursorSizeValue.text = context.getString(R.string.cursor_physics_percent, config.cursorSizePercent)
        vb.sbCursorSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorSizePercent = minSize + progress
                vb.tvCursorSizeValue.text = context.getString(R.string.cursor_physics_percent, config.cursorSizePercent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Speed and Acceleration
        val minP = Config.CURSOR_PHYSICS_PERCENT_MIN
        val maxP = Config.CURSOR_PHYSICS_PERCENT_MAX
        val range = maxP - minP
        vb.sbCursorMaxSpeed.max = range
        vb.sbCursorAcceleration.max = range
        fun refreshValueLabels() {
            vb.tvCursorMaxSpeedValue.text = context.getString(R.string.cursor_physics_percent, config.cursorMaxSpeedPercent)
            vb.tvCursorAccelerationValue.text = context.getString(R.string.cursor_physics_percent, config.cursorAccelerationPercent)
        }
        vb.sbCursorMaxSpeed.progress = config.cursorMaxSpeedPercent - minP
        vb.sbCursorAcceleration.progress = config.cursorAccelerationPercent - minP
        refreshValueLabels()
        vb.sbCursorMaxSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorMaxSpeedPercent = minP + progress
                refreshValueLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        vb.sbCursorAcceleration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                config.cursorAccelerationPercent = minP + progress
                refreshValueLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun initAdBlockConfigUI() {
        vb.scAdblock.isChecked = config.adBlockEnabled
        vb.etAdBlockerListUrl.setText(config.adBlockListURL.value)
        vb.llAdblock.setOnClickListener {
            vb.scAdblock.isChecked = !vb.scAdblock.isChecked
            config.adBlockEnabled = vb.scAdblock.isChecked
            vb.llAdBlockerDetails.visibility = if (vb.scAdblock.isChecked) VISIBLE else GONE
        }
        vb.llAdBlockerDetails.visibility = if (config.adBlockEnabled) VISIBLE else GONE

        adblockModel.clientLoading.subscribe(activity as FragmentActivity) {
            updateAdBlockInfo()
        }

        vb.btnAdBlockerUpdate.setOnClickListener {
            if (adblockModel.clientLoading.value) return@setOnClickListener
            saveAdBlockListUrl()
            adblockModel.loadAdBlockList(true)
            it.isEnabled = false
        }

        updateAdBlockInfo()
    }

    private fun saveAdBlockListUrl() {
        val value = vb.etAdBlockerListUrl.text.toString().trim()
        config.adBlockListURL.value = value.ifEmpty { Config.DEFAULT_ADBLOCK_LIST_URL }
    }

    private fun updateAdBlockInfo() {
        val dateFormat = SimpleDateFormat("hh:mm dd MMMM yyyy", Locale.getDefault())
        val lastUpdate = if (config.adBlockListLastUpdate == 0L)
            context.getString(R.string.never) else
            dateFormat.format(Date(config.adBlockListLastUpdate))
        val infoText = "${context.getString(R.string.last_update)}: $lastUpdate"
        vb.tvAdBlockerListInfo.text = infoText
        val loadingAdBlockList = adblockModel.clientLoading.value
        vb.btnAdBlockerUpdate.visibility = if (loadingAdBlockList) View.GONE else View.VISIBLE
        vb.pbAdBlockerListLoading.visibility = if (loadingAdBlockList) View.VISIBLE else View.GONE
    }

    private fun initUAStringConfigUI(context: Context) {
        if (config.userAgentString.value?.contains("Browser/1.0 ") == true) {//legacy ua string - now default one should be used
            config.userAgentString.value = null
        }
        val selected = if (config.userAgentString.value == null) {
            0
        } else {
            settingsModel.uaStrings.indexOf(config.userAgentString.value ?: "")
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, settingsModel.userAgentStringTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spTitles.adapter = adapter

        if (selected != -1) {
            vb.spTitles.setSelection(selected, false)
            vb.etUAString.setText(settingsModel.uaStrings[selected])
        } else {
            vb.spTitles.setSelection(settingsModel.userAgentStringTitles.size - 1, false)
            vb.llUAString.visibility = View.VISIBLE
            vb.etUAString.setText(config.userAgentString.value ?: "")
            vb.etUAString.requestFocus()
        }
        vb.spTitles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (position == settingsModel.userAgentStringTitles.size - 1 && vb.llUAString.visibility == View.GONE) {
                    vb.llUAString.visibility = View.VISIBLE
                    vb.llUAString.startAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in))
                    vb.etUAString.requestFocus()
                }
                vb.etUAString.setText(settingsModel.uaStrings[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>) {

            }
        }
    }

    private fun initHomePageAndSearchEngineConfigUI() {
        var selected = 0
        if ("" != config.searchEngineURL.value) {
            selected = Config.SearchEnginesURLs.indexOf(config.searchEngineURL.value)
        }

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, Config.SearchEnginesTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        vb.spEngine.adapter = adapter

        if (selected != -1) {
            vb.spEngine.setSelection(selected)
            vb.etUrl.setText(Config.SearchEnginesURLs[selected])
        } else {
            vb.spEngine.setSelection(Config.SearchEnginesTitles.size - 1)
            vb.llURL.visibility = View.VISIBLE
            vb.etUrl.setText(config.searchEngineURL.value)
            vb.etUrl.requestFocus()
        }
        vb.spEngine.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                if (position == (Config.SearchEnginesTitles.size - 1)) {
                    if (vb.llURL.visibility == View.GONE) {
                        vb.llURL.visibility = View.VISIBLE
                        vb.llURL.startAnimation(
                            AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
                        )
                    }
                    vb.etUrl.setText(config.searchEngineURL.value)
                    vb.etUrl.requestFocus()
                    return
                } else {
                    vb.llURL.visibility = View.GONE
                    vb.etUrl.setText(Config.SearchEnginesURLs[position])
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val homePageSpinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, context.resources.getStringArray(R.array.home_page_modes))
        homePageSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spHomePage.adapter = homePageSpinnerAdapter
        vb.spHomePage.setSelection(settingsModel.homePageMode.ordinal)

        vb.spHomePage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val homePageMode = Config.HomePageMode.entries[position]
                vb.llCustomHomePage.visibility = if (homePageMode == Config.HomePageMode.CUSTOM) View.VISIBLE else View.GONE
                vb.llHomePageLinksMode.visibility = if (homePageMode == Config.HomePageMode.HOME_PAGE) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val homePageLinksSpinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, context.resources.getStringArray(R.array.home_page_links_modes))
        homePageLinksSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        vb.spHomePageLinks.adapter = homePageLinksSpinnerAdapter
        vb.spHomePageLinks.setSelection(settingsModel.homePageLinksMode.ordinal)

        vb.etCustomHomePageUrl.setText(settingsModel.homePage)
    }

    fun save() {
        val customSearchEngineUrl = vb.etUrl.text.toString()
        settingsModel.setSearchEngineURL(customSearchEngineUrl)

        val homePageMode = Config.HomePageMode.entries[vb.spHomePage.selectedItemPosition]
        val customHomePageURL = vb.etCustomHomePageUrl.text.toString()
        val homePageLinksMode = Config.HomePageLinksMode.entries[vb.spHomePageLinks.selectedItemPosition]
        settingsModel.setHomePageProperties(homePageMode, customHomePageURL, homePageLinksMode)

        val userAgent = vb.etUAString.text.toString().trim(' ')
        config.userAgentString.value = userAgent.ifEmpty { null }
        saveAdBlockListUrl()
    }

    private fun initQuickToolsUI() {
        vb.btnQuickHistory.setOnClickListener {
            onDismissDialog?.invoke()
            (activity as? MainActivity)?.showHistory()
        }
        vb.btnQuickFavorites.setOnClickListener {
            onDismissDialog?.invoke()
            (activity as? MainActivity)?.showFavorites()
        }
        vb.btnQuickClipboard.setOnClickListener {
            onDismissDialog?.invoke()
            (activity as? MainActivity)?.showClipboard()
        }
        vb.btnQuickDownloads.setOnClickListener {
            onDismissDialog?.invoke()
            (activity as? MainActivity)?.showDownloads()
        }
        vb.btnQuickIncognito.setOnClickListener {
            (activity as? MainActivity)?.toggleIncognitoMode()
            Toast.makeText(context, R.string.incognito_mode, Toast.LENGTH_SHORT).show()
        }
        vb.btnQuickPopupBlock.setOnClickListener {
            onDismissDialog?.invoke()
            (activity as? MainActivity)?.apply {
                lifecycleScope.launch {
                    showPopupBlockOptions()
                }
            }
        }
        vb.btnQuickRotate.setOnClickListener {
            val nextOrientation = when (config.screenOrientation) {
                Config.ORIENTATION_LANDSCAPE -> Config.ORIENTATION_PORTRAIT
                Config.ORIENTATION_PORTRAIT -> Config.ORIENTATION_AUTO
                else -> Config.ORIENTATION_LANDSCAPE
            }
            config.screenOrientation = nextOrientation
            (activity as? MainActivity)?.applyScreenOrientation()
            val label = when (nextOrientation) {
                Config.ORIENTATION_PORTRAIT -> "Screen Orientation: Portrait"
                Config.ORIENTATION_AUTO -> "Screen Orientation: Auto Rotate"
                else -> "Screen Orientation: Landscape"
            }
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        }

        vb.btnQuickZoomIn.setOnClickListener {
            (activity as? MainActivity)?.zoomWebIn()
            Toast.makeText(context, R.string.quick_zoom_in, Toast.LENGTH_SHORT).show()
        }

        vb.btnQuickZoomOut.setOnClickListener {
            (activity as? MainActivity)?.zoomWebOut()
            Toast.makeText(context, R.string.quick_zoom_out, Toast.LENGTH_SHORT).show()
        }

        vb.btnQuickZoomReset.setOnClickListener {
            (activity as? MainActivity)?.applyWebPageZoom(100)
            config.webPageZoomPercent = 100
            vb.sbWebPageZoom.progress = 100 - Config.WEB_PAGE_ZOOM_PERCENT_MIN
            vb.tvWebPageZoomValue.text = "100%"
            Toast.makeText(context, R.string.quick_zoom_reset, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initDisplayAndZoomSettingsUI() {
        // UI Scaling controls
        val minUiScale = Config.UI_SCALE_PERCENT_MIN
        val maxUiScale = Config.UI_SCALE_PERCENT_MAX
        vb.sbUiScale.max = maxUiScale - minUiScale
        vb.sbUiScale.progress = config.uiScalePercent - minUiScale
        vb.tvUiScaleValue.text = "${config.uiScalePercent}%"

        vb.sbUiScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = minUiScale + progress
                config.uiScalePercent = value
                vb.tvUiScaleValue.text = "$value%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        vb.btnUiScale100.setOnClickListener {
            config.uiScalePercent = 100
            vb.sbUiScale.progress = 100 - minUiScale
            vb.tvUiScaleValue.text = "100%"
        }
        vb.btnUiScale125.setOnClickListener {
            config.uiScalePercent = 125
            vb.sbUiScale.progress = 125 - minUiScale
            vb.tvUiScaleValue.text = "125%"
        }
        vb.btnUiScale150.setOnClickListener {
            config.uiScalePercent = 150
            vb.sbUiScale.progress = 150 - minUiScale
            vb.tvUiScaleValue.text = "150%"
        }
        vb.btnUiScaleApply.setOnClickListener {
            onDismissDialog?.invoke()
            (activity as? MainActivity)?.applyUiScale()
            Toast.makeText(context, R.string.apply_ui_scale, Toast.LENGTH_SHORT).show()
        }

        // Web Page Zoom controls
        val minWebZoom = Config.WEB_PAGE_ZOOM_PERCENT_MIN
        val maxWebZoom = Config.WEB_PAGE_ZOOM_PERCENT_MAX
        vb.sbWebPageZoom.max = maxWebZoom - minWebZoom
        vb.sbWebPageZoom.progress = config.webPageZoomPercent - minWebZoom
        vb.tvWebPageZoomValue.text = "${config.webPageZoomPercent}%"

        vb.sbWebPageZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = minWebZoom + progress
                config.webPageZoomPercent = value
                vb.tvWebPageZoomValue.text = "$value%"
                (activity as? MainActivity)?.applyWebPageZoom(value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fun setWebZoom(percent: Int) {
            config.webPageZoomPercent = percent
            vb.sbWebPageZoom.progress = (percent - minWebZoom).coerceIn(0, maxWebZoom - minWebZoom)
            vb.tvWebPageZoomValue.text = "$percent%"
            (activity as? MainActivity)?.applyWebPageZoom(percent)
        }

        vb.btnWebZoom75.setOnClickListener { setWebZoom(75) }
        vb.btnWebZoom100.setOnClickListener { setWebZoom(100) }
        vb.btnWebZoom125.setOnClickListener { setWebZoom(125) }
        vb.btnWebZoom150.setOnClickListener { setWebZoom(150) }
        vb.btnWebZoom200.setOnClickListener { setWebZoom(200) }
    }

    fun showCategory(category: SettingsCategory) {
        when (category) {
            SettingsCategory.GENERAL -> {
                vb.cardDisplayZoom.visibility = View.VISIBLE
                vb.cardThemeMedia.visibility = View.VISIBLE
                vb.cardBrowserEngine.visibility = View.GONE
                vb.cardAdBlockPrivacy.visibility = View.GONE
                vb.cardQuickTools.visibility = View.GONE
                vb.cardRemoteCursor.visibility = View.GONE
                vb.cardCacheData.visibility = View.GONE
            }
            SettingsCategory.PRIVACY -> {
                vb.cardAdBlockPrivacy.visibility = View.VISIBLE
                vb.cardCacheData.visibility = View.VISIBLE
                vb.cardDisplayZoom.visibility = View.GONE
                vb.cardThemeMedia.visibility = View.GONE
                vb.cardBrowserEngine.visibility = View.GONE
                vb.cardQuickTools.visibility = View.GONE
                vb.cardRemoteCursor.visibility = View.GONE
            }
            SettingsCategory.BROWSER -> {
                vb.cardBrowserEngine.visibility = View.VISIBLE
                vb.cardDisplayZoom.visibility = View.GONE
                vb.cardThemeMedia.visibility = View.GONE
                vb.cardAdBlockPrivacy.visibility = View.GONE
                vb.cardQuickTools.visibility = View.GONE
                vb.cardRemoteCursor.visibility = View.GONE
                vb.cardCacheData.visibility = View.GONE
            }
            SettingsCategory.TOOLS -> {
                vb.cardQuickTools.visibility = View.VISIBLE
                vb.cardAdBlockPrivacy.visibility = View.VISIBLE
                vb.cardDisplayZoom.visibility = View.GONE
                vb.cardThemeMedia.visibility = View.GONE
                vb.cardBrowserEngine.visibility = View.GONE
                vb.cardRemoteCursor.visibility = View.GONE
                vb.cardCacheData.visibility = View.GONE
            }
            SettingsCategory.REMOTE -> {
                vb.cardRemoteCursor.visibility = View.VISIBLE
                vb.cardDisplayZoom.visibility = View.GONE
                vb.cardThemeMedia.visibility = View.GONE
                vb.cardBrowserEngine.visibility = View.GONE
                vb.cardAdBlockPrivacy.visibility = View.GONE
                vb.cardQuickTools.visibility = View.GONE
                vb.cardCacheData.visibility = View.GONE
            }
        }
        post { fullScroll(ScrollView.FOCUS_UP) }
    }
}

enum class SettingsCategory {
    GENERAL,
    PRIVACY,
    BROWSER,
    TOOLS,
    REMOTE
}

