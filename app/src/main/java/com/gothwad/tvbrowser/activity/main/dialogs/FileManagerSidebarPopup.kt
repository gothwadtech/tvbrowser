package com.gothwad.tvbrowser.activity.main.dialogs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.BuildConfig
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Stack

class FileManagerSidebarPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    private lateinit var btnFmBack: ImageButton
    private lateinit var tvFmTitle: TextView
    private lateinit var btnFmHome: ImageButton
    private lateinit var btnFmFolderUp: ImageButton
    private lateinit var btnFmCatStorage: Button
    private lateinit var btnFmCatDownloads: Button
    private lateinit var btnFmCatApks: Button
    private lateinit var btnFmCatMedia: Button
    private lateinit var tvFmCurrentPath: TextView
    private lateinit var rvFmFiles: RecyclerView
    private lateinit var pbFmLoading: ProgressBar
    private lateinit var llFmEmpty: LinearLayout

    private var currentDirectory: File = Environment.getExternalStorageDirectory()
    private val dirHistory = Stack<File>()
    private val fileList = mutableListOf<File>()
    private lateinit var adapter: FileManagerSidebarAdapter

    init {
        rootContainer = object : FrameLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE,
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            if (dirHistory.isNotEmpty()) {
                                currentDirectory = dirHistory.pop()
                                loadDirectory(currentDirectory, pushHistory = false)
                                return true
                            } else {
                                dismiss()
                                return true
                            }
                        }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        contentView = LayoutInflater.from(activity).inflate(R.layout.dialog_sidebar_file_manager, rootContainer, true)

        val popupWidth = SidebarHelper.calculateSidebarWidth(activity)

        popupWindow = PopupWindow(
            rootContainer,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 24f
            animationStyle = R.style.SideDrawerAnimation
        }

        bindViews()
        setupListeners()
        setupRecyclerView()
    }

    private fun bindViews() {
        btnFmBack = contentView.findViewById(R.id.btnFmBack)
        tvFmTitle = contentView.findViewById(R.id.tvFmTitle)
        btnFmHome = contentView.findViewById(R.id.btnFmHome)
        btnFmFolderUp = contentView.findViewById(R.id.btnFmFolderUp)
        btnFmCatStorage = contentView.findViewById(R.id.btnFmCatStorage)
        btnFmCatDownloads = contentView.findViewById(R.id.btnFmCatDownloads)
        btnFmCatApks = contentView.findViewById(R.id.btnFmCatApks)
        btnFmCatMedia = contentView.findViewById(R.id.btnFmCatMedia)
        tvFmCurrentPath = contentView.findViewById(R.id.tvFmCurrentPath)
        rvFmFiles = contentView.findViewById(R.id.rvFmFiles)
        pbFmLoading = contentView.findViewById(R.id.pbFmLoading)
        llFmEmpty = contentView.findViewById(R.id.llFmEmpty)

        contentView.findViewById<View>(R.id.vFileManagerBackdrop).setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        rvFmFiles.layoutManager = LinearLayoutManager(activity)
        adapter = FileManagerSidebarAdapter(
            items = fileList,
            onItemClick = { file -> onFileClicked(file) },
            onDeleteClick = { file -> deleteFile(file) }
        )
        rvFmFiles.adapter = adapter
    }

    private fun setupListeners() {
        btnFmBack.setOnClickListener { dismiss() }

        btnFmHome.setOnClickListener {
            loadDirectory(Environment.getExternalStorageDirectory(), pushHistory = true)
        }

        btnFmFolderUp.setOnClickListener {
            val parent = currentDirectory.parentFile
            if (parent != null && parent.canRead()) {
                loadDirectory(parent, pushHistory = true)
            } else {
                Toast.makeText(activity, "Root folder reached", Toast.LENGTH_SHORT).show()
            }
        }

        btnFmCatStorage.setOnClickListener {
            loadDirectory(Environment.getExternalStorageDirectory(), pushHistory = true)
        }

        btnFmCatDownloads.setOnClickListener {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            loadDirectory(downloads, pushHistory = true)
        }

        btnFmCatApks.setOnClickListener {
            filterApkFiles()
        }

        btnFmCatMedia.setOnClickListener {
            filterMediaFiles()
        }
    }

    fun show(anchorView: View? = null) {
        val decorView = activity.window.decorView
        val header = activity.findViewById<View>(R.id.rlActionBar) ?: anchorView ?: decorView

        val loc = IntArray(2)
        header.getLocationInWindow(loc)
        if (loc[1] == 0) {
            header.getLocationOnScreen(loc)
        }
        val headerBottom = loc[1] + header.height

        val screenWidth = if (decorView.width > 0) decorView.width else activity.resources.displayMetrics.widthPixels
        val screenHeight = if (decorView.height > 0) decorView.height else activity.resources.displayMetrics.heightPixels

        val popupWidth = SidebarHelper.calculateSidebarWidth(activity)
        val popupHeight = (screenHeight - headerBottom).coerceAtLeast(100)

        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.isClippingEnabled = false

        val xPos = screenWidth - popupWidth
        popupWindow.showAtLocation(decorView, Gravity.TOP or Gravity.START, xPos, headerBottom)

        loadDirectory(currentDirectory, pushHistory = false)

        contentView.post {
            btnFmBack.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun loadDirectory(dir: File, pushHistory: Boolean) {
        if (pushHistory && currentDirectory != dir) {
            dirHistory.push(currentDirectory)
        }
        currentDirectory = dir
        tvFmCurrentPath.text = dir.absolutePath

        pbFmLoading.visibility = View.VISIBLE
        llFmEmpty.visibility = View.GONE
        rvFmFiles.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val files = dir.listFiles()?.toList() ?: emptyList()
            val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.getDefault()) }))

            withContext(Dispatchers.Main) {
                pbFmLoading.visibility = View.GONE
                fileList.clear()
                fileList.addAll(sorted)
                adapter.notifyDataSetChanged()

                llFmEmpty.visibility = if (fileList.isEmpty()) View.VISIBLE else View.GONE
                rvFmFiles.visibility = if (fileList.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun filterApkFiles() {
        tvFmCurrentPath.text = "Filtered: APK Packages"
        pbFmLoading.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val storage = Environment.getExternalStorageDirectory()
            val apks = mutableListOf<File>()
            scanForExtension(storage, listOf("apk"), apks, maxDepth = 4, currentDepth = 0)

            withContext(Dispatchers.Main) {
                pbFmLoading.visibility = View.GONE
                fileList.clear()
                fileList.addAll(apks)
                adapter.notifyDataSetChanged()

                llFmEmpty.visibility = if (fileList.isEmpty()) View.VISIBLE else View.GONE
                rvFmFiles.visibility = if (fileList.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun filterMediaFiles() {
        tvFmCurrentPath.text = "Filtered: Media Files"
        pbFmLoading.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val storage = Environment.getExternalStorageDirectory()
            val media = mutableListOf<File>()
            scanForExtension(storage, listOf("mp4", "mkv", "mp3", "jpg", "png"), media, maxDepth = 4, currentDepth = 0)

            withContext(Dispatchers.Main) {
                pbFmLoading.visibility = View.GONE
                fileList.clear()
                fileList.addAll(media)
                adapter.notifyDataSetChanged()

                llFmEmpty.visibility = if (fileList.isEmpty()) View.VISIBLE else View.GONE
                rvFmFiles.visibility = if (fileList.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun scanForExtension(dir: File, exts: List<String>, out: MutableList<File>, maxDepth: Int, currentDepth: Int) {
        if (currentDepth > maxDepth || out.size >= 100) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (f.isDirectory && !f.name.startsWith(".")) {
                scanForExtension(f, exts, out, maxDepth, currentDepth + 1)
            } else if (f.isFile) {
                val ext = f.extension.lowercase(Locale.getDefault())
                if (exts.contains(ext)) {
                    out.add(f)
                    if (out.size >= 100) return
                }
            }
        }
    }

    private fun onFileClicked(file: File) {
        if (file.isDirectory) {
            loadDirectory(file, pushHistory = true)
        } else {
            try {
                val fileURI = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", file)
                } else {
                    Uri.fromFile(file)
                }

                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileURI, activity.contentResolver.getType(fileURI) ?: "*/*")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(openIntent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(activity, "No application found to open this file", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(activity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteFile(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore
            }
            loadDirectory(currentDirectory, pushHistory = false)
        }
    }
}

class FileManagerSidebarAdapter(
    private val items: List<File>,
    private val onItemClick: (File) -> Unit,
    private val onDeleteClick: (File) -> Unit
) : RecyclerView.Adapter<FileManagerSidebarAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llFileItemRoot)
        val ivIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        val tvName: TextView = view.findViewById(R.id.tvFileName)
        val tvDetails: TextView = view.findViewById(R.id.tvFileDetails)
        val btnOptions: ImageButton = view.findViewById(R.id.btnFileOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = items[position]
        holder.tvName.text = file.name

        if (file.isDirectory) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder)
            val count = file.list()?.size ?: 0
            holder.tvDetails.text = "$count items • Folder"
        } else {
            val ext = file.extension.lowercase(Locale.getDefault())
            val iconRes = when (ext) {
                "apk" -> R.drawable.ic_file_apk
                "mp4", "mkv", "avi", "webm" -> R.drawable.ic_file_video
                "mp3", "wav", "aac", "ogg" -> R.drawable.ic_file_audio
                "jpg", "jpeg", "png", "gif" -> R.drawable.ic_file_image
                "pdf", "doc", "docx", "txt" -> R.drawable.ic_file_doc
                "zip", "rar", "7z", "tar" -> R.drawable.ic_file_zip
                else -> R.drawable.ic_file_generic
            }
            holder.ivIcon.setImageResource(iconRes)
            val sizeStr = formatSize(file.length())
            holder.tvDetails.text = "$sizeStr • File"
        }

        holder.root.setOnClickListener { onItemClick(file) }
        holder.btnOptions.setOnClickListener { onDeleteClick(file) }
    }

    override fun getItemCount(): Int = items.size

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
