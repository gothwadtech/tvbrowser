package com.gothwad.tvbrowser.activity.clipboard

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R

class ClipboardAdapter(
    private val context: Context,
    private var items: List<ClipboardItem>,
    private val onItemClick: (ClipboardItem) -> Unit,
    private val onCopyClick: (ClipboardItem) -> Unit,
    private val onDeleteClick: (ClipboardItem) -> Unit
) : RecyclerView.Adapter<ClipboardAdapter.ClipboardViewHolder>() {

    fun updateItems(newItems: List<ClipboardItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clipboard_row, parent, false)
        return ClipboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClipboardViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ClipboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivTypeIcon: ImageView = itemView.findViewById(R.id.ivTypeIcon)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvPreviewText: TextView = itemView.findViewById(R.id.tvPreviewText)
        private val tvCategoryBadge: TextView = itemView.findViewById(R.id.tvCategoryBadge)
        private val tvCharCount: TextView = itemView.findViewById(R.id.tvCharCount)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvCopyCount: TextView = itemView.findViewById(R.id.tvCopyCount)
        private val btnRowCopy: ImageButton = itemView.findViewById(R.id.btnRowCopy)
        private val btnRowDelete: ImageButton = itemView.findViewById(R.id.btnRowDelete)

        fun bind(item: ClipboardItem) {
            tvTitle.text = item.displayTitle
            tvPreviewText.text = item.previewText
            tvCharCount.text = item.charCountText
            tvTimestamp.text = item.formattedDate
            tvCopyCount.text = if (item.copyCount > 1) "• Copied ${item.copyCount}x" else ""

            val neutralIconColor = ContextCompat.getColor(context, R.color.day_night_icon_color)
            ivTypeIcon.setColorFilter(neutralIconColor)
            btnRowCopy.setColorFilter(neutralIconColor)

            when (item.type) {
                ClipboardItem.TYPE_URL -> {
                    tvCategoryBadge.text = "LINK"
                    ivTypeIcon.setImageResource(R.drawable.ic_nav_clipboard)
                }
                ClipboardItem.TYPE_IMAGE -> {
                    tvCategoryBadge.text = "IMAGE"
                    ivTypeIcon.setImageResource(R.drawable.ic_nav_clipboard)
                }
                ClipboardItem.TYPE_EMAIL -> {
                    tvCategoryBadge.text = "EMAIL"
                    ivTypeIcon.setImageResource(R.drawable.ic_nav_clipboard)
                }
                ClipboardItem.TYPE_CODE -> {
                    tvCategoryBadge.text = "CODE"
                    ivTypeIcon.setImageResource(R.drawable.ic_snippet)
                }
                else -> {
                    tvCategoryBadge.text = "TEXT"
                    ivTypeIcon.setImageResource(R.drawable.ic_nav_clipboard)
                }
            }
            tvCategoryBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.progressbar_tint))

            itemView.setOnClickListener { onItemClick(item) }
            btnRowCopy.setOnClickListener { onCopyClick(item) }
            btnRowDelete.setOnClickListener { onDeleteClick(item) }
        }
    }
}
