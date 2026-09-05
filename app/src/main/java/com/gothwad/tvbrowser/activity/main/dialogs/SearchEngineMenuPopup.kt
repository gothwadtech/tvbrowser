package com.gothwad.tvbrowser.activity.main.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.activity.main.showFavoritesDialog
import com.gothwad.tvbrowser.activity.main.showHistoryActivity
import com.gothwad.tvbrowser.activity.main.showTabsRowDialog

class SearchEngineMenuPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val contentView: View
    private val popupWidth: Int

    init {
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_search_engine_menu, null)
        popupWidth = (250 * activity.resources.displayMetrics.density).toInt()
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
            setOnDismissListener {
                // Return focus to URL edit text or home if needed
            }
        }

        setupViews()
    }

    private fun bindMenuItem(view: View, action: () -> Unit) {
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
        val currentEngine = config.guessSearchEngineName()

        // Checkmark visibility based on current selection
        val ivCheckGoogle: ImageView = contentView.findViewById(R.id.ivCheckGoogle)
        val ivCheckBing: ImageView = contentView.findViewById(R.id.ivCheckBing)
        val ivCheckDuckDuckGo: ImageView = contentView.findViewById(R.id.ivCheckDuckDuckGo)
        val ivCheckPerplexity: ImageView = contentView.findViewById(R.id.ivCheckPerplexity)
        val ivCheckWikipedia: ImageView = contentView.findViewById(R.id.ivCheckWikipedia)

        ivCheckGoogle.visibility = if (currentEngine == "google") View.VISIBLE else View.GONE
        ivCheckBing.visibility = if (currentEngine == "bing") View.VISIBLE else View.GONE
        ivCheckDuckDuckGo.visibility = if (currentEngine == "ddg") View.VISIBLE else View.GONE
        ivCheckPerplexity.visibility = if (currentEngine == "perplexity") View.VISIBLE else View.GONE
        ivCheckWikipedia.visibility = if (currentEngine == "wikipedia") View.VISIBLE else View.GONE

        // Search engine items
        bindEngineItem(
            view = contentView.findViewById(R.id.llEngineGoogle),
            engineName = "Google",
            urlPattern = "https://www.google.com/search?q=[query]"
        )

        bindEngineItem(
            view = contentView.findViewById(R.id.llEngineBing),
            engineName = "Bing",
            urlPattern = "https://www.bing.com/search?q=[query]"
        )

        bindEngineItem(
            view = contentView.findViewById(R.id.llEngineDuckDuckGo),
            engineName = "DuckDuckGo",
            urlPattern = "https://duckduckgo.com/?q=[query]"
        )

        bindEngineItem(
            view = contentView.findViewById(R.id.llEnginePerplexity),
            engineName = "Perplexity",
            urlPattern = "https://www.perplexity.ai/search?q=[query]"
        )

        bindEngineItem(
            view = contentView.findViewById(R.id.llEngineWikipedia),
            engineName = "Wikipedia (en)",
            urlPattern = "https://en.wikipedia.org/wiki/Special:Search?search=[query]"
        )

        // Shortcut 1: Bookmarks
        bindMenuItem(contentView.findViewById(R.id.llShortcutBookmarks)) {
            activity.showFavoritesDialog()
        }

        // Shortcut 2: Tabs
        bindMenuItem(contentView.findViewById(R.id.llShortcutTabs)) {
            activity.showTabsRowDialog()
        }

        // Shortcut 3: History
        bindMenuItem(contentView.findViewById(R.id.llShortcutHistory)) {
            activity.showHistoryActivity()
        }

        // Shortcut 4: Actions (opens ChromeMenuPopup)
        bindMenuItem(contentView.findViewById(R.id.llShortcutActions)) {
            val anchor = activity.findViewById<View>(R.id.ivLockIcon) ?: activity.vb.vActionBar
            ChromeMenuPopup(activity).show(anchor)
        }

        // Bottom: Search Settings
        bindMenuItem(contentView.findViewById(R.id.llSearchSettings)) {
            SearchEngineConfigDialogFactory.show(
                context = activity,
                settings = activity.settingsModel,
                cancellable = true,
                callback = object : SearchEngineConfigDialogFactory.Callback {
                    override fun onDone(url: String) {
                        activity.vb.vActionBar.updateAddressBarIcon(activity.vb.vActionBar.getUrlEditText().let {
                            (it as? EditText)?.text?.toString() ?: ""
                        })
                    }
                }
            )
        }
    }

    private fun bindEngineItem(view: View, engineName: String, urlPattern: String) {
        bindMenuItem(view) {
            val etUrl = activity.vb.vActionBar.getUrlEditText() as? EditText
            val enteredQuery = etUrl?.text?.toString()?.trim() ?: ""

            // Update default search engine in Config
            activity.config.searchEngineURL.value = urlPattern
            activity.settingsModel.setSearchEngineURL(urlPattern)

            // Update search bar icon immediately
            activity.vb.vActionBar.updateAddressBarIcon(enteredQuery)

            if (enteredQuery.isNotEmpty()) {
                // If user already typed query, execute search with this engine!
                activity.search(enteredQuery)
            } else {
                Toast.makeText(activity, "$engineName selected", Toast.LENGTH_SHORT).show()
                etUrl?.requestFocus()
            }
        }
    }

    fun show(anchorView: View) {
        val density = activity.resources.displayMetrics.density
        val xOffset = (-2 * density).toInt()
        val yOffset = (4 * density).toInt()

        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)

        contentView.post {
            // Find which view to focus first
            val currentEngine = activity.config.guessSearchEngineName()
            val viewToFocus = when (currentEngine) {
                "google" -> contentView.findViewById<View>(R.id.llEngineGoogle)
                "bing" -> contentView.findViewById<View>(R.id.llEngineBing)
                "ddg" -> contentView.findViewById<View>(R.id.llEngineDuckDuckGo)
                "perplexity" -> contentView.findViewById<View>(R.id.llEnginePerplexity)
                "wikipedia" -> contentView.findViewById<View>(R.id.llEngineWikipedia)
                else -> contentView.findViewById<View>(R.id.llEngineGoogle)
            }
            viewToFocus?.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }
}
