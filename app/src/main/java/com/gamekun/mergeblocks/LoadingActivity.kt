package com.gamekun.mergeblocks

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Loading screen: shows an asset-loading progress bar with a live percent
 * tracker and rotating gameplay tips, then launches gameplay. It also enforces
 * the internet requirement right before play — progress only advances online.
 */
class LoadingActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bar: ProgressBar
    private lateinit var percent: TextView
    private lateinit var tip: TextView
    private lateinit var noConnection: View

    private var progress = 0
    private var launched = false
    private lateinit var tips: Array<String>
    private var tipIndex = 0
    private var mode = GameStore.MODE_NEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)
        GameSettings.load(this)

        mode = intent.getStringExtra(GameStore.EXTRA_MODE) ?: GameStore.MODE_NEW
        bar = findViewById(R.id.loadingBar)
        percent = findViewById(R.id.loadingPercent)
        tip = findViewById(R.id.loadingTip)
        noConnection = findViewById(R.id.noConnectionRoot)
        findViewById<Button>(R.id.retryButton).setOnClickListener { tick() }

        tips = resources.getStringArray(R.array.loading_tips)
        tipIndex = tips.indices.random()
        tip.text = tips[tipIndex]

        rotateTips()
        tick()
    }

    /** One progress step; stalls (and shows the gate) whenever the device is offline. */
    private fun tick() {
        if (launched) return
        if (!Connectivity.isOnline(this)) {
            noConnection.visibility = View.VISIBLE
            return
        }
        noConnection.visibility = View.GONE
        progress = (progress + (3..7).random()).coerceAtMost(100)
        bar.progress = progress
        percent.text = getString(R.string.loading_percent, progress)
        if (progress >= 100) {
            launch()
        } else {
            handler.postDelayed({ tick() }, 90)
        }
    }

    private fun rotateTips() {
        handler.postDelayed({
            if (launched) return@postDelayed
            tipIndex = (tipIndex + 1) % tips.size
            tip.animate().alpha(0f).setDuration(200).withEndAction {
                tip.text = tips[tipIndex]
                tip.animate().alpha(1f).setDuration(200).start()
            }.start()
            rotateTips()
        }, 2200)
    }

    private fun launch() {
        if (launched) return
        launched = true
        startActivity(
            Intent(this, MainActivity::class.java).putExtra(GameStore.EXTRA_MODE, mode)
        )
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
