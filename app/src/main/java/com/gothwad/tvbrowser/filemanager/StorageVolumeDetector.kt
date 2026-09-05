package com.gothwad.tvbrowser.filemanager

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

data class StoragePartition(
    val name: String,
    val path: File,
    val isRemovable: Boolean,
    val isUsb: Boolean
)

object StorageVolumeDetector {

    fun getStoragePartitions(context: Context): List<StoragePartition> {
        val partitions = mutableListOf<StoragePartition>()
        val seenPaths = mutableSetOf<String>()

        val primaryStorage = Environment.getExternalStorageDirectory()
        if (primaryStorage != null && primaryStorage.exists()) {
            seenPaths.add(primaryStorage.absolutePath)
        }

        // Method 1: StorageManager storageVolumes (API 24+)
        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                for (vol in sm.storageVolumes) {
                    val desc = vol.getDescription(context)
                    val isRemovable = vol.isRemovable
                    val file: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory
                    } else {
                        try {
                            val getPath = vol.javaClass.getMethod("getPath")
                            val p = getPath.invoke(vol) as? String
                            p?.let { File(it) }
                        } catch (_: Throwable) {
                            null
                        }
                    }

                    if (file != null && file.exists() && file.canRead()) {
                        val canonical = file.canonicalPath
                        if (!seenPaths.contains(canonical) && !canonical.contains("emulated/0")) {
                            seenPaths.add(canonical)
                            val isUsb = desc.lowercase(Locale.ROOT).contains("usb") ||
                                    canonical.lowercase(Locale.ROOT).contains("usb") ||
                                    canonical.lowercase(Locale.ROOT).contains("otg")
                            val label = if (desc.isNotBlank()) desc else (if (isUsb) "USB Drive" else "SD Card")
                            partitions.add(StoragePartition(label, file, isRemovable, isUsb))
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // Method 2: ContextCompat.getExternalFilesDirs
        try {
            val dirs = ContextCompat.getExternalFilesDirs(context, null)
            for (dir in dirs) {
                if (dir != null) {
                    val root = findMountPoint(dir)
                    if (root != null && root.exists() && root.canRead()) {
                        val canonical = root.canonicalPath
                        if (!seenPaths.contains(canonical) && !canonical.contains("emulated/0")) {
                            seenPaths.add(canonical)
                            val isUsb = canonical.lowercase(Locale.ROOT).contains("usb") ||
                                    canonical.lowercase(Locale.ROOT).contains("otg")
                            val label = if (isUsb) "USB Drive (${root.name})" else "External Storage (${root.name})"
                            partitions.add(StoragePartition(label, root, isRemovable = true, isUsb = isUsb))
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // Method 3: Check /storage directory directly
        try {
            val storageRoot = File("/storage")
            if (storageRoot.exists() && storageRoot.isDirectory) {
                val subFiles = storageRoot.listFiles()
                if (subFiles != null) {
                    for (f in subFiles) {
                        if (f.isDirectory && f.canRead() && !f.name.equals("emulated", ignoreCase = true) && !f.name.equals("self", ignoreCase = true)) {
                            val canonical = f.canonicalPath
                            if (!seenPaths.contains(canonical)) {
                                seenPaths.add(canonical)
                                val isUsb = canonical.lowercase(Locale.ROOT).contains("usb") ||
                                        canonical.lowercase(Locale.ROOT).contains("otg")
                                val label = if (isUsb) "USB Drive (${f.name})" else "Storage (${f.name})"
                                partitions.add(StoragePartition(label, f, isRemovable = true, isUsb = isUsb))
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        return partitions
    }

    private fun findMountPoint(file: File): File? {
        var current: File? = file
        while (current != null && current.parentFile != null) {
            val parent = current.parentFile ?: break
            if (parent.absolutePath == "/storage" || parent.absolutePath == "/mnt" || parent.absolutePath == "/mnt/media_rw") {
                return current
            }
            current = parent
        }
        return null
    }
}
