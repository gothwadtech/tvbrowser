package com.gothwad.tvbrowser.activity.clipboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.databinding.ActivityClipboardBinding

class ClipboardActivity : AppCompatActivity() {

    companion object {
        const val KEY_URL_TO_OPEN = "clipboard_url_to_open"
    }

    private lateinit var vb: ActivityClipboardBinding
    private lateinit var repository: ClipboardRepository
    private lateinit var adapter: ClipboardAdapter
    private var allItems: MutableList<ClipboardItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityClipboardBinding.inflate(layoutInflater)
        setContentView(vb.root)

        repository = ClipboardRepository(this)

        setupRecyclerView()
        setupListeners()
        syncWithSystemClipboard()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        syncWithSystemClipboard()
        loadData()
    }

    private fun syncWithSystemClipboard() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (cm.hasPrimaryClip()) {
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    val text = item.text?.toString() ?: item.uri?.toString()
                    if (!text.isNullOrBlank()) {
                        repository.recordCopiedText(text)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupRecyclerView() {
        adapter = ClipboardAdapter(
            context = this,
            items = emptyList(),
            onItemClick = { item -> showDetailDialog(item) },
            onCopyClick = { item ->
                repository.copyToSystemClipboard(item, showToast = true)
                loadData()
            },
            onDeleteClick = { item ->
                confirmDeleteItem(item)
            }
        )

        vb.rvClipboard.layoutManager = LinearLayoutManager(this)
        vb.rvClipboard.adapter = adapter
    }

    private fun setupListeners() {
        vb.btnBack.setOnClickListener { finish() }

        vb.btnClearHistory.setOnClickListener { showClearHistoryDialog() }

        vb.etSearchClipboard.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearchFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadData() {
        allItems = repository.getAllItems()
        applySearchFilter()
    }

    private fun applySearchFilter() {
        val query = vb.etSearchClipboard.text.toString().trim().lowercase()

        var filtered = allItems
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.title.lowercase().contains(query) ||
                        it.text.lowercase().contains(query) ||
                        it.type.lowercase().contains(query)
            }.toMutableList()
        }

        adapter.updateItems(filtered)

        vb.tvItemsCount.text = "${filtered.size} items"
        vb.llEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        vb.rvClipboard.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showDetailDialog(item: ClipboardItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_clipboard_detail, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val tvCategoryBadge: TextView = dialogView.findViewById(R.id.tvDetailCategoryBadge)
        val tvTitle: TextView = dialogView.findViewById(R.id.tvDetailTitle)
        val tvTimestamp: TextView = dialogView.findViewById(R.id.tvDetailTimestamp)
        val tvFullText: TextView = dialogView.findViewById(R.id.tvDetailFullText)
        val tvStats: TextView = dialogView.findViewById(R.id.tvDetailCharWordStats)
        val tvCopyCount: TextView = dialogView.findViewById(R.id.tvDetailCopyCount)

        val btnCopy: Button = dialogView.findViewById(R.id.btnDetailCopy)
        val btnOpenUrl: Button = dialogView.findViewById(R.id.btnDetailOpenUrl)
        val btnDelete: Button = dialogView.findViewById(R.id.btnDetailDelete)
        val btnClose: Button = dialogView.findViewById(R.id.btnDetailClose)

        tvCategoryBadge.text = item.type
        tvTitle.text = item.displayTitle
        tvTimestamp.text = item.formattedDate
        tvFullText.text = item.text

        val wordCount = item.text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        tvStats.text = "Length: ${item.text.length} chars • $wordCount words"
        tvCopyCount.text = if (item.copyCount > 1) "Copied ${item.copyCount} times" else "Copied 1 time"

        btnOpenUrl.visibility = if (item.isUrl) View.VISIBLE else View.GONE

        btnCopy.setOnClickListener {
            repository.copyToSystemClipboard(item, showToast = true)
            loadData()
            dialog.dismiss()
        }

        btnOpenUrl.setOnClickListener {
            dialog.dismiss()
            openUrlInBrowser(item.text)
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            confirmDeleteItem(item)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun openUrlInBrowser(url: String) {
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }

        val resultIntent = Intent()
        resultIntent.putExtra(KEY_URL_TO_OPEN, formattedUrl)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun confirmDeleteItem(item: ClipboardItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Clipboard Item")
            .setMessage("Are you sure you want to delete this item from history?\n\n\"${item.displayTitle}\"")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteItem(item.id)
                loadData()
                Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearHistoryDialog() {
        if (allItems.isEmpty()) {
            Toast.makeText(this, "Clipboard history is already empty", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Clear All Clipboard History")
            .setMessage("Do you want to permanently clear all clipboard history?")
            .setPositiveButton("Clear All") { _, _ ->
                repository.clearAll()
                loadData()
                Toast.makeText(this, "All clipboard history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
