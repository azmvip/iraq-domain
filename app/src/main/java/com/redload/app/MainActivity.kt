package com.redload.app

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import com.google.firebase.analytics.FirebaseAnalytics
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CHANNEL_ID = "redload_download"
        private const val CHANNEL_NAME = "تقدم التحميل"
        private const val DOWNLOADING_TEXT = "جارٍ تحميل فيديو تيك توك..."
    }

    // API key loaded from BuildConfig (set via local.properties → buildConfigField)
    private val RAPIDAPI_KEY: String by lazy { BuildConfig.RAPIDAPI_KEY }
    private val RAPIDAPI_HOST: String by lazy { BuildConfig.RAPIDAPI_HOST }

    private lateinit var urlInput: EditText
    private lateinit var fetchBtn: Button
    private lateinit var statusMsg: TextView
    private lateinit var resultCard: LinearLayout
    private lateinit var resultTitle: TextView
    private lateinit var statLikes: TextView
    private lateinit var statDuration: TextView
    private lateinit var dlMp4Hd: LinearLayout
    private lateinit var dlMp3: LinearLayout
    private lateinit var pasteBtn: ImageButton
    private lateinit var loadingLayout: View
    private lateinit var headerCard: View
    private lateinit var urlCard: View
    private lateinit var howtoCard: View

    private var mp4Url: String? = null
    private var mp3Url: String? = null
    private var fileBaseName: String = "redload_video"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val analytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createNotificationChannel()

        urlInput      = findViewById(R.id.urlInput)
        fetchBtn      = findViewById(R.id.fetchBtn)
        statusMsg     = findViewById(R.id.statusMsg)
        resultCard    = findViewById(R.id.resultCard)
        resultTitle   = findViewById(R.id.resultTitle)
        statLikes     = findViewById(R.id.statLikes)
        statDuration  = findViewById(R.id.statDuration)
        dlMp4Hd       = findViewById(R.id.dlMp4Hd)
        dlMp3         = findViewById(R.id.dlMp3)
        pasteBtn      = findViewById(R.id.pasteBtn)
        loadingLayout = findViewById(R.id.loadingLayout)
        headerCard    = findViewById(R.id.headerCard)
        urlCard       = findViewById(R.id.urlCard)
        howtoCard     = findViewById(R.id.howtoCard)

        pasteBtn.setOnClickListener { it.bounce(); pasteFromClipboard() }
        findViewById<View>(R.id.browseEntryCard).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }
        fetchBtn.setOnClickListener { it.bounce(); handleFetch() }
        dlMp4Hd.setOnClickListener { it.bounce(); downloadFile(mp4Url, "$fileBaseName.mp4") }
        dlMp3.setOnClickListener   { it.bounce(); downloadFile(mp3Url, "$fileBaseName.mp3") }

        animateEntrance()
    }

    // ─── Entrance animations ─────────────────────────────────────────────────

    private fun animateEntrance() {
        val views = listOf(headerCard, urlCard, howtoCard)
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 40f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(90L * index)
                .setDuration(360)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** Small tactile "press" bounce used on every clickable element. */
    private fun View.bounce() {
        animate().scaleX(0.94f).scaleY(0.94f).setDuration(80)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(140)
                    .setInterpolator(OvershootInterpolator(2f)).start()
            }.start()
    }

    /** Fade + slide reveal used when the result card appears. */
    private fun revealResultCard() {
        resultCard.alpha = 0f
        resultCard.translationY = 30f
        resultCard.visibility = View.VISIBLE
        resultCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(380)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    // ─── Connectivity ────────────────────────────────────────────────────────

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            return info != null && info.isConnected
        }
    }

    private fun goToNoInternet() {
        startActivity(Intent(this, NoInternetActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    // ─── Clipboard ───────────────────────────────────────────────────────────

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            urlInput.setText(clip.getItemAt(0).text)
        } else {
            toast("لا يوجد نص في الحافظة")
        }
    }

    // ─── Status / Toast ──────────────────────────────────────────────────────

    private fun setStatus(msg: String, isError: Boolean) {
        if (msg.isEmpty()) {
            statusMsg.visibility = View.GONE
        } else {
            statusMsg.text = msg
            statusMsg.setTextColor(
                if (isError) getColor(R.color.red) else getColor(R.color.grey_600)
            )
            statusMsg.visibility = View.VISIBLE
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ─── Video ID extraction ──────────────────────────────────────────────────

    private fun extractVideoId(url: String): String? {
        if (Pattern.compile("^(https?://)?(vt|vm|t)\\.tiktok\\.com/", Pattern.CASE_INSENSITIVE)
                .matcher(url).find()) return url
        if (Pattern.compile("^(https?://)?tiktok\\.com/t/", Pattern.CASE_INSENSITIVE)
                .matcher(url).find()) return url
        var m = Pattern.compile("/video/(\\d+)").matcher(url)
        if (m.find()) return m.group(1)
        m = Pattern.compile("(\\d{15,25})").matcher(url)
        if (m.find()) return m.group(1)
        return null
    }

    private fun formatNumber(n: Long): String = when {
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
        n >= 1_000     -> String.format("%.1fK", n / 1_000.0)
        else           -> n.toString()
    }

    // ─── Fetch ───────────────────────────────────────────────────────────────

    private fun handleFetch() {
        val raw = urlInput.text.toString().trim()
        if (raw.isEmpty()) { setStatus("الصق رابط الفيديو أولاً", true); return }

        if (!isNetworkAvailable()) {
            goToNoInternet(); return
        }

        val videoId = extractVideoId(raw)
        if (videoId == null) {
            setStatus("لم أستطع استخراج معرف الفيديو، جرّب رابط تيك توك صحيح", true)
            return
        }

        fetchBtn.isEnabled = false
        fetchBtn.text = "جاري الجلب..."
        loadingLayout.visibility = View.VISIBLE
        setStatus("", false)
        resultCard.visibility = View.GONE

        Thread {
            try {
                val apiUrlTarget = if (videoId.startsWith("http")) videoId
                    else "https://www.tiktok.com/video/$videoId"

                val endpoint = "https://${RAPIDAPI_HOST}/?url=" +
                        URLEncoder.encode(apiUrlTarget, "UTF-8") + "&hd=1"

                val url  = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("X-RapidAPI-Key",  RAPIDAPI_KEY)
                conn.setRequestProperty("X-RapidAPI-Host", RAPIDAPI_HOST)
                conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/96.0 Safari/537.36")
                conn.connectTimeout = 20000
                conn.readTimeout    = 20000
                conn.instanceFollowRedirects = true

                val responseCode = conn.responseCode
                if (responseCode != 200) throw Exception("فشل الطلب ($responseCode)")

                val reader   = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) response.append(line)
                reader.close()

                val json = JSONObject(response.toString())
                if (json.optInt("code", -1) != 0)
                    throw Exception(json.optString("msg", "تعذّر جلب بيانات الفيديو"))

                val data     = json.getJSONObject("data")
                val title    = data.optString("title", "فيديو بدون عنوان")
                val likes    = data.optLong("digg_count", 0)
                val duration = data.optInt("duration", 0)
                val hdPlay   = data.optString("hdplay",
                                   data.optString("play",
                                       data.optString("wmplay", "")))
                val music    = data.optString("music",
                                   data.optString("music_info", ""))

                val cleanBase = (if (title.isNotEmpty()) title else videoId)
                    .replace(Regex("[^\\w\\-]+"), "_").take(40).ifEmpty { videoId }

                mainHandler.post {
                    resultTitle.text = title
                    statLikes.text   = formatNumber(likes)
                    statDuration.text = if (duration > 0) "$duration ثانية" else "—"
                    mp4Url      = hdPlay
                    fileBaseName = cleanBase

                    if (music.isNotEmpty()) {
                        mp3Url = music
                        dlMp3.visibility = View.VISIBLE
                    } else {
                        mp3Url = null
                        dlMp3.visibility = View.GONE
                    }

                    revealResultCard()
                    loadingLayout.visibility = View.GONE
                    toast("تم جلب الفيديو بنجاح")
                    fetchBtn.isEnabled = true
                    fetchBtn.text      = "جلب الفيديو"
                }

            } catch (e: Exception) {
                mainHandler.post {
                    setStatus(e.message ?: "حدث خطأ غير متوقع", true)
                    loadingLayout.visibility = View.GONE
                    fetchBtn.isEnabled = true
                    fetchBtn.text      = "جلب الفيديو"
                }
            }
        }.start()
    }

    // ─── Download with real progress ────────────────────────────────────────

    private fun downloadFile(url: String?, filename: String) {
        if (url.isNullOrEmpty()) { toast("الرابط غير متوفر"); return }
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(filename)
                setDescription("RedLoad — جاري التحميل")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                addRequestHeader("User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/96.0 Safari/537.36")
                addRequestHeader("Referer", "https://www.tiktok.com/")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val dm  = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id  = dm.enqueue(request)
            toast("بدأ التحميل...")
            trackProgress(id, filename)

            // Custom Analytics event: counts every successful download start
            val analyticsBundle = android.os.Bundle().apply {
                putString("file_type", if (filename.endsWith(".mp3")) "mp3" else "mp4")
            }
            analytics.logEvent("video_downloaded", analyticsBundle)

        } catch (e: Exception) {
            toast("تعذّر بدء التحميل: ${e.message}")
        }
    }

    private fun trackProgress(downloadId: Long, filename: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = downloadId.toInt()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(DOWNLOADING_TEXT)
            .setContentText("جاري التحميل...")
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        nm.notify(notifId, builder.build())

        Thread {
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var active = true
            while (active) {
                val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (cursor != null && cursor.moveToFirst()) {
                    val status     = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total      = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    cursor.close()

                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            if (total > 0) {
                                val pct  = (downloaded * 100 / total).toInt()
                                val mb   = "%.1f".format(downloaded / 1_048_576.0)
                                val tmb  = "%.1f".format(total / 1_048_576.0)
                                builder.setProgress(100, pct, false)
                                    .setContentText("$pct%  •  $mb / $tmb MB")
                            } else {
                                builder.setProgress(0, 0, true)
                                    .setContentText("جاري التحميل...")
                            }
                            nm.notify(notifId, builder.build())
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            nm.cancel(notifId)
                            active = false
                            mainHandler.post { toast("اكتمل التحميل — $filename") }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            nm.cancel(notifId)
                            active = false
                            mainHandler.post { toast("فشل التحميل، تحقق من الاتصال") }
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            builder.setContentText("موقوف مؤقتاً...")
                            nm.notify(notifId, builder.build())
                        }
                    }
                } else {
                    cursor?.close()
                    active = false
                }
                if (active) Thread.sleep(800)
            }
        }.start()
    }

    // ─── Notification Channel ────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات تقدم تنزيل الملفات"
                enableLights(false)
                enableVibration(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }
}
