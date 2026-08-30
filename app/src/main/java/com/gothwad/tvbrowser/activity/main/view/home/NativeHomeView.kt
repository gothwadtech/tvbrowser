package com.gothwad.tvbrowser.activity.main.view.home

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.activity.main.toggleIncognitoMode
import com.gothwad.tvbrowser.utils.activity
import org.json.JSONArray
import org.json.JSONObject

class NativeHomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val prefs: SharedPreferences = context.getSharedPreferences("native_home_shortcuts_v11", Context.MODE_PRIVATE)
    private val bookmarkItems = mutableListOf<HomeShortcutItem>()
    private var bookmarksAdapter: HomeCardAdapter? = null

    var onNavigateUrl: ((String) -> Unit)? = null

    private lateinit var rvBookmarks: RecyclerView
    private lateinit var svIncognitoHome: ScrollView
    private lateinit var btnExitIncognito: Button
    private lateinit var flNativeHomeRoot: FrameLayout

    private val tickerHandler = Handler(Looper.getMainLooper())
    private val tickerRunnable = object : Runnable {
        override fun run() {
            updateDashboardCardsLive()
            tickerHandler.postDelayed(this, 1000L)
        }
    }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_native_home, this, true)
        initViews()
        setupBookmarks()
        updateIncognitoState((activity as? MainActivity)?.config?.incognitoMode == true)
    }

    private fun initViews() {
        flNativeHomeRoot = findViewById(R.id.llNativeHomeRoot)
        rvBookmarks = findViewById(R.id.rvBookmarks)
        svIncognitoHome = findViewById(R.id.svIncognitoHome)
        btnExitIncognito = findViewById(R.id.btnExitIncognito)
    }

    fun updateIncognitoState(isIncognito: Boolean, mainActivity: MainActivity? = activity as? MainActivity) {
        if (isIncognito) {
            rvBookmarks.visibility = View.GONE
            svIncognitoHome.visibility = View.VISIBLE
            flNativeHomeRoot.setBackgroundColor(Color.parseColor("#1F1F1F"))
            btnExitIncognito.setOnClickListener {
                (mainActivity ?: activity as? MainActivity)?.toggleIncognitoMode(andSwitchProcess = true)
            }
            stopDashboardTicker()
        } else {
            rvBookmarks.visibility = View.VISIBLE
            svIncognitoHome.visibility = View.GONE
            startDashboardTicker()
        }
    }

    private fun createDashboardCards(): List<HomeShortcutItem> {
        val netInfo = SystemMonitorHelper.getNetworkInfo(context)
        val ramStats = SystemMonitorHelper.getRamStats(context)
        val storageStats = SystemMonitorHelper.getStorageStats()

        val ramUsedStr = SystemMonitorHelper.formatBytes(ramStats.usedBytes)
        val ramTotalStr = SystemMonitorHelper.formatBytes(ramStats.totalBytes)

        val storageFreeStr = SystemMonitorHelper.formatBytes(storageStats.freeBytes)
        val storageUsedStr = SystemMonitorHelper.formatBytes(storageStats.usedBytes)
        val storageTotalStr = SystemMonitorHelper.formatBytes(storageStats.totalBytes)

        return listOf(
            HomeShortcutItem(
                title = SystemMonitorHelper.getFormattedTime(),
                subtitleText = SystemMonitorHelper.getTimeSubtitle(),
                iconDrawableRes = R.drawable.ic_stat_time,
                isDashboardCard = true,
                dashboardType = "TIME"
            ),
            HomeShortcutItem(
                title = SystemMonitorHelper.getFormattedDate(),
                subtitleText = SystemMonitorHelper.getCalendarSubtitle(),
                iconDrawableRes = R.drawable.ic_stat_calendar,
                isDashboardCard = true,
                dashboardType = "CALENDAR"
            ),
            HomeShortcutItem(
                title = netInfo.first,
                subtitleText = netInfo.second,
                iconDrawableRes = R.drawable.ic_stat_network,
                isDashboardCard = true,
                dashboardType = "NETWORK"
            ),
            HomeShortcutItem(
                title = "RAM: $ramUsedStr / $ramTotalStr (${ramStats.usedPercent}%)",
                subtitleText = "⚡ Click to Boost RAM",
                iconDrawableRes = R.drawable.ic_stat_ram,
                isDashboardCard = true,
                dashboardType = "RAM"
            ),
            HomeShortcutItem(
                title = "Disk: $storageFreeStr Free",
                subtitleText = "Used: $storageUsedStr / $storageTotalStr (${storageStats.usedPercent}%)",
                iconDrawableRes = R.drawable.ic_stat_storage,
                isDashboardCard = true,
                dashboardType = "STORAGE"
            )
        )
    }

    private fun populateBookmarkItemsList() {
        bookmarkItems.clear()

        // 0. Gothwad Browser Category (Dashboard Status Cards)
        bookmarkItems.add(
            HomeShortcutItem(
                title = "Gothwad Browser",
                isHeader = true
            )
        )
        bookmarkItems.addAll(createDashboardCards())

        // 1. My Shortcuts Category (Manual user-added shortcuts + Add/Remove controls)
        val userManualBookmarks = loadUserBookmarks()
        bookmarkItems.add(
            HomeShortcutItem(
                title = "My Shortcuts",
                isHeader = true
            )
        )

        // Add any user-added custom shortcuts
        for (item in userManualBookmarks) {
            bookmarkItems.add(item)
        }

        // Add Shortcut Card
        bookmarkItems.add(
            HomeShortcutItem(
                title = "Add Shortcut",
                url = "",
                subtitleText = "New shortcut",
                iconDrawableRes = R.drawable.ic_add,
                isAddButton = true
            )
        )

        // Remove Shortcut Card
        bookmarkItems.add(
            HomeShortcutItem(
                title = "Remove Shortcut",
                url = "",
                subtitleText = "Delete shortcut",
                iconDrawableRes = R.drawable.ic_delete,
                isDeleteButton = true
            )
        )

        // 2. Google Services Category
        bookmarkItems.add(
            HomeShortcutItem(
                title = "Google Services",
                isHeader = true
            )
        )
        bookmarkItems.addAll(HomeData.getGoogleShortcuts())

        // 3. Social Media Category
        bookmarkItems.add(
            HomeShortcutItem(
                title = "Social Media",
                isHeader = true
            )
        )
        bookmarkItems.addAll(HomeData.getSocialMediaShortcuts())

        // 4. Entertainment & Streaming Category
        bookmarkItems.add(
            HomeShortcutItem(
                title = "Entertainment & Streaming",
                isHeader = true
            )
        )
        bookmarkItems.addAll(HomeData.getEntertainmentShortcuts())

        // 5. Utilities & Tools Category
        bookmarkItems.add(
            HomeShortcutItem(
                title = "Utilities & Tools",
                isHeader = true
            )
        )
        bookmarkItems.addAll(HomeData.getUtilityShortcuts())
    }

    private fun startDashboardTicker() {
        tickerHandler.removeCallbacks(tickerRunnable)
        tickerHandler.postDelayed(tickerRunnable, 1000L)
    }

    private fun stopDashboardTicker() {
        tickerHandler.removeCallbacks(tickerRunnable)
    }

    private fun updateDashboardCardsLive() {
        try {
            if (!::rvBookmarks.isInitialized || rvBookmarks.isComputingLayout) return
            if (bookmarksAdapter == null) return
            if (bookmarkItems.size < 6) return
            if (!bookmarkItems[0].isHeader || bookmarkItems[0].title != "Gothwad Browser") return

            SystemMonitorHelper.updateNetworkSpeed()
            val netInfo = SystemMonitorHelper.getNetworkInfo(context)
            val ramStats = SystemMonitorHelper.getRamStats(context)
            val storageStats = SystemMonitorHelper.getStorageStats()

            val ramUsedStr = SystemMonitorHelper.formatBytes(ramStats.usedBytes)
            val ramTotalStr = SystemMonitorHelper.formatBytes(ramStats.totalBytes)

            val storageFreeStr = SystemMonitorHelper.formatBytes(storageStats.freeBytes)
            val storageUsedStr = SystemMonitorHelper.formatBytes(storageStats.usedBytes)
            val storageTotalStr = SystemMonitorHelper.formatBytes(storageStats.totalBytes)

            // Pos 1: TIME
            bookmarkItems[1] = bookmarkItems[1].copy(
                title = SystemMonitorHelper.getFormattedTime(),
                subtitleText = SystemMonitorHelper.getTimeSubtitle()
            )
            // Pos 2: CALENDAR
            bookmarkItems[2] = bookmarkItems[2].copy(
                title = SystemMonitorHelper.getFormattedDate(),
                subtitleText = SystemMonitorHelper.getCalendarSubtitle()
            )
            // Pos 3: NETWORK
            bookmarkItems[3] = bookmarkItems[3].copy(
                title = netInfo.first,
                subtitleText = netInfo.second
            )
            // Pos 4: RAM
            bookmarkItems[4] = bookmarkItems[4].copy(
                title = "RAM: $ramUsedStr / $ramTotalStr (${ramStats.usedPercent}%)",
                subtitleText = "⚡ Click to Boost RAM"
            )
            // Pos 5: STORAGE
            bookmarkItems[5] = bookmarkItems[5].copy(
                title = "Disk: $storageFreeStr Free",
                subtitleText = "Used: $storageUsedStr / $storageTotalStr (${storageStats.usedPercent}%)"
            )

            if (!rvBookmarks.isComputingLayout) {
                bookmarksAdapter?.notifyItemRangeChanged(1, 5, "STATS_PAYLOAD")
            }
        } catch (e: Throwable) {
            // Ignore any transient update errors
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startDashboardTicker()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopDashboardTicker()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            startDashboardTicker()
        } else {
            stopDashboardTicker()
        }
    }

    private fun handleDashboardCardClick(item: HomeShortcutItem) {
        when (item.dashboardType) {
            "TIME" -> showTimeInfoDialog()
            "CALENDAR" -> showCalendarInfoDialog()
            "NETWORK" -> showNetworkInfoDialog()
            "RAM" -> showRamBoostDialog()
            "STORAGE" -> showStorageInfoDialog()
        }
    }

    private fun showTimeInfoDialog() {
        val cal = java.util.Calendar.getInstance()
        val timeFormatted = SystemMonitorHelper.getFormattedTime()
        val timeZone = cal.timeZone.displayName ?: "Local Time"
        val timeZoneId = cal.timeZone.id

        AlertDialog.Builder(context)
            .setTitle("🕒 System Time")
            .setMessage("Current Time: $timeFormatted\nTimezone: $timeZone ($timeZoneId)\nLive clock updates automatically.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCalendarInfoDialog() {
        val dateFormatted = SystemMonitorHelper.getFormattedDate()
        val subtitle = SystemMonitorHelper.getCalendarSubtitle()
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val isLeapYear = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

        AlertDialog.Builder(context)
            .setTitle("📅 System Calendar")
            .setMessage("Date: $dateFormatted\nProgress: $subtitle\nYear: $year (Leap year: ${if (isLeapYear) "Yes" else "No"})")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showNetworkInfoDialog() {
        val netInfo = SystemMonitorHelper.getNetworkInfo(context)
        val downSpeed = SystemMonitorHelper.currentDownloadSpeedStr
        val upSpeed = SystemMonitorHelper.currentUploadSpeedStr

        val totalRx = SystemMonitorHelper.formatBytes(android.net.TrafficStats.getTotalRxBytes())
        val totalTx = SystemMonitorHelper.formatBytes(android.net.TrafficStats.getTotalTxBytes())

        AlertDialog.Builder(context)
            .setTitle("🌐 Network & Speed Monitor")
            .setMessage("Status: ${netInfo.first}\n\nReal-Time Speed:\n• Download: $downSpeed\n• Upload: $upSpeed\n\nTotal Session Traffic:\n• Total Received: $totalRx\n• Total Sent: $totalTx\n\n(Monitored directly via device network stats without consuming internet)")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showRamBoostDialog() {
        val freed = SystemMonitorHelper.performRamBoost(context)
        val ramStats = SystemMonitorHelper.getRamStats(context)
        val used = SystemMonitorHelper.formatBytes(ramStats.usedBytes)
        val total = SystemMonitorHelper.formatBytes(ramStats.totalBytes)
        val avail = SystemMonitorHelper.formatBytes(ramStats.availBytes)

        updateDashboardCardsLive()

        val freedText = if (freed > 0) "${SystemMonitorHelper.formatBytes(freed)} freed!" else "Memory already optimized!"
        Toast.makeText(context, "⚡ RAM Boosted: $freedText", Toast.LENGTH_SHORT).show()

        AlertDialog.Builder(context)
            .setTitle("⚡ RAM & Performance Status")
            .setMessage("RAM Optimizer Result: $freedText\n\n• Used RAM: $used (${ramStats.usedPercent}%)\n• Available Free RAM: $avail\n• Total RAM: $total\n\nBrowser cache and memory trimmed safely.")
            .setPositiveButton("OK", null)
            .setNeutralButton("Boost Again") { _, _ ->
                showRamBoostDialog()
            }
            .show()
    }

    private fun showStorageInfoDialog() {
        val storage = SystemMonitorHelper.getStorageStats()
        val total = SystemMonitorHelper.formatBytes(storage.totalBytes)
        val free = SystemMonitorHelper.formatBytes(storage.freeBytes)
        val used = SystemMonitorHelper.formatBytes(storage.usedBytes)
        val cores = Runtime.getRuntime().availableProcessors()

        AlertDialog.Builder(context)
            .setTitle("💾 Storage & Device Info")
            .setMessage("Internal Storage:\n• Free: $free\n• Used: $used (${storage.usedPercent}%)\n• Total: $total\n\nDevice & CPU Info:\n• Device: ${Build.MANUFACTURER} ${Build.MODEL}\n• Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n• CPU Cores: $cores Cores")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupBookmarks() {
        populateBookmarkItemsList()

        // 5-Column Grid with full-span Section Headers
        val gridLayoutManager = GridLayoutManager(context, 5, RecyclerView.VERTICAL, false)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (position in bookmarkItems.indices && bookmarkItems[position].isHeader) 5 else 1
            }
        }

        rvBookmarks.layoutManager = gridLayoutManager
        rvBookmarks.setHasFixedSize(false)
        rvBookmarks.isNestedScrollingEnabled = true

        bookmarksAdapter = HomeCardAdapter(
            items = bookmarkItems,
            onItemClick = { shortcut ->
                if (shortcut.isDashboardCard) {
                    handleDashboardCardClick(shortcut)
                } else if (shortcut.url.isNotEmpty()) {
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
                if (shortcut.isUserBookmark) {
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
        bookmarksAdapter?.updateItems(ArrayList(bookmarkItems))
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
        return list
    }

    private fun saveUserBookmarks(items: List<HomeShortcutItem>) {
        val array = JSONArray()
        for (item in items) {
            if (!item.isAddButton && !item.isDeleteButton && !item.isActionCard && !item.isHeader && item.isUserBookmark) {
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

                    val currentList = loadUserBookmarks().toMutableList()
                    currentList.add(newItem)
                    saveUserBookmarks(currentList)
                    reloadShortcuts()

                    Toast.makeText(context, "Shortcut '$name' added!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveSelectionDialog() {
        val userShortcuts = loadUserBookmarks()
        if (userShortcuts.isEmpty()) {
            Toast.makeText(context, "No manual shortcuts to remove", Toast.LENGTH_SHORT).show()
            return
        }

        val shortcutTitles = userShortcuts.map { "${it.title} (${it.domainText})" }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Select Shortcut to Remove")
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
            .setMessage("Remove '${item.title}' from My Shortcuts?")
            .setPositiveButton("Delete") { _, _ ->
                val userOnly = loadUserBookmarks().filter { it.url != item.url || it.title != item.title }
                saveUserBookmarks(userOnly)
                reloadShortcuts()
                Toast.makeText(context, "'${item.title}' deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun scrollToTop() {
        rvBookmarks.smoothScrollToPosition(0)
    }

    fun getFocusedShortcutPosition(): Int {
        val focusedChild = rvBookmarks.findFocus() ?: return -1
        val itemView = rvBookmarks.findContainingItemView(focusedChild) ?: return -1
        return rvBookmarks.getChildAdapterPosition(itemView)
    }

    fun getFocusedShortcutColumn(): Int {
        val pos = getFocusedShortcutPosition()
        if (pos < 0) return 0
        // Find column within current row (accounting for section headers which span 5)
        var col = 0
        for (i in 0..pos) {
            if (i >= bookmarkItems.size) break
            if (bookmarkItems[i].isHeader) {
                col = 0
            } else {
                if (i == pos) return col % 5
                col = (col + 1) % 5
            }
        }
        return col % 5
    }

    fun navigateFocus(keyCode: Int): Boolean {
        val mainActivity = activity as? MainActivity
        val isIncognito = mainActivity?.config?.incognitoMode == true
        if (isIncognito && svIncognitoHome.visibility == View.VISIBLE) {
            btnExitIncognito.requestFocus()
            return true
        }

        val currentPos = getFocusedShortcutPosition()
        if (currentPos < 0) {
            catchFocus()
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // Find previous focusable item (skip section headers)
                var prevPos = currentPos - 1
                while (prevPos >= 0 && prevPos < bookmarkItems.size && bookmarkItems[prevPos].isHeader) {
                    prevPos--
                }
                if (prevPos >= 0 && prevPos < bookmarkItems.size) {
                    focusPosition(prevPos)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Find next focusable item (skip section headers)
                var nextPos = currentPos + 1
                while (nextPos < bookmarkItems.size && bookmarkItems[nextPos].isHeader) {
                    nextPos++
                }
                if (nextPos < bookmarkItems.size) {
                    focusPosition(nextPos)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                // Look for item 5 columns back in the grid layout (1 row up)
                var targetPos = currentPos - 5
                while (targetPos >= 0 && bookmarkItems[targetPos].isHeader) {
                    targetPos--
                }
                if (targetPos >= 0 && !bookmarkItems[targetPos].isHeader) {
                    focusPosition(targetPos)
                    return true
                }
                // No item 1 row above -> return false to transition focus to toolbar header
                return false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Move 1 row down (5 items forward in grid), skipping headers
                var targetPos = currentPos + 5
                if (targetPos >= bookmarkItems.size) {
                    targetPos = bookmarkItems.size - 1
                }
                while (targetPos < bookmarkItems.size && bookmarkItems[targetPos].isHeader) {
                    targetPos++
                }
                if (targetPos < bookmarkItems.size && !bookmarkItems[targetPos].isHeader && targetPos != currentPos) {
                    focusPosition(targetPos)
                    return true
                } else if (currentPos < bookmarkItems.size - 1) {
                    var nextPos = currentPos + 1
                    while (nextPos < bookmarkItems.size && bookmarkItems[nextPos].isHeader) {
                        nextPos++
                    }
                    if (nextPos < bookmarkItems.size && !bookmarkItems[nextPos].isHeader) {
                        focusPosition(nextPos)
                        return true
                    }
                }
                return true
            }
        }
        return false
    }

    fun focusPosition(pos: Int) {
        if (pos !in bookmarkItems.indices) return
        if (bookmarkItems[pos].isHeader) return
        rvBookmarks.smoothScrollToPosition(pos)
        val view = rvBookmarks.layoutManager?.findViewByPosition(pos)
        if (view != null) {
            view.requestFocus()
        } else {
            rvBookmarks.postDelayed({
                rvBookmarks.layoutManager?.findViewByPosition(pos)?.requestFocus()
            }, 50)
        }
    }

    fun focusShortcutAtColumn(col: Int) {
        val mainActivity = activity as? MainActivity
        val isIncognito = mainActivity?.config?.incognitoMode == true
        if (isIncognito && svIncognitoHome.visibility == View.VISIBLE) {
            btnExitIncognito.requestFocus()
            return
        }
        val count = bookmarkItems.size
        if (count <= 1) return
        var targetPos = (1 + col).coerceIn(1, count - 1)
        if (targetPos < bookmarkItems.size && bookmarkItems[targetPos].isHeader) {
            targetPos = if (targetPos + 1 < bookmarkItems.size) targetPos + 1 else targetPos - 1
        }
        targetPos = targetPos.coerceIn(1, count - 1)
        focusPosition(targetPos)
    }

    fun catchFocus() {
        val mainActivity = activity as? MainActivity
        val isIncognito = mainActivity?.config?.incognitoMode == true
        if (isIncognito && svIncognitoHome.visibility == View.VISIBLE) {
            btnExitIncognito.requestFocus()
            return
        }
        if (hasFocus()) return
        // First focusable shortcut is at position 1 (pos 0 is header)
        val firstChild = rvBookmarks.layoutManager?.findViewByPosition(1) ?: rvBookmarks.layoutManager?.findViewByPosition(0)
        if (firstChild != null) {
            firstChild.requestFocus()
        } else {
            rvBookmarks.post {
                val child = rvBookmarks.layoutManager?.findViewByPosition(1) ?: rvBookmarks.layoutManager?.findViewByPosition(0)
                child?.requestFocus() ?: rvBookmarks.requestFocus()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "native_home_shortcuts_v11"
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
            return list
        }

        fun saveUserBookmarks(context: Context, items: List<HomeShortcutItem>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            for (item in items) {
                if (!item.isAddButton && !item.isDeleteButton && !item.isActionCard && !item.isHeader && item.isUserBookmark) {
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
