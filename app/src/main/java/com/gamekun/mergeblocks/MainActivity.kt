package com.gamekun.mergeblocks

import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
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
        private val READY_COLOR = Color.WHITE
        private val EMPTY_COLOR = Color.parseColor("#0F4D2A")
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
        startBreathing(undoButton, 0)
        startBreathing(hammerButton, 200)
        startBreathing(shuffleButton, 400)

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
            bounce(undoButton)
            if (undoCount <= 0) {
                offerRefill("boost_undo")
            } else if (gameView.animateUndo()) {
                undoCount--
                saveBoosters()
                gameOverOverlay.visibility = View.GONE
                updateScores()
                updateBoosterBar()
            } else {
                toast(getString(R.string.nothing_to_undo))
            }
        }
        hammerButton.setOnClickListener {
            bounce(hammerButton)
            if (hammerCount <= 0) {
                offerRefill("boost_hammer")
            } else {
                gameView.hammerMode = !gameView.hammerMode
                if (gameView.hammerMode) {
                    toast(getString(R.string.hammer_hint))
                    hammerButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                updateBoosterBar()
            }
        }
        shuffleButton.setOnClickListener {
            bounce(shuffleButton)
            if (shuffleCount <= 0) {
                offerRefill("boost_shuffle")
            } else if (gameView.animateShuffle()) {
                shuffleCount--
                saveBoosters()
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

    /** Builds a two-line label: big icon on top, count (or "AD") as a small badge below. */
    private fun boosterLabel(icon: String, count: Int): SpannableString {
        val badge = if (count > 0) count.toString() else getString(R.string.ad_badge)
        val full = "$icon\n$badge"
        val badgeStart = icon.length + 1
        return SpannableString(full).apply {
            setSpan(AbsoluteSizeSpan(26, true), 0, icon.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(13, true), badgeStart, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan(if (count > 0) READY_COLOR else EMPTY_COLOR),
                badgeStart, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun updateBoosterBar() {
        undoButton.text = boosterLabel("↩", undoCount)
        hammerButton.text = boosterLabel("🔨", hammerCount)
        shuffleButton.text = boosterLabel("🔀", shuffleCount)

        undoButton.setBackgroundResource(if (undoCount > 0) R.drawable.booster_undo_bg else R.drawable.booster_ad_bg)
        shuffleButton.setBackgroundResource(if (shuffleCount > 0) R.drawable.booster_shuffle_bg else R.drawable.booster_ad_bg)
        hammerButton.setBackgroundResource(
            when {
                gameView.hammerMode -> R.drawable.booster_armed_bg
                hammerCount > 0 -> R.drawable.booster_hammer_bg
                else -> R.drawable.booster_ad_bg
            }
        )
        hammerButton.animate()
            .scaleX(if (gameView.hammerMode) 1.12f else 1f)
            .scaleY(if (gameView.hammerMode) 1.12f else 1f)
            .setInterpolator(OvershootInterpolator())
            .setDuration(180)
            .start()
    }

    /** Subtle continuous "look at me" breathing pulse so boosters aren't easy to miss. */
    private fun startBreathing(view: View, startDelay: Long) {
        view.animate()
            .alpha(0.82f)
            .setStartDelay(startDelay)
            .setDuration(1200)
            .withEndAction {
                view.animate().alpha(1f).setDuration(1200).withEndAction {
                    startBreathing(view, 0)
                }.start()
            }.start()
    }

    private fun bounce(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(140)
                .setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

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
        undoButton.animate().cancel()
        hammerButton.animate().cancel()
        shuffleButton.animate().cancel()
        adView?.destroy()
        super.onDestroy()
    }
}
