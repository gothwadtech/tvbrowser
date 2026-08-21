package com.gothwad.tvbrowser.activity.main.dialogs.settings

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.SettingsModel

class SettingsDialog(context: Context, val model: SettingsModel) :
    Dialog(context, R.style.SettingsDialog),
    DialogInterface.OnDismissListener, VersionSettingsView.Callback {

    private var mainView: MainSettingsView? = null
    private var versionView: VersionSettingsView? = null
    private var shortcutsView: ShortcutsSettingsView? = null
    private var flTabsContent: FrameLayout? = null

    private lateinit var btnTabGeneral: Button
    private lateinit var btnTabPrivacy: Button
    private lateinit var btnTabBrowser: Button
    private lateinit var btnTabTools: Button
    private lateinit var btnTabRemote: Button
    private lateinit var btnTabKeyboardMouse: Button
    private lateinit var btnTabShortcuts: Button
    private lateinit var btnTabAbout: Button

    private val allButtons by lazy {
        listOf(
            btnTabGeneral,
            btnTabPrivacy,
            btnTabBrowser,
            btnTabTools,
            btnTabRemote,
            btnTabKeyboardMouse,
            btnTabShortcuts,
            btnTabAbout
        )
    }

    init {
        setTitle(R.string.settings)
        setContentView(R.layout.dialog_settings)

        flTabsContent = findViewById(R.id.flTabsContent)
        findViewById<View>(R.id.ibCloseSettings)?.setOnClickListener { dismiss() }

        btnTabGeneral = findViewById(R.id.btnTabGeneral)
        btnTabPrivacy = findViewById(R.id.btnTabPrivacy)
        btnTabBrowser = findViewById(R.id.btnTabBrowser)
        btnTabTools = findViewById(R.id.btnTabTools)
        btnTabRemote = findViewById(R.id.btnTabRemote)
        btnTabKeyboardMouse = findViewById(R.id.btnTabKeyboardMouse)
        btnTabShortcuts = findViewById(R.id.btnTabShortcuts)
        btnTabAbout = findViewById(R.id.btnTabAbout)

        mainView = MainSettingsView(context).apply {
            onDismissDialog = { dismiss() }
        }

        versionView = VersionSettingsView(context).apply {
            callback = this@SettingsDialog
        }

        shortcutsView = ShortcutsSettingsView(context)

        setupTabButtons()
        selectCategory(btnTabGeneral, SettingsCategory.GENERAL)

        setOnDismissListener(this)
    }

    private fun setupTabButtons() {
        btnTabGeneral.setOnClickListener { selectCategory(btnTabGeneral, SettingsCategory.GENERAL) }
        btnTabPrivacy.setOnClickListener { selectCategory(btnTabPrivacy, SettingsCategory.PRIVACY) }
        btnTabBrowser.setOnClickListener { selectCategory(btnTabBrowser, SettingsCategory.BROWSER) }
        btnTabTools.setOnClickListener { selectCategory(btnTabTools, SettingsCategory.TOOLS) }
        btnTabRemote.setOnClickListener { selectCategory(btnTabRemote, SettingsCategory.REMOTE) }
        btnTabKeyboardMouse.setOnClickListener { selectCategory(btnTabKeyboardMouse, SettingsCategory.KEYBOARD_MOUSE) }
        btnTabShortcuts.setOnClickListener { selectShortcutsTab(btnTabShortcuts) }
        btnTabAbout.setOnClickListener { selectAboutTab(btnTabAbout) }

        btnTabGeneral.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectCategory(btnTabGeneral, SettingsCategory.GENERAL)
        }
        btnTabPrivacy.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectCategory(btnTabPrivacy, SettingsCategory.PRIVACY)
        }
        btnTabBrowser.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectCategory(btnTabBrowser, SettingsCategory.BROWSER)
        }
        btnTabTools.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectCategory(btnTabTools, SettingsCategory.TOOLS)
        }
        btnTabRemote.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectCategory(btnTabRemote, SettingsCategory.REMOTE)
        }
        btnTabKeyboardMouse.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectCategory(btnTabKeyboardMouse, SettingsCategory.KEYBOARD_MOUSE)
        }
        btnTabShortcuts.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectShortcutsTab(btnTabShortcuts)
        }
        btnTabAbout.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) selectAboutTab(btnTabAbout)
        }
    }

    private fun updateHighlight(activeButton: Button) {
        val activeColor = ContextCompat.getColor(context, R.color.day_night_text_color_contrast)
        val inactiveColor = ContextCompat.getColor(context, R.color.day_night_text_secondary)
        val activeTint = ContextCompat.getColor(context, R.color.progressbar_tint)
        val defaultTint = ContextCompat.getColor(context, R.color.day_night_text_secondary)

        allButtons.forEach { btn ->
            val isActive = (btn == activeButton)
            btn.isActivated = isActive
            if (isActive) {
                btn.setTextColor(activeColor)
                btn.setTypeface(null, Typeface.BOLD)
                btn.compoundDrawableTintList = ColorStateList.valueOf(activeTint)
            } else {
                btn.setTextColor(inactiveColor)
                btn.setTypeface(null, Typeface.NORMAL)
                btn.compoundDrawableTintList = ColorStateList.valueOf(defaultTint)
            }
        }
    }

    private fun selectCategory(button: Button, category: SettingsCategory) {
        updateHighlight(button)
        val container = flTabsContent ?: return
        val mv = mainView ?: return

        if (container.indexOfChild(mv) == -1) {
            container.removeAllViews()
            container.addView(mv)
        }
        mv.showCategory(category)
    }

    private fun selectShortcutsTab(button: Button) {
        updateHighlight(button)
        val container = flTabsContent ?: return
        val sv = shortcutsView ?: return

        container.removeAllViews()
        container.addView(sv)
    }

    private fun selectAboutTab(button: Button) {
        updateHighlight(button)
        val container = flTabsContent ?: return
        val vv = versionView ?: return

        container.removeAllViews()
        container.addView(vv)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onDismiss(dialog: DialogInterface?) {
        mainView?.save()
    }

    override fun onNeedToCloseSettings() {
        dismiss()
    }
}
