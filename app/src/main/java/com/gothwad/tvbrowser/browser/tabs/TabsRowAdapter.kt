package com.gothwad.tvbrowser.browser.tabs

import android.graphics.Bitmap
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.Config
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.model.WebTabState
import com.gothwad.tvbrowser.singleton.FaviconsPool
import com.gothwad.tvbrowser.utils.activity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TabsRowAdapter(
    private var tabs: MutableList<WebTabState>,
    private var currentTab: WebTabState?,
    private val onTabClick: (WebTabState) -> Unit,
    private val onCloseTabClick: (WebTabState) -> Unit,
    private val onTabFocused: (WebTabState) -> Unit = {}
) : RecyclerView.Adapter<TabsRowAdapter.TabRowViewHolder>() {

    class TabRowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val llTabRowCardRoot: LinearLayout = view.findViewById(R.id.llTabRowCardRoot)
        val ivTabFavicon: ImageView = view.findViewById(R.id.ivTabFavicon)
        val tvTabTitle: TextView = view.findViewById(R.id.tvTabTitle)
        val ibCloseTab: ImageButton = view.findViewById(R.id.ibCloseTab)
        val ivTabPreview: ImageView = view.findViewById(R.id.ivTabPreview)
        val tvTabDomain: TextView = view.findViewById(R.id.tvTabDomain)
        val tvActiveBadge: TextView = view.findViewById(R.id.tvActiveBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabRowViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab_row_card, parent, false)
        return TabRowViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabRowViewHolder, position: Int) {
        val tab = tabs[position]
        val isActive = tab == currentTab
        holder.itemView.tag = tab

        val isHome = tab.url.isEmpty() ||
                tab.url == Config.HOME_PAGE_URL ||
                tab.url == Config.HOME_URL_ALIAS ||
                tab.url == "about:blank" ||
                tab.title.equals("Home Screen", ignoreCase = true) ||
                tab.title.equals("Home", ignoreCase = true)

        if (isHome) {
            holder.tvTabTitle.text = holder.itemView.context.getString(R.string.home_screen)
            holder.tvTabDomain.text = "Home Screen"
            holder.ivTabFavicon.setImageResource(R.drawable.ic_home_grey_900_24dp)
            holder.ivTabPreview.setImageResource(0)
        } else {
            holder.tvTabTitle.text = if (tab.title.isNotBlank()) tab.title else tab.url
            val domain = try {
                val uri = Uri.parse(tab.url)
                uri.host?.removePrefix("www.") ?: tab.url
            } catch (e: Exception) {
                tab.url
            }
            holder.tvTabDomain.text = domain
            holder.ivTabFavicon.setImageResource(R.drawable.ic_tab_default_favicon)

            // Async Favicon loading
            val activity = holder.itemView.activity as? AppCompatActivity
            val scope = activity?.lifecycleScope
            scope?.launch(Dispatchers.Main) {
                try {
                    val favicon = FaviconsPool.get(tab.url)
                    if (holder.itemView.tag == tab && favicon != null) {
                        holder.ivTabFavicon.setImageBitmap(favicon)
                    }
                } catch (_: Exception) {}
            }

            // Thumbnail loading
            if (tab.thumbnail != null) {
                holder.ivTabPreview.setImageBitmap(tab.thumbnail)
            } else {
                scope?.launch(Dispatchers.IO) {
                    try {
                        val thumb = tab.loadThumbnail()
                        withContext(Dispatchers.Main) {
                            if (holder.itemView.tag == tab && thumb != null) {
                                holder.ivTabPreview.setImageBitmap(thumb)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Active Badge
        holder.tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE
        holder.llTabRowCardRoot.isSelected = isActive

        // TV Focus Animation
        holder.llTabRowCardRoot.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                onTabFocused(tab)
                v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
                v.elevation = 8f
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                v.elevation = 2f
            }
        }

        holder.llTabRowCardRoot.setOnClickListener {
            onTabClick(tab)
        }

        holder.ibCloseTab.setOnClickListener {
            onCloseTabClick(tab)
        }
    }

    override fun getItemCount(): Int = tabs.size

    override fun onViewRecycled(holder: TabRowViewHolder) {
        super.onViewRecycled(holder)
        holder.ivTabPreview.setImageBitmap(null)
        holder.ivTabFavicon.setImageBitmap(null)
    }

    fun updateTabs(newTabs: List<WebTabState>, newCurrentTab: WebTabState?) {
        tabs = newTabs.toMutableList()
        currentTab = newCurrentTab
        notifyDataSetChanged()
    }
}
