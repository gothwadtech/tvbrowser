package com.gothwad.tvbrowser.activity.main.view.home

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R

class HomeCardAdapter(
    private val items: List<HomeShortcutItem>,
    private val onItemClick: (HomeShortcutItem) -> Unit,
    private val onAddClick: () -> Unit,
    private val onRemoveClick: () -> Unit,
    private val onItemLongClick: ((HomeShortcutItem) -> Boolean)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_SHORTCUT = 0
        const val VIEW_TYPE_ACTION = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isActionCard || items[position].isAddButton) {
            VIEW_TYPE_ACTION
        } else {
            VIEW_TYPE_SHORTCUT
        }
    }

    class ShortcutViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val flIconCircle: FrameLayout = view.findViewById(R.id.flIconCircle)
        val ivIconDrawable: ImageView = view.findViewById(R.id.ivIconDrawable)
        val tvIconText: TextView = view.findViewById(R.id.tvIconText)
        val tvShortcutTitle: TextView = view.findViewById(R.id.tvShortcutTitle)
        val tvShortcutDomain: TextView = view.findViewById(R.id.tvShortcutDomain)
    }

    class ActionViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val btnCardAdd: LinearLayout = view.findViewById(R.id.btnCardAdd)
        val btnCardRemove: LinearLayout = view.findViewById(R.id.btnCardRemove)
        val ivAddIcon: ImageView = view.findViewById(R.id.ivAddIcon)
        val ivRemoveIcon: ImageView = view.findViewById(R.id.ivRemoveIcon)
        val tvAddLabel: TextView = view.findViewById(R.id.tvAddLabel)
        val tvRemoveLabel: TextView = view.findViewById(R.id.tvRemoveLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_ACTION) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_action_card, parent, false)
            ActionViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_card, parent, false)
            ShortcutViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]

        if (holder is ActionViewHolder) {
            holder.btnCardAdd.isFocusable = true
            holder.btnCardAdd.isFocusableInTouchMode = true
            holder.btnCardRemove.isFocusable = true
            holder.btnCardRemove.isFocusableInTouchMode = true

            holder.btnCardAdd.setOnClickListener { onAddClick() }
            holder.btnCardRemove.setOnClickListener { onRemoveClick() }

            holder.btnCardAdd.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                    v.elevation = 8f
                    holder.tvAddLabel.setTextColor(ContextCompat.getColor(v.context, R.color.progressbar_tint))
                    holder.ivAddIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(v.context, R.color.progressbar_tint))
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    v.elevation = 1f
                    holder.tvAddLabel.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_color_contrast))
                    holder.ivAddIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(v.context, R.color.day_night_icon_color))
                }
            }

            holder.btnCardRemove.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150).start()
                    v.elevation = 8f
                    holder.tvRemoveLabel.setTextColor(ContextCompat.getColor(v.context, R.color.progressbar_tint))
                    holder.ivRemoveIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(v.context, R.color.progressbar_tint))
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    v.elevation = 1f
                    holder.tvRemoveLabel.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_color_contrast))
                    holder.ivRemoveIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(v.context, R.color.day_night_icon_color))
                }
            }
        } else if (holder is ShortcutViewHolder) {
            holder.tvShortcutTitle.text = item.title
            holder.tvShortcutDomain.text = item.domainText

            if (item.iconDrawableRes != null && item.iconDrawableRes != 0) {
                holder.ivIconDrawable.visibility = View.VISIBLE
                holder.tvIconText.visibility = View.GONE
                holder.ivIconDrawable.setImageResource(item.iconDrawableRes)
            } else {
                holder.ivIconDrawable.visibility = View.GONE
                holder.tvIconText.visibility = View.VISIBLE
                holder.tvIconText.text = item.singleLetter
                holder.tvIconText.textSize = 18f
            }

            holder.itemView.isFocusable = true
            holder.itemView.isFocusableInTouchMode = true
            holder.itemView.isClickable = true

            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(150)
                        .start()
                    v.elevation = 8f
                    holder.tvShortcutTitle.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_color_contrast))
                    holder.tvShortcutDomain.setTextColor(ContextCompat.getColor(v.context, R.color.progressbar_tint))
                } else {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                    v.elevation = 1f
                    holder.tvShortcutTitle.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_color_contrast))
                    holder.tvShortcutDomain.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_secondary))
                }
            }

            holder.itemView.setOnClickListener {
                onItemClick(item)
            }

            holder.itemView.setOnLongClickListener {
                onItemLongClick?.invoke(item) ?: false
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
