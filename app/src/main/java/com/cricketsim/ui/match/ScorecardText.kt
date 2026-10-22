package com.cricketsim.ui.match

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.cricketsim.logic.BoundarySize
import com.cricketsim.logic.InningsData
import com.cricketsim.logic.MatchFormat
import com.cricketsim.logic.MatchState
import com.cricketsim.logic.PitchType
import com.cricketsim.logic.Player
import com.cricketsim.logic.PlayerAwards
import com.cricketsim.logic.PlayerRole
import com.cricketsim.logic.Stadium
import com.cricketsim.logic.SuperOver
import com.cricketsim.logic.ImpactPlayer
import com.cricketsim.logic.Team
import com.cricketsim.logic.TossDecision
import com.cricketsim.logic.WeatherCondition
import java.util.Locale

/** Who leads a side on the field. For a side whose XI was picked by the computer none is stored, so the usual rule fills the gap. */
internal class MatchLeaders(val captain: Player?, val vice: Player?, val keeper: Player?)

/**
 * The finished match as clear PLAIN TEXT (for the Share and Copy buttons on the result screen), plus the
 * small naming helpers the Match info screen shares with it so both say things the same way.
 */
object ScorecardText {

    private fun one(v: Double): String = String.format(Locale.US, "%.1f", v)
    private fun two(v: Double): String = String.format(Locale.US, "%.2f", v)

    internal fun formatName(format: MatchFormat): String = when (format) {
        MatchFormat.TEST -> "Test"
        MatchFormat.T20 -> "T20"
        MatchFormat.ODI -> "ODI"
        MatchFormat.T10 -> "T10"
        MatchFormat.FIVE_OVERS -> "5 overs"
        MatchFormat.ONE_OVER -> "1 over"
    }

    internal fun pitchName(pitch: PitchType): String = when (pitch) {
        PitchType.BATTING -> "batting-friendly"
        PitchType.BOWLING -> "bowling-friendly"
        PitchType.BALANCED -> "balanced"
        PitchType.DUSTY -> "dusty, helps spin"
        PitchType.GREEN -> "green, helps seam bowlers"
    }

    internal fun boundaryName(size: BoundarySize): String = when (size) {
        BoundarySize.SMALL -> "small"
        BoundarySize.MEDIUM -> "medium"
        BoundarySize.LARGE -> "large"
    }

    internal fun weatherName(condition: WeatherCondition): String = when (condition) {
        WeatherCondition.CLEAR -> "Clear"
        WeatherCondition.PARTLY_CLOUDY -> "Partly cloudy"
        WeatherCondition.OVERCAST -> "Overcast"
        WeatherCondition.HUMID -> "Humid"
        WeatherCondition.LIGHT_RAIN -> "Light rain"
    }

    internal fun dewName(dewFactor: Double): String = when {
        dewFactor < 0.25 -> "little dew expected"
        dewFactor < 0.6 -> "some dew expected"
        else -> "heavy dew expected"
    }

    internal fun roleName(role: PlayerRole): String = when (role) {
        PlayerRole.BATSMAN -> "batter"
        PlayerRole.BOWLER -> "bowler"
        PlayerRole.ALL_ROUNDER -> "all-rounder"
        PlayerRole.WICKETKEEPER -> "wicketkeeper"
    }

    internal fun tossLine(state: MatchState): String {
        val winner = if (state.tossWinnerId == state.userTeam.id) state.userTeam.name else state.opponentTeam.name
        val choice = if (state.tossDecision == TossDecision.BAT) "bat" else "bowl"
        return "$winner won the toss and chose to $choice."
    }

    /** Captain, vice-captain and keeper of an XI; when none is stored, the two most valuable players and the keeper by role. */
    internal fun leaders(team: Team): MatchLeaders {
        val byId = team.players.associateBy { it.id }
        fun value(p: Player): Double = maxOf(p.battingRating, p.bowlingRating) + p.fieldingRating * 0.1
        val ranked = team.players.sortedByDescending { value(it) }
        val captain = team.captainId?.let { byId[it] } ?: ranked.firstOrNull()
        val vice = team.viceCaptainId?.let { byId[it] } ?: ranked.firstOrNull { it.id != captain?.id }
        val keeper = team.wicketkeeperId?.let { byId[it] } ?: team.players.firstOrNull { it.role == PlayerRole.WICKETKEEPER }
        return MatchLeaders(captain, vice, keeper)
    }

    /** Who is captain, vice-captain and wicketkeeper of each side, by player id (for the tags beside names). */
    internal fun roleMap(state: MatchState): Map<String, String> {
        val map = HashMap<String, String>()
        for (team in listOf(state.userTeam, state.opponentTeam)) {
            val lead = leaders(team)
            val jobs = LinkedHashMap<String, MutableList<String>>()
            lead.captain?.let { jobs.getOrPut(it.id) { mutableListOf() }.add("captain") }
            lead.vice?.let { jobs.getOrPut(it.id) { mutableListOf() }.add("vice-captain") }
            lead.keeper?.let { jobs.getOrPut(it.id) { mutableListOf() }.add("wicketkeeper") }
            jobs.forEach { (id, list) -> map[id] = list.joinToString(" and ") }
        }
        return map
    }

    /** "India: captain Rohit Sharma, vice-captain Virat Kohli, wicketkeeper KL Rahul." */
    internal fun leadersLine(team: Team): String {
        val lead = leaders(team)
        return "${team.name}: captain ${lead.captain?.name ?: "not named"}, vice-captain ${lead.vice?.name ?: "not named"}, " +
            "wicketkeeper ${lead.keeper?.name ?: "not named"}."
    }

    /** The whole finished match: teams, ground, toss, result, then each innings in full. */
    fun build(state: MatchState, stadium: Stadium, resultText: String?): String {
        // After a Super Over the ordinary match's two innings are kept in regulationState.
        val full = state.regulationState ?: state
        val sb = StringBuilder()
        sb.append("SCORECARD\n")
        sb.append(state.userTeam.name).append(" vs ").append(state.opponentTeam.name).append("\n")
        sb.append(formatName(state.format)).append(" match at ").append(stadium.name)
            .append(", ").append(stadium.city).append(", ").append(stadium.country).append("\n")
        sb.append(tossLine(state)).append("\n")
        if (!resultText.isNullOrBlank()) sb.append("Result: ").append(resultText).append("\n")
        PlayerAwards.playerOfTheMatch(state)?.let {
            sb.append("Player of the match: ").append(it.name).append(" (").append(it.teamName).append("), ")
                .append(it.summary).append(".\n")
        }

        val roles = roleMap(state)
        sb.append("\nTEAM LEADERS\n")
        sb.append(leadersLine(state.userTeam)).append("\n")
        sb.append(leadersLine(state.opponentTeam)).append("\n")

        full.firstInningsData?.let { appendInnings(sb, "FIRST INNINGS", it, roles) }
        appendInnings(sb, "SECOND INNINGS", full.currentInningsData, roles)

        val superOver = SuperOver.summaryLines(state)
        if (superOver.isNotEmpty()) {
            sb.append("\nSUPER OVER\n")
            superOver.forEach { sb.append(it).append("\n") }
        }
        val impact = ImpactPlayer.summaryLines(state)
        if (impact.isNotEmpty()) {
            sb.append("\nIMPACT PLAYERS\n")
            impact.forEach { sb.append(it).append("\n") }
        }
        sb.append("\nShared from the Accessible Cricket Simulator.")
        return sb.toString()
    }

    private fun appendInnings(sb: StringBuilder, title: String, inn: InningsData, roles: Map<String, String>) {
        sb.append("\n").append(title).append("\n")
        sb.append(inn.battingTeamName).append(": ").append(inn.totalRuns).append("/").append(inn.totalWickets)
            .append(" in ").append(inn.totalOvers).append(".").append(inn.totalBalls).append(" overs\n\n")

        sb.append("Batting\n")
        inn.batsmanStats.forEachIndexed { i, b ->
            val status = if (b.isOut) ScorecardLines.dismissalPhrase(b.dismissalType, b.dismissedBy, b.caughtBy, b.caughtAt) else "not out"
            sb.append(i + 1).append(". ").append(ScorecardLines.named(b.playerName, b.playerId, roles)).append(" - ").append(status)
                .append(" - ").append(b.runs).append(" off ").append(countOf(b.ballsFaced, "ball"))
                .append(", ").append(countOf(b.fours, "four")).append(", ").append(countOf(b.sixes, "six", "sixes"))
                .append(", strike rate ").append(one(b.strikeRate)).append("\n")
        }
        sb.append("Extras: ").append(inn.extras.total).append(" (")
            .append(countOf(inn.extras.wides, "wide")).append(", ").append(countOf(inn.extras.noBalls, "no ball")).append(")\n")
        sb.append("Total: ").append(inn.totalRuns).append(" for ").append(inn.totalWickets)
            .append(" in ").append(inn.totalOvers).append(".").append(inn.totalBalls).append(" overs\n\n")

        sb.append("Bowling\n")
        inn.bowlerStats.forEachIndexed { i, b ->
            sb.append(i + 1).append(". ").append(ScorecardLines.named(b.playerName, b.playerId, roles)).append(" - ")
                .append(b.overs).append(".").append(b.balls).append(" overs, ")
                .append(countOf(b.maidens, "maiden")).append(", ")
                .append(countOf(b.runsConceded, "run")).append(", ")
                .append(countOf(b.wickets, "wicket")).append(", economy ").append(two(b.economy)).append("\n")
        }

        if (inn.fallOfWickets.isNotEmpty()) {
            sb.append("\nFall of wickets\n")
            inn.fallOfWickets.forEach { w ->
                sb.append(w.wicketNumber).append("-").append(w.teamRuns).append(" (").append(ScorecardLines.named(w.batsmanName, w.batsmanId, roles))
                    .append(", ").append(w.overs).append(".").append(w.balls).append(" overs)\n")
            }
        }
    }

    /** Opens the phone's share sheet with the text. */
    fun share(context: Context, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, "Share the scorecard").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(chooser) }
    }

    /** Puts the text on the clipboard and says so. */
    fun copy(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Scorecard", text))
        Toast.makeText(context, "The scorecard is copied. You can paste it anywhere.", Toast.LENGTH_SHORT).show()
    }
}
