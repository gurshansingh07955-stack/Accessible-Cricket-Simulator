package com.cricketsim.logic

/**
 * Play match > Play series (patch 146, part 1: setting a series up and saving it; playing it is part 2).
 *
 * A series is a number of matches between two (one vs one), three (triangular) or four (quadrangular) teams, in one format or in several
 * ("multi format": each chosen format has its own number of matches), at the grounds the player ticked. Up to [SeriesRules.MAX_SAVED] series are kept.
 * The matches of a triangular or quadrangular series are shared between the teams in turn.
 */
enum class SeriesKind(val label: String, val teams: Int, val text: String) {
    ONE_VS_ONE("One vs one", 2, "Two teams play each other."),
    TRIANGULAR("Triangular", 3, "Three teams. The matches are shared between them in turn."),
    QUADRANGULAR("Quadrangular", 4, "Four teams. The matches are shared between them in turn.")
}

/** [matches] matches in one format ([formatKey] is a TournamentFormats key, "TEST", or "custom:<id>"). */
data class SeriesLeg(val formatKey: String = "T20", val matches: Int = 1)

/** A saved series. Teams are keys: "int:<team id>" for a national team, "custom:<team id>" for a custom team of the player's. */
data class SeriesDef(
    val id: String = "",
    val name: String = "",
    val kind: String = "ONE_VS_ONE",
    val multiFormat: Boolean = false,
    val legs: List<SeriesLeg> = emptyList(),
    val teamKeys: List<String> = emptyList(),
    val stadiumIds: List<String> = emptyList(),
    val createdAt: Long = 0L
)

/** A team the player can pick for a series. */
data class SeriesTeamChoice(val key: String, val team: Team, val custom: Boolean)

object SeriesRules {
    const val MAX_SAVED = 5
    const val MIN_MATCHES = 1
    const val MAX_MATCHES = 27
    const val TEST_KEY = "TEST"

    fun kindOf(def: SeriesDef): SeriesKind = SeriesKind.values().find { it.name == def.kind } ?: SeriesKind.ONE_VS_ONE

    /** Every format a series can use: Test, the game's limited-overs formats, then the player's own formats. */
    fun formatOptions(custom: List<CustomFormat>): List<FormatOption> =
        listOf(FormatOption(TEST_KEY, "Test", "A match played over several days, with up to two innings a side.")) +
            TournamentFormats.builtIn() + TournamentFormats.custom(custom)

    fun formatName(key: String, options: List<FormatOption>): String = options.find { it.key == key }?.name ?: key

    fun totalMatches(def: SeriesDef): Int = def.legs.sumOf { it.matches }

    /** "3 T20 matches, 2 ODI matches". */
    fun legsText(legs: List<SeriesLeg>, options: List<FormatOption>): String =
        legs.joinToString(", ") { "${it.matches} ${formatName(it.formatKey, options)} ${if (it.matches == 1) "match" else "matches"}" }

    /** Every national team, then every custom team of the player's that is ready to play. */
    fun teamChoices(): List<SeriesTeamChoice> =
        CricketData.getAllTeams().sortedBy { it.name }.map { SeriesTeamChoice("int:" + it.id, it, false) } +
            Customisation.playableCustomTeams().sortedBy { it.name }.map { SeriesTeamChoice("custom:" + it.id, it, true) }

    /** The teams behind a list of keys, in the order of the keys (a team that no longer exists is left out). */
    fun teamsFor(keys: List<String>): List<Team> = keys.mapNotNull { key ->
        when {
            key.startsWith("int:") -> CricketData.getTeamById(key.removePrefix("int:"))
            key.startsWith("custom:") -> Customisation.customTeamsAsTeams().find { it.id == key.removePrefix("custom:") }
            else -> null
        }
    }

    /** Every ground the game has, and the countries of the chosen teams that have grounds of their own (the first countries to show). */
    fun groundsFor(teams: List<Team>): Pair<List<Stadium>, List<String>> {
        val all = AllStadiums.list()
        val countries = LinkedHashSet<String>()
        for (t in teams) {
            val own = all.filter { it.country.equals(t.name, ignoreCase = true) || t.id in it.homeTeamIds }
            own.map { it.country }.distinct().sorted().forEach { countries.add(it) }
        }
        return all to countries.toList()
    }

    fun autoName(kind: SeriesKind, teamNames: List<String>): String = when (kind) {
        SeriesKind.ONE_VS_ONE -> teamNames.joinToString(" v ") + " series"
        else -> kind.label + " series: " + teamNames.joinToString(", ")
    }

    /** [base], or [base] 2, [base] 3... so no two saved series share a name. */
    fun uniqueName(base: String, existing: List<String>): String {
        if (existing.none { it.equals(base, ignoreCase = true) }) return base
        var n = 2
        while (existing.any { it.equals("$base $n", ignoreCase = true) }) n++
        return "$base $n"
    }

    fun newId(): String = "series_" + System.currentTimeMillis()
}
