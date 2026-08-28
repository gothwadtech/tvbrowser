package com.gothwad.tvbrowser.activity.main

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.gothwad.tvbrowser.AppContext
import com.gothwad.tvbrowser.BrowserApp
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.model.HostConfig
import com.gothwad.tvbrowser.model.WebTabState
import com.gothwad.tvbrowser.singleton.AppDatabase
import com.gothwad.tvbrowser.utils.Utils
import com.gothwad.tvbrowser.utils.activemodel.ActiveModel
import com.gothwad.tvbrowser.utils.observable.ObservableList
import com.gothwad.tvbrowser.utils.observable.ObservableValue
import com.gothwad.tvbrowser.webengine.WebEngineWindowProviderCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.URL

class TabsModel : ActiveModel() {
    companion object {
        var TAG: String = TabsModel::class.java.simpleName
    }

    var loaded = false
    val currentTab = ObservableValue<WebTabState?>(null)
    val tabsStates = ObservableList<WebTabState>()
    private val config = AppContext.provideConfig()
    private var incognitoMode = config.incognitoMode

    init {
        tabsStates.subscribe({
            //auto-update positions on any list change
            var positionsChanged = false
            tabsStates.forEachIndexed { index, webTabState ->
                if (webTabState.position != index) {
                    webTabState.position = index
                    positionsChanged = true
                }
            }
            if (positionsChanged) {
                val tabsListClone = listOf(*tabsStates.toTypedArray())
                modelScope.launch(Dispatchers.IO) {
                    try {
                        val tabsDao = AppDatabase.db.tabsDao()
                        tabsDao.updatePositions(tabsListClone)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error updating tab positions in DB: $e")
                    }
                }
            }
        }, false)
    }

    fun loadState() = modelScope.launch(Dispatchers.Main) {
        if (loaded) {
            //check is incognito mode changed
            if (incognitoMode != config.incognitoMode) {
                incognitoMode = config.incognitoMode
                loaded = false
            } else {
                return@launch
            }
        }
        val tabsDao = AppDatabase.db.tabsDao()
        tabsStates.replaceAll(tabsDao.getAll(config.incognitoMode))
        loaded = true
    }

    suspend fun saveTab(tab: WebTabState) = withContext(Dispatchers.IO) {
        try {
            val tabsDB = AppDatabase.db.tabsDao()
            if (tab.selected) {
                tabsDB.unselectAll(config.incognitoMode)
            }
            tab.saveWebViewStateToFile()
            if (tab.id != 0L) {
                tabsDB.update(tab)
            } else {
                tab.id = tabsDB.insert(tab)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save tab in DB: $e")
        }
    }

    fun onCloseTab(tab: WebTabState) {
        tab.webEngine.onDetachFromWindow(completely = true, destroyTab = true)
        tabsStates.remove(tab)
        modelScope.launch(Dispatchers.IO) {
            try {
                val tabsDB = AppDatabase.db.tabsDao()
                tabsDB.delete(tab)
                tab.removeFiles()
            } catch (e: Exception) {
                Log.w(TAG, "Error deleting tab: $e")
            }
        }
    }

    fun onCloseAllTabs() = modelScope.launch(Dispatchers.IO) {
        try {
            val tabsClone = ArrayList(tabsStates)
            withContext(Dispatchers.Main) {
                tabsStates.clear()
            }
            val tabsDB = AppDatabase.db.tabsDao()
            tabsDB.deleteAll(config.incognitoMode)
            tabsClone.forEach { it.removeFiles() }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing all tabs: $e")
        }
    }

    fun onDetachActivity() {
        for (tab in tabsStates) {
            tab.webEngine.onDetachFromWindow(completely = true, destroyTab = false)
        }
    }

    fun changeTab(
        newTab: WebTabState,
        webViewProvider: (tab: WebTabState) -> View?,
        webViewParent: ViewGroup,
        webEngineWindowProviderCallback: WebEngineWindowProviderCallback
    ) {
        val oldTab = currentTab.value
        if (oldTab == newTab && newTab.webEngine.getView() != null && newTab.webEngine.getView()?.parent == webViewParent) {
            return
        }

        if (oldTab != null && oldTab != newTab) {
            oldTab.selected = false
            oldTab.webEngine.onDetachFromWindow(completely = false, destroyTab = false)
            oldTab.onPause()
            modelScope.launch(Dispatchers.IO) {
                try {
                    saveTab(oldTab)
                } catch (e: Exception) {
                    Log.w(TAG, "Error saving old tab: $e")
                }
            }
        }

        tabsStates.forEach {
            it.selected = (it == newTab)
        }
        newTab.selected = true

        var wv = newTab.webEngine.getView()
        var needReloadUrl = false
        if (wv == null) {
            wv = webViewProvider(newTab)
            if (wv == null) {
                return
            }
            needReloadUrl = !newTab.restoreWebView()
        }

        newTab.webEngine.onAttachToWindow(webEngineWindowProviderCallback, webViewParent)

        if (needReloadUrl && newTab.url.isNotEmpty() && newTab.url != "about:blank" && newTab.url != Config.HOME_PAGE_URL && newTab.url != Config.HOME_URL_ALIAS) {
            newTab.webEngine.loadUrl(newTab.url)
        }
        newTab.webEngine.setNetworkAvailable(Utils.isNetworkConnected(BrowserApp.instance))

        currentTab.value = newTab
    }

    private val hostConfigCache = java.util.concurrent.ConcurrentHashMap<String, HostConfig>()

    fun getCachedHostConfig(tab: WebTabState): HostConfig? {
        val currentHostName = try {
            java.net.URL(tab.url).host
        } catch (e: Exception) {
            null
        } ?: return null

        tab.cachedHostConfig?.let {
            if (it.hostName == currentHostName) return it
        }
        hostConfigCache[currentHostName]?.let {
            tab.cachedHostConfig = it
            return it
        }
        modelScope.launch(Dispatchers.IO) {
            findHostConfig(tab, false)
        }
        return null
    }

    suspend fun findHostConfig(tab: WebTabState, createIfNotFound: Boolean): HostConfig? = withContext(Dispatchers.IO) {
        Log.d(WebTabState.TAG, "findOrCreateHostConfig")
        val currentHostName = try {
            java.net.URL(tab.url).host
        } catch (e: Exception) {
            Log.w(WebTabState.TAG, "Can not parse current url host: $e")
            return@withContext null
        }
        var hostConfig = tab.cachedHostConfig
        if (hostConfig == null || hostConfig.hostName != currentHostName) {
            hostConfig = hostConfigCache[currentHostName]
            if (hostConfig == null) {
                val db = com.gothwad.tvbrowser.singleton.AppDatabase.db.hostsDao()
                hostConfig = db.findByHostName(currentHostName)
                if (hostConfig == null && createIfNotFound) {
                    hostConfig = HostConfig(currentHostName)
                    hostConfig.id = db.insert(hostConfig)
                }
            }
            if (hostConfig != null) {
                hostConfigCache[currentHostName] = hostConfig
            }
            tab.cachedHostConfig = hostConfig
        }
        return@withContext hostConfig
    }

    suspend fun changePopupBlockingLevel(newLevel: Int, tab: WebTabState) {
        val hostConfig = findHostConfig(tab,true) ?: return
        hostConfig.popupBlockLevel = newLevel
        hostConfigCache[hostConfig.hostName] = hostConfig
        withContext(Dispatchers.IO) {
            AppDatabase.db.hostsDao().update(hostConfig)
        }
    }
}