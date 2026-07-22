package com.gamekun.mergeblocks

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Title screen: a big visual anchor (logo + wordmark), a gently floating logo,
 * a pulsing "TAP TO START" action prompt, and tap-anywhere input detection.
 */
class TitleActivity : AppCompatActivity() {

    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_title)
        GameSettings.load(this)

        val logo = findViewById<View>(R.id.titleLogo)
        val prompt = findViewById<TextView>(R.id.tapPrompt)
        val root = findViewById<View>(R.id.titleRoot)

        floatLogo(logo)
        pulse(prompt)

        root.setOnClickListener { goToMenu() }
    }

    private fun goToMenu() {
        if (navigated) return
        navigated = true
        startActivity(Intent(this, MenuActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    /** Slow up/down bob so the emblem feels alive. */
    private fun floatLogo(view: View) {
        view.animate()
            .translationY(-24f)
            .setDuration(1600)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                view.animate().translationY(0f).setDuration(1600)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction { floatLogo(view) }.start()
            }.start()
    }

    /** Pulsing fade on the tap prompt. */
    private fun pulse(view: View) {
        view.animate().alpha(0.25f).setDuration(750).withEndAction {
            view.animate().alpha(1f).setDuration(750).withEndAction { pulse(view) }.start()
        }.start()
    }

    override fun onResume() {
        super.onResume()
        navigated = false
    }

    override fun onDestroy() {
        findViewById<View>(R.id.titleLogo)?.animate()?.cancel()
        findViewById<View>(R.id.tapPrompt)?.animate()?.cancel()
        super.onDestroy()
    }
}
