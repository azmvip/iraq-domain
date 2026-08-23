package com.redload.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class UpdateRequiredActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update)

        val remoteVersion = intent.getStringExtra("remote_version") ?: "جديدة"
        val updateUrl = intent.getStringExtra("update_url")?.takeIf { it.isNotBlank() }
            ?: BuildConfig.UPDATE_URL
        val currentVersion = BuildConfig.APP_VERSION

        // Show version comparison
        val tvCurrentVersion = findViewById<TextView>(R.id.tvCurrentVersion)
        val tvLatestVersion  = findViewById<TextView>(R.id.tvLatestVersion)
        tvCurrentVersion?.text = currentVersion
        tvLatestVersion?.text  = remoteVersion

        val icon = findViewById<ImageView>(R.id.updateIcon)
        val card = findViewById<View>(R.id.updateCard)
        val downloadBtn = findViewById<Button>(R.id.downloadBtn)
        animateEntrance(icon, card)

        // Download button → open browser
        downloadBtn.setOnClickListener {
            it.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(140)
                    .setInterpolator(OvershootInterpolator(2f)).start()
            }.start()
            openInBrowser(updateUrl)
        }
    }

    private fun animateEntrance(icon: ImageView, card: View) {
        icon.alpha = 0f
        icon.scaleX = 0.5f
        icon.scaleY = 0.5f
        icon.rotation = -25f
        icon.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f)
            .setDuration(550)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()

        card.alpha = 0f
        card.translationY = 50f
        card.animate().alpha(1f).translationY(0f)
            .setStartDelay(200)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun openInBrowser(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                this, "تعذر فتح المتصفح", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
