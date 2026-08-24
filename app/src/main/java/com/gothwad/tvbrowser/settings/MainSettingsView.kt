package com.gothwad.tvbrowser.settings

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.gothwad.tvbrowser.AppContext
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.activity.main.AdblockModel
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.databinding.ViewSettingsMainBinding
import com.gothwad.tvbrowser.utils.activemodel.ActiveModelsRepository
import com.gothwad.tvbrowser.utils.activity
import com.gothwad.tvbrowser.webengine.WebEngineFactory
import kotlinx.coroutines.launch

class MainSettingsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    private var vb = ViewSettingsMainBinding.inflate(LayoutInflater.from(getContext()), this, true)
    var settingsModel = ActiveModelsRepository.get(SettingsModel::class, activity!!)
    var adblockModel = ActiveModelsRepository.get(AdblockModel::class, activity!!)
    var config = AppContext.provideConfig()
    var onDismissDialog: (() -> Unit)? = null

    init {
        SettingsGeneralSection.initQuickToolsUI(context, vb, config, onDismissDialog, activity)
        SettingsDisplaySection.initDisplayAndZoomSettingsUI(context, vb, config, onDismissDialog, activity)
        SettingsEngineSection.initWebBrowserEngineSettingsUI(context, vb, config, activity)
        SettingsEngineSection.initHomePageAndSearchEngineConfigUI(context, vb, config, settingsModel)
        SettingsEngineSection.initUAStringConfigUI(context, vb, config, settingsModel)
        SettingsEngineSection.initAdBlockConfigUI(context, vb, config, adblockModel, activity)
        SettingsGeneralSection.initThemeSettingsUI(context, vb, config, onDismissDialog, activity)

        initSimpleToggles()
        SettingsRemoteSection.initVirtualCursorPhysicsSettingsUI(context, vb, config)
        SettingsRemoteSection.initKeyboardMouseSettingsUI(context, vb, config, activity)
        SettingsGeneralSection.initAppLockSettingsUI(context, vb)

        vb.btnClearWebCache.setOnClickListener {
            (activity as? MainActivity)?.lifecycleScope?.launch {
                WebEngineFactory.clearCache(context)
                Toast.makeText(context, android.R.string.ok, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initSimpleToggles() {
        vb.scWebViewAlgorithmicDarkeningWithDarkUiMode.isChecked = config.webviewUseAlgorithmicDarkeningWithDarkUiMode
        vb.scWebViewAlgorithmicDarkeningWithDarkUiMode.setOnCheckedChangeListener { _, isChecked ->
            config.webviewUseAlgorithmicDarkeningWithDarkUiMode = isChecked
        }

        vb.scAllowAutoplayMedia.isChecked = config.allowAutoplayMedia
        vb.scAllowAutoplayMedia.setOnCheckedChangeListener { _, isChecked ->
            config.allowAutoplayMedia = isChecked
        }

        vb.scKeepScreenOn.isChecked = settingsModel.keepScreenOn.value
        vb.scKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            settingsModel.keepScreenOn.value = isChecked
        }

        vb.scDisableVirtualKeyboard.isChecked = config.disableVirtualKeyboard
        vb.scDisableVirtualKeyboard.setOnCheckedChangeListener { _, isChecked ->
            config.disableVirtualKeyboard = isChecked
            (activity as? MainActivity)?.applySoftInputMode()
        }

        vb.scEnableVirtualDpad.isChecked = config.enableVirtualDpad
        vb.scEnableVirtualDpad.setOnCheckedChangeListener { _, isChecked ->
            config.enableVirtualDpad = isChecked
            (activity as? MainActivity)?.updateVirtualDpadVisibility()
        }

        vb.scNavigateWithJoystickAxes.isChecked = !config.disableMotionAxesDpadNavigation
        vb.scNavigateWithJoystickAxes.setOnCheckedChangeListener { _, isChecked ->
            config.disableMotionAxesDpadNavigation = !isChecked
        }
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
        SettingsEngineSection.saveAdBlockListUrl(vb, config)
    }

    fun showCategory(category: SettingsCategory) {
        vb.cardDisplayZoom.visibility = if (category == SettingsCategory.GENERAL) View.VISIBLE else View.GONE
        vb.cardThemeMedia.visibility = if (category == SettingsCategory.GENERAL) View.VISIBLE else View.GONE

        vb.cardAppLock.visibility = if (category == SettingsCategory.PRIVACY) View.VISIBLE else View.GONE
        vb.cardCacheData.visibility = if (category == SettingsCategory.PRIVACY) View.VISIBLE else View.GONE

        vb.cardBrowserEngine.visibility = if (category == SettingsCategory.BROWSER) View.VISIBLE else View.GONE

        vb.cardQuickTools.visibility = if (category == SettingsCategory.TOOLS) View.VISIBLE else View.GONE
        vb.cardAdBlockPrivacy.visibility = if (category == SettingsCategory.TOOLS) View.VISIBLE else View.GONE

        vb.cardRemoteCursor.visibility = if (category == SettingsCategory.REMOTE) View.VISIBLE else View.GONE

        vb.cardKeyboardMouse.visibility = if (category == SettingsCategory.KEYBOARD_MOUSE) View.VISIBLE else View.GONE

        post { fullScroll(ScrollView.FOCUS_UP) }
    }
}
