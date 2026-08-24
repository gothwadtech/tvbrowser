package com.gothwad.tvbrowser.activity.history

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.model.HistoryItem
import com.gothwad.tvbrowser.singleton.FaviconsPool
import com.gothwad.tvbrowser.utils.activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

class HistoryItemView(context: Context, private val viewType: Int) : FrameLayout(context) {
    private var tvDate: TextView? = null
    private var tvTitle: TextView? = null
    private var tvURL: TextView? = null
    private var tvTime: TextView? = null
    private var ivHistoryFavicon: ImageView? = null
    private var cbSelection: CheckBox? = null
    var historyItem: HistoryItem? = null

    init {
        LayoutInflater.from(context).inflate(
            if (viewType == HistoryAdapter.VIEW_TYPE_HEADER)
                R.layout.view_history_header_item
            else
                R.layout.view_history_item, this
        )
        when (viewType) {
            HistoryAdapter.VIEW_TYPE_HEADER -> {
                tvDate = findViewById(R.id.tvDate)
            }
            HistoryAdapter.VIEW_TYPE_HISTORY_ITEM -> {
                tvTitle = findViewById(R.id.tvTitle)
                tvURL = findViewById(R.id.tvURL)
                tvTime = findViewById(R.id.tvTime)
                ivHistoryFavicon = findViewById(R.id.ivHistoryFavicon)
                cbSelection = findViewById(R.id.cbSelection)
            }
        }
    }

    fun setHistoryItem(historyItem: HistoryItem, multiselectMode: Boolean) {
        this.historyItem = historyItem
        when (viewType) {
            HistoryAdapter.VIEW_TYPE_HEADER -> {
                val df = SimpleDateFormat.getDateInstance()
                tvDate?.text = df.format(Date(historyItem.time))
            }
            HistoryAdapter.VIEW_TYPE_HISTORY_ITEM -> {
                tvTitle?.text = if (!historyItem.title.isNullOrBlank()) historyItem.title else historyItem.url
                tvURL?.text = historyItem.url
                val sdf = SimpleDateFormat("HH:mm")
                tvTime?.text = sdf.format(Date(historyItem.time))
                cbSelection?.visibility = if (multiselectMode) VISIBLE else GONE
                cbSelection?.isChecked = historyItem.selected

                // Favicon / logo
                val url = historyItem.url ?: ""
                val logoRes = getLogoForUrl(url)
                if (logoRes != 0) {
                    ivHistoryFavicon?.setImageResource(logoRes)
                } else {
                    ivHistoryFavicon?.setImageResource(R.drawable.ic_tab_default_favicon)
                    val activity = this.activity as? AppCompatActivity
                    activity?.lifecycleScope?.launch(Dispatchers.Main) {
                        try {
                            val favicon = FaviconsPool.get(url)
                            if (this@HistoryItemView.historyItem == historyItem && favicon != null) {
                                ivHistoryFavicon?.setImageBitmap(favicon)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun getLogoForUrl(url: String): Int {
        val lower = url.lowercase()
        return when {
            lower.contains("google.com") -> R.drawable.ic_logo_google
            lower.contains("youtube.com") -> R.drawable.ic_logo_youtube
            lower.contains("whatsapp.com") -> R.drawable.ic_logo_whatsapp
            lower.contains("reddit.com") -> R.drawable.ic_logo_reddit
            lower.contains("netflix.com") -> R.drawable.ic_logo_netflix
            lower.contains("spotify.com") -> R.drawable.ic_logo_spotify
            lower.contains("amazon.com") -> R.drawable.ic_logo_amazon
            lower.contains("github.com") -> R.drawable.ic_logo_github
            lower.contains("facebook.com") -> R.drawable.ic_logo_facebook
            lower.contains("instagram.com") -> R.drawable.ic_logo_instagram
            lower.contains("telegram.org") -> R.drawable.ic_logo_telegram
            lower.contains("wikipedia.org") -> R.drawable.ic_logo_wikipedia
            else -> 0
        }
    }

    fun setSelection(selected: Boolean) {
        if (viewType == HistoryAdapter.VIEW_TYPE_HEADER) return
        cbSelection?.isChecked = selected
        historyItem?.selected = selected
    }
}
