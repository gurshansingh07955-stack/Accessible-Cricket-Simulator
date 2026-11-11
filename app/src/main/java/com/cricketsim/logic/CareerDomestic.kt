package com.cricketsim.logic

import java.util.Calendar

/**
 * The domestic level of a career: the state team's tournaments, the game's own domestic competitions of the player's country (India: the Syed Mushtaq Ali
 * Trophy, the Vijay Hazare Trophy and the Ranji Trophy; England: the Blast, the One-Day Cup and the County Championship; and so on). A season of them is
 * a list of [TournamentState]s made by the game's own tournament builders, with the player written into his state team's squad in the place of its weakest
 * player of his role. The player's state team is the first of the game's domestic teams of his state ([CareerProfile.domesticTeams]).
 */
object CareerDomestic {

    class Def(
        val tournamentId: String,
        val label: String,
        val shortLabel: String,
        val days: Int,
        val build: () -> Pair<List<Team>, Map<String, SquadTag>>,
        val make: (String, Pair<List<Team>, Map<String, SquadTag>>) -> TournamentState
    )

    /** The domestic tournaments of a country, shortest format first: the order they are played in a season. */
    fun defsFor(country: String): List<Def> = when (country) {
        "India" -> listOf(
            Def(DomesticWhiteBall.SMAT_ID, DomesticWhiteBall.SMAT_NAME, "T20", 1, { DomesticWhiteBall.buildTeams() }, { t, b -> DomesticWhiteBall.newTournament(DomesticWhiteBall.SMAT_ID, t, b) }),
            Def(DomesticWhiteBall.VHT_ID, DomesticWhiteBall.VHT_NAME, "50-over", 1, { DomesticWhiteBall.buildTeams() }, { t, b -> DomesticWhiteBall.newTournament(DomesticWhiteBall.VHT_ID, t, b) }),
            Def(Ranji.TOURNAMENT_ID, Ranji.TOURNAMENT_NAME, "Ranji", 4, { Ranji.buildTeams() }, { t, b -> Ranji.newTournament(t, b) })
        )
        "England" -> listOf(
            Def(CountyCricket.BLAST_ID, CountyCricket.BLAST_NAME, "T20", 1, { CountyCricket.buildTeams() }, { t, b -> CountyCricket.newTournament(CountyCricket.BLAST_ID, t, b) }),
            Def(CountyCricket.ODC_ID, CountyCricket.ODC_NAME, "50-over", 1, { CountyCricket.buildTeams() }, { t, b -> CountyCricket.newTournament(CountyCricket.ODC_ID, t, b) }),
            Def(CountyCricket.CC_ID, CountyCricket.CC_NAME, "Championship", 4, { CountyCricket.buildTeams() }, { t, b -> CountyCricket.newTournament(CountyCricket.CC_ID, t, b) })
        )
        "Australia" -> listOf(
            Def(AusDomestic.ODC_ID, AusDomestic.ODC_NAME, "50-over", 1, { AusDomestic.buildTeams() }, { t, b -> AusDomestic.newTournament(AusDomestic.ODC_ID, t, b) }),
            Def(AusDomestic.SHIELD_ID, AusDomestic.SHIELD_NAME, "Shield", 4, { AusDomestic.buildTeams() }, { t, b -> AusDomestic.newTournament(AusDomestic.SHIELD_ID, t, b) })
        )
        "South Africa" -> listOf(
            Def(SaDomestic.T20_ID, SaDomestic.T20_NAME, "T20", 1, { SaDomestic.buildTeams() }, { t, b -> SaDomestic.newTournament(SaDomestic.T20_ID, t, b) }),
            Def(SaDomestic.ODC_ID, SaDomestic.ODC_NAME, "50-over", 1, { SaDomestic.buildTeams() }, { t, b -> SaDomestic.newTournament(SaDomestic.ODC_ID, t, b) }),
            Def(SaDomestic.FOURDAY_ID, SaDomestic.FOURDAY_NAME, "4-day", 4, { SaDomestic.buildTeams() }, { t, b -> SaDomestic.newTournament(SaDomestic.FOURDAY_ID, t, b) })
        )
        "New Zealand" -> listOf(
            Def(NzDomestic.SMASH_ID, NzDomestic.SMASH_NAME, "T20", 1, { NzDomestic.buildTeams() }, { t, b -> NzDomestic.newTournament(NzDomestic.SMASH_ID, t, b) }),
            Def(NzDomestic.FORD_ID, NzDomestic.FORD_NAME, "50-over", 1, { NzDomestic.buildTeams() }, { t, b -> NzDomestic.newTournament(NzDomestic.FORD_ID, t, b) }),
            Def(NzDomestic.PLUNKET_ID, NzDomestic.PLUNKET_NAME, "Plunket", 4, { NzDomestic.buildTeams() }, { t, b -> NzDomestic.newTournament(NzDomestic.PLUNKET_ID, t, b) })
        )
        "Pakistan" -> listOf(
            Def(PakDomestic.GOLD_ID, PakDomestic.GOLD_NAME, "Gold", 4, { PakDomestic.buildTeams() }, { t, b -> PakDomestic.newTournament(PakDomestic.GOLD_ID, t, b) }),
            Def(PakDomestic.SILVER_ID, PakDomestic.SILVER_NAME, "Silver", 4, { PakDomestic.buildTeams() }, { t, b -> PakDomestic.newTournament(PakDomestic.SILVER_ID, t, b) })
        )
        else -> emptyList()
    }

    fun defFor(tournamentId: String): Def? =
        CareerWorld.COUNTRIES.flatMap { defsFor(it) }.find { it.tournamentId == tournamentId }

    /** The id the player has in every state tournament of his career. */
    fun playerId(careerId: String): String = "career_${careerId}_me"

    private fun v(p: Player): Double = maxOf(p.battingRating, p.bowlingRating) + p.fieldingRating * 0.1

    /** The player as the game's player, with his current ratings. */
    fun asPlayer(career: CareerProfile, teamId: String): Player = Player(
        id = playerId(career.id),
        teamId = teamId,
        name = career.fullName,
        role = CareerSeason.roleOf(career.role),
        battingRating = career.batting.coerceIn(1, 95),
        bowlingRating = career.bowling.coerceIn(1, 95),
        fieldingRating = career.fieldingRatingForState(),
        bowlingStyle = CareerSeason.styleOf(career.bowlingStyle)
    )

    private fun CareerProfile.fieldingRatingForState(): Int = fielding.coerceIn(1, 95)

    /** The squad with the player in the place of its weakest player of his role (a keeper is only replaced by a keeper). */
    internal fun withPlayer(team: Team, me: Player): Team {
        val sameRole = team.players.filter { it.role == me.role }
        val candidates = if (me.role == PlayerRole.WICKETKEEPER && sameRole.isNotEmpty()) sameRole
        else sameRole.ifEmpty { team.players.filter { it.role != PlayerRole.WICKETKEEPER } }
        val victim = candidates.minByOrNull { v(it) } ?: return team
        return team.copy(players = team.players.map { if (it.id == victim.id) me else it })
    }

    /** The state team's squad as the game has it, without the player (used for the state selectors' decision). Null if the state has no team. */
    fun stateSquad(career: CareerProfile): Team? {
        val name = career.domesticTeams.firstOrNull() ?: return null
        for (def in defsFor(career.country)) {
            val built = def.build()
            val team = built.first.find { it.name == name }
            if (team != null) return CareerInternational.evolved(team, career.season, null, career.country)
        }
        return null
    }

    /** A season of the state team's tournaments (every one the team plays in), the player written into the squad, dated week by week after each other. */
    fun newSeason(career: CareerProfile): List<TournamentState>? {
        val name = career.domesticTeams.firstOrNull() ?: return null
        val pid = playerId(career.id)
        val start = CareerSeason.firstSaturday()
        var offsetWeeks = 0
        val out = ArrayList<TournamentState>()
        for (def in defsFor(career.country)) {
            val built = def.build()
            val team = built.first.find { it.name == name } ?: continue
            val state0 = try { def.make(team.id, built) } catch (e: Exception) { continue }
            if (state0.teams.none { it.id == team.id }) continue
            val me = asPlayer(career, team.id)
            val teams = state0.teams.map { t ->
                val aged = CareerInternational.evolved(t, career.season, null, career.country)
                if (t.id == team.id) withPlayer(aged, me) else aged
            }
            val mine = state0.fixtures.count { it.homeId == team.id || it.awayId == team.id }
            val startAt = Calendar.getInstance().apply { timeInMillis = start.timeInMillis; add(Calendar.DAY_OF_YEAR, 7 * offsetWeeks) }.timeInMillis
            out.add(state0.copy(teams = teams, tags = state0.tags + (pid to SquadTag.DOMESTIC), createdAt = startAt))
            offsetWeeks += mine
        }
        return out.ifEmpty { null }
    }
}
