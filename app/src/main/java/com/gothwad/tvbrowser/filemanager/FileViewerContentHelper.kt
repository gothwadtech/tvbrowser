package com.gothwad.tvbrowser.filemanager

import android.content.Context
import android.text.TextUtils
import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileViewerContentHelper {

    fun isCodeFile(extension: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in setOf(
            "kt", "java", "py", "js", "ts", "jsx", "tsx", "c", "cpp", "h", "hpp", "cs",
            "html", "htm", "css", "scss", "sass", "less", "xml", "json", "yaml", "yml",
            "sh", "bash", "zsh", "sql", "php", "rb", "go", "rs", "swift", "gradle",
            "properties", "env", "ini", "conf", "cfg", "toml", "bat", "cmd", "diff",
            "patch", "txt", "log", "md", "markdown", "csv", "svg"
        )
    }

    fun isMarkdown(extension: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in setOf("md", "markdown")
    }

    fun isImage(extension: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in setOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "ico", "heic", "avif")
    }

    fun isMedia(extension: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "mp3", "wav", "ogg", "m4a", "flac", "aac")
    }

    fun isPdf(extension: String): Boolean {
        return extension.equals("pdf", ignoreCase = true)
    }

    fun isArchive(extension: String): Boolean {
        val ext = extension.lowercase(Locale.ROOT)
        return ext in setOf("zip", "jar", "apk", "tar", "gz", "7z", "rar", "bz2", "xz")
    }

    fun generateHtmlForFile(context: Context, file: File): String {
        if (!file.exists() || !file.canRead()) {
            return generateErrorHtml(file.name, "File not found or cannot be read: ${file.absolutePath}")
        }

        val ext = file.extension.lowercase(Locale.ROOT)
        val sizeFormatted = formatFileSize(file.length())
        val dateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))

        return when {
            isMarkdown(ext) -> {
                val content = try { file.readText() } catch (e: Exception) { "Error reading file: ${e.message}" }
                generateMarkdownHtml(file.name, content, sizeFormatted, dateFormatted)
            }
            isCodeFile(ext) -> {
                val content = try {
                    if (file.length() > 5 * 1024 * 1024) {
                        "// File is too large (> 5 MB) for syntax highlighted view\n" + file.bufferedReader().useLines { lines ->
                            lines.take(2000).joinToString("\n")
                        } + "\n\n// ... Remaining truncated"
                    } else {
                        file.readText()
                    }
                } catch (e: Exception) {
                    "// Error reading file: ${e.message}"
                }
                generateCodeHtml(file.name, ext, content, sizeFormatted, dateFormatted)
            }
            isImage(ext) -> {
                generateImageHtml(file.name, file.absolutePath, sizeFormatted, dateFormatted)
            }
            isMedia(ext) -> {
                generateMediaHtml(file.name, file.absolutePath, ext, sizeFormatted, dateFormatted)
            }
            else -> {
                val content = try {
                    file.readText()
                } catch (e: Exception) {
                    "Cannot display binary file as text: ${e.message}"
                }
                generateCodeHtml(file.name, ext, content, sizeFormatted, dateFormatted)
            }
        }
    }

    private fun generateMarkdownHtml(fileName: String, rawContent: String, sizeStr: String, dateStr: String): String {
        val escapedRaw = TextUtils.htmlEncode(rawContent)
        val renderedHtml = parseMarkdownToHtml(rawContent)

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
                <title>${TextUtils.htmlEncode(fileName)}</title>
                <style>
                    :root {
                        --bg-primary: #0F172A;
                        --bg-surface: #1E293B;
                        --bg-surface-elevated: #334155;
                        --text-primary: #F8FAFC;
                        --text-secondary: #94A3B8;
                        --accent-color: #0284C7;
                        --border-color: #334155;
                        --code-bg: #0B1120;
                        --inline-code-bg: #1E293B;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                        background: var(--bg-primary);
                        color: var(--text-primary);
                        line-height: 1.6;
                        padding: 0;
                        margin: 0;
                    }
                    .toolbar {
                        position: sticky;
                        top: 0;
                        z-index: 100;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        background: var(--bg-surface);
                        border-bottom: 1px solid var(--border-color);
                        padding: 12px 20px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                    }
                    .file-info {
                        display: flex;
                        flex-direction: column;
                    }
                    .file-name {
                        font-size: 16px;
                        font-weight: 700;
                        color: var(--text-primary);
                    }
                    .file-meta {
                        font-size: 12px;
                        color: var(--text-secondary);
                        margin-top: 2px;
                    }
                    .actions {
                        display: flex;
                        gap: 8px;
                    }
                    button {
                        background: var(--accent-color);
                        color: #FFFFFF;
                        border: none;
                        padding: 8px 14px;
                        border-radius: 6px;
                        font-size: 13px;
                        font-weight: 600;
                        cursor: pointer;
                        transition: opacity 0.2s;
                    }
                    button:active { opacity: 0.8; }
                    .content-container {
                        max-width: 960px;
                        margin: 0 auto;
                        padding: 24px 20px 60px 20px;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        color: #FFFFFF;
                        margin-top: 1.4em;
                        margin-bottom: 0.6em;
                        font-weight: 700;
                    }
                    h1 { font-size: 28px; border-bottom: 1px solid var(--border-color); padding-bottom: 8px; }
                    h2 { font-size: 22px; border-bottom: 1px solid var(--border-color); padding-bottom: 6px; }
                    h3 { font-size: 18px; }
                    p { margin-bottom: 1em; color: #E2E8F0; font-size: 15px; }
                    a { color: #38BDF8; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    ul, ol { margin-left: 24px; margin-bottom: 1em; color: #E2E8F0; }
                    li { margin-bottom: 4px; }
                    blockquote {
                        border-left: 4px solid var(--accent-color);
                        background: var(--bg-surface);
                        padding: 12px 16px;
                        margin: 16px 0;
                        border-radius: 0 8px 8px 0;
                        color: #CBD5E1;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 16px 0;
                        background: var(--bg-surface);
                        border-radius: 8px;
                        overflow: hidden;
                    }
                    th, td {
                        border: 1px solid var(--border-color);
                        padding: 10px 14px;
                        text-align: left;
                    }
                    th { background: var(--bg-surface-elevated); color: #FFFFFF; font-weight: 600; }
                    code {
                        background: var(--inline-code-bg);
                        color: #38BDF8;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-family: monospace;
                        font-size: 13.5px;
                    }
                    pre {
                        background: var(--code-bg);
                        border: 1px solid var(--border-color);
                        border-radius: 8px;
                        padding: 16px;
                        overflow-x: auto;
                        margin: 16px 0;
                    }
                    pre code {
                        background: none;
                        padding: 0;
                        color: #F8FAFC;
                        font-size: 14px;
                        line-height: 1.5;
                    }
                    .raw-view {
                        display: none;
                        white-space: pre-wrap;
                        font-family: monospace;
                        font-size: 14px;
                        color: #E2E8F0;
                        background: var(--code-bg);
                        padding: 16px;
                        border-radius: 8px;
                        border: 1px solid var(--border-color);
                    }
                </style>
            </head>
            <body>
                <div class="toolbar">
                    <div class="file-info">
                        <span class="file-name">${TextUtils.htmlEncode(fileName)}</span>
                        <span class="file-meta">Markdown Document • $sizeStr • $dateStr</span>
                    </div>
                    <div class="actions">
                        <button onclick="toggleView()" id="btnToggle">View Raw</button>
                        <button onclick="copyContent()">Copy All</button>
                    </div>
                </div>
                <div class="content-container">
                    <div id="renderedView">$renderedHtml</div>
                    <pre class="raw-view" id="rawView">$escapedRaw</pre>
                </div>
                <script>
                    var isRaw = false;
                    function toggleView() {
                        isRaw = !isRaw;
                        document.getElementById('renderedView').style.display = isRaw ? 'none' : 'block';
                        document.getElementById('rawView').style.display = isRaw ? 'block' : 'none';
                        document.getElementById('btnToggle').innerText = isRaw ? 'View Formatted' : 'View Raw';
                    }
                    function copyContent() {
                        var text = document.getElementById('rawView').innerText;
                        navigator.clipboard.writeText(text).then(function() {
                            alert('Copied to clipboard!');
                        });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun generateCodeHtml(fileName: String, ext: String, rawContent: String, sizeStr: String, dateStr: String): String {
        val lines = rawContent.lines()
        val lineCount = lines.size

        val formattedLines = StringBuilder()
        for ((index, line) in lines.withIndex()) {
            val lineNum = index + 1
            val highlighted = highlightSyntax(TextUtils.htmlEncode(line), ext)
            formattedLines.append("""<div class="code-line"><span class="line-num">$lineNum</span><span class="line-text">$highlighted</span></div>""")
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
                <title>${TextUtils.htmlEncode(fileName)}</title>
                <style>
                    :root {
                        --bg-primary: #0F172A;
                        --bg-surface: #1E293B;
                        --text-primary: #F8FAFC;
                        --text-secondary: #94A3B8;
                        --accent-color: #0284C7;
                        --border-color: #334155;
                        --gutter-bg: #0B1120;
                        --gutter-text: #64748B;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                        background: var(--bg-primary);
                        color: var(--text-primary);
                        margin: 0;
                        padding: 0;
                    }
                    .toolbar {
                        position: sticky;
                        top: 0;
                        z-index: 100;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        background: var(--bg-surface);
                        border-bottom: 1px solid var(--border-color);
                        padding: 12px 20px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                    }
                    .file-info {
                        display: flex;
                        flex-direction: column;
                    }
                    .file-name {
                        font-size: 16px;
                        font-weight: 700;
                        color: var(--text-primary);
                    }
                    .file-meta {
                        font-size: 12px;
                        color: var(--text-secondary);
                        margin-top: 2px;
                    }
                    .actions {
                        display: flex;
                        gap: 8px;
                    }
                    button {
                        background: var(--accent-color);
                        color: #FFFFFF;
                        border: none;
                        padding: 8px 14px;
                        border-radius: 6px;
                        font-size: 13px;
                        font-weight: 600;
                        cursor: pointer;
                        transition: opacity 0.2s;
                    }
                    button:active { opacity: 0.8; }
                    .code-editor {
                        font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, Courier, monospace;
                        font-size: 13.5px;
                        line-height: 20px;
                        padding: 16px 0 60px 0;
                        background: var(--bg-primary);
                        overflow-x: auto;
                    }
                    .code-line {
                        display: flex;
                        min-width: 100%;
                    }
                    .code-line:hover {
                        background: rgba(255, 255, 255, 0.04);
                    }
                    .line-num {
                        user-select: none;
                        width: 54px;
                        min-width: 54px;
                        text-align: right;
                        padding-right: 16px;
                        color: var(--gutter-text);
                        background: var(--gutter-bg);
                        border-right: 1px solid var(--border-color);
                    }
                    .line-text {
                        padding-left: 16px;
                        white-space: pre;
                        color: #E2E8F0;
                    }
                    /* Syntax highlighting */
                    .kw { color: #F43F5E; font-weight: 600; }
                    .str { color: #34D399; }
                    .com { color: #94A3B8; font-style: italic; }
                    .num { color: #FBBF24; }
                    .tag { color: #38BDF8; font-weight: 600; }
                    .fn { color: #A78BFA; }
                </style>
            </head>
            <body>
                <div class="toolbar">
                    <div class="file-info">
                        <span class="file-name">${TextUtils.htmlEncode(fileName)}</span>
                        <span class="file-meta">${ext.uppercase(Locale.ROOT)} Source • $lineCount lines • $sizeStr • $dateStr</span>
                    </div>
                    <div class="actions">
                        <button onclick="copyContent()">Copy All</button>
                    </div>
                </div>
                <div class="code-editor" id="codeEditor">
                    $formattedLines
                </div>
                <script>
                    function copyContent() {
                        var lines = document.querySelectorAll('.line-text');
                        var text = Array.from(lines).map(function(l) { return l.innerText; }).join('\n');
                        navigator.clipboard.writeText(text).then(function() {
                            alert('Code copied to clipboard!');
                        });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun generateImageHtml(fileName: String, filePath: String, sizeStr: String, dateStr: String): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
                <title>${TextUtils.htmlEncode(fileName)}</title>
                <style>
                    :root {
                        --bg-primary: #0F172A;
                        --bg-surface: #1E293B;
                        --text-primary: #F8FAFC;
                        --text-secondary: #94A3B8;
                        --border-color: #334155;
                        --accent-color: #0284C7;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        background: var(--bg-primary);
                        color: var(--text-primary);
                        min-height: 100vh;
                        display: flex;
                        flex-direction: column;
                    }
                    .toolbar {
                        position: sticky;
                        top: 0;
                        z-index: 100;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        background: var(--bg-surface);
                        border-bottom: 1px solid var(--border-color);
                        padding: 12px 20px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                    }
                    .file-name { font-size: 16px; font-weight: 700; }
                    .file-meta { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
                    .image-container {
                        flex: 1;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 24px;
                        overflow: auto;
                    }
                    img {
                        max-width: 100%;
                        max-height: 85vh;
                        border-radius: 8px;
                        box-shadow: 0 8px 30px rgba(0,0,0,0.5);
                        object-fit: contain;
                    }
                </style>
            </head>
            <body>
                <div class="toolbar">
                    <div class="file-info">
                        <span class="file-name">${TextUtils.htmlEncode(fileName)}</span>
                        <span class="file-meta">Image • $sizeStr • $dateStr</span>
                    </div>
                </div>
                <div class="image-container">
                    <img src="file://$filePath" alt="${TextUtils.htmlEncode(fileName)}" />
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun generateMediaHtml(fileName: String, filePath: String, ext: String, sizeStr: String, dateStr: String): String {
        val isVideo = ext in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp")
        val mediaElement = if (isVideo) {
            """<video controls autoplay name="media" style="width: 100%; max-height: 75vh; border-radius: 8px; background: #000;"><source src="file://$filePath" type="video/$ext">Your browser does not support video playback.</video>"""
        } else {
            """<div style="background: #1E293B; padding: 40px; border-radius: 12px; text-align: center;"><div style="font-size: 48px; margin-bottom: 16px;">🎵</div><audio controls autoplay style="width: 100%; max-width: 500px;"><source src="file://$filePath" type="audio/$ext">Your browser does not support audio playback.</audio></div>"""
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
                <title>${TextUtils.htmlEncode(fileName)}</title>
                <style>
                    :root {
                        --bg-primary: #0F172A;
                        --bg-surface: #1E293B;
                        --text-primary: #F8FAFC;
                        --text-secondary: #94A3B8;
                        --border-color: #334155;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        background: var(--bg-primary);
                        color: var(--text-primary);
                        min-height: 100vh;
                        display: flex;
                        flex-direction: column;
                    }
                    .toolbar {
                        position: sticky;
                        top: 0;
                        z-index: 100;
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        background: var(--bg-surface);
                        border-bottom: 1px solid var(--border-color);
                        padding: 12px 20px;
                        box-shadow: 0 4px 12px rgba(0,0,0,0.3);
                    }
                    .file-name { font-size: 16px; font-weight: 700; }
                    .file-meta { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
                    .media-container {
                        flex: 1;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 24px;
                    }
                </style>
            </head>
            <body>
                <div class="toolbar">
                    <div class="file-info">
                        <span class="file-name">${TextUtils.htmlEncode(fileName)}</span>
                        <span class="file-meta">${if (isVideo) "Video" else "Audio"} Player • $sizeStr • $dateStr</span>
                    </div>
                </div>
                <div class="media-container">
                    $mediaElement
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun generateErrorHtml(fileName: String, errorMsg: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Error Opening File</title>
                <style>
                    body { background: #0F172A; color: #F8FAFC; font-family: sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                    .card { background: #1E293B; padding: 32px; border-radius: 12px; border: 1px solid #334155; max-width: 480px; text-align: center; }
                    h2 { color: #F43F5E; margin-bottom: 12px; }
                    p { color: #94A3B8; font-size: 14px; line-height: 1.5; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>Unable to Open File</h2>
                    <p><strong>${TextUtils.htmlEncode(fileName)}</strong></p>
                    <p style="margin-top: 10px;">${TextUtils.htmlEncode(errorMsg)}</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun highlightSyntax(escapedLine: String, ext: String): String {
        var res = escapedLine
        // Highlight strings
        res = res.replace(Regex("&quot;(.*?)&quot;"), """<span class="str">&quot;$1&quot;</span>""")
        res = res.replace(Regex("&#39;(.*?)&#39;"), """<span class="str">&#39;$1&#39;</span>""")

        // Highlight single-line comments
        if (res.contains("//")) {
            val idx = res.indexOf("//")
            val codePart = res.substring(0, idx)
            val commentPart = res.substring(idx)
            return highlightKeywords(codePart, ext) + """<span class="com">$commentPart</span>"""
        } else if (res.contains("#") && ext in setOf("py", "sh", "bash", "yaml", "yml", "conf", "ini", "properties")) {
            val idx = res.indexOf("#")
            val codePart = res.substring(0, idx)
            val commentPart = res.substring(idx)
            return highlightKeywords(codePart, ext) + """<span class="com">$commentPart</span>"""
        }

        return highlightKeywords(res, ext)
    }

    private fun highlightKeywords(escapedCode: String, ext: String): String {
        var res = escapedCode
        val keywords = listOf(
            "fun", "val", "var", "class", "interface", "object", "package", "import",
            "return", "if", "else", "when", "while", "for", "in", "is", "as", "null",
            "true", "false", "private", "public", "protected", "internal", "override",
            "def", "function", "const", "let", "typeof", "instanceof", "try", "catch",
            "finally", "throw", "throws", "new", "this", "super", "extends", "implements",
            "abstract", "static", "final", "void", "async", "await", "yield", "export",
            "default", "from", "select", "from", "where", "insert", "update", "delete"
        )
        for (kw in keywords) {
            res = res.replace(Regex("""\b($kw)\b"""), """<span class="kw">$1</span>""")
        }
        return res
    }

    private fun parseMarkdownToHtml(markdown: String): String {
        val lines = markdown.lines()
        val sb = StringBuilder()
        var inList = false
        var inCodeBlock = false
        var codeLang = ""
        val codeBlockSb = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    sb.append("<pre><code>").append(TextUtils.htmlEncode(codeBlockSb.toString())).append("</code></pre>\n")
                    codeBlockSb.clear()
                    inCodeBlock = false
                } else {
                    if (inList) { sb.append("</ul>\n"); inList = false }
                    inCodeBlock = true
                    codeLang = trimmed.removePrefix("```").trim()
                }
                continue
            }

            if (inCodeBlock) {
                codeBlockSb.append(line).append("\n")
                continue
            }

            if (trimmed.startsWith("# ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h1>").append(formatInlineMarkdown(trimmed.substring(2))).append("</h1>\n")
            } else if (trimmed.startsWith("## ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h2>").append(formatInlineMarkdown(trimmed.substring(3))).append("</h2>\n")
            } else if (trimmed.startsWith("### ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h3>").append(formatInlineMarkdown(trimmed.substring(4))).append("</h3>\n")
            } else if (trimmed.startsWith("#### ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<h4>").append(formatInlineMarkdown(trimmed.substring(5))).append("</h4>\n")
            } else if (trimmed.startsWith("> ")) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<blockquote>").append(formatInlineMarkdown(trimmed.substring(2))).append("</blockquote>\n")
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                if (!inList) { sb.append("<ul>\n"); inList = true }
                val itemText = trimmed.substring(2)
                if (itemText.startsWith("[ ] ")) {
                    sb.append("<li><input type='checkbox' disabled /> ").append(formatInlineMarkdown(itemText.substring(4))).append("</li>\n")
                } else if (itemText.startsWith("[x] ") || itemText.startsWith("[X] ")) {
                    sb.append("<li><input type='checkbox' checked disabled /> ").append(formatInlineMarkdown(itemText.substring(4))).append("</li>\n")
                } else {
                    sb.append("<li>").append(formatInlineMarkdown(itemText)).append("</li>\n")
                }
            } else if (trimmed.isEmpty()) {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<br/>\n")
            } else {
                if (inList) { sb.append("</ul>\n"); inList = false }
                sb.append("<p>").append(formatInlineMarkdown(line)).append("</p>\n")
            }
        }

        if (inCodeBlock) {
            sb.append("<pre><code>").append(TextUtils.htmlEncode(codeBlockSb.toString())).append("</code></pre>\n")
        }
        if (inList) {
            sb.append("</ul>\n")
        }

        return sb.toString()
    }

    private fun formatInlineMarkdown(text: String): String {
        var res = TextUtils.htmlEncode(text)
        // **bold**
        res = res.replace(Regex("""\*\*(.*?)\*\*"""), "<strong>$1</strong>")
        // *italic*
        res = res.replace(Regex("""\*(.*?)\*"""), "<em>$1</em>")
        // `code`
        res = res.replace(Regex("""`(.*?)`"""), "<code>$1</code>")
        // [text](url)
        res = res.replace(Regex("""\[(.*?)\]\((.*?)\)"""), "<a href=\"$2\">$1</a>")
        return res
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
