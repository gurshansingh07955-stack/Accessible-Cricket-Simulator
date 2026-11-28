package com.cricketsim.persistence

import android.content.Context

/**
 * Remembers that the welcome screens (ui/home/WelcomeScreens.kt) have been seen, so they come only the first time the game is opened
 * (patch 160). It is set when the player makes the last choice, "Continue to tutorial" or "Skip tutorial"; leaving the app before that shows
 * the welcome again next time.
 */
object WelcomeStore {
    private const val PREFS = "cricket_sim_welcome"
    private const val KEY_DONE = "welcome_done"

    fun isDone(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }
}
