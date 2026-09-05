package com.gothwad.tvbrowser.filemanager

import com.gothwad.tvbrowser.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val childCount: Int = -1,
    val isSelected: Boolean = false
) {
    val extension: String = if (isDirectory) "" else file.extension.lowercase(Locale.ROOT)

    val formattedSize: String
        get() {
            if (isDirectory) {
                return if (childCount >= 0) "$childCount items" else "Folder"
            }
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            val df = java.text.DecimalFormat("#,##0.#")
            return "${df.format(size / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    val iconRes: Int
        get() {
            if (isDirectory) return R.drawable.ic_folder
            return when (extension) {
                "apk" -> R.drawable.ic_file_apk
                "mp4", "mkv", "avi", "mov", "webm", "ts", "flv", "3gp", "m4v" -> R.drawable.ic_file_video
                "mp3", "m4a", "aac", "flac", "wav", "ogg", "wma", "opus" -> R.drawable.ic_file_audio
                "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg" -> R.drawable.ic_file_image
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "json", "xml", "html" -> R.drawable.ic_file_doc
                "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso" -> R.drawable.ic_file_zip
                else -> R.drawable.ic_file_generic
            }
        }
}
