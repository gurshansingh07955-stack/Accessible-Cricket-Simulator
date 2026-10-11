package com.cricketsim.persistence

import android.content.Context
import com.cricketsim.logic.IplAuctionState
import com.cricketsim.logic.IplData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The saved IPL auction: one JSON file, written to a temp file and renamed over the real one, so a
 * crash mid-save leaves the last good save. A missing, unreadable or old-version file simply counts
 * as "no saved auction"; loading never throws. Same approach as TournamentSaveStore.
 */
class IplSaveStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "saved_ipl_auction.json")
    private val lock = Mutex()
    private val gson = Gson()

    suspend fun load(): IplAuctionState? = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                if (!file.exists()) null
                else gson.fromJson(file.readText(), IplAuctionState::class.java)
                    ?.takeIf { it.version == IplData.SAVE_VERSION && it.players.isNotEmpty() && it.sets.isNotEmpty() }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun save(state: IplAuctionState) {
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
