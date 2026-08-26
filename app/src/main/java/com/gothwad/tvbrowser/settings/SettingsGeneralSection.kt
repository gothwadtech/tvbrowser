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
        var isUpdatingProgrammatically = false

        fun syncSwitchState() {
            isUpdatingProgrammatically = true
            vb.scAppLock.isChecked = com.gothwad.tvbrowser.singleton.AppLockManager.isLockEnabled(context)
            isUpdatingProgrammatically = false
            updateAppLockStatus(context, vb)
        }

        syncSwitchState()

        val toggleAction = {
            val currentlyEnabled = com.gothwad.tvbrowser.singleton.AppLockManager.isLockEnabled(context)
            if (!currentlyEnabled) {
                // User wants to enable PIN lock
                if (!com.gothwad.tvbrowser.singleton.AppLockManager.hasPinSet(context)) {
                    // Force user to create and confirm 4-digit PIN first
                    val dlg = com.gothwad.tvbrowser.activity.lock.TvPinDialog(
                        context = context,
                        mode = com.gothwad.tvbrowser.activity.lock.TvPinDialog.Mode.CREATE,
                        onSuccess = {
                            syncSwitchState()
                        },
                        onCancel = {
                            syncSwitchState()
                        }
                    )
                    dlg.show()
                } else {
                    com.gothwad.tvbrowser.singleton.AppLockManager.setLockEnabled(context, true)
                    syncSwitchState()
                }
            } else {
                // User wants to disable PIN lock -> Require PIN verification
                val dlg = com.gothwad.tvbrowser.activity.lock.TvPinDialog(
                    context = context,
                    mode = com.gothwad.tvbrowser.activity.lock.TvPinDialog.Mode.VERIFY,
                    onSuccess = {
                        com.gothwad.tvbrowser.singleton.AppLockManager.setLockEnabled(context, false)
                        syncSwitchState()
                        Toast.makeText(context, "PIN Lock Disabled", Toast.LENGTH_SHORT).show()
                    },
                    onCancel = {
                        syncSwitchState()
                    }
                )
                dlg.show()
            }
        }

        vb.llAppLock.setOnClickListener {
            toggleAction()
        }

        vb.scAppLock.setOnClickListener {
            toggleAction()
        }

        vb.scAppLock.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingProgrammatically) return@setOnCheckedChangeListener
            // Revert direct toggle and route through secure action
            syncSwitchState()
            toggleAction()
        }

        vb.btnChangePin.setOnClickListener {
            val mode = if (com.gothwad.tvbrowser.singleton.AppLockManager.hasPinSet(context)) {
                com.gothwad.tvbrowser.activity.lock.TvPinDialog.Mode.CHANGE
            } else {
                com.gothwad.tvbrowser.activity.lock.TvPinDialog.Mode.CREATE
            }
            val dlg = com.gothwad.tvbrowser.activity.lock.TvPinDialog(
                context = context,
                mode = mode,
                onSuccess = {
                    syncSwitchState()
                }
            )
            dlg.show()
        }
    }

    private fun updateAppLockStatus(context: Context, vb: ViewSettingsMainBinding) {
        val isEnabled = com.gothwad.tvbrowser.singleton.AppLockManager.isLockEnabled(context)
        val hasPin = com.gothwad.tvbrowser.singleton.AppLockManager.hasPinSet(context)
        if (isEnabled) {
            vb.tvAppLockStatus.text = "PIN Lock Active (Protected)"
            vb.tvAppLockStatus.setTextColor(0xFF38BDF8.toInt())
            vb.btnChangePin.visibility = View.VISIBLE
            vb.btnChangePin.text = "Change PIN"
        } else if (hasPin) {
            vb.tvAppLockStatus.text = "PIN Lock Disabled (PIN configured)"
            vb.tvAppLockStatus.setTextColor(0xFF94A3B8.toInt())
            vb.btnChangePin.visibility = View.VISIBLE
            vb.btnChangePin.text = "Change PIN"
        } else {
            vb.tvAppLockStatus.text = "No PIN set (Set 4-digit PIN to enable)"
            vb.tvAppLockStatus.setTextColor(0xFF94A3B8.toInt())
            vb.btnChangePin.visibility = View.GONE
        }
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
