package com.cricketsim.persistence

import android.content.Context
import com.cricketsim.logic.BowlingLength
import com.cricketsim.logic.DeliveryLength
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.MatchState
import com.cricketsim.logic.MisExecutedLength
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.TossResult
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Type

/**
 * Everything needed to put a match back exactly as it was: the whole
 * MatchState (which already contains both teams' XIs, the score, both
 * innings' statistics, the field, weather, rain state and every pending
 * pick), plus what the match SCREEN keeps itself — the stadium and toss it
 * was created with, the recent commentary, and whether the innings-break
 * screen is still to be shown.
 *
 * Deliberately NOT saved: which sub-screen was open (a delivery in
 * progress, the field screen, the scorecard). A resumed match always opens
 * on its ordinary screen, at the state after the last completed action.
 * The audio director's running tallies (back-to-back boundaries, the
 * bowler's wicket streak) also start fresh, so a hat-trick or a run of
 * fours straddling a resume goes uncommented; that is the only audible
 * difference.
 */
data class MatchSnapshot(
    val version: Int,
    val stadium: Stadium,
    val toss: TossResult,
    val state: MatchState,
    val recentCommentary: List<String>,
    val showInningsBreak: Boolean,
    val breakLastBall: String
)

/**
 * The replacement for the web's saveMatch / loadMatch / clearMatch /
 * hasActiveMatch (helpers/matchState.tsx), which are backed by browser
 * localStorage. The pure state transitions in MatchStateMachine needed no
 * change; this just writes and reads their result.
 *
 * FORMAT. The snapshot is JSON in one file in the app's private storage
 * (`saved_match.json`), produced by Gson's reflection over the existing data
 * classes, so the logic layer did not need annotating or touching. Two
 * things reflection can't do on its own are handled: DeliveryLength (a
 * sealed interface implemented by two enums, held by BallOutcome) gets an
 * explicit adapter, and a `version` number guards the shape.
 *
 * SAFETY.
 * - Writes go to a temp file that is then renamed over the real one, so a
 *   crash or a kill mid-save leaves the previous good save intact rather
 *   than a half-written file.
 * - A missing, unreadable, corrupt or old-version save is simply "no
 *   saved match" — loading never throws and never crashes the app. The
 *   web's loadMatch has a block of backfill code for old saves missing
 *   newer fields; this does not migrate, it discards, which is safe for a
 *   game where a match lasts an hour: bump CURRENT_VERSION whenever any
 *   data class in MatchState's tree changes shape.
 * - Save, load and clear share one lock, so a load right after a leave
 *   can't read a save that is half way through being replaced.
 *
 * WHY REFLECTION IS A RISK. Gson builds Kotlin data classes without
 * running their constructors, so a field missing from the JSON would be
 * null in a non-null Kotlin property. That can't happen for a file this
 * class wrote itself under the same version, and load() checks the critical
 * pieces anyway. It is untested, like everything else written recently.
 */
class MatchSaveStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "saved_match.json")
    private val lock = Mutex()

    private val gson: Gson = GsonBuilder()
        // A hierarchy adapter, because the enums that implement
        // DeliveryLength would otherwise be written as bare names, losing
        // which of the two enums they belong to.
        .registerTypeHierarchyAdapter(DeliveryLength::class.java, DeliveryLengthAdapter())
        .create()

    suspend fun save(snapshot: MatchSnapshot) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    val temp = File(file.parentFile, file.name + ".part")
                    temp.writeText(gson.toJson(snapshot))
                    if (!temp.renameTo(file)) {
                        file.delete()
                        temp.renameTo(file)
                    }
                } catch (e: Exception) {
                    // A failed save must never crash a match; the previous
                    // save (if any) is still on disk.
                }
            }
        }
    }

    /** The saved match, or null if there isn't a usable one. Never throws. */
    suspend fun load(): MatchSnapshot? = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                if (!file.exists()) {
                    null
                } else {
                    val snapshot = gson.fromJson(file.readText(), MatchSnapshot::class.java)
                    if (snapshot != null && isUsable(snapshot)) snapshot else null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching { file.delete() }
                runCatching { File(file.parentFile, file.name + ".part").delete() }
            }
        }
    }

    // Read the pieces as Any? so the compiler can't reason that a non-null
    // Kotlin type is "always" non-null: reflection can leave one null.
    private fun isUsable(snapshot: MatchSnapshot): Boolean {
        val state: Any? = snapshot.state
        val stadium: Any? = snapshot.stadium
        val toss: Any? = snapshot.toss
        val commentary: Any? = snapshot.recentCommentary
        if (snapshot.version != CURRENT_VERSION) return false
        if (state == null || stadium == null || toss == null || commentary == null) return false
        return snapshot.state.battingTeam.players.isNotEmpty() && snapshot.state.bowlingTeam.players.isNotEmpty()
    }

    companion object {
        /** Bump whenever any class in MatchState's tree changes shape; older saves are then discarded. */
        const val CURRENT_VERSION = 1
    }
}

/**
 * A spoken description of a saved match for the Resume button, so a screen-
 * reader user knows what they are resuming before they commit to it.
 */
fun MatchSnapshot.summary(): String {
    val formatName = when (state.format) {
        MatchFormat.T20 -> "T20"
        MatchFormat.ODI -> "One Day International"
        MatchFormat.TEST -> "Test match"
    }
    val innings = if (state.currentInnings == 1) "first innings" else "second innings"
    val score = "${state.battingTeam.name} ${state.score.runs} for ${state.score.wickets} after ${state.score.overs}.${state.score.balls} overs"
    return "${state.userTeam.name} versus ${state.opponentTeam.name}, $formatName, $innings, $score."
}

/** Writes a DeliveryLength as {kind, name} so it can be read back as the right one of its two enums. */
private class DeliveryLengthAdapter : JsonSerializer<DeliveryLength>, JsonDeserializer<DeliveryLength> {

    override fun serialize(src: DeliveryLength, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val json = JsonObject()
        when (src) {
            is BowlingLength -> {
                json.addProperty("kind", "bowling")
                json.addProperty("name", src.name)
            }
            is MisExecutedLength -> {
                json.addProperty("kind", "misexecuted")
                json.addProperty("name", src.name)
            }
            else -> throw IllegalArgumentException("Unknown DeliveryLength: $src")
        }
        return json
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): DeliveryLength {
        val obj = json.asJsonObject
        val name = obj.get("name").asString
        return when (obj.get("kind").asString) {
            "bowling" -> BowlingLength.valueOf(name)
            "misexecuted" -> MisExecutedLength.valueOf(name)
            else -> throw JsonParseException("Unknown DeliveryLength kind")
        }
    }
}
