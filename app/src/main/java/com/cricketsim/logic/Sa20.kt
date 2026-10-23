package com.cricketsim.logic

/**
 * SA20 2027 (Betway SA20 Season 5, 17 January to 21 February 2027; Sunrisers Eastern Cape are the champions).
 *
 *  - SIX TEAMS, the real ones: Durban's Super Giants, Joburg Super Kings, MI Cape Town, Paarl Royals, Pretoria
 *    Capitals and Sunrisers Eastern Cape, with the squads they finished the 7 October 2026 auction with
 *    (18 or 19 players each, the wildcard included; Joburg's wildcard was not yet named).
 *  - THE FORMAT, as published: a double round-robin (30 league matches, every team hosting every other once, at its
 *    own ground), then Qualifier 1 (1st v 2nd) at Boland Park, the Eliminator (3rd v 4th) at the Wanderers,
 *    Qualifier 2 (Qualifier 1 loser v Eliminator winner) at the Wanderers and the Final at Newlands: 34 matches, T20.
 *  - THE FIXTURES: who hosts whom is certain (each team hosts each rival once). The ORDER of the first 16 matches
 *    follows the published list; for matches 17 to 30 the sources I could read disagreed on team names, so the
 *    order there is reconstructed from the dates and grounds (the pairings and venues are right, an odd date may
 *    be swapped).
 *  - THE RULES used: at most four overseas players in the XI. NOT built: the league's bonus point (4 for a win
 *    plus 1 for a big run-rate win; the game's table gives 2 a win), and the under-23 rule.
 *
 * All six grounds were already in the game. Players already in the game (roster or one added by an earlier
 * tournament) are COPIED with their ratings; the others are new with ESTIMATED ratings (the nw() lines).
 * It reuses the CPL machinery as it is.
 */
object Sa20 {

    const val TOURNAMENT_ID = "sa20_2027"
    const val TOURNAMENT_NAME = "SA20 2027"

    private const val KINGSMEAD = "kingsmead"
    private const val WANDERERS = "wanderers"
    private const val NEWLANDS = "newlands"
    private const val BOLAND = "boland-park"
    private const val CENTURION = "supersport-park"
    private const val GQEBERHA = "st-georges-park"

    private class NewSpec(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)
    private class Slot(val name: String, val lookup: String?, val tag: SquadTag, val spec: NewSpec?)
    private class TeamDef(val id: String, val name: String, val shortName: String, val code: String, val home: String, val squad: List<Slot>)

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN
    private val S = SquadTag.DOMESTIC   // a South African
    private val X = SquadTag.OVERSEAS   // an overseas player (Gous plays for the USA, so counts as overseas)

    private fun ex(name: String, tag: SquadTag) = Slot(name, null, tag, null)
    private fun al(name: String, gameName: String, tag: SquadTag) = Slot(name, gameName, tag, null)
    private fun nw(name: String, tag: SquadTag, role: PlayerRole, bat: Int, bowl: Int, style: BowlingStyle = PACE) =
        Slot(name, null, tag, NewSpec(role, bat, bowl, style))

    private val DEFS: List<TeamDef> by lazy {
        listOf(
            TeamDef("sa_dsg", "Durban's Super Giants", "Super Giants", "DSG", KINGSMEAD, listOf(
                ex("Jos Buttler", X), ex("Devon Conway", X), ex("Sam Curran", X), ex("Liam Livingstone", X), ex("Sunil Narine", X),
                ex("Kane Williamson", X), ex("Marques Ackerman", S), nw("David Bedingham", S, BAT, 72, 10),
                nw("Eathan Bosch", S, AR, 56, 62), ex("Gerald Coetzee", S), ex("Tony de Zorzi", S),
                nw("Daryn Dupavillon", S, BWL, 20, 66), nw("Simon Harmer", S, BWL, 25, 78, SPIN), nw("Evan Jones", S, BAT, 60, 15),
                ex("Aiden Markram", S), nw("Kyle Simmonds", S, AR, 56, 58), ex("Kwena Maphaka", S),
                nw("Andile Simelane", S, BWL, 20, 62), ex("Heinrich Klaasen", S)
            )),
            TeamDef("sa_jsk", "Joburg Super Kings", "Joburg", "JSK", WANDERERS, listOf(
                nw("Leus du Plooy", S, BAT, 70, 25, SPIN), ex("Jason Holder", X), ex("Akeal Hosein", X), ex("James Vince", X),
                nw("Matthew de Villiers", S, AR, 56, 58), ex("Donovan Ferreira", S), nw("Dian Forrester", S, AR, 62, 60),
                nw("Duan Jansen", S, BWL, 20, 70), ex("Rilee Rossouw", S), nw("Janco Smit", S, AR, 52, 58),
                ex("Prenelan Subrayen", S), ex("Faf du Plessis", S), ex("Blessing Muzarabani", X), ex("Kieron Pollard", X),
                ex("Andries Gous", X), nw("Dayyaan Galiem", S, BAT, 58, 30), nw("Jarren Bacher", S, AR, 50, 52, SPIN),
                nw("Jason Rowles", S, BAT, 66, 15)
            )),
            TeamDef("sa_mict", "MI Cape Town", "MI Cape Town", "MIC", NEWLANDS, listOf(
                ex("Will Jacks", X), ex("Rashid Khan", X), ex("Nicholas Pooran", X), ex("Corbin Bosch", S), ex("Reeza Hendricks", S),
                nw("Thomas Kaber", S, WK, 60, 10), ex("George Linde", S), nw("Dane Piedt", S, BWL, 25, 70, SPIN),
                ex("Ryan Rickelton", S), nw("Jason Smith", S, AR, 62, 56), nw("Tiaan van Vuuren", S, BWL, 20, 64),
                ex("Trent Boult", X), ex("Wiaan Mulder", S), ex("Shimron Hetmyer", X), nw("Lehan Botha", S, BAT, 52, 15),
                nw("Daniel Smith", S, BWL, 20, 58), ex("Tristan Luus", S), nw("Oliver Whitehead", S, BAT, 58, 15),
                ex("Kagiso Rabada", S)
            )),
            TeamDef("sa_pr", "Paarl Royals", "Paarl", "PAR", BOLAND, listOf(
                ex("Dan Lawrence", X), ex("Gudakesh Motie", X), ex("Azmatullah Omarzai", X), ex("Sikandar Raza", X),
                nw("Thomas Rew", X, WK, 66, 10), ex("Asa Tribe", X), ex("Mujeeb Ur Rahman", X),
                al("Ottneil Baartman", "Ottniel Baartman", S), ex("Bjorn Fortuin", S), ex("Rubin Hermann", S),
                nw("Keagan Lion-Cachet", S, AR, 56, 62), ex("David Miller", S), nw("Delano Potgieter", S, AR, 54, 58, SPIN),
                nw("Kyle Verreynne", S, WK, 72, 10), ex("Hardus Viljoen", S), nw("Jacob Johannes Basson", S, AR, 52, 50, SPIN),
                nw("Nqobani Mokoena", S, BWL, 20, 64), ex("Lhuan-dre Pretorius", S), ex("Joe Root", X)
            )),
            TeamDef("sa_pc", "Pretoria Capitals", "Pretoria", "PRC", CENTURION, listOf(
                ex("Sherfane Rutherford", X), ex("Phil Salt", X), ex("Matthew Short", X), nw("Connor Esterhuizen", S, WK, 66, 10),
                nw("Wihan Lubbe", S, WK, 62, 10), ex("Keshav Maharaj", S), al("Lungisani Ngidi", "Lungi Ngidi", S),
                nw("Bryce Parsons", S, AR, 60, 62), nw("Gideon Peters", S, BWL, 20, 66), ex("Lizaad Williams", S),
                nw("Codi Yusuf", S, BWL, 20, 66), ex("Rassie van der Dussen", S), ex("Temba Bavuma", S),
                nw("Josh Breed", S, AR, 54, 50, SPIN), ex("Ackeem Auguste", X), ex("Keemo Paul", X), ex("Dewald Brevis", S),
                nw("Bayanda Majola", S, BWL, 20, 60, SPIN), ex("Andre Russell", X)
            )),
            TeamDef("sa_sec", "Sunrisers Eastern Cape", "Sunrisers", "SEC", GQEBERHA, listOf(
                ex("Tristan Stubbs", S), ex("Mitchell Marsh", X), ex("Adam Milne", X), ex("Rishad Hossain", X), ex("Quinton de Kock", S),
                ex("Matthew Breetzke", S), ex("Anrich Nortje", S), nw("Mitchell van Buuren", S, AR, 58, 56, SPIN),
                nw("Jordan Hermann", S, BAT, 68, 10), nw("Lewis Gregory", X, AR, 56, 70), nw("Senuran Muthusamy", S, AR, 56, 60, SPIN),
                nw("Patrick Kruger", S, AR, 58, 60), ex("James Coles", X), nw("Lutho Sipamla", S, BWL, 20, 74),
                ex("Nandre Burger", S), ex("Craig Overton", X), nw("CJ King", S, AR, 54, 58), nw("JP King", S, AR, 56, 50),
                ex("Marco Jansen", S)
            ))
        )
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun homeGround(teamId: String): String = DEFS.find { it.id == teamId }?.home ?: NEWLANDS

    val stadiums: List<Stadium> by lazy {
        listOf(GQEBERHA, BOLAND, CENTURION, NEWLANDS, KINGSMEAD, WANDERERS).mapNotNull { StadiumData.getStadiumById(it) }
    }

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** The six franchises (strongest first); South Africans are tagged DOMESTIC, everybody else OVERSEAS (at most four play). */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        val cpl = CplData.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val adt = AbuDhabiT10.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val lpl = LankaPremierLeague.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val ilt = InternationalLeagueT20.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val bbl = BigBashLeague.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val mlc = MajorLeagueCricket.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = DEFS.map { def ->
            val players = def.squad.mapIndexed { index, slot ->
                val id = "${def.id}_${index + 1}"
                val key = norm(slot.lookup ?: slot.name)
                val base = if (slot.spec == null) {
                    roster[key] ?: cpl[key] ?: adt[key] ?: lpl[key] ?: ilt[key] ?: bbl[key] ?: mlc[key]
                } else null
                val player = if (base != null) {
                    base.copy(id = id, teamId = def.id, name = slot.name)
                } else {
                    val s = slot.spec ?: NewSpec(PlayerRole.BATSMAN, 60, 20, BowlingStyle.PACE)
                    Player(
                        id = id, teamId = def.id, name = slot.name, role = s.role,
                        battingRating = s.bat, bowlingRating = s.bowl,
                        fieldingRating = CricketData.deriveFieldingRating(s.role, s.bat, s.bowl),
                        bowlingStyle = s.style
                    )
                }
                tags[id] = slot.tag
                player
            }.sortedByDescending { maxOf(it.battingRating, it.bowlingRating) }
            Team(
                id = def.id, name = def.name, countryCode = def.code,
                battingRating = avg(players.map { it.battingRating }.sortedDescending().take(8)),
                bowlingRating = avg(players.map { it.bowlingRating }.sortedDescending().take(5)),
                fieldingRating = avg(players.map { it.fieldingRating }),
                players = players
            )
        }
        return Pair(teams, tags)
    }

    // S Sunrisers EC, P Pretoria, D Durban, J Joburg, M MI Cape Town, R Paarl Royals: "home away". The home side's ground is the venue.
    private val LEAGUE_FIXTURES = listOf(
        "S P", "R J", "P D", "M J", "D S", "M R", "D P", "J S", "R M", "P S",
        "D R", "S J", "R P", "S M", "J D", "P R", "M D", "J R", "M S", "P J",
        "D M", "R S", "J P", "S D", "J M", "R D", "P M", "D J", "S R", "M P"
    )

    private val TEAM_OF = mapOf('S' to "sa_sec", 'P' to "sa_pc", 'D' to "sa_dsg", 'J' to "sa_jsk", 'M' to "sa_mict", 'R' to "sa_pr")

    /** The league fixtures at the home sides' grounds, then Qualifier 1 (Paarl), the Eliminator and Qualifier 2 (Wanderers) and the Final (Newlands). */
    fun buildSchedule(): List<TournamentFixture> {
        val fixtures = ArrayList<TournamentFixture>()
        LEAGUE_FIXTURES.forEachIndexed { index, code ->
            val p = code.split(" ")
            val home = TEAM_OF.getValue(p[0][0])
            fixtures.add(TournamentFixture(index, index + 1, CplData.LEAGUE, home, TEAM_OF.getValue(p[1][0]), homeGround(home)))
        }
        var next = fixtures.size
        fun playoff(stage: String, ground: String, note: String) {
            fixtures.add(TournamentFixture(next, next + 1, stage, null, null, ground, placeholder = note))
            next++
        }
        playoff("Qualifier 1", BOLAND, "1st v 2nd in the table")
        playoff("Eliminator", WANDERERS, "3rd v 4th in the table")
        playoff("Qualifier 2", WANDERERS, "Loser of Qualifier 1 v winner of the Eliminator")
        playoff("Final", NEWLANDS, "Winners of Qualifier 1 and Qualifier 2")
        return fixtures
    }

    fun newTournament(userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = TOURNAMENT_ID,
            name = TOURNAMENT_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )
}
