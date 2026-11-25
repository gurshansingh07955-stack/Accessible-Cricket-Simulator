package com.cricketsim.persistence

import android.content.Context
import com.cricketsim.logic.SeriesDef
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The player's saved series (up to five): one JSON file in the app's private storage, written to a temp file and renamed over the real one
 * (the same approach as CustomFormatStore). A missing or unreadable file counts as "no series yet"; loading never throws.
 */
class SeriesStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "series.json")
    private val lock = Mutex()
    private val gson = Gson()

    suspend fun load(): List<SeriesDef> = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                if (!file.exists()) {
                    emptyList()
                } else {
                    gson.fromJson(file.readText(), Array<SeriesDef>::class.java)
                        ?.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.legs != null && it.teamKeys != null && it.stadiumIds != null }
                        ?: emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun save(series: List<SeriesDef>) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(gson.toJson(series.toTypedArray()))
                    if (!temp.renameTo(file)) {
                        file.delete()
                        temp.renameTo(file)
                    }
                } catch (e: Exception) {
                    // Nothing more can be done here; the next save tries again.
                }
            }
        }
    }
}
