package com.gothwad.tvbrowser.activity.main

import android.util.Patterns
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.utils.Utils
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

fun MainActivity.handleSearch(aText: String) {
    var text = aText
    val trimmedLowercased = text.trim { it <= ' ' }.lowercase()
    if (Patterns.WEB_URL.matcher(text).matches() || trimmedLowercased.startsWith("http://") || trimmedLowercased.startsWith("https://")) {
        if (!text.lowercase().contains("://")) {
            text = "https://$text"
        }
        navigate(text)
    } else {
        var query: String? = null
        try {
            query = URLEncoder.encode(text, "utf-8")
        } catch (e1: UnsupportedEncodingException) {
            e1.printStackTrace()
            Utils.showToast(this, R.string.error)
            return
        }
        val searchUrl = config.searchEngineURL.value.replace("[query]", query)
        navigate(searchUrl)
    }
}

fun MainActivity.applyWebPageZoom(percent: Int) {
    config.webPageZoomPercent = percent
    tabsModel.tabsStates.forEach { tabState ->
        tabState.webEngine.setPageZoom(percent)
    }
}

fun MainActivity.zoomWebIn() {
    val next = (config.webPageZoomPercent + 10).coerceAtMost(Config.WEB_PAGE_ZOOM_PERCENT_MAX)
    applyWebPageZoom(next)
}

fun MainActivity.zoomWebOut() {
    val next = (config.webPageZoomPercent - 10).coerceAtLeast(Config.WEB_PAGE_ZOOM_PERCENT_MIN)
    applyWebPageZoom(next)
}
