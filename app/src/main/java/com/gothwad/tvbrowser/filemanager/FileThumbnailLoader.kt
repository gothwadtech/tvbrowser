package com.gothwad.tvbrowser.filemanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object FileThumbnailLoader {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val memoryCache = object : LruCache<String, Drawable>(cacheSize) {
        override fun sizeOf(key: String, value: Drawable): Int {
            return if (value is BitmapDrawable && value.bitmap != null) {
                value.bitmap.byteCount / 1024
            } else {
                32
            }
        }
    }

    private val loaderScope = CoroutineScope(Dispatchers.IO + Job())

    fun getCached(path: String): Drawable? {
        return memoryCache.get(path)
    }

    fun loadThumbnail(
        context: Context,
        item: FileItem,
        onLoaded: (Drawable) -> Unit
    ) {
        val path = item.file.absolutePath
        val cached = memoryCache.get(path)
        if (cached != null) {
            onLoaded(cached)
            return
        }

        val ext = item.extension.lowercase(Locale.ROOT)
        val isImage = ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
        val isVideo = ext in setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "flv", "3gp", "m4v")
        val isApk = ext == "apk"

        if (!isImage && !isVideo && !isApk) {
            return
        }

        loaderScope.launch {
            val drawable: Drawable? = try {
                when {
                    isImage -> decodeImageThumbnail(context, item.file)
                    isVideo -> decodeVideoThumbnail(context, item.file)
                    isApk -> decodeApkIcon(context, item.file)
                    else -> null
                }
            } catch (_: Throwable) {
                null
            }

            if (drawable != null) {
                memoryCache.put(path, drawable)
                withContext(Dispatchers.Main) {
                    onLoaded(drawable)
                }
            }
        }
    }

    private fun decodeImageThumbnail(context: Context, file: File): Drawable? {
        if (!file.exists() || !file.canRead()) return null
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return null

        val targetSize = 140
        var sampleSize = 1
        while ((origW / sampleSize) > targetSize * 2 && (origH / sampleSize) > targetSize * 2) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun decodeVideoThumbnail(context: Context, file: File): Drawable? {
        if (!file.exists() || !file.canRead()) return null
        val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ThumbnailUtils.createVideoThumbnail(file, Size(140, 140), null)
            } catch (_: Throwable) {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
            }
        } else {
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
        }
        return bitmap?.let { BitmapDrawable(context.resources, it) }
    }

    private fun decodeApkIcon(context: Context, file: File): Drawable? {
        if (!file.exists() || !file.canRead()) return null
        val pm = context.packageManager
        val pi = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val appInfo = pi.applicationInfo ?: return null
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath
        return appInfo.loadIcon(pm)
    }
}
