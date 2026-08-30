package com.gothwad.tvbrowser.filemanager

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gothwad.tvbrowser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PdfViewerDialog(
    private val activity: Activity,
    private val pdfFile: File
) : Dialog(activity, R.style.Theme_Dialog_Fullscreen) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var pageCount: Int = 0
    private var zoomFactor: Float = 1.0f

    private val pageBitmapCache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private lateinit var rvPages: RecyclerView
    private lateinit var tvTitle: TextView
    private lateinit var tvPageCount: TextView
    private lateinit var pbLoading: ProgressBar
    private lateinit var adapter: PdfPageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_pdf_viewer)
        window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

        initViews()
        loadPdf()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvPdfTitle)
        tvPageCount = findViewById(R.id.tvPdfPageCount)
        rvPages = findViewById(R.id.rvPdfPages)
        pbLoading = findViewById(R.id.pbPdfLoading)

        tvTitle.text = pdfFile.name
        val fileSizeStr = FileViewerContentHelper.formatFileSize(pdfFile.length())
        tvPageCount.text = "Loading PDF • $fileSizeStr"

        findViewById<ImageButton>(R.id.btnPdfClose).setOnClickListener {
            dismiss()
        }

        findViewById<ImageButton>(R.id.btnPdfShare).setOnClickListener {
            FileManagerOperations.shareFile(activity, pdfFile)
        }

        findViewById<ImageButton>(R.id.btnPdfZoomIn).setOnClickListener {
            if (zoomFactor < 2.5f) {
                zoomFactor += 0.25f
                pageBitmapCache.evictAll()
                adapter.notifyItemRangeChanged(0, pageCount)
            }
        }

        findViewById<ImageButton>(R.id.btnPdfZoomOut).setOnClickListener {
            if (zoomFactor > 0.6f) {
                zoomFactor -= 0.25f
                pageBitmapCache.evictAll()
                adapter.notifyItemRangeChanged(0, pageCount)
            }
        }

        val layoutManager = LinearLayoutManager(activity)
        rvPages.layoutManager = layoutManager

        rvPages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                if (firstVisible != RecyclerView.NO_POSITION && pageCount > 0) {
                    val current = firstVisible + 1
                    tvPageCount.text = "Page $current of $pageCount • $fileSizeStr"
                }
            }
        })
    }

    private fun loadPdf() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    fileDescriptor?.let {
                        pdfRenderer = PdfRenderer(it)
                        pageCount = pdfRenderer?.pageCount ?: 0
                    }
                }

                pbLoading.visibility = View.GONE

                if (pageCount <= 0) {
                    Toast.makeText(activity, "PDF has no pages or is empty", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@launch
                }

                val fileSizeStr = FileViewerContentHelper.formatFileSize(pdfFile.length())
                tvPageCount.text = "Page 1 of $pageCount • $fileSizeStr"

                adapter = PdfPageAdapter()
                rvPages.adapter = adapter

            } catch (e: Exception) {
                pbLoading.visibility = View.GONE
                Toast.makeText(activity, "Failed to open PDF: ${e.message}", Toast.LENGTH_LONG).show()
                dismiss()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
        pageBitmapCache.evictAll()
        try {
            pdfRenderer?.close()
            pdfRenderer = null
            fileDescriptor?.close()
            fileDescriptor = null
        } catch (_: Exception) {}
    }

    private inner class PdfPageAdapter : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {

        override fun getItemCount(): Int = pageCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return PdfPageViewHolder(view)
        }

        override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun onViewRecycled(holder: PdfPageViewHolder) {
            super.onViewRecycled(holder)
            holder.cancelRendering()
        }

        inner class PdfPageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivPage: ImageView = itemView.findViewById(R.id.ivPdfPageImage)
            private val tvPageNum: TextView = itemView.findViewById(R.id.tvPdfPageNumber)
            private var renderJob: Job? = null

            fun bind(pageIndex: Int) {
                tvPageNum.text = "Page ${pageIndex + 1} of $pageCount"
                val cached = pageBitmapCache.get(pageIndex)
                if (cached != null && !cached.isRecycled) {
                    ivPage.setImageBitmap(cached)
                    return
                }

                ivPage.setImageDrawable(null)
                renderJob?.cancel()
                renderJob = scope.launch {
                    val bitmap = renderPageBitmap(pageIndex)
                    if (bitmap != null) {
                        pageBitmapCache.put(pageIndex, bitmap)
                        ivPage.setImageBitmap(bitmap)
                    }
                }
            }

            fun cancelRendering() {
                renderJob?.cancel()
                renderJob = null
            }
        }
    }

    private suspend fun renderPageBitmap(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        synchronized(renderer) {
            try {
                val page = renderer.openPage(pageIndex)
                val baseWidth = page.width
                val baseHeight = page.height

                val scale = (1.5f * zoomFactor).coerceIn(0.5f, 3.0f)
                val renderWidth = (baseWidth * scale).toInt().coerceAtLeast(100)
                val renderHeight = (baseHeight * scale).toInt().coerceAtLeast(100)

                val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }
}
