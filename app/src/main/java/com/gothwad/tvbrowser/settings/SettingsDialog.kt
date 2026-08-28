package com.gothwad.tvbrowser.settings

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.gothwad.tvbrowser.R

class SettingsDialog(context: Context, val model: SettingsModel) :
    Dialog(context, R.style.TvSettingsDrawerDialog),
    DialogInterface.OnDismissListener, VersionSettingsView.Callback {

    private var mainView: MainSettingsView? = null
    private var versionView: VersionSettingsView? = null
    private var shortcutsView: ShortcutsSettingsView? = null

    private lateinit var llSettingsPageCategories: LinearLayout
    private lateinit var llSettingsPageDetail: LinearLayout
    private lateinit var flTabsContent: FrameLayout
    private lateinit var tvSettingsDetailTitle: TextView
    private lateinit var tvSettingsDetailBadge: TextView
    private lateinit var btnSettingsBackToCategories: ImageButton
    private lateinit var ibCloseSettings: ImageButton

    // Page 1 Category Items (17 Granular Sections)
    private lateinit var itemCatDisplayScale: View
    private lateinit var itemCatWebZoom: View
    private lateinit var itemCatThemes: View
    private lateinit var itemCatMediaPlayback: View
    private lateinit var itemCatSearchEngine: View
    private lateinit var itemCatHomePage: View
    private lateinit var itemCatUserAgent: View
    private lateinit var itemCatWebEngine: View
    private lateinit var itemCatAdBlock: View
    private lateinit var itemCatAppLock: View
    private lateinit var itemCatCacheStorage: View
    private lateinit var itemCatQuickTools: View
    private lateinit var itemCatRemoteNav: View
    private lateinit var itemCatCursorPhysics: View
    private lateinit var itemCatKeyboardMouse: View
    private lateinit var itemCatShortcuts: View
    private lateinit var itemCatAbout: View

    private var lastFocusedCategoryItem: View? = null

    init {
        setTitle(R.string.settings)
        setContentView(R.layout.dialog_settings)

        bindViews()
        initChildViews()
        setupListeners()

        setOnDismissListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setGravity(Gravity.END)
            val dm = context.resources.displayMetrics
            // Proportional slim width (26% of screen width) so it looks thin and sleek at all UI scale ratios
            val panelWidth = (dm.widthPixels * 0.26f).toInt()
            setLayout(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT)
            val lp = attributes ?: WindowManager.LayoutParams()
            lp.gravity = Gravity.END
            lp.x = 0
            lp.y = 0
            lp.width = panelWidth
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            attributes = lp

            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.55f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(R.style.SideDrawerAnimation)
        }
        setCanceledOnTouchOutside(true)
    }

    private fun bindViews() {
        llSettingsPageCategories = findViewById(R.id.llSettingsPageCategories)
        llSettingsPageDetail = findViewById(R.id.llSettingsPageDetail)
        flTabsContent = findViewById(R.id.flTabsContent)
        tvSettingsDetailTitle = findViewById(R.id.tvSettingsDetailTitle)
        tvSettingsDetailBadge = findViewById(R.id.tvSettingsDetailBadge)
        btnSettingsBackToCategories = findViewById(R.id.btnSettingsBackToCategories)
        ibCloseSettings = findViewById(R.id.ibCloseSettings)

        itemCatDisplayScale = findViewById(R.id.itemCatDisplayScale)
        itemCatWebZoom = findViewById(R.id.itemCatWebZoom)
        itemCatThemes = findViewById(R.id.itemCatThemes)
        itemCatMediaPlayback = findViewById(R.id.itemCatMediaPlayback)
        itemCatSearchEngine = findViewById(R.id.itemCatSearchEngine)
        itemCatHomePage = findViewById(R.id.itemCatHomePage)
        itemCatUserAgent = findViewById(R.id.itemCatUserAgent)
        itemCatWebEngine = findViewById(R.id.itemCatWebEngine)
        itemCatAdBlock = findViewById(R.id.itemCatAdBlock)
        itemCatAppLock = findViewById(R.id.itemCatAppLock)
        itemCatCacheStorage = findViewById(R.id.itemCatCacheStorage)
        itemCatQuickTools = findViewById(R.id.itemCatQuickTools)
        itemCatRemoteNav = findViewById(R.id.itemCatRemoteNav)
        itemCatCursorPhysics = findViewById(R.id.itemCatCursorPhysics)
        itemCatKeyboardMouse = findViewById(R.id.itemCatKeyboardMouse)
        itemCatShortcuts = findViewById(R.id.itemCatShortcuts)
        itemCatAbout = findViewById(R.id.itemCatAbout)

        findViewById<View>(R.id.vSettingsBackdrop)?.setOnClickListener {
            dismiss()
        }
    }

    private fun initChildViews() {
        mainView = MainSettingsView(context).apply {
            onDismissDialog = { dismiss() }
        }

        versionView = VersionSettingsView(context).apply {
            callback = this@SettingsDialog
        }

        shortcutsView = ShortcutsSettingsView(context)
    }

    private fun setupListeners() {
        ibCloseSettings.setOnClickListener { dismiss() }
        btnSettingsBackToCategories.setOnClickListener { showCategoriesPage() }

        bindCategoryItem(itemCatDisplayScale) {
            openMainCategory(itemCatDisplayScale, "Display & UI Scale", "Display & Layout", SettingsCategory.DISPLAY_SCALE)
        }
        bindCategoryItem(itemCatWebZoom) {
            openMainCategory(itemCatWebZoom, "Web Page Zoom", "Zoom Controls", SettingsCategory.WEB_ZOOM)
        }
        bindCategoryItem(itemCatThemes) {
            openMainCategory(itemCatThemes, "Themes & Appearance", "Appearance & Dark Mode", SettingsCategory.THEMES)
        }
        bindCategoryItem(itemCatMediaPlayback) {
            openMainCategory(itemCatMediaPlayback, "Media & Playback", "Video & Audio", SettingsCategory.MEDIA_PLAYBACK)
        }
        bindCategoryItem(itemCatSearchEngine) {
            openMainCategory(itemCatSearchEngine, "Search Engine", "Default Engine", SettingsCategory.SEARCH_ENGINE)
        }
        bindCategoryItem(itemCatHomePage) {
            openMainCategory(itemCatHomePage, "Home Page & Startup", "Startup & Bookmarks", SettingsCategory.HOME_PAGE)
        }
        bindCategoryItem(itemCatUserAgent) {
            openMainCategory(itemCatUserAgent, "User Agent & Identity", "Desktop / Mobile Mode", SettingsCategory.USER_AGENT)
        }
        bindCategoryItem(itemCatWebEngine) {
            openMainCategory(itemCatWebEngine, "Web Engine & Rendering", "Browser Core", SettingsCategory.WEB_ENGINE)
        }
        bindCategoryItem(itemCatAdBlock) {
            openMainCategory(itemCatAdBlock, "Ad & Tracker Blocker", "Filters & Rules", SettingsCategory.AD_BLOCKER)
        }
        bindCategoryItem(itemCatAppLock) {
            openMainCategory(itemCatAppLock, "App Lock & PIN Security", "Master Passcode", SettingsCategory.APP_LOCK)
        }
        bindCategoryItem(itemCatCacheStorage) {
            openMainCategory(itemCatCacheStorage, "Cache & Storage", "Data & History", SettingsCategory.CACHE_STORAGE)
        }
        bindCategoryItem(itemCatQuickTools) {
            openMainCategory(itemCatQuickTools, "Quick Tools & Utilities", "Toolbox & Actions", SettingsCategory.QUICK_TOOLS)
        }
        bindCategoryItem(itemCatRemoteNav) {
            openMainCategory(itemCatRemoteNav, "Remote & Joystick", "Navigation Controls", SettingsCategory.REMOTE_NAV)
        }
        bindCategoryItem(itemCatCursorPhysics) {
            openMainCategory(itemCatCursorPhysics, "Virtual Cursor & Physics", "Pointer Controls", SettingsCategory.CURSOR_PHYSICS)
        }
        bindCategoryItem(itemCatKeyboardMouse) {
            openMainCategory(itemCatKeyboardMouse, "Keyboard & Mouse", "Hardware Devices", SettingsCategory.KEYBOARD_MOUSE)
        }
        bindCategoryItem(itemCatShortcuts) {
            openShortcuts(itemCatShortcuts)
        }
        bindCategoryItem(itemCatAbout) {
            openAbout(itemCatAbout)
        }
    }

    private fun bindCategoryItem(view: View, action: () -> Unit) {
        view.setOnClickListener { action() }
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        action()
                        true
                    }
                    else -> false
                }
            } else false
        }
    }

    private fun openMainCategory(
        clickedItem: View,
        title: String,
        badge: String,
        category: SettingsCategory
    ) {
        lastFocusedCategoryItem = clickedItem
        tvSettingsDetailTitle.text = title
        tvSettingsDetailBadge.text = badge

        val mv = mainView ?: return
        flTabsContent.removeAllViews()
        flTabsContent.addView(mv)
        mv.showCategory(category)

        showDetailPage()
    }

    private fun openShortcuts(clickedItem: View) {
        lastFocusedCategoryItem = clickedItem
        tvSettingsDetailTitle.text = context.getString(R.string.tab_shortcuts)
        tvSettingsDetailBadge.text = "Navigation & Keys"

        val sv = shortcutsView ?: return
        flTabsContent.removeAllViews()
        flTabsContent.addView(sv)

        showDetailPage()
    }

    private fun openAbout(clickedItem: View) {
        lastFocusedCategoryItem = clickedItem
        tvSettingsDetailTitle.text = "About & Version"
        tvSettingsDetailBadge.text = "Build Information"

        val vv = versionView ?: return
        flTabsContent.removeAllViews()
        flTabsContent.addView(vv)

        showDetailPage()
    }

    private fun showDetailPage() {
        llSettingsPageCategories.visibility = View.GONE
        llSettingsPageDetail.visibility = View.VISIBLE
        btnSettingsBackToCategories.post {
            btnSettingsBackToCategories.requestFocus()
        }
    }

    fun showCategoriesPage() {
        mainView?.save()
        llSettingsPageDetail.visibility = View.GONE
        llSettingsPageCategories.visibility = View.VISIBLE
        val focusTarget = lastFocusedCategoryItem ?: itemCatDisplayScale
        focusTarget.post {
            focusTarget.requestFocus()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_BUTTON_B -> {
                    if (llSettingsPageDetail.visibility == View.VISIBLE) {
                        showCategoriesPage()
                        return true
                    } else {
                        dismiss()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDismiss(dialog: DialogInterface?) {
        mainView?.save()
    }

    override fun onNeedToCloseSettings() {
        dismiss()
    }
}
