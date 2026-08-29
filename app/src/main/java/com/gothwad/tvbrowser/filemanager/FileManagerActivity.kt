package com.gothwad.tvbrowser.filemanager

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import android.view.DragEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.Stack

class FileManagerActivity : AppCompatActivity() {

    enum class Category {
        STORAGE, SYSTEM_ROOT, RECENTS, DOWNLOADS, APKS, VIDEOS, AUDIO, IMAGES, DOCS
    }

    private var currentCategory = Category.STORAGE
    private var currentDirectory: File = Environment.getExternalStorageDirectory()

    // MT Manager style History Stacks for forward and backward navigation
    private val backHistoryStack = Stack<File>()
    private val forwardHistoryStack = Stack<File>()

    private lateinit var rvFiles: RecyclerView
    private lateinit var adapter: FileManagerAdapter
    private lateinit var tvCurrentPath: TextView
    private lateinit var tvItemCountBadge: TextView
    private lateinit var ivPathTypeIcon: ImageView
    private lateinit var llEmptyView: LinearLayout
    private lateinit var llPermissionPrompt: LinearLayout
    private lateinit var btnGrantPermission: Button
    private lateinit var pbLoading: ProgressBar

    private val STORAGE_PERMISSION_REQUEST_CODE = 1001

    private lateinit var ibNavHistoryBack: ImageButton
    private lateinit var ibNavHistoryForward: ImageButton

    // Left Sidebar category layouts
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        initViews()
        setupListeners()
        setupDragAndDropSupport()
        loadSidebarStorageStats()
        checkPermissionsAndLoad()
    }

    private fun initViews() {
        rvFiles = findViewById(R.id.rvFiles)
        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        tvItemCountBadge = findViewById(R.id.tvItemCountBadge)
        ivPathTypeIcon = findViewById(R.id.ivPathTypeIcon)
        llEmptyView = findViewById(R.id.llEmptyView)
        llPermissionPrompt = findViewById(R.id.llPermissionPrompt)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        pbLoading = findViewById(R.id.pbLoading)

        ibNavHistoryBack = findViewById(R.id.ibNavHistoryBack)
        ibNavHistoryForward = findViewById(R.id.ibNavHistoryForward)

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

        // Compact list with crisp divider & padding
        rvFiles.layoutManager = LinearLayoutManager(this)
        adapter = FileManagerAdapter(
            items = emptyList(),
            onItemClick = { fileItem -> onFileClicked(fileItem) },
            onItemLongClick = { fileItem ->
                FileManagerOperations.showFileOptionsDialog(
                    context = this,
                    item = fileItem,
                    onOpen = { onFileClicked(it) },
                    onRefresh = { loadCurrentCategory() }
                )
                true
            },
            onItemMoreClick = { fileItem ->
                FileManagerOperations.showFileOptionsDialog(
                    context = this,
                    item = fileItem,
                    onOpen = { onFileClicked(it) },
                    onRefresh = { loadCurrentCategory() }
                )
            }
        )
        rvFiles.adapter = adapter
    }

    private fun setupListeners() {
        // 1. Exit to browser
        findViewById<ImageButton>(R.id.ibBack).setOnClickListener {
            finish()
        }

        // 2. Home / Internal Storage Root Shortcut
        findViewById<ImageButton>(R.id.ibHomeRoot).setOnClickListener {
            navigateToDirectory(Environment.getExternalStorageDirectory(), Category.STORAGE)
        }

        // 3. History Back (<) Navigation
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

        // 4. History Forward (>) Navigation
        ibNavHistoryForward.setOnClickListener {
            if (forwardHistoryStack.isNotEmpty()) {
                backHistoryStack.push(currentDirectory)
                currentDirectory = forwardHistoryStack.pop()
                syncCategoryWithDirectory(currentDirectory)
                loadCurrentCategory()
            }
        }

        // 5. Clickable Path container for Breadcrumbs / Direct path jump
        findViewById<LinearLayout>(R.id.llPathContainer).setOnClickListener {
            showPathJumpDialog()
        }

        // 6. Refresh / Reload
        findViewById<ImageButton>(R.id.ibRefresh).setOnClickListener {
            loadCurrentCategory()
            loadSidebarStorageStats()
        }

        // 7. Create New File
        findViewById<ImageButton>(R.id.ibNewFile).setOnClickListener {
            if (currentCategory == Category.SYSTEM_ROOT && !currentDirectory.canWrite()) {
                Toast.makeText(this, "Root directory is read-only without root access", Toast.LENGTH_SHORT).show()
            } else {
                FileManagerOperations.showNewFileDialog(this, currentDirectory) {
                    loadCurrentCategory()
                }
            }
        }

        // 8. Create New Folder
        findViewById<ImageButton>(R.id.ibNewFolder).setOnClickListener {
            if (currentCategory == Category.SYSTEM_ROOT && !currentDirectory.canWrite()) {
                Toast.makeText(this, "Root directory is read-only without root access", Toast.LENGTH_SHORT).show()
            } else {
                FileManagerOperations.showNewFolderDialog(this, currentDirectory) {
                    loadCurrentCategory()
                }
            }
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

        btnCatRecents.setOnClickListener {
            switchCategoryView(Category.RECENTS)
        }

        btnCatApks.setOnClickListener {
            switchCategoryView(Category.APKS)
        }

        btnCatVideos.setOnClickListener {
            switchCategoryView(Category.VIDEOS)
        }

        btnCatAudio.setOnClickListener {
            switchCategoryView(Category.AUDIO)
        }

        btnCatImages.setOnClickListener {
            switchCategoryView(Category.IMAGES)
        }

        btnCatDocs.setOnClickListener {
            switchCategoryView(Category.DOCS)
        }

        btnGrantPermission.setOnClickListener {
            requestStoragePermission()
        }
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
            } catch (e: Exception) {
                // Ignore storage calculation errors
            }
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

    private fun showPathJumpDialog() {
        val etPath = EditText(this).apply {
            setText(currentDirectory.absolutePath)
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Go to Path")
            .setView(etPath)
            .setPositiveButton("Go") { _, _ ->
                val targetPath = etPath.text.toString().trim()
                val targetDir = File(targetPath)
                if (targetDir.exists() && targetDir.isDirectory) {
                    navigateToDirectory(targetDir, Category.STORAGE)
                } else {
                    Toast.makeText(this, "Directory does not exist or inaccessible", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        if (hasStoragePermission()) {
            if (llPermissionPrompt.visibility == View.VISIBLE) {
                llPermissionPrompt.visibility = View.GONE
                loadCurrentCategory()
                loadSidebarStorageStats()
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
            } catch (e: Exception) {
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
            val filesList = when (currentCategory) {
                Category.STORAGE, Category.SYSTEM_ROOT, Category.DOWNLOADS -> loadDirectoryFiles(currentDirectory)
                Category.RECENTS -> loadRecentFiles()
                Category.APKS -> scanFilesByExtensions(setOf("apk"))
                Category.VIDEOS -> scanFilesByExtensions(setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "flv", "3gp", "m4v"))
                Category.AUDIO -> scanFilesByExtensions(setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "wma", "opus"))
                Category.IMAGES -> scanFilesByExtensions(setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg"))
                Category.DOCS -> scanFilesByExtensions(setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "json", "zip", "rar", "7z"))
            }

            withContext(Dispatchers.Main) {
                pbLoading.visibility = View.GONE
                tvCurrentPath.text = when (currentCategory) {
                    Category.STORAGE, Category.SYSTEM_ROOT, Category.DOWNLOADS -> currentDirectory.absolutePath
                    Category.RECENTS -> "Recent Files (Chronological)"
                    else -> "Category: ${currentCategory.name}"
                }
                tvItemCountBadge.text = "${filesList.size} items"

                if (filesList.isEmpty()) {
                    llEmptyView.visibility = View.VISIBLE
                    adapter.updateItems(emptyList())
                } else {
                    llEmptyView.visibility = View.GONE
                    adapter.updateItems(filesList)
                }
                updateNavButtonStates()
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
                list.add(FileItem(file = f))
            }
        }
    }

    private fun loadDirectoryFiles(dir: File): List<FileItem> {
        val list = mutableListOf<FileItem>()
        if (!dir.exists() || !dir.canRead()) return list

        val files = dir.listFiles() ?: return list
        for (f in files) {
            list.add(FileItem(file = f))
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
                    list.add(FileItem(file = f))
                }
            }
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
