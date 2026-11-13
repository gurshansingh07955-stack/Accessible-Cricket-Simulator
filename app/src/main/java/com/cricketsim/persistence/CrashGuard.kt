package com.cricketsim.persistence

import android.content.Context
import java.io.File

/**
 * Stops a crash from repeating. When the game crashes it writes a small flag file (and then crashes as it would have). A match that was resumed marks itself while it is open
 * (MatchSaveStore.markResume, cleared when the match screen is left normally); the next time that saved match is loaded, if a crash happened after it was marked, the saved match is
 * discarded instead of being resumed again, so a match that crashes the game on resume cannot lock the player out of the game. A match whose screen was only closed by the system (no crash)
 * is kept. Installed once, from MainActivity.
 */
object CrashGuard {

    private const val FLAG = "crash_flag"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val dir = context.applicationContext.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(dir, FLAG).writeText(System.currentTimeMillis().toString()) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** True if the game crashed at or after [time] (milliseconds since 1970). */
    fun crashedSince(dir: File?, time: Long): Boolean {
        val flag = File(dir, FLAG)
        return flag.exists() && flag.lastModified() >= time
    }
}
