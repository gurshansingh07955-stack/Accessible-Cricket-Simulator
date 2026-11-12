package com.cricketsim.logic

import kotlin.math.roundToInt
import kotlin.random.Random

/** A franchise's offer to the player for a season (kept in the career file while it is on the table). */
data class LeagueOffer(
    val leagueId: String,
    val leagueName: String,
    val teamId: String,
    val teamName: String,
    /** True if the league is not in his own country: he takes an overseas place in the squad and in the XI. */
    val overseas: Boolean,
    /** The IPL's auction is rebuilt from this seed when the offer is accepted (the same squads come out). */
    val seed: Long,
    val note: String,
    /** True if the franchise also offers him its captaincy. */
    val captain: Boolean = false
)

/**
 * League selection (My Player, part ten): the franchise T20 leagues of the game, for a player at the state or the international level.
 *
 * HOW THE OFFERS ARE MADE ([offersFor]). Every franchise of every league open to him is looked at: where would he rank among its players of his role
 * (his ratings against theirs)? A franchise offers if he would be in its T20 XI: the top three of a role (one wicketkeeper), widened or narrowed by the
 * career difficulty, a place fewer if he would be an overseas player, a place more or fewer for his T20 form, a place more if he is an international.
 * The leagues are tried in order of prestige (IPL, Big Bash, SA20, CPL, MLC, LPL, Abu Dhabi T10); at most two offers from a league and five in all.
 * Rules taken from the real game: an Indian player may only play the IPL (the BCCI keeps active players out of other leagues), a Pakistani player is
 * not offered the IPL. The IPL's squads come from a computer-only auction run when the offers are made (the same seed rebuilds it when he accepts).
 *
 * HOW IT PLAYS. Accepting adds that league's season to the career season ([buildSeason]): the real teams and fixtures of the game's league, the player
 * written into the franchise squad in place of its weakest player of his role. The league is dated after the rest of the season, so a player who is
 * also an international plays his series and his tournament first. One league a season. The ILT20 is not offered (its XI quotas of UAE and Afghan players
 * cannot be honoured when the selectors name the player's XI).
 *
 * Not built: the Impact Player and the Power Surge in these matches (the game's tournaments have them, the career hub does not pass them), the real
 * overseas cap being enforced when the selectors name the XI (it is respected in the offers and in his ranking, not checked in the XI).
 */
object CareerLeagues {

    /** Matches (state, international or league) the franchises want to have seen before they make an offer. */
    const val MIN_MATCHES = 5
    private const val MAX_OFFERS = 5
    private const val MAX_PER_LEAGUE = 2

    private class Built(val pair: Pair<List<Team>, Map<String, SquadTag>>, val auction: IplAuctionState? = null)

    private class League(
        val id: String,
        val name: String,
        /** The country the league is played in, as the career's country is spelled ("India", "Australia"). */
        val country: String,
        val prestige: Int,
        val build: (Long) -> Built,
        val make: (String, Built, Random) -> TournamentState
    )

    private class Candidate(val prestige: Int, val margin: Int, val offer: LeagueOffer)

    private val LEAGUES: List<League> by lazy {
        listOf(
            League("ipl", "Indian Premier League", "India", 7,
                { seed ->
                    val auction = IplAuction.runAll(IplAuction.newAuction("career_none", IplData.buildPool(), seed))
                    Built(IplSeason.buildTeams(auction), auction)
                },
                { teamId, built, random -> IplSeason.newTournament((built.auction ?: error("no auction")).copy(userTeamId = teamId), random) }),
            League("bbl", "Big Bash League", "Australia", 6,
                { Built(BigBashLeague.buildTeams()) },
                { teamId, built, _ -> BigBashLeague.newTournament(teamId, built.pair) }),
            League("sa20", "SA20", "South Africa", 5,
                { Built(Sa20.buildTeams()) },
                { teamId, built, _ -> Sa20.newTournament(teamId, built.pair) }),
            League("cpl", "Caribbean Premier League", "West Indies", 4,
                { Built(CplData.buildTeams()) },
                { teamId, built, random -> CplData.newTournament(teamId, built.pair, random) }),
            League("ilt20", "International League T20", "UAE", 3,
                { Built(InternationalLeagueT20.buildTeams()) },
                { teamId, built, _ -> InternationalLeagueT20.newTournament(teamId, built.pair) }),
            League("mlc", "Major League Cricket", "USA", 3,
                { Built(MajorLeagueCricket.buildTeams()) },
                { teamId, built, _ -> MajorLeagueCricket.newTournament(teamId, built.pair) }),
            League("lpl", "Lanka Premier League", "Sri Lanka", 2,
                { Built(LankaPremierLeague.buildTeams()) },
                { teamId, built, random -> LankaPremierLeague.newTournament(teamId, built.pair, random) }),
            League("adt10", "Abu Dhabi T10", "UAE", 1,
                { Built(AbuDhabiT10.buildTeams()) },
                { teamId, built, random -> AbuDhabiT10.newTournament(teamId, built.pair, random) })
        )
    }

    private val LEAGUE_IDS = setOf(
        IplSeason.TOURNAMENT_ID, BigBashLeague.TOURNAMENT_ID, Sa20.TOURNAMENT_ID, CplData.TOURNAMENT_ID,
        MajorLeagueCricket.TOURNAMENT_ID, LankaPremierLeague.TOURNAMENT_ID, AbuDhabiT10.TOURNAMENT_ID, InternationalLeagueT20.TOURNAMENT_ID
    )

    private val LEAGUE_NAMES = mapOf(
        IplSeason.TOURNAMENT_ID to "Indian Premier League", BigBashLeague.TOURNAMENT_ID to "Big Bash League", Sa20.TOURNAMENT_ID to "SA20",
        CplData.TOURNAMENT_ID to "Caribbean Premier League", MajorLeagueCricket.TOURNAMENT_ID to "Major League Cricket",
        LankaPremierLeague.TOURNAMENT_ID to "Lanka Premier League", AbuDhabiT10.TOURNAMENT_ID to "Abu Dhabi T10",
        InternationalLeagueT20.TOURNAMENT_ID to "International League T20"
    )

    /** The name of a league (without the season) for the career record's tabs. */
    fun leagueNameOf(state: TournamentState): String = LEAGUE_NAMES[state.tournamentId] ?: state.name

    /** True for the season of a franchise league a career is playing in. */
    fun isLeague(state: TournamentState): Boolean = state.tournamentId in LEAGUE_IDS

    /** A league as a competition of the career hub (its name is the competition's name). */
    fun competition(state: TournamentState): CareerSeason.Competition =
        CareerSeason.Competition(state.tournamentId, state.name, "League", 1)

    // ------------------------------------------------------------ small helpers

    private fun value(p: Player): Double = when (p.role) {
        PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER -> p.battingRating * 0.8 + p.fieldingRating * 0.2
        PlayerRole.BOWLER -> p.bowlingRating * 0.8 + p.fieldingRating * 0.2
        PlayerRole.ALL_ROUNDER -> p.battingRating * 0.45 + p.bowlingRating * 0.45 + p.fieldingRating * 0.1
    }

    private fun myValue(career: CareerProfile, role: PlayerRole): Double = when (role) {
        PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER -> career.batting * 0.8 + career.fielding * 0.2
        PlayerRole.BOWLER -> career.bowling * 0.8 + career.fielding * 0.2
        PlayerRole.ALL_ROUNDER -> career.batting * 0.45 + career.bowling * 0.45 + career.fielding * 0.1
    }

    private fun roleWord(role: PlayerRole): String = when (role) {
        PlayerRole.BATSMAN -> "batsmen"
        PlayerRole.BOWLER -> "bowlers"
        PlayerRole.ALL_ROUNDER -> "all-rounders"
        PlayerRole.WICKETKEEPER -> "wicketkeepers"
    }

    /** Places in a T20 XI for a role. */
    private fun slots(role: PlayerRole): Int = if (role == PlayerRole.WICKETKEEPER) 1 else 3

    private fun ordinal(n: Int): String = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    /** T20 form: his last matches in T20 (or overall if he has played fewer than two): a place for every ten points of impact above 25, up to three either way. */
    private fun formAdjust(career: CareerProfile): Int {
        val t20 = career.formByFormat?.get("t20").orEmpty().takeLast(3)
        val form = if (t20.size >= 2) t20 else career.form.orEmpty().takeLast(3)
        return if (form.isEmpty()) 0 else ((form.average() - 25.0) / 10.0).roundToInt().coerceIn(-3, 3)
    }

    private fun formNote(adjust: Int): String = when {
        adjust > 0 -> " Your good form moves you up ${if (adjust == 1) "a place" else "$adjust places"}."
        adjust < 0 -> " Your poor form drops you ${if (adjust == -1) "a place" else "${-adjust} places"}."
        else -> ""
    }

    private fun seen(career: CareerProfile): Int = career.stateMatches + career.internationalMatches + career.leagueMatches

    /** The leagues open to him: an Indian player only the IPL, a Pakistani player every league but the IPL. */
    private fun eligible(career: CareerProfile): List<League> = when (career.nation) {
        "Pakistan" -> LEAGUES.filter { it.id != "ipl" }
        else -> LEAGUES
    }

    /** League matches he must have played before a franchise offers him its captaincy. */
    const val CAPTAIN_MATCHES = 10

    /**
     * A franchise's leadership score for him: a point for every ten league matches, 0.3 for every place of form, his standing among the franchise's players (top two +2, top five +1),
     * an international +1.5, 25 or over +1. At 4.0, with [CAPTAIN_MATCHES] league matches played, the franchise offers him its captaincy with its contract.
     */
    private fun captainScore(career: CareerProfile, standing: Int, adjust: Int): Double =
        career.leagueMatches / 10.0 + adjust * 0.3 + (if (standing <= 2) 2.0 else if (standing <= 5) 1.0 else 0.0) +
            (if (career.isInternational) 1.5 else 0.0) + (if (career.age >= 25) 1.0 else 0.0)

    // ------------------------------------------------------------ the offers

    /** Why no franchise has made an offer (for the message when the list is empty). */
    fun noOfferReason(career: CareerProfile): String =
        if (!career.atOrAboveState) "The franchises only look at players of the state and national squads."
        else if (seen(career) < MIN_MATCHES) "The franchises want to see at least $MIN_MATCHES matches of yours first: you have played ${seen(career)}."
        else "No franchise open to you would pick you yet: they compare your ratings and T20 form with their squads. Keep improving and check again."

    /**
     * The franchises that would play him, best leagues first. Builds the leagues' squads (and runs the IPL's auction), so call it off the main thread.
     * Empty if none would.
     */
    fun offersFor(career: CareerProfile): List<LeagueOffer> {
        if (!career.atOrAboveState || seen(career) < MIN_MATCHES) return emptyList()
        val role = CareerSeason.roleOf(career.role)
        val mine = myValue(career, role)
        val factor = CareerWorld.selectionFactor(career.difficulty)
        val adjust = formAdjust(career)
        val grace = (if (career.isInternational) 1 else 0) + (when (career.specialism) { "White-ball" -> 1; "Test" -> -2; else -> 0 })
        val seed = career.id.hashCode().toLong() * 1000L + career.season
        val found = ArrayList<Candidate>()
        for (league in eligible(career)) {
            val built = (try { league.build(seed) } catch (e: Exception) { null }) ?: continue
            val overseas = league.country != career.nation
            val here = ArrayList<Candidate>()
            for (team in built.pair.first.map { CareerInternational.evolved(it, career.season, null) }) {
                val group = team.players.filter { it.role == role }
                val rank = 1 + group.count { value(it) > mine }
                val cutoff = ((slots(role) / factor).roundToInt() - (if (overseas) 1 else 0)).coerceIn(1, group.size + 1)
                // The franchise he played for last season wants to keep him: a place in his favour.
                val retained = career.lastLeagueTeamId == team.id
                val margin = cutoff - (rank - adjust - grace - (if (retained) 1 else 0))
                val retentionText = if (retained) " Your franchise wants to keep you." else ""
                val standing = 1 + team.players.count { value(it) > mine }
                val captainOffer = career.leagueMatches >= CAPTAIN_MATCHES && captainScore(career, standing, adjust) >= 4.0
                if (margin >= 0) {
                    val note = "You would rank ${ordinal(rank)} among the ${group.size + 1} ${roleWord(role)} of ${team.name}${if (overseas) ", as an overseas player" else ""}, and a T20 XI takes the top $cutoff.${formNote(adjust)}"
                    here.add(Candidate(league.prestige, margin, LeagueOffer(league.id, league.name, team.id, team.name, overseas, seed,
                        note + retentionText + " Contract offered: " + CareerFinance.money(CareerFinance.contractFor(career, league.id, retained).toLong()) + "." +
                            (if (captainOffer) " The franchise wants you as its captain." else ""), captainOffer)))
                }
            }
            found.addAll(here.sortedByDescending { it.margin }.take(MAX_PER_LEAGUE))
        }
        return found.sortedWith(compareByDescending<Candidate> { it.prestige }.thenByDescending { it.margin }).take(MAX_OFFERS).map { it.offer }
    }

    /**
     * The league season of an accepted offer: the game's own league with the player written into the franchise squad (in place of its weakest player
     * of his role), dated from [startAt]. Null if it could not be built. Builds squads (and the IPL's auction): call it off the main thread.
     */
    fun buildSeason(career: CareerProfile, offer: LeagueOffer, startAt: Long): TournamentState? {
        val league = LEAGUES.find { it.id == offer.leagueId } ?: return null
        return try {
            val built = league.build(offer.seed)
            val team = built.pair.first.find { it.id == offer.teamId } ?: return null
            val state0 = league.make(offer.teamId, built, Random(offer.seed))
            val me = CareerDomestic.asPlayer(career, team.id)
            val tag = if (offer.overseas) SquadTag.OVERSEAS else SquadTag.DOMESTIC
            val teams = state0.teams.map { t ->
                val aged = CareerInternational.evolved(t, career.season, null)
                if (t.id == team.id) CareerDomestic.withPlayer(aged, me) else aged
            }
            state0.copy(name = "${league.name}, season ${career.season}", teams = teams, tags = state0.tags + (me.id to tag), createdAt = startAt)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The IPL's Impact Player rule in a career match: the computer's choice of five substitutes for each side, when the player is the captain (he then uses his Impact Player from the
     * Match info page, as in the game's IPL). Null in every other league and when he is not the captain (a player who only bats and bowls leaves the team decisions to the computer,
     * so no Impact Players are used by either side).
     */
    fun impactFor(state: TournamentState, userXI: Team, oppXI: Team, captain: Boolean): ImpactSetup? {
        if (state.tournamentId != IplSeason.TOURNAMENT_ID || !captain) return null
        val userSquad = state.teams.find { it.id == userXI.id } ?: return null
        val oppSquad = state.teams.find { it.id == oppXI.id } ?: return null
        val overseas = state.tags.filterValues { it == SquadTag.OVERSEAS }.keys.toList()
        val overseasSet = overseas.toSet()
        return ImpactSetup(
            ImpactPlayer.aiSubstitutes(userSquad.players, userXI.players, overseasSet),
            ImpactPlayer.aiSubstitutes(oppSquad.players, oppXI.players, overseasSet),
            overseas
        )
    }

    // ------------------------------------------------------------ selection for a match

    /**
     * The franchise selectors' decision for his next league match: his rank among his franchise's players of his role against the places a T20 XI gives that
     * role (a place fewer for an overseas player), his T20 form, and a place in his favour for his first three league matches. The first is a debut.
     */
    fun matchDecision(career: CareerProfile, state: TournamentState): CareerSelection.Decision {
        val team = state.teams.find { it.id == state.userTeamId } ?: return CareerSelection.Decision(true, 1, 11, 15, 0, "")
        val me = team.players.find { it.id == CareerDomestic.playerId(career.id) }
            ?: return CareerSelection.Decision(true, 1, 11, team.players.size, 0, "")
        val overseas = state.tags[me.id] == SquadTag.OVERSEAS
        val group = team.players.filter { it.role == me.role }.sortedByDescending { value(it) }
        val rank = group.indexOfFirst { it.id == me.id }.let { if (it < 0) group.size else it + 1 }
        val factor = CareerWorld.selectionFactor(career.difficulty)
        val cutoff = ((slots(me.role) / factor).roundToInt() - (if (overseas) 1 else 0)).coerceIn(1, group.size)
        val adjust = formAdjust(career)
        val grace = (if (career.leagueMatches < 3) 1 else 0) + (when (career.specialism) { "White-ball" -> 1; "Test" -> -2; else -> 0 })
        val debut = career.leagueMatches == 0
        val captain = career.leagueDecidedSeason == career.season && career.leagueCaptainTeamId != null && career.leagueCaptainTeamId == state.userTeamId
        val selected = debut || captain || rank - adjust - grace <= cutoff
        val base = "You rank ${ordinal(rank)} of ${group.size} ${roleWord(me.role)} at ${team.name}${if (overseas) " as an overseas player" else ""}, and a T20 XI takes the top $cutoff.${formNote(adjust)}"
        val reason = when {
            debut -> "You are in the XI. The franchise gives every new signing a debut: this is your first league match. $base"
            captain -> "You are in the XI: you are the captain of ${team.name}. $base"
            selected -> "You are in the XI. $base"
            else -> "You are not in the XI. $base Score runs, take wickets or improve your ratings to move up."
        }
        return CareerSelection.Decision(selected, rank, cutoff, group.size, adjust, reason)
    }
}
