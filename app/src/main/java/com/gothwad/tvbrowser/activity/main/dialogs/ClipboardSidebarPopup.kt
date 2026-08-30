package com.gothwad.tvbrowser.activity.main.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.notes.clipboard.ClipboardItem
import com.gothwad.tvbrowser.singleton.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardSidebarPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    private lateinit var btnClipboardBack: ImageButton
    private lateinit var tvClipboardTitle: TextView
    private lateinit var btnClearClipboard: Button
    private lateinit var rvClipboard: RecyclerView
    private lateinit var llEmptyClipboard: LinearLayout

    private val clipboardList = mutableListOf<ClipboardItem>()
    private lateinit var adapter: ClipboardSidebarAdapter

    init {
        rootContainer = object : FrameLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE,
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            dismiss()
                            return true
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

        contentView = LayoutInflater.from(activity).inflate(R.layout.dialog_sidebar_clipboard, rootContainer, true)

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
        btnClipboardBack = contentView.findViewById(R.id.btnClipboardBack)
        tvClipboardTitle = contentView.findViewById(R.id.tvClipboardTitle)
        btnClearClipboard = contentView.findViewById(R.id.btnClearClipboard)
        rvClipboard = contentView.findViewById(R.id.rvClipboard)
        llEmptyClipboard = contentView.findViewById(R.id.llEmptyClipboard)

        contentView.findViewById<View>(R.id.vClipboardBackdrop).setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        rvClipboard.layoutManager = LinearLayoutManager(activity)
        adapter = ClipboardSidebarAdapter(
            items = clipboardList,
            onItemClick = { item -> copyToClipboard(item) },
            onDeleteClick = { item -> deleteItem(item) }
        )
        rvClipboard.adapter = adapter
    }

    private fun setupListeners() {
        btnClipboardBack.setOnClickListener { dismiss() }

        btnClearClipboard.setOnClickListener {
            clearAll()
        }
    }

    private fun copyToClipboard(item: ClipboardItem) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", item.text)
        cm?.setPrimaryClip(clip)
        Toast.makeText(activity, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun deleteItem(item: ClipboardItem) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            db.clipboardDao().delete(item)
            withContext(Dispatchers.Main) {
                loadClipboard()
            }
        }
    }

    private fun clearAll() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            db.clipboardDao().deleteAll()
            withContext(Dispatchers.Main) {
                loadClipboard()
            }
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

        loadClipboard()

        contentView.post {
            btnClipboardBack.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun loadClipboard() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            val all = db.clipboardDao().getAll()
            withContext(Dispatchers.Main) {
                clipboardList.clear()
                clipboardList.addAll(all)
                adapter.notifyDataSetChanged()

                llEmptyClipboard.visibility = if (clipboardList.isEmpty()) View.VISIBLE else View.GONE
                rvClipboard.visibility = if (clipboardList.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}

class ClipboardSidebarAdapter(
    private val items: List<ClipboardItem>,
    private val onItemClick: (ClipboardItem) -> Unit,
    private val onDeleteClick: (ClipboardItem) -> Unit
) : RecyclerView.Adapter<ClipboardSidebarAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llClipboardItemRoot)
        val tvText: TextView = view.findViewById(R.id.tvClipboardText)
        val tvTime: TextView = view.findViewById(R.id.tvClipboardTime)
        val btnDelete: ImageButton = view.findViewById(R.id.btnClipboardDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_clipboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvText.text = item.text

        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        holder.tvTime.text = sdf.format(Date(item.timestamp))

        holder.root.setOnClickListener { onItemClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
