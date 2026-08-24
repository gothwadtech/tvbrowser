package com.gothwad.tvbrowser.activity.downloads

import android.net.Uri
import android.os.Build
import android.util.Log
import com.gothwad.tvbrowser.BrowserApp
import com.gothwad.tvbrowser.model.Download
import com.gothwad.tvbrowser.service.downloads.DownloadTask
import com.gothwad.tvbrowser.service.downloads.FileDownloadTask
import com.gothwad.tvbrowser.singleton.AppDatabase
import com.gothwad.tvbrowser.utils.observable.ObservableList
import com.gothwad.tvbrowser.utils.activemodel.ActiveModel
import java.io.File

class ActiveDownloadsModel: ActiveModel() {
    val activeDownloads = ObservableList<DownloadTask>()
    private val listeners = java.util.ArrayList<Listener>()

    interface Listener {
        fun onDownloadUpdated(downloadInfo: Download)
        fun onDownloadError(downloadInfo: Download, responseCode: Int, responseMessage: String)
        fun onAllDownloadsComplete()
    }

    suspend fun deleteItem(download: Download) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contentResolver = BrowserApp.instance.contentResolver
            val rowsDeleted = try {
                contentResolver.delete(Uri.parse(download.filepath), null)
            } catch (e: Exception) {
                Log.w(FileDownloadTask.TAG, "Failed to delete file from MediaStore", e)
                0
            }
            if (rowsDeleted < 1) {
                Log.e(FileDownloadTask.TAG, "Failed to delete file from MediaStore")
            }
        } else {
            File(download.filepath).delete()
        }
        AppDatabase.db.downloadDao().delete(download)
    }

    fun registerListener(listener: Listener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun cancelDownload(download: Download) {
        for (i in activeDownloads.indices) {
            val task = activeDownloads[i]
            if (task.downloadInfo.id == download.id) {
                task.downloadInfo.cancelled = true
                break
            }
        }
    }

    fun notifyListenersAboutError(task: DownloadTask, responseCode: Int, responseMessage: String) {
        for (i in listeners.indices) {
            listeners[i].onDownloadError(task.downloadInfo, responseCode, responseMessage)
        }
    }

    fun notifyListenersAboutDownloadProgress(task: DownloadTask) {
        for (i in listeners.indices) {
            listeners[i].onDownloadUpdated(task.downloadInfo)
        }
    }

    fun onDownloadEnded(task: DownloadTask) {
        activeDownloads.remove(task)
        if (activeDownloads.isEmpty()) {
            for (i in listeners.indices) {
                listeners[i].onAllDownloadsComplete()
            }
        }
    }
}