package com.gothwad.tvbrowser.activity.main.dialogs

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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

class BrowserSidebarPopup(private val activity: MainActivity) : Dialog(activity, R.style.TvSideDrawerDialog) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_browser_side_menu)

        window?.apply {
            setGravity(Gravity.START)
            val dm = activity.resources.displayMetrics
            // Proportional slim width (25% of screen width) so it looks thin and sleek at all UI scale ratios
            val panelWidth = (dm.widthPixels * 0.25f).toInt()
            setLayout(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT)
            val lp = attributes ?: WindowManager.LayoutParams()
            lp.gravity = Gravity.START
            lp.x = 0
            lp.y = 0
            lp.width = panelWidth
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            attributes = lp

            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.55f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(R.style.SideDrawerLeftAnimation)
        }
        setCanceledOnTouchOutside(true)

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
        val currentTab = activity.tabsModel.currentTab.value

        // Backdrop click dismisses drawer
        findViewById<View>(R.id.vSideMenuBackdrop)?.setOnClickListener {
            dismiss()
        }

        // Circular Back Button
        val btnBack: ImageButton? = findViewById(R.id.btnSideMenuBack)
        btnBack?.setOnClickListener {
            dismiss()
        }
        btnBack?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE -> {
                        dismiss()
                        true
                    }
                    else -> false
                }
            } else false
        }

        // 1. Privacy Protection / App Lock
        findViewById<View>(R.id.btnSidePrivacy)?.let { btn ->
            bindItem(btn) {
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
        }

        // 2. Incognito Mode
        val tvIncognito = findViewById<TextView>(R.id.tvSideIncognitoTitle)
        if (config.incognitoMode) {
            tvIncognito?.text = "Exit incognito"
        } else {
            tvIncognito?.text = "Start incognito"
        }
        findViewById<View>(R.id.btnSideIncognito)?.let { btn ->
            bindItem(btn) {
                activity.toggleIncognitoMode(andSwitchProcess = true)
            }
        }

        // 3. Share
        findViewById<View>(R.id.btnSideShare)?.let { btn ->
            bindItem(btn) {
                val url = currentTab?.url
                if (!url.isNullOrEmpty() && url != Config.HOME_PAGE_URL) {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, url)
                        putExtra(Intent.EXTRA_SUBJECT, currentTab.title ?: "Link")
                        type = "text/plain"
                    }
                    activity.startActivity(Intent.createChooser(sendIntent, "Share URL"))
                } else {
                    Toast.makeText(activity, "No active page to share", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 4. Bookmarks
        findViewById<View>(R.id.btnSideBookmarks)?.let { btn ->
            bindItem(btn) {
                activity.showFavoritesDialog()
            }
        }

        // 5. History
        findViewById<View>(R.id.btnSideHistory)?.let { btn ->
            bindItem(btn) {
                activity.showHistoryActivity()
            }
        }

        // 6. Downloads
        findViewById<View>(R.id.btnSideDownloads)?.let { btn ->
            bindItem(btn) {
                activity.startActivity(Intent(activity, DownloadsActivity::class.java))
            }
        }

        // 7. File Manager
        findViewById<View>(R.id.btnSideFileManager)?.let { btn ->
            bindItem(btn) {
                activity.startActivity(Intent(activity, FileManagerActivity::class.java))
            }
        }

        // 8. Notes
        findViewById<View>(R.id.btnSideNotes)?.let { btn ->
            bindItem(btn) {
                activity.startActivity(Intent(activity, NotesActivity::class.java))
            }
        }

        // 9. Native Clipboard
        findViewById<View>(R.id.btnSideClipboard)?.let { btn ->
            bindItem(btn) {
                activity.showClipboardActivity()
            }
        }

        // 10. Desktop View Mode
        val ivDesktopCheck = findViewById<ImageView>(R.id.ivSideDesktopCheck)
        val isDesktop = config.desktopMode.value || config.userAgentString.value?.contains("Windows") == true
        ivDesktopCheck?.setImageResource(
            if (isDesktop) R.drawable.ic_check_box_checked else R.drawable.ic_check_box_outline
        )
        findViewById<View>(R.id.btnSideDesktopMode)?.let { btn ->
            bindItem(btn) {
                val willBeDesktop = !isDesktop
                config.desktopMode.value = willBeDesktop
                config.userAgentString.value = if (willBeDesktop) Config.DESKTOP_UA else null
                for (tab in activity.tabsModel.tabsStates) {
                    tab.webEngine.userAgentString = if (willBeDesktop) Config.DESKTOP_UA else null
                }
                currentTab?.webEngine?.reload()
                Toast.makeText(activity, if (willBeDesktop) "Desktop mode enabled" else "Mobile mode enabled", Toast.LENGTH_SHORT).show()
            }
        }

        // 11. Full Settings
        findViewById<View>(R.id.btnSideSettings)?.let { btn ->
            bindItem(btn) {
                activity.showSettingsDialog()
            }
        }

        // Initial focus request on first navigation item
        findViewById<View>(R.id.btnSidePrivacy)?.post {
            findViewById<View>(R.id.btnSidePrivacy)?.requestFocus()
        }
    }

    fun show(anchorView: View? = null) {
        show()
    }
}
