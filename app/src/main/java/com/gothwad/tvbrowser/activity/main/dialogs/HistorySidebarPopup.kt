package com.gothwad.tvbrowser.activity.main.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.model.HistoryItem
import com.gothwad.tvbrowser.singleton.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistorySidebarPopup(
    private val activity: MainActivity,
    private val onHistoryItemSelected: (HistoryItem) -> Unit
) {

    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    private lateinit var btnHistoryBack: ImageButton
    private lateinit var tvHistoryTitle: TextView
    private lateinit var btnClearHistory: Button
    private lateinit var etHistorySearch: EditText
    private lateinit var btnHistoryFilterAll: Button
    private lateinit var btnHistoryFilterToday: Button
    private lateinit var btnHistoryFilterOlder: Button
    private lateinit var rvHistory: RecyclerView
    private lateinit var pbHistoryLoading: ProgressBar
    private lateinit var llEmptyHistory: LinearLayout

    private val allHistory = mutableListOf<HistoryItem>()
    private val displayedHistory = mutableListOf<HistoryItem>()
    private lateinit var adapter: HistorySidebarAdapter
    private var currentFilter = 0 // 0: All, 1: Today, 2: Older
    private var searchQuery = ""

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

        contentView = LayoutInflater.from(activity).inflate(R.layout.dialog_sidebar_history, rootContainer, true)

        val popupWidth = SidebarHelper.calculateLeftSidebarWidth(activity)

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
            animationStyle = R.style.SideDrawerLeftAnimation
        }

        bindViews()
        setupListeners()
        setupRecyclerView()
    }

    private fun bindViews() {
        btnHistoryBack = contentView.findViewById(R.id.btnHistoryBack)
        tvHistoryTitle = contentView.findViewById(R.id.tvHistoryTitle)
        btnClearHistory = contentView.findViewById(R.id.btnClearHistory)
        etHistorySearch = contentView.findViewById(R.id.etHistorySearch)
        btnHistoryFilterAll = contentView.findViewById(R.id.btnHistoryFilterAll)
        btnHistoryFilterToday = contentView.findViewById(R.id.btnHistoryFilterToday)
        btnHistoryFilterOlder = contentView.findViewById(R.id.btnHistoryFilterOlder)
        rvHistory = contentView.findViewById(R.id.rvHistory)
        pbHistoryLoading = contentView.findViewById(R.id.pbHistoryLoading)
        llEmptyHistory = contentView.findViewById(R.id.llEmptyHistory)

        contentView.findViewById<View>(R.id.vHistoryBackdrop).setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        rvHistory.layoutManager = LinearLayoutManager(activity)
        adapter = HistorySidebarAdapter(
            items = displayedHistory,
            onItemClick = { item ->
                dismiss()
                onHistoryItemSelected(item)
            },
            onDeleteClick = { item -> deleteHistoryItem(item) }
        )
        rvHistory.adapter = adapter
    }

    private fun setupListeners() {
        btnHistoryBack.setOnClickListener { dismiss() }

        btnClearHistory.setOnClickListener {
            clearAllHistory()
        }

        etHistorySearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty().trim().lowercase(Locale.getDefault())
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnHistoryFilterAll.setOnClickListener {
            currentFilter = 0
            updateFilterButtons()
            applyFilters()
        }
        btnHistoryFilterToday.setOnClickListener {
            currentFilter = 1
            updateFilterButtons()
            applyFilters()
        }
        btnHistoryFilterOlder.setOnClickListener {
            currentFilter = 2
            updateFilterButtons()
            applyFilters()
        }
    }

    private fun updateFilterButtons() {
        btnHistoryFilterAll.isSelected = currentFilter == 0
        btnHistoryFilterToday.isSelected = currentFilter == 1
        btnHistoryFilterOlder.isSelected = currentFilter == 2

        btnHistoryFilterAll.setTextColor(if (currentFilter == 0) Color.WHITE else Color.parseColor("#94A3B8"))
        btnHistoryFilterToday.setTextColor(if (currentFilter == 1) Color.WHITE else Color.parseColor("#94A3B8"))
        btnHistoryFilterOlder.setTextColor(if (currentFilter == 2) Color.WHITE else Color.parseColor("#94A3B8"))
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

        val popupWidth = SidebarHelper.calculateLeftSidebarWidth(activity)
        val popupHeight = (screenHeight - headerBottom).coerceAtLeast(100)

        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.isClippingEnabled = false

        // LEFT SIDEBAR: xPos = 0
        popupWindow.showAtLocation(decorView, Gravity.TOP or Gravity.START, 0, headerBottom)

        loadHistory()

        contentView.post {
            btnHistoryBack.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun loadHistory() {
        pbHistoryLoading.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            val all = db.historyDao().getAll()
            withContext(Dispatchers.Main) {
                pbHistoryLoading.visibility = View.GONE
                allHistory.clear()
                allHistory.addAll(all)
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        val filtered = allHistory.filter { item ->
            val matchesSearch = searchQuery.isEmpty() ||
                    item.title?.lowercase(Locale.getDefault())?.contains(searchQuery) == true ||
                    item.url?.lowercase(Locale.getDefault())?.contains(searchQuery) == true

            val matchesFilter = when (currentFilter) {
                1 -> (now - item.time) <= oneDayMillis
                2 -> (now - item.time) > oneDayMillis
                else -> true
            }

            matchesSearch && matchesFilter
        }

        displayedHistory.clear()
        displayedHistory.addAll(filtered)
        adapter.notifyDataSetChanged()

        llEmptyHistory.visibility = if (displayedHistory.isEmpty()) View.VISIBLE else View.GONE
        rvHistory.visibility = if (displayedHistory.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun deleteHistoryItem(item: HistoryItem) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            db.historyDao().delete(item)
            withContext(Dispatchers.Main) {
                allHistory.remove(item)
                applyFilters()
            }
        }
    }

    private fun clearAllHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            db.historyDao().deleteAll()
            withContext(Dispatchers.Main) {
                allHistory.clear()
                applyFilters()
            }
        }
    }
}

class HistorySidebarAdapter(
    private val items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit,
    private val onDeleteClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistorySidebarAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llHistoryItemRoot)
        val ivFavicon: ImageView = view.findViewById(R.id.ivHistoryFavicon)
        val tvTitle: TextView = view.findViewById(R.id.tvHistoryTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvHistoryUrl)
        val tvTime: TextView = view.findViewById(R.id.tvHistoryTime)
        val btnDelete: ImageButton = view.findViewById(R.id.btnHistoryDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = if (item.title.isNullOrBlank()) item.url else item.title
        holder.tvUrl.text = item.url

        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        holder.tvTime.text = sdf.format(Date(item.time))

        holder.root.setOnClickListener { onItemClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
