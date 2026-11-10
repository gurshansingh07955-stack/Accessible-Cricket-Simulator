package com.cricketsim.logic

import java.util.Locale
import kotlin.math.roundToInt

/**
 * My Player, part fifteen: money. A player is paid in match fees (club, state and national matches; franchise league matches are covered by the league contract),
 * in a national central contract once he is an international, in a league contract when he signs for a franchise, and in prizes when his team wins a competition.
 * Match fees are paid as he plays ([matchFee]); the contracts and the prizes when the season is settled ([settle]). The figures are in dollars, invented for the game.
 *
 * A league contract ([contractFor]) is the league's base (IPL 400,000 ... Abu Dhabi T10 50,000) times 0.4 plus his main rating over 100, 10% more if the franchise keeps him from the
 * year before ([CareerProfile.lastLeagueTeamId]), 25% more for an international. Dials: [matchFee], [BASE], the central contract in [centralContract].
 */
object CareerFinance {

    class Payout(val career: CareerProfile, val text: String)

    fun money(amount: Long): String = "\$" + String.format(Locale.US, "%,d", amount)

    /** What he is paid for a match: Test 4,000, one-day 2,500, T20 international 1,500 (also the T20 World Cup and so on), state 120, club 25; none for a franchise league match. */
    fun matchFee(career: CareerProfile, format: String, league: Boolean): Int = when {
        league -> 0
        format == "test" -> 4000
        format == "odi" -> 2500
        format == "t20" -> 1500
        career.isState -> 120
        else -> 25
    }

    private val BASE = mapOf(
        "ipl" to 400_000, "bbl" to 150_000, "sa20" to 120_000, "cpl" to 100_000,
        "mlc" to 100_000, "ilt20" to 90_000, "lpl" to 60_000, "adt10" to 50_000
    )

    fun contractFor(career: CareerProfile, leagueId: String, retained: Boolean): Int {
        val main = when (CareerSeason.roleOf(career.role)) {
            PlayerRole.BOWLER -> career.bowling.toDouble()
            PlayerRole.ALL_ROUNDER -> (career.batting + career.bowling) / 2.0
            else -> career.batting.toDouble()
        }
        val raw = (BASE[leagueId] ?: 50_000) * (0.4 + main / 100.0) * (if (retained) 1.1 else 1.0) * (if (career.isInternational) 1.25 else 1.0)
        return (raw / 1000.0).roundToInt() * 1000
    }

    fun contract(career: CareerProfile, offer: LeagueOffer): Int = contractFor(career, offer.leagueId, career.lastLeagueTeamId == offer.teamId)

    /** The national central contract for the season: 60,000, 20,000 more for a vice-captain, 40,000 more for a captain. */
    fun centralContract(career: CareerProfile): Long =
        if (!career.isInternational) 0L else 60_000L + when (career.nationalRole) {
            "Captain" -> 40_000L
            "Vice-captain" -> 20_000L
            else -> 0L
        }

    /** At the end of a season: the contracts and the prizes are paid. */
    fun settle(career: CareerProfile, states: List<TournamentState>): Payout {
        var contracts = centralContract(career)
        if (career.leagueContract > 0 && career.leagueDecidedSeason == career.season) contracts += career.leagueContract.toLong()
        var prizes = 0L
        for (s in states) {
            if (!CareerSeason.isComplete(s)) continue
            val champion = CareerSeason.champion(s) ?: continue
            if (champion.id != s.userTeamId) continue
            prizes += when {
                CareerInternational.isTournament(s) || CareerLeagues.isLeague(s) -> 20_000L
                CareerInternational.isSeries(s) -> 5_000L
                else -> 500L
            }
        }
        val fees = career.seasonEarnings
        val total = fees + contracts + prizes
        val updated = career.copy(money = career.money + contracts + prizes, seasonEarnings = total)
        val text = if (total <= 0L) "" else " This season you earned ${money(total)}: ${money(fees)} in match fees, ${money(contracts)} in contracts and ${money(prizes)} in prizes."
        return Payout(updated, text)
    }

    /** The money part of the Career tab. */
    fun lines(career: CareerProfile): List<String> {
        val out = ArrayList<String>()
        out.add("Career earnings: ${money(career.money)}.")
        if (!career.retired) {
            out.add("This season so far, in match fees: ${money(career.seasonEarnings)}.")
            if (career.leagueContract > 0 && career.leagueDecidedSeason == career.season) {
                out.add("Your league contract this season is worth ${money(career.leagueContract.toLong())}, paid when the season ends.")
            }
            if (career.isInternational) out.add("Your national central contract pays ${money(centralContract(career))} when the season ends.")
        }
        return out
    }
}

/**
 * My Player, part fifteen: what the papers say. When a season is settled each finished competition may earn a line (the star of the competition, a hundred,
 * a five-wicket haul, 200 runs, eight wickets, or a poor run over three or more matches). The last fifteen lines are kept for the Career tab.
 */
object CareerMedia {

    private fun starOf(state: TournamentState, pid: String): Boolean {
        val best = state.playerStats.values.maxWithOrNull(compareBy<PlayerStats>({ it.impact }, { it.runs + it.wickets * 20 })) ?: return false
        return best.playerId == pid && best.impact > 0.0
    }

    fun lines(career: CareerProfile, states: List<TournamentState>): List<String> {
        val out = ArrayList<String>()
        for (s in states) {
            if (!CareerSeason.isComplete(s)) continue
            val pid = CareerSeason.playerIdIn(s, career) ?: continue
            val st = s.playerStats[pid] ?: continue
            if (st.matches == 0) continue
            val tag = "Season ${career.season}, ${CareerSeason.competitionOf(s).label}: "
            val averageImpact = st.impact / st.matches
            val line = when {
                starOf(s, pid) -> "the papers name you the star of the competition."
                st.hundreds > 0 -> "a hundred makes the headlines: your best was ${st.highScore}."
                st.fiveWickets > 0 -> "a five-wicket haul has the pundits talking."
                st.runs >= 200 -> "commentators praise your ${st.runs} runs."
                st.wickets >= 8 -> "you are the talk of the dressing room with ${st.wickets} wickets."
                st.matches >= 3 && averageImpact < 8.0 -> "the press ask whether you are good enough: little to show in ${st.matches} matches."
                else -> null
            }
            if (line != null) out.add(tag + line)
        }
        return out.take(4)
    }
}
