package com.redload.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.regex.Pattern

/**
 * Lightweight "browse & detect" screen.
 *
 * The user browses any site inside a normal WebView. Every network request the
 * page makes is inspected locally on-device (shouldInterceptRequest) — no
 * external server involved. Requests that look like a video (by file
 * extension) are collected into a list the user can download from directly.
 *
 * This is a heuristic, not a guarantee: sites that stream video as HLS/DASH
 * fragments (many .ts chunks) or that obfuscate the media URL won't always
 * be caught by this simple approach.
 */
class BrowserActivity : AppCompatActivity() {

    companion object {
        // Common direct video file extensions to watch for in requested URLs.
        private val VIDEO_EXTENSION_PATTERN =
            Pattern.compile("\\.(mp4|webm|mov|m3u8|mkv)(\\?.*)?$", Pattern.CASE_INSENSITIVE)
    }

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var goBtn: ImageButton
    private lateinit var backBtn: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var detectedPanel: LinearLayout
    private lateinit var detectedHeader: LinearLayout
    private lateinit var detectedCountText: TextView
    private lateinit var detectedToggleArrow: TextView
    private lateinit var detectedListScroll: View
    private lateinit var detectedListContainer: LinearLayout

    private val mainHandler = Handler(Looper.getMainLooper())
    private val detectedUrls = LinkedHashSet<String>()
    private var panelExpanded = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.browserUrlInput)
        goBtn = findViewById(R.id.browserGoBtn)
        backBtn = findViewById(R.id.browserBackBtn)
        progressBar = findViewById(R.id.browserProgress)
        detectedPanel = findViewById(R.id.detectedPanel)
        detectedHeader = findViewById(R.id.detectedHeader)
        detectedCountText = findViewById(R.id.detectedCountText)
        detectedToggleArrow = findViewById(R.id.detectedToggleArrow)
        detectedListScroll = findViewById(R.id.detectedListScroll)
        detectedListContainer = findViewById(R.id.detectedListContainer)

        setupWebView()

        backBtn.setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else finish()
        }
        goBtn.setOnClickListener { loadFromInput() }
        urlInput.setOnEditorActionListener { _, _, _ -> loadFromInput(); true }
        detectedHeader.setOnClickListener { toggleDetectedPanel() }

        // Start on a neutral, popular starting point — user can type any URL.
        loadUrl("https://www.google.com")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.userAgentString = settings.userAgentString +
            " Mobile Safari/537.36" // helps some sites serve their mobile layout

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { urlInput.setText(it) }
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }

            // Called on a background thread for every resource the page requests.
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString()
                if (reqUrl != null && looksLikeVideo(reqUrl)) {
                    onVideoDetected(reqUrl)
                }
                // Returning null lets the request proceed normally — we're only observing.
                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.setDownloadListener { url, _, _, mimeType, _ ->
            // Some sites trigger a native "download" event directly (e.g. a
            // direct video link opened by the page itself) — catch those too.
            if (looksLikeVideo(url) || (mimeType?.startsWith("video/") == true)) {
                onVideoDetected(url)
            } else {
                downloadUrl(url, suggestedFileName(url))
            }
        }
    }

    private fun looksLikeVideo(url: String): Boolean {
        return VIDEO_EXTENSION_PATTERN.matcher(url).find()
    }

    private fun onVideoDetected(url: String) {
        if (detectedUrls.contains(url)) return
        detectedUrls.add(url)
        mainHandler.post { addDetectedRow(url) }
    }

    private fun addDetectedRow(url: String) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_detected_video, detectedListContainer, false)

        val label = row.findViewById<TextView>(R.id.itemVideoLabel)
        val downloadBtn = row.findViewById<TextView>(R.id.itemDownloadBtn)

        label.text = suggestedFileName(url)
        downloadBtn.setOnClickListener {
            downloadUrl(url, suggestedFileName(url))
        }

        detectedListContainer.addView(row, 0) // newest first
        detectedPanel.visibility = View.VISIBLE
        detectedCountText.text = "تم اكتشاف ${detectedUrls.size} فيديو — اضغط للتحميل"
    }

    private fun toggleDetectedPanel() {
        panelExpanded = !panelExpanded
        detectedListScroll.visibility = if (panelExpanded) View.VISIBLE else View.GONE
        detectedToggleArrow.text = if (panelExpanded) "▲" else "▼"
    }

    private fun suggestedFileName(url: String): String {
        val last = url.substringAfterLast('/').substringBefore('?')
        return if (last.isNotBlank() && last.contains('.')) last
        else "redload_video_${System.currentTimeMillis()}.mp4"
    }

    private fun downloadUrl(url: String, filename: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            request.setTitle(filename)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
            // Some CDNs require a plausible referer/user-agent to serve the file.
            request.addRequestHeader("User-Agent", webView.settings.userAgentString)
            request.addRequestHeader("Referer", webView.url ?: "")

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "بدأ التحميل...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر بدء التحميل: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadFromInput() {
        var text = urlInput.text.toString().trim()
        if (text.isEmpty()) return
        if (!text.startsWith("http://") && !text.startsWith("https://")) {
            text = "https://$text"
        }
        loadUrl(text)
    }

    private fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
