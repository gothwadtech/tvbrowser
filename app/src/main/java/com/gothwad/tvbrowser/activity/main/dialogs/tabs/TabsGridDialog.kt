package com.gothwad.tvbrowser.activity.main.dialogs.tabs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.activity.main.MainActivity
import com.gothwad.tvbrowser.model.WebTabState

class TabsGridDialog(
    private val activity: MainActivity,
    private val onTabSelected: (WebTabState) -> Unit,
    private val onNewTabRequested: () -> Unit,
    private val onCloseTabRequested: (WebTabState) -> Unit,
    private val onCloseAllTabsRequested: () -> Unit
) : Dialog(activity, R.style.SettingsDialog) {

    private lateinit var tvTabsHeaderTitle: TextView
    private lateinit var btnNewTabAction: Button
    private lateinit var btnCloseAllTabs: Button
    private lateinit var btnDoneTabs: Button
    private lateinit var rvTabsGrid: RecyclerView
    private lateinit var adapter: TabsGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_tabs_grid)

        window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88).toInt(),
            (activity.resources.displayMetrics.heightPixels * 0.85).toInt()
        )

        initViews()
        setupRecyclerView()
        setupActions()
    }

    private fun initViews() {
        tvTabsHeaderTitle = findViewById(R.id.tvTabsHeaderTitle)
        btnNewTabAction = findViewById(R.id.btnNewTabAction)
        btnCloseAllTabs = findViewById(R.id.btnCloseAllTabs)
        btnDoneTabs = findViewById(R.id.btnDoneTabs)
        rvTabsGrid = findViewById(R.id.rvTabsGrid)
    }

    private fun setupRecyclerView() {
        val tabs = activity.tabsModel.tabsStates
        val currentTab = activity.tabsModel.currentTab.value

        updateTitle(tabs.size)

        rvTabsGrid.layoutManager = GridLayoutManager(context, 3)
        adapter = TabsGridAdapter(
            tabs = tabs.toMutableList(),
            currentTab = currentTab,
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

    private fun updateTitle(count: Int) {
        tvTabsHeaderTitle.text = "📑 Open Tabs ($count)"
    }

    fun refreshData() {
        val tabs = activity.tabsModel.tabsStates
        val currentTab = activity.tabsModel.currentTab.value
        updateTitle(tabs.size)
        adapter.updateTabs(tabs, currentTab)
        if (tabs.isEmpty()) {
            dismiss()
        }
    }

    private fun setupActions() {
        btnNewTabAction.setOnClickListener {
            dismiss()
            onNewTabRequested()
        }

        btnCloseAllTabs.setOnClickListener {
            dismiss()
            onCloseAllTabsRequested()
        }

        btnDoneTabs.setOnClickListener {
            dismiss()
        }
    }
}
