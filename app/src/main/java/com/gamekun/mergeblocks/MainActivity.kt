package com.gamekun.mergeblocks

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var scoreText: TextView
    private lateinit var bestText: TextView
    private lateinit var gameOverOverlay: LinearLayout
    private lateinit var continueButton: Button
    private lateinit var adManager: AdManager
    private var adView: AdView? = null

    private val prefs by lazy { getSharedPreferences("merge_blocks", MODE_PRIVATE) }
    private var bestScore = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameView = findViewById(R.id.gameView)
        scoreText = findViewById(R.id.scoreValue)
        bestText = findViewById(R.id.bestValue)
        gameOverOverlay = findViewById(R.id.gameOverOverlay)
        continueButton = findViewById(R.id.continueButton)

        bestScore = prefs.getInt("best_score", 0)
        updateScores()

        findViewById<Button>(R.id.newGameButton).setOnClickListener { startNewGame() }
        findViewById<Button>(R.id.restartButton).setOnClickListener { startNewGame() }

        gameView.onBoardChanged = { updateScores() }
        gameView.onGameOver = {
            adManager.onGameOver()
            continueButton.visibility =
                if (adManager.isRewardedReady) View.VISIBLE else View.GONE
            gameOverOverlay.visibility = View.VISIBLE
        }

        continueButton.setOnClickListener {
            adManager.showRewarded {
                gameView.game.revive()
                gameOverOverlay.visibility = View.GONE
                gameView.invalidate()
            }
        }

        adManager = AdManager(this)
        adManager.initialize { loadBanner() }
    }

    private fun loadBanner() {
        if (adView != null) return
        val container = findViewById<LinearLayout>(R.id.adContainer)
        val banner = AdView(this).apply {
            adUnitId = AdManager.BANNER_ID
            setAdSize(AdSize.BANNER)
        }
        container.addView(banner)
        banner.loadAd(AdRequest.Builder().build())
        adView = banner
    }

    private fun startNewGame() {
        gameView.game.reset()
        gameOverOverlay.visibility = View.GONE
        updateScores()
        gameView.invalidate()
    }

    private fun updateScores() {
        val score = gameView.game.score
        if (score > bestScore) {
            bestScore = score
            prefs.edit().putInt("best_score", bestScore).apply()
        }
        scoreText.text = score.toString()
        bestText.text = bestScore.toString()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }
}
