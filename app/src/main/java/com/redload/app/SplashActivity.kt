package com.redload.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Splash / loading screen shown at app startup.
 * While a short branded animation plays, it checks connectivity and app
 * version in the background, then routes to the correct next screen.
 */
class SplashActivity : AppCompatActivity() {

    companion object {
        private const val REMOTE_CONFIG_KEY = "min_supported_version"
        private const val REMOTE_UPDATE_URL_KEY = "update_url"

        // Always 12 hours — Google's recommended production interval.
        private const val FETCH_INTERVAL_SECONDS = 43200L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var minTimeElapsed = false
    private var checkFinished = false
    private var pendingIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splashLogo)
        val appName = findViewById<TextView>(R.id.splashAppName)
        val tagline = findViewById<TextView>(R.id.splashTagline)
        val progress = findViewById<View>(R.id.splashProgress)

        animateEntrance(logo, appName, tagline, progress)

        // Keep the splash visible for a minimum, pleasant duration
        mainHandler.postDelayed({
            minTimeElapsed = true
            proceedIfReady()
        }, 1500)

        checkConnectivityAndVersion()
    }

    // ─── Animations ──────────────────────────────────────────────────────────

    private fun animateEntrance(logo: ImageView, appName: TextView, tagline: TextView, progress: View) {
        logo.alpha = 0f
        logo.scaleX = 0.6f
        logo.scaleY = 0.6f
        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(650)
            .setInterpolator(OvershootInterpolator(1.3f))
            .withEndAction { startBreathing(logo) }
            .start()

        appName.alpha = 0f
        appName.translationY = 24f
        appName.animate().alpha(1f).translationY(0f).setStartDelay(320).setDuration(400).start()

        tagline.alpha = 0f
        tagline.translationY = 24f
        tagline.animate().alpha(1f).translationY(0f).setStartDelay(440).setDuration(400).start()

        progress.alpha = 0f
        progress.animate().alpha(1f).setStartDelay(650).setDuration(300).start()
    }

    private fun startBreathing(view: View) {
        val breathing = ObjectAnimator.ofPropertyValuesHolder(
            view,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f)
        )
        breathing.duration = 900
        breathing.repeatCount = ObjectAnimator.INFINITE
        breathing.repeatMode = ObjectAnimator.REVERSE
        breathing.interpolator = AccelerateDecelerateInterpolator()
        breathing.start()
    }

    // ─── Connectivity & Version Check ────────────────────────────────────────

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            info != null && info.isConnected
        }
    }

    private fun checkConnectivityAndVersion() {
        if (!isNetworkAvailable()) {
            pendingIntent = Intent(this, NoInternetActivity::class.java)
            checkFinished = true
            mainHandler.post { proceedIfReady() }
            return
        }

        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_SECONDS)
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.setDefaultsAsync(
                mapOf(
                    REMOTE_CONFIG_KEY to BuildConfig.APP_VERSION,
                    REMOTE_UPDATE_URL_KEY to BuildConfig.UPDATE_URL
                )
            )

            remoteConfig.fetchAndActivate().addOnCompleteListener { _ ->
                val remoteVersion = remoteConfig.getString(REMOTE_CONFIG_KEY)
                val updateUrl = remoteConfig.getString(REMOTE_UPDATE_URL_KEY)
                    .ifEmpty { BuildConfig.UPDATE_URL }

                pendingIntent = if (remoteVersion.isNotEmpty() &&
                    compareVersions(BuildConfig.APP_VERSION, remoteVersion) < 0) {
                    Intent(this, UpdateRequiredActivity::class.java)
                        .putExtra("remote_version", remoteVersion)
                        .putExtra("update_url", updateUrl)
                } else {
                    Intent(this, MainActivity::class.java)
                }
                checkFinished = true
                proceedIfReady()
            }
        } catch (_: Exception) {
            // Remote Config failed silently — let the user continue
            pendingIntent = Intent(this, MainActivity::class.java)
            checkFinished = true
            mainHandler.post { proceedIfReady() }
        }
    }

    /** Returns negative if a < b, 0 if equal, positive if a > b */
    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val diff = (pa.getOrElse(i) { 0 }) - (pb.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    private fun proceedIfReady() {
        if (minTimeElapsed && checkFinished && pendingIntent != null) {
            startActivity(pendingIntent)
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            finish()
        }
    }
}
