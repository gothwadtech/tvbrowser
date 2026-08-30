package com.gothwad.tvbrowser.singleton

import android.content.Context
import android.content.SharedPreferences
import com.gothwad.tvbrowser.BrowserApp
import java.io.File

object AppLockManager {
    private const val PREFS_NAME = "tv_browser_app_lock_prefs"
    private const val KEY_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_PIN_HASH = "app_lock_pin"

    private const val FILE_PIN = "app_lock_pin.dat"
    private const val FILE_ENABLED = "app_lock_enabled.flag"
    private const val FILE_SESSION_TOKEN = "app_lock_session.token"

    @Volatile
    private var isSessionUnlocked = false

    private fun getFilesDir(context: Context? = null): File? {
        return try {
            context?.filesDir ?: BrowserApp.instance.filesDir
        } catch (_: Throwable) {
            null
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasPinSet(context: Context): Boolean {
        val pin = getPin(context)
        return !pin.isNullOrEmpty() && pin.length == 4 && pin.all { it.isDigit() }
    }

    fun isLockEnabled(context: Context): Boolean {
        if (!hasPinSet(context)) return false
        val enabledInPrefs = getPrefs(context).getBoolean(KEY_LOCK_ENABLED, false)
        if (enabledInPrefs) return true
        val flagFile = getFilesDir(context)?.let { File(it, FILE_ENABLED) }
        return flagFile?.exists() == true
    }

    fun setLockEnabled(context: Context, enabled: Boolean) {
        if (enabled && !hasPinSet(context)) {
            return
        }
        getPrefs(context).edit().putBoolean(KEY_LOCK_ENABLED, enabled).commit()
        val flagFile = getFilesDir(context)?.let { File(it, FILE_ENABLED) }
        try {
            if (enabled) {
                flagFile?.writeText("true")
            } else {
                flagFile?.delete()
                setSessionUnlocked(context, true)
            }
        } catch (_: Throwable) {}
    }

    fun getPin(context: Context): String? {
        val prefsPin = getPrefs(context).getString(KEY_PIN_HASH, null)
        if (!prefsPin.isNullOrEmpty()) return prefsPin
        val pinFile = getFilesDir(context)?.let { File(it, FILE_PIN) }
        if (pinFile?.exists() == true) {
            val filePin = try { pinFile.readText().trim() } catch (_: Throwable) { null }
            if (!filePin.isNullOrEmpty()) {
                getPrefs(context).edit().putString(KEY_PIN_HASH, filePin).commit()
                return filePin
            }
        }
        return null
    }

    fun setPin(context: Context, pin: String) {
        if (pin.length == 4 && pin.all { it.isDigit() }) {
            getPrefs(context).edit().putString(KEY_PIN_HASH, pin).commit()
            val pinFile = getFilesDir(context)?.let { File(it, FILE_PIN) }
            try {
                pinFile?.writeText(pin)
            } catch (_: Throwable) {}
        }
    }

    fun verifyPin(context: Context, inputPin: String): Boolean {
        val currentPin = getPin(context) ?: return false
        return inputPin == currentPin
    }

    fun clearPinAndDisableLock(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_LOCK_ENABLED, false)
            .commit()
        try {
            getFilesDir(context)?.let { dir ->
                File(dir, FILE_PIN).delete()
                File(dir, FILE_ENABLED).delete()
            }
        } catch (_: Throwable) {}
        setSessionUnlocked(context, true)
    }

    fun isSessionUnlocked(context: Context? = null): Boolean {
        if (isSessionUnlocked) return true
        val tokenFile = getFilesDir(context)?.let { File(it, FILE_SESSION_TOKEN) }
        if (tokenFile?.exists() == true) {
            isSessionUnlocked = true
            return true
        }
        return false
    }

    fun setSessionUnlocked(context: Context, unlocked: Boolean) {
        isSessionUnlocked = unlocked
        val tokenFile = getFilesDir(context)?.let { File(it, FILE_SESSION_TOKEN) }
        try {
            if (unlocked) {
                tokenFile?.writeText(System.currentTimeMillis().toString())
            } else {
                tokenFile?.delete()
            }
        } catch (_: Throwable) {}
    }

    fun setSessionUnlocked(unlocked: Boolean) {
        isSessionUnlocked = unlocked
        val tokenFile = getFilesDir()?.let { File(it, FILE_SESSION_TOKEN) }
        try {
            if (unlocked) {
                tokenFile?.writeText(System.currentTimeMillis().toString())
            } else {
                tokenFile?.delete()
            }
        } catch (_: Throwable) {}
    }

    fun requiresUnlock(context: Context): Boolean {
        return isLockEnabled(context) && !isSessionUnlocked(context)
    }
}

