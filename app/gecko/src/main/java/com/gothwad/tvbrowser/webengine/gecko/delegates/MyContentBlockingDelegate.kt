package com.gothwad.tvbrowser.webengine.gecko.delegates

import com.gothwad.tvbrowser.webengine.gecko.GeckoWebEngine
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoSession

class MyContentBlockingDelegate(private val webEngine: GeckoWebEngine): ContentBlocking.Delegate {
    override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
        webEngine.callback?.onBlockedAd(event.uri)
    }

    override fun onContentLoaded(session: GeckoSession, event: ContentBlocking.BlockEvent) {}
}