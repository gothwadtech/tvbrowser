package com.gothwad.tvbrowser.activity.main.view.tabs

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.model.WebTabState

/**
 * Legacy TabsAdapter maintained for backward compatibility.
 * Modern tab management is handled by TabsGridAdapter in dialogs/tabs/.
 */
class TabsAdapter(
    private var tabs: List<WebTabState> = emptyList(),
    private val onTabClick: ((WebTabState) -> Unit)? = null
) : RecyclerView.Adapter<TabsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = View(parent.context)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tab = tabs.getOrNull(position) ?: return
        holder.itemView.setOnClickListener { onTabClick?.invoke(tab) }
    }

    override fun getItemCount(): Int = tabs.size

    fun updateTabs(newTabs: List<WebTabState>) {
        this.tabs = newTabs
        notifyDataSetChanged()
    }
}
