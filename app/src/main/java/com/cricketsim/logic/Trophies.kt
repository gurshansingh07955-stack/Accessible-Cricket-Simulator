package com.cricketsim.logic

/**
 * One trophy the player won (patch 140): a tournament his team won, with the Final's scorecard so he can open it later. Game trophies are kept in
 * trophies.json (persistence/TrophyStore); a career's trophies live in the career itself (`CareerProfile.trophies`) so Career mode keeps its own.
 */
data class TrophyRecord(
    /** One trophy: the tournament's id and when it was created, so winning it is never recorded twice. */
    val key: String,
    /** The tournament's id (one edition, such as "ranji2027"); a trophy of the same id is the same competition won again. */
    val tournamentId: String,
    val name: String,
    val teamName: String,
    /** When it was won (the real time); 0 for a career trophy, which has [note] ("Season 3, 2028") instead. */
    val wonAt: Long = 0L,
    val note: String = "",
    /** "Final: India v Australia", or "Last match: ..." for a competition with no Final (the County Championship). */
    val finalLabel: String = "",
    val finalSummary: String = "",
    /** The Final's scorecard (the slim one the tournament keeps: no ball-by-ball); null if the match was only simulated without one. */
    val scorecard: MatchState? = null
)

/** The wins of one competition. */
class TrophyGroup(val tournamentId: String, val trophies: List<TrophyRecord>) {
    /** The newest win's name ("Ranji Trophy 2027"). */
    val name: String get() = trophies.first().name
}

object Trophies {
    /**
     * The trophy for a finished tournament, or null if the player's team did not win it. [champion] is the winner ([CplSimulator.champion], or the career's own
     * [CareerSeason.champion]); [readable] turns the result's text into names (a career's team ids); [note] is the season for a career trophy.
     */
    fun fromState(
        state: TournamentState,
        champion: Team?,
        note: String = "",
        readable: (String) -> String = { it }
    ): TrophyRecord? {
        if (champion == null || champion.id != state.userTeamId) return null
        val finalFixture = state.fixtures.firstOrNull { it.stage == "Final" }
        val lastMatch = if (finalFixture != null) null else
            state.fixtures.lastOrNull { it.result != null && (it.homeId == champion.id || it.awayId == champion.id) }
        val fixture = finalFixture ?: lastMatch
        fun nameOf(id: String?): String = state.teams.firstOrNull { it.id == id }?.name.orEmpty()
        val label = fixture?.let { (if (finalFixture != null) "Final" else "Last match") + ": ${nameOf(it.homeId)} v ${nameOf(it.awayId)}" }.orEmpty()
        return TrophyRecord(
            key = "${state.tournamentId}|${state.createdAt}",
            tournamentId = state.tournamentId,
            name = state.name,
            teamName = champion.name,
            wonAt = if (note.isEmpty()) System.currentTimeMillis() else 0L,
            note = note,
            finalLabel = label,
            finalSummary = readable(fixture?.result?.summary.orEmpty()),
            scorecard = fixture?.scorecard
        )
    }

    /** The trophies grouped by competition, the most recently won first; inside a group the newest first. */
    fun groups(trophies: List<TrophyRecord>): List<TrophyGroup> {
        val ordered = trophies.reversed()
        return ordered.groupBy { it.tournamentId }.map { TrophyGroup(it.key, it.value) }
    }

    fun countText(n: Int): String = if (n == 1) "1 trophy" else "$n trophies"
}
