package com.gamekun.mergeblocks

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Boot screen: shows the company logo, the engine credit, then the legal /
 * middleware notice in sequence, and gates on an internet connection (the
 * game requires one) before handing off to the title screen.
 */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var noConnection: View
    private var advanced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        GameSettings.load(this)

        title = findViewById(R.id.splashTitle)
        subtitle = findViewById(R.id.splashSubtitle)
        noConnection = findViewById(R.id.noConnectionRoot)
        findViewById<Button>(R.id.retryButton).setOnClickListener { proceed() }

        // Stage 0 (company) is already in the layout; schedule the next stages.
        handler.postDelayed({ stage(getString(R.string.splash_engine), "") }, 1300)
        handler.postDelayed({
            stage(getString(R.string.company_name), getString(R.string.splash_legal))
        }, 2600)
        handler.postDelayed({ proceed() }, 4400)
    }

    /** Cross-fades the title/subtitle to the next splash stage. */
    private fun stage(newTitle: String, newSubtitle: String) {
        if (isFinishing) return
        title.animate().alpha(0f).setDuration(220).withEndAction {
            title.text = newTitle
            subtitle.text = newSubtitle
            title.animate().alpha(1f).setDuration(260).start()
        }.start()
        subtitle.animate().alpha(0f).setDuration(220).start()
        handler.postDelayed({ subtitle.animate().alpha(1f).setDuration(260).start() }, 240)
    }

    private fun proceed() {
        if (advanced || isFinishing) return
        if (!Connectivity.isOnline(this)) {
            noConnection.visibility = View.VISIBLE
            return
        }
        advanced = true
        startActivity(Intent(this, TitleActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
