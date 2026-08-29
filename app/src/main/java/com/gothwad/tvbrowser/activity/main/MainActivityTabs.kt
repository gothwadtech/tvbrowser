package com.gothwad.tvbrowser.activity.main

import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.gothwad.tvbrowser.browser.tabs.TabsRowDialog
import com.gothwad.tvbrowser.browser.tabs.TopTabsAdapter
import com.gothwad.tvbrowser.model.WebTabState

fun MainActivity.setupTopTabBar() {
    val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    vb.rvTopTabs.layoutManager = layoutManager
    val adapter = TopTabsAdapter(
        tabs = tabsModel.tabsStates,
        currentTab = tabsModel.currentTab.value,
        onTabClick = { tab ->
            switchToTab(tab)
        },
        onCloseTabClick = { tab ->
            closeTab(tab)
        },
        onTabFocused = { _, position, _ ->
            vb.rvTopTabs.smoothScrollToPosition(position)
        }
    )
    topTabsAdapter = adapter
    vb.rvTopTabs.adapter = adapter

    vb.ibTopNewTab.setOnClickListener {
        openInNewTab(settingsModel.homePage, tabsModel.tabsStates.size, needToHideMenuOverlay = false, navigateImmediately = true)
    }

    val isTopTabBarEnabled = config.showTopTabBar.value
    vb.llTopTabBar.visibility = if (isTopTabBarEnabled) View.VISIBLE else View.GONE

    config.showTopTabBar.subscribe(this) { isEnabled ->
        vb.llTopTabBar.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }
}

fun MainActivity.refreshTopTabs() {
    val adapter = topTabsAdapter ?: return
    val current = tabsModel.currentTab.value
    adapter.updateData(tabsModel.tabsStates, current)
    if (current != null) {
        val index = tabsModel.tabsStates.indexOf(current)
        if (index >= 0) {
            vb.rvTopTabs.post {
                vb.rvTopTabs.smoothScrollToPosition(index)
            }
        }
    }
}

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
    refreshTopTabs()
}

fun MainActivity.closeAllTabs() {
    val tabsToClose = tabsModel.tabsStates.toList()
    for (tab in tabsToClose) {
        closeTab(tab)
    }
    openInNewTab(settingsModel.homePage, 0, needToHideMenuOverlay = false, navigateImmediately = true)
}

