package com.gothwad.tvbrowser.activity.main.view.home

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.downloads.DownloadsActivity
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.activity.main.showSettingsDialog
import com.gothwad.tvbrowser.activity.main.toggleIncognitoMode
import com.gothwad.tvbrowser.filemanager.FileManagerActivity
import com.gothwad.tvbrowser.notes.NotesActivity
import com.gothwad.tvbrowser.utils.activity
import org.json.JSONArray
import org.json.JSONObject

class NativeHomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val prefs: SharedPreferences = context.getSharedPreferences("native_home_shortcuts_v10", Context.MODE_PRIVATE)
    private val bookmarkItems = mutableListOf<HomeShortcutItem>()
    private var bookmarksAdapter: HomeCardAdapter? = null

    var onNavigateUrl: ((String) -> Unit)? = null

    private lateinit var rvBookmarks: RecyclerView
    private lateinit var svIncognitoHome: ScrollView
    private lateinit var btnExitIncognito: Button
    private lateinit var flNativeHomeRoot: FrameLayout

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_native_home, this, true)
        initViews()
        setupBookmarks()
    }

    private fun initViews() {
        flNativeHomeRoot = findViewById(R.id.llNativeHomeRoot)
        rvBookmarks = findViewById(R.id.rvBookmarks)
        svIncognitoHome = findViewById(R.id.svIncognitoHome)
        btnExitIncognito = findViewById(R.id.btnExitIncognito)
        updateIncognitoState((activity as? MainActivity)?.config?.incognitoMode == true)
    }

    fun updateIncognitoState(isIncognito: Boolean, mainActivity: MainActivity? = activity as? MainActivity) {
        if (isIncognito) {
            rvBookmarks.visibility = View.GONE
            svIncognitoHome.visibility = View.VISIBLE
            flNativeHomeRoot.setBackgroundColor(Color.parseColor("#1F1F1F"))
            btnExitIncognito.setOnClickListener {
                (mainActivity ?: activity as? MainActivity)?.toggleIncognitoMode(andSwitchProcess = true)
            }
        } else {
            rvBookmarks.visibility = View.VISIBLE
            svIncognitoHome.visibility = View.GONE
        }
    }

    private fun populateBookmarkItemsList() {
        bookmarkItems.clear()
        val userBookmarks = loadUserBookmarks()
        val nonAction = userBookmarks.filter { !it.isActionCard && !it.isAddButton }
        val sorted = HomeData.sortShortcutsWithGoogleFirst(nonAction)

        // All shortcuts: Google #1, then alphabetical A-Z
        bookmarkItems.addAll(sorted)

        // Place the combined Action Card (Add + Remove) at the VERY END as the last item
        bookmarkItems.add(
            HomeShortcutItem(
                title = "Manage",
                url = "",
                isActionCard = true
            )
        )
    }

    private fun setupBookmarks() {
        populateBookmarkItemsList()

        // Fixed 4-Column Horizontal Grid with infinite vertical rows
        val gridLayoutManager = GridLayoutManager(context, 4, RecyclerView.VERTICAL, false)
        rvBookmarks.layoutManager = gridLayoutManager
        rvBookmarks.setHasFixedSize(false)
        rvBookmarks.isNestedScrollingEnabled = true

        bookmarksAdapter = HomeCardAdapter(
            items = bookmarkItems,
            onItemClick = { shortcut ->
                if (shortcut.url.isNotEmpty()) {
                    onNavigateUrl?.invoke(shortcut.url)
                }
            },
            onAddClick = {
                showAddBookmarkDialog()
            },
            onRemoveClick = {
                showRemoveSelectionDialog()
            },
            onItemLongClick = { shortcut ->
                if (!shortcut.isActionCard && !shortcut.isAddButton) {
                    showDeleteBookmarkDialog(shortcut)
                    true
                } else {
                    false
                }
            }
        )
        rvBookmarks.adapter = bookmarksAdapter
    }

    private fun reloadShortcuts() {
        populateBookmarkItemsList()
        bookmarksAdapter?.notifyDataSetChanged()
    }

    private fun loadUserBookmarks(): List<HomeShortcutItem> {
        val list = mutableListOf<HomeShortcutItem>()
        val jsonStr = prefs.getString("bookmarks_json", null)
        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val name = obj.optString("name", "Site")
                    val url = obj.optString("url", "")
                    if (url.isNotEmpty()) {
                        list.add(
                            HomeShortcutItem(
                                title = name,
                                url = url,
                                iconDrawableRes = HomeData.getIconForUrlOrTitle(url, name),
                                isUserBookmark = true
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (list.isEmpty()) {
            val defaults = HomeData.getDefaultGoogleBookmarks()
            list.addAll(defaults)
            saveUserBookmarks(list)
        }

        return HomeData.sortShortcutsWithGoogleFirst(list)
    }

    private fun saveUserBookmarks(items: List<HomeShortcutItem>) {
        val array = JSONArray()
        for (item in items) {
            if (!item.isAddButton && !item.isActionCard) {
                val obj = JSONObject()
                obj.put("name", item.title)
                obj.put("url", item.url)
                array.put(obj)
            }
        }
        prefs.edit().putString("bookmarks_json", array.toString()).apply()
    }

    private fun showAddBookmarkDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_bookmark, null)
        val etName: EditText = dialogView.findViewById(R.id.etBookmarkName)
        val etUrl: EditText = dialogView.findViewById(R.id.etBookmarkUrl)

        AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                var name = etName.text.toString().trim()
                var url = etUrl.text.toString().trim()

                if (url.isNotEmpty()) {
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    if (name.isEmpty()) {
                        name = try { java.net.URI(url).host?.replace("www.", "") ?: "Site" } catch (e: Exception) { "Site" }
                    }

                    val newItem = HomeShortcutItem(
                        title = name,
                        url = url,
                        iconDrawableRes = HomeData.getIconForUrlOrTitle(url, name),
                        isUserBookmark = true
                    )

                    val currentList = bookmarkItems.filter { !it.isAddButton && !it.isActionCard }.toMutableList()
                    currentList.add(newItem)
                    val sorted = HomeData.sortShortcutsWithGoogleFirst(currentList)
                    saveUserBookmarks(sorted)
                    reloadShortcuts()

                    Toast.makeText(context, "Shortcut added!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveSelectionDialog() {
        val userShortcuts = bookmarkItems.filter { !it.isActionCard && !it.isAddButton }
        if (userShortcuts.isEmpty()) {
            Toast.makeText(context, "No shortcuts to remove", Toast.LENGTH_SHORT).show()
            return
        }

        val shortcutTitles = userShortcuts.map { "${it.title} (${it.domainText})" }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("🗑️ Select Shortcut to Remove")
            .setItems(shortcutTitles) { _, which ->
                if (which in userShortcuts.indices) {
                    val target = userShortcuts[which]
                    showDeleteBookmarkDialog(target)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteBookmarkDialog(item: HomeShortcutItem) {
        AlertDialog.Builder(context)
            .setTitle("Delete Shortcut")
            .setMessage("Remove '${item.title}' from shortcuts?")
            .setPositiveButton("Delete") { _, _ ->
                val userOnly = bookmarkItems.filter { !it.isActionCard && !it.isAddButton && it != item }
                val sorted = HomeData.sortShortcutsWithGoogleFirst(userOnly)
                saveUserBookmarks(sorted)
                reloadShortcuts()
                Toast.makeText(context, "'${item.title}' deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun scrollToTop() {
        rvBookmarks.smoothScrollToPosition(0)
    }

    fun catchFocus() {
        val mainActivity = activity as? MainActivity
        val isIncognito = mainActivity?.config?.incognitoMode == true
        if (isIncognito && svIncognitoHome.visibility == View.VISIBLE) {
            btnExitIncognito.requestFocus()
            return
        }
        if (hasFocus()) return
        val firstChild = rvBookmarks.layoutManager?.findViewByPosition(0)
        if (firstChild != null) {
            firstChild.requestFocus()
        } else {
            rvBookmarks.post {
                val child = rvBookmarks.layoutManager?.findViewByPosition(0)
                child?.requestFocus() ?: rvBookmarks.requestFocus()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "native_home_shortcuts_v10"
        private const val KEY_BOOKMARKS = "bookmarks_json"

        fun loadUserBookmarks(context: Context): List<HomeShortcutItem> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val list = mutableListOf<HomeShortcutItem>()
            val jsonStr = prefs.getString(KEY_BOOKMARKS, null)
            if (jsonStr != null) {
                try {
                    val array = JSONArray(jsonStr)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val name = obj.optString("name", "Site")
                        val url = obj.optString("url", "")
                        if (url.isNotEmpty()) {
                            list.add(
                                HomeShortcutItem(
                                    title = name,
                                    url = url,
                                    iconDrawableRes = HomeData.getIconForUrlOrTitle(url, name),
                                    isUserBookmark = true
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (list.isEmpty()) {
                val defaults = HomeData.getDefaultGoogleBookmarks()
                list.addAll(defaults)
                saveUserBookmarks(context, list)
            }
            return HomeData.sortShortcutsWithGoogleFirst(list)
        }

        fun saveUserBookmarks(context: Context, items: List<HomeShortcutItem>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            for (item in items) {
                if (!item.isAddButton && !item.isActionCard) {
                    val obj = JSONObject()
                    obj.put("name", item.title)
                    obj.put("url", item.url)
                    array.put(obj)
                }
            }
            prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply()
        }
    }
}
