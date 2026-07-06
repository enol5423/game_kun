package com.gamekun.mergeblocks

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var undoButton: TextView
    private lateinit var hammerButton: TextView
    private lateinit var shuffleButton: TextView
    private lateinit var adManager: AdManager
    private var adView: AdView? = null

    private val prefs by lazy { getSharedPreferences("merge_blocks", MODE_PRIVATE) }
    private var bestScore = 0

    // Booster inventory, persisted across sessions.
    private var undoCount = 0
    private var hammerCount = 0
    private var shuffleCount = 0

    companion object {
        private const val REWARD_AMOUNT = 3
        private const val START_UNDO = 3
        private const val START_HAMMER = 2
        private const val START_SHUFFLE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gameView = findViewById(R.id.gameView)
        scoreText = findViewById(R.id.scoreValue)
        bestText = findViewById(R.id.bestValue)
        gameOverOverlay = findViewById(R.id.gameOverOverlay)
        continueButton = findViewById(R.id.continueButton)
        undoButton = findViewById(R.id.undoButton)
        hammerButton = findViewById(R.id.hammerButton)
        shuffleButton = findViewById(R.id.shuffleButton)

        bestScore = prefs.getInt("best_score", 0)
        undoCount = prefs.getInt("boost_undo", START_UNDO)
        hammerCount = prefs.getInt("boost_hammer", START_HAMMER)
        shuffleCount = prefs.getInt("boost_shuffle", START_SHUFFLE)
        updateScores()
        updateBoosterBar()

        findViewById<Button>(R.id.newGameButton).setOnClickListener { startNewGame() }
        findViewById<Button>(R.id.restartButton).setOnClickListener { startNewGame() }

        gameView.onBoardChanged = { updateScores() }
        gameView.onGameOver = {
            adManager.onGameOver()
            continueButton.visibility =
                if (adManager.isRewardedReady) View.VISIBLE else View.GONE
            gameOverOverlay.visibility = View.VISIBLE
        }
        gameView.onHammerHit = { success ->
            if (success) {
                hammerCount--
                saveBoosters()
            }
            gameView.hammerMode = false
            updateBoosterBar()
        }

        undoButton.setOnClickListener {
            if (undoCount <= 0) {
                offerRefill("boost_undo")
            } else if (gameView.game.undo()) {
                undoCount--
                saveBoosters()
                gameOverOverlay.visibility = View.GONE
                gameView.refresh()
                updateScores()
                updateBoosterBar()
            } else {
                toast(getString(R.string.nothing_to_undo))
            }
        }
        hammerButton.setOnClickListener {
            if (hammerCount <= 0) {
                offerRefill("boost_hammer")
            } else {
                gameView.hammerMode = !gameView.hammerMode
                if (gameView.hammerMode) toast(getString(R.string.hammer_hint))
                updateBoosterBar()
            }
        }
        shuffleButton.setOnClickListener {
            if (shuffleCount <= 0) {
                offerRefill("boost_shuffle")
            } else if (gameView.game.shuffle()) {
                shuffleCount--
                saveBoosters()
                gameView.refresh()
                updateScores()
                updateBoosterBar()
            }
        }

        continueButton.setOnClickListener {
            adManager.showRewarded {
                gameView.game.revive()
                gameOverOverlay.visibility = View.GONE
                gameView.refresh()
            }
        }

        adManager = AdManager(this)
        adManager.initialize { loadBanner() }
    }

    /** Empty booster tapped: watch a rewarded ad to refill it. */
    private fun offerRefill(prefKey: String) {
        if (!adManager.isRewardedReady) {
            toast(getString(R.string.no_ad_available))
            return
        }
        toast(getString(R.string.refill_hint, REWARD_AMOUNT))
        adManager.showRewarded {
            when (prefKey) {
                "boost_undo" -> undoCount += REWARD_AMOUNT
                "boost_hammer" -> hammerCount += REWARD_AMOUNT
                "boost_shuffle" -> shuffleCount += REWARD_AMOUNT
            }
            saveBoosters()
            updateBoosterBar()
        }
    }

    private fun updateBoosterBar() {
        undoButton.text = getString(R.string.booster_undo, boosterLabel(undoCount))
        hammerButton.text = getString(R.string.booster_hammer, boosterLabel(hammerCount))
        shuffleButton.text = getString(R.string.booster_shuffle, boosterLabel(shuffleCount))
        hammerButton.isSelected = gameView.hammerMode
        hammerButton.alpha = if (gameView.hammerMode) 1f else 0.9f
    }

    private fun boosterLabel(count: Int) = if (count > 0) count.toString() else "AD"

    private fun saveBoosters() {
        prefs.edit()
            .putInt("boost_undo", undoCount)
            .putInt("boost_hammer", hammerCount)
            .putInt("boost_shuffle", shuffleCount)
            .apply()
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
        gameView.hammerMode = false
        gameOverOverlay.visibility = View.GONE
        updateScores()
        updateBoosterBar()
        gameView.refresh()
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

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

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
