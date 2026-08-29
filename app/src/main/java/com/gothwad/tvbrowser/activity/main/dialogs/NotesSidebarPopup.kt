package com.gothwad.tvbrowser.activity.main.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
import com.gothwad.tvbrowser.notes.NoteItem
import com.gothwad.tvbrowser.singleton.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class NotesSidebarPopup(private val activity: MainActivity) {

    private val popupWindow: PopupWindow
    private val rootContainer: FrameLayout
    private val contentView: View

    private lateinit var btnNotesBack: ImageButton
    private lateinit var tvNotesTitle: TextView
    private lateinit var btnAddNewNote: Button
    private lateinit var llNoteEditorBox: LinearLayout
    private lateinit var etNoteTitle: EditText
    private lateinit var etNoteContent: EditText
    private lateinit var btnCancelNote: Button
    private lateinit var btnSaveNote: Button
    private lateinit var rvNotes: RecyclerView
    private lateinit var llEmptyNotes: LinearLayout

    private val notesList = mutableListOf<NoteItem>()
    private lateinit var adapter: NotesSidebarAdapter
    private var editingNote: NoteItem? = null

    init {
        rootContainer = object : FrameLayout(activity) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE,
                        KeyEvent.KEYCODE_BUTTON_B -> {
                            if (llNoteEditorBox.visibility == View.VISIBLE) {
                                hideEditor()
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

        contentView = LayoutInflater.from(activity).inflate(R.layout.dialog_sidebar_notes, rootContainer, true)

        val dm = activity.resources.displayMetrics
        val popupWidth = (dm.widthPixels * 0.28f).toInt().coerceIn(300, 560)

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
        btnNotesBack = contentView.findViewById(R.id.btnNotesBack)
        tvNotesTitle = contentView.findViewById(R.id.tvNotesTitle)
        btnAddNewNote = contentView.findViewById(R.id.btnAddNewNote)
        llNoteEditorBox = contentView.findViewById(R.id.llNoteEditorBox)
        etNoteTitle = contentView.findViewById(R.id.etNoteTitle)
        etNoteContent = contentView.findViewById(R.id.etNoteContent)
        btnCancelNote = contentView.findViewById(R.id.btnCancelNote)
        btnSaveNote = contentView.findViewById(R.id.btnSaveNote)
        rvNotes = contentView.findViewById(R.id.rvNotes)
        llEmptyNotes = contentView.findViewById(R.id.llEmptyNotes)

        contentView.findViewById<View>(R.id.vNotesBackdrop).setOnClickListener {
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        rvNotes.layoutManager = LinearLayoutManager(activity)
        adapter = NotesSidebarAdapter(
            items = notesList,
            onItemClick = { note -> editNote(note) },
            onDeleteClick = { note -> deleteNote(note) }
        )
        rvNotes.adapter = adapter
    }

    private fun setupListeners() {
        btnNotesBack.setOnClickListener { dismiss() }

        btnAddNewNote.setOnClickListener {
            editingNote = null
            etNoteTitle.setText("")
            etNoteContent.setText("")
            llNoteEditorBox.visibility = View.VISIBLE
            etNoteTitle.requestFocus()
        }

        btnCancelNote.setOnClickListener {
            hideEditor()
        }

        btnSaveNote.setOnClickListener {
            saveCurrentNote()
        }
    }

    private fun hideEditor() {
        editingNote = null
        llNoteEditorBox.visibility = View.GONE
        btnAddNewNote.requestFocus()
    }

    private fun editNote(note: NoteItem) {
        editingNote = note
        etNoteTitle.setText(note.title)
        etNoteContent.setText(note.content)
        llNoteEditorBox.visibility = View.VISIBLE
        etNoteContent.requestFocus()
    }

    private fun saveCurrentNote() {
        val title = etNoteTitle.text.toString().trim()
        val content = etNoteContent.text.toString().trim()

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(activity, "Cannot save an empty note", Toast.LENGTH_SHORT).show()
            return
        }

        val note = editingNote?.copy(
            title = if (title.isEmpty()) "Untitled Note" else title,
            content = content,
            timestamp = System.currentTimeMillis()
        ) ?: NoteItem(
            id = UUID.randomUUID().toString(),
            title = if (title.isEmpty()) "Untitled Note" else title,
            content = content,
            timestamp = System.currentTimeMillis(),
            colorHex = "#0284C7"
        )

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            db.notesDao().insert(note)
            withContext(Dispatchers.Main) {
                hideEditor()
                loadNotes()
            }
        }
    }

    private fun deleteNote(note: NoteItem) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            db.notesDao().delete(note)
            withContext(Dispatchers.Main) {
                loadNotes()
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

        val popupWidth = (screenWidth * 0.28f).toInt().coerceIn(300, 560)
        val popupHeight = (screenHeight - headerBottom).coerceAtLeast(100)

        popupWindow.width = popupWidth
        popupWindow.height = popupHeight
        popupWindow.isClippingEnabled = false

        val xPos = screenWidth - popupWidth
        popupWindow.showAtLocation(decorView, Gravity.TOP or Gravity.START, xPos, headerBottom)

        loadNotes()

        contentView.post {
            btnNotesBack.requestFocus()
        }
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    private fun loadNotes() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.db
            val all = db.notesDao().getAllNotes()
            withContext(Dispatchers.Main) {
                notesList.clear()
                notesList.addAll(all)
                adapter.notifyDataSetChanged()

                llEmptyNotes.visibility = if (notesList.isEmpty()) View.VISIBLE else View.GONE
                rvNotes.visibility = if (notesList.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}

class NotesSidebarAdapter(
    private val items: List<NoteItem>,
    private val onItemClick: (NoteItem) -> Unit,
    private val onDeleteClick: (NoteItem) -> Unit
) : RecyclerView.Adapter<NotesSidebarAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.llNoteItemRoot)
        val vColor: View = view.findViewById(R.id.vNoteColorStrip)
        val tvTitle: TextView = view.findViewById(R.id.tvNoteTitle)
        val tvContent: TextView = view.findViewById(R.id.tvNoteContent)
        val tvDate: TextView = view.findViewById(R.id.tvNoteDate)
        val btnDelete: ImageButton = view.findViewById(R.id.btnNoteDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_note, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvContent.text = item.content

        try {
            holder.vColor.setBackgroundColor(Color.parseColor(item.colorHex ?: "#0284C7"))
        } catch (e: Exception) {
            holder.vColor.setBackgroundColor(Color.parseColor("#0284C7"))
        }

        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        holder.tvDate.text = sdf.format(Date(item.timestamp))

        holder.root.setOnClickListener { onItemClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
