package com.gothwad.tvbrowser.settings

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.gothwad.tvbrowser.BrowserApp
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.AdblockModel
import com.gothwad.tvbrowser.databinding.ViewSettingsMainBinding
import com.gothwad.tvbrowser.webengine.WebEngineFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SettingsEngineSection {

    fun initWebBrowserEngineSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        activity: Context?
    ) {
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
                            showRestartDialog(context, activity)
                        }
                        .setNegativeButton(R.string.cancel) { _, _ ->
                            vb.spWebEngine.setSelection(Config.SupportedWebEngines.indexOf(config.webEngine), false)
                        }
                        .show()
                    return
                }
                config.webEngine = Config.SupportedWebEngines[position]
                showRestartDialog(context, activity)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showRestartDialog(context: Context, activity: Context?) {
        AlertDialog.Builder(context)
            .setTitle(R.string.need_restart)
            .setMessage(R.string.need_restart_message)
            .setPositiveButton(R.string.exit) { _, _ ->
                BrowserApp.instance.needToExitProcessAfterMainActivityFinish = true
                BrowserApp.instance.needRestartMainActivityAfterExitingProcess = true
                (activity as? android.app.Activity)?.finish()
            }
            .setCancelable(false)
            .show()
    }

    fun initHomePageAndSearchEngineConfigUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        settingsModel: SettingsModel
    ) {
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
                        vb.llURL.startAnimation(AnimationUtils.loadAnimation(context, android.R.anim.fade_in))
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

    fun initUAStringConfigUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        settingsModel: SettingsModel
    ) {
        if (config.userAgentString.value?.contains("Browser/1.0 ") == true) {
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

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    fun initAdBlockConfigUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        adblockModel: AdblockModel,
        activity: Context?
    ) {
        vb.scAdblock.isChecked = config.adBlockEnabled
        vb.etAdBlockerListUrl.setText(config.adBlockListURL.value)
        vb.llAdblock.setOnClickListener {
            vb.scAdblock.isChecked = !vb.scAdblock.isChecked
            config.adBlockEnabled = vb.scAdblock.isChecked
            vb.llAdBlockerDetails.visibility = if (vb.scAdblock.isChecked) View.VISIBLE else View.GONE
        }
        vb.llAdBlockerDetails.visibility = if (config.adBlockEnabled) View.VISIBLE else View.GONE

        (activity as? FragmentActivity)?.let { fragAct ->
            adblockModel.clientLoading.subscribe(fragAct) {
                updateAdBlockInfo(context, vb, config, adblockModel)
            }
        }

        vb.btnAdBlockerUpdate.setOnClickListener {
            if (adblockModel.clientLoading.value) return@setOnClickListener
            saveAdBlockListUrl(vb, config)
            adblockModel.loadAdBlockList(true)
            it.isEnabled = false
        }

        updateAdBlockInfo(context, vb, config, adblockModel)
    }

    fun saveAdBlockListUrl(vb: ViewSettingsMainBinding, config: Config) {
        val value = vb.etAdBlockerListUrl.text.toString().trim()
        config.adBlockListURL.value = value.ifEmpty { Config.DEFAULT_ADBLOCK_LIST_URL }
    }

    fun updateAdBlockInfo(context: Context, vb: ViewSettingsMainBinding, config: Config, adblockModel: AdblockModel) {
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
}
