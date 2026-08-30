package com.gothwad.tvbrowser

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.gothwad.tvbrowser.activity.IncognitoModeMainActivity
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.model.HostConfig
import com.gothwad.tvbrowser.notes.clipboard.ClipboardRepository
import com.gothwad.tvbrowser.service.keepalive.BrowserKeepAliveService
import com.gothwad.tvbrowser.singleton.AppDatabase
import com.gothwad.tvbrowser.singleton.FaviconsPool
import com.gothwad.tvbrowser.utils.activemodel.ActiveModelsRepository
import com.gothwad.tvbrowser.webengine.webview.WebViewWebEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.net.CookieHandler
import java.net.CookieManager
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Created by PDT on 09.09.2016.
 */
class BrowserApp : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        lateinit var instance: BrowserApp
        const val CHANNEL_ID_DOWNLOADS: String = "downloads"
        const val MAIN_PREFS_NAME = "main.xml"
        val TAG = BrowserApp::class.simpleName
    }

    lateinit var threadPool: ThreadPoolExecutor
        private set

    var needToExitProcessAfterMainActivityFinish = false
    var needRestartMainActivityAfterExitingProcess = false

    var isAppInForeground: Boolean = false
        private set
    private var currentActivityRef: WeakReference<Activity>? = null

    val currentActivity: Activity?
        get() = currentActivityRef?.get()

    override fun onCreate() {
        Log.i(TAG, "onCreate")
        super.onCreate()

        instance = this

        AppContext.init(this, Config(getSharedPreferences(MAIN_PREFS_NAME, MODE_MULTI_PROCESS)))

        val maxThreadsInOfflineJobsPool = Runtime.getRuntime().availableProcessors()
        threadPool = ThreadPoolExecutor(0, maxThreadsInOfflineJobsPool, 20,
                TimeUnit.SECONDS, ArrayBlockingQueue(maxThreadsInOfflineJobsPool))

        initWebEngineStuff()

        initNotificationChannels()

        ActiveModelsRepository.init(this)

        when (AppContext.provideConfig().theme.value) {
            Config.Theme.BLACK_AMOLED,
            Config.Theme.BLACK_CHARCOAL,
            Config.Theme.BLACK_MIDNIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            Config.Theme.WHITE_PURE,
            Config.Theme.WHITE_WARM,
            Config.Theme.WHITE_COOL -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        registerActivityLifecycleCallbacks(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isAppInForeground = true
                Log.d(TAG, "Process entered foreground -> stopping keep-alive service")
                BrowserKeepAliveService.stop(this@BrowserApp)
            }

            override fun onStop(owner: LifecycleOwner) {
                isAppInForeground = false
                Log.d(TAG, "Process entered background -> starting keep-alive service")
                BrowserKeepAliveService.start(this@BrowserApp)
            }
        })

        initClipboardListener()
    }

    private fun initClipboardListener() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            clipboard.addPrimaryClipChangedListener {
                handlePrimaryClipChanged()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register OnPrimaryClipChangedListener: ${t.message}")
        }
    }

    private fun handlePrimaryClipChanged() {
        // Android 10+ background restriction & focus check
        if (!isAppInForeground) {
            return
        }

        // Respect incognito mode: do not record if active session is incognito
        if (isCurrentSessionIncognito()) {
            return
        }

        // Prevent feedback loops when the app's own code writes to clipboard
        if (ClipboardRepository.isInternalClipboardWrite) {
            return
        }

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount <= 0) return
            val clipItem = clip.getItemAt(0) ?: return
            val rawText = clipItem.coerceToText(this)?.toString() ?: return
            val text = rawText.trim()
            if (text.isEmpty()) return

            val now = SystemClock.uptimeMillis()
            if (text == ClipboardRepository.lastCopiedByAppText && (now - ClipboardRepository.lastCopiedByAppTime < 3000L)) {
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = ClipboardRepository(this@BrowserApp)
                    val allItems = repo.getAllItems()
                    val mostRecent = allItems.firstOrNull()?.text?.trim()
                    if (mostRecent != null && mostRecent == text) {
                        return@launch
                    }

                    repo.recordCopiedText(text)
                    Log.d(TAG, "Captured native text copy into ClipboardRepository (${text.take(30)}...)")
                } catch (t: Throwable) {
                    Log.e(TAG, "Error recording clipboard text: ${t.message}")
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Clipboard access denied (SecurityException): ${e.message}")
        } catch (t: Throwable) {
            Log.e(TAG, "Error processing primary clip changed: ${t.message}")
        }
    }

    fun isCurrentSessionIncognito(): Boolean {
        val act = currentActivity
        if (act is IncognitoModeMainActivity) return true
        if (act is MainActivity) {
            if (act.config.incognitoMode) return true
            if (act.tabsModel.currentTab.value?.incognito == true) return true
        }
        return AppContext.provideConfig().incognitoMode
    }

    @Suppress("KotlinConstantConditions")
    private fun initWebEngineStuff() {
        Log.i(TAG, "initWebEngineStuff")

        try {
            Class.forName("com.gothwad.tvbrowser.webengine.webview.WebViewWebEngine")
        } catch (e: ClassNotFoundException) {
            throw AssertionError(e) // WebViews are always available
        }

        val cookieManager = CookieManager()
        CookieHandler.setDefault(cookieManager)
        FaviconsPool.databaseDelegate = object : FaviconsPool.DatabaseDelegate {
            override fun findByHostName(host: String): HostConfig? {
                return AppDatabase.db.hostsDao().findByHostName(host)
            }

            override suspend fun update(hostConfig: HostConfig) {
                AppDatabase.db.hostsDao().update(hostConfig)
            }

            override suspend fun insert(newHostConfig: HostConfig) {
                AppDatabase.db.hostsDao().insert(newHostConfig)
            }
        }
    }

    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.downloads)
            val descriptionText = getString(R.string.downloads_notifications_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID_DOWNLOADS, name, importance)
            channel.description = descriptionText
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }
    override fun onActivityPaused(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        Log.i(TAG, "onActivityDestroyed: " + activity.javaClass.simpleName)
        if (needToExitProcessAfterMainActivityFinish && activity is MainActivity) {
            Log.i(TAG, "onActivityDestroyed: exiting process")
            if (needRestartMainActivityAfterExitingProcess) {
                Log.i(TAG, "onActivityDestroyed: restarting main activity")
                val intent = Intent(this@BrowserApp, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }
            exitProcess(0)
        }
    }
}
