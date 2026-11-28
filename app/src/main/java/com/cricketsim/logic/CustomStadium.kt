package com.cricketsim.logic

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * A ground the player designed (Home > Advanced Customisation > Create Custom Stadiums).
 *
 * It stores what drives play (the pitch, the boundary size and the weather numbers) and the average T20 first-innings
 * score the game worked out from them. Every custom ground is listed under the country "Custom" (see [CustomStadiums.COUNTRY]),
 * which is how it shows up in the quick-match stadium list, in series and in the custom tournament stadium picker.
 */
data class CustomStadium(
    val id: String,
    val name: String,
    val pitch: PitchType,
    val boundary: BoundarySize,
    val baseRain: Double,
    val humidity: Int,
    val temperature: Int,
    val wind: Int,
    val dew: Double,
    val weatherText: String,
    val averageScore: Int
)

/** One weather the player can pick (or one copied from a game ground): the numbers the weather system reads. */
class WeatherOption(
    val label: String,
    val detail: String,
    val rain: Double,
    val humidity: Int,
    val temperature: Int,
    val wind: Int,
    val dew: Double,
    val text: String
)

/** How the average score was worked out, so the summary can say it. */
class ScoreBreakdown(val base: Int, val pitch: Int, val boundary: Int, val weather: Int, val total: Int)

object CustomStadiums {

    /** The "country" every custom ground is listed under. */
    const val COUNTRY = "Custom"
    const val CITY = "Custom stadium"
    const val ID_PREFIX = "custom-stadium-"
    const val MAX_NAME_LENGTH = 40

    /** The average first-innings T20 score of a balanced pitch, medium boundaries and ordinary weather (the game's own grounds average about 161). */
    private const val BASE_SCORE = 162

    private val gson = Gson()
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var file: File? = null
    @Volatile private var items: List<CustomStadium> = emptyList()
    @Volatile private var built: List<Stadium> = emptyList()

    /** A saved ground that no longer exists (a saved tournament or series still names it): played as a plain neutral ground. */
    val DELETED = Stadium(
        id = ID_PREFIX + "deleted", name = "Deleted custom stadium", city = CITY, country = COUNTRY,
        homeTeamIds = emptyList(), pitchType = PitchType.BALANCED, pitchDescription = pitchText(PitchType.BALANCED),
        boundarySize = BoundarySize.MEDIUM, altitudeM = 100, dewFactor = 0.25, avgT20FirstInningsScore = 160,
        climate = StadiumClimate(0.3, 65, 26, 12, "Warm conditions with a moderate chance of rain.")
    )

    val WEATHER_OPTIONS: List<WeatherOption> = listOf(
        WeatherOption("Hot and dry", "Very little rain, low humidity and high temperatures. Helps batters; hardly any dew.",
            0.05, 35, 35, 10, 0.10, "Hot and dry with very little rain risk."),
        WeatherOption("Warm with a chance of rain", "The most common weather in the game: warm, some humidity and a moderate chance of rain.",
            0.30, 65, 26, 12, 0.25, "Warm conditions with a moderate chance of rain."),
        WeatherOption("Hot and humid", "Hot and sticky, with the occasional storm and a lot of dew in the evening.",
            0.35, 78, 32, 10, 0.30, "Hot and humid with the occasional storm."),
        WeatherOption("Mild and pleasant", "Comfortable temperatures, little rain and a light breeze.",
            0.08, 55, 22, 10, 0.20, "Mild conditions with little rain."),
        WeatherOption("Cool and changeable", "Cool and cloudy, with a moderate chance of showers and some wind.",
            0.30, 70, 15, 18, 0.30, "Cool, changeable conditions."),
        WeatherOption("Rainy", "Genuinely rain-prone: expect interruptions. Bowlers like the damp air.",
            0.55, 82, 27, 14, 0.30, "Genuinely rain-prone conditions."),
        WeatherOption("Windy", "Almost no rain but a strong breeze all day, which makes big hitting harder.",
            0.10, 60, 22, 28, 0.15, "Mild and breezy with almost no rain.")
    )

    fun pitchLabel(p: PitchType): String = when (p) {
        PitchType.BATTING -> "Batting pitch"
        PitchType.BALANCED -> "Balanced pitch"
        PitchType.GREEN -> "Green pitch"
        PitchType.DUSTY -> "Dusty pitch"
        PitchType.BOWLING -> "Bowling pitch"
    }

    /** The same sentences the game's own grounds use. */
    fun pitchText(p: PitchType): String = when (p) {
        PitchType.BATTING -> "A flat, true surface that generally favors batters."
        PitchType.BALANCED -> "A fair, even contest between bat and ball."
        PitchType.GREEN -> "A grassy surface offering real seam and swing."
        PitchType.DUSTY -> "A surface that takes turn and wears as the match progresses."
        PitchType.BOWLING -> "A surface offering genuine assistance to the bowlers."
    }

    /** What a pitch means for the player choosing it. */
    fun pitchChoiceText(p: PitchType): String = when (p) {
        PitchType.BATTING -> "A flat, true surface that favours batters. Higher scores."
        PitchType.BALANCED -> "A fair, even contest between bat and ball."
        PitchType.GREEN -> "A grassy surface with seam and swing. Helps fast bowlers."
        PitchType.DUSTY -> "A dry surface that takes turn and wears. Helps spinners."
        PitchType.BOWLING -> "A surface that gives genuine help to the bowlers. Low scores."
    }

    fun boundaryLabel(b: BoundarySize): String = when (b) {
        BoundarySize.SMALL -> "Small boundaries"
        BoundarySize.MEDIUM -> "Medium boundaries"
        BoundarySize.LARGE -> "Large boundaries"
    }

    fun boundaryChoiceText(b: BoundarySize): String = when (b) {
        BoundarySize.SMALL -> "Short boundaries: more fours and sixes, higher scores."
        BoundarySize.MEDIUM -> "A normal size."
        BoundarySize.LARGE -> "Long boundaries: fours and sixes are harder to hit, lower scores."
    }

    private fun pitchAdjust(p: PitchType): Int = when (p) {
        PitchType.BATTING -> 14
        PitchType.BALANCED -> 0
        PitchType.GREEN -> -10
        PitchType.DUSTY -> -9
        PitchType.BOWLING -> -22
    }

    private fun boundaryAdjust(b: BoundarySize): Int = when (b) {
        BoundarySize.SMALL -> 6
        BoundarySize.MEDIUM -> 0
        BoundarySize.LARGE -> -7
    }

    /** Rain and humidity pull scores down, heat pushes them up a little, wind pulls them down (at most 10 either way). */
    private fun weatherAdjust(rain: Double, humidity: Int, temperature: Int, wind: Int): Int {
        val raw = -20.0 * (rain - 0.25) - 0.08 * (humidity - 60) + 0.2 * (temperature - 26) - 0.2 * (wind - 12)
        return raw.roundToInt().coerceIn(-10, 10)
    }

    /**
     * The average T20 first-innings score of a ground with this pitch, boundary size and weather. The adjustments were set
     * against the game's own 113 grounds (balanced pitch and medium boundaries about 161, batting pitches about 176, green and
     * dusty pitches about 152, small boundaries about 6 higher); the result is rounded to the nearest 5, as the game's own are.
     */
    fun score(pitch: PitchType, boundary: BoundarySize, w: WeatherOption): ScoreBreakdown {
        val p = pitchAdjust(pitch)
        val b = boundaryAdjust(boundary)
        val wx = weatherAdjust(w.rain, w.humidity, w.temperature, w.wind)
        val raw = (BASE_SCORE + p + b + wx).toDouble()
        val total = ((raw / 5.0).roundToInt() * 5).coerceIn(110, 210)
        return ScoreBreakdown(BASE_SCORE, p, b, wx, total)
    }

    // ---- The saved grounds ---------------------------------------------------------------------------

    fun init(context: Context) {
        val f = File(context.applicationContext.filesDir, "custom_stadiums.json")
        file = f
        items = try {
            if (f.exists()) (gson.fromJson(f.readText(), Array<CustomStadium>::class.java)?.toList() ?: emptyList()) else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        rebuild()
    }

    fun list(): List<CustomStadium> = items

    /** The grounds as the game reads them (every one under the country "Custom"). */
    fun stadiums(): List<Stadium> = built

    fun find(id: String): Stadium? = built.find { it.id == id }

    fun add(stadium: CustomStadium) = change { it + stadium }

    fun delete(id: String) = change { list -> list.filter { it.id != id } }

    private fun change(edit: (List<CustomStadium>) -> List<CustomStadium>) {
        synchronized(this) {
            items = edit(items)
            rebuild()
            val json = gson.toJson(items.toTypedArray())
            val f = file ?: return
            writer.execute {
                try {
                    val temp = File(f.parentFile, f.name + ".tmp")
                    temp.writeText(json)
                    if (!temp.renameTo(f)) {
                        f.delete()
                        temp.renameTo(f)
                    }
                } catch (e: Exception) {
                    // A failed save must never crash the game; the next change saves again.
                }
            }
        }
    }

    private fun rebuild() {
        built = items.map { c ->
            Stadium(
                id = c.id, name = c.name, city = CITY, country = COUNTRY, homeTeamIds = emptyList(),
                pitchType = c.pitch, pitchDescription = pitchText(c.pitch), boundarySize = c.boundary,
                altitudeM = 100, dewFactor = c.dew, avgT20FirstInningsScore = c.averageScore,
                climate = StadiumClimate(c.baseRain, c.humidity, c.temperature, c.wind, c.weatherText)
            )
        }
    }
}
