package com.gothwad.tvbrowser.filemanager

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

object MediaStoreHelper {

    fun queryMediaCategory(
        context: Context,
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        limit: Int = 300
    ): List<FileItem> {
        val result = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC LIMIT $limit"

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            cursor?.use { c ->
                val dataIndex = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val sizeIndex = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateIndex = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

                while (c.moveToNext()) {
                    val path = if (dataIndex != -1) c.getString(dataIndex) else null
                    if (!path.isNullOrBlank()) {
                        val file = File(path)
                        if (file.exists() && !file.isDirectory) {
                            val size = if (sizeIndex != -1) c.getLong(sizeIndex) else file.length()
                            val mod = if (dateIndex != -1) c.getLong(dateIndex) * 1000L else file.lastModified()
                            result.add(
                                FileItem(
                                    file = file,
                                    name = file.name,
                                    isDirectory = false,
                                    size = size,
                                    lastModified = mod,
                                    childCount = -1
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        return result
    }

    fun loadImages(context: Context): List<FileItem> {
        return queryMediaCategory(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    }

    fun loadVideos(context: Context): List<FileItem> {
        return queryMediaCategory(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    }

    fun loadAudio(context: Context): List<FileItem> {
        return queryMediaCategory(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }

    fun loadApks(context: Context): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.MediaColumns.DATA} LIKE '%.apk'"
        return queryMediaCategory(context, uri, selection = selection)
    }

    fun loadDocuments(context: Context): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.MediaColumns.DATA} LIKE '%.pdf' OR " +
                "${MediaStore.MediaColumns.DATA} LIKE '%.doc' OR " +
                "${MediaStore.MediaColumns.DATA} LIKE '%.docx' OR " +
                "${MediaStore.MediaColumns.DATA} LIKE '%.xls' OR " +
                "${MediaStore.MediaColumns.DATA} LIKE '%.xlsx' OR " +
                "${MediaStore.MediaColumns.DATA} LIKE '%.ppt' OR " +
                "${MediaStore.MediaColumns.DATA} LIKE '%.pptx' OR " +
                "${MediaStore.MediaColumns.DATA} LIKE '%.txt' OR " +
                "${MediaStore.MediaColumns.DATA} LIKE '%.zip' OR " +
                "${MediaStore.MediaColumns.DATA} LIKE '%.rar'"
        return queryMediaCategory(context, uri, selection = selection)
    }

    fun loadRecentFiles(context: Context): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.MediaColumns.SIZE} > 0"
        return queryMediaCategory(context, uri, selection = selection, limit = 150)
    }
}
