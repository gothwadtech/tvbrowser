package com.gothwad.tvbrowser.singleton

import android.content.Context
import android.content.SharedPreferences

object AppLockManager {
    private const val PREFS_NAME = "tv_browser_app_lock_prefs"
    private const val KEY_LOCK_ENABLED = "app_lock_enabled"
    private const val KEY_PIN_HASH = "app_lock_pin"

    private var isSessionUnlocked = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isLockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LOCK_ENABLED, false)
    }

    fun setLockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
        if (!enabled) {
            isSessionUnlocked = true
        }
    }

    fun getPin(context: Context): String {
        return getPrefs(context).getString(KEY_PIN_HASH, "0000") ?: "0000"
    }

    fun setPin(context: Context, pin: String) {
        getPrefs(context).edit().putString(KEY_PIN_HASH, pin).apply()
    }

    fun verifyPin(context: Context, inputPin: String): Boolean {
        val currentPin = getPin(context)
        return inputPin == currentPin
    }

    fun isSessionUnlocked(): Boolean = isSessionUnlocked

    fun setSessionUnlocked(unlocked: Boolean) {
        isSessionUnlocked = unlocked
    }

    fun requiresUnlock(context: Context): Boolean {
        return isLockEnabled(context) && !isSessionUnlocked
    }
}
