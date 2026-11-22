package com.cricketsim.logic

/**
 * FULL STATISTICS (Home > Full statistics, patch 141): the whole game's records, from every match the player has PLAYED himself, in every mode except Career
 * mode, and not from a match he handed to "Simulate" (nor from the matches the computer plays by itself in a tournament). The statistics are kept by format, in
 * the tabs All, Test, ODI, T20 and T10 (a Five-over or One-over match or a custom format counts in All only).
 *
 * A finished match is added once ([GameStats.recorded] holds the ids), from its scorecard: totals, centuries, five-wicket hauls, the records (the highest and the
 * lowest total, the highest score, the best bowling) and a table of players (for the leaders). The fastest 50, 100, 150 and 200 come from the balls as they were
 * played (`MatchState.fastestMarks`, see Milestones), because a scorecard does not say on which ball a score was reached.
 */
data class StatEntry(
    /** The record's figure: a total, a score, the wickets of a bowling analysis, or the balls of a fastest innings. */
    val value: Int = 0,
    /** The second figure: the wickets of a total, the balls of a score, the runs of a bowling analysis. */
    val second: Int = 0,
    val player: String = "",
    val team: String = "",
    val against: String = "",
    val notOut: Boolean = false
)

data class PlayerAgg(
    val name: String = "",
    val team: String = "",
    val runs: Int = 0,
    val balls: Int = 0,
    val innings: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val hundreds: Int = 0,
    val fifties: Int = 0,
    val ballsBowled: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0
)

data class FormatStats(
    val matches: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val other: Int = 0,
    val innings: Int = 0,
    val runs: Int = 0,
    val wickets: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val hundreds: Int = 0,
    val fifties: Int = 0,
    val doubleHundreds: Int = 0,
    val ducks: Int = 0,
    val fiveWickets: Int = 0,
    val highestTotal: StatEntry? = null,
    val lowestTotal: StatEntry? = null,
    val highestScore: StatEntry? = null,
    val bestBowling: StatEntry? = null,
    val fastestFifty: StatEntry? = null,
    val fastestHundred: StatEntry? = null,
    val fastest150: StatEntry? = null,
    val fastest200: StatEntry? = null,
    val players: Map<String, PlayerAgg> = emptyMap()
)

data class GameStats(
    /** Keys "TEST", "ODI", "T20", "T10" and "OTHER". */
    val formats: Map<String, FormatStats> = emptyMap(),
    /** The ids of the matches already added, so a match is never counted twice. */
    val recorded: List<String> = emptyList()
)

object GameStatistics {
    /** The tabs, in order: the label shown and the key of the format ("All" is every format). */
    val TABS = listOf("All" to "", "Test" to "TEST", "ODI" to "ODI", "T20" to "T20", "T10" to "T10")

    private const val MAX_PLAYERS = 600
    private const val MAX_RECORDED = 500

    fun formatKey(card: MatchState): String = when (card.format) {
        MatchFormat.TEST -> "TEST"
        MatchFormat.ODI -> "ODI"
        MatchFormat.T20 -> "T20"
        MatchFormat.T10 -> "T10"
        else -> "OTHER"
    }

    private fun better(current: StatEntry?, candidate: StatEntry): StatEntry =
        if (current == null || candidate.value < current.value) candidate else current

    /** Adds a finished match to the statistics. A Career match, a match the user simulated, or one already added changes nothing. */
    fun record(old: GameStats, finished: MatchState): GameStats {
        val card = finished.regulationState ?: finished
        if (finished.careerPlayerId != null || card.careerPlayerId != null) return old
        if (finished.userSimulated || card.userSimulated) return old
        if (card.id in old.recorded) return old

        val key = formatKey(card)
        val innings: List<InningsData> =
            if (card.test != null) TestMatch.allInnings(card) else listOfNotNull(card.firstInningsData, card.currentInningsData)
        var f = old.formats[key] ?: FormatStats()
        f = f.copy(matches = f.matches + 1)
        val winner = finished.winnerId ?: card.winnerId
        f = when {
            winner == null -> f.copy(other = f.other + 1)
            winner == card.userTeam.id -> f.copy(won = f.won + 1)
            else -> f.copy(lost = f.lost + 1)
        }
        val players = HashMap<String, PlayerAgg>(f.players)

        for (inn in innings) {
            if (inn.totalRuns == 0 && inn.totalOvers == 0 && inn.totalBalls == 0) continue
            f = f.copy(
                innings = f.innings + 1,
                runs = f.runs + inn.totalRuns,
                wickets = f.wickets + inn.totalWickets,
                fours = f.fours + inn.batsmanStats.sumOf { it.fours },
                sixes = f.sixes + inn.batsmanStats.sumOf { it.sixes }
            )
            val total = StatEntry(inn.totalRuns, inn.totalWickets, "", inn.battingTeamName, inn.bowlingTeamName)
            val high = f.highestTotal
            if (high == null || inn.totalRuns > high.value) f = f.copy(highestTotal = total)
            val low = f.lowestTotal
            if (inn.totalWickets >= 10 && (low == null || inn.totalRuns < low.value)) f = f.copy(lowestTotal = total)

            for (b in inn.batsmanStats) {
                if (b.ballsFaced == 0 && !b.isOut) continue
                f = f.copy(
                    hundreds = f.hundreds + (if (b.runs >= 100) 1 else 0),
                    fifties = f.fifties + (if (b.runs in 50..99) 1 else 0),
                    doubleHundreds = f.doubleHundreds + (if (b.runs >= 200) 1 else 0),
                    ducks = f.ducks + (if (b.isOut && b.runs == 0) 1 else 0)
                )
                val hs = f.highestScore
                if (hs == null || b.runs > hs.value) {
                    f = f.copy(highestScore = StatEntry(b.runs, b.ballsFaced, b.playerName, inn.battingTeamName, inn.bowlingTeamName, notOut = !b.isOut))
                }
                val p = players[b.playerName] ?: PlayerAgg(b.playerName, inn.battingTeamName)
                players[b.playerName] = p.copy(
                    team = inn.battingTeamName,
                    runs = p.runs + b.runs, balls = p.balls + b.ballsFaced, innings = p.innings + 1,
                    fours = p.fours + b.fours, sixes = p.sixes + b.sixes,
                    hundreds = p.hundreds + (if (b.runs >= 100) 1 else 0),
                    fifties = p.fifties + (if (b.runs in 50..99) 1 else 0)
                )
            }
            for (w in inn.bowlerStats) {
                val balls = w.overs * 6 + w.balls
                if (balls == 0 && w.wickets == 0) continue
                f = f.copy(fiveWickets = f.fiveWickets + (if (w.wickets >= 5) 1 else 0))
                val bb = f.bestBowling
                if (w.wickets > 0 && (bb == null || w.wickets > bb.value || (w.wickets == bb.value && w.runsConceded < bb.second))) {
                    f = f.copy(bestBowling = StatEntry(w.wickets, w.runsConceded, w.playerName, inn.bowlingTeamName, inn.battingTeamName))
                }
                val p = players[w.playerName] ?: PlayerAgg(w.playerName, inn.bowlingTeamName)
                players[w.playerName] = p.copy(
                    team = inn.bowlingTeamName,
                    ballsBowled = p.ballsBowled + balls, runsConceded = p.runsConceded + w.runsConceded, wickets = p.wickets + w.wickets
                )
            }
        }

        // The fastest 50, 100, 150 and 200, from the balls as they were played.
        val marks = (finished.fastestMarks.orEmpty() + card.fastestMarks.orEmpty()).distinct()
        for (m in marks) {
            val entry = StatEntry(m.balls, 0, m.playerName, m.teamName, m.against)
            f = when (m.step) {
                50 -> f.copy(fastestFifty = better(f.fastestFifty, entry))
                100 -> f.copy(fastestHundred = better(f.fastestHundred, entry))
                150 -> f.copy(fastest150 = better(f.fastest150, entry))
                200 -> f.copy(fastest200 = better(f.fastest200, entry))
                else -> f
            }
        }

        // Keep the table of players to a sensible size: the most productive stay.
        val kept = if (players.size <= MAX_PLAYERS) players else
            players.entries.sortedByDescending { it.value.runs + it.value.wickets * 25 }.take(MAX_PLAYERS).associate { it.key to it.value }
        f = f.copy(players = kept)
        return GameStats(old.formats + (key to f), (old.recorded + card.id).takeLast(MAX_RECORDED))
    }

    // ------------------------------------------------------------ reading

    private fun bestOf(a: StatEntry?, b: StatEntry?, higher: Boolean): StatEntry? = when {
        a == null -> b
        b == null -> a
        higher -> if (b.value > a.value) b else a
        else -> if (b.value < a.value) b else a
    }

    private fun merge(a: FormatStats, b: FormatStats): FormatStats {
        val players = HashMap<String, PlayerAgg>(a.players)
        for ((name, p) in b.players) {
            val q = players[name]
            players[name] = if (q == null) p else q.copy(
                team = p.team, runs = q.runs + p.runs, balls = q.balls + p.balls, innings = q.innings + p.innings,
                fours = q.fours + p.fours, sixes = q.sixes + p.sixes, hundreds = q.hundreds + p.hundreds, fifties = q.fifties + p.fifties,
                ballsBowled = q.ballsBowled + p.ballsBowled, runsConceded = q.runsConceded + p.runsConceded, wickets = q.wickets + p.wickets
            )
        }
        // The best bowling: more wickets, then fewer runs.
        val ab = a.bestBowling
        val bbw = b.bestBowling
        val bowl = when {
            ab == null -> bbw
            bbw == null -> ab
            bbw.value > ab.value || (bbw.value == ab.value && bbw.second < ab.second) -> bbw
            else -> ab
        }
        return FormatStats(
            matches = a.matches + b.matches, won = a.won + b.won, lost = a.lost + b.lost, other = a.other + b.other,
            innings = a.innings + b.innings, runs = a.runs + b.runs, wickets = a.wickets + b.wickets, fours = a.fours + b.fours, sixes = a.sixes + b.sixes,
            hundreds = a.hundreds + b.hundreds, fifties = a.fifties + b.fifties, doubleHundreds = a.doubleHundreds + b.doubleHundreds,
            ducks = a.ducks + b.ducks, fiveWickets = a.fiveWickets + b.fiveWickets,
            highestTotal = bestOf(a.highestTotal, b.highestTotal, true), lowestTotal = bestOf(a.lowestTotal, b.lowestTotal, false),
            highestScore = bestOf(a.highestScore, b.highestScore, true), bestBowling = bowl,
            fastestFifty = bestOf(a.fastestFifty, b.fastestFifty, false), fastestHundred = bestOf(a.fastestHundred, b.fastestHundred, false),
            fastest150 = bestOf(a.fastest150, b.fastest150, false), fastest200 = bestOf(a.fastest200, b.fastest200, false),
            players = players
        )
    }

    /** One tab's statistics: "All" is every format together. */
    fun view(stats: GameStats, tab: String): FormatStats {
        val key = TABS.firstOrNull { it.first == tab }?.second.orEmpty()
        if (key.isNotEmpty()) return stats.formats[key] ?: FormatStats()
        return stats.formats.values.fold(FormatStats()) { acc, f -> merge(acc, f) }
    }

    private fun plural(n: Int, one: String, many: String): String = if (n == 1) "$n $one" else "$n $many"

    private fun against(e: StatEntry): String =
        if (e.team.isNotEmpty() && e.against.isNotEmpty()) "${e.team} against ${e.against}" else e.team

    private fun fastestText(e: StatEntry?): String =
        if (e == null) "-" else "${plural(e.value, "ball", "balls")}, ${e.player}, ${against(e)}"

    private fun top(players: Collection<PlayerAgg>, n: Int, value: (PlayerAgg) -> Int, label: (PlayerAgg) -> String): List<String> {
        val ranked = players.filter { value(it) > 0 }.sortedWith(compareByDescending<PlayerAgg> { value(it) }.thenBy { it.name }).take(n)
        if (ranked.isEmpty()) return listOf("-")
        return ranked.mapIndexed { i, p -> "${i + 1}. ${p.name}, ${p.team}: ${label(p)}" }
    }

    /** A tab as sections of lines, to be shown as headings and text. */
    fun sections(f: FormatStats): List<Pair<String, List<String>>> {
        val overview = listOf(
            "Matches played: ${f.matches}",
            "Won: ${f.won}",
            "Lost: ${f.lost}",
            "Drawn, tied or no result: ${f.other}",
            "Innings: ${f.innings}",
            "Runs scored: ${f.runs}",
            "Wickets taken: ${f.wickets}",
            "Fours: ${f.fours}",
            "Sixes: ${f.sixes}",
            "Hundreds: ${f.hundreds}",
            "Double hundreds: ${f.doubleHundreds}",
            "Fifties: ${f.fifties}",
            "Ducks: ${f.ducks}",
            "Five wickets or more in an innings: ${f.fiveWickets}"
        )
        val hs = f.highestScore
        val ht = f.highestTotal
        val lt = f.lowestTotal
        val bb = f.bestBowling
        val records = listOf(
            "Highest team total: ${if (ht == null) "-" else "${ht.value} for ${ht.second}, ${against(ht)}"}",
            "Lowest team total, all out: ${if (lt == null) "-" else "${lt.value}, ${against(lt)}"}",
            "Highest score: ${if (hs == null) "-" else "${hs.player} ${hs.value}${if (hs.notOut) " not out" else ""} from ${plural(hs.second, "ball", "balls")}, ${against(hs)}"}",
            "Best bowling in an innings: ${if (bb == null) "-" else "${bb.player} ${bb.value} for ${bb.second}, ${against(bb)}"}"
        )
        val fastest = listOf(
            "Fastest fifty: ${fastestText(f.fastestFifty)}",
            "Fastest hundred: ${fastestText(f.fastestHundred)}",
            "Fastest 150: ${fastestText(f.fastest150)}",
            "Fastest 200: ${fastestText(f.fastest200)}"
        )
        val all = f.players.values
        return listOf(
            "Overview" to overview,
            "Records" to records,
            "Fastest innings" to fastest,
            "Most runs" to top(all, 5, { it.runs }, { "${it.runs} runs" }),
            "Most wickets" to top(all, 5, { it.wickets }, { "${it.wickets} wickets" }),
            "Most hundreds" to top(all, 5, { it.hundreds }, { plural(it.hundreds, "hundred", "hundreds") }),
            "Most sixes" to top(all, 5, { it.sixes }, { plural(it.sixes, "six", "sixes") })
        )
    }
}
