package com.gothwad.tvbrowser.filemanager

import android.content.ClipData
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R

class FileManagerAdapter(
    private var items: List<FileItem>,
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem) -> Boolean,
    private val onItemMoreClick: ((FileItem) -> Unit)? = null
) : RecyclerView.Adapter<FileManagerAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
        val tvFileDate: TextView = view.findViewById(R.id.tvFileDate)
        val ivFolderArrow: ImageView = view.findViewById(R.id.ivFolderArrow)
        val ibItemMore: ImageButton = view.findViewById(R.id.ibItemMore)
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

        // Arrow indicator: Show on folders to indicate entering/navigating into folder
        if (item.isDirectory) {
            holder.ivFolderArrow.visibility = View.VISIBLE
        } else {
            holder.ivFolderArrow.visibility = View.GONE
        }

        holder.itemView.isFocusable = true
        holder.itemView.isClickable = true

        // Smooth TV D-pad Focus animation & hover effects for PC mouse
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate()
                    .scaleX(1.015f)
                    .scaleY(1.015f)
                    .setDuration(100)
                    .start()
                v.elevation = 4f
                holder.tvFileName.setTextColor(ContextCompat.getColor(v.context, android.R.color.white))
                holder.ivFolderArrow.setColorFilter(ContextCompat.getColor(v.context, R.color.progressbar_tint))
            } else {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
                v.elevation = 1f
                holder.tvFileName.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_color_contrast))
                holder.ivFolderArrow.setColorFilter(ContextCompat.getColor(v.context, R.color.day_night_disabled_icon_color))
            }
        }

        // PC / Mouse & Remote Click
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        // More button click
        holder.ibItemMore.setOnClickListener {
            onItemMoreClick?.invoke(item) ?: onItemLongClick(item)
        }

        // Long click for TV remote options or PC right-click / drag
        holder.itemView.setOnLongClickListener { v ->
            val itemUri = item.file.absolutePath
            val clipData = ClipData.newPlainText("file_path", itemUri)
            val shadow = View.DragShadowBuilder(v)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(clipData, shadow, item, 0)
            }
            onItemLongClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<FileItem>) {
        val oldItems = this.items
        this.items = newItems
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition].file.absolutePath == newItems[newItemPosition].file.absolutePath
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = oldItems[oldItemPosition]
                val new = newItems[newItemPosition]
                return old.name == new.name &&
                        old.formattedSize == new.formattedSize &&
                        old.formattedDate == new.formattedDate &&
                        old.isDirectory == new.isDirectory &&
                        old.iconRes == new.iconRes
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }
}
