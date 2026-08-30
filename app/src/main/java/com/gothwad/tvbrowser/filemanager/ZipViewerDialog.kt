package com.gothwad.tvbrowser.filemanager

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ZipViewerDialog(
    private val activity: Activity,
    private val archiveFile: File
) : Dialog(activity, R.style.Theme_Dialog_Fullscreen) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var allEntries = listOf<ZipEntryInfo>()
    private var currentFolder = ""

    private lateinit var tvTitle: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvCurrentPath: TextView
    private lateinit var btnUp: ImageButton
    private lateinit var btnExtractAll: Button
    private lateinit var btnInstallApk: Button
    private lateinit var rvEntries: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var llEmpty: LinearLayout
    private lateinit var adapter: ZipEntryAdapter

    data class ZipEntryInfo(
        val fullPath: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val compressedSize: Long,
        val parentPath: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_zip_viewer)
        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

        initViews()
        loadArchive()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvZipTitle)
        tvStats = findViewById(R.id.tvZipStats)
        tvCurrentPath = findViewById(R.id.tvZipCurrentPath)
        btnUp = findViewById(R.id.btnZipUp)
        btnExtractAll = findViewById(R.id.btnZipExtractAll)
        btnInstallApk = findViewById(R.id.btnZipInstallApk)
        rvEntries = findViewById(R.id.rvZipEntries)
        pbLoading = findViewById(R.id.pbZipLoading)
        llEmpty = findViewById(R.id.llZipEmpty)

        tvTitle.text = archiveFile.name
        val isApk = archiveFile.extension.equals("apk", ignoreCase = true)
        if (isApk) {
            btnInstallApk.visibility = View.VISIBLE
            btnInstallApk.setOnClickListener {
                FileManagerOperations.openFile(activity, archiveFile)
            }
        }

        findViewById<ImageButton>(R.id.btnZipClose).setOnClickListener {
            dismiss()
        }

        btnUp.setOnClickListener {
            navigateUp()
        }

        btnExtractAll.setOnClickListener {
            extractAllArchive()
        }

        rvEntries.layoutManager = LinearLayoutManager(activity)
        adapter = ZipEntryAdapter()
        rvEntries.adapter = adapter
    }

    private fun loadArchive() {
        pbLoading.visibility = View.VISIBLE
        scope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    readZipEntries(archiveFile)
                }
                allEntries = entries
                pbLoading.visibility = View.GONE

                val totalCount = entries.size
                val totalUncompressed = entries.sumOf { it.size }
                val totalCompressed = entries.sumOf { it.compressedSize }

                tvStats.text = "$totalCount items • ${FileViewerContentHelper.formatFileSize(totalUncompressed)} (Compressed: ${FileViewerContentHelper.formatFileSize(totalCompressed)})"

                updateFolderView("")
            } catch (e: Exception) {
                pbLoading.visibility = View.GONE
                Toast.makeText(activity, "Failed to read archive: ${e.message}", Toast.LENGTH_LONG).show()
                dismiss()
            }
        }
    }

    private fun readZipEntries(file: File): List<ZipEntryInfo> {
        val list = mutableListOf<ZipEntryInfo>()
        val seenDirs = mutableSetOf<String>()
        val zip = ZipFile(file)
        try {
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                var path = entry.name.replace('\\', '/')
                val isDir = entry.isDirectory || path.endsWith('/')
                if (path.endsWith('/')) {
                    path = path.substring(0, path.length - 1)
                }
                if (path.isEmpty()) continue

                val lastSlash = path.lastIndexOf('/')
                val parent = if (lastSlash >= 0) path.substring(0, lastSlash) else ""
                val name = if (lastSlash >= 0) path.substring(lastSlash + 1) else path

                // Ensure parent directories are also recorded
                var currentParent = parent
                while (currentParent.isNotEmpty() && !seenDirs.contains(currentParent)) {
                    seenDirs.add(currentParent)
                    val pSlash = currentParent.lastIndexOf('/')
                    val pParent = if (pSlash >= 0) currentParent.substring(0, pSlash) else ""
                    val pName = if (pSlash >= 0) currentParent.substring(pSlash + 1) else currentParent
                    list.add(ZipEntryInfo(currentParent, pName, true, 0, 0, pParent))
                    currentParent = pParent
                }

                if (isDir) {
                    if (!seenDirs.contains(path)) {
                        seenDirs.add(path)
                        list.add(ZipEntryInfo(path, name, true, 0, 0, parent))
                    }
                } else {
                    list.add(ZipEntryInfo(path, name, false, entry.size.coerceAtLeast(0), entry.compressedSize.coerceAtLeast(0), parent))
                }
            }
        } finally {
            zip.close()
        }
        return list
    }

    private fun updateFolderView(folderPath: String) {
        currentFolder = folderPath
        tvCurrentPath.text = if (folderPath.isEmpty()) "/" else "/$folderPath"
        btnUp.isEnabled = folderPath.isNotEmpty()
        btnUp.alpha = if (folderPath.isNotEmpty()) 1.0f else 0.4f

        val currentItems = allEntries.filter { it.parentPath == folderPath }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))

        adapter.setItems(currentItems)
        llEmpty.visibility = if (currentItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun navigateUp() {
        if (currentFolder.isEmpty()) return
        val lastSlash = currentFolder.lastIndexOf('/')
        val parent = if (lastSlash >= 0) currentFolder.substring(0, lastSlash) else ""
        updateFolderView(parent)
    }

    private fun onEntryClicked(entry: ZipEntryInfo) {
        if (entry.isDirectory) {
            updateFolderView(entry.fullPath)
        } else {
            previewEntry(entry)
        }
    }

    private fun previewEntry(entry: ZipEntryInfo) {
        scope.launch {
            val progressDialog = ProgressDialog(activity).apply {
                setMessage("Extracting ${entry.name}...")
                setCancelable(false)
                show()
            }

            try {
                val tempFile = withContext(Dispatchers.IO) {
                    val previewDir = File(activity.cacheDir, "archive_preview").apply { mkdirs() }
                    val target = File(previewDir, entry.name)
                    val zip = ZipFile(archiveFile)
                    try {
                        val zipEntry = zip.getEntry(entry.fullPath) ?: zip.getEntry(entry.fullPath + "/")
                        if (zipEntry != null) {
                            zip.getInputStream(zipEntry).use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    } finally {
                        zip.close()
                    }
                    target
                }

                progressDialog.dismiss()

                if (tempFile.exists() && tempFile.length() > 0) {
                    FileManagerOperations.openFile(activity, tempFile)
                } else {
                    Toast.makeText(activity, "Failed to preview ${entry.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(activity, "Error previewing file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun extractAllArchive() {
        val defaultExtractDir = File(archiveFile.parentFile ?: activity.cacheDir, "Extracted_${archiveFile.nameWithoutExtension}")

        AlertDialog.Builder(activity)
            .setTitle("Extract Archive")
            .setMessage("Extract all files to:\n${defaultExtractDir.absolutePath}")
            .setPositiveButton("Extract") { _, _ ->
                performExtraction(defaultExtractDir)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performExtraction(targetDir: File) {
        val progressDialog = ProgressDialog(activity).apply {
            setTitle("Extracting Archive")
            setMessage("Preparing...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = allEntries.count { !it.isDirectory }
            isIndeterminate = false
            setCancelable(false)
            show()
        }

        scope.launch {
            var extractedCount = 0
            var errorMessage: String? = null

            withContext(Dispatchers.IO) {
                try {
                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }
                    val zip = ZipFile(archiveFile)
                    val entries = zip.entries()
                    val buffer = ByteArray(16 * 1024)

                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val destFile = File(targetDir, entry.name)

                        if (entry.isDirectory) {
                            destFile.mkdirs()
                            continue
                        }

                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(destFile).use { output ->
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                        }

                        extractedCount++
                        withContext(Dispatchers.Main) {
                            progressDialog.progress = extractedCount
                            progressDialog.setMessage("Extracting ${entry.name}")
                        }
                    }
                    zip.close()
                } catch (e: Exception) {
                    errorMessage = e.message
                }
            }

            progressDialog.dismiss()

            if (errorMessage != null) {
                Toast.makeText(activity, "Extraction failed: $errorMessage", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(activity, "Extracted $extractedCount files to ${targetDir.name}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    private inner class ZipEntryAdapter : RecyclerView.Adapter<ZipEntryAdapter.ZipEntryViewHolder>() {
        private var items = listOf<ZipEntryInfo>()

        fun setItems(newItems: List<ZipEntryInfo>) {
            items = newItems
            notifyDataSetChanged() // Full list refresh for subfolder switch
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ZipEntryViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_zip_entry, parent, false)
            return ZipEntryViewHolder(view)
        }

        override fun onBindViewHolder(holder: ZipEntryViewHolder, position: Int) {
            holder.bind(items[position])
        }

        inner class ZipEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivIcon: ImageView = itemView.findViewById(R.id.ivZipItemIcon)
            private val tvName: TextView = itemView.findViewById(R.id.tvZipItemName)
            private val tvDetails: TextView = itemView.findViewById(R.id.tvZipItemDetails)
            private val ivArrow: ImageView = itemView.findViewById(R.id.ivZipItemArrow)

            fun bind(entry: ZipEntryInfo) {
                tvName.text = entry.name

                if (entry.isDirectory) {
                    ivIcon.setImageResource(R.drawable.ic_folder)
                    ivIcon.imageTintList = null
                    tvDetails.text = "Folder"
                    ivArrow.visibility = View.VISIBLE
                } else {
                    val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    ivIcon.setImageResource(getFileIconRes(ext))
                    val sizeStr = FileViewerContentHelper.formatFileSize(entry.size)
                    val compSizeStr = FileViewerContentHelper.formatFileSize(entry.compressedSize)
                    val ratio = if (entry.size > 0) ((1.0 - (entry.compressedSize.toDouble() / entry.size)) * 100).toInt().coerceIn(0, 99) else 0
                    tvDetails.text = "$sizeStr • Comp: $compSizeStr ($ratio% saved)"
                    ivArrow.visibility = View.GONE
                }

                itemView.setOnClickListener {
                    onEntryClicked(entry)
                }
            }

            private fun getFileIconRes(ext: String): Int {
                return when {
                    FileViewerContentHelper.isImage(ext) -> R.drawable.ic_file_image
                    FileViewerContentHelper.isMedia(ext) -> R.drawable.ic_file_video
                    FileViewerContentHelper.isPdf(ext) -> R.drawable.ic_file_doc
                    FileViewerContentHelper.isArchive(ext) -> R.drawable.ic_file_zip
                    FileViewerContentHelper.isCodeFile(ext) -> R.drawable.ic_file_doc
                    else -> R.drawable.ic_file_generic
                }
            }
        }
    }
}
