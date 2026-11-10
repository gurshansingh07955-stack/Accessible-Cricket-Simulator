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

    // ------------------------------------------------------------ the state level

    private fun pv(p: Player): Double = when (p.role) {
        PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER -> p.battingRating * 0.8 + p.fieldingRating * 0.2
        PlayerRole.BOWLER -> p.bowlingRating * 0.8 + p.fieldingRating * 0.2
        else -> p.battingRating * 0.45 + p.bowlingRating * 0.45 + p.fieldingRating * 0.1
    }

    private fun roleSlots(role: PlayerRole): Int = if (role == PlayerRole.WICKETKEEPER) 1 else 3

    /** The decision for the player's next match in a competition: the club's rank among his club-mates, or the state squad's. */
    fun forState(career: CareerProfile, state: TournamentState): Decision =
        if (career.injuredMatches > 0) CareerHealth.decision(career)
        else if (CareerSeason.isClub(state)) decide(career)
        else if (CareerInternational.isInternational(state)) CareerInternational.matchDecision(career, state)
        else if (CareerLeagues.isLeague(state)) CareerLeagues.matchDecision(career, state)
        else decideAtState(career, state)

    /**
     * Once called up, the player is ranked in the state squad among the players of his role (the squad is bigger and stronger than a club's):
     * the selectors take the top three of a role (one keeper), widened or narrowed by the difficulty. His first state match is a debut; his first
     * three state matches and being 19 or younger each count a place in his favour; form still moves him up or down by up to three places.
     */
    fun decideAtState(career: CareerProfile, state: TournamentState): Decision {
        val team = state.teams.find { it.id == state.userTeamId } ?: return Decision(true, 1, 11, 15, 0, "")
        val me = team.players.find { it.id == CareerSeason.playerIdIn(state, career) } ?: return Decision(true, 1, 11, team.players.size, 0, "")
        val group = team.players.filter { it.role == me.role }.sortedByDescending { pv(it) }
        val rank = group.indexOfFirst { it.id == me.id }.let { if (it < 0) group.size else it + 1 }
        val factor = CareerWorld.selectionFactor(career.difficulty)
        val cutoff = (roleSlots(me.role) / factor).roundToInt().coerceIn(1, group.size)
        val form = career.form.orEmpty().takeLast(3)
        val adjust = if (form.isEmpty()) 0 else ((form.average() - 25.0) / 10.0).roundToInt().coerceIn(-3, 3)
        val grace = (if (career.stateMatches < 3) 1 else 0) + (if (career.age <= 19) 1 else 0)
        val debut = career.stateMatches == 0
        val selected = debut || rank - adjust - grace <= cutoff
        val formNote = when {
            adjust > 0 -> " Your good form moves you up ${if (adjust == 1) "a place" else "$adjust places"}."
            adjust < 0 -> " Your poor form drops you ${if (adjust == -1) "a place" else "${-adjust} places"}."
            else -> ""
        }
        val base = "You rank ${ordinal(rank)} of ${group.size} players of your role in the ${team.name} squad, and the selectors on ${career.difficulty.lowercase()} pick the top $cutoff.$formNote"
        val reason = when {
            debut -> "You are in the XI. The state selectors give every new player a debut: this is your first state match. $base"
            selected -> "You are in the XI. $base"
            else -> "You are not in the XI. $base Score runs, take wickets or improve your ratings to move up."
        }
        return Decision(selected, rank, cutoff, group.size, adjust, reason)
    }

    /**
     * The decision at the end of a season: is the player called up to the state squad (or does he keep his place in it)? He is ranked, with his current
     * ratings, against the squad's players of his role. His form (the average impact of his last five matches against 25, a place for every ten) and being
     * 19 or younger help him; a player already in the squad gets two places of loyalty.
     */
    fun stateDecision(career: CareerProfile, squad: Team): Decision {
        val role = CareerSeason.roleOf(career.role)
        val myValue = when (role) {
            PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER -> career.batting * 0.8 + career.fielding * 0.2
            PlayerRole.BOWLER -> career.bowling * 0.8 + career.fielding * 0.2
            else -> career.batting * 0.45 + career.bowling * 0.45 + career.fielding * 0.1
        }
        val group = squad.players.filter { it.role == role }
        val rank = 1 + group.count { pv(it) > myValue }
        val factor = CareerWorld.selectionFactor(career.difficulty)
        val cutoff = (roleSlots(role) / factor).roundToInt().coerceIn(1, group.size + 1)
        val form = career.form.orEmpty().takeLast(5)
        val adjust = if (form.isEmpty()) 0 else ((form.average() - 25.0) / 10.0).roundToInt().coerceIn(-3, 3)
        val grace = (if (career.age <= 19) 1 else 0) + (if (career.atOrAboveState) 2 else 0)
        val selected = rank - adjust - grace <= cutoff
        val formNote = when {
            adjust > 0 -> " Your good form moves you up ${if (adjust == 1) "a place" else "$adjust places"}."
            adjust < 0 -> " Your poor form drops you ${if (adjust == -1) "a place" else "${-adjust} places"}."
            else -> ""
        }
        val loyal = if (career.atOrAboveState) " As a player already in the squad you get two places of loyalty." else ""
        val youth = if (career.age <= 19) " The selectors back young players." else ""
        val base = "With your ratings you would rank ${ordinal(rank)} among the ${group.size} players of your role in the ${squad.name} squad, and the selectors on ${career.difficulty.lowercase()} pick the top $cutoff.$youth$loyal$formNote"
        val reason = if (selected) {
            if (career.atOrAboveState) "The ${squad.name} selectors keep you in the squad. $base" else "The ${squad.name} selectors call you up to the state squad! $base"
        } else {
            if (career.atOrAboveState) "The ${squad.name} selectors drop you from the squad. $base" else "The ${squad.name} selectors do not call you up this year. $base Keep scoring runs and taking wickets and improving."
        }
        return Decision(selected, rank, cutoff, group.size, adjust, reason)
    }
}
