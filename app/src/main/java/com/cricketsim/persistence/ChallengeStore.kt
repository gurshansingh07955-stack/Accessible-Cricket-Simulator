package com.cricketsim.persistence

import android.content.Context
import com.cricketsim.logic.ChallengeData
import com.cricketsim.logic.ChallengeEntry
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Today's challenges and the player's record (patch 161), in `challenges.json`; and, for each challenge, two saved matches:
 *  - `challenge_start_<id>.json`  the challenge exactly as it was built (the start of the situation), so "Try again" always begins from the same position;
 *  - `challenge_play_<id>.json`   the match being played (it saves itself part-way, so leaving a challenge and coming back resumes it).
 * A new day replaces the old day's challenges and deletes their saved matches. A missing or damaged file is simply "no challenges yet".
 */
class ChallengeStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "challenges.json")
    private val lock = Mutex()
    private val gson = Gson()

    fun startStore(id: String) = MatchSaveStore(appContext, "challenge_start_$id.json")

    fun playStore(id: String) = MatchSaveStore(appContext, "challenge_play_$id.json")

    suspend fun load(): ChallengeData = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                val data = if (file.exists()) gson.fromJson(file.readText(), ChallengeData::class.java) else null
                val entries: Any? = data?.entries
                if (data == null || entries == null) ChallengeData() else data
            } catch (e: Exception) {
                ChallengeData()
            }
        }
    }

    suspend fun save(data: ChallengeData) {
        withContext(Dispatchers.IO) {
            lock.withLock { write(data) }
        }
    }

    private fun write(data: ChallengeData) {
        try {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(gson.toJson(data))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            // A failed save must never crash the game; the next change saves again.
        }
    }

    /** Deletes the saved matches of a day that is over. */
    suspend fun clearMatches(old: ChallengeData) {
        for (e in old.entries) {
            startStore(e.id).clear()
            playStore(e.id).clear()
        }
    }

    private suspend fun update(id: String, change: (ChallengeEntry) -> ChallengeEntry, totals: (ChallengeData, ChallengeEntry, ChallengeEntry) -> ChallengeData): ChallengeData =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val current = try {
                    val d = if (file.exists()) gson.fromJson(file.readText(), ChallengeData::class.java) else null
                    val entries: Any? = d?.entries
                    if (d == null || entries == null) ChallengeData() else d
                } catch (e: Exception) {
                    ChallengeData()
                }
                val old = current.entries.find { it.id == id }
                if (old == null) {
                    current
                } else {
                    val updated = change(old)
                    val next = totals(current.copy(entries = current.entries.map { if (it.id == id) updated else it }), old, updated)
                    write(next)
                    next
                }
            }
        }

    /** A new try was started. */
    suspend fun recordAttempt(id: String): ChallengeData =
        update(id, { it.copy(attempts = it.attempts + 1) }, { d, _, _ -> d.copy(attemptsAllTime = d.attemptsAllTime + 1) })

    /** The match was won or lost. A challenge that has been completed stays completed. */
    suspend fun recordResult(id: String, won: Boolean): ChallengeData =
        update(
            id,
            { it.copy(result = if (won || it.result == "WON") "WON" else "LOST") },
            { d, old, updated -> if (old.result != "WON" && updated.result == "WON") d.copy(completedAllTime = d.completedAllTime + 1) else d }
        )
}
