package com.gothwad.tvbrowser.filemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gothwad.tvbrowser.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class InAppFileViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"

        fun start(context: Context, filePath: String) {
            val intent = Intent(context, InAppFileViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, filePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private var targetFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_viewer)

        val path = intent.getStringExtra(EXTRA_FILE_PATH) ?: intent.data?.path
        if (path == null) {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File does not exist: $path", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        targetFile = file

        initViews(file)
        loadFileContent(file)
    }

    private fun initViews(file: File) {
        val tvName = findViewById<TextView>(R.id.tvViewerFileName)
        val tvMeta = findViewById<TextView>(R.id.tvViewerFileMeta)
        val btnBack = findViewById<ImageButton>(R.id.btnViewerBack)
        val btnShare = findViewById<ImageButton>(R.id.btnViewerShare)

        tvName.text = file.name
        val sizeStr = FileViewerContentHelper.formatFileSize(file.length())
        tvMeta.text = "${file.extension.uppercase()} • $sizeStr"

        btnBack.setOnClickListener { finish() }
        btnShare.setOnClickListener {
            FileManagerOperations.shareFile(this, file)
        }
    }

    private fun loadFileContent(file: File) {
        val wv = findViewById<WebView>(R.id.wvFileContent)
        val pb = findViewById<ProgressBar>(R.id.pbViewerLoading)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        pb.visibility = View.VISIBLE

        lifecycleScope.launch {
            val html = withContext(Dispatchers.IO) {
                FileViewerContentHelper.generateHtmlForFile(this@InAppFileViewerActivity, file)
            }
            pb.visibility = View.GONE
            wv.loadDataWithBaseURL("file://${file.parent ?: ""}/", html, "text/html", "UTF-8", null)
        }
    }
}
