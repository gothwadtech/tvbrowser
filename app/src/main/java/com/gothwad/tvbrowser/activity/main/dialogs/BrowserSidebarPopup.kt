package com.gothwad.tvbrowser.activity.main.dialogs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.gothwad.tvbrowser.BuildConfig
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.downloads.DownloadsActivity
import com.gothwad.tvbrowser.activity.history.HistoryActivity
import com.gothwad.tvbrowser.activity.lock.AppLockActivity
import com.gothwad.tvbrowser.activity.lock.TvPinDialog
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.activity.main.showClipboardActivity
import com.gothwad.tvbrowser.activity.main.showFavoritesDialog
import com.gothwad.tvbrowser.activity.main.showHistoryActivity
import com.gothwad.tvbrowser.activity.main.showSettingsDialog
import com.gothwad.tvbrowser.activity.main.toggleIncognitoMode
import com.gothwad.tvbrowser.filemanager.FileManagerActivity
import com.gothwad.tvbrowser.notes.NotesActivity
import com.gothwad.tvbrowser.singleton.AppLockManager

class BrowserSidebarPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val contentView: View
    private val popupWidth: Int

    init {
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_browser_sidebar, null)
        popupWidth = (240 * activity.resources.displayMetrics.density).toInt()
        popupWindow = PopupWindow(
            contentView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 24f
        }

        setupViews()
    }

    private fun bindItem(view: View, action: () -> Unit) {
        view.setOnClickListener {
            dismiss()
            action()
        }
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        view.performClick()
                        true
                    }
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_BUTTON_B -> {
                        dismiss()
                        true
                    }
                    else -> false
                }
            } else false
        }
    }

    private fun setupViews() {
        val config = activity.config

        // 1. File Manager
        bindItem(contentView.findViewById(R.id.btnSidebarFileManager)) {
            activity.startActivity(Intent(activity, FileManagerActivity::class.java))
        }

        // 2. Bookmarks
        bindItem(contentView.findViewById(R.id.btnSidebarBookmarks)) {
            activity.showFavoritesDialog()
        }

        // 3. History
        bindItem(contentView.findViewById(R.id.btnSidebarHistory)) {
            activity.showHistoryActivity()
        }

        // 4. Downloads
        bindItem(contentView.findViewById(R.id.btnSidebarDownloads)) {
            activity.startActivity(Intent(activity, DownloadsActivity::class.java))
        }

        // 5. Notes
        bindItem(contentView.findViewById(R.id.btnSidebarNotes)) {
            activity.startActivity(Intent(activity, NotesActivity::class.java))
        }

        // 6. Native Clipboard
        bindItem(contentView.findViewById(R.id.btnSidebarClipboard)) {
            activity.showClipboardActivity()
        }

        // 7. Incognito Mode
        val tvIncognito = contentView.findViewById<TextView>(R.id.tvSidebarIncognito)
        if (config.incognitoMode) {
            tvIncognito.text = "Exit Incognito Mode"
        } else {
            tvIncognito.text = "New Incognito Tab"
        }
        bindItem(contentView.findViewById(R.id.btnSidebarIncognito)) {
            activity.toggleIncognitoMode(andSwitchProcess = true)
        }

        // 8. App Lock Security
        bindItem(contentView.findViewById(R.id.btnSidebarAppLock)) {
            if (AppLockManager.isLockEnabled(activity)) {
                AppLockManager.setSessionUnlocked(false)
                activity.startActivity(Intent(activity, AppLockActivity::class.java))
            } else {
                val dlg = TvPinDialog(
                    context = activity,
                    mode = TvPinDialog.Mode.CREATE,
                    onSuccess = {
                        AppLockManager.setSessionUnlocked(false)
                        activity.startActivity(Intent(activity, AppLockActivity::class.java))
                    }
                )
                dlg.show()
            }
        }

        // App UI Zoom buttons
        val btnUi100: Button = contentView.findViewById(R.id.btnSidebarUiScale100)
        val btnUi125: Button = contentView.findViewById(R.id.btnSidebarUiScale125)
        val btnUi150: Button = contentView.findViewById(R.id.btnSidebarUiScale150)

        fun applyUi(scale: Int) {
            config.uiScalePercent = scale
            dismiss()
            activity.applyUiScale()
            Toast.makeText(activity, "App UI Scale set to $scale%", Toast.LENGTH_SHORT).show()
        }

        btnUi100.setOnClickListener { applyUi(100) }
        btnUi125.setOnClickListener { applyUi(125) }
        btnUi150.setOnClickListener { applyUi(150) }

        // 9. Settings
        bindItem(contentView.findViewById(R.id.btnSidebarSettings)) {
            activity.showSettingsDialog()
        }

        // 10. Help & feedback
        bindItem(contentView.findViewById(R.id.btnSidebarHelp)) {
            AlertDialog.Builder(activity)
                .setTitle("Gothwad TV Browser")
                .setMessage("Modern Fast TV Web Browser with Native Clipboard, File Manager & Notes.\n\nVersion: ${BuildConfig.VERSION_NAME}\nDeveloper: gothwadtech@gmail.com")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    fun show(anchorView: View) {
        val density = activity.resources.displayMetrics.density
        val xOffset = 0
        val yOffset = (4 * density).toInt()

        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)

        contentView.post {
            contentView.findViewById<View>(R.id.btnSidebarFileManager)?.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }
}
