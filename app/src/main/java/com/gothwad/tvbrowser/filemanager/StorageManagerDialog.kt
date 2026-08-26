package com.gothwad.tvbrowser.filemanager

import android.app.Dialog
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.gothwad.tvbrowser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.util.Locale

class StorageManagerDialog(private val context: Context) {

    fun show() {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_storage_manager, null)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            (560 * context.resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<ImageButton>(R.id.btnCloseStorageDialog).setOnClickListener {
            dialog.dismiss()
        }

        val tvInternalStatsText: TextView = view.findViewById(R.id.tvInternalStatsText)
        val pbInternalStorage: ProgressBar = view.findViewById(R.id.pbInternalStorage)
        val tvSystemStatsText: TextView = view.findViewById(R.id.tvSystemStatsText)
        val pbSystemStorage: ProgressBar = view.findViewById(R.id.pbSystemStorage)

        val tvStatApks: TextView = view.findViewById(R.id.tvStatApks)
        val tvStatVideos: TextView = view.findViewById(R.id.tvStatVideos)
        val tvStatAudio: TextView = view.findViewById(R.id.tvStatAudio)
        val tvStatImages: TextView = view.findViewById(R.id.tvStatImages)
        val tvStatDocs: TextView = view.findViewById(R.id.tvStatDocs)

        // 1. Internal Storage (ROM User data partition)
        try {
            val internalRoot = Environment.getExternalStorageDirectory()
            val stat = StatFs(internalRoot.path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val usedBytes = totalBytes - freeBytes

            val df = DecimalFormat("#,##0.#")
            val usedGB = df.format(usedBytes.toDouble() / (1024 * 1024 * 1024))
            val totalGB = df.format(totalBytes.toDouble() / (1024 * 1024 * 1024))
            val freeGB = df.format(freeBytes.toDouble() / (1024 * 1024 * 1024))

            val percent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0
            pbInternalStorage.progress = percent
            tvInternalStatsText.text = "Used: $usedGB GB / $totalGB GB ($percent% used • Free: $freeGB GB)"
        } catch (e: Exception) {
            tvInternalStatsText.text = "Internal Storage: Available"
        }

        // 2. System Root Storage (/system root partition)
        try {
            val rootDir = File("/system")
            val stat = StatFs(rootDir.path)
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val usedBytes = totalBytes - freeBytes

            val df = DecimalFormat("#,##0.#")
            val usedGB = df.format(usedBytes.toDouble() / (1024 * 1024 * 1024))
            val totalGB = df.format(totalBytes.toDouble() / (1024 * 1024 * 1024))

            val percent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 85
            pbSystemStorage.progress = percent
            tvSystemStatsText.text = "Root partition (/system): Used $usedGB GB / $totalGB GB ($percent%)\n⚠️ Protected System ROM partition (Write-protected without root privileges)"
        } catch (e: Exception) {
            tvSystemStatsText.text = "Root partition: Read-only Android System (Write-protected)"
        }

        // 3. Calculate category breakdown asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            var apkBytes = 0L; var apkCount = 0
            var videoBytes = 0L; var videoCount = 0
            var audioBytes = 0L; var audioCount = 0
            var imageBytes = 0L; var imageCount = 0
            var docBytes = 0L; var docCount = 0

            val apkExts = setOf("apk")
            val videoExts = setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "flv", "3gp", "m4v")
            val audioExts = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "wma", "opus")
            val imgExts = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg")
            val docExts = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "json", "zip", "rar", "7z")

            fun scanDir(dir: File, depth: Int) {
                if (depth > 4 || !dir.exists() || !dir.canRead()) return
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (f.name.startsWith(".")) continue
                    if (f.isDirectory) {
                        if (!f.name.equals("Android", ignoreCase = true)) {
                            scanDir(f, depth + 1)
                        }
                    } else {
                        val ext = f.extension.lowercase(Locale.ROOT)
                        val len = f.length()
                        when {
                            apkExts.contains(ext) -> { apkBytes += len; apkCount++ }
                            videoExts.contains(ext) -> { videoBytes += len; videoCount++ }
                            audioExts.contains(ext) -> { audioBytes += len; audioCount++ }
                            imgExts.contains(ext) -> { imageBytes += len; imageCount++ }
                            docExts.contains(ext) -> { docBytes += len; docCount++ }
                        }
                    }
                }
            }

            val storageDir = Environment.getExternalStorageDirectory()
            scanDir(storageDir, 0)

            fun formatBytes(bytes: Long): String {
                val df = DecimalFormat("#,##0.#")
                return when {
                    bytes >= 1024 * 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
                    bytes >= 1024 * 1024 -> "${df.format(bytes.toDouble() / (1024 * 1024))} MB"
                    bytes >= 1024 -> "${df.format(bytes.toDouble() / 1024)} KB"
                    else -> "$bytes B"
                }
            }

            withContext(Dispatchers.Main) {
                tvStatApks.text = "$apkCount items • ${formatBytes(apkBytes)}"
                tvStatVideos.text = "$videoCount items • ${formatBytes(videoBytes)}"
                tvStatAudio.text = "$audioCount items • ${formatBytes(audioBytes)}"
                tvStatImages.text = "$imageCount items • ${formatBytes(imageBytes)}"
                tvStatDocs.text = "$docCount items • ${formatBytes(docBytes)}"
            }
        }

        dialog.show()
    }
}
