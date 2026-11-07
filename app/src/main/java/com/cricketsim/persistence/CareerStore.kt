package com.cricketsim.persistence

import android.content.Context
import com.cricketsim.logic.CareerProfile
import com.cricketsim.logic.CareerWorld
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The saved careers of My Player: one JSON file in the app's private storage (at most [CareerWorld.MAX_CAREERS]), written to a
 * temp file and renamed over the real one, like the other stores. A missing or unreadable file counts as "no careers yet".
 */
class CareerStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "careers.json")
    private val lock = Mutex()
    private val gson = Gson()

    suspend fun load(): List<CareerProfile> = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                if (!file.exists()) {
                    emptyList()
                } else {
                    gson.fromJson(file.readText(), Array<CareerProfile>::class.java)
                        ?.filter { it.firstName.isNotBlank() && it.id.isNotBlank() }
                        ?.take(CareerWorld.MAX_CAREERS)
                        ?: emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun save(careers: List<CareerProfile>) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(gson.toJson(careers.take(CareerWorld.MAX_CAREERS).toTypedArray()))
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
