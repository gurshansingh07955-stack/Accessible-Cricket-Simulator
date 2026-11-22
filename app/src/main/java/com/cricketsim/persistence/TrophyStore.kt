package com.cricketsim.persistence

import android.content.Context
import com.cricketsim.logic.CplSimulator
import com.cricketsim.logic.TrophyRecord
import com.cricketsim.logic.Trophies
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The trophies the player has won in the game's tournaments (patch 140; career trophies are kept in the career itself): one JSON file in the app's private
 * storage, written to a temp file and renamed over the real one, like the other stores. A missing or unreadable file counts as "no trophies yet".
 *
 * [add] records a trophy once (by its key). [backfill] looks through the tournaments saved on the phone for champions that were crowned before this update and
 * adds their trophies, so earlier wins are not lost; it only ever adds.
 */
class TrophyStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "trophies.json")
    private val lock = Mutex()
    private val gson = Gson()

    private fun read(): List<TrophyRecord> = try {
        if (!file.exists()) emptyList()
        else gson.fromJson(file.readText(), Array<TrophyRecord>::class.java)?.filter { it.key.isNotBlank() } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(all: List<TrophyRecord>) {
        try {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(gson.toJson(all.takeLast(MAX_TROPHIES).toTypedArray()))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            // A failed save must never crash the game; the next save tries again.
        }
    }

    suspend fun load(): List<TrophyRecord> = withContext(Dispatchers.IO) { lock.withLock { read() } }

    /** Records a trophy; one already recorded (same key) is left as it is. */
    suspend fun add(trophy: TrophyRecord) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val all = read()
                if (all.none { it.key == trophy.key }) write(all + trophy)
            }
        }
    }

    /** Adds the trophies of tournaments already saved on the phone whose champion is the player's team (crowned before trophies were recorded). */
    suspend fun backfill() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    val all = read()
                    val known = all.map { it.key }.toHashSet()
                    val found = ArrayList<TrophyRecord>()
                    val files = appContext.filesDir.listFiles { f -> f.name.startsWith("saved_tournament_") && f.name.endsWith(".json") } ?: emptyArray()
                    for (f in files) {
                        // Only a real tournament file loads (a saved part-played match has the same prefix and loads as null).
                        val state = TournamentSaveStore(appContext, f.name).load() ?: continue
                        if (!state.celebrated) continue
                        val trophy = Trophies.fromState(state, CplSimulator.champion(state))?.copy(wonAt = f.lastModified()) ?: continue
                        if (known.add(trophy.key)) found.add(trophy)
                    }
                    if (found.isNotEmpty()) write(all + found)
                } catch (e: Exception) {
                    // A tournament that cannot be read is skipped.
                }
            }
        }
    }

    companion object {
        const val MAX_TROPHIES = 300
    }
}
