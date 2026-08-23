package com.redload.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class NoInternetActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_internet)

        val icon = findViewById<ImageView>(R.id.noInternetIcon)
        val title = findViewById<TextView>(R.id.noInternetTitle)
        val msg = findViewById<TextView>(R.id.noInternetMsg)
        val retryBtn = findViewById<Button>(R.id.retryBtn)

        animateEntrance(icon, title, msg, retryBtn)

        retryBtn.setOnClickListener {
            it.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(140)
                    .setInterpolator(OvershootInterpolator(2f)).start()
            }.start()

            if (isNetworkAvailable()) {
                // Restart full flow from the beginning (splash → main)
                startActivity(android.content.Intent(this, SplashActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                              android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            } else {
                android.widget.Toast.makeText(
                    this, "لا يزال لا يوجد اتصال بالإنترنت", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun animateEntrance(icon: ImageView, title: TextView, msg: TextView, retryBtn: Button) {
        icon.alpha = 0f
        icon.scaleX = 0.6f
        icon.scaleY = 0.6f
        icon.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(1.2f))
            .withEndAction { startShake(icon) }
            .start()

        title.alpha = 0f
        title.translationY = 20f
        title.animate().alpha(1f).translationY(0f).setStartDelay(200).setDuration(350).start()

        msg.alpha = 0f
        msg.translationY = 20f
        msg.animate().alpha(1f).translationY(0f).setStartDelay(300).setDuration(350).start()

        retryBtn.alpha = 0f
        retryBtn.translationY = 20f
        retryBtn.animate().alpha(1f).translationY(0f).setStartDelay(420).setDuration(350).start()
    }

    /** Gentle looping "wobble" on the wifi-off icon to draw attention. */
    private fun startShake(view: ImageView) {
        view.animate()
            .rotationBy(-6f)
            .setDuration(700)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                view.animate().rotationBy(12f).setDuration(700)
                    .withEndAction {
                        view.animate().rotationBy(-6f).setDuration(700)
                            .withEndAction { view.rotation = 0f }
                            .start()
                    }.start()
            }.start()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net  = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            info != null && info.isConnected
        }
    }
}
