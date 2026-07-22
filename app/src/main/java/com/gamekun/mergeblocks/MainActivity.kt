package com.gamekun.mergeblocks

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var scoreText: TextView
    private lateinit var bestText: TextView
    private lateinit var gameOverOverlay: LinearLayout
    private lateinit var stuckSubtitle: TextView
    private lateinit var stuckBoosterRow: LinearLayout
    private lateinit var stuckOr: TextView
    private lateinit var stuckUndo: LinearLayout
    private lateinit var stuckHammer: LinearLayout
    private lateinit var stuckShuffle: LinearLayout
    private lateinit var stuckMega: LinearLayout
    private lateinit var stuckUndoBadge: TextView
    private lateinit var stuckHammerBadge: TextView
    private lateinit var stuckShuffleBadge: TextView
    private lateinit var stuckMegaBadge: TextView
    private lateinit var extraLifeButton: TextView
    private lateinit var powerupsButton: TextView
    private lateinit var tryAgainButton: TextView

    private lateinit var undoButton: LinearLayout
    private lateinit var hammerButton: LinearLayout
    private lateinit var shuffleButton: LinearLayout
    private lateinit var megaButton: LinearLayout
    private lateinit var undoBadge: TextView
    private lateinit var hammerBadge: TextView
    private lateinit var shuffleBadge: TextView
    private lateinit var megaBadge: TextView

    private lateinit var adManager: AdManager
    private var adView: AdView? = null

    private val prefs by lazy { GameStore.prefs(this) }
    private var bestScore = 0

    // Booster inventory, persisted across sessions.
    private var undoCount = 0
    private var hammerCount = 0
    private var shuffleCount = 0
    private var megaCount = 0

    companion object {
        private const val REWARD_AMOUNT = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        GameSettings.load(this)

        gameView = findViewById(R.id.gameView)
        scoreText = findViewById(R.id.scoreValue)
        bestText = findViewById(R.id.bestValue)
        gameOverOverlay = findViewById(R.id.gameOverOverlay)
        stuckSubtitle = findViewById(R.id.stuckSubtitle)
        stuckBoosterRow = findViewById(R.id.stuckBoosterRow)
        stuckOr = findViewById(R.id.stuckOr)
        stuckUndo = findViewById(R.id.stuckUndo)
        stuckHammer = findViewById(R.id.stuckHammer)
        stuckShuffle = findViewById(R.id.stuckShuffle)
        stuckMega = findViewById(R.id.stuckMega)
        stuckUndoBadge = findViewById(R.id.stuckUndoBadge)
        stuckHammerBadge = findViewById(R.id.stuckHammerBadge)
        stuckShuffleBadge = findViewById(R.id.stuckShuffleBadge)
        stuckMegaBadge = findViewById(R.id.stuckMegaBadge)
        extraLifeButton = findViewById(R.id.extraLifeButton)
        powerupsButton = findViewById(R.id.powerupsButton)
        tryAgainButton = findViewById(R.id.tryAgainButton)
        undoButton = findViewById(R.id.undoButton)
        hammerButton = findViewById(R.id.hammerButton)
        shuffleButton = findViewById(R.id.shuffleButton)
        megaButton = findViewById(R.id.megaButton)
        undoBadge = findViewById(R.id.undoBadge)
        hammerBadge = findViewById(R.id.hammerBadge)
        shuffleBadge = findViewById(R.id.shuffleBadge)
        megaBadge = findViewById(R.id.megaBadge)

        bestScore = GameStore.best(this)
        undoCount = prefs.getInt(GameStore.KEY_UNDO, GameStore.START_UNDO)
        hammerCount = prefs.getInt(GameStore.KEY_HAMMER, GameStore.START_HAMMER)
        shuffleCount = prefs.getInt(GameStore.KEY_SHUFFLE, GameStore.START_SHUFFLE)
        megaCount = prefs.getInt(GameStore.KEY_MEGA, GameStore.START_MEGA)

        startFromIntent()
        updateScores()
        updateBoosterBar()
        startBreathing(undoButton, 0)
        startBreathing(hammerButton, 150)
        startBreathing(shuffleButton, 300)
        startBreathing(megaButton, 450)

        findViewById<View>(R.id.homeButton).setOnClickListener { goHome() }

        gameView.onBoardChanged = { updateScores() }
        // No moves left isn't automatically game over — offer power-ups / options.
        gameView.onGameOver = { showStuck() }
        gameView.onHammerHit = { success ->
            if (success) { hammerCount--; saveBoosters() }
            gameView.tapMode = GameView.TapMode.NONE
            updateBoosterBar()
            reevaluateStuck()
        }
        gameView.onMegaBombHit = { success ->
            if (success) { megaCount--; saveBoosters() }
            gameView.tapMode = GameView.TapMode.NONE
            updateBoosterBar()
            reevaluateStuck()
        }

        undoButton.setOnClickListener {
            bounce(undoButton)
            if (undoCount <= 0) {
                offerRefill(GameStore.KEY_UNDO)
            } else if (gameView.animateUndo()) {
                undoCount--; saveBoosters()
                gameOverOverlay.visibility = View.GONE
                updateScores(); updateBoosterBar()
            } else {
                toast(getString(R.string.nothing_to_undo))
            }
        }
        hammerButton.setOnClickListener {
            bounce(hammerButton)
            if (hammerCount <= 0) {
                offerRefill(GameStore.KEY_HAMMER)
            } else {
                gameView.tapMode =
                    if (gameView.tapMode == GameView.TapMode.HAMMER) GameView.TapMode.NONE
                    else GameView.TapMode.HAMMER
                if (gameView.tapMode == GameView.TapMode.HAMMER) toast(getString(R.string.hammer_hint))
                updateBoosterBar()
            }
        }
        shuffleButton.setOnClickListener {
            bounce(shuffleButton)
            if (shuffleCount <= 0) {
                offerRefill(GameStore.KEY_SHUFFLE)
            } else if (gameView.animateShuffle()) {
                shuffleCount--; saveBoosters()
                updateScores(); updateBoosterBar()
            }
        }
        megaButton.setOnClickListener {
            bounce(megaButton)
            if (megaCount <= 0) {
                offerRefill(GameStore.KEY_MEGA)
            } else {
                gameView.tapMode =
                    if (gameView.tapMode == GameView.TapMode.MEGA_BOMB) GameView.TapMode.NONE
                    else GameView.TapMode.MEGA_BOMB
                if (gameView.tapMode == GameView.TapMode.MEGA_BOMB) toast(getString(R.string.mega_hint))
                updateBoosterBar()
            }
        }

        // --- "No moves" panel: use a power-up to break free, no ad forced. ---
        stuckUndo.setOnClickListener {
            if (undoCount > 0 && gameView.animateUndo()) {
                undoCount--; saveBoosters(); updateScores(); updateBoosterBar()
                reevaluateStuck()
            }
        }
        stuckShuffle.setOnClickListener {
            if (shuffleCount > 0 && gameView.animateShuffle()) {
                shuffleCount--; saveBoosters(); updateScores(); updateBoosterBar()
                reevaluateStuck()
            }
        }
        // Hammer / mega arm a tap: dismiss the panel so the player can aim on the board.
        stuckHammer.setOnClickListener {
            if (hammerCount > 0) {
                gameView.tapMode = GameView.TapMode.HAMMER
                updateBoosterBar()
                hideStuck()
                toast(getString(R.string.hammer_hint))
            }
        }
        stuckMega.setOnClickListener {
            if (megaCount > 0) {
                gameView.tapMode = GameView.TapMode.MEGA_BOMB
                updateBoosterBar()
                hideStuck()
                toast(getString(R.string.mega_hint))
            }
        }

        // Rewarded, opt-in continue paths — only wired to fire when the player wants them.
        extraLifeButton.setOnClickListener {
            adManager.showRewarded {
                gameView.game.revive()
                hideStuck()
                gameView.refresh()
                updateScores()
            }
        }
        powerupsButton.setOnClickListener {
            adManager.showRewarded {
                undoCount += REWARD_AMOUNT
                hammerCount += REWARD_AMOUNT
                shuffleCount += REWARD_AMOUNT
                megaCount += REWARD_AMOUNT
                saveBoosters()
                updateBoosterBar()
                toast(getString(R.string.powerups_added, REWARD_AMOUNT))
                showStuck() // re-render so the freshly granted power-ups appear
            }
        }
        // Smart interstitial: only at this natural break, and only if eligible.
        tryAgainButton.setOnClickListener {
            adManager.maybeShowInterstitial { startNewGame() }
        }

        adManager = AdManager(this)
        adManager.initialize { loadBanner() }
    }

    /** Show the adaptive "no moves" panel, tailored to what the player still has. */
    private fun showStuck() {
        // Undo only helps if there's a move on the stack to take back.
        val undoUsable = undoCount > 0 && gameView.game.canUndo
        val anyChip = undoUsable || hammerCount > 0 || shuffleCount > 0 || megaCount > 0

        stuckBoosterRow.visibility = if (anyChip) View.VISIBLE else View.GONE
        stuckSubtitle.setText(
            if (anyChip) R.string.stuck_use_powerup else R.string.stuck_no_powerup
        )
        chipState(stuckUndo, stuckUndoBadge, undoCount, undoUsable)
        chipState(stuckHammer, stuckHammerBadge, hammerCount, hammerCount > 0)
        chipState(stuckShuffle, stuckShuffleBadge, shuffleCount, shuffleCount > 0)
        chipState(stuckMega, stuckMegaBadge, megaCount, megaCount > 0)

        // Only surface ad options when an ad is actually ready — never a dead button.
        val adReady = adManager.isRewardedReady
        extraLifeButton.visibility = if (adReady) View.VISIBLE else View.GONE
        powerupsButton.visibility = if (adReady) View.VISIBLE else View.GONE
        stuckOr.visibility = if (anyChip && adReady) View.VISIBLE else View.GONE

        gameOverOverlay.visibility = View.VISIBLE
    }

    private fun chipState(chip: View, badge: TextView, count: Int, usable: Boolean) {
        chip.visibility = if (usable) View.VISIBLE else View.GONE
        badge.text = count.toString()
    }

    private fun hideStuck() {
        gameOverOverlay.visibility = View.GONE
    }

    /** After a power-up, keep playing if it opened a move; otherwise re-offer options. */
    private fun reevaluateStuck() {
        if (gameView.game.isGameOver) showStuck() else hideStuck()
    }

    /** New game vs. resume the local save, based on the launching intent. */
    private fun startFromIntent() {
        val mode = intent.getStringExtra(GameStore.EXTRA_MODE) ?: GameStore.MODE_NEW
        val save = prefs.getString(GameStore.KEY_SAVE, null)
        if (mode == GameStore.MODE_CONTINUE && !save.isNullOrEmpty() && gameView.game.loadFrom(save)) {
            gameView.refresh()
        } else {
            gameView.game.reset()
            GameStore.clearSave(this)
            gameView.refresh()
        }
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
                GameStore.KEY_UNDO -> undoCount += REWARD_AMOUNT
                GameStore.KEY_HAMMER -> hammerCount += REWARD_AMOUNT
                GameStore.KEY_SHUFFLE -> shuffleCount += REWARD_AMOUNT
                GameStore.KEY_MEGA -> megaCount += REWARD_AMOUNT
            }
            saveBoosters()
            updateBoosterBar()
        }
    }

    private fun setBadge(badge: TextView, count: Int) {
        badge.text = if (count > 0) count.toString() else getString(R.string.ad_badge)
    }

    private fun updateBoosterBar() {
        setBadge(undoBadge, undoCount)
        setBadge(hammerBadge, hammerCount)
        setBadge(shuffleBadge, shuffleCount)
        setBadge(megaBadge, megaCount)

        undoButton.setBackgroundResource(
            if (undoCount > 0) R.drawable.booster_undo_bg else R.drawable.booster_ad_bg
        )
        shuffleButton.setBackgroundResource(
            if (shuffleCount > 0) R.drawable.booster_shuffle_bg else R.drawable.booster_ad_bg
        )
        val hammerArmed = gameView.tapMode == GameView.TapMode.HAMMER
        hammerButton.setBackgroundResource(
            when {
                hammerArmed -> R.drawable.booster_armed_bg
                hammerCount > 0 -> R.drawable.booster_hammer_bg
                else -> R.drawable.booster_ad_bg
            }
        )
        val megaArmed = gameView.tapMode == GameView.TapMode.MEGA_BOMB
        megaButton.setBackgroundResource(
            when {
                megaArmed -> R.drawable.booster_mega_armed_bg
                megaCount > 0 -> R.drawable.booster_mega_bg
                else -> R.drawable.booster_ad_bg
            }
        )
        armScale(hammerButton, hammerArmed)
        armScale(megaButton, megaArmed)
    }

    private fun armScale(view: View, armed: Boolean) {
        view.animate()
            .scaleX(if (armed) 1.12f else 1f)
            .scaleY(if (armed) 1.12f else 1f)
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
        if (GameSettings.vibration) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(140)
                .setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

    private fun saveBoosters() {
        prefs.edit()
            .putInt(GameStore.KEY_UNDO, undoCount)
            .putInt(GameStore.KEY_HAMMER, hammerCount)
            .putInt(GameStore.KEY_SHUFFLE, shuffleCount)
            .putInt(GameStore.KEY_MEGA, megaCount)
            .apply()
    }

    private fun loadBanner() {
        if (adView != null) return
        val container = findViewById<LinearLayout>(R.id.adContainer)
        container.visibility = View.GONE // stay collapsed until an ad really arrives
        val banner = AdView(this).apply {
            adUnitId = AdManager.BANNER_ID
            setAdSize(AdSize.BANNER)
            adListener = object : AdListener() {
                override fun onAdLoaded() { container.visibility = View.VISIBLE }
                override fun onAdFailedToLoad(error: LoadAdError) { container.visibility = View.GONE }
            }
        }
        container.addView(banner)
        banner.loadAd(AdRequest.Builder().build())
        adView = banner
    }

    private fun startNewGame() {
        gameView.game.reset()
        gameView.tapMode = GameView.TapMode.NONE
        GameStore.clearSave(this)
        gameOverOverlay.visibility = View.GONE
        updateScores()
        updateBoosterBar()
        gameView.refresh()
    }

    private fun updateScores() {
        val score = gameView.game.score
        if (score > bestScore) {
            bestScore = score
            prefs.edit().putInt(GameStore.KEY_BEST, bestScore).apply()
        }
        scoreText.text = score.toString()
        bestText.text = bestScore.toString()
    }

    /** Persist the board locally so Continue can resume it (no cloud). */
    private fun persistGame() {
        if (gameView.game.hasProgress) {
            prefs.edit().putString(GameStore.KEY_SAVE, gameView.game.serialize()).apply()
        } else {
            GameStore.clearSave(this)
        }
    }

    private fun goHome() {
        persistGame()
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        persistGame()
        super.onBackPressed()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onPause() {
        persistGame()
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
        megaButton.animate().cancel()
        adView?.destroy()
        super.onDestroy()
    }
}
