package com.cricketsim.persistence

import android.content.Context
import com.cricketsim.logic.CustomFormat
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The player's custom match formats: one JSON file in the app's private storage, written to a temp file and
 * renamed over the real one (the same approach as TournamentSaveStore). A missing or unreadable file counts as
 * "no custom formats yet"; loading never throws.
 */
class CustomFormatStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "custom_formats.json")
    private val lock = Mutex()
    private val gson = Gson()

    suspend fun load(): List<CustomFormat> = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                if (!file.exists()) {
                    emptyList()
                } else {
                    gson.fromJson(file.readText(), Array<CustomFormat>::class.java)
                        ?.filter { it.name.isNotBlank() && it.overs >= CustomFormat.MIN_OVERS }
                        ?: emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun save(formats: List<CustomFormat>) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(gson.toJson(formats.toTypedArray()))
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
}
