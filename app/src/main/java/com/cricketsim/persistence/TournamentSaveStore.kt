package com.cricketsim.persistence

import android.content.Context
import com.cricketsim.logic.CplData
import com.cricketsim.logic.TournamentState
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The saved CPL tournament: one JSON file in the app's private storage, written to a temp file and
 * renamed over the real one so a crash mid-save leaves the last good save. A missing, unreadable or
 * old-version file simply counts as "no saved tournament"; loading never throws.
 *
 * Same approach as MatchSaveStore. Bump CplData.SAVE_VERSION if a saved class changes shape in a
 * way a default value cannot cover.
 */
class TournamentSaveStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "saved_tournament_cpl2026.json")
    private val lock = Mutex()
    private val gson = Gson()

    suspend fun load(): TournamentState? = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                if (!file.exists()) {
                    null
                } else {
                    gson.fromJson(file.readText(), TournamentState::class.java)
                        ?.takeIf { it.version == CplData.SAVE_VERSION && it.teams.isNotEmpty() && it.fixtures.isNotEmpty() }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun save(state: TournamentState) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(gson.toJson(state))
                    if (!temp.renameTo(file)) {
                        file.delete()
                        temp.renameTo(file)
                    }
                } catch (e: Exception) {
                    // A failed save must never crash the game; the next save tries again.
                }
            }
        }
    }

    suspend fun delete() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    file.delete()
                } catch (e: Exception) {
                    // Nothing more to do.
                }
            }
        }
    }
}
