package com.gothwad.tvbrowser.activity.main.dialogs

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.Toast
import com.gothwad.tvbrowser.BuildConfig
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.clipboard.ClipboardActivity
import com.gothwad.tvbrowser.activity.downloads.DownloadsActivity
import com.gothwad.tvbrowser.activity.filemanager.FileManagerActivity
import com.gothwad.tvbrowser.activity.lock.AppLockActivity
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.activity.main.openInNewTab
import com.gothwad.tvbrowser.activity.main.showClipboardActivity
import com.gothwad.tvbrowser.activity.main.showFavoritesDialog
import com.gothwad.tvbrowser.activity.main.showHistoryActivity
import com.gothwad.tvbrowser.activity.main.showSettingsDialog
import com.gothwad.tvbrowser.activity.main.toggleIncognitoMode
import com.gothwad.tvbrowser.activity.notes.NotesActivity
import com.gothwad.tvbrowser.singleton.AppLockManager

class ChromeMenuPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val contentView: View

    init {
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_chrome_menu, null)
        popupWindow = PopupWindow(
            contentView,
            (230 * activity.resources.displayMetrics.density).toInt(),
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

    private fun setupViews() {
        val currentTab = activity.tabsModel.currentTab.value
        val config = activity.config

        // Top 3 Quick Action Icons: Lock, Page Info, Refresh
        val btnAppLock: ImageButton = contentView.findViewById(R.id.btnMenuAppLock)
        val btnPageInfo: ImageButton = contentView.findViewById(R.id.btnMenuPageInfo)
        val btnRefresh: ImageButton = contentView.findViewById(R.id.btnMenuRefresh)

        btnAppLock.setOnClickListener {
            dismiss()
            if (AppLockManager.isLockEnabled(activity)) {
                AppLockManager.setSessionUnlocked(false)
                activity.startActivity(Intent(activity, AppLockActivity::class.java))
            } else {
                activity.showSettingsDialog()
                Toast.makeText(activity, "Configure App Lock PIN in Privacy Settings", Toast.LENGTH_SHORT).show()
            }
        }

        btnPageInfo.setOnClickListener {
            dismiss()
            val url = currentTab?.url ?: ""
            val isSecure = url.startsWith("https://")
            AlertDialog.Builder(activity)
                .setTitle(if (isSecure) "🔒 Secure Connection" else "ℹ️ Page Info")
                .setMessage("URL: ${if (url.isEmpty()) "Home Page" else url}\n\nSecurity: ${if (isSecure) "Encrypted Connection (HTTPS / SSL Active)" else "Unencrypted Connection (HTTP)"}\n\nCookies: Active\nJavaScript: Enabled")
                .setPositiveButton("OK", null)
                .show()
        }

        btnRefresh.setOnClickListener {
            dismiss()
            currentTab?.webEngine?.reload()
        }

        // List Actions
        // 1. New Tab
        contentView.findViewById<View>(R.id.btnMenuNewTab).setOnClickListener {
            dismiss()
            activity.openInNewTab(Config.HOME_PAGE_URL, needToHideMenuOverlay = true)
        }

        // 2. New Incognito Tab
        contentView.findViewById<View>(R.id.btnMenuIncognito).setOnClickListener {
            dismiss()
            activity.toggleIncognitoMode(andSwitchProcess = true)
        }

        // 3. TV Notes
        contentView.findViewById<View>(R.id.btnMenuNotes).setOnClickListener {
            dismiss()
            activity.startActivity(Intent(activity, NotesActivity::class.java))
        }

        // 4. Clipboard Manager
        contentView.findViewById<View>(R.id.btnMenuClipboard).setOnClickListener {
            dismiss()
            activity.showClipboardActivity()
        }

        // 5. File Manager
        contentView.findViewById<View>(R.id.btnMenuFileManager).setOnClickListener {
            dismiss()
            activity.startActivity(Intent(activity, FileManagerActivity::class.java))
        }

        // 6. History
        contentView.findViewById<View>(R.id.btnMenuHistory).setOnClickListener {
            dismiss()
            activity.showHistoryActivity()
        }

        // 7. Share...
        contentView.findViewById<View>(R.id.btnMenuShare).setOnClickListener {
            dismiss()
            val url = currentTab?.url
            if (!url.isNullOrEmpty() && url != Config.HOME_PAGE_URL) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, url)
                    type = "text/plain"
                }
                activity.startActivity(Intent.createChooser(sendIntent, "Share URL"))
            } else {
                Toast.makeText(activity, "No active page to share", Toast.LENGTH_SHORT).show()
            }
        }

        // 8. Desktop Site Checkbox
        val btnDesktop: View = contentView.findViewById(R.id.btnMenuDesktop)
        val cbDesktop: CheckBox = contentView.findViewById(R.id.cbDesktopSite)
        val isDesktop = config.userAgentString.value?.contains("Windows") == true
        cbDesktop.isChecked = isDesktop

        btnDesktop.setOnClickListener {
            val willBeDesktop = !cbDesktop.isChecked
            cbDesktop.isChecked = willBeDesktop
            val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"
            config.userAgentString.value = if (willBeDesktop) desktopUa else null
            currentTab?.webEngine?.reload()
            dismiss()
            Toast.makeText(activity, if (willBeDesktop) "Desktop mode enabled" else "Mobile mode enabled", Toast.LENGTH_SHORT).show()
        }

        // 9. Settings
        contentView.findViewById<View>(R.id.btnMenuSettings).setOnClickListener {
            dismiss()
            activity.showSettingsDialog()
        }

        // 10. Help & feedback
        contentView.findViewById<View>(R.id.btnMenuHelp).setOnClickListener {
            dismiss()
            AlertDialog.Builder(activity)
                .setTitle("Gothwad TV Browser")
                .setMessage("Modern Fast TV Web Browser with Native Clipboard, File Manager & Notes.\n\nVersion: ${BuildConfig.VERSION_NAME}\nDeveloper: gothwadtech@gmail.com")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    fun show(anchorView: View) {
        val density = activity.resources.displayMetrics.density
        val xOffset = (-200 * density).toInt()
        val yOffset = (4 * density).toInt()
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)

        contentView.findViewById<View>(R.id.btnMenuNewTab).requestFocus()
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }
}
