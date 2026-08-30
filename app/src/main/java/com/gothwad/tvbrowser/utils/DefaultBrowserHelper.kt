package com.gothwad.tvbrowser.utils

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object DefaultBrowserHelper {

    fun isDefaultBrowser(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
                }
            }
            val testIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            val resolveInfo = context.packageManager.resolveActivity(testIntent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    fun requestSetDefaultBrowser(context: Context) {
        // 1. If Android 10+ (API 29+), use RoleManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    Toast.makeText(context, "Gothwad Browser is already your default browser", Toast.LENGTH_SHORT).show()
                    return
                }
                try {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    if (context !is Activity) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    // Fallthrough to standard settings
                }
            }
        }

        // 2. Try Default Apps Settings (Android 7+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
                Toast.makeText(context, "Select 'Browser app' -> choose 'Gothwad Browser'", Toast.LENGTH_LONG).show()
                return
            } catch (e: Exception) {
                // Fallthrough to app details
            }
        }

        // 3. Fallback to App Details Settings
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            Toast.makeText(context, "Set Gothwad Browser as default browser in App Settings", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open system settings", Toast.LENGTH_SHORT).show()
        }
    }
}
