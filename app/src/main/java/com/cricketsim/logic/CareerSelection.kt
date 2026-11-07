package com.cricketsim.logic

import kotlin.math.roundToInt

/**
 * Who the selectors pick. The player is ranked against the other players of his district club by one number, his value in
 * his role (a batsman by his batting, a bowler by his bowling, an all-rounder by both). The selectors take the top [cutoff]
 * players, and the career difficulty moves the cutoff: Very easy takes almost everyone, Medium the top eleven, Hard the top nine, Legend the top
 * seven ([CareerWorld.selectionFactor]). Recent form moves the player up or down by as many as three places: the average impact of
 * his last three matches against 25 points, a place for every ten.
 */
object CareerSelection {

    class Decision(
        val selected: Boolean,
        val rank: Int,
        val cutoff: Int,
        val squadSize: Int,
        val formAdjust: Int,
        val reason: String
    )

    private fun value(p: CareerPlayer): Double = when (p.role) {
        "Batsman", "Wicketkeeper-batsman" -> p.batting * 0.8 + p.fielding * 0.2
        "Bowler" -> p.bowling * 0.8 + p.fielding * 0.2
        "Batting all-rounder" -> p.batting * 0.55 + p.bowling * 0.35 + p.fielding * 0.1
        else -> p.bowling * 0.55 + p.batting * 0.35 + p.fielding * 0.1
    }

    private fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    fun decide(career: CareerProfile): Decision {
        val squad = career.myClub?.players ?: return Decision(true, 1, 11, 15, 0, "")
        val sorted = squad.sortedByDescending { value(it) }
        val rank = sorted.indexOfFirst { it.isUser }.let { if (it < 0) squad.size else it + 1 }
        val factor = CareerWorld.selectionFactor(career.difficulty)
        val cutoff = (11 / factor).roundToInt().coerceIn(5, squad.size)
        val form = career.form.orEmpty().takeLast(3)
        val adjust = if (form.isEmpty()) 0 else ((form.average() - 25.0) / 10.0).roundToInt().coerceIn(-3, 3)
        val selected = rank - adjust <= cutoff
        val formNote = when {
            adjust > 0 -> " Your good form moves you up ${if (adjust == 1) "a place" else "$adjust places"}."
            adjust < 0 -> " Your poor form drops you ${if (adjust == -1) "a place" else "${-adjust} places"}."
            else -> ""
        }
        val base = "You rank ${ordinal(rank)} of ${squad.size} players at your club, and the selectors on ${career.difficulty.lowercase()} take the top $cutoff.$formNote"
        val reason = if (selected) "You are in the XI. $base" else "You are not in the XI. $base Score runs, take wickets or improve your ratings to move up."
        return Decision(selected, rank, cutoff, squad.size, adjust, reason)
    }

    private fun v(p: Player): Double = maxOf(p.battingRating, p.bowlingRating) + p.fieldingRating * 0.1

    /**
     * The player's club squad as the selectors leave it for a match. Not selected: the squad without him. Selected: the club's best
     * eleven, with him in it (in the place of the weakest player of his own kind; the keeper keeps his place unless he is the keeper).
     */
    fun squadForMatch(squad: Team, tags: Map<String, SquadTag>, stadium: Stadium, userPlayerId: String, selected: Boolean): Team {
        val me = squad.players.find { it.id == userPlayerId } ?: return squad
        if (!selected) return squad.copy(players = squad.players.filter { it.id != userPlayerId })
        val best = CplSimulator.pickXI(squad, tags, stadium)
        if (best.players.any { it.id == userPlayerId }) return best
        val others = best.players.filter { it.role != PlayerRole.WICKETKEEPER || me.role == PlayerRole.WICKETKEEPER }
        val sameKind = others.filter { it.role == me.role }
        val victim = (if (sameKind.isEmpty()) others else sameKind).minByOrNull { v(it) } ?: return best
        return best.copy(players = best.players.map { if (it.id == victim.id) me else it })
    }

    /** The eleven the player's club takes the field with for a match he is selected for (captain and keeper set). */
    fun matchXI(squad: Team, tags: Map<String, SquadTag>, stadium: Stadium, userPlayerId: String): Team =
        CplSimulator.pickXI(squadForMatch(squad, tags, stadium, userPlayerId, true), tags, stadium)
}
