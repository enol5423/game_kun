package com.gamekun.mergeblocks

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/** Options: vibration + screen-shake toggles and local data resets. */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        GameSettings.load(this)

        findViewById<TextView>(R.id.headerTitle).setText(R.string.settings_title)
        findViewById<View>(R.id.headerBack).setOnClickListener { finish() }

        val vib = findViewById<SwitchCompat>(R.id.switchVibration)
        val shake = findViewById<SwitchCompat>(R.id.switchShake)
        vib.isChecked = GameSettings.vibration
        shake.isChecked = GameSettings.screenShake
        vib.setOnCheckedChangeListener { _, on -> GameSettings.setVibration(this, on) }
        shake.setOnCheckedChangeListener { _, on -> GameSettings.setScreenShake(this, on) }

        findViewById<View>(R.id.btnResetBest).setOnClickListener {
            GameStore.prefs(this).edit().putInt(GameStore.KEY_BEST, 0).apply()
            toast(getString(R.string.reset_done))
        }
        findViewById<View>(R.id.btnResetBoosters).setOnClickListener {
            GameStore.restoreFreeBoosters(this)
            toast(getString(R.string.reset_done))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
