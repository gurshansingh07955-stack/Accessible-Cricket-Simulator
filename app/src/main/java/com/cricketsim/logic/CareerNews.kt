package com.cricketsim.logic

import kotlin.math.exp
import kotlin.random.Random

/** One line of the career's news: mine = about the player or his team; false = what is happening in world cricket. */
data class NewsItem(val text: String, val mine: Boolean = false)

/**
 * My Player, part twenty-four, first part: the news after every match, and world cricket in it.
 *
 *  - After a match of the player's (played or simulated): the result, what he did (a hundred, a fifty, wickets, Player of the Match) and, in a final, the title.
 *  - After any step of the season: the real results of the other matches that were just played in his competitions (finals first), and a made-up
 *    headline or two about world cricket (an international result, a star's performance, a squad or a series). The made-up ones are the game's own world:
 *    they are not real results. Switch them off with [GENERATED_WORLD_NEWS].
 *  - The Career tab > In the news has two tabs: All, and My news.
 *
 * [NEWS_AFTER_EVERY_MATCH] false brings back the old way (the papers write only when a season ends).
 */
object CareerNews {
    const val NEWS_AFTER_EVERY_MATCH = true
    const val GENERATED_WORLD_NEWS = true
    private const val KEEP = 80

    fun add(career: CareerProfile, items: List<NewsItem>): CareerProfile =
        if (items.isEmpty()) career else career.copy(newsFeed = (career.newsFeed.orEmpty() + items).takeLast(KEEP))

    /** Everything, oldest first. The season-end lines saved before the feed existed come first. */
    fun all(career: CareerProfile): List<NewsItem> {
        val feed = career.newsFeed.orEmpty()
        val texts = feed.map { it.text }.toSet()
        val old = career.media.orEmpty().filter { it !in texts }.map { NewsItem(it, true) }
        return old + feed
    }

    private fun pick(random: Random, vararg options: String): String = options[random.nextInt(options.size)]

    private fun wicketsText(n: Int): String = if (n == 1) "a wicket" else "$n wickets"

    /** The news about the player's own match: the result, what he did, the title. */
    fun afterMyMatch(career: CareerProfile, state: TournamentState, fixtureId: Int, before: PlayerStats?, after: PlayerStats?, random: Random): CareerProfile {
        if (!NEWS_AFTER_EVERY_MATCH) return career
        val fixture = state.fixtures.find { it.id == fixtureId } ?: return career
        val result = fixture.result ?: return career
        val label = CareerSeason.competitionOf(state).label
        val stage = if (fixture.stage != CplData.LEAGUE) ", ${fixture.stage}" else ""
        val out = ArrayList<NewsItem>()
        out.add(NewsItem("$label$stage: ${CareerSeason.readable(state, result.summary)}", true))
        if (fixture.stage == "Final" && result.winnerId == state.userTeamId) {
            out.add(NewsItem("$label: ${CareerSeason.nameOf(state, state.userTeamId)} win the final and the title.", true))
        }
        val pid = CareerSeason.playerIdIn(state, career)
        if (pid != null && after != null && after.matches > (before?.matches ?: 0)) {
            val runs = after.runs - (before?.runs ?: 0)
            val balls = after.balls - (before?.balls ?: 0)
            val wkts = after.wickets - (before?.wickets ?: 0)
            val conceded = after.runsConceded - (before?.runsConceded ?: 0)
            val ballsText = if (balls > 0) " from $balls balls" else ""
            val line: String? = when {
                after.hundreds > (before?.hundreds ?: 0) -> pick(
                    random,
                    "You make a hundred: $runs runs$ballsText. The papers love it.",
                    "A hundred from you: $runs runs$ballsText, and the match is yours."
                )
                after.fiveWickets > (before?.fiveWickets ?: 0) ->
                    "You take ${wicketsText(wkts)} for $conceded runs: a five-wicket haul that makes the headlines."
                after.fifties > (before?.fifties ?: 0) -> pick(
                    random,
                    "A fine fifty from you: $runs runs$ballsText.",
                    "You score $runs runs$ballsText: a half-century the coach praises."
                )
                wkts >= 3 -> "You take ${wicketsText(wkts)} for $conceded runs."
                runs >= 30 -> "You score $runs runs$ballsText."
                wkts > 0 -> "You take ${wicketsText(wkts)} for $conceded runs."
                else -> null
            }
            if (line != null) out.add(NewsItem(line, true))
            if (result.playerOfMatchId == pid) out.add(NewsItem("You are named Player of the Match.", true))
        }
        return add(career, out)
    }

    /**
     * The news about everybody else after a step of the season: the real results (of the matches that went from unplayed to played between [before] and
     * [after], not the player's own; knockout matches first, at most two) and, if switched on, made-up world cricket headlines.
     */
    fun afterMatches(career: CareerProfile, before: List<TournamentState>, after: List<TournamentState>, random: Random): CareerProfile {
        if (!NEWS_AFTER_EVERY_MATCH) return career
        val fresh = ArrayList<Pair<TournamentState, TournamentFixture>>()
        after.forEachIndexed { i, s ->
            val old = before.getOrNull(i) ?: return@forEachIndexed
            val done = old.fixtures.filter { it.result != null }.map { it.id }.toSet()
            s.fixtures.forEach { f ->
                if (f.result != null && f.id !in done && !CplSimulator.involves(f, s.userTeamId)) fresh.add(Pair(s, f))
            }
        }
        val chosen = (fresh.filter { it.second.stage != CplData.LEAGUE }.shuffled(random) + fresh.filter { it.second.stage == CplData.LEAGUE }.shuffled(random)).take(2)
        val out = ArrayList<NewsItem>()
        chosen.forEach { (s, f) ->
            val r = f.result ?: return@forEach
            val stage = if (f.stage != CplData.LEAGUE) ", ${f.stage}" else ""
            val pom = r.playerOfMatch?.let { " Player of the Match: ${CareerSeason.readable(s, it)}." } ?: ""
            out.add(NewsItem("${CareerSeason.competitionOf(s).label}$stage: ${CareerSeason.readable(s, r.summary)}$pom", false))
        }
        if (GENERATED_WORLD_NEWS) {
            val n = if (random.nextInt(3) == 0) 2 else 1
            repeat(n) { generated(random)?.let { out.add(NewsItem(it, false)) } }
        }
        return add(career, out)
    }

    // ---------------------------------------------------------------- made-up world cricket

    private fun generated(random: Random): String? {
        val teams = CricketData.getAllTeams()
        if (teams.size < 2) return null
        val roll = random.nextInt(100)
        return when {
            roll < 45 -> internationalResult(teams, random)
            roll < 80 -> starPerformance(teams, random)
            else -> otherNews(teams, random)
        }
    }

    private fun twoTeams(teams: List<Team>, random: Random): Pair<Team, Team> {
        val a = teams[random.nextInt(teams.size)]
        var b = teams[random.nextInt(teams.size)]
        var guard = 0
        while (b.id == a.id && guard < 20) {
            b = teams[random.nextInt(teams.size)]
            guard++
        }
        return Pair(a, b)
    }

    private fun internationalResult(teams: List<Team>, random: Random): String {
        val (a, b) = twoTeams(teams, random)
        val sa = (a.battingRating + a.bowlingRating) / 2.0
        val sb = (b.battingRating + b.bowlingRating) / 2.0
        val aWins = random.nextDouble() < 1.0 / (1.0 + exp(-(sa - sb) / 10.0))
        val winner = if (aWins) a else b
        val loser = if (aWins) b else a
        val first = 135 + random.nextInt(65)
        return if (random.nextBoolean()) {
            // The winner bats first and defends the total.
            val lost = maxOf(70, first - 1 - random.nextInt(45))
            "World cricket, T20 international: ${winner.name} $first/${3 + random.nextInt(6)}, ${loser.name} $lost/${5 + random.nextInt(6)}. ${winner.name} won by ${first - lost} runs."
        } else {
            // The winner chases.
            val chase = first + 1 + random.nextInt(6)
            val lostWickets = random.nextInt(8)
            "World cricket, T20 international: ${loser.name} $first/${4 + random.nextInt(6)}, ${winner.name} $chase/$lostWickets. ${winner.name} won by ${10 - lostWickets} wickets."
        }
    }

    private fun starPerformance(teams: List<Team>, random: Random): String {
        val team = teams[random.nextInt(teams.size)]
        val others = teams.filter { it.id != team.id }
        val opp = others[random.nextInt(others.size)]
        val batter = team.players.filter { it.battingRating >= 80 }.randomOrNull(random)
        val bowler = team.players.filter { it.bowlingRating >= 80 }.randomOrNull(random)
        return when {
            batter != null && (bowler == null || random.nextBoolean()) -> {
                val runs = 60 + random.nextInt(85)
                val balls = (runs * (60 + random.nextInt(45)) / 100).coerceAtLeast(20)
                "World cricket: ${batter.name} scores $runs off $balls balls for ${team.name} against ${opp.name}."
            }
            bowler != null -> {
                val w = 3 + random.nextInt(4)
                val conceded = w * (5 + random.nextInt(8)) + random.nextInt(8)
                "World cricket: ${bowler.name} takes $w wickets for $conceded runs for ${team.name} against ${opp.name}."
            }
            else -> otherNews(teams, random)
        }
    }

    private fun otherNews(teams: List<Team>, random: Random): String {
        val (a, b) = twoTeams(teams, random)
        return when (random.nextInt(4)) {
            0 -> "World cricket: ${a.name} win the series against ${b.name} ${pick(random, "2-1", "3-0", "2-0", "3-2")}."
            1 -> "World cricket: ${a.name} name their squad for the next series."
            2 -> {
                val star = a.players.maxByOrNull { maxOf(it.battingRating, it.bowlingRating) }
                if (star != null) "World cricket: an injury scare for ${star.name} ahead of ${a.name}'s next match."
                else "World cricket: ${a.name} wait on the fitness of their squad."
            }
            else -> "World cricket: the ${a.name} coach says the side is ready for the next big tournament."
        }
    }
}
