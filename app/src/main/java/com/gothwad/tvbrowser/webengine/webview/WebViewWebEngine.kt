package com.gothwad.tvbrowser.webengine.webview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.gothwad.tvbrowser.AppContext
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.model.WebTabState
import com.gothwad.tvbrowser.utils.Utils
import com.gothwad.tvbrowser.webengine.WebEngine
import com.gothwad.tvbrowser.webengine.WebEngineFactory
import com.gothwad.tvbrowser.webengine.WebEngineProvider
import com.gothwad.tvbrowser.webengine.WebEngineProviderCallback
import com.gothwad.tvbrowser.webengine.WebEngineWindowProviderCallback
import com.gothwad.tvbrowser.widgets.cursor.CursorDrawerDelegate
import com.gothwad.tvbrowser.widgets.cursor.CursorLayout

class WebViewWebEngine(val tab: WebTabState) : WebEngine, CursorDrawerDelegate.Callback {
    private var webView: WebViewEx? = null
    internal var callback: WebEngineWindowProviderCallback? = null
    private var viewParent: CursorLayout? = null
    private var fullScreenView: View? = null
    private val permissionsRequests = HashMap<Int, Boolean>()
    private val jsInterface = AndroidJSInterface(this)

    private val webViewCallback = WebViewWebEngineCallback(
        getCallback = { callback },
        getWebView = { webView },
        getViewParent = { viewParent },
        onCustomViewChanged = { view, isShow ->
            if (isShow) {
                fullScreenView = view
            } else {
                fullScreenView?.let { viewParent?.removeView(it) }
                fullScreenView = null
            }
        },
        onPermissionRecord = { reqCode, isGeo ->
            permissionsRequests[reqCode] = isGeo
        }
    )

    override fun getWebEngineName(): String = "WebView"

    override fun isSameSession(internalRepresentation: Any): Boolean = internalRepresentation == webView

    override val url: String? get() = webView?.url

    override var userAgentString: String? = null
        set(value) {
            field = value
            webView?.settings?.userAgentString = value
        }

    override fun saveState(): Any {
        val bundle = Bundle()
        webView?.saveState(bundle)
        return bundle
    }

    override fun restoreState(savedInstanceState: Any) {
        if (savedInstanceState is Bundle) {
            webView?.restoreState(savedInstanceState)
        } else {
            throw IllegalArgumentException("savedInstanceState must be Bundle")
        }
    }

    override fun stateFromBytes(bytes: ByteArray): Any? = Utils.bytesToBundle(bytes)

    override fun loadUrl(url: String) { webView?.loadUrl(url) }
    override fun canGoForward(): Boolean = webView?.canGoForward() ?: false
    override fun goForward() { webView?.goForward() }
    override fun canZoomIn(): Boolean = webView?.canZoomIn() ?: false

    override fun zoomIn() {
        val cfg = AppContext.provideConfig()
        val next = (cfg.webPageZoomPercent + 10).coerceAtMost(Config.WEB_PAGE_ZOOM_PERCENT_MAX)
        setPageZoom(next)
    }

    override fun canZoomOut(): Boolean = webView?.canZoomOut() ?: false

    override fun zoomOut() {
        val cfg = AppContext.provideConfig()
        val next = (cfg.webPageZoomPercent - 10).coerceAtLeast(Config.WEB_PAGE_ZOOM_PERCENT_MIN)
        setPageZoom(next)
    }

    override fun zoomBy(zoomBy: Float) { webView?.zoomBy(zoomBy) }

    override fun setPageZoom(percent: Int) {
        webView?.settings?.textZoom = 100
        val scale = percent / 100f
        webView?.evaluateJavascript("""
            (function() {
                document.documentElement.style.zoom = '$scale';
            })();
        """.trimIndent(), null)
    }

    override fun evaluateJavascript(script: String) { webView?.evaluateJavascript(script, null) }
    override fun setNetworkAvailable(connected: Boolean) { webView?.setNetworkAvailable(connected) }
    override fun getView(): View? = webView

    @Throws(Exception::class)
    override fun getOrCreateView(activityContext: Context): View {
        if (webView == null) {
            webView = WebViewEx(activityContext, webViewCallback, jsInterface)
            val cfg = AppContext.provideConfig()
            val effectiveUa = userAgentString ?: cfg.userAgentString.value ?: if (cfg.desktopMode.value) Config.DESKTOP_UA else null
            if (effectiveUa != null) {
                userAgentString = effectiveUa
                webView?.settings?.userAgentString = effectiveUa
            }
        }
        return webView!!
    }

    override fun canGoBack(): Boolean = webView?.canGoBack() ?: false
    override fun goBack() { webView?.goBack() }
    override fun clearHistory() { webView?.clearHistory() }
    override fun reload() { webView?.reload() }

    override fun onFilePicked(resultCode: Int, data: Intent?) {
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return
        webView?.onFilePicked(data)
    }

    override fun onResume() { webView?.onResume() }
    override fun onPause() { webView?.onPause() }
    override fun onUpdateAdblockSetting(newState: Boolean) { webView?.onUpdateAdblockSetting(newState) }
    override fun hideFullscreenView() { webView?.hideCustomView() }
    override fun togglePlayback() { webView?.evaluateJavascript("tvBroTogglePlayback()", null) }
    override fun stopPlayback() { webView?.evaluateJavascript("tvBroStopPlayback()", null) }
    override fun rewind() { webView?.evaluateJavascript("tvBroRewind()", null) }
    override fun fastForward() { webView?.evaluateJavascript("tvBroFastForward()", null) }
    override suspend fun renderThumbnail(bitmap: Bitmap?): Bitmap? = webView?.renderThumbnail(bitmap)

    override fun onAttachToWindow(callback: WebEngineWindowProviderCallback, parent: ViewGroup) {
        this.callback = callback
        val wv = webView ?: return
        this.viewParent = parent as? CursorLayout
        (wv.parent as? ViewGroup)?.removeView(wv)
        parent.removeAllViews()
        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        parent.addView(wv, lp)
        viewParent?.cursorDrawerDelegate?.callback = this
        onResume()
    }

    override fun onDetachFromWindow(completely: Boolean, destroyTab: Boolean) {
        try { onPause() } catch (e: Exception) {}
        (webView?.parent as? ViewGroup)?.removeView(webView)
        viewParent = null
        callback = null
        if (completely || destroyTab) {
            try { webView?.destroy() } catch (e: Exception) {}
            webView = null
        }
    }

    override fun trimMemory() {
        val wv = webView
        if (wv != null && !wv.isAttachedToWindow) {
            this.webView = null
        }
    }

    override fun onPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        val isGeo = permissionsRequests[requestCode] ?: return false
        permissionsRequests.remove(requestCode)
        if (grantResults.isEmpty()) return true
        webView?.onPermissionsResult(permissions, grantResults, isGeo)
        return true
    }

    override fun onLongPress(x: Int, y: Int) {
        webView?.let {
            it.evaluateJavascript(Scripts.LONG_PRESS_SCRIPT) { href ->
                val linkUrl = if (href == "null") null else href
                webViewCallback.onContextMenu(it.currentOriginalUrl.toString(), linkUrl, x, y)
            }
        }
    }

    override fun onCursorNearTop() { callback?.onScrollChange(0, 0, -100) }
    override fun onScrollDirection(dy: Int) { callback?.onScrollChange(0, 0, dy) }
    override fun isVirtualCursorMode(): Boolean = viewParent?.cursorEnabled ?: true

    override fun setVirtualCursorMode(enabled: Boolean) {
        viewParent?.cursorEnabled = enabled
        if (enabled) {
            viewParent?.cursorDrawerDelegate?.animateAppearing()
        }
        webView?.setVirtualCursorMode(enabled)
    }

    override fun getCursorDrawerDelegate(): CursorDrawerDelegate? = viewParent?.cursorDrawerDelegate

    companion object {
        init {
            WebEngineFactory.registerProvider(WebEngineProvider("WebView", object : WebEngineProviderCallback {
                override suspend fun initialize(context: Context, webViewContainer: CursorLayout) {}
                override fun createWebEngine(tab: WebTabState): WebEngine = WebViewWebEngine(tab)
                override suspend fun clearCache(ctx: Context) { WebView(ctx).clearCache(true) }
                override fun onThemeSettingUpdated(value: Config.Theme) {}
                override fun getWebEngineVersionString(): String {
                    val pkg = WebViewCompat.getCurrentWebViewPackage(AppContext.get())
                    return (pkg?.packageName ?: "unknown") + ":" + (pkg?.versionName ?: "unknown")
                }
            }))
        }
    }
}
