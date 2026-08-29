package com.gothwad.tvbrowser.notes

import android.content.Intent
import android.os.Bundle
import com.gothwad.tvbrowser.utils.setupAsSidebar
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import com.gothwad.tvbrowser.notes.clipboard.ClipboardActivity
import kotlinx.coroutines.launch

class NotesActivity : AppCompatActivity() {

    enum class NoteTab {
        ALL_NOTES, ARCHIVED
    }

    private lateinit var notesRepository: NotesRepository
    private lateinit var adapter: NotesAdapter
    private lateinit var rvNotes: RecyclerView

    // Normal Header Elements
    private lateinit var llNormalHeader: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnTabAllNotes: LinearLayout
    private lateinit var tvTabAllNotesText: TextView
    private lateinit var ivTabAllNotesIcon: ImageView
    private lateinit var btnTabArchived: LinearLayout
    private lateinit var tvTabArchivedText: TextView
    private lateinit var ivTabArchivedIcon: ImageView
    private lateinit var etSearchNotes: EditText
    private lateinit var tvNotesCountBadge: TextView
    private lateinit var btnClipboardHistory: ImageButton
    private lateinit var btnToggleSelect: ImageButton
    private lateinit var btnAddNote: ImageButton

    // Selection Header Elements
    private lateinit var llSelectionHeader: LinearLayout
    private lateinit var btnCloseSelection: ImageButton
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnSelectAll: Button
    private lateinit var btnArchiveSelected: Button
    private lateinit var btnDeleteSelected: Button

    // Empty View
    private lateinit var llEmptyNotesView: LinearLayout
    private lateinit var ivEmptyNotesIcon: ImageView
    private lateinit var tvEmptyNotesTitle: TextView
    private lateinit var tvEmptyNotesSubtext: TextView

    private var currentTab = NoteTab.ALL_NOTES
    private var allNotesList: MutableList<NoteItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupAsSidebar(true)
        setContentView(R.layout.activity_notes)

        notesRepository = NotesRepository(this)
        initViews()
        setupListeners()
        loadNotes()
    }

    private fun initViews() {
        rvNotes = findViewById(R.id.rvNotes)

        // Normal Header
        llNormalHeader = findViewById(R.id.llNormalHeader)
        btnBack = findViewById(R.id.btnBack)
        btnTabAllNotes = findViewById(R.id.btnTabAllNotes)
        tvTabAllNotesText = findViewById(R.id.tvTabAllNotesText)
        ivTabAllNotesIcon = findViewById(R.id.ivTabAllNotesIcon)
        btnTabArchived = findViewById(R.id.btnTabArchived)
        tvTabArchivedText = findViewById(R.id.tvTabArchivedText)
        ivTabArchivedIcon = findViewById(R.id.ivTabArchivedIcon)
        etSearchNotes = findViewById(R.id.etSearchNotes)
        tvNotesCountBadge = findViewById(R.id.tvNotesCountBadge)
        btnClipboardHistory = findViewById(R.id.btnClipboardHistory)
        btnToggleSelect = findViewById(R.id.btnToggleSelect)
        btnAddNote = findViewById(R.id.btnAddNote)

        // Selection Header
        llSelectionHeader = findViewById(R.id.llSelectionHeader)
        btnCloseSelection = findViewById(R.id.btnCloseSelection)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnArchiveSelected = findViewById(R.id.btnArchiveSelected)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)

        // Empty View
        llEmptyNotesView = findViewById(R.id.llEmptyNotesView)
        ivEmptyNotesIcon = findViewById(R.id.ivEmptyNotesIcon)
        tvEmptyNotesTitle = findViewById(R.id.tvEmptyNotesTitle)
        tvEmptyNotesSubtext = findViewById(R.id.tvEmptyNotesSubtext)

        rvNotes.layoutManager = GridLayoutManager(this, 3)
        adapter = NotesAdapter(
            items = emptyList(),
            onItemClick = { note -> showEditNoteDialog(note) },
            onItemLongClick = { note ->
                if (!adapter.isSelectionMode) {
                    enterSelectionMode()
                    adapter.toggleSelection(note.id)
                } else {
                    adapter.toggleSelection(note.id)
                }
            },
            onSelectionCountChanged = { count ->
                updateSelectionHeader(count)
            }
        )
        rvNotes.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnTabAllNotes.setOnClickListener {
            if (currentTab != NoteTab.ALL_NOTES) {
                exitSelectionMode()
                currentTab = NoteTab.ALL_NOTES
                updateTabHighlight()
                loadNotes()
            }
        }

        btnTabArchived.setOnClickListener {
            if (currentTab != NoteTab.ARCHIVED) {
                exitSelectionMode()
                currentTab = NoteTab.ARCHIVED
                updateTabHighlight()
                loadNotes()
            }
        }

        btnClipboardHistory.setOnClickListener {
            startActivity(Intent(this, ClipboardActivity::class.java))
        }

        btnToggleSelect.setOnClickListener {
            enterSelectionMode()
        }

        btnAddNote.setOnClickListener {
            showEditNoteDialog(null)
        }

        btnCloseSelection.setOnClickListener {
            exitSelectionMode()
        }

        btnSelectAll.setOnClickListener {
            val visibleNotes = getCurrentFilteredNotes()
            if (adapter.getSelectedCount() >= visibleNotes.size && visibleNotes.isNotEmpty()) {
                adapter.deselectAll()
            } else {
                adapter.selectAll()
            }
        }

        btnArchiveSelected.setOnClickListener {
            val selectedIds = adapter.selectedIds.toSet()
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "No notes selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val willArchive = (currentTab == NoteTab.ALL_NOTES)
            lifecycleScope.launch {
                notesRepository.archiveNotes(selectedIds, willArchive)
                val actionText = if (willArchive) "Archived" else "Unarchived"
                Toast.makeText(this@NotesActivity, "$actionText ${selectedIds.size} notes", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                loadNotes()
            }
        }

        btnDeleteSelected.setOnClickListener {
            val selectedIds = adapter.selectedIds.toSet()
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "No notes selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Delete Notes")
                .setMessage("Are you sure you want to delete ${selectedIds.size} selected note(s)?")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        notesRepository.deleteNotes(selectedIds)
                        Toast.makeText(this@NotesActivity, "Deleted ${selectedIds.size} note(s)", Toast.LENGTH_SHORT).show()
                        exitSelectionMode()
                        loadNotes()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        etSearchNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilterAndDisplay(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        updateTabHighlight()
    }

    private fun updateTabHighlight() {
        val selectedColor = ContextCompat.getColor(this, R.color.day_night_text_color_contrast)
        val unselectedColor = ContextCompat.getColor(this, R.color.day_night_text_secondary)
        val activeAccent = ContextCompat.getColor(this, R.color.progressbar_tint)

        if (currentTab == NoteTab.ALL_NOTES) {
            btnTabAllNotes.isSelected = true
            tvTabAllNotesText.setTextColor(selectedColor)
            tvTabAllNotesText.paint.isFakeBoldText = true
            ivTabAllNotesIcon.setColorFilter(activeAccent)

            btnTabArchived.isSelected = false
            tvTabArchivedText.setTextColor(unselectedColor)
            tvTabArchivedText.paint.isFakeBoldText = false
            ivTabArchivedIcon.setColorFilter(unselectedColor)

            btnArchiveSelected.text = "Archive"
            btnArchiveSelected.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_archive, 0, 0, 0)
        } else {
            btnTabArchived.isSelected = true
            tvTabArchivedText.setTextColor(selectedColor)
            tvTabArchivedText.paint.isFakeBoldText = true
            ivTabArchivedIcon.setColorFilter(activeAccent)

            btnTabAllNotes.isSelected = false
            tvTabAllNotesText.setTextColor(unselectedColor)
            tvTabAllNotesText.paint.isFakeBoldText = false
            ivTabAllNotesIcon.setColorFilter(unselectedColor)

            btnArchiveSelected.text = "Unarchive"
            btnArchiveSelected.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_unarchive, 0, 0, 0)
        }
    }

    private fun enterSelectionMode() {
        adapter.setSelectionMode(true)
        llNormalHeader.visibility = View.GONE
        llSelectionHeader.visibility = View.VISIBLE
        updateSelectionHeader(adapter.getSelectedCount())
    }

    private fun exitSelectionMode() {
        adapter.setSelectionMode(false)
        llSelectionHeader.visibility = View.GONE
        llNormalHeader.visibility = View.VISIBLE
    }

    private fun updateSelectionHeader(count: Int) {
        tvSelectionCount.text = "$count selected"
        val visibleNotes = getCurrentFilteredNotes()
        if (count >= visibleNotes.size && visibleNotes.isNotEmpty()) {
            btnSelectAll.text = "Deselect All"
        } else {
            btnSelectAll.text = "Select All"
        }

        btnArchiveSelected.isEnabled = count > 0
        btnDeleteSelected.isEnabled = count > 0
        btnArchiveSelected.alpha = if (count > 0) 1.0f else 0.5f
        btnDeleteSelected.alpha = if (count > 0) 1.0f else 0.5f
    }

    private fun loadNotes() {
        lifecycleScope.launch {
            allNotesList = notesRepository.getAllNotes().toMutableList()
            applyFilterAndDisplay(etSearchNotes.text.toString())
        }
    }

    private fun getCurrentFilteredNotes(): List<NoteItem> {
        val query = etSearchNotes.text.toString().trim().lowercase()
        val tabNotes = allNotesList.filter {
            if (currentTab == NoteTab.ALL_NOTES) !it.isArchived else it.isArchived
        }

        return if (query.isBlank()) {
            tabNotes
        } else {
            tabNotes.filter {
                it.title.lowercase().contains(query) || it.content.lowercase().contains(query)
            }
        }
    }

    private fun applyFilterAndDisplay(query: String) {
        val filtered = getCurrentFilteredNotes()
        adapter.updateItems(filtered)

        tvNotesCountBadge.text = "${filtered.size} notes"

        if (filtered.isEmpty()) {
            llEmptyNotesView.visibility = View.VISIBLE
            if (currentTab == NoteTab.ARCHIVED) {
                ivEmptyNotesIcon.setImageResource(R.drawable.ic_archive)
                tvEmptyNotesTitle.text = "No archived notes"
                tvEmptyNotesSubtext.text = "Notes you archive will appear here"
            } else {
                ivEmptyNotesIcon.setImageResource(R.drawable.ic_nav_notes)
                tvEmptyNotesTitle.text = "No notes found"
                tvEmptyNotesSubtext.text = "Click the '+' button to create your first note"
            }
        } else {
            llEmptyNotesView.visibility = View.GONE
        }
    }

    private fun showEditNoteDialog(existingNote: NoteItem?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_note, null)
        val tvEditDialogTitle: TextView = dialogView.findViewById(R.id.tvEditDialogTitle)
        val etTitle: EditText = dialogView.findViewById(R.id.etNoteTitle)
        val etContent: EditText = dialogView.findViewById(R.id.etNoteContent)
        val chkPin: CheckBox = dialogView.findViewById(R.id.chkPinNote)

        var selectedColor = existingNote?.colorHex ?: "#1E293B"

        if (existingNote != null) {
            tvEditDialogTitle.text = "📝 Edit Note"
            etTitle.setText(existingNote.title)
            etContent.setText(existingNote.content)
            chkPin.isChecked = existingNote.isPinned
        } else {
            tvEditDialogTitle.text = "➕ Create New Note"
            chkPin.isChecked = false
        }

        val colorButtons = listOf(
            R.id.btnColorDark to "#1E293B",
            R.id.btnColorEmerald to "#065F46",
            R.id.btnColorBlue to "#1E3A8A",
            R.id.btnColorPurple to "#581C87",
            R.id.btnColorAmber to "#78350F"
        )

        for ((btnId, colorHex) in colorButtons) {
            dialogView.findViewById<Button>(btnId)?.setOnClickListener {
                selectedColor = colorHex
                Toast.makeText(this, "Color selected", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save Note") { _, _ ->
                val title = etTitle.text.toString().trim()
                val content = etContent.text.toString().trim()

                if (title.isNotEmpty() || content.isNotEmpty()) {
                    val noteToSave = existingNote?.apply {
                        this.title = title
                        this.content = content
                        this.colorHex = selectedColor
                        this.isPinned = chkPin.isChecked
                        this.timestamp = System.currentTimeMillis()
                    } ?: NoteItem(
                        title = title,
                        content = content,
                        colorHex = selectedColor,
                        isPinned = chkPin.isChecked,
                        isArchived = false,
                        timestamp = System.currentTimeMillis()
                    )

                    lifecycleScope.launch {
                        notesRepository.addOrUpdateNote(noteToSave)
                        loadNotes()
                        Toast.makeText(this@NotesActivity, "Note saved successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (adapter.isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }
}
