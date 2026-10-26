package com.cricketsim.logic

import kotlin.random.Random

/**
 * ABU DHABI T10 2026: the tournament as announced for November 2026 (the 10th edition).
 *
 *  - SIX TEAMS, the real ones: UAE Bulls (the defending champions), Yas Lions, Royal Desert Champions,
 *    Emirates Eagles, United Tigers and Arabian Aces. Squads are the 18 players each franchise named
 *    after the 2026 player draft.
 *  - THE FORMAT, as announced: a double round-robin (every team plays the other five twice, 30 league
 *    matches, three a day), then the Qualifier (2nd v 3rd in the table) and the Final (1st in the table v
 *    the Qualifier winner): 32 matches, 10 overs a side, all at Sheikh Zayed Stadium, Abu Dhabi.
 *  - THE PLAYERS: anyone already in the game (its own roster, or a player the CPL added) is COPIED with
 *    his ratings, never recreated. Only the players the game did not have are new, with ratings that are
 *    ESTIMATES on the game's scale (see the nw() lines; change them there). A few names are spelled
 *    differently in the game and are linked with al().
 *  - THE RULES: no overseas cap and no breakout rule are known for this league, so none is applied.
 *
 * It reuses the CPL machinery as it is (TournamentState, CplSimulator, the Matches / Table / Venues /
 * Stats tabs, saving, scorecards, Player of the Match, the celebration). Only what is T10-specific is here.
 */
object AbuDhabiT10 {

    const val TOURNAMENT_ID = "adt10_2026"
    const val TOURNAMENT_NAME = "Abu Dhabi T10 2026"
    const val GROUND_ID = "sheikh-zayed"

    private class NewSpec(val role: PlayerRole, val bat: Int, val bowl: Int, val style: BowlingStyle)

    /** A squad slot: a player copied from the game (spec null; `lookup` is the game's spelling if it differs) or a new one. */
    private class Slot(val name: String, val lookup: String?, val spec: NewSpec?)

    private class TeamDef(val id: String, val name: String, val shortName: String, val code: String, val squad: List<Slot>)

    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    private fun ex(name: String) = Slot(name, null, null)
    private fun al(name: String, gameName: String) = Slot(name, gameName, null)
    private fun nw(name: String, role: PlayerRole, bat: Int, bowl: Int, style: BowlingStyle = PACE) =
        Slot(name, null, NewSpec(role, bat, bowl, style))

    private val DEFS: List<TeamDef> by lazy {
        listOf(
            TeamDef("adt_bulls", "UAE Bulls", "Bulls", "BUL", listOf(
                ex("Kieron Pollard"), ex("Sunil Narine"), ex("Romario Shepherd"), nw("Muhammad Rohid", BAT, 62, 10),
                nw("Tahir Zaman", BAT, 55, 30), nw("Aneurin Donald", BAT, 78, 10), al("Muhammad Amir", "Mohammad Amir"),
                ex("Andre Fletcher"), nw("Khwaja Nafay", BAT, 70, 10), ex("Nuwan Thushara"), ex("Khushdil Shah"),
                ex("Mohammad Wasim Jr"), nw("Max Bryant", BAT, 74, 10), ex("Azam Khan"), nw("Wali Muhammad", AR, 54, 58),
                nw("Uddish Suri", BWL, 25, 62), ex("Bradley Currie"), ex("Shadab Khan")
            )),
            TeamDef("adt_lions", "Yas Lions", "Lions", "LIO", listOf(
                ex("Jason Holder"), nw("David Willey", AR, 66, 74), ex("Andries Gous"), nw("Ajay Kumar", BWL, 20, 66),
                nw("Ansh Tandon", BAT, 58, 15), ex("James Vince"), ex("Dasun Shanaka"), nw("Eshan Malinga", BWL, 16, 72),
                ex("Karim Janat"), nw("Sanjay Krishnamurthi", BAT, 60, 10), nw("Tom Moores", WK, 70, 10),
                nw("Khuzaima Bin Tanveer", BWL, 20, 66), ex("Obed McCoy"), ex("Kamil Pooran"), nw("Haider Ali", BAT, 66, 10),
                nw("Masood Gurbaz", WK, 62, 10), ex("Aryan Dutt"), ex("Akeal Hosein")
            )),
            TeamDef("adt_champions", "Royal Desert Champions", "Champions", "RDC", listOf(
                ex("Nicholas Pooran"), ex("Phil Salt"), ex("Sherfane Rutherford"), ex("Ali Naseer"), ex("Aayan Afzal Khan"),
                ex("Muhammad Waseem"), ex("Naseem Shah"), ex("Maheesh Theekshana"), nw("Dushmantha Chameera", BWL, 16, 78),
                ex("Luke Wood"), ex("Gulbadin Naib"), ex("Brandon King"), ex("Janith Liyanage"), ex("Keemo Paul"),
                nw("Dhruv Parashar", AR, 55, 62), nw("Raizal Nadir", BAT, 56, 15), al("Tajinder Singh Dhillon", "Tajinder Singh"),
                ex("Chris Jordan")
            )),
            TeamDef("adt_eagles", "Emirates Eagles", "Eagles", "EAG", listOf(
                ex("Rovman Powell"), ex("Shimron Hetmyer"), ex("Trent Boult"), nw("Haider Razzaq", AR, 56, 60),
                nw("Luqman Faisal", BWL, 20, 60, SPIN), nw("David Wiese", AR, 70, 72), ex("Noor Ahmad"), ex("Alex Hales"),
                nw("KS Bharat", WK, 74, 8), nw("Akif Javed", BWL, 18, 70), nw("Hassan Nawaz", BAT, 74, 10), ex("Rehan Ahmed"),
                nw("Ali Raza", BWL, 20, 68), ex("Chris Green"), nw("Harshit Kaushik", BWL, 20, 60), nw("Hilal Afghan", BAT, 58, 20),
                nw("Toby Albert", BAT, 68, 10), ex("Kusal Perera")
            )),
            TeamDef("adt_tigers", "United Tigers", "Tigers", "TIG", listOf(
                ex("Fakhar Zaman"), ex("Iftikhar Ahmed"), ex("Faheem Ashraf"), nw("Zeeshan Naseer", AR, 58, 55),
                nw("Awais Ali Shah", BAT, 55, 15), ex("Azmatullah Omarzai"), al("Sufyan Moqim", "Sufiyan Muqeem"),
                nw("Abbas Afridi", BWL, 20, 76), ex("Odean Smith"), ex("Nurul Hasan Sohan"), nw("Shak Mahedi Hasan", AR, 62, 72, SPIN),
                nw("Habibur Rahman Sohan", BAT, 58, 10), nw("Noor ul Rahman", BWL, 20, 66, SPIN), ex("Shamim Hossain"),
                nw("Adithya Shetty", AR, 55, 60), nw("Noor Ullah Ayubi", BWL, 20, 58, SPIN), ex("Paul van Meekeren"),
                nw("Yasin Patel", BAT, 55, 15)
            )),
            TeamDef("adt_aces", "Arabian Aces", "Aces", "ACE", listOf(
                ex("Moeen Ali"), ex("Andre Russell"), ex("Liam Livingstone"), ex("Alishan Sharafu"), nw("Muhammad Arfan", BWL, 20, 66),
                nw("Tom Kohler-Cadmore", WK, 80, 10), nw("Richard Gleeson", BWL, 15, 76), nw("Sam Billings", WK, 80, 10),
                nw("Paul Walter", AR, 66, 62), ex("Alzarri Joseph"), ex("Imran Tahir"), nw("Salman Irshad", BWL, 20, 72),
                ex("Quentin Sampson"), ex("Dwaine Pretorius"), al("Vriitya Arvind", "Vriitya Aravind"),
                nw("Ibrahim Masood", BAT, 58, 15), ex("Amshi De Silva"), ex("Dunith Wellalage")
            ))
        )
    }

    fun isTeam(teamId: String): Boolean = DEFS.any { it.id == teamId }

    fun shortNameOrNull(teamId: String): String? = DEFS.find { it.id == teamId }?.shortName

    fun stadium(id: String): Stadium? = stadiums.find { it.id == id }

    val stadiums: List<Stadium> by lazy { listOfNotNull(StadiumData.getStadiumById(GROUND_ID)) }

    /** 10 overs a side here; the CPL and the IPL are T20. */
    fun formatFor(tournamentId: String?): MatchFormat = TournamentRules.baseFormatFor(tournamentId) ?: when (tournamentId) {
        TOURNAMENT_ID -> MatchFormat.T10
        ChampionsTrophy.TOURNAMENT_ID -> MatchFormat.ODI // the Champions Trophy is 50 overs a side
        else -> MatchFormat.T20
    }

    /** Balls in a full innings, for the net run rate (a side bowled out counts the full quota). */
    fun fullInningsBalls(tournamentId: String): Int = TournamentRules.ballsFor(tournamentId) ?: when (tournamentId) {
        TOURNAMENT_ID -> 60
        ChampionsTrophy.TOURNAMENT_ID -> 300
        else -> 120
    }

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun avg(values: List<Int>): Int = if (values.isEmpty()) 50 else values.average().toInt().coerceIn(1, 100)

    /** The six franchises with their squads (strongest first). Everybody is tagged DOMESTIC: no rule here uses the tags. */
    fun buildTeams(): Pair<List<Team>, Map<String, SquadTag>> {
        val roster = CricketData.getAllTeams().flatMap { it.players }.associateBy { norm(it.name) }
        // Players the CPL added (Hales, Pollard, Narine and so on) are in the game too: reuse them as well.
        val cpl = CplData.buildTeams().first.flatMap { it.players }.associateBy { norm(it.name) }
        val tags = LinkedHashMap<String, SquadTag>()
        val teams = DEFS.map { def ->
            val players = def.squad.mapIndexed { index, slot ->
                val id = "${def.id}_${index + 1}"
                val key = norm(slot.lookup ?: slot.name)
                val base = if (slot.spec == null) (roster[key] ?: cpl[key]) else null
                val player = if (base != null) {
                    base.copy(id = id, teamId = def.id, name = slot.name)
                } else {
                    val s = slot.spec ?: NewSpec(PlayerRole.BATSMAN, 60, 20, BowlingStyle.PACE)
                    Player(
                        id = id, teamId = def.id, name = slot.name, role = PlayerProfiles.role(slot.name, s.role),
                        battingRating = s.bat, bowlingRating = s.bowl,
                        fieldingRating = CricketData.deriveFieldingRating(PlayerProfiles.role(slot.name, s.role), s.bat, s.bowl),
                        bowlingStyle = PlayerProfiles.style(slot.name, s.style)
                    )
                }
                tags[id] = SquadTag.DOMESTIC
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
        return Customisation.applyToBuilt(Pair(teams, tags), true)
    }

    /**
     * The league: every team plays the other five twice, three matches a day (so on a match day every
     * team plays once): the circle method gives five match days for one meeting of everybody, played
     * twice with the order of days changed. Different every time. Then the Qualifier and the Final.
     */
    fun buildSchedule(teamIds: List<String>, random: Random): List<TournamentFixture> {
        val order = teamIds.shuffled(random).toMutableList()
        val n = order.size
        fun leg(swap: Boolean): List<List<Pair<String, String>>> {
            val ring = order.toMutableList()
            val days = ArrayList<List<Pair<String, String>>>()
            repeat(n - 1) {
                val day = ArrayList<Pair<String, String>>()
                for (i in 0 until n / 2) {
                    val a = ring[i]
                    val b = ring[n - 1 - i]
                    day.add(if (swap) b to a else a to b)
                }
                days.add(day.shuffled(random))
                // Keep the first team still and turn the rest of the ring by one place.
                ring.add(1, ring.removeAt(n - 1))
            }
            return days.shuffled(random)
        }
        val days = leg(false) + leg(true)
        val fixtures = ArrayList<TournamentFixture>()
        var index = 0
        for (day in days) for ((home, away) in day) {
            fixtures.add(TournamentFixture(index, index + 1, CplData.LEAGUE, home, away, GROUND_ID))
            index++
        }
        fixtures.add(TournamentFixture(index, index + 1, "Qualifier", null, null, GROUND_ID, placeholder = "2nd v 3rd in the table"))
        index++
        fixtures.add(TournamentFixture(index, index + 1, "Final", null, null, GROUND_ID, placeholder = "1st in the table v the winner of the Qualifier"))
        return fixtures
    }

    fun newTournament(userTeamId: String, built: Pair<List<Team>, Map<String, SquadTag>>, random: Random): TournamentState =
        TournamentState(
            version = CplData.SAVE_VERSION,
            tournamentId = TOURNAMENT_ID,
            name = TOURNAMENT_NAME,
            userTeamId = userTeamId,
            teams = built.first,
            tags = built.second,
            fixtures = buildSchedule(built.first.map { it.id }, random),
            playerStats = emptyMap(),
            createdAt = System.currentTimeMillis()
        )
}
