package com.gothwad.tvbrowser.activity.notes

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R

class NotesActivity : Activity() {

    private lateinit var notesRepository: NotesRepository
    private lateinit var adapter: NotesAdapter
    private lateinit var rvNotes: RecyclerView
    private lateinit var etSearchNotes: EditText
    private lateinit var btnAddNote: Button
    private lateinit var btnBack: ImageButton

    private var allNotesList: MutableList<NoteItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes)

        notesRepository = NotesRepository(this)
        rvNotes = findViewById(R.id.rvNotes)
        etSearchNotes = findViewById(R.id.etSearchNotes)
        btnAddNote = findViewById(R.id.btnAddNote)
        btnBack = findViewById(R.id.btnBack)

        rvNotes.layoutManager = GridLayoutManager(this, 3)
        adapter = NotesAdapter(
            items = emptyList(),
            onItemClick = { note -> showEditNoteDialog(note) },
            onItemLongClick = { note -> showNoteOptionsDialog(note) }
        )
        rvNotes.adapter = adapter

        btnBack.setOnClickListener { finish() }
        btnAddNote.setOnClickListener { showEditNoteDialog(null) }

        etSearchNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterNotes(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadNotes()
        btnAddNote.requestFocus()
    }

    private fun loadNotes() {
        allNotesList = notesRepository.getAllNotes()
        filterNotes(etSearchNotes.text.toString())
    }

    private fun filterNotes(query: String) {
        if (query.isBlank()) {
            adapter.updateItems(allNotesList)
        } else {
            val q = query.trim().lowercase()
            val filtered = allNotesList.filter {
                it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
            }
            adapter.updateItems(filtered)
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
                        timestamp = System.currentTimeMillis()
                    )

                    notesRepository.addOrUpdateNote(noteToSave)
                    loadNotes()
                    Toast.makeText(this, "Note saved successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun showNoteOptionsDialog(note: NoteItem) {
        val options = arrayOf(
            if (note.isPinned) "📌 Unpin Note" else "📌 Pin Note to Top",
            "✏️ Edit Note",
            "🗑️ Delete Note"
        )

        AlertDialog.Builder(this)
            .setTitle(if (note.title.isNotEmpty()) note.title else "Note Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        note.isPinned = !note.isPinned
                        notesRepository.addOrUpdateNote(note)
                        loadNotes()
                    }
                    1 -> showEditNoteDialog(note)
                    2 -> {
                        notesRepository.deleteNote(note.id)
                        loadNotes()
                        Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
