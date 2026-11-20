package com.cricketsim.logic

/**
 * The weather of the match about to be played (patch 134). The pitch report shown before the toss rolls it for the ground ([forReport]); the match then takes the SAME
 * weather ([take]), so what the commentators said before the toss is what the match gets. A match with no pitch report before it (or one whose report is more than
 * three hours old, or for another ground) rolls its own, as it always did.
 */
object MatchWeatherCache {
    private var stadiumId: String? = null
    private var weather: WeatherSnapshot? = null
    private var shownAt = 0L
    private const val MAX_AGE_MS = 3 * 60 * 60 * 1000L

    /** Rolls the weather for this ground from its climate and keeps it for the match. */
    @Synchronized
    fun forReport(stadium: Stadium): WeatherSnapshot {
        val rolled = WeatherSystem.generateWeatherForStadium(stadium)
        stadiumId = stadium.id
        weather = rolled
        shownAt = System.currentTimeMillis()
        return rolled
    }

    /** The weather shown on the pitch report for this ground, once (it is forgotten after), or a new roll. */
    @Synchronized
    fun take(stadium: Stadium): WeatherSnapshot {
        val kept = weather
        val fresh = kept != null && stadiumId == stadium.id && System.currentTimeMillis() - shownAt < MAX_AGE_MS
        stadiumId = null
        weather = null
        return if (fresh && kept != null) kept else WeatherSystem.generateWeatherForStadium(stadium)
    }
}
