package com.gothwad.tvbrowser.filemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
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
        STORAGE, DOWNLOADS, APKS, VIDEOS, AUDIO, IMAGES, DOCS
    }

    private var currentCategory = Category.STORAGE
    private var currentDirectory: File = Environment.getExternalStorageDirectory()
    private val directoryStack = Stack<File>()

    private lateinit var rvFiles: RecyclerView
    private lateinit var adapter: FileManagerAdapter
    private lateinit var tvCurrentPath: TextView
    private lateinit var tvStorageStats: TextView
    private lateinit var llEmptyView: LinearLayout
    private lateinit var pbLoading: ProgressBar

    private lateinit var btnCatStorage: Button
    private lateinit var btnCatDownloads: Button
    private lateinit var btnCatApks: Button
    private lateinit var btnCatVideos: Button
    private lateinit var btnCatAudio: Button
    private lateinit var btnCatImages: Button
    private lateinit var btnCatDocs: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        initViews()
        setupListeners()
        updateStorageStats()
        checkPermissionsAndLoad()
    }

    private fun initViews() {
        rvFiles = findViewById(R.id.rvFiles)
        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        tvStorageStats = findViewById(R.id.tvStorageStats)
        llEmptyView = findViewById(R.id.llEmptyView)
        pbLoading = findViewById(R.id.pbLoading)

        btnCatStorage = findViewById(R.id.btnCatStorage)
        btnCatDownloads = findViewById(R.id.btnCatDownloads)
        btnCatApks = findViewById(R.id.btnCatApks)
        btnCatVideos = findViewById(R.id.btnCatVideos)
        btnCatAudio = findViewById(R.id.btnCatAudio)
        btnCatImages = findViewById(R.id.btnCatImages)
        btnCatDocs = findViewById(R.id.btnCatDocs)

        // 3-column grid for TV landscape
        rvFiles.layoutManager = GridLayoutManager(this, 3)
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
            }
        )
        rvFiles.adapter = adapter
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.ibBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.ibFolderUp).setOnClickListener { navigateUp() }
        findViewById<ImageButton>(R.id.ibNewFolder).setOnClickListener {
            FileManagerOperations.showNewFolderDialog(this, currentDirectory) {
                loadCurrentCategory()
            }
        }
        findViewById<ImageButton>(R.id.ibRefresh).setOnClickListener { loadCurrentCategory() }

        val categoryButtons = listOf(
            btnCatStorage to Category.STORAGE,
            btnCatDownloads to Category.DOWNLOADS,
            btnCatApks to Category.APKS,
            btnCatVideos to Category.VIDEOS,
            btnCatAudio to Category.AUDIO,
            btnCatImages to Category.IMAGES,
            btnCatDocs to Category.DOCS
        )

        for ((btn, cat) in categoryButtons) {
            btn.setOnClickListener {
                if (currentCategory != cat || cat == Category.STORAGE) {
                    currentCategory = cat
                    directoryStack.clear()
                    updateCategoryButtonsHighlight(btn)
                    loadCurrentCategory()
                }
            }
        }
    }

    private fun updateCategoryButtonsHighlight(selectedBtn: Button) {
        val buttons = listOf(btnCatStorage, btnCatDownloads, btnCatApks, btnCatVideos, btnCatAudio, btnCatImages, btnCatDocs)
        val selectedColor = ContextCompat.getColor(this, R.color.day_night_text_color_contrast)
        val unselectedColor = ContextCompat.getColor(this, R.color.day_night_text_secondary)
        for (btn in buttons) {
            if (btn == selectedBtn) {
                btn.setTextColor(selectedColor)
                btn.isSelected = true
            } else {
                btn.setTextColor(unselectedColor)
                btn.isSelected = false
            }
        }
    }

    private fun checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
                return
            }
        }
        loadCurrentCategory()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        loadCurrentCategory()
    }

    private fun updateStorageStats() {
        try {
            val root = Environment.getExternalStorageDirectory()
            val stat = StatFs(root.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize

            val df = java.text.DecimalFormat("#,##0.#")
            val freeGB = df.format(freeBytes.toDouble() / (1024 * 1024 * 1024))
            val totalGB = df.format(totalBytes.toDouble() / (1024 * 1024 * 1024))

            tvStorageStats.text = "Free: $freeGB GB / $totalGB GB"
        } catch (_: Exception) {
            tvStorageStats.text = "Storage"
        }
    }

    private fun loadCurrentCategory() {
        pbLoading.visibility = View.VISIBLE
        llEmptyView.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val filesList = when (currentCategory) {
                Category.STORAGE -> loadDirectoryFiles(currentDirectory)
                Category.DOWNLOADS -> {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    currentDirectory = downloadsDir
                    loadDirectoryFiles(downloadsDir)
                }
                Category.APKS -> scanFilesByExtensions(setOf("apk"))
                Category.VIDEOS -> scanFilesByExtensions(setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "flv", "3gp", "m4v"))
                Category.AUDIO -> scanFilesByExtensions(setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "wma", "opus"))
                Category.IMAGES -> scanFilesByExtensions(setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg"))
                Category.DOCS -> scanFilesByExtensions(setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "json", "zip", "rar", "7z"))
            }

            withContext(Dispatchers.Main) {
                pbLoading.visibility = View.GONE
                tvCurrentPath.text = if (currentCategory == Category.STORAGE || currentCategory == Category.DOWNLOADS) {
                    currentDirectory.absolutePath
                } else {
                    "Filtered: ${currentCategory.name}"
                }

                if (filesList.isEmpty()) {
                    llEmptyView.visibility = View.VISIBLE
                    adapter.updateItems(emptyList())
                } else {
                    llEmptyView.visibility = View.GONE
                    adapter.updateItems(filesList)
                }
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
            directoryStack.push(currentDirectory)
            currentDirectory = fileItem.file
            loadCurrentCategory()
        } else {
            FileManagerOperations.openFile(this, fileItem.file)
        }
    }

    private fun navigateUp() {
        if (currentCategory == Category.STORAGE && directoryStack.isNotEmpty()) {
            currentDirectory = directoryStack.pop()
            loadCurrentCategory()
        } else if (currentDirectory.parentFile != null && currentDirectory.parentFile?.canRead() == true) {
            currentDirectory = currentDirectory.parentFile!!
            loadCurrentCategory()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentCategory == Category.STORAGE && directoryStack.isNotEmpty()) {
            currentDirectory = directoryStack.pop()
            loadCurrentCategory()
        } else {
            super.onBackPressed()
        }
    }
}
