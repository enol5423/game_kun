package com.gamekun.mergeblocks

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Main menu: New Game, Continue (only if a local save exists), Settings and
 * Extra Content. No cloud — Continue reads the game state saved on this device.
 */
class MenuActivity : AppCompatActivity() {

    private lateinit var continueBtn: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        GameSettings.load(this)

        continueBtn = findViewById(R.id.btnContinue)

        wire(R.id.btnNew) { launchGame(GameStore.MODE_NEW) }
        wire(R.id.btnContinue) {
            if (GameStore.hasSave(this)) launchGame(GameStore.MODE_CONTINUE)
            else toast(getString(R.string.menu_new))
        }
        wire(R.id.btnSettings) { open(SettingsActivity::class.java) }
        wire(R.id.btnExtra) { open(ExtraContentActivity::class.java) }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.menuBest).text = GameStore.best(this).toString()
        // Continue is only meaningful with a save on this device.
        val hasSave = GameStore.hasSave(this)
        continueBtn.alpha = if (hasSave) 1f else 0.45f
        continueBtn.isEnabled = hasSave
    }

    private fun wire(id: Int, action: () -> Unit) {
        findViewById<View>(id).setOnClickListener { v ->
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120)
                    .setInterpolator(OvershootInterpolator()).start()
                action()
            }.start()
        }
    }

    private fun launchGame(mode: String) {
        startActivity(
            Intent(this, LoadingActivity::class.java).putExtra(GameStore.EXTRA_MODE, mode)
        )
    }

    private fun open(cls: Class<*>) {
        startActivity(Intent(this, cls))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
