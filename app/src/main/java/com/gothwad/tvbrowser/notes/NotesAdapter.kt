package com.gothwad.tvbrowser.notes

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R

class NotesAdapter(
    private var items: List<NoteItem>,
    private val onItemClick: (NoteItem) -> Unit,
    private val onItemLongClick: (NoteItem) -> Unit,
    private val onSelectionCountChanged: (Int) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    var isSelectionMode: Boolean = false
        private set

    val selectedIds = mutableSetOf<String>()

    fun updateItems(newItems: List<NoteItem>) {
        val oldItems = this.items
        this.items = newItems
        // Remove ids that no longer exist
        val currentIds = newItems.map { it.id }.toSet()
        selectedIds.retainAll(currentIds)
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition].id == newItems[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldItems[oldItemPosition]
                val new = newItems[newItemPosition]
                return old.title == new.title &&
                        old.content == new.content &&
                        old.timestamp == new.timestamp &&
                        old.isPinned == new.isPinned &&
                        old.isArchived == new.isArchived &&
                        old.colorHex == new.colorHex &&
                        (selectedIds.contains(old.id) == selectedIds.contains(new.id))
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedIds.clear()
            }
            onSelectionCountChanged(selectedIds.size)
            notifyItemRangeChanged(0, items.size)
        }
    }

    fun toggleSelection(noteId: String) {
        val pos = items.indexOfFirst { it.id == noteId }
        if (selectedIds.contains(noteId)) {
            selectedIds.remove(noteId)
        } else {
            selectedIds.add(noteId)
        }
        onSelectionCountChanged(selectedIds.size)
        if (pos != -1) {
            notifyItemChanged(pos)
        } else {
            notifyItemRangeChanged(0, items.size)
        }
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(items.map { it.id })
        onSelectionCountChanged(selectedIds.size)
        notifyItemRangeChanged(0, items.size)
    }

    fun deselectAll() {
        selectedIds.clear()
        onSelectionCountChanged(0)
        notifyItemRangeChanged(0, items.size)
    }

    fun getSelectedCount(): Int = selectedIds.size

    fun getSelectedNotes(): List<NoteItem> = items.filter { it.id in selectedIds }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note_card, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val llNoteCard: LinearLayout = itemView.findViewById(R.id.llNoteCard)
        private val tvNoteTitle: TextView = itemView.findViewById(R.id.tvNoteTitle)
        private val tvNoteContent: TextView = itemView.findViewById(R.id.tvNoteContent)
        private val tvNoteDate: TextView = itemView.findViewById(R.id.tvNoteDate)
        private val ivPin: ImageView = itemView.findViewById(R.id.ivPin)
        private val ivArchiveBadge: ImageView = itemView.findViewById(R.id.ivArchiveBadge)
        private val ivCheckSelect: ImageView = itemView.findViewById(R.id.ivCheckSelect)

        fun bind(item: NoteItem) {
            tvNoteTitle.text = if (item.title.isNotEmpty()) item.title else "Untitled Note"
            tvNoteContent.text = item.content
            tvNoteDate.text = item.formattedDate
            ivPin.visibility = if (item.isPinned) View.VISIBLE else View.GONE
            ivArchiveBadge.visibility = if (item.isArchived) View.VISIBLE else View.GONE

            val isSelected = selectedIds.contains(item.id)

            if (isSelectionMode) {
                ivCheckSelect.visibility = View.VISIBLE
                if (isSelected) {
                    ivCheckSelect.setImageResource(R.drawable.ic_check_box_checked)
                    ivCheckSelect.setColorFilter(ContextCompat.getColor(itemView.context, R.color.progressbar_tint))
                } else {
                    ivCheckSelect.setImageResource(R.drawable.ic_check_box_outline)
                    ivCheckSelect.setColorFilter(ContextCompat.getColor(itemView.context, R.color.day_night_icon_color))
                }
            } else {
                ivCheckSelect.visibility = View.GONE
            }

            val parsedColor = try {
                Color.parseColor(item.colorHex)
            } catch (_: Exception) {
                0xFF1E293B.toInt()
            }

            val strokeColor = when {
                isSelected -> 0xFF3B82F6.toInt() // Selected blue stroke
                else -> 0xFF334155.toInt()
            }
            val strokeWidth = if (isSelected) {
                (2.5f * itemView.context.resources.displayMetrics.density).toInt()
            } else {
                1
            }

            val normalDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * itemView.context.resources.displayMetrics.density
                setColor(parsedColor)
                setStroke(strokeWidth, strokeColor)
            }

            val focusedDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * itemView.context.resources.displayMetrics.density
                setColor(parsedColor)
                setStroke((2.5f * itemView.context.resources.displayMetrics.density).toInt(), 0xFF60A5FA.toInt())
            }

            llNoteCard.background = normalDrawable

            llNoteCard.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    llNoteCard.background = focusedDrawable
                    llNoteCard.animate().scaleX(1.03f).scaleY(1.03f).translationZ(6f).setDuration(120).start()
                } else {
                    llNoteCard.background = normalDrawable
                    llNoteCard.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(120).start()
                }
            }

            llNoteCard.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item.id)
                } else {
                    onItemClick(item)
                }
            }

            llNoteCard.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }
}
