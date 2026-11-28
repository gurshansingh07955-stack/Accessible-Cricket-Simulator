package com.cricketsim.logic

import com.cricketsim.persistence.MatchSaveStore
import com.cricketsim.persistence.MatchSnapshot
import com.cricketsim.ui.match.MatchSimulation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * CHALLENGE MODE (patch 161): five new challenges every day, each a match situation the player has to win.
 *
 * Every challenge is a T10, T20 or ODI between two international teams, at a random ground, with both Playing XIs and the toss decided by the game:
 *  - CHASE_START   the other side has batted: "Chase 216 in 50 overs", the match opens at the innings break.
 *  - DEFEND_START  you have batted: "Defend 180 in 50 overs", you bowl next.
 *  - CHASE_MID     a chase already under way: "Pakistan made 287. India are 128 for 4 after 25 overs." You take over.
 *  - DEFEND_MID    a defence already under way: "India made 287. Pakistan are 128 for 4 after 25 overs." You bowl.
 * A challenge is completed by winning the match.
 *
 * THE DAY'S FIVE are planned from the date alone ([ChallengePlanner.plan]), so they are the same all day and new at midnight. Each situation is then BUILT
 * by really playing the innings with the computer on both sides ([ChallengeBuilder], the same ball model and engine the "Simulate match" feature uses),
 * trying again until the first innings total lands near the planned one, so the scorecards, bowling figures and batters at the crease are all real.
 * The numbers in a challenge are therefore the ones the simulation produced (about the planned total, not exactly it).
 */
enum class ChallengeKind { CHASE_START, DEFEND_START, CHASE_MID, DEFEND_MID }

data class ChallengeEntry(
    val id: String,
    val kind: ChallengeKind,
    /** A [MatchFormat] name: T10, T20 or ODI. */
    val formatName: String,
    val userTeamId: String,
    val opponentTeamId: String,
    val stadiumId: String,
    /** The first-innings total the planner aimed for (the built challenge is within a few percent of it). */
    val designTotal: Int,
    /** For the "from the middle" kinds: the overs already played in the second innings. */
    val midOvers: Int,
    /** PREPARING (being built), READY, or UNAVAILABLE (could not be built; it is tried again the next time the screen opens). */
    val status: String = "PREPARING",
    val title: String = "",
    val description: String = "",
    val winChance: Int = 0,
    /** NONE, WON (completed) or LOST (the last try was lost). */
    val result: String = "NONE",
    val attempts: Int = 0
)

data class ChallengeData(
    val day: String = "",
    val entries: List<ChallengeEntry> = emptyList(),
    val completedAllTime: Int = 0,
    val attemptsAllTime: Int = 0
)

object ChallengePlanner {
    const val COUNT = 5

    /** Today's date on the phone, as 2026-11-28: the challenges change when it does. */
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** The average first-innings score of a format at a ground, as the game's own ball model plays it (T10 about 0.62 of a T20, ODI about 1.78). */
    fun par(format: MatchFormat, stadium: Stadium): Double =
        stadium.avgT20FirstInningsScore * when (format) {
            MatchFormat.T10 -> 0.62
            MatchFormat.ODI -> 1.78
            else -> 1.0
        }

    /** The day's five, in order, each still to be built. The same date always gives the same five. */
    fun plan(day: String): List<ChallengeEntry> {
        val random = Random(day.hashCode().toLong() * 7919L + 13L)
        val teams = CricketData.getAllTeams().filter { it.players.size >= 11 }
        val grounds = StadiumData.STADIUMS
        val extra = listOf(MatchFormat.T10, MatchFormat.T20, MatchFormat.T20, MatchFormat.ODI, MatchFormat.ODI)
        // Every day has at least one T10, one T20 and one ODI.
        val formats = (listOf(MatchFormat.T10, MatchFormat.T20, MatchFormat.ODI) + List(COUNT - 3) { extra[random.nextInt(extra.size)] }).shuffled(random)
        val kindList = ChallengeKind.values().toList()
        val kinds = (kindList + List(COUNT - kindList.size) { kindList[random.nextInt(kindList.size)] }).shuffled(random)
        val usedUsers = HashSet<String>()
        val out = ArrayList<ChallengeEntry>()
        for (i in 0 until COUNT) {
            var user = teams[random.nextInt(teams.size)]
            var guard = 0
            while (user.id in usedUsers && guard++ < 30) user = teams[random.nextInt(teams.size)]
            usedUsers.add(user.id)
            var opponent = teams[random.nextInt(teams.size)]
            while (opponent.id == user.id) opponent = teams[random.nextInt(teams.size)]
            val format = formats[i]
            val kind = kinds[i]
            val stadium = grounds[random.nextInt(grounds.size)]
            val overs = MatchEngine.getFormatOvers(format)
            val par = par(format, stadium)
            // A low total to defend or a high one to chase is the hard end; the ranges keep every challenge possible.
            val ratio = when (kind) {
                ChallengeKind.DEFEND_START -> random.nextDouble(0.78, 1.08)
                ChallengeKind.CHASE_START -> random.nextDouble(0.95, 1.30)
                ChallengeKind.CHASE_MID -> random.nextDouble(0.90, 1.25)
                ChallengeKind.DEFEND_MID -> random.nextDouble(0.85, 1.15)
            }
            val mid = if (kind == ChallengeKind.CHASE_MID || kind == ChallengeKind.DEFEND_MID)
                (overs * random.nextDouble(0.40, 0.62)).roundToInt().coerceIn(2, overs - 2) else 0
            out.add(
                ChallengeEntry(
                    id = "$day-${i + 1}", kind = kind, formatName = format.name,
                    userTeamId = user.id, opponentTeamId = opponent.id, stadiumId = stadium.id,
                    designTotal = (par * ratio).roundToInt(), midOvers = mid
                )
            )
        }
        return out
    }
}

object ChallengeBuilder {
    /** A built challenge: the match to open (a saved-match snapshot), and the words for it. */
    class Built(val snapshot: MatchSnapshot, val title: String, val description: String, val winChance: Int)

    private const val OBSERVER = "challenge_observer"
    private const val TOLERANCE = 0.07
    private const val MAX_ATTEMPTS = 40
    private const val TIME_BUDGET_MS = 10_000L

    private fun xiOf(team: Team): Team {
        val auto = CricketData.autoSelectPlayingXI(team)
        return CricketData.buildMatchSquad(
            fullTeam = team,
            selectedPlayerIds = auto.players.map { it.id },
            captainId = auto.captainId,
            viceCaptainId = auto.viceCaptainId,
            wicketkeeperId = auto.wicketkeeperId
        )
    }

    private fun formatLabel(f: MatchFormat): String = when (f) {
        MatchFormat.T10 -> "T10"
        MatchFormat.ODI -> "ODI"
        else -> "T20"
    }

    private fun oversText(s: MatchScore): String = if (s.balls == 0) "${s.overs}" else "${s.overs}.${s.balls}"

    private fun inningsText(name: String, s: MatchScore): String =
        if (s.wickets >= 10) "$name were all out for ${s.runs} in ${oversText(s)} overs" else "$name made ${s.runs} for ${s.wickets} in ${oversText(s)} overs"

    /** The first innings, played by the computer on both sides (neither is "the user"), from the toss to the end of the innings. Null if it did not finish normally. */
    private fun firstInnings(format: MatchFormat, stadium: Stadium, userXI: Team, oppXI: Team, toss: TossResult, weather: WeatherSnapshot, random: Random): MatchState? {
        var s = MatchStateMachine.createNewMatch(format, stadium.pitchType, userXI, oppXI, toss.winnerId, toss.decision, stadium.id, weather)
        s = s.copy(
            userTeam = userXI.copy(id = OBSERVER),
            needsOpenerSelection = false,
            needsBowlerSelection = false,
            fieldPlacements = FieldingSystem.generateAiFieldPlacements(
                FieldingSystem.getFieldingPlayers(s.bowlingTeam, s.currentBowler.id),
                FieldingSystem.isRestrictedOver(s, 0)
            )
        )
        val result = MatchFastForward.run(s, stadium, FastForwardRequest(null, 0, null), random)
        val out = result.state
        return if (result.stop == "innings" && out.currentInnings == 1 && MatchSimulation.isInningsOver(out)) out else null
    }

    /** Builds one challenge. Slow (it plays whole innings several times): call it off the main thread. Null if it could not be built. */
    fun build(entry: ChallengeEntry, random: Random = Random.Default): Built? {
        val format = MatchFormat.valueOf(entry.formatName)
        val userFull = CricketData.getTeamById(entry.userTeamId) ?: return null
        val oppFull = CricketData.getTeamById(entry.opponentTeamId) ?: return null
        val stadium = StadiumData.getStadiumById(entry.stadiumId) ?: return null
        val userXI = xiOf(userFull)
        val oppXI = xiOf(oppFull)
        val overs = MatchEngine.getFormatOvers(format)
        val userBatsFirst = entry.kind == ChallengeKind.DEFEND_START || entry.kind == ChallengeKind.DEFEND_MID
        // The toss is decided to fit the situation: whoever batted first either won the toss and chose to bat, or lost it and was put in.
        val toss = if (userBatsFirst) {
            if (random.nextBoolean()) TossResult(userXI.id, TossDecision.BAT) else TossResult(oppXI.id, TossDecision.BOWL)
        } else {
            if (random.nextBoolean()) TossResult(oppXI.id, TossDecision.BAT) else TossResult(userXI.id, TossDecision.BOWL)
        }
        val weather = WeatherSystem.generateWeatherForStadium(stadium)

        // The first innings: played again until its total is close to the planned one (or the best of the tries is kept).
        val started = System.currentTimeMillis()
        var best: MatchState? = null
        var bestGap = Int.MAX_VALUE
        var attempts = 0
        while (attempts < MAX_ATTEMPTS && System.currentTimeMillis() - started < TIME_BUDGET_MS) {
            attempts++
            val fin = firstInnings(format, stadium, userXI, oppXI, toss, weather, random) ?: continue
            val gap = abs(fin.score.runs - entry.designTotal)
            if (gap < bestGap) {
                best = fin
                bestGap = gap
            }
            if (gap <= entry.designTotal * TOLERANCE) break
        }
        val first = best ?: return null
        val firstScore = first.score
        val firstBatting = if (userBatsFirst) userXI else oppXI
        val secondBatting = if (userBatsFirst) oppXI else userXI
        val tossLine = "${if (toss.winnerId == userXI.id) userXI.name else oppXI.name} won the toss and chose to ${if (toss.decision == TossDecision.BAT) "bat" else "bowl"} first."
        val where = "${formatLabel(format)}, ${userXI.name} versus ${oppXI.name} at ${stadium.name}, ${stadium.city}."
        val target = firstScore.runs + 1

        val state: MatchState
        val showBreak: Boolean
        val title: String
        val story: String
        if (entry.kind == ChallengeKind.CHASE_START || entry.kind == ChallengeKind.DEFEND_START) {
            // The match opens at the innings break: the user's side is back to being the user's (the engine then asks for what the user must choose).
            state = MatchStateMachine.switchInnings(first.copy(userTeam = userXI))
            showBreak = true
            if (entry.kind == ChallengeKind.CHASE_START) {
                title = "Chase $target in $overs overs"
                story = "${inningsText(firstBatting.name, firstScore)}. ${userXI.name} need $target to win in $overs overs. You bat."
            } else {
                title = "Defend ${firstScore.runs} in $overs overs"
                story = "${inningsText(firstBatting.name, firstScore)}. ${oppXI.name} need $target to win in $overs overs. You bowl: defend it."
            }
        } else {
            // From the middle: the second innings is played by the computer for the overs planned, on a fresh try until the position is a real contest.
            var mid: MatchState? = null
            var tries = 0
            while (tries < 8) {
                tries++
                val switched = MatchStateMachine.switchInnings(first)
                if (switched.currentInnings != 2) return null
                val res = MatchFastForward.run(switched, stadium, FastForwardRequest(entry.midOvers, 0, null), random)
                val s = res.state
                val ended = MatchSimulation.isInningsOver(s) || MatchSimulation.isTargetReached(s)
                if (res.stop != "done" || ended) continue
                val ballsLeft = overs * 6 - (s.score.overs * 6 + s.score.balls)
                val need = target - s.score.runs
                if (ballsLeft <= 0 || need <= 0) continue
                val required = need * 6.0 / ballsLeft
                val limit = when (format) { MatchFormat.T10 -> 18.0; MatchFormat.ODI -> 11.0; else -> 15.0 }
                mid = s
                if (s.score.wickets <= 6 && required in 3.0..limit) break
            }
            val s = mid ?: return null
            var m = s.copy(userTeam = userXI)
            // The user picks the next bowler at the end of an over, as always, when his side is bowling.
            if (m.score.balls == 0 && m.ballByBall.isNotEmpty() && m.bowlingTeam.id == userXI.id && m.userSelects) {
                m = m.copy(needsBowlerSelection = true)
            }
            state = m
            showBreak = false
            val ballsLeft = overs * 6 - (m.score.overs * 6 + m.score.balls)
            val need = target - m.score.runs
            val required = need * 6.0 / ballsLeft
            val now = "${secondBatting.name} are ${m.score.runs} for ${m.score.wickets} after ${oversText(m.score)} overs and need $need more from $ballsLeft balls (${"%.1f".format(Locale.US, required)} an over)."
            if (entry.kind == ChallengeKind.CHASE_MID) {
                title = "Chase $target: ${m.score.runs} for ${m.score.wickets} after ${oversText(m.score)} overs"
                story = "${inningsText(firstBatting.name, firstScore)}. $now Take over the chase."
            } else {
                title = "Defend ${firstScore.runs}: ${secondBatting.name} ${m.score.runs} for ${m.score.wickets} after ${oversText(m.score)} overs"
                story = "${inningsText(firstBatting.name, firstScore)}. $now You are bowling: defend it."
            }
        }

        val battingPercent = WinProbability.percentForBatting(state)
        val userBatting = state.battingTeam.id == userXI.id
        val chance = if (battingPercent == null) 50 else if (userBatting) battingPercent else 100 - battingPercent
        val description = "$where $story $tossLine Your chance of winning at the start: $chance percent."
        val snapshot = MatchSnapshot(
            version = MatchSaveStore.CURRENT_VERSION,
            stadium = stadium,
            toss = toss,
            state = state,
            recentCommentary = emptyList(),
            showInningsBreak = showBreak,
            breakLastBall = ""
        )
        return Built(snapshot, title, description, chance)
    }
}
