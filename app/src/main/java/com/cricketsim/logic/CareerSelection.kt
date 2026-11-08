package com.cricketsim.logic

import kotlin.math.roundToInt

/**
 * Who the selectors pick. The player is ranked against the club-mates of HIS OWN ROLE (batsmen against batsmen, bowlers against bowlers...)
 * by one number, his value in that role. An XI has a fixed number of places for each role ([ROLE_SLOTS]: one keeper, three batsmen, two batting
 * and two bowling all-rounders, three bowlers), and the career difficulty widens or narrows them: Very easy takes almost everyone, Medium the
 * places as they are, Hard and Legend fewer ([CareerWorld.selectionFactor]).
 *
 * Three things help a newcomer: his FIRST match is always a debut, his first three matches count one place in his favour, and a player of
 * 19 or younger gets one more place (the selectors back young talent). Recent form moves him up or down by as many as three places: the average
 * impact of his last three matches against 25 points, a place for every ten.
 */
object CareerSelection {

    /** Places in an XI for each role. */
    private val ROLE_SLOTS = mapOf(
        "Wicketkeeper-batsman" to 1,
        "Batsman" to 3,
        "Batting all-rounder" to 2,
        "Bowling all-rounder" to 2,
        "Bowler" to 3
    )

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

    private fun plural(role: String): String = if (role.endsWith("man")) role.dropLast(3) + "men" else role + "s"

    fun decide(career: CareerProfile): Decision {
        val squad = career.myClub?.players ?: return Decision(true, 1, 11, 15, 0, "")
        val me = squad.find { it.isUser } ?: return Decision(true, 1, 11, squad.size, 0, "")
        val group = squad.filter { it.role == me.role }.sortedByDescending { value(it) }
        val rank = group.indexOfFirst { it.isUser }.let { if (it < 0) group.size else it + 1 }
        val factor = CareerWorld.selectionFactor(career.difficulty)
        val slots = ROLE_SLOTS[me.role] ?: 3
        val cutoff = (slots / factor).roundToInt().coerceIn(1, group.size)
        val form = career.form.orEmpty().takeLast(3)
        val adjust = if (form.isEmpty()) 0 else ((form.average() - 25.0) / 10.0).roundToInt().coerceIn(-3, 3)
        val grace = (if (career.matchesPlayed < 3) 1 else 0) + (if (career.age <= 19) 1 else 0)
        val debut = career.matchesPlayed == 0
        val captain = career.isCaptain
        val selected = captain || debut || rank - adjust - grace <= cutoff

        val formNote = when {
            adjust > 0 -> " Your good form moves you up ${if (adjust == 1) "a place" else "$adjust places"}."
            adjust < 0 -> " Your poor form drops you ${if (adjust == -1) "a place" else "${-adjust} places"}."
            else -> ""
        }
        val graceNote = when {
            grace == 2 -> " The selectors are giving a young newcomer two extra places."
            grace == 1 -> if (career.age <= 19) " The selectors back young players: one extra place." else " You are still new, so you get one extra place."
            else -> ""
        }
        val base = "You rank ${ordinal(rank)} of ${group.size} ${plural(me.role.lowercase())} at your club, and the selectors on ${career.difficulty.lowercase()} pick the top $cutoff.$graceNote$formNote"
        val reason = when {
            captain -> "You are the captain, so you always play, and you make the team decisions. $base"
            debut -> "You are in the XI. The selectors give every new player a debut: this is your first match. $base"
            selected -> "You are in the XI. $base"
            else -> "You are not in the XI. $base Score runs, take wickets or improve your ratings to move up."
        }
        return Decision(selected, rank, cutoff, group.size, adjust, reason)
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

    /** The eleven the player's club takes the field with for a match he is selected for (captain and keeper set), the player at his batting number. */
    fun matchXI(squad: Team, tags: Map<String, SquadTag>, stadium: Stadium, userPlayerId: String, battingNumber: Int = 0): Team {
        val xi = CplSimulator.pickXI(squadForMatch(squad, tags, stadium, userPlayerId, true), tags, stadium)
        if (battingNumber !in 1..11) return xi
        val me = xi.players.find { it.id == userPlayerId } ?: return xi
        val order = xi.players.filter { it.id != userPlayerId }.toMutableList()
        order.add((battingNumber - 1).coerceIn(0, order.size), me)
        return xi.copy(players = order)
    }
}
