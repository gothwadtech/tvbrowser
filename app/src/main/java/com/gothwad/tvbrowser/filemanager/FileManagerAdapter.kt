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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import java.io.File

class FileManagerAdapter(
    private var items: List<FileItem>,
    private val onItemClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem) -> Boolean,
    private val onItemMoreClick: ((FileItem) -> Unit)? = null,
    private val onSelectionChanged: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<FileManagerAdapter.FileViewHolder>() {

    var isGridMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    var isMultiSelect: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) selectedPaths.clear()
                updateSelectionState()
                onSelectionChanged?.invoke(selectedPaths.size)
            }
        }

    val selectedPaths = mutableSetOf<String>()

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
        val tvFileDate: TextView? = view.findViewById(R.id.tvFileDate)
        val ivFolderArrow: ImageView? = view.findViewById(R.id.ivFolderArrow)
        val ibItemMore: ImageButton = view.findViewById(R.id.ibItemMore)
        val ivSelectCheck: ImageView? = view.findViewById(R.id.ivSelectCheck)
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val layoutRes = if (viewType == 1) R.layout.item_file_manager_grid else R.layout.item_file_manager_card
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val item = items[position]
        val path = item.file.absolutePath
        val isSelected = selectedPaths.contains(path)

        holder.tvFileName.text = item.name
        holder.tvFileSize.text = item.formattedSize
        holder.tvFileDate?.text = item.formattedDate

        // Selection Checkbox
        if (isMultiSelect) {
            holder.ivSelectCheck?.visibility = View.VISIBLE
            if (isSelected) {
                holder.ivSelectCheck?.setImageResource(R.drawable.ic_check_box_checked)
                holder.ivSelectCheck?.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.progressbar_tint))
            } else {
                holder.ivSelectCheck?.setImageResource(R.drawable.ic_check_box_outline)
                holder.ivSelectCheck?.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.day_night_disabled_icon_color))
            }
        } else {
            holder.ivSelectCheck?.visibility = View.GONE
        }

        // Folder Arrow (in card view)
        if (item.isDirectory) {
            holder.ivFolderArrow?.visibility = View.VISIBLE
        } else {
            holder.ivFolderArrow?.visibility = View.GONE
        }

        // Async Thumbnail / Icon Loading
        holder.ivFileIcon.tag = path
        holder.ivFileIcon.setImageResource(item.iconRes)
        val cached = FileThumbnailLoader.getCached(path)
        if (cached != null) {
            holder.ivFileIcon.setImageDrawable(cached)
        } else {
            FileThumbnailLoader.loadThumbnail(holder.itemView.context, item) { drawable ->
                if (holder.ivFileIcon.tag == path) {
                    holder.ivFileIcon.setImageDrawable(drawable)
                }
            }
        }

        holder.itemView.isFocusable = true
        holder.itemView.isClickable = true

        // TV D-pad Focus animation & hover effects
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val scale = if (isGridMode) 1.05f else 1.015f
                v.animate().scaleX(scale).scaleY(scale).setDuration(100).start()
                v.elevation = 6f
                holder.tvFileName.setTextColor(ContextCompat.getColor(v.context, android.R.color.white))
                holder.ivFolderArrow?.setColorFilter(ContextCompat.getColor(v.context, R.color.progressbar_tint))
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                v.elevation = 1f
                holder.tvFileName.setTextColor(ContextCompat.getColor(v.context, R.color.day_night_text_color_contrast))
                holder.ivFolderArrow?.setColorFilter(ContextCompat.getColor(v.context, R.color.day_night_disabled_icon_color))
            }
        }

        // Click Handling
        holder.itemView.setOnClickListener {
            if (isMultiSelect) {
                toggleSelection(item)
            } else {
                onItemClick(item)
            }
        }

        // More button click
        holder.ibItemMore.setOnClickListener {
            onItemMoreClick?.invoke(item) ?: onItemLongClick(item)
        }

        // Long Click Handling
        holder.itemView.setOnLongClickListener { v ->
            if (isMultiSelect) {
                toggleSelection(item)
                true
            } else {
                try {
                    val context = v.context
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${com.gothwad.tvbrowser.BuildConfig.APPLICATION_ID}.provider",
                        item.file
                    )
                    val mimeType = FileManagerOperations.getMimeType(item.file)
                    val clipItem = ClipData.Item(uri)
                    val clipData = ClipData(item.name, arrayOf(mimeType, android.content.ClipDescription.MIMETYPE_TEXT_URILIST, android.content.ClipDescription.MIMETYPE_TEXT_PLAIN), clipItem)
                    val shadow = View.DragShadowBuilder(v)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        v.startDragAndDrop(clipData, shadow, item.file, View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
                    }
                } catch (_: Exception) {}
                onItemLongClick(item)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun toggleSelection(item: FileItem) {
        val path = item.file.absolutePath
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path)
        } else {
            selectedPaths.add(path)
        }
        updateSelectionState()
        onSelectionChanged?.invoke(selectedPaths.size)
    }

    fun selectAll() {
        selectedPaths.clear()
        for (item in items) {
            selectedPaths.add(item.file.absolutePath)
        }
        updateSelectionState()
        onSelectionChanged?.invoke(selectedPaths.size)
    }

    fun clearSelection() {
        selectedPaths.clear()
        updateSelectionState()
        onSelectionChanged?.invoke(0)
    }

    fun getSelectedFiles(): List<File> {
        return items.filter { selectedPaths.contains(it.file.absolutePath) }.map { it.file }
    }

    private fun updateSelectionState() {
        val updatedList = items.map { item ->
            item.copy(isSelected = selectedPaths.contains(item.file.absolutePath))
        }
        updateItems(updatedList)
    }

    fun updateItems(newItems: List<FileItem>) {
        val oldItems = this.items
        this.items = newItems
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return oldItems[oldPos].file.absolutePath == newItems[newPos].file.absolutePath
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val o = oldItems[oldPos]
                val n = newItems[newPos]
                return o.name == n.name &&
                        o.formattedSize == n.formattedSize &&
                        o.formattedDate == n.formattedDate &&
                        o.isDirectory == n.isDirectory &&
                        o.isSelected == n.isSelected &&
                        o.iconRes == n.iconRes
            }
        })
        diffResult.dispatchUpdatesTo(this)
    }
}

