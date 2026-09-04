package com.gothwad.tvbrowser.webengine.gecko

import com.gothwad.tvbrowser.Config
import org.mozilla.geckoview.GeckoRuntimeSettings

fun Config.Theme.toGeckoPreferredColorScheme(): Int {
    return when (this) {
        Config.Theme.SYSTEM -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        Config.Theme.WHITE_PURE,
        Config.Theme.WHITE_WARM,
        Config.Theme.WHITE_COOL -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        Config.Theme.BLACK_AMOLED,
        Config.Theme.BLACK_CHARCOAL,
        Config.Theme.BLACK_MIDNIGHT -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
    }
}