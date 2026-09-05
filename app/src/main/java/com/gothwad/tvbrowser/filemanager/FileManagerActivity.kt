package com.gothwad.tvbrowser.filemanager

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.Formatter
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.BuildConfig
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.utils.setupAsSidebar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.Stack

class FileManagerActivity : AppCompatActivity() {

    enum class Category {
        STORAGE, SYSTEM_ROOT, RECENTS, DOWNLOADS, APKS, VIDEOS, AUDIO, IMAGES, DOCS
    }

    enum class SortMode(val title: String) {
        NAME_ASC("Name (A to Z)"),
        NAME_DESC("Name (Z to A)"),
        DATE_DESC("Date (Newest first)"),
        DATE_ASC("Date (Oldest first)"),
        SIZE_DESC("Size (Largest first)"),
        SIZE_ASC("Size (Smallest first)"),
        TYPE_ASC("Type / Extension")
    }

    private var currentCategory = Category.STORAGE
    private var currentDirectory: File = Environment.getExternalStorageDirectory()

    // History Stacks for forward and backward navigation
    private val backHistoryStack = Stack<File>()
    private val forwardHistoryStack = Stack<File>()

    private var allLoadedFiles: List<FileItem> = emptyList()
    private var currentFilterText: String = ""
    private var sortMode: SortMode = SortMode.NAME_ASC
    private var isGridMode: Boolean = false
    private var isMultiSelectMode: Boolean = false

    private lateinit var prefs: SharedPreferences

    // Views
    private lateinit var rvFiles: RecyclerView
    private lateinit var adapter: FileManagerAdapter
    private lateinit var hsvBreadcrumbs: HorizontalScrollView
    private lateinit var llBreadcrumbs: LinearLayout
    private lateinit var tvCurrentPath: TextView
    private lateinit var tvItemCountBadge: TextView
    private lateinit var ivPathTypeIcon: ImageView
    private lateinit var llPathContainer: LinearLayout

    // Search Views
    private lateinit var llSearchBar: LinearLayout
    private lateinit var etSearchQuery: EditText
    private lateinit var ibClearSearch: ImageButton
    private lateinit var ibSearch: ImageButton

    // Header Action Buttons
    private lateinit var ibSort: ImageButton
    private lateinit var ibViewMode: ImageButton
    private lateinit var ibSelectMode: ImageButton
    private lateinit var ibPaste: ImageButton
    private lateinit var ibRefresh: ImageButton
    private lateinit var ibNewFile: ImageButton
    private lateinit var ibNewFolder: ImageButton
    private lateinit var ibNavHistoryBack: ImageButton
    private lateinit var ibNavHistoryForward: ImageButton

    // USB / OTG Partitions Container
    private lateinit var llUsbPartitions: LinearLayout

    // Multi-Select Bottom Bar
    private lateinit var llMultiSelectBar: LinearLayout
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnSelectAll: Button
    private lateinit var btnBatchCopy: Button
    private lateinit var btnBatchCut: Button
    private lateinit var btnBatchDelete: Button
    private lateinit var btnBatchShare: Button
    private lateinit var btnCancelSelect: Button

    // Sidebar Category Layouts
    private lateinit var btnCatStorage: LinearLayout
    private lateinit var btnCatSystemRoot: LinearLayout
    private lateinit var btnCatRecents: LinearLayout
    private lateinit var btnCatDownloads: LinearLayout
    private lateinit var btnCatApks: LinearLayout
    private lateinit var btnCatVideos: LinearLayout
    private lateinit var btnCatAudio: LinearLayout
    private lateinit var btnCatImages: LinearLayout
    private lateinit var btnCatDocs: LinearLayout

    // Sidebar Storage Info Section
    private lateinit var llSidebarStorageManager: LinearLayout
    private lateinit var pbSidebarStorage: ProgressBar
    private lateinit var tvSidebarStorageStats: TextView

    // Status Views
    private lateinit var llEmptyView: LinearLayout
    private lateinit var llPermissionPrompt: LinearLayout
    private lateinit var btnGrantPermission: Button
    private lateinit var pbLoading: ProgressBar

    private var searchJob: Job? = null
    private val STORAGE_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupAsSidebar(true)
        setContentView(R.layout.activity_file_manager)

        prefs = getSharedPreferences("file_manager_prefs", Context.MODE_PRIVATE)
        isGridMode = prefs.getBoolean("pref_grid_mode", false)
        val savedSortOrdinal = prefs.getInt("pref_sort_mode", SortMode.NAME_ASC.ordinal)
        sortMode = SortMode.values().getOrElse(savedSortOrdinal) { SortMode.NAME_ASC }

        initViews()
        setupListeners()
        setupDragAndDropSupport()
        loadSidebarStorageStats()
        loadUsbPartitions()
        updatePasteButton()
        checkPermissionsAndLoad()
    }

    private fun initViews() {
        rvFiles = findViewById(R.id.rvFiles)
        llPathContainer = findViewById(R.id.llPathContainer)
        hsvBreadcrumbs = findViewById(R.id.hsvBreadcrumbs)
        llBreadcrumbs = findViewById(R.id.llBreadcrumbs)
        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        tvItemCountBadge = findViewById(R.id.tvItemCountBadge)
        ivPathTypeIcon = findViewById(R.id.ivPathTypeIcon)

        llSearchBar = findViewById(R.id.llSearchBar)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        ibClearSearch = findViewById(R.id.ibClearSearch)
        ibSearch = findViewById(R.id.ibSearch)

        ibSort = findViewById(R.id.ibSort)
        ibViewMode = findViewById(R.id.ibViewMode)
        ibSelectMode = findViewById(R.id.ibSelectMode)
        ibPaste = findViewById(R.id.ibPaste)
        ibRefresh = findViewById(R.id.ibRefresh)
        ibNewFile = findViewById(R.id.ibNewFile)
        ibNewFolder = findViewById(R.id.ibNewFolder)
        ibNavHistoryBack = findViewById(R.id.ibNavHistoryBack)
        ibNavHistoryForward = findViewById(R.id.ibNavHistoryForward)

        llUsbPartitions = findViewById(R.id.llUsbPartitions)

        llMultiSelectBar = findViewById(R.id.llMultiSelectBar)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnBatchCopy = findViewById(R.id.btnBatchCopy)
        btnBatchCut = findViewById(R.id.btnBatchCut)
        btnBatchDelete = findViewById(R.id.btnBatchDelete)
        btnBatchShare = findViewById(R.id.btnBatchShare)
        btnCancelSelect = findViewById(R.id.btnCancelSelect)

        btnCatStorage = findViewById(R.id.btnCatStorage)
        btnCatSystemRoot = findViewById(R.id.btnCatSystemRoot)
        btnCatRecents = findViewById(R.id.btnCatRecents)
        btnCatDownloads = findViewById(R.id.btnCatDownloads)
        btnCatApks = findViewById(R.id.btnCatApks)
        btnCatVideos = findViewById(R.id.btnCatVideos)
        btnCatAudio = findViewById(R.id.btnCatAudio)
        btnCatImages = findViewById(R.id.btnCatImages)
        btnCatDocs = findViewById(R.id.btnCatDocs)

        llSidebarStorageManager = findViewById(R.id.llSidebarStorageManager)
        pbSidebarStorage = findViewById(R.id.pbSidebarStorage)
        tvSidebarStorageStats = findViewById(R.id.tvSidebarStorageStats)

        llEmptyView = findViewById(R.id.llEmptyView)
        llPermissionPrompt = findViewById(R.id.llPermissionPrompt)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        pbLoading = findViewById(R.id.pbLoading)

        setupRecyclerViewLayout()

        adapter = FileManagerAdapter(
            items = emptyList(),
            onItemClick = { fileItem -> onFileClicked(fileItem) },
            onItemLongClick = { fileItem ->
                FileManagerOperations.showFileOptionsDialog(
                    context = this,
                    item = fileItem,
                    onOpen = { onFileClicked(it) },
                    onRefresh = {
                        updatePasteButton()
                        loadCurrentCategory()
                    }
                )
                true
            },
            onItemMoreClick = { fileItem ->
                FileManagerOperations.showFileOptionsDialog(
                    context = this,
                    item = fileItem,
                    onOpen = { onFileClicked(it) },
                    onRefresh = {
                        updatePasteButton()
                        loadCurrentCategory()
                    }
                )
            },
            onSelectionChanged = { count ->
                tvSelectedCount.text = "$count selected"
            }
        )
        adapter.isGridMode = isGridMode
        rvFiles.adapter = adapter
    }

    private fun setupRecyclerViewLayout() {
        if (isGridMode) {
            val spanCount = if (resources.configuration.smallestScreenWidthDp >= 600) 4 else 3
            rvFiles.layoutManager = GridLayoutManager(this, spanCount)
            ibViewMode.setImageResource(R.drawable.ic_view_list)
            ibViewMode.contentDescription = "Switch to List View"
        } else {
            rvFiles.layoutManager = LinearLayoutManager(this)
            ibViewMode.setImageResource(R.drawable.ic_view_grid)
            ibViewMode.contentDescription = "Switch to Grid View"
        }
    }

    private fun setupListeners() {
        // Exit to browser
        findViewById<ImageButton>(R.id.ibBack).setOnClickListener {
            finish()
        }

        // Home / Internal Storage Root Shortcut
        findViewById<ImageButton>(R.id.ibHomeRoot).setOnClickListener {
            navigateToDirectory(Environment.getExternalStorageDirectory(), Category.STORAGE)
        }

        // History Back (<)
        ibNavHistoryBack.setOnClickListener {
            if (backHistoryStack.isNotEmpty()) {
                forwardHistoryStack.push(currentDirectory)
                currentDirectory = backHistoryStack.pop()
                syncCategoryWithDirectory(currentDirectory)
                loadCurrentCategory()
            } else if (currentDirectory.parentFile != null && currentDirectory.parentFile?.canRead() == true && currentDirectory.absolutePath != "/") {
                navigateFolderUp()
            }
        }

        // History Forward (>)
        ibNavHistoryForward.setOnClickListener {
            if (forwardHistoryStack.isNotEmpty()) {
                backHistoryStack.push(currentDirectory)
                currentDirectory = forwardHistoryStack.pop()
                syncCategoryWithDirectory(currentDirectory)
                loadCurrentCategory()
            }
        }

        // Search Toggle Button
        ibSearch.setOnClickListener {
            toggleSearchBar()
        }

        // Search Input TextWatcher for Instant In-Folder Filter
        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentFilterText = s?.toString()?.trim() ?: ""
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Search Action (Enter key on keyboard / remote) -> Recursive Deep Search
        etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performRecursiveSearch(etSearchQuery.text.toString().trim())
                true
            } else {
                false
            }
        }

        ibClearSearch.setOnClickListener {
            if (etSearchQuery.text.isNotEmpty()) {
                etSearchQuery.setText("")
            } else {
                toggleSearchBar(false)
            }
        }

        // Sort Options Dialog
        ibSort.setOnClickListener {
            showSortDialog()
        }

        // Grid / List Switcher Button
        ibViewMode.setOnClickListener {
            isGridMode = !isGridMode
            prefs.edit().putBoolean("pref_grid_mode", isGridMode).apply()
            setupRecyclerViewLayout()
            adapter.isGridMode = isGridMode
        }

        // Multi-Selection Toggle Button
        ibSelectMode.setOnClickListener {
            toggleMultiSelectMode()
        }

        // Paste Button
        ibPaste.setOnClickListener {
            executePasteAction()
        }

        // Refresh / Reload
        ibRefresh.setOnClickListener {
            loadCurrentCategory()
            loadSidebarStorageStats()
            loadUsbPartitions()
            updatePasteButton()
        }

        // Create New File
        ibNewFile.setOnClickListener {
            if (currentCategory == Category.SYSTEM_ROOT && !currentDirectory.canWrite()) {
                Toast.makeText(this, "Root directory is read-only without root access", Toast.LENGTH_SHORT).show()
            } else {
                FileManagerOperations.showNewFileDialog(this, currentDirectory) {
                    loadCurrentCategory()
                }
            }
        }

        // Create New Folder
        ibNewFolder.setOnClickListener {
            if (currentCategory == Category.SYSTEM_ROOT && !currentDirectory.canWrite()) {
                Toast.makeText(this, "Root directory is read-only without root access", Toast.LENGTH_SHORT).show()
            } else {
                FileManagerOperations.showNewFolderDialog(this, currentDirectory) {
                    loadCurrentCategory()
                }
            }
        }

        // Multi-Select Bar Buttons
        btnSelectAll.setOnClickListener {
            adapter.selectAll()
        }

        btnBatchCopy.setOnClickListener {
            val selected = adapter.getSelectedFiles()
            if (selected.isNotEmpty()) {
                FileManagerOperations.copyFiles(selected)
                updatePasteButton()
                toggleMultiSelectMode(false)
                Toast.makeText(this, "Copied ${selected.size} items to clipboard. Navigate to target and click Paste.", Toast.LENGTH_LONG).show()
            }
        }

        btnBatchCut.setOnClickListener {
            val selected = adapter.getSelectedFiles()
            if (selected.isNotEmpty()) {
                FileManagerOperations.cutFiles(selected)
                updatePasteButton()
                toggleMultiSelectMode(false)
                Toast.makeText(this, "Cut ${selected.size} items to clipboard. Navigate to target and click Paste.", Toast.LENGTH_LONG).show()
            }
        }

        btnBatchDelete.setOnClickListener {
            val selected = adapter.getSelectedFiles()
            if (selected.isEmpty()) return@setOnClickListener

            AlertDialog.Builder(this)
                .setTitle("Delete Files")
                .setMessage("Are you sure you want to permanently delete ${selected.size} items?")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val deletedCount = FileManagerOperations.deleteBatch(selected)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@FileManagerActivity, "Deleted $deletedCount items", Toast.LENGTH_SHORT).show()
                            toggleMultiSelectMode(false)
                            loadCurrentCategory()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnBatchShare.setOnClickListener {
            val selected = adapter.getSelectedFiles().filter { !it.isDirectory }
            if (selected.isEmpty()) {
                Toast.makeText(this, "Folders cannot be shared directly", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            shareBatchFiles(selected)
        }

        btnCancelSelect.setOnClickListener {
            toggleMultiSelectMode(false)
        }

        // Sidebar Storage Manager Click
        llSidebarStorageManager.setOnClickListener {
            StorageManagerDialog(this).show()
        }

        // Left Panel Category & Storage Partition buttons
        btnCatStorage.setOnClickListener {
            navigateToDirectory(Environment.getExternalStorageDirectory(), Category.STORAGE)
        }

        btnCatSystemRoot.setOnClickListener {
            navigateToDirectory(File("/"), Category.SYSTEM_ROOT)
        }

        btnCatDownloads.setOnClickListener {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            navigateToDirectory(downloadsDir, Category.DOWNLOADS)
        }

        btnCatRecents.setOnClickListener { switchCategoryView(Category.RECENTS) }
        btnCatApks.setOnClickListener { switchCategoryView(Category.APKS) }
        btnCatVideos.setOnClickListener { switchCategoryView(Category.VIDEOS) }
        btnCatAudio.setOnClickListener { switchCategoryView(Category.AUDIO) }
        btnCatImages.setOnClickListener { switchCategoryView(Category.IMAGES) }
        btnCatDocs.setOnClickListener { switchCategoryView(Category.DOCS) }

        btnGrantPermission.setOnClickListener {
            requestStoragePermission()
        }
    }

    private fun toggleSearchBar(forceState: Boolean? = null) {
        val show = forceState ?: (llSearchBar.visibility != View.VISIBLE)
        if (show) {
            llPathContainer.visibility = View.GONE
            llSearchBar.visibility = View.VISIBLE
            etSearchQuery.requestFocus()
        } else {
            etSearchQuery.setText("")
            currentFilterText = ""
            llSearchBar.visibility = View.GONE
            llPathContainer.visibility = View.VISIBLE
            applyFilterAndSort()
        }
    }

    private fun toggleMultiSelectMode(forceState: Boolean? = null) {
        isMultiSelectMode = forceState ?: !isMultiSelectMode
        adapter.isMultiSelect = isMultiSelectMode
        llMultiSelectBar.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
        tvSelectedCount.text = "${adapter.selectedPaths.size} selected"
    }

    private fun updatePasteButton() {
        if (FileManagerOperations.hasClipboard()) {
            val clip = FileManagerOperations.clipboard
            val count = clip?.files?.size ?: 0
            val mode = if (clip?.mode == FileManagerOperations.ClipMode.CUT) "Cut" else "Copy"
            ibPaste.visibility = View.VISIBLE
            ibPaste.contentDescription = "Paste ($mode $count items)"
        } else {
            ibPaste.visibility = View.GONE
        }
    }

    private fun executePasteAction() {
        val clip = FileManagerOperations.clipboard ?: return
        if (!currentDirectory.canWrite()) {
            Toast.makeText(this, "Current directory is read-only", Toast.LENGTH_SHORT).show()
            return
        }

        pbLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val (count, msg) = FileManagerOperations.pasteTo(currentDirectory)
            withContext(Dispatchers.Main) {
                pbLoading.visibility = View.GONE
                Toast.makeText(this@FileManagerActivity, msg, Toast.LENGTH_SHORT).show()
                updatePasteButton()
                loadCurrentCategory()
            }
        }
    }

    private fun showSortDialog() {
        val modes = SortMode.values()
        val titles = modes.map { it.title }.toTypedArray()
        val checkedItem = modes.indexOf(sortMode).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Sort Files By")
            .setSingleChoiceItems(titles, checkedItem) { dialog, which ->
                sortMode = modes[which]
                prefs.edit().putInt("pref_sort_mode", sortMode.ordinal).apply()
                applyFilterAndSort()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadUsbPartitions() {
        lifecycleScope.launch(Dispatchers.IO) {
            val partitions = StorageVolumeDetector.getStoragePartitions(this@FileManagerActivity)
            withContext(Dispatchers.Main) {
                llUsbPartitions.removeAllViews()
                for (part in partitions) {
                    addPartitionView(part)
                }
            }
        }
    }

    private fun addPartitionView(part: StoragePartition) {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (34 * resources.displayMetrics.density).toInt()
            ).apply {
                bottomMargin = (2 * resources.displayMetrics.density).toInt()
            }
            setBackgroundResource(R.drawable.sidebar_nav_button_bg)
            isClickable = true
            isFocusable = true
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                (8 * resources.displayMetrics.density).toInt(),
                0,
                (6 * resources.displayMetrics.density).toInt(),
                0
            )
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (17 * resources.displayMetrics.density).toInt(),
                (17 * resources.displayMetrics.density).toInt()
            )
            setImageResource(if (part.isUsb) R.drawable.ic_usb else R.drawable.ic_storage_internal)
            setColorFilter(ContextCompat.getColor(this@FileManagerActivity, R.color.progressbar_tint))
        }

        val title = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply {
                marginStart = (8 * resources.displayMetrics.density).toInt()
            }
            isSingleLine = true
            text = part.name
            setTextColor(ContextCompat.getColor(this@FileManagerActivity, R.color.day_night_text_color_contrast))
            textSize = 11.5f
        }

        row.addView(icon)
        row.addView(title)
        row.setOnClickListener {
            navigateToDirectory(part.path, Category.STORAGE)
        }

        llUsbPartitions.addView(row)
    }

    private fun loadSidebarStorageStats() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                val totalBytes = stat.totalBytes
                val availableBytes = stat.availableBytes
                val usedBytes = totalBytes - availableBytes
                val percentUsed = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0

                val totalStr = Formatter.formatFileSize(this@FileManagerActivity, totalBytes)
                val usedStr = Formatter.formatFileSize(this@FileManagerActivity, usedBytes)

                withContext(Dispatchers.Main) {
                    pbSidebarStorage.progress = percentUsed
                    tvSidebarStorageStats.text = "$usedStr / $totalStr used ($percentUsed%)"
                }
            } catch (_: Exception) {}
        }
    }

    private fun navigateToDirectory(targetDir: File, category: Category) {
        if (currentDirectory.absolutePath != targetDir.absolutePath || currentCategory != category) {
            backHistoryStack.push(currentDirectory)
            forwardHistoryStack.clear()
            currentDirectory = targetDir
            currentCategory = category
            updateCategoryButtonsHighlight()
            loadCurrentCategory()
        }
    }

    private fun switchCategoryView(category: Category) {
        currentCategory = category
        updateCategoryButtonsHighlight()
        loadCurrentCategory()
    }

    private fun syncCategoryWithDirectory(dir: File) {
        currentCategory = when {
            dir.absolutePath == "/" || dir.absolutePath.startsWith("/system") || dir.absolutePath.startsWith("/data") || dir.absolutePath.startsWith("/etc") -> Category.SYSTEM_ROOT
            dir.absolutePath.contains("Download", ignoreCase = true) -> Category.DOWNLOADS
            else -> Category.STORAGE
        }
        updateCategoryButtonsHighlight()
    }

    private fun navigateFolderUp() {
        val parent = currentDirectory.parentFile
        if (parent != null) {
            backHistoryStack.push(currentDirectory)
            forwardHistoryStack.clear()
            currentDirectory = parent
            syncCategoryWithDirectory(currentDirectory)
            loadCurrentCategory()
        } else if (currentDirectory.absolutePath != "/") {
            backHistoryStack.push(currentDirectory)
            forwardHistoryStack.clear()
            currentDirectory = File("/")
            syncCategoryWithDirectory(currentDirectory)
            loadCurrentCategory()
        } else {
            Toast.makeText(this, "Already at root filesystem /", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBreadcrumbs(dir: File) {
        val isFolderCategory = currentCategory == Category.STORAGE || currentCategory == Category.SYSTEM_ROOT || currentCategory == Category.DOWNLOADS

        if (!isFolderCategory) {
            hsvBreadcrumbs.visibility = View.GONE
            tvCurrentPath.visibility = View.VISIBLE
            tvCurrentPath.text = when (currentCategory) {
                Category.RECENTS -> "Recent Files"
                else -> "Category: ${currentCategory.name.lowercase().replaceFirstChar { it.uppercase() }}"
            }
            return
        }

        hsvBreadcrumbs.visibility = View.VISIBLE
        tvCurrentPath.visibility = View.GONE
        llBreadcrumbs.removeAllViews()

        val chain = mutableListOf<File>()
        var curr: File? = dir
        while (curr != null) {
            chain.add(0, curr)
            curr = curr.parentFile
        }

        val primaryStorage = Environment.getExternalStorageDirectory().absolutePath

        for ((index, folder) in chain.withIndex()) {
            val displayName = when {
                folder.absolutePath == "/" -> "Root (/)"
                folder.absolutePath == primaryStorage -> "Internal (ROM)"
                else -> folder.name
            }

            val chip = TextView(this).apply {
                text = displayName
                textSize = 11.5f
                setTextColor(ContextCompat.getColor(this@FileManagerActivity, if (index == chain.size - 1) android.R.color.white else R.color.day_night_text_color_contrast))
                setBackgroundResource(R.drawable.button_bg_selector)
                isClickable = true
                isFocusable = true
                val padH = (6 * resources.displayMetrics.density).toInt()
                val padV = (2 * resources.displayMetrics.density).toInt()
                setPadding(padH, padV, padH, padV)
                setOnClickListener {
                    if (folder.absolutePath != currentDirectory.absolutePath) {
                        navigateToDirectory(folder, if (folder.absolutePath == "/") Category.SYSTEM_ROOT else Category.STORAGE)
                    }
                }
            }
            llBreadcrumbs.addView(chip)

            if (index < chain.size - 1) {
                val arrow = TextView(this).apply {
                    text = " > "
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(this@FileManagerActivity, R.color.day_night_disabled_icon_color))
                }
                llBreadcrumbs.addView(arrow)
            }
        }

        hsvBreadcrumbs.post {
            hsvBreadcrumbs.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
    }

    private fun setupDragAndDropSupport() {
        rvFiles.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                }
                DragEvent.ACTION_DRAG_ENTERED -> true
                DragEvent.ACTION_DRAG_LOCATION -> true
                DragEvent.ACTION_DRAG_EXITED -> true
                DragEvent.ACTION_DROP -> {
                    val item: ClipData.Item = event.clipData?.getItemAt(0) ?: return@setOnDragListener false
                    val srcPath = item.text?.toString()
                    if (!srcPath.isNullOrEmpty() && currentDirectory.canWrite()) {
                        val srcFile = File(srcPath)
                        if (srcFile.exists() && srcFile.parentFile != currentDirectory) {
                            val destFile = File(currentDirectory, srcFile.name)
                            if (srcFile.renameTo(destFile)) {
                                Toast.makeText(this, "Moved to ${currentDirectory.name}", Toast.LENGTH_SHORT).show()
                                loadCurrentCategory()
                            }
                        }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> true
                else -> false
            }
        }
    }

    private fun updateNavButtonStates() {
        ibNavHistoryBack.alpha = if (backHistoryStack.isNotEmpty() || (currentDirectory.parentFile != null && currentDirectory.absolutePath != "/")) 1.0f else 0.4f
        ibNavHistoryForward.alpha = if (forwardHistoryStack.isNotEmpty()) 1.0f else 0.4f

        if (currentDirectory.absolutePath == "/" || currentDirectory.absolutePath.startsWith("/system")) {
            ivPathTypeIcon.setImageResource(R.drawable.ic_root_partition)
        } else {
            ivPathTypeIcon.setImageResource(R.drawable.ic_folder)
        }
    }

    private fun updateCategoryButtonsHighlight() {
        val buttonMap = mapOf(
            Category.STORAGE to Pair(btnCatStorage, R.id.tvCatStorageText),
            Category.SYSTEM_ROOT to Pair(btnCatSystemRoot, R.id.tvCatSystemRootText),
            Category.DOWNLOADS to Pair(btnCatDownloads, R.id.tvCatDownloadsText),
            Category.RECENTS to Pair(btnCatRecents, R.id.tvCatRecentsText),
            Category.APKS to Pair(btnCatApks, R.id.tvCatApksText),
            Category.VIDEOS to Pair(btnCatVideos, R.id.tvCatVideosText),
            Category.AUDIO to Pair(btnCatAudio, R.id.tvCatAudioText),
            Category.IMAGES to Pair(btnCatImages, R.id.tvCatImagesText),
            Category.DOCS to Pair(btnCatDocs, R.id.tvCatDocsText)
        )

        val selectedColor = ContextCompat.getColor(this, R.color.day_night_text_color_contrast)
        val unselectedColor = ContextCompat.getColor(this, R.color.day_night_text_secondary)

        for ((cat, pair) in buttonMap) {
            val (container, textId) = pair
            val textView = container.findViewById<TextView>(textId)
            if (cat == currentCategory) {
                container.isSelected = true
                textView?.setTextColor(selectedColor)
                textView?.paint?.isFakeBoldText = true
            } else {
                container.isSelected = false
                textView?.setTextColor(unselectedColor)
                textView?.paint?.isFakeBoldText = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePasteButton()
        if (hasStoragePermission()) {
            if (llPermissionPrompt.visibility == View.VISIBLE) {
                llPermissionPrompt.visibility = View.GONE
                loadCurrentCategory()
                loadSidebarStorageStats()
                loadUsbPartitions()
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "Unable to open storage settings: ${e2.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun checkPermissionsAndLoad(promptUser: Boolean = true) {
        if (hasStoragePermission()) {
            llPermissionPrompt.visibility = View.GONE
            loadCurrentCategory()
            loadSidebarStorageStats()
            loadUsbPartitions()
        } else {
            pbLoading.visibility = View.GONE
            llEmptyView.visibility = View.GONE
            adapter.updateItems(emptyList())
            llPermissionPrompt.visibility = View.VISIBLE
            btnGrantPermission.requestFocus()
            if (promptUser) {
                requestStoragePermission()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                llPermissionPrompt.visibility = View.GONE
                loadCurrentCategory()
                loadSidebarStorageStats()
                loadUsbPartitions()
            } else {
                llPermissionPrompt.visibility = View.VISIBLE
                Toast.makeText(this, "Storage permission is required to browse files", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCurrentCategory() {
        pbLoading.visibility = View.VISIBLE
        llEmptyView.visibility = View.GONE
        updateNavButtonStates()

        lifecycleScope.launch(Dispatchers.IO) {
            val filesList: List<FileItem> = when (currentCategory) {
                Category.STORAGE, Category.SYSTEM_ROOT, Category.DOWNLOADS -> loadDirectoryFiles(currentDirectory)
                Category.RECENTS -> {
                    val ms = MediaStoreHelper.loadRecentFiles(this@FileManagerActivity)
                    if (ms.isNotEmpty()) ms else loadRecentFiles()
                }
                Category.APKS -> {
                    val ms = MediaStoreHelper.loadApks(this@FileManagerActivity)
                    if (ms.isNotEmpty()) ms else scanFilesByExtensions(setOf("apk"))
                }
                Category.VIDEOS -> {
                    val ms = MediaStoreHelper.loadVideos(this@FileManagerActivity)
                    if (ms.isNotEmpty()) ms else scanFilesByExtensions(setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "flv", "3gp", "m4v"))
                }
                Category.AUDIO -> {
                    val ms = MediaStoreHelper.loadAudio(this@FileManagerActivity)
                    if (ms.isNotEmpty()) ms else scanFilesByExtensions(setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "wma", "opus"))
                }
                Category.IMAGES -> {
                    val ms = MediaStoreHelper.loadImages(this@FileManagerActivity)
                    if (ms.isNotEmpty()) ms else scanFilesByExtensions(setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg"))
                }
                Category.DOCS -> {
                    val ms = MediaStoreHelper.loadDocuments(this@FileManagerActivity)
                    if (ms.isNotEmpty()) ms else scanFilesByExtensions(setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "json", "zip", "rar", "7z"))
                }
            }

            withContext(Dispatchers.Main) {
                pbLoading.visibility = View.GONE
                allLoadedFiles = filesList
                updateBreadcrumbs(currentDirectory)
                applyFilterAndSort()
                updateNavButtonStates()
            }
        }
    }

    private fun applyFilterAndSort() {
        var list = allLoadedFiles

        // 1. In-Folder / Category Filter
        if (currentFilterText.isNotBlank()) {
            list = list.filter { it.name.contains(currentFilterText, ignoreCase = true) }
        }

        // 2. Configurable Sorting
        val isFolderMode = currentCategory == Category.STORAGE || currentCategory == Category.SYSTEM_ROOT || currentCategory == Category.DOWNLOADS

        list = list.sortedWith { a, b ->
            if (isFolderMode && a.isDirectory != b.isDirectory) {
                if (a.isDirectory) -1 else 1
            } else {
                when (sortMode) {
                    SortMode.NAME_ASC -> a.name.lowercase(Locale.ROOT).compareTo(b.name.lowercase(Locale.ROOT))
                    SortMode.NAME_DESC -> b.name.lowercase(Locale.ROOT).compareTo(a.name.lowercase(Locale.ROOT))
                    SortMode.DATE_DESC -> b.lastModified.compareTo(a.lastModified)
                    SortMode.DATE_ASC -> a.lastModified.compareTo(b.lastModified)
                    SortMode.SIZE_DESC -> b.size.compareTo(a.size)
                    SortMode.SIZE_ASC -> a.size.compareTo(b.size)
                    SortMode.TYPE_ASC -> a.extension.compareTo(b.extension)
                }
            }
        }

        tvItemCountBadge.text = "${list.size} items"
        if (list.isEmpty()) {
            llEmptyView.visibility = View.VISIBLE
            adapter.updateItems(emptyList())
        } else {
            llEmptyView.visibility = View.GONE
            adapter.updateItems(list)
        }
    }

    private fun performRecursiveSearch(query: String) {
        if (query.isBlank()) {
            applyFilterAndSort()
            return
        }

        searchJob?.cancel()
        pbLoading.visibility = View.VISIBLE
        searchJob = lifecycleScope.launch(Dispatchers.IO) {
            val results = mutableListOf<FileItem>()
            val baseDir = if (currentCategory == Category.SYSTEM_ROOT) File("/") else currentDirectory
            searchRecursiveDir(baseDir, query.lowercase(Locale.ROOT), results, 0, 5)
            withContext(Dispatchers.Main) {
                pbLoading.visibility = View.GONE
                allLoadedFiles = results
                tvCurrentPath.visibility = View.VISIBLE
                hsvBreadcrumbs.visibility = View.GONE
                tvCurrentPath.text = "Search: \"$query\""
                applyFilterAndSort()
            }
        }
    }

    private fun searchRecursiveDir(dir: File, queryLower: String, list: MutableList<FileItem>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth || !dir.exists() || !dir.canRead() || list.size > 300) return
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.name.lowercase(Locale.ROOT).contains(queryLower)) {
                val childCount = if (f.isDirectory) f.list()?.size ?: 0 else -1
                list.add(FileItem(file = f, childCount = childCount))
            }
            if (f.isDirectory && !f.name.equals("Android", ignoreCase = true)) {
                searchRecursiveDir(f, queryLower, list, depth + 1, maxDepth)
            }
        }
    }

    private fun loadRecentFiles(): List<FileItem> {
        val list = mutableListOf<FileItem>()
        val rootDir = Environment.getExternalStorageDirectory()
        scanRecentRecursive(rootDir, list, 0, 4)
        list.sortByDescending { it.lastModified }
        return list.take(100)
    }

    private fun scanRecentRecursive(dir: File, list: MutableList<FileItem>, currentDepth: Int, maxDepth: Int) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.canRead() || list.size > 300) return
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (!f.name.equals("Android", ignoreCase = true)) {
                    scanRecentRecursive(f, list, currentDepth + 1, maxDepth)
                }
            } else {
                list.add(FileItem(file = f, childCount = -1))
            }
        }
    }

    private fun loadDirectoryFiles(dir: File): List<FileItem> {
        val list = mutableListOf<FileItem>()
        if (!dir.exists() || !dir.canRead()) return list

        val files = dir.listFiles() ?: return list
        for (f in files) {
            // Count directory items here on Dispatchers.IO to prevent main-thread lag
            val count = if (f.isDirectory) f.list()?.size ?: 0 else -1
            list.add(FileItem(file = f, childCount = count))
        }

        // Directories first, then alphabetical
        list.sortWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
        return list
    }

    private fun scanFilesByExtensions(extensions: Set<String>): List<FileItem> {
        val list = mutableListOf<FileItem>()
        val rootDir = Environment.getExternalStorageDirectory()
        scanRecursive(rootDir, extensions, list, 0, 4)
        list.sortByDescending { it.lastModified }
        return list
    }

    private fun scanRecursive(dir: File, extensions: Set<String>, list: MutableList<FileItem>, currentDepth: Int, maxDepth: Int) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.canRead() || list.size > 250) return
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (!f.name.equals("Android", ignoreCase = true)) {
                    scanRecursive(f, extensions, list, currentDepth + 1, maxDepth)
                }
            } else {
                val ext = f.extension.lowercase(Locale.ROOT)
                if (extensions.contains(ext)) {
                    list.add(FileItem(file = f, childCount = -1))
                }
            }
        }
    }

    private fun shareBatchFiles(files: List<File>) {
        try {
            val uris = ArrayList<Uri>()
            for (f in files) {
                uris.add(FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.provider", f))
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share ${files.size} files via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share files: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onFileClicked(fileItem: FileItem) {
        if (fileItem.isDirectory) {
            backHistoryStack.push(currentDirectory)
            forwardHistoryStack.clear()
            currentDirectory = fileItem.file
            syncCategoryWithDirectory(currentDirectory)
            loadCurrentCategory()
        } else {
            FileManagerOperations.openFile(this, fileItem.file)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isMultiSelectMode) {
            toggleMultiSelectMode(false)
            return
        }
        if (llSearchBar.visibility == View.VISIBLE) {
            toggleSearchBar(false)
            return
        }
        if (backHistoryStack.isNotEmpty()) {
            forwardHistoryStack.push(currentDirectory)
            currentDirectory = backHistoryStack.pop()
            syncCategoryWithDirectory(currentDirectory)
            loadCurrentCategory()
        } else if (currentDirectory.parentFile != null && currentDirectory.parentFile?.canRead() == true && currentDirectory.absolutePath != "/") {
            navigateFolderUp()
        } else {
            super.onBackPressed()
        }
    }
}
