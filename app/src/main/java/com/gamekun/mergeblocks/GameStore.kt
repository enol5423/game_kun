package com.gamekun.mergeblocks

import android.content.Context
import android.content.SharedPreferences

/** Shared SharedPreferences keys and small helpers used across screens. */
object GameStore {
    const val PREFS = "merge_blocks"

    const val KEY_SAVE = "save_state"
    const val KEY_BEST = "best_score"

    const val KEY_UNDO = "boost_undo"
    const val KEY_HAMMER = "boost_hammer"
    const val KEY_SHUFFLE = "boost_shuffle"
    const val KEY_MEGA = "boost_mega"

    const val START_UNDO = 3
    const val START_HAMMER = 2
    const val START_SHUFFLE = 2
    const val START_MEGA = 1

    const val EXTRA_MODE = "mode"
    const val MODE_NEW = "new"
    const val MODE_CONTINUE = "continue"

    fun prefs(c: Context): SharedPreferences =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSave(c: Context): Boolean =
        !prefs(c).getString(KEY_SAVE, null).isNullOrEmpty()

    fun best(c: Context): Int = prefs(c).getInt(KEY_BEST, 0)

    fun clearSave(c: Context) = prefs(c).edit().remove(KEY_SAVE).apply()

    fun restoreFreeBoosters(c: Context) = prefs(c).edit()
        .putInt(KEY_UNDO, START_UNDO)
        .putInt(KEY_HAMMER, START_HAMMER)
        .putInt(KEY_SHUFFLE, START_SHUFFLE)
        .putInt(KEY_MEGA, START_MEGA)
        .apply()
}
