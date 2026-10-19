package com.cricketsim.logic

import kotlin.random.Random

/**
 * The IPL's IMPACT PLAYER rule, as the real competition plays it (used in the IPL season; the CPL does not have it).
 *
 *  - Before the toss each side names FIVE substitutes (besides its XI).
 *  - During the match a side may bring ONE of them on, once, in place of any player of its XI.
 *  - Only when the ball is dead: at the start of an innings, at the end of an over, or when a wicket falls.
 *  - No later than the end of the 14th over. Not in a match shortened to fewer than 10 overs a side, nor in a Super Over.
 *  - The player who goes off takes no further part: he cannot bat, bowl or field again (his figures so far stand).
 *  - The Impact Player may bat and bowl his full share, as any other player (a fresh four overs).
 *  - A batter at the crease and the bowler of the over cannot be replaced.
 *  - The XI may never have more than FOUR overseas players: an overseas player can only come on for an overseas
 *    player, unless the XI has fewer than four.
 *  - If the captain goes off the captaincy passes to the vice-captain (or the best remaining player); the Impact
 *    Player does not become captain. A replaced wicketkeeper is replaced behind the stumps by another keeper.
 *
 * Not modelled: the real rule's injury and concussion substitutes. Computer-v-computer matches of a tournament
 * (played by the quick AI model) do not use Impact Players; the matches you play do.
 *
 * The computer side chooses its five substitutes ([aiSubstitutes]) and when to use its Impact Player ([aiChoose]):
 * it values what each player can still give the side from now on (his batting if the side has batting still to
 * do, his bowling if it has bowling to do, a bowler's overs left, a batter who has already batted being worth
 * nothing more with the bat) and swaps when the best swap gains enough, asking for more early in the match and
 * for less as the end of the 14th over comes near.
 */
data class ImpactSetup(
    val userSubs: List<Player>,
    val opponentSubs: List<Player>,
    val overseasIds: List<String>
)

data class ImpactSwap(
    val teamId: String,
    val teamName: String,
    val outId: String,
    val outName: String,
    val inId: String,
    val inName: String,
    val innings: Int,
    val overs: Int,
    val balls: Int
)

data class ImpactState(
    val userSubs: List<Player>,
    val opponentSubs: List<Player>,
    val userUsed: Boolean = false,
    val opponentUsed: Boolean = false,
    /** Every overseas player of both squads (for the four-overseas rule). */
    val overseasIds: List<String> = emptyList(),
    /** Who went off and who came on, for the stats and the scorecard (they are no longer in the team's XI list). */
    val replacedOut: List<Player> = emptyList(),
    val swappedIn: List<Player> = emptyList(),
    val swaps: List<ImpactSwap> = emptyList()
)

object ImpactPlayer {

    const val SUBSTITUTES = 5
    const val LAST_OVER_TO_USE = 14
    const val MIN_OVERS_PER_SIDE = 10
    const val MAX_OVERSEAS_IN_XI = 4

    fun teamOf(s: MatchState, teamId: String): Team = if (teamId == s.userTeam.id) s.userTeam else s.opponentTeam

    fun isActive(s: MatchState): Boolean =
        s.impact != null && s.superOverRound == 0 && s.oversLimit >= MIN_OVERS_PER_SIDE && s.matchStatus == MatchStatus.IN_PROGRESS

    fun hasUsed(s: MatchState, teamId: String): Boolean {
        val i = s.impact ?: return true
        return if (teamId == s.userTeam.id) i.userUsed else i.opponentUsed
    }

    fun substitutes(s: MatchState, teamId: String): List<Player> {
        val i = s.impact ?: return emptyList()
        return if (teamId == s.userTeam.id) i.userSubs else i.opponentSubs
    }

    private fun withinOverLimit(s: MatchState): Boolean =
        s.score.overs < LAST_OVER_TO_USE || (s.score.overs == LAST_OVER_TO_USE && s.score.balls == 0)

    /** The ball is dead and it is a moment the rule allows. */
    fun windowOpen(s: MatchState): Boolean {
        if (!isActive(s) || s.activeRainDelay != null || s.drinksBreak != null) return false
        if (s.needsOpenerSelection || s.needsBowlerSelection) return false
        if (!withinOverLimit(s)) return false
        return MatchStateMachine.deliveriesThisOver(s) == 0 || s.pendingDismissal != null || s.ballByBall.lastOrNull()?.isWicket == true
    }

    /** Players of the XI who may go off now: not a batter at the crease, not the bowler of the over. */
    fun replaceable(s: MatchState, teamId: String): List<Player> {
        val team = teamOf(s, teamId)
        val pending = s.pendingDismissal?.playerId
        val atCrease = if (s.battingTeam.id == teamId) {
            listOf(s.currentBatsmen.first.id, s.currentBatsmen.second.id).filter { it != pending }
        } else {
            emptyList()
        }
        val bowler = if (s.bowlingTeam.id == teamId) s.currentBowler.id else null
        return team.players.filter { it.id !in atCrease && it.id != bowler }
    }

    /** True if the XI would still have at most four overseas players after this swap. */
    fun overseasOk(s: MatchState, teamId: String, out: Player, incoming: Player): Boolean {
        val overseas = s.impact?.overseasIds.orEmpty().toSet()
        val remaining = teamOf(s, teamId).players.filter { it.id != out.id }.count { it.id in overseas }
        return remaining + (if (incoming.id in overseas) 1 else 0) <= MAX_OVERSEAS_IN_XI
    }

    fun canUse(s: MatchState, teamId: String): Boolean =
        windowOpen(s) && !hasUsed(s, teamId) && substitutes(s, teamId).isNotEmpty() && replaceable(s, teamId).isNotEmpty()

    /** One line for the match screen: what the user can do, or why not. */
    fun statusLine(s: MatchState, teamId: String): String {
        val imp = s.impact ?: return ""
        val used = imp.swaps.firstOrNull { it.teamId == teamId }
        return when {
            used != null -> "Impact Player used: ${used.inName} came in for ${used.outName}."
            !withinOverLimit(s) -> "Impact Player: no longer possible after the end of the 14th over."
            s.oversLimit < MIN_OVERS_PER_SIDE -> "Impact Player: not allowed in a match shortened to fewer than 10 overs."
            else -> "Impact Player available: at the end of an over or when a wicket falls, until the end of the 14th over."
        }
    }

    /** Makes the swap. Null if the rule does not allow it right now. */
    fun apply(s: MatchState, teamId: String, outId: String, inId: String): MatchState? {
        if (!canUse(s, teamId)) return null
        val imp = s.impact ?: return null
        val team = teamOf(s, teamId)
        val out = replaceable(s, teamId).firstOrNull { it.id == outId } ?: return null
        val sub = substitutes(s, teamId).firstOrNull { it.id == inId } ?: return null
        if (!overseasOk(s, teamId, out, sub)) return null

        // In batting order: he takes the replaced player's place, unless that player has already batted this innings
        // (then he is added at the end, as the last man in).
        val batted = s.battingTeam.id == teamId && s.currentInningsData.batsmanStats.any { it.playerId == out.id }
        val index = team.players.indexOfFirst { it.id == out.id }
        val newPlayers = if (batted || index < 0) {
            team.players.filter { it.id != out.id } + sub
        } else {
            team.players.toMutableList().also { it[index] = sub }
        }
        var captain = team.captainId
        var vice = team.viceCaptainId
        var keeper = team.wicketkeeperId
        val others = newPlayers.filter { it.id != sub.id }
        if (keeper == out.id) keeper = newPlayers.firstOrNull { it.role == PlayerRole.WICKETKEEPER }?.id ?: sub.id
        if (captain == out.id) {
            captain = vice?.takeIf { v -> v != out.id && others.any { it.id == v } }
                ?: others.maxByOrNull { it.battingRating + it.bowlingRating }?.id ?: sub.id
        }
        if (vice == out.id || vice == captain) {
            vice = others.filter { it.id != captain }.maxByOrNull { it.battingRating + it.bowlingRating }?.id
        }
        val newTeam = team.copy(players = newPlayers, captainId = captain, viceCaptainId = vice, wicketkeeperId = keeper)
        fun swapped(t: Team): Team = if (t.id == teamId) newTeam else t

        val swap = ImpactSwap(
            teamId = teamId, teamName = team.name, outId = out.id, outName = out.name, inId = sub.id, inName = sub.name,
            innings = s.currentInnings, overs = s.score.overs, balls = s.score.balls
        )
        val isUser = teamId == s.userTeam.id
        val newImpact = imp.copy(
            userUsed = imp.userUsed || isUser,
            opponentUsed = imp.opponentUsed || !isUser,
            replacedOut = imp.replacedOut + out,
            swappedIn = imp.swappedIn + sub,
            swaps = imp.swaps + swap
        )
        val placements = if (s.bowlingTeam.id == teamId) {
            s.fieldPlacements.map { if (it.playerId == out.id) it.copy(playerId = sub.id) else it }
        } else {
            s.fieldPlacements
        }
        return s.copy(
            userTeam = swapped(s.userTeam),
            opponentTeam = swapped(s.opponentTeam),
            battingTeam = swapped(s.battingTeam),
            bowlingTeam = swapped(s.bowlingTeam),
            fieldPlacements = placements,
            impact = newImpact
        )
    }

    fun announcement(swap: ImpactSwap): String =
        "Impact Player! ${swap.inName} comes in for ${swap.outName} for ${swap.teamName}."

    /** For the result screen. */
    fun summaryLines(s: MatchState): List<String> =
        s.impact?.swaps.orEmpty().map {
            "Impact Player: ${it.inName} came in for ${it.outName} (${it.teamName}, ${it.overs}.${it.balls} overs into innings ${it.innings})."
        }

    /** Everyone of this team who took part: the XI as it was, the player who went off and the Impact Player who came on. */
    fun participants(s: MatchState, team: Team): List<Player> {
        val imp = s.impact ?: return team.players
        return (team.players + imp.replacedOut.filter { it.teamId == team.id } + imp.swappedIn.filter { it.teamId == team.id })
            .distinctBy { it.id }
    }

    // ---- The computer's choices ---------------------------------------------------------------------

    private fun canBowl(p: Player) = p.role == PlayerRole.BOWLER || p.role == PlayerRole.ALL_ROUNDER

    /**
     * Five substitutes from the players outside the XI: the best batter, the best fast bowler, the best spinner and the
     * best all-rounder (an Indian player first where the choice is close, as the overseas places are scarce), and then
     * whoever is best of the rest. `squad` is the whole squad; the answer never contains anyone from `xi`.
     */
    fun aiSubstitutes(squad: List<Player>, xi: List<Player>, overseasIds: Set<String>): List<Player> {
        val inXi = xi.map { it.id }.toSet()
        val reserves = squad.filter { it.id !in inXi }
        val chosen = ArrayList<Player>()
        fun take(candidates: List<Player>, score: (Player) -> Int) {
            val best = candidates.filter { c -> chosen.none { it.id == c.id } }
                .maxByOrNull { score(it) - (if (it.id in overseasIds) 4 else 0) } ?: return
            chosen.add(best)
        }
        take(reserves.filter { it.role == PlayerRole.BATSMAN || it.role == PlayerRole.WICKETKEEPER }) { it.battingRating }
        take(reserves.filter { it.role == PlayerRole.BOWLER && it.bowlingStyle == BowlingStyle.PACE }) { it.bowlingRating }
        take(reserves.filter { canBowl(it) && it.bowlingStyle == BowlingStyle.SPIN }) { it.bowlingRating }
        take(reserves.filter { it.role == PlayerRole.ALL_ROUNDER }) { it.battingRating + it.bowlingRating }
        while (chosen.size < SUBSTITUTES) {
            val before = chosen.size
            take(reserves) { maxOf(it.battingRating, it.bowlingRating) }
            if (chosen.size == before) break
        }
        return chosen
    }

    private class Context(
        val batNow: Boolean,
        val batLater: Boolean,
        val bowlNow: Boolean,
        val bowlLater: Boolean,
        val maxOvers: Int,
        val batted: Set<String>,
        val oversBowled: Map<String, Int>,
        val batBoost: Double,
        val bowlBoost: Double
    )

    private fun contextFor(s: MatchState, teamId: String): Context {
        val batNow = s.battingTeam.id == teamId
        val bowlNow = s.bowlingTeam.id == teamId
        val first = s.currentInnings == 1
        var batBoost = 1.0
        var bowlBoost = 1.0
        val target = s.target
        if (target != null && !first) {
            val ballsLeft = (s.oversLimit * 6 - (s.score.overs * 6 + s.score.balls)).coerceAtLeast(1)
            val required = (target - s.score.runs) * 6.0 / ballsLeft
            if (batNow && required > 9.5) batBoost = 1.2
            if (bowlNow && required > 8.0) bowlBoost = 1.1
        }
        return Context(
            batNow = batNow,
            batLater = first && !batNow,
            bowlNow = bowlNow,
            bowlLater = first && !bowlNow,
            maxOvers = MatchStateMachine.getMaxOversPerBowler(s.format),
            batted = if (batNow) s.currentInningsData.batsmanStats.map { it.playerId }.toSet() else emptySet(),
            oversBowled = if (bowlNow) s.currentInningsData.bowlerStats.associate { it.playerId to it.overs } else emptyMap(),
            batBoost = batBoost,
            bowlBoost = bowlBoost
        )
    }

    /** What this player can still give his side from now on. A substitute is fresh: not yet batted, a full quota to bowl. */
    private fun worth(p: Player, c: Context, fresh: Boolean): Double {
        val needBat = c.batNow || c.batLater
        val needBowl = c.bowlNow || c.bowlLater
        val weightBat = if (needBat && needBowl) 0.5 else 1.0
        val weightBowl = if (needBat && needBowl) 0.5 else 1.0
        val batAvail = when {
            c.batLater -> 1.0
            c.batNow -> if (fresh || p.id !in c.batted) 1.0 else 0.0
            else -> 0.0
        }
        val batPlace = if (p.battingRating >= 60) 1.0 else 0.6
        val bowlAvail = when {
            !canBowl(p) -> 0.0
            c.bowlLater -> 1.0
            c.bowlNow -> if (fresh) 1.0 else maxOf(0, c.maxOvers - (c.oversBowled[p.id] ?: 0)).toDouble() / c.maxOvers
            else -> 0.0
        }
        return weightBat * c.batBoost * p.battingRating * batAvail * batPlace +
            weightBowl * c.bowlBoost * p.bowlingRating * bowlAvail
    }

    /**
     * Whether the computer side uses its Impact Player now, and the swap (who goes off, who comes on). It looks for the
     * swap that gains the most, and makes it only if the gain is big enough for this stage of the match: a lot early,
     * little by the 12th over, anything at the end of the 14th (the last chance). A wicket that has just fallen makes a
     * batting swap a little easier. A small chance of waiting one more moment keeps the timing from being predictable.
     */
    fun aiChoose(s: MatchState, teamId: String): Pair<Player, Player>? {
        if (!canUse(s, teamId)) return null
        val c = contextFor(s, teamId)
        var best: Triple<Player, Player, Double>? = null
        for (out in replaceable(s, teamId)) {
            for (sub in substitutes(s, teamId)) {
                if (!overseasOk(s, teamId, out, sub)) continue
                val gain = worth(sub, c, fresh = true) - worth(out, c, fresh = false)
                if (best == null || gain > best.third) best = Triple(out, sub, gain)
            }
        }
        val pick = best ?: return null
        val overs = s.score.overs
        val atStart = overs == 0 && s.score.balls == 0
        var needed = when {
            overs < 6 -> 12.0
            overs < 10 -> 9.0
            overs < 12 -> 6.0
            overs < 14 -> 3.0
            else -> 0.5
        }
        // The break between innings is the natural moment to reshape the side; the very start of the match is not.
        if (atStart) needed = if (s.currentInnings == 2) 8.0 else 16.0
        if (s.ballByBall.lastOrNull()?.isWicket == true && c.batNow) needed -= 3.0
        if (pick.third < needed) return null
        val lastChance = overs >= LAST_OVER_TO_USE
        if (!lastChance && Random.nextDouble() < 0.15) return null
        return Pair(pick.first, pick.second)
    }
}
