package com.gothwad.tvbrowser.activity.notes

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R

class NotesAdapter(
    private var items: List<NoteItem>,
    private val onItemClick: (NoteItem) -> Unit,
    private val onItemLongClick: (NoteItem) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    fun updateItems(newItems: List<NoteItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

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

        fun bind(item: NoteItem) {
            tvNoteTitle.text = if (item.title.isNotEmpty()) item.title else "Untitled Note"
            tvNoteContent.text = item.content
            tvNoteDate.text = item.formattedDate
            ivPin.visibility = if (item.isPinned) View.VISIBLE else View.GONE

            val parsedColor = try {
                Color.parseColor(item.colorHex)
            } catch (e: Exception) {
                0xFF1E293B.toInt()
            }

            val normalDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * itemView.context.resources.displayMetrics.density
                setColor(parsedColor)
                setStroke(1, 0xFF334155.toInt())
            }

            val focusedDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * itemView.context.resources.displayMetrics.density
                setColor(parsedColor)
                setStroke((2.5f * itemView.context.resources.displayMetrics.density).toInt(), 0xFF38BDF8.toInt())
            }

            llNoteCard.background = normalDrawable

            llNoteCard.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    llNoteCard.background = focusedDrawable
                    llNoteCard.animate().scaleX(1.04f).scaleY(1.04f).translationZ(8f).setDuration(150).start()
                } else {
                    llNoteCard.background = normalDrawable
                    llNoteCard.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start()
                }
            }

            llNoteCard.setOnClickListener { onItemClick(item) }
            llNoteCard.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }
}
