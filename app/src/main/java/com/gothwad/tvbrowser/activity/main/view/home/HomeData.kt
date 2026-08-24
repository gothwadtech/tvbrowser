package com.gothwad.tvbrowser.activity.main.view.home

import com.gothwad.tvbrowser.R

data class HomeShortcutItem(
    val title: String,
    val url: String,
    val iconDrawableRes: Int? = null,
    val isAddButton: Boolean = false,
    val isActionCard: Boolean = false,
    val isUserBookmark: Boolean = false
) {
    val singleLetter: String
        get() {
            val trimmed = title.trim()
            return if (trimmed.isNotEmpty()) trimmed.first().uppercase() else "•"
        }

    val domainText: String
        get() {
            if (isAddButton || isActionCard) return "Shortcut Controls"
            return try {
                val uri = java.net.URI(url)
                val host = uri.host ?: url
                host.removePrefix("www.").removePrefix("https://").removePrefix("http://")
            } catch (e: Exception) {
                url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            }
        }
}

object HomeData {

    fun getIconForUrlOrTitle(url: String, title: String): Int? {
        val u = url.lowercase()
        val t = title.lowercase()

        return when {
            u.contains("instagram.com") || t.contains("instagram") || t == "insta" -> R.drawable.ic_logo_instagram
            u.contains("whatsapp.com") || t.contains("whatsapp") || t.contains("wa") -> R.drawable.ic_logo_whatsapp
            u.contains("telegram.org") || t.contains("telegram") || t == "tg" -> R.drawable.ic_logo_telegram
            u.contains("youtube.com") || u.contains("youtu.be") || t == "youtube" -> R.drawable.ic_logo_youtube
            u.contains("mail.google.com") || u.contains("gmail.com") || t.contains("gmail") -> R.drawable.ic_logo_gmail
            u.contains("drive.google.com") || t.contains("drive") -> R.drawable.ic_logo_drive
            u.contains("docs.google.com") || t.contains("docs") -> R.drawable.ic_logo_docs
            u.contains("sheets.google.com") || t.contains("sheets") -> R.drawable.ic_logo_sheets
            u.contains("meet.google.com") || t.contains("meet") -> R.drawable.ic_logo_meet
            u.contains("maps.google.com") || t.contains("maps") -> R.drawable.ic_logo_maps
            u.contains("photos.google.com") || t.contains("photos") -> R.drawable.ic_logo_photos
            u.contains("calendar.google.com") || t.contains("calendar") -> R.drawable.ic_logo_calendar
            u.contains("translate.google.com") || t.contains("translate") -> R.drawable.ic_logo_translate
            u.contains("gemini.google.com") || t.contains("gemini") -> R.drawable.ic_logo_gemini
            u.contains("spotify.com") || t.contains("spotify") -> R.drawable.ic_logo_spotify
            u.contains("netflix.com") || t.contains("netflix") -> R.drawable.ic_logo_netflix
            u.contains("amazon.") || t.contains("amazon") -> R.drawable.ic_logo_amazon
            u.contains("facebook.com") || t.contains("facebook") || t == "fb" -> R.drawable.ic_logo_facebook
            u.contains("google.com") || t == "google" -> R.drawable.ic_logo_google
            u.contains("wikipedia.org") || t.contains("wikipedia") -> R.drawable.ic_logo_wikipedia
            u.contains("github.com") || t.contains("github") -> R.drawable.ic_logo_github
            u.contains("reddit.com") || t.contains("reddit") -> R.drawable.ic_logo_reddit
            u.contains("weather.com") || t.contains("weather") -> R.drawable.ic_logo_weather
            else -> null
        }
    }

    /**
     * Sorts shortcuts: Google is always strictly #1 at the front.
     * All remaining shortcuts are sorted alphabetically A-Z by title.
     */
    fun sortShortcutsWithGoogleFirst(items: List<HomeShortcutItem>): List<HomeShortcutItem> {
        val googleItems = items.filter { it.title.equals("Google", ignoreCase = true) }
        val nonGoogleItems = items.filter { !it.title.equals("Google", ignoreCase = true) }
            .sortedBy { it.title.trim().lowercase() }

        return googleItems + nonGoogleItems
    }

    fun getDefaultGoogleBookmarks(): List<HomeShortcutItem> {
        val rawList = listOf(
            Pair("Google", "https://www.google.com"),
            Pair("Amazon", "https://www.amazon.com"),
            Pair("Calendar", "https://calendar.google.com"),
            Pair("Cricbuzz", "https://www.cricbuzz.com"),
            Pair("Docs", "https://docs.google.com"),
            Pair("Drive", "https://drive.google.com"),
            Pair("Facebook", "https://www.facebook.com"),
            Pair("Gemini", "https://gemini.google.com"),
            Pair("GitHub", "https://github.com"),
            Pair("Gmail", "https://mail.google.com"),
            Pair("Instagram", "https://www.instagram.com"),
            Pair("Maps", "https://maps.google.com"),
            Pair("Netflix", "https://www.netflix.com"),
            Pair("Photos", "https://photos.google.com"),
            Pair("Pinterest", "https://www.pinterest.com"),
            Pair("Reddit", "https://www.reddit.com"),
            Pair("Sheets", "https://sheets.google.com"),
            Pair("Spotify", "https://open.spotify.com"),
            Pair("Telegram", "https://web.telegram.org"),
            Pair("Translate", "https://translate.google.com"),
            Pair("Weather", "https://weather.com"),
            Pair("WhatsApp", "https://web.whatsapp.com"),
            Pair("Wikipedia", "https://www.wikipedia.org"),
            Pair("YouTube", "https://www.youtube.com")
        )

        val items = rawList.map { (title, url) ->
            HomeShortcutItem(
                title = title,
                url = url,
                iconDrawableRes = getIconForUrlOrTitle(url, title),
                isUserBookmark = true
            )
        }

        return sortShortcutsWithGoogleFirst(items)
    }
}
