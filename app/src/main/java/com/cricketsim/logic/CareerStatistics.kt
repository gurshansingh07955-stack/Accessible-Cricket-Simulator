package com.cricketsim.logic

import java.util.Locale

/**
 * Everything a player has done in one kind of cricket (a tournament or a format), match by match (My Player, part nineteen). The fields marked "scorecard" can only be counted
 * from a match's scorecard; a match played without one (some simulated first-class matches) adds only what the tournament statistics hold (matches, innings, runs, balls, fours,
 * sixes, fifties, hundreds, wickets, five-wicket hauls, catches), so ducks, maidens, hat-tricks, run-outs and the rest can be a little low in long-form cricket.
 */
data class DetailedRecord(
    val matches: Int = 0,
    // --- batting
    val innings: Int = 0,
    val notOuts: Int = 0,
    val runs: Int = 0,
    val balls: Int = 0,
    val highScore: Int = 0,
    val highScoreNotOut: Boolean = false,
    val fifties: Int = 0,
    /** 100 or more, so a double hundred counts here too. */
    val hundreds: Int = 0,
    val doubleHundreds: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    /** Scorecard. Out for nought. */
    val ducks: Int = 0,
    /** Scorecard. Out for nought off his first ball. */
    val goldenDucks: Int = 0,
    // --- bowling
    val ballsBowled: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    val bestWickets: Int = 0,
    val bestRuns: Int = 0,
    val bestMatchWickets: Int = 0,
    val bestMatchRuns: Int = 0,
    /** Innings with 3 or more, 4 or more and 5 or more wickets; matches with 10 or more. */
    val threeWickets: Int = 0,
    val fourWickets: Int = 0,
    val fiveWickets: Int = 0,
    val tenWicketMatches: Int = 0,
    /** Scorecard. */
    val maidens: Int = 0,
    val wides: Int = 0,
    val noBalls: Int = 0,
    /** Scorecard. Three wickets in three balls in a row; four in a row is a double hat-trick, which counts as a hat-trick too. */
    val hatTricks: Int = 0,
    val doubleHatTricks: Int = 0,
    // --- fielding
    val catches: Int = 0,
    /** Scorecard. */
    val runOuts: Int = 0,
    val stumpings: Int = 0,
    val mostCatchesInMatch: Int = 0,
    // --- awards and results
    val playerOfMatch: Int = 0,
    val playerOfSeries: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val tied: Int = 0,
    val drawn: Int = 0,
    val noResult: Int = 0,
    // --- as captain
    val captainMatches: Int = 0,
    val captainWon: Int = 0,
    val captainLost: Int = 0,
    val captainTied: Int = 0,
    val captainDrawn: Int = 0,
    val captainNoResult: Int = 0,
    val captainTossesWon: Int = 0
) {
    operator fun plus(o: DetailedRecord): DetailedRecord {
        val higher = o.highScore > highScore || (o.highScore == highScore && o.highScoreNotOut && !highScoreNotOut)
        val betterInnings = o.bestWickets > bestWickets || (o.bestWickets == bestWickets && o.bestWickets > 0 && (bestWickets == 0 || o.bestRuns < bestRuns))
        val betterMatch = o.bestMatchWickets > bestMatchWickets || (o.bestMatchWickets == bestMatchWickets && o.bestMatchWickets > 0 && (bestMatchWickets == 0 || o.bestMatchRuns < bestMatchRuns))
        return DetailedRecord(
            matches + o.matches, innings + o.innings, notOuts + o.notOuts, runs + o.runs, balls + o.balls,
            if (higher) o.highScore else highScore, if (higher) o.highScoreNotOut else highScoreNotOut,
            fifties + o.fifties, hundreds + o.hundreds, doubleHundreds + o.doubleHundreds, fours + o.fours, sixes + o.sixes,
            ducks + o.ducks, goldenDucks + o.goldenDucks,
            ballsBowled + o.ballsBowled, runsConceded + o.runsConceded, wickets + o.wickets,
            if (betterInnings) o.bestWickets else bestWickets, if (betterInnings) o.bestRuns else bestRuns,
            if (betterMatch) o.bestMatchWickets else bestMatchWickets, if (betterMatch) o.bestMatchRuns else bestMatchRuns,
            threeWickets + o.threeWickets, fourWickets + o.fourWickets, fiveWickets + o.fiveWickets, tenWicketMatches + o.tenWicketMatches,
            maidens + o.maidens, wides + o.wides, noBalls + o.noBalls, hatTricks + o.hatTricks, doubleHatTricks + o.doubleHatTricks,
            catches + o.catches, runOuts + o.runOuts, stumpings + o.stumpings, maxOf(mostCatchesInMatch, o.mostCatchesInMatch),
            playerOfMatch + o.playerOfMatch, playerOfSeries + o.playerOfSeries,
            won + o.won, lost + o.lost, tied + o.tied, drawn + o.drawn, noResult + o.noResult,
            captainMatches + o.captainMatches, captainWon + o.captainWon, captainLost + o.captainLost, captainTied + o.captainTied,
            captainDrawn + o.captainDrawn, captainNoResult + o.captainNoResult, captainTossesWon + o.captainTossesWon
        )
    }
}

/**
 * My Player, part nineteen: the full career record. Each match a player plays adds to the record of its kind, kept under a key "Group|Tab": Club (Three-day, One-day, T20), State
 * (a tab for each tournament: Ranji Trophy, Vijay Hazare Trophy and so on), International (Test, ODI, T20) and League (a tab for each league: IPL, Big Bash and so on). The records
 * kept before this part (CareerProfile.records) are added under "Earlier matches" (Club, State, League) or to their format (Test, ODI, T20 internationals).
 */
object CareerStatistics {

    /** The kinds of cricket the Career record offers, in order. */
    val GROUPS = listOf(
        "All" to "All cricket",
        "Club" to "Club cricket",
        "State" to "State cricket",
        "International" to "International cricket",
        "League" to "Franchise leagues"
    )

    private const val EARLIER = "Earlier matches"

    // ------------------------------------------------------------ which record a match belongs to

    /** The key of the record a match of this competition is added to. */
    fun keyOf(career: CareerProfile, state: TournamentState): String = when {
        CareerInternational.isInternational(state) -> "International|" + when (CareerInternational.formatKeyOf(state)) {
            "test" -> "Test"
            "odi" -> "ODI"
            else -> "T20"
        }
        CareerLeagues.isLeague(state) -> "League|" + CareerLeagues.leagueNameOf(state)
        CareerSeason.isClub(state) -> "Club|" + when (CareerSeason.competitionOf(state).key) {
            "t20" -> "T20"
            "odi" -> "One-day"
            else -> "Three-day"
        }
        else -> "State|" + CareerSeason.competitionOf(state).label
    }

    // ------------------------------------------------------------ adding a match

    private fun bowlerCredited(name: String): Boolean = name in setOf("BOWLED", "LBW", "CAUGHT", "CAUGHT_AND_BOWLED", "STUMPED", "HIT_WICKET")

    private fun hatTricksOf(innings: InningsData, bowlerName: String): Pair<Int, Int> {
        val mine = innings.fallOfWickets
            .filter { it.dismissedBy == bowlerName && bowlerCredited(it.dismissalType.name) }
            .map { it.overs * 6 + it.balls }
            .sorted()
        var hat = 0
        var double = 0
        var run = 1
        for (i in 1..mine.size) {
            if (i < mine.size && mine[i] == mine[i - 1] + 1) {
                run++
            } else {
                if (mine.isNotEmpty() && run >= 3) hat++
                if (mine.isNotEmpty() && run >= 4) double++
                run = 1
            }
        }
        return hat to double
    }

    /**
     * Adds the match he has just played (fixture [fixtureId] of [state]) to his record. [before] and [after] are his tournament statistics around the match: if the match has a scorecard it is
     * read for the details, otherwise the difference of the two is used. Does nothing if he did not play.
     */
    fun record(career: CareerProfile, state: TournamentState, fixtureId: Int, before: PlayerStats?, after: PlayerStats?): CareerProfile {
        if (after == null || after.matches <= (before?.matches ?: 0)) return career
        val pid = CareerSeason.playerIdIn(state, career) ?: return career
        val fixture = state.fixtures.find { it.id == fixtureId } ?: return career
        val key = keyOf(career, state)
        val old = career.detail.orEmpty()
        val myTeamId = state.userTeamId
        val myName = state.teams.flatMap { it.players }.find { it.id == pid }?.name ?: career.fullName
        var r = old[key] ?: DetailedRecord()
        r = r.copy(matches = r.matches + 1)
        val card = fixture.scorecard
        val innings: List<InningsData> = when {
            card == null -> emptyList()
            card.test != null -> TestMatch.allInnings(card)
            else -> listOfNotNull(card.firstInningsData, card.currentInningsData)
        }
        if (innings.isEmpty()) {
            fun d(f: (PlayerStats) -> Int) = f(after) - (before?.let(f) ?: 0)
            val betterBest = after.bestWickets > r.bestWickets || (after.bestWickets == r.bestWickets && after.bestWickets > 0 && after.bestRuns < r.bestRuns)
            val newHigh = after.highScore > r.highScore || (after.highScore == r.highScore && after.highScoreNotOut && !r.highScoreNotOut)
            r = r.copy(
                innings = r.innings + d { it.innings }, notOuts = r.notOuts + d { it.notOuts }, runs = r.runs + d { it.runs }, balls = r.balls + d { it.balls },
                fours = r.fours + d { it.fours }, sixes = r.sixes + d { it.sixes },
                highScore = if (newHigh) after.highScore else r.highScore, highScoreNotOut = if (newHigh) after.highScoreNotOut else r.highScoreNotOut,
                fifties = r.fifties + d { it.fifties }, hundreds = r.hundreds + d { it.hundreds },
                ballsBowled = r.ballsBowled + d { it.ballsBowled }, runsConceded = r.runsConceded + d { it.runsConceded }, wickets = r.wickets + d { it.wickets },
                fiveWickets = r.fiveWickets + d { it.fiveWickets },
                bestWickets = if (betterBest) after.bestWickets else r.bestWickets, bestRuns = if (betterBest) after.bestRuns else r.bestRuns,
                catches = r.catches + d { it.catches }
            )
        } else {
            var matchWickets = 0
            var matchRuns = 0
            var bowled = false
            var matchCatches = 0
            val keeper = career.role.contains("Wicketkeeper")
            for (inn in innings) {
                val bat = inn.batsmanStats.find { it.playerId == pid }
                if (bat != null) {
                    val notOut = !bat.isOut
                    val newHigh = bat.runs > r.highScore || (bat.runs == r.highScore && notOut && !r.highScoreNotOut)
                    val duck = bat.isOut && bat.runs == 0
                    r = r.copy(
                        innings = r.innings + 1, notOuts = r.notOuts + (if (notOut) 1 else 0), runs = r.runs + bat.runs, balls = r.balls + bat.ballsFaced,
                        fours = r.fours + bat.fours, sixes = r.sixes + bat.sixes,
                        highScore = if (newHigh) bat.runs else r.highScore, highScoreNotOut = if (newHigh) notOut else r.highScoreNotOut,
                        fifties = r.fifties + (if (bat.runs in 50..99) 1 else 0), hundreds = r.hundreds + (if (bat.runs >= 100) 1 else 0),
                        doubleHundreds = r.doubleHundreds + (if (bat.runs >= 200) 1 else 0),
                        ducks = r.ducks + (if (duck) 1 else 0), goldenDucks = r.goldenDucks + (if (duck && bat.ballsFaced == 1) 1 else 0)
                    )
                }
                val bowl = inn.bowlerStats.find { it.playerId == pid }
                if (bowl != null) {
                    bowled = true
                    matchWickets += bowl.wickets
                    matchRuns += bowl.runsConceded
                    val (hat, double) = hatTricksOf(inn, myName)
                    val better = bowl.wickets > r.bestWickets || (bowl.wickets == r.bestWickets && bowl.wickets > 0 && bowl.runsConceded < r.bestRuns)
                    r = r.copy(
                        ballsBowled = r.ballsBowled + bowl.overs * 6 + bowl.balls, runsConceded = r.runsConceded + bowl.runsConceded, wickets = r.wickets + bowl.wickets,
                        bestWickets = if (better) bowl.wickets else r.bestWickets, bestRuns = if (better) bowl.runsConceded else r.bestRuns,
                        threeWickets = r.threeWickets + (if (bowl.wickets >= 3) 1 else 0), fourWickets = r.fourWickets + (if (bowl.wickets >= 4) 1 else 0),
                        fiveWickets = r.fiveWickets + (if (bowl.wickets >= 5) 1 else 0),
                        maidens = r.maidens + bowl.maidens, wides = r.wides + bowl.wides, noBalls = r.noBalls + bowl.noBalls,
                        hatTricks = r.hatTricks + hat, doubleHatTricks = r.doubleHatTricks + double
                    )
                }
                if (inn.bowlingTeamId == myTeamId) {
                    for (b in inn.batsmanStats) {
                        if (!b.isOut) continue
                        val type = b.dismissalType?.name
                        val caught = (type == "CAUGHT" && b.caughtBy == myName) || (type == "CAUGHT_AND_BOWLED" && b.dismissedBy == myName)
                        if (caught) {
                            matchCatches++
                            r = r.copy(catches = r.catches + 1)
                        }
                        if (type == "RUN_OUT" && b.dismissedBy == myName) r = r.copy(runOuts = r.runOuts + 1)
                        if (type == "STUMPED" && keeper) r = r.copy(stumpings = r.stumpings + 1)
                    }
                }
            }
            if (bowled) {
                val betterMatch = matchWickets > r.bestMatchWickets || (matchWickets == r.bestMatchWickets && matchWickets > 0 && matchRuns < r.bestMatchRuns)
                r = r.copy(
                    bestMatchWickets = if (betterMatch) matchWickets else r.bestMatchWickets, bestMatchRuns = if (betterMatch) matchRuns else r.bestMatchRuns,
                    tenWicketMatches = r.tenWicketMatches + (if (matchWickets >= 10) 1 else 0)
                )
            }
            r = r.copy(mostCatchesInMatch = maxOf(r.mostCatchesInMatch, matchCatches))
        }
        // Awards, the result of the team, and the captaincy.
        val result = fixture.result
        if (result != null) {
            if (result.playerOfMatchId == pid) r = r.copy(playerOfMatch = r.playerOfMatch + 1)
            val captain = CareerSeason.captainIn(career, state)
            val kind = when {
                result.fcKind == "DRAW" -> "D"
                result.fcKind == "TIE" -> "T"
                result.winnerId == null -> "N"
                result.winnerId == myTeamId -> "W"
                else -> "L"
            }
            r = when (kind) {
                "W" -> r.copy(won = r.won + 1)
                "L" -> r.copy(lost = r.lost + 1)
                "T" -> r.copy(tied = r.tied + 1)
                "D" -> r.copy(drawn = r.drawn + 1)
                else -> r.copy(noResult = r.noResult + 1)
            }
            if (captain) {
                r = r.copy(captainMatches = r.captainMatches + 1)
                r = when (kind) {
                    "W" -> r.copy(captainWon = r.captainWon + 1)
                    "L" -> r.copy(captainLost = r.captainLost + 1)
                    "T" -> r.copy(captainTied = r.captainTied + 1)
                    "D" -> r.copy(captainDrawn = r.captainDrawn + 1)
                    else -> r.copy(captainNoResult = r.captainNoResult + 1)
                }
                if (card != null && card.tossWinnerId == myTeamId) r = r.copy(captainTossesWon = r.captainTossesWon + 1)
            }
        }
        return career.copy(detail = old + (key to r))
    }

    private fun isPlayerOfSeries(state: TournamentState, pid: String): Boolean {
        val best = state.playerStats.values.maxWithOrNull(compareBy<PlayerStats>({ it.impact }, { it.runs + it.wickets * 20 })) ?: return false
        return best.playerId == pid && best.impact > 0.0
    }

    /** The career with a Player of the Series award added to the record of each finished competition in which he had the most impact. */
    fun withSeriesAwards(career: CareerProfile, states: List<TournamentState>): CareerProfile {
        var map = career.detail.orEmpty()
        for (s in states) {
            if (!CareerSeason.isComplete(s)) continue
            val pid = CareerSeason.playerIdIn(s, career) ?: continue
            val st = s.playerStats[pid] ?: continue
            if (st.matches == 0 || !isPlayerOfSeries(s, pid)) continue
            val key = keyOf(career, s)
            val r = map[key] ?: DetailedRecord()
            map = map + (key to r.copy(playerOfSeries = r.playerOfSeries + 1))
        }
        return career.copy(detail = map)
    }

    // ------------------------------------------------------------ reading the record

    private fun legacy(r: CareerRecord): DetailedRecord = DetailedRecord(
        matches = r.matches, innings = r.innings, notOuts = r.notOuts, runs = r.runs, balls = r.balls, highScore = r.highScore,
        fifties = r.fifties, hundreds = r.hundreds, ballsBowled = r.ballsBowled, runsConceded = r.runsConceded, wickets = r.wickets,
        bestWickets = r.bestWickets, bestRuns = r.bestRuns, fiveWickets = r.fiveWickets, catches = r.catches
    )

    /** Every record, under "Group|Tab" keys, with the earlier (undetailed) records added. */
    fun entries(career: CareerProfile): Map<String, DetailedRecord> {
        val out = LinkedHashMap<String, DetailedRecord>()
        out.putAll(career.detail.orEmpty())
        for ((old, rec) in career.records.orEmpty()) {
            if (rec.matches == 0) continue
            val key = when (old) {
                "Test" -> "International|Test"
                "ODI" -> "International|ODI"
                "T20I" -> "International|T20"
                // The records kept before the full record began have no tournament detail and no tab: only the international formats (which have one) are kept.
                else -> continue
            }
            out[key] = (out[key] ?: DetailedRecord()) + legacy(rec)
        }
        return out
    }

    fun total(career: CareerProfile): DetailedRecord = entries(career).values.fold(DetailedRecord()) { a, b -> a + b }

    /** The record of one kind of cricket: group "All" is everything; tab "All" is the whole group. */
    fun view(career: CareerProfile, group: String, tab: String): DetailedRecord {
        if (group == "All") return total(career)
        val all = entries(career)
        if (tab == "All") return all.filterKeys { it.startsWith("$group|") }.values.fold(DetailedRecord()) { a, b -> a + b }
        return all["$group|$tab"] ?: DetailedRecord()
    }

    /** The tabs of a group: all of them, then the formats (Club, International) or the tournaments with matches (State, League). */
    fun tabsOf(career: CareerProfile, group: String): List<String> {
        val present = entries(career).keys.filter { it.startsWith("$group|") }.map { it.substringAfter('|') }
        return when (group) {
            "All" -> listOf("All")
            "Club" -> listOf("All", "Three-day", "One-day", "T20")
            "International" -> listOf("All", "Test", "ODI", "T20")
            else -> listOf("All") + present.sorted()
        }
    }

    /** One sentence for the menu. */
    fun headline(career: CareerProfile): String {
        val t = total(career)
        return if (t.matches == 0) "No matches yet." else "${t.matches} matches, ${t.runs} runs, ${t.wickets} wickets, ${t.catches} catches."
    }

    /** A few sentences for the career summary. */
    fun summaryLines(career: CareerProfile): List<String> {
        val out = ArrayList<String>()
        for ((group, title) in GROUPS.drop(1)) {
            val r = view(career, group, "All")
            if (r.matches > 0) out.add("$title: ${r.matches} matches, ${r.runs} runs${if (r.hundreds > 0) ", ${r.hundreds} hundreds" else ""}, ${r.wickets} wickets, ${r.catches} catches.")
        }
        val t = total(career)
        if (t.matches > 0) out.add("In all: ${t.matches} matches, ${t.runs} runs, ${t.wickets} wickets, ${t.catches} catches.")
        return out
    }

    // ------------------------------------------------------------ words for a record

    private fun one(v: Double): String = String.format(Locale.US, "%.1f", v)
    private fun two(v: Double): String = String.format(Locale.US, "%.2f", v)
    private fun overs(balls: Int): String = "${balls / 6}.${balls % 6}"

    /** The record as sections of "Label: value" lines. */
    fun sections(r: DetailedRecord): List<Pair<String, List<String>>> {
        val outs = r.innings - r.notOuts
        val batting = listOf(
            "Matches: ${r.matches}",
            "Innings: ${r.innings}",
            "Not outs: ${r.notOuts}",
            "Runs: ${r.runs}",
            "Balls faced: ${r.balls}",
            "Highest score: ${if (r.innings == 0) "-" else r.highScore.toString() + (if (r.highScoreNotOut) "*" else "")}",
            "Batting average: ${if (outs > 0) two(r.runs.toDouble() / outs) else "-"}",
            "Strike rate: ${if (r.balls > 0) two(r.runs * 100.0 / r.balls) else "-"}",
            "Fifties: ${r.fifties}",
            "Hundreds: ${r.hundreds}",
            "Double hundreds: ${r.doubleHundreds}",
            "Fours: ${r.fours}",
            "Sixes: ${r.sixes}",
            "Runs from boundaries: ${r.fours * 4 + r.sixes * 6}",
            "Ducks: ${r.ducks}",
            "Golden ducks: ${r.goldenDucks}"
        )
        val bowling = listOf(
            "Overs bowled: ${overs(r.ballsBowled)}",
            "Maidens: ${r.maidens}",
            "Runs conceded: ${r.runsConceded}",
            "Wickets: ${r.wickets}",
            "Bowling average: ${if (r.wickets > 0) two(r.runsConceded.toDouble() / r.wickets) else "-"}",
            "Economy: ${if (r.ballsBowled > 0) two(r.runsConceded * 6.0 / r.ballsBowled) else "-"}",
            "Bowling strike rate: ${if (r.wickets > 0) one(r.ballsBowled.toDouble() / r.wickets) else "-"}",
            "Best bowling in an innings: ${if (r.bestWickets > 0) "${r.bestWickets} for ${r.bestRuns}" else "-"}",
            "Best bowling in a match: ${if (r.bestMatchWickets > 0) "${r.bestMatchWickets} for ${r.bestMatchRuns}" else "-"}",
            "Three wickets or more in an innings: ${r.threeWickets}",
            "Four wickets or more in an innings: ${r.fourWickets}",
            "Five wickets or more in an innings: ${r.fiveWickets}",
            "Ten wickets or more in a match: ${r.tenWicketMatches}",
            "Hat-tricks: ${r.hatTricks}",
            "Double hat-tricks: ${r.doubleHatTricks}",
            "Wides: ${r.wides}",
            "No-balls: ${r.noBalls}"
        )
        val fielding = listOf(
            "Catches: ${r.catches}",
            "Run-outs: ${r.runOuts}",
            "Stumpings: ${r.stumpings}",
            "Dismissals in the field: ${r.catches + r.runOuts + r.stumpings}",
            "Most catches in a match: ${r.mostCatchesInMatch}"
        )
        val awards = listOf("Player of the Match: ${r.playerOfMatch}", "Player of the Series: ${r.playerOfSeries}")
        val played = r.won + r.lost + r.tied + r.drawn + r.noResult
        val results = listOf(
            "Won: ${r.won}", "Lost: ${r.lost}", "Tied: ${r.tied}", "Drawn: ${r.drawn}", "No result: ${r.noResult}",
            "Win percentage: ${if (played > 0) one(r.won * 100.0 / played) + "%" else "-"}"
        )
        val captain = listOf(
            "Matches as captain: ${r.captainMatches}",
            "Won: ${r.captainWon}", "Lost: ${r.captainLost}", "Tied: ${r.captainTied}", "Drawn: ${r.captainDrawn}", "No result: ${r.captainNoResult}",
            "Win percentage as captain: ${if (r.captainMatches > 0) one(r.captainWon * 100.0 / r.captainMatches) + "%" else "-"}",
            "Tosses won as captain: ${r.captainTossesWon}"
        )
        return listOf(
            "Batting" to batting, "Bowling" to bowling, "Fielding" to fielding, "Awards" to awards,
            "Results for your team" to results, "Captaincy" to captain
        )
    }
}
