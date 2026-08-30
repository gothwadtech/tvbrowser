package com.gothwad.tvbrowser.activity.main.dialogs

import android.app.Activity
import android.view.View
import com.gothwad.tvbrowser.R

object SidebarHelper {
    /**
     * Calculates the uniform horizontal width for all sidebar menus (Settings, Downloads,
     * File Manager, Tabs, Notes, Clipboard, Browser Menu, Bookmarks, History, etc.).
     * The width is anchored dynamically to header geometry, extending from the starting edge
     * of the microphone/voice search icon inside the address bar to the edge of the screen.
     */
    fun calculateSidebarWidth(activity: Activity): Int {
        val decorView = activity.window.decorView
        val screenWidth = if (decorView.width > 0) decorView.width else activity.resources.displayMetrics.widthPixels
        val micIcon = activity.findViewById<View>(R.id.ibVoiceSearch)
        val searchBar = activity.findViewById<View>(R.id.vActionBar)
        val dynamicWidth: Int = if (micIcon != null && micIcon.width > 0) {
            val micLoc = IntArray(2)
            micIcon.getLocationInWindow(micLoc)
            val micStart = micLoc[0]
            (screenWidth - micStart).coerceIn(240, (screenWidth * 0.60f).toInt())
        } else if (searchBar != null && searchBar.width > 0) {
            val searchLoc = IntArray(2)
            searchBar.getLocationInWindow(searchLoc)
            val micOffset = (34 * activity.resources.displayMetrics.density).toInt()
            val estimatedMicStart = (searchLoc[0] + searchBar.width - micOffset).coerceAtLeast(searchLoc[0])
            (screenWidth - estimatedMicStart).coerceIn(240, (screenWidth * 0.60f).toInt())
        } else {
            (screenWidth * 0.32f).toInt().coerceIn(280, 560)
        }
        return dynamicWidth
    }
}
