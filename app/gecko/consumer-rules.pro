-keep class com.gothwad.tvbrowser.webengine.gecko.GeckoWebEngine { *; }

-keepclassmembers class org.mozilla.geckoview.** {
    *** mDisplay;
}
