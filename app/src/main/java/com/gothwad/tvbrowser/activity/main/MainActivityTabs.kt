package com.gothwad.tvbrowser.activity.main

import android.content.Intent
import com.gothwad.tvbrowser.browser.tabs.TabsRowDialog
import com.gothwad.tvbrowser.model.WebTabState

fun MainActivity.showTabsRowDialog() {
    val existing = currentTabsDialog
    if (existing?.isShowing == true) return

    val dialog = TabsRowDialog(
        activity = this,
        onTabSelected = { tab ->
            switchToTab(tab)
        },
        onNewTabRequested = {
            openInNewTab(settingsModel.homePage, tabsModel.tabsStates.size, needToHideMenuOverlay = false, navigateImmediately = true)
        },
        onCloseTabRequested = { tab ->
            closeTab(tab)
        },
        onCloseAllTabsRequested = {
            closeAllTabs()
        }
    )
    currentTabsDialog = dialog
    dialog.show()
}

fun MainActivity.updateTabCountBadge() {
    val count = tabsModel.tabsStates.size
    vb.tvTabCountBadge.text = if (count > 0) count.toString() else "1"
}

fun MainActivity.closeAllTabs() {
    val tabsToClose = tabsModel.tabsStates.toList()
    for (tab in tabsToClose) {
        closeTab(tab)
    }
    openInNewTab(settingsModel.homePage, 0, needToHideMenuOverlay = false, navigateImmediately = true)
}
