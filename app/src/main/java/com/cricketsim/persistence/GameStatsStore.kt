package com.cricketsim.persistence

import android.content.Context
import com.cricketsim.logic.GameStatistics
import com.cricketsim.logic.GameStats
import com.cricketsim.logic.MatchState
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The game's full statistics (patch 141): one JSON file in the app's private storage, written to a temp file and renamed over the real one, like the other
 * stores. A missing or unreadable file counts as "no statistics yet". [record] adds one finished match (logic/GameStats.kt decides whether it counts).
 */
class GameStatsStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "game_stats.json")
    private val lock = Mutex()
    private val gson = Gson()

    private fun read(): GameStats = try {
        if (!file.exists()) GameStats() else gson.fromJson(file.readText(), GameStats::class.java) ?: GameStats()
    } catch (e: Exception) {
        GameStats()
    }

    private fun write(stats: GameStats) {
        try {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(gson.toJson(stats))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            // A failed save must never crash the game; the next match tries again.
        }
    }

    suspend fun load(): GameStats = withContext(Dispatchers.IO) { lock.withLock { read() } }

    /** Adds a finished match. A Career match, a simulated one, or one already added leaves the statistics as they are. */
    suspend fun record(finished: MatchState) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val old = read()
                val updated = GameStatistics.record(old, finished)
                if (updated !== old) write(updated)
            }
        }
    }
}
