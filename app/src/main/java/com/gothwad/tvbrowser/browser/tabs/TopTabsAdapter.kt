package com.gothwad.tvbrowser.browser.tabs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
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

class TopTabsAdapter(
    private var tabs: MutableList<WebTabState>,
    private var currentTab: WebTabState?,
    private val onTabClick: (WebTabState) -> Unit,
    private val onCloseTabClick: (WebTabState) -> Unit,
    private val onTabFocused: (WebTabState, Int, View) -> Unit = { _, _, _ -> }
) : RecyclerView.Adapter<TopTabsAdapter.TopTabViewHolder>() {

    class TopTabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val llChromeTabRoot: LinearLayout = view.findViewById(R.id.llChromeTabRoot)
        val ivTabFavicon: ImageView = view.findViewById(R.id.ivTabFavicon)
        val tvTabTitle: TextView = view.findViewById(R.id.tvTabTitle)
        val ibTabClose: ImageButton = view.findViewById(R.id.ibTabClose)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopTabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_top_chrome_tab, parent, false)
        return TopTabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TopTabViewHolder, position: Int) {
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
            holder.ivTabFavicon.setImageResource(R.drawable.ic_home_grey_900_24dp)
        } else {
            holder.tvTabTitle.text = if (tab.title.isNotBlank()) tab.title else tab.url
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
        }

        // Active styling
        holder.llChromeTabRoot.isSelected = isActive
        holder.llChromeTabRoot.isActivated = isActive
        holder.tvTabTitle.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        holder.tvTabTitle.alpha = if (isActive) 1.0f else 0.85f

        // Click actions
        holder.llChromeTabRoot.setOnClickListener {
            onTabClick(tab)
        }

        holder.ibTabClose.setOnClickListener {
            onCloseTabClick(tab)
        }

        // TV Focus Animation
        holder.llChromeTabRoot.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                onTabFocused(tab, holder.bindingAdapterPosition, v)
                v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                v.elevation = 6f
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                v.elevation = 0f
            }
        }

        holder.ibTabClose.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }
        }
    }

    override fun getItemCount(): Int = tabs.size

    fun updateData(newTabs: List<WebTabState>, newCurrentTab: WebTabState?) {
        tabs = newTabs.toMutableList()
        currentTab = newCurrentTab
        notifyDataSetChanged()
    }
}
