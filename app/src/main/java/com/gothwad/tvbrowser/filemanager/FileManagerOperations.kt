package com.gothwad.tvbrowser.filemanager

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.gothwad.tvbrowser.BuildConfig
import com.gothwad.tvbrowser.activity.main.openFileInNewTab
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object FileManagerOperations {

    fun getMimeType(file: File): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        if (ext == "apk") return "application/vnd.android.package-archive"
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mime ?: "*/*"
    }

    fun openFile(context: Context, file: File) {
        val ext = file.extension.lowercase(Locale.ROOT)
        when {
            FileViewerContentHelper.isPdf(ext) -> {
                if (context is android.app.Activity) {
                    PdfViewerDialog(context, file).show()
                } else {
                    InAppFileViewerActivity.start(context, file.absolutePath)
                }
            }
            FileViewerContentHelper.isArchive(ext) -> {
                if (ext == "apk") {
                    showApkChoiceDialog(context, file)
                } else if (context is android.app.Activity) {
                    ZipViewerDialog(context, file).show()
                } else {
                    InAppFileViewerActivity.start(context, file.absolutePath)
                }
            }
            FileViewerContentHelper.isMarkdown(ext) ||
            FileViewerContentHelper.isCodeFile(ext) ||
            FileViewerContentHelper.isImage(ext) ||
            FileViewerContentHelper.isMedia(ext) -> {
                if (context is com.gothwad.tvbrowser.activity.main.MainActivity) {
                    context.openFileInNewTab(file)
                } else {
                    InAppFileViewerActivity.start(context, file.absolutePath)
                }
            }
            else -> {
                openFileExternal(context, file)
            }
        }
    }

    fun showApkChoiceDialog(context: Context, file: File) {
        if (context !is android.app.Activity) {
            openFileExternal(context, file)
            return
        }

        AlertDialog.Builder(context)
            .setTitle(file.name)
            .setMessage("Choose action for this Android Package:")
            .setPositiveButton("Install APK") { _, _ ->
                openFileExternal(context, file)
            }
            .setNeutralButton("Explore Contents") { _, _ ->
                ZipViewerDialog(context, file).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun openFileExternal(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
            val mimeType = getMimeType(file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No external app found to handle this file", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun showFileOptionsDialog(
        context: Context,
        item: FileItem,
        onOpen: (FileItem) -> Unit,
        onRefresh: () -> Unit
    ) {
        val options = mutableListOf<String>()
        val ext = item.extension.lowercase(Locale.ROOT)

        if (item.isDirectory) {
            options.add("Open Folder")
        } else if (ext == "apk") {
            options.add("Install APK")
            options.add("Explore APK Contents")
        } else if (FileViewerContentHelper.isArchive(ext)) {
            options.add("Explore Archive")
            options.add("Extract Archive")
        } else if (FileViewerContentHelper.isPdf(ext)) {
            options.add("View PDF")
        } else {
            options.add("Open in Browser / Viewer")
        }

        if (!item.isDirectory) {
            options.add("Open with External App")
        }

        options.add("Rename")
        options.add("Delete")
        options.add("Details")
        if (!item.isDirectory) options.add("Share")

        AlertDialog.Builder(context)
            .setTitle(item.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Open Folder", "View PDF", "Open in Browser / Viewer" -> onOpen(item)
                    "Install APK" -> openFileExternal(context, item.file)
                    "Explore APK Contents", "Explore Archive" -> {
                        if (context is android.app.Activity) {
                            ZipViewerDialog(context, item.file).show()
                        }
                    }
                    "Extract Archive" -> {
                        if (context is android.app.Activity) {
                            ZipViewerDialog(context, item.file).show()
                        }
                    }
                    "Open with External App" -> openFileExternal(context, item.file)
                    "Rename" -> showRenameDialog(context, item, onRefresh)
                    "Delete" -> showDeleteDialog(context, item, onRefresh)
                    "Details" -> showDetailsDialog(context, item)
                    "Share" -> shareFile(context, item.file)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showRenameDialog(context: Context, item: FileItem, onRefresh: () -> Unit) {
        val etInput = EditText(context).apply {
            setText(item.name)
            setSelection(item.name.length)
        }

        AlertDialog.Builder(context)
            .setTitle("Rename")
            .setView(etInput)
            .setPositiveButton("Rename") { _, _ ->
                val newName = etInput.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    val target = File(item.file.parentFile, newName)
                    if (item.file.renameTo(target)) {
                        Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                        onRefresh()
                    } else {
                        Toast.makeText(context, "Failed to rename", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showDeleteDialog(context: Context, item: FileItem, onRefresh: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete '${item.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                if (deleteRecursive(item.file)) {
                    Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
                    onRefresh()
                } else {
                    Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }

    fun showDetailsDialog(context: Context, item: FileItem) {
        val details = """
            Name: ${item.name}
            Path: ${item.file.absolutePath}
            Size: ${item.formattedSize}
            Last Modified: ${item.formattedDate}
            Type: ${if (item.isDirectory) "Folder" else item.extension.uppercase(Locale.ROOT)}
            Writable: ${item.file.canWrite()}
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("File Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    fun showNewFolderDialog(context: Context, currentDirectory: File, onRefresh: () -> Unit) {
        val etInput = EditText(context).apply {
            hint = "Folder Name (e.g. My Videos)"
        }

        AlertDialog.Builder(context)
            .setTitle("New Folder")
            .setView(etInput)
            .setPositiveButton("Create") { _, _ ->
                val folderName = etInput.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    val newDir = File(currentDirectory, folderName)
                    if (newDir.mkdir()) {
                        Toast.makeText(context, "Folder created", Toast.LENGTH_SHORT).show()
                        onRefresh()
                    } else {
                        Toast.makeText(context, "Failed to create folder (Check permissions)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showNewFileDialog(context: Context, currentDirectory: File, onRefresh: () -> Unit) {
        val etInput = EditText(context).apply {
            hint = "File Name (e.g. note.txt)"
        }

        AlertDialog.Builder(context)
            .setTitle("New File")
            .setView(etInput)
            .setPositiveButton("Create") { _, _ ->
                val fileName = etInput.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    val newFile = File(currentDirectory, fileName)
                    try {
                        if (newFile.createNewFile()) {
                            Toast.makeText(context, "File created", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        } else {
                            Toast.makeText(context, "File already exists or failed to create", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to create file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
