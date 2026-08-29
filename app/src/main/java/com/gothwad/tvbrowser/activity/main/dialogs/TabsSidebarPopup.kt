package com.gothwad.tvbrowser.activity.main.dialogs

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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.model.WebTabState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TabsSidebarPopup(
    private val activity: MainActivity,
    private val onTabSelected: (WebTabState) -> Unit,
    private val onNewTabRequested: () -> Unit,
    private val onCloseTabRequested: (WebTabState) -> Unit,
    private val onCloseAllTabsRequested: () -> Unit
) {

    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    private lateinit var btnTabsBack: ImageButton
    private lateinit var tvTabsTitle: TextView
    private lateinit var btnAddNewTab: Button
    private lateinit var btnCloseAllTabs: Button
    private lateinit var rvTabsGrid: RecyclerView
    private lateinit var adapter: TabsSidebarAdapter

    private var tabsList = mutableListOf<WebTabState>()

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

        contentView = LayoutInflater.from(activity).inflate(R.layout.dialog_sidebar_tabs, rootContainer, true)

        val dm = activity.resources.displayMetrics
        val popupWidth = (dm.widthPixels * 0.38f).toInt().coerceIn(360, 640)

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
        btnTabsBack = contentView.findViewById(R.id.btnTabsBack)
        tvTabsTitle = contentView.findViewById(R.id.tvTabsTitle)
        btnAddNewTab = contentView.findViewById(R.id.btnAddNewTab)
        btnCloseAllTabs = contentView.findViewById(R.id.btnCloseAllTabs)
        rvTabsGrid = contentView.findViewById(R.id.rvTabsGrid)

        contentView.findViewById<View>(R.id.vTabsBackdrop).setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = GridLayoutManager(activity, 2)
        rvTabsGrid.layoutManager = layoutManager

        adapter = TabsSidebarAdapter(
            tabs = tabsList,
            currentTab = activity.tabsModel.currentTab.value,
            onTabClick = { tab ->
                dismiss()
                onTabSelected(tab)
            },
            onCloseTabClick = { tab ->
                onCloseTabRequested(tab)
                refreshData()
            }
        )
        rvTabsGrid.adapter = adapter
    }

    private fun setupListeners() {
        btnTabsBack.setOnClickListener { dismiss() }

        btnAddNewTab.setOnClickListener {
            dismiss()
            onNewTabRequested()
        }

        btnCloseAllTabs.setOnClickListener {
            dismiss()
            onCloseAllTabsRequested()
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

        val popupWidth = (screenWidth * 0.38f).toInt().coerceIn(360, 640)
        val popupHeight = (screenHeight - headerBottom).coerceAtLeast(100)

        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.isClippingEnabled = false

        val xPos = screenWidth - popupWidth
        popupWindow.showAtLocation(decorView, Gravity.TOP or Gravity.START, xPos, headerBottom)

        refreshData()

        val activeIndex = tabsList.indexOf(activity.tabsModel.currentTab.value)
        if (activeIndex >= 0) {
            rvTabsGrid.post {
                rvTabsGrid.scrollToPosition(activeIndex)
                val activeView = rvTabsGrid.layoutManager?.findViewByPosition(activeIndex)
                activeView?.requestFocus()
            }
        } else {
            contentView.post {
                btnTabsBack.requestFocus()
            }
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    fun refreshData() {
        val tabs = activity.tabsModel.tabsStates
        val currentTab = activity.tabsModel.currentTab.value

        tabsList.clear()
        tabsList.addAll(tabs)
        tvTabsTitle.text = "Open Tabs (${tabsList.size})"

        adapter.update(tabsList, currentTab)

        if (tabsList.isEmpty()) {
            dismiss()
        }
    }
}

class TabsSidebarAdapter(
    private var tabs: MutableList<WebTabState>,
    private var currentTab: WebTabState?,
    private val onTabClick: (WebTabState) -> Unit,
    private val onCloseTabClick: (WebTabState) -> Unit
) : RecyclerView.Adapter<TabsSidebarAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llTabItemRoot)
        val ivFavicon: ImageView = view.findViewById(R.id.ivTabFavicon)
        val tvActiveBadge: TextView = view.findViewById(R.id.tvTabActiveBadge)
        val btnClose: ImageButton = view.findViewById(R.id.btnTabClose)
        val tvTitle: TextView = view.findViewById(R.id.tvTabTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvTabUrl)
    }

    fun update(newTabs: List<WebTabState>, newCurrentTab: WebTabState?) {
        tabs.clear()
        tabs.addAll(newTabs)
        currentTab = newCurrentTab
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_tab, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tab = tabs[position]
        val isActive = tab == currentTab

        holder.tvTitle.text = if (tab.title.isNullOrBlank()) "New Tab" else tab.title
        holder.tvUrl.text = if (tab.url.isNullOrBlank()) "about:blank" else tab.url

        holder.tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE
        holder.root.isSelected = isActive

        holder.itemView.tag = tab
        holder.ivFavicon.setImageResource(R.drawable.ic_tab_default_favicon)

        val scope = (holder.itemView.context as? AppCompatActivity)?.lifecycleScope
        scope?.launch(Dispatchers.Main) {
            try {
                val favicon = com.gothwad.tvbrowser.singleton.FaviconsPool.get(tab.url)
                if (holder.itemView.tag == tab && favicon != null) {
                    holder.ivFavicon.setImageBitmap(favicon)
                }
            } catch (_: Exception) {}
        }

        holder.root.setOnClickListener { onTabClick(tab) }
        holder.btnClose.setOnClickListener { onCloseTabClick(tab) }
    }

    override fun getItemCount(): Int = tabs.size
}
