package com.cricketsim.logic

/**
 * Custom tournaments: the way a tournament is laid out ([ScheduleType]), the match formats a tournament can use
 * ([TournamentFormats]), the game's own tournaments that can be renamed or given another format
 * ([GameTournaments]) and every team a custom tournament can pick ([TeamCatalog]).
 *
 * Home > Advanced Customisation > Create Custom Tournaments builds a [CustomTournamentDef] (stored by
 * [Customisation]). Playing one is Tournament > Custom tournaments.
 */

/** How a custom tournament is played. [minTeams] is the fewest teams it makes sense for. */
enum class ScheduleType(val label: String, val description: String, val minTeams: Int) {
    SINGLE_RR(
        "Round Robin",
        "Every team plays every other team once. The team on top of the table wins.", 3
    ),
    DOUBLE_RR(
        "Double Round Robin",
        "Every team plays every other team twice, home and away. The team on top of the table wins.", 3
    ),
    RR_FINAL(
        "Round Robin and Final",
        "Every team plays every other team once, then the top two meet in the Final.", 3
    ),
    RR_SEMIS(
        "Round Robin, Semi-finals and Final",
        "A round robin, then the top four play two semi-finals (1st v 4th, 2nd v 3rd) and the Final.", 4
    ),
    RR_IPL(
        "Round Robin and IPL-style Playoffs",
        "A round robin, then the top four play Qualifier 1, the Eliminator, Qualifier 2 and the Final.", 4
    ),
    KNOCKOUT(
        "Knockout",
        "Single elimination: lose and you are out. The draw is random; with a number of teams that is not a power of two, some teams get a bye in the first round.", 2
    ),
    GROUPS_SEMIS(
        "Two Groups, Semi-finals and Final",
        "The teams are split into two groups, each a round robin; the top two of each group play the semi-finals (1st v 2nd of the other group) and the Final.", 6
    );

    /** The matches, stage by stage, for [n] teams. */
    fun breakdown(n: Int): List<Pair<String, Int>> {
        val rr = n * (n - 1) / 2
        return when (this) {
            SINGLE_RR -> listOf("League matches" to rr)
            DOUBLE_RR -> listOf("League matches (each pair twice)" to n * (n - 1))
            RR_FINAL -> listOf("League matches" to rr, "Final" to 1)
            RR_SEMIS -> listOf("League matches" to rr, "Semi-finals" to 2, "Final" to 1)
            RR_IPL -> listOf("League matches" to rr, "Qualifier 1" to 1, "Eliminator" to 1, "Qualifier 2" to 1, "Final" to 1)
            KNOCKOUT -> knockoutRounds(n)
            GROUPS_SEMIS -> {
                val a = (n + 1) / 2
                val b = n / 2
                listOf("Group A matches" to a * (a - 1) / 2, "Group B matches" to b * (b - 1) / 2, "Semi-finals" to 2, "Final" to 1)
            }
        }
    }

    fun matchCount(n: Int): Int = breakdown(n).sumOf { it.second }

    private fun knockoutRounds(n: Int): List<Pair<String, Int>> {
        val rounds = ArrayList<Pair<String, Int>>()
        var remaining = n
        var round = 1
        while (remaining > 1) {
            val matches = remaining / 2
            val name = when (matches) {
                1 -> "Final"
                2 -> "Semi-finals"
                4 -> "Quarter-finals"
                else -> "Round $round"
            }
            rounds.add(name to matches)
            remaining = remaining - matches
            round++
        }
        return rounds
    }

    companion object {
        const val MAX_TEAMS = 16
    }
}

/** A match format a tournament can use: one of the game's own, or one the player made ("custom:<id>"). */
data class FormatOption(val key: String, val name: String, val description: String)

object TournamentFormats {
    /** The game's own limited-overs formats (Test matches cannot be used in a tournament). */
    fun builtIn(): List<FormatOption> = listOf(
        FormatOption("T20", "T20", "20 overs a side. Powerplay: the first 6 overs, at most 3 fielders in the deep. A bowler bowls up to 4 overs."),
        FormatOption("ODI", "ODI", "50 overs a side. Powerplay: the first 10 overs, at most 3 fielders in the deep. A bowler bowls up to 10 overs."),
        FormatOption("T10", "T10", "10 overs a side. Powerplay: the first 3 overs, at most 3 fielders in the deep. A bowler bowls up to 2 overs."),
        FormatOption("FIVE_OVERS", "Five-over", "5 overs a side. Powerplay: the first 9 balls, at most 3 fielders in the deep. A bowler bowls 1 over."),
        FormatOption("ONE_OVER", "One-over", "1 over a side. Powerplay: the first 2 balls, at most 3 fielders in the deep. One bowler bowls the over.")
    )

    fun custom(formats: List<CustomFormat>): List<FormatOption> = formats.map {
        FormatOption(
            "custom:" + it.id, it.name + " (custom)",
            it.description() + " A bowler bowls up to ${it.maxOversPerBowler} overs."
        )
    }

    fun all(formats: List<CustomFormat>): List<FormatOption> = builtIn() + custom(formats)

    fun find(key: String, formats: List<CustomFormat>): FormatOption? = all(formats).find { it.key == key }
}

/** One of the game's own tournaments, as listed by Edit Tournament. */
data class GameTournament(val id: String, val name: String, val formatKey: String, val group: String)

object GameTournaments {
    /** The tournament id behind a Tournament page "kind" (a custom tournament's kind is "custom:<id>"). */
    fun idForKind(kind: String): String = when (kind) {
        "cpl" -> CplData.TOURNAMENT_ID
        "ipl" -> "ipl2027"
        "adt10" -> AbuDhabiT10.TOURNAMENT_ID
        "lpl" -> LankaPremierLeague.TOURNAMENT_ID
        "ilt" -> InternationalLeagueT20.TOURNAMENT_ID
        "bbl" -> BigBashLeague.TOURNAMENT_ID
        "mlc" -> MajorLeagueCricket.TOURNAMENT_ID
        "sa20" -> Sa20.TOURNAMENT_ID
        "wc2026" -> T20WorldCup.TOURNAMENT_ID
        "ct2025" -> ChampionsTrophy.TOURNAMENT_ID
        "asia2025" -> AsiaCup.TOURNAMENT_ID
        "ag2026" -> AsianGames.TOURNAMENT_ID
        else -> kind.removePrefix("custom:")
    }

    val all: List<GameTournament> by lazy {
        listOf(
            GameTournament(CplData.TOURNAMENT_ID, CplData.TOURNAMENT_NAME, "T20", "League tournaments"),
            GameTournament("ipl2027", "IPL 2027", "T20", "League tournaments"),
            GameTournament(AbuDhabiT10.TOURNAMENT_ID, AbuDhabiT10.TOURNAMENT_NAME, "T10", "League tournaments"),
            GameTournament(LankaPremierLeague.TOURNAMENT_ID, LankaPremierLeague.TOURNAMENT_NAME, "T20", "League tournaments"),
            GameTournament(InternationalLeagueT20.TOURNAMENT_ID, InternationalLeagueT20.TOURNAMENT_NAME, "T20", "League tournaments"),
            GameTournament(BigBashLeague.TOURNAMENT_ID, BigBashLeague.TOURNAMENT_NAME, "T20", "League tournaments"),
            GameTournament(MajorLeagueCricket.TOURNAMENT_ID, MajorLeagueCricket.TOURNAMENT_NAME, "T20", "League tournaments"),
            GameTournament(Sa20.TOURNAMENT_ID, Sa20.TOURNAMENT_NAME, "T20", "League tournaments"),
            GameTournament(T20WorldCup.TOURNAMENT_ID, T20WorldCup.TOURNAMENT_NAME, "T20", "International tournaments"),
            GameTournament(ChampionsTrophy.TOURNAMENT_ID, ChampionsTrophy.TOURNAMENT_NAME, "ODI", "International tournaments"),
            GameTournament(AsiaCup.TOURNAMENT_ID, AsiaCup.TOURNAMENT_NAME, "T20", "International tournaments"),
            GameTournament(AsianGames.TOURNAMENT_ID, AsianGames.TOURNAMENT_NAME, "T20", "International tournaments")
        )
    }
}

/** One team a custom tournament can pick. [key] says where it comes from and is what a tournament stores. */
class TeamEntry(val key: String, val kind: String, val group: String, val team: Team)

object TeamCatalog {
    /** A custom team needs this many players before it can play. */
    const val MIN_PLAYERS = 11

    const val INTERNATIONAL = "int"
    const val TOURNAMENT = "tour"
    const val LEAGUE = "league"
    const val CUSTOM = "custom"

    private fun entry(kind: String, group: String, t: Team) = TeamEntry("$kind|$group|${t.id}", kind, group, t)

    /** Every team there is to pick (a few hundred players are built, so call it off the main thread). */
    fun load(): List<TeamEntry> {
        val out = ArrayList<TeamEntry>()
        CricketData.getAllTeams().sortedBy { it.name }.forEach { out.add(entry(INTERNATIONAL, "International teams", it)) }
        listOf(
            T20WorldCup.TOURNAMENT_NAME to T20WorldCup.buildTeams().first,
            ChampionsTrophy.TOURNAMENT_NAME to ChampionsTrophy.buildTeams().first,
            AsiaCup.TOURNAMENT_NAME to AsiaCup.buildTeams().first,
            AsianGames.TOURNAMENT_NAME to AsianGames.buildTeams().first
        ).forEach { (title, teams) -> teams.forEach { out.add(entry(TOURNAMENT, title, it)) } }
        listOf(
            "Caribbean Premier League" to CplData.buildTeams().first,
            "Abu Dhabi T10" to AbuDhabiT10.buildTeams().first,
            "Lanka Premier League" to LankaPremierLeague.buildTeams().first,
            "International League T20" to InternationalLeagueT20.buildTeams().first,
            "Big Bash League" to BigBashLeague.buildTeams().first,
            "Major League Cricket" to MajorLeagueCricket.buildTeams().first,
            "SA20" to Sa20.buildTeams().first
        ).forEach { (title, teams) -> teams.forEach { out.add(entry(LEAGUE, title, it)) } }
        Customisation.customTeamsAsTeams().forEach { out.add(entry(CUSTOM, "Custom teams", it)) }
        return out
    }
}

/** A change the player made to one of the game's own tournaments (null = unchanged). */
data class TournamentEdit(val name: String? = null, val formatKey: String? = null)

/**
 * A tournament the player designed. [formatKey] is a [FormatOption] key, [schedule] a [ScheduleType] name,
 * [teamKeys] are [TeamEntry] keys, [drsReviews] is 1 to 4 or [UNLIMITED_REVIEWS].
 */
data class CustomTournamentDef(
    val id: String = "",
    val name: String = "",
    val formatKey: String = "T20",
    val schedule: String = "SINGLE_RR",
    val teamKeys: List<String> = emptyList(),
    val stadiumIds: List<String> = emptyList(),
    val drsEnabled: Boolean = true,
    val drsReviews: Int = 2,
    val rainEnabled: Boolean = true
) {
    companion object {
        const val UNLIMITED_REVIEWS = 99
        const val MAX_NAME_LENGTH = 40
    }
}
