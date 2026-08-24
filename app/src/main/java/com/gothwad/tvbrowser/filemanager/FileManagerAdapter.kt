package com.gothwad.tvbrowser.filemanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R

class FileManagerAdapter(
    private var items: List<FileItem>,
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem) -> Boolean
) : RecyclerView.Adapter<FileManagerAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
        val tvFileDate: TextView = view.findViewById(R.id.tvFileDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file_manager_card, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = items[position]

        holder.tvFileName.text = item.name
        holder.tvFileSize.text = item.formattedSize
        holder.tvFileDate.text = item.formattedDate
        holder.ivFileIcon.setImageResource(item.iconRes)

        holder.itemView.isFocusable = true
        holder.itemView.isClickable = true

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate()
                    .scaleX(1.04f)
                    .scaleY(1.04f)
                    .setDuration(150)
                    .start()
                v.elevation = 8f
                holder.tvFileName.setTextColor(ContextCompat.getColor(v.context, android.R.color.white))
            } else {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
                v.elevation = 2f
                holder.tvFileName.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_color_contrast))
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<FileItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
