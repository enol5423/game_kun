package com.gamekun.mergeblocks

import android.content.Context

/**
 * Lightweight global settings backed by SharedPreferences, read by the game view
 * and every screen. Call [load] in each Activity's onCreate so the in-memory
 * flags reflect the user's latest choices.
 */
object GameSettings {

    private const val PREFS = "merge_blocks"

    var vibration = true
        private set
    var screenShake = true
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        vibration = p.getBoolean("set_vibration", true)
        screenShake = p.getBoolean("set_shake", true)
    }

    fun setVibration(context: Context, on: Boolean) {
        vibration = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("set_vibration", on).apply()
    }

    fun setScreenShake(context: Context, on: Boolean) {
        screenShake = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("set_shake", on).apply()
    }
}
