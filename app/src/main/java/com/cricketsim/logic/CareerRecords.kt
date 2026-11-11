package com.cricketsim.logic

import kotlin.math.roundToInt

/** What a player has done in one kind of cricket over his whole career (Club, State, Test, ODI, T20I or League). */
data class CareerRecord(
    val matches: Int = 0,
    val innings: Int = 0,
    val notOuts: Int = 0,
    val runs: Int = 0,
    val balls: Int = 0,
    val highScore: Int = 0,
    val fifties: Int = 0,
    val hundreds: Int = 0,
    val ballsBowled: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    val fiveWickets: Int = 0,
    val bestWickets: Int = 0,
    val bestRuns: Int = 0,
    val catches: Int = 0
)

/** A goal for the season ("Score 300 runs this season"). Its progress is counted from the season's competitions, so it is never stored. */
data class CareerObjective(val kind: String, val target: Int, val text: String)

/**
 * My Player, part eleven: the career record, the rankings, the season's objectives, the honours and the end-of-season settlement.
 *
 * THE RECORD. After every match [updated] adds what he did (the difference of his tournament statistics around the match) to the record of the kind of
 * cricket it was: "Club", "State", "T20I", "ODI", "Test" or "League". [recordLines] says it in sentences.
 *
 * RANKINGS ([rankings]). His batting and bowling ratings against every player of the game's national teams, and his place in his country's squad.
 *
 * OBJECTIVES ([newObjectives]): three goals by role and level, counted from the season's competitions ([tally]). At the end of the season
 * ([settleSeason]) every goal reached gives +1 to the rating it is about, and the honours of the season (the competitions his team won, the
 * Player of the Series awards) are added to his honours. Dials: the targets in [newObjectives].
 */
object CareerRecords {

    private val ORDER = listOf(
        "Test" to "Test matches",
        "ODI" to "One-day internationals",
        "T20I" to "T20 internationals",
        "League" to "Franchise leagues",
        "State" to "State cricket",
        "Club" to "Club cricket"
    )

    /** The kind of cricket a match was. */
    fun categoryOf(career: CareerProfile, format: String, league: Boolean): String = when {
        league -> "League"
        format == "test" -> "Test"
        format == "odi" -> "ODI"
        format == "t20" -> "T20I"
        career.isState -> "State"
        else -> "Club"
    }

    private fun add(rec: CareerRecord?, before: PlayerStats?, after: PlayerStats): CareerRecord {
        val r = rec ?: CareerRecord()
        fun d(f: (PlayerStats) -> Int) = f(after) - (before?.let(f) ?: 0)
        val betterBest = after.bestWickets > r.bestWickets || (after.bestWickets == r.bestWickets && after.bestWickets > 0 && after.bestRuns < r.bestRuns)
        return r.copy(
            matches = r.matches + 1,
            innings = r.innings + d { it.innings },
            notOuts = r.notOuts + d { it.notOuts },
            runs = r.runs + d { it.runs },
            balls = r.balls + d { it.balls },
            highScore = maxOf(r.highScore, after.highScore),
            fifties = r.fifties + d { it.fifties },
            hundreds = r.hundreds + d { it.hundreds },
            ballsBowled = r.ballsBowled + d { it.ballsBowled },
            runsConceded = r.runsConceded + d { it.runsConceded },
            wickets = r.wickets + d { it.wickets },
            fiveWickets = r.fiveWickets + d { it.fiveWickets },
            bestWickets = if (betterBest) after.bestWickets else r.bestWickets,
            bestRuns = if (betterBest) after.bestRuns else r.bestRuns,
            catches = r.catches + d { it.catches }
        )
    }

    /** The career's records with the match just played added (the caller has checked that he played). */
    fun updated(career: CareerProfile, before: PlayerStats?, after: PlayerStats, format: String, league: Boolean): Map<String, CareerRecord> {
        val key = categoryOf(career, format, league)
        val old = career.records.orEmpty()
        return old + (key to add(old[key], before, after))
    }

    private fun describe(r: CareerRecord): String {
        val parts = ArrayList<String>()
        parts.add("${r.matches} ${if (r.matches == 1) "match" else "matches"}")
        if (r.balls > 0) {
            val outs = r.innings - r.notOuts
            val average = if (outs > 0) "average " + String.format(java.util.Locale.US, "%.1f", r.runs.toDouble() / outs) else "not out in every innings"
            val milestones = (if (r.hundreds > 0) ", ${r.hundreds} hundreds" else "") + (if (r.fifties > 0) ", ${r.fifties} fifties" else "")
            parts.add("${r.runs} runs in ${r.innings} innings, $average, highest ${r.highScore}$milestones")
        }
        if (r.ballsBowled > 0) {
            parts.add("${r.wickets} wickets for ${r.runsConceded} runs, best figures ${r.bestWickets} for ${r.bestRuns}")
        }
        if (r.catches > 0) parts.add("${r.catches} catches")
        return parts.joinToString(". ") + "."
    }

    /** The career record in sentences, one line for each kind of cricket he has played, and his totals. */
    fun recordLines(career: CareerProfile): List<String> {
        val recs = career.records.orEmpty()
        val out = ArrayList<String>()
        for ((key, label) in ORDER) {
            val r = recs[key] ?: continue
            if (r.matches > 0) out.add("$label: ${describe(r)}")
        }
        val all = recs.values
        if (all.sumOf { it.matches } > 0 && out.size > 1) {
            out.add("In all: ${all.sumOf { it.matches }} matches, ${all.sumOf { it.runs }} runs, ${all.sumOf { it.wickets }} wickets, ${all.sumOf { it.catches }} catches.")
        }
        return out
    }

    // ------------------------------------------------------------ rankings

    private fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    private fun value(p: Player): Double = when (p.role) {
        PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER -> p.battingRating * 0.8 + p.fieldingRating * 0.2
        PlayerRole.BOWLER -> p.bowlingRating * 0.8 + p.fieldingRating * 0.2
        PlayerRole.ALL_ROUNDER -> p.battingRating * 0.45 + p.bowlingRating * 0.45 + p.fieldingRating * 0.1
    }

    private fun roleWord(role: PlayerRole): String = when (role) {
        PlayerRole.BATSMAN -> "batsmen"
        PlayerRole.BOWLER -> "bowlers"
        PlayerRole.ALL_ROUNDER -> "all-rounders"
        PlayerRole.WICKETKEEPER -> "wicketkeepers"
    }

    /** Where his ratings put him among the players of the game's national teams, and in his own country's squad. Builds the teams: call it off the main thread. */
    fun rankings(career: CareerProfile): List<String> {
        val teams = CricketData.getAllTeams().map { CareerInternational.evolved(it, career.season, null) }
        val everyone = teams.flatMap { it.players }
        val role = CareerSeason.roleOf(career.role)
        val out = ArrayList<String>()
        if (role != PlayerRole.BOWLER) {
            val pool = everyone.filter { it.role != PlayerRole.BOWLER }
            val rank = 1 + pool.count { it.battingRating > career.batting }
            out.add("Batting: ${ordinal(rank)} of ${pool.size + 1} batters of the game's national teams, with a batting rating of ${career.batting}.")
        }
        if (role == PlayerRole.BOWLER || role == PlayerRole.ALL_ROUNDER) {
            val pool = everyone.filter { it.role == PlayerRole.BOWLER || it.role == PlayerRole.ALL_ROUNDER }
            val rank = 1 + pool.count { it.bowlingRating > career.bowling }
            out.add("Bowling: ${ordinal(rank)} of ${pool.size + 1} bowlers of the game's national teams, with a bowling rating of ${career.bowling}.")
        }
        val fieldRank = 1 + everyone.count { it.fieldingRating > career.fielding }
        out.add("Fielding: ${ordinal(fieldRank)} of ${everyone.size + 1} players, with a fielding rating of ${career.fielding}.")
        val squad = teams.find { it.name == career.nation }
        if (squad != null) {
            val mine = when (role) {
                PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER -> career.batting * 0.8 + career.fielding * 0.2
                PlayerRole.BOWLER -> career.bowling * 0.8 + career.fielding * 0.2
                PlayerRole.ALL_ROUNDER -> career.batting * 0.45 + career.bowling * 0.45 + career.fielding * 0.1
            }
            val group = squad.players.filter { it.role == role }
            val rank = 1 + group.count { value(it) > mine }
            out.add("In the ${career.nation} squad you would rank ${ordinal(rank)} of ${group.size + 1} ${roleWord(role)}.")
        }
        return out
    }

    // ------------------------------------------------------------ objectives

    /** What he has done this season, counted from the season's competitions. */
    class Tally(val matches: Int, val runs: Int, val wickets: Int, val catches: Int, val fifties: Int, val pos: Int)

    private fun isPlayerOfSeries(state: TournamentState, pid: String): Boolean {
        val best = state.playerStats.values.maxWithOrNull(compareBy<PlayerStats>({ it.impact }, { it.runs + it.wickets * 20 })) ?: return false
        return best.playerId == pid && best.impact > 0.0
    }

    fun tally(career: CareerProfile, states: List<TournamentState>): Tally {
        var matches = 0
        var runs = 0
        var wickets = 0
        var catches = 0
        var fifties = 0
        var pos = 0
        for (s in states) {
            val pid = CareerSeason.playerIdIn(s, career) ?: continue
            val st = s.playerStats[pid] ?: continue
            matches += st.matches
            runs += st.runs
            wickets += st.wickets
            catches += st.catches
            fifties += st.fifties
            if (CareerSeason.isComplete(s) && isPlayerOfSeries(s, pid)) pos++
        }
        return Tally(matches, runs, wickets, catches, fifties, pos)
    }

    private fun objective(kind: String, target: Int): CareerObjective {
        val text = when (kind) {
            "runs" -> "Score $target runs this season"
            "wickets" -> "Take $target wickets this season"
            "fifties" -> "Score $target ${if (target == 1) "fifty" else "fifties"} this season"
            "catches" -> "Take $target catches this season"
            else -> "Be Player of the Series at least once"
        }
        return CareerObjective(kind, target, text)
    }

    /** Three goals for the season by role and level (a state player's season is longer than a club player's; an international's matches are harder). */
    fun newObjectives(career: CareerProfile): List<CareerObjective> {
        val scale = when {
            career.isInternational -> 0.9
            career.isState -> 1.2
            else -> 1.0
        }
        fun five(n: Int): Int = ((n * scale) / 5.0).roundToInt().coerceAtLeast(1) * 5
        fun few(n: Int): Int = (n * scale).roundToInt().coerceAtLeast(1)
        return when (career.role) {
            "Batsman" -> listOf(objective("runs", five(250)), objective("fifties", few(2)), objective("pos", 1))
            "Wicketkeeper-batsman" -> listOf(objective("runs", five(200)), objective("catches", few(5)), objective("pos", 1))
            "Bowler" -> listOf(objective("wickets", few(12)), objective("catches", few(3)), objective("pos", 1))
            else -> listOf(objective("runs", five(150)), objective("wickets", few(8)), objective("pos", 1))
        }
    }

    fun progress(o: CareerObjective, t: Tally): Int = when (o.kind) {
        "runs" -> t.runs
        "wickets" -> t.wickets
        "fifties" -> t.fifties
        "catches" -> t.catches
        else -> t.pos
    }

    /** One sentence for each objective: how far he is, or that it is done. */
    fun objectiveLines(career: CareerProfile, states: List<TournamentState>): List<String> {
        val t = tally(career, states)
        return newObjectives(career).map { o ->
            val p = progress(o, t)
            if (p >= o.target) "${o.text}: done." else "${o.text}: $p so far."
        }
    }

    // ------------------------------------------------------------ the end of the season

    class Settlement(val career: CareerProfile, val text: String)

    /**
     * At the end of a season: the honours (the competitions his team won, the Player of the Series awards) are added to his honours, and every
     * objective reached gives +1 to the rating it is about (batting for runs and fifties, bowling for wickets, fielding for catches, the main skill of his
     * role for the Player of the Series award).
     */
    fun settleSeason(career: CareerProfile, states: List<TournamentState>): Settlement {
        val season = career.season
        val honours = ArrayList<String>()
        for (s in states) {
            if (!CareerSeason.isComplete(s)) continue
            val label = CareerSeason.competitionOf(s).label
            val champion = CareerSeason.champion(s)
            if (champion != null && champion.id == s.userTeamId) honours.add("Season $season: won the $label with ${champion.name}.")
            val pid = CareerSeason.playerIdIn(s, career)
            if (pid != null && s.playerStats[pid] != null && isPlayerOfSeries(s, pid)) honours.add("Season $season: Player of the Series in the $label.")
        }
        val t = tally(career, states)
        val objectives = newObjectives(career)
        var bat = career.batting
        var bowl = career.bowling
        var field = career.fielding
        var done = 0
        val mainBat = career.role != "Bowler"
        for (o in objectives) {
            if (progress(o, t) < o.target) continue
            done++
            when (o.kind) {
                "runs", "fifties" -> bat = (bat + 1).coerceAtMost(95)
                "wickets" -> bowl = (bowl + 1).coerceAtMost(95)
                "catches" -> field = (field + 1).coerceAtMost(95)
                else -> if (mainBat) bat = (bat + 1).coerceAtMost(95) else bowl = (bowl + 1).coerceAtMost(95)
            }
        }
        val changes = listOfNotNull(
            if (bat > career.batting) "batting +${bat - career.batting}" else null,
            if (bowl > career.bowling) "bowling +${bowl - career.bowling}" else null,
            if (field > career.fielding) "fielding +${field - career.fielding}" else null
        )
        if (done == objectives.size && done > 0 && t.matches > 0) honours.add("Season $season: reached every season objective.")
        val clubs = career.clubs.map { club ->
            if (club.name != career.club) club
            else club.copy(players = club.players.map { if (it.isUser) it.copy(batting = bat, bowling = bowl, fielding = field) else it })
        }
        val updated = career.copy(
            batting = bat, bowling = bowl, fielding = field, clubTeams = clubs,
            honours = (career.honours.orEmpty() + honours).takeLast(60)
        )
        val objectiveText = if (t.matches == 0) "" else "You reached $done of ${objectives.size} season objectives" + (if (changes.isEmpty()) "." else " and your ratings grew: ${changes.joinToString(", ")}.")
        val honourText = if (honours.isEmpty()) "" else " Honours: ${honours.joinToString(" ") { it.substringAfter(": ") }}"
        // Ageing and retirement (part twelve).
        val aged = CareerHealth.aged(updated)
        val agedText = if (aged.batting < updated.batting || aged.bowling < updated.bowling) " Age is catching up with you: your ratings have dropped." else ""
        val decided = CareerHealth.retirementDecision(aged)
        val retiredText = if (decided.retired && !updated.retired) " " + CareerHealth.retirementText(decided) else ""
        // Money and the papers (part fifteen).
        val paid = CareerFinance.settle(decided, states)
        val withMedia = paid.career.copy(media = (decided.media.orEmpty() + CareerMedia.lines(decided, states)).takeLast(15))
        return Settlement(withMedia, (objectiveText + honourText + agedText + retiredText + paid.text).trim())
    }
}
