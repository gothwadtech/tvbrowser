package com.gothwad.tvbrowser.settings

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.activity.main.applyWebPageZoom
import com.gothwad.tvbrowser.activity.main.zoomWebIn
import com.gothwad.tvbrowser.activity.main.zoomWebOut
import com.gothwad.tvbrowser.databinding.ViewSettingsMainBinding
import com.gothwad.tvbrowser.webengine.WebEngineFactory
import kotlinx.coroutines.launch

object SettingsGeneralSection {

    fun initThemeSettingsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        onDismissDialog: (() -> Unit)?,
        activity: Context?
    ) {
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

                onDismissDialog?.invoke()
                (activity as? AppCompatActivity)?.recreate()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    fun initAppLockSettingsUI(context: Context, vb: ViewSettingsMainBinding) {
        val isLocked = com.gothwad.tvbrowser.singleton.AppLockManager.isLockEnabled(context)
        vb.scAppLock.isChecked = isLocked
        vb.llAppLock.setOnClickListener {
            vb.scAppLock.toggle()
        }
        vb.scAppLock.setOnCheckedChangeListener { _, isChecked ->
            com.gothwad.tvbrowser.singleton.AppLockManager.setLockEnabled(context, isChecked)
            updateAppLockStatus(context, vb)
        }

        vb.btnChangePin.setOnClickListener {
            showChangePinDialog(context, vb)
        }

        updateAppLockStatus(context, vb)
    }

    private fun updateAppLockStatus(context: Context, vb: ViewSettingsMainBinding) {
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

    private fun showChangePinDialog(context: Context, vb: ViewSettingsMainBinding) {
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
                    updateAppLockStatus(context, vb)
                    Toast.makeText(context, "New PIN saved successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "PIN must be exactly 4 digits!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun initQuickToolsUI(
        context: Context,
        vb: ViewSettingsMainBinding,
        config: Config,
        onDismissDialog: (() -> Unit)?,
        activity: Context?
    ) {
        val mainAct = activity as? MainActivity
        vb.btnQuickHistory.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.showHistory()
        }
        vb.btnQuickFavorites.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.showFavorites()
        }
        vb.btnQuickClipboard.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.showClipboard()
        }
        vb.btnQuickDownloads.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.showDownloads()
        }
        vb.btnQuickIncognito.setOnClickListener {
            mainAct?.toggleIncognitoMode()
            Toast.makeText(context, R.string.incognito_mode, Toast.LENGTH_SHORT).show()
        }
        vb.btnQuickPopupBlock.setOnClickListener {
            onDismissDialog?.invoke()
            mainAct?.apply {
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
            mainAct?.applyScreenOrientation()
            val label = when (nextOrientation) {
                Config.ORIENTATION_PORTRAIT -> "Screen Orientation: Portrait"
                Config.ORIENTATION_AUTO -> "Screen Orientation: Auto Rotate"
                else -> "Screen Orientation: Landscape"
            }
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        }

        vb.btnQuickZoomIn.setOnClickListener {
            mainAct?.zoomWebIn()
            vb.sbWebPageZoom.progress = (config.webPageZoomPercent - Config.WEB_PAGE_ZOOM_PERCENT_MIN).coerceIn(0, Config.WEB_PAGE_ZOOM_PERCENT_MAX - Config.WEB_PAGE_ZOOM_PERCENT_MIN)
            vb.tvWebPageZoomValue.text = "${config.webPageZoomPercent}%"
            Toast.makeText(context, R.string.quick_zoom_in, Toast.LENGTH_SHORT).show()
        }

        vb.btnQuickZoomOut.setOnClickListener {
            mainAct?.zoomWebOut()
            vb.sbWebPageZoom.progress = (config.webPageZoomPercent - Config.WEB_PAGE_ZOOM_PERCENT_MIN).coerceIn(0, Config.WEB_PAGE_ZOOM_PERCENT_MAX - Config.WEB_PAGE_ZOOM_PERCENT_MIN)
            vb.tvWebPageZoomValue.text = "${config.webPageZoomPercent}%"
            Toast.makeText(context, R.string.quick_zoom_out, Toast.LENGTH_SHORT).show()
        }

        vb.btnQuickZoomReset.setOnClickListener {
            mainAct?.applyWebPageZoom(100)
            config.webPageZoomPercent = 100
            vb.sbWebPageZoom.progress = 100 - Config.WEB_PAGE_ZOOM_PERCENT_MIN
            vb.tvWebPageZoomValue.text = "100%"
            Toast.makeText(context, R.string.quick_zoom_reset, Toast.LENGTH_SHORT).show()
        }
    }
}
