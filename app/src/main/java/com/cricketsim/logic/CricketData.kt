package com.cricketsim.logic

import java.util.UUID
import kotlin.math.roundToInt

/**
 * Ported from helpers/cricketData.tsx (the Floot/React web app remains
 * the source of truth for gameplay design). Pure Kotlin, no Android
 * dependencies — usable and unit-testable independent of the eventual
 * Compose UI layer. See PORTING_NOTES.md at the repo root for what's
 * been ported so far and what's still pending.
 */

enum class PlayerRole { BATSMAN, BOWLER, ALL_ROUNDER, WICKETKEEPER }

/**
 * Only meaningful for players who can bowl (BOWLER or ALL_ROUNDER).
 * Will drive which set of bowling variations is offered and which speed
 * range applies once bowlingSystem.tsx's port lands.
 */
enum class BowlingStyle { PACE, SPIN }

data class Player(
    val id: String,
    val teamId: String,
    val name: String,
    val role: PlayerRole,
    val battingRating: Int, // 1-100
    val bowlingRating: Int, // 1-100
    val fieldingRating: Int, // 1-100
    val bowlingStyle: BowlingStyle
)

data class Team(
    val id: String,
    val name: String,
    val countryCode: String,
    val battingRating: Int, // 1-100
    val bowlingRating: Int, // 1-100
    val fieldingRating: Int, // 1-100
    val players: List<Player>,
    // Only ever set on a MATCH SQUAD (see CricketData.buildMatchSquad) —
    // the roster-level Team objects in CricketData.getAllTeams() never
    // set these, since there's no single "the" captain/keeper for a full
    // squad, only for whichever XI actually takes the field.
    val captainId: String? = null,
    val viceCaptainId: String? = null,
    val wicketkeeperId: String? = null
)

object CricketData {

    // --- Fielding rating derivation ---
    //
    // Fielding wasn't tracked per player in the original data model, so
    // rather than hand-authoring a rating for every one of the ~430
    // players below, it's derived here from role and overall ability:
    // wicketkeepers and all-rounders (picked partly for their
    // athleticism) skew high, specialist batsmen get a solid athletic
    // baseline, and specialist bowlers — fielded mainly for their arm
    // rather than their agility — skew a bit lower.
    private fun clampRating(value: Double): Int = value.roundToInt().coerceIn(35, 97)

    fun deriveFieldingRating(role: PlayerRole, battingRating: Int, bowlingRating: Int): Int = when (role) {
        PlayerRole.WICKETKEEPER -> clampRating(battingRating * 0.5 + 45)
        PlayerRole.ALL_ROUNDER -> clampRating(((battingRating + bowlingRating) / 2.0) * 0.8 + 15)
        PlayerRole.BOWLER -> clampRating(bowlingRating * 0.5 + 30)
        PlayerRole.BATSMAN -> clampRating(battingRating * 0.7 + 15)
    }

    /**
     * A handful of real players are individually famous specifically FOR
     * their fielding — their reputation has little to do with how good a
     * batsman or bowler they are, which is exactly what the formula above
     * can't see. fieldingRatingOverride lets those specific players'
     * fielding be hand-set instead of silently flattened into the generic
     * formula (see Kohli, Jadeja, Glenn Phillips, Cameron Green, David
     * Warner, and Andre Russell below).
     */
    private fun createPlayer(
        teamId: String,
        name: String,
        role: PlayerRole,
        battingRating: Int,
        bowlingRating: Int,
        bowlingStyle: BowlingStyle = BowlingStyle.PACE,
        fieldingRatingOverride: Int? = null
    ): Player = Player(
        id = UUID.randomUUID().toString(),
        teamId = teamId,
        name = name,
        role = role,
        battingRating = battingRating,
        bowlingRating = bowlingRating,
        fieldingRating = fieldingRatingOverride ?: deriveFieldingRating(role, battingRating, bowlingRating),
        bowlingStyle = bowlingStyle
    )

    // Shorthand so the roster table below reads close to one player per
    // line, matching the original TSX source layout.
    private val BAT = PlayerRole.BATSMAN
    private val BWL = PlayerRole.BOWLER
    private val AR = PlayerRole.ALL_ROUNDER
    private val WK = PlayerRole.WICKETKEEPER
    private val PACE = BowlingStyle.PACE
    private val SPIN = BowlingStyle.SPIN

    // --- Team ids ---
    private const val INDIA_ID = "team_ind"
    private const val AUS_ID = "team_aus"
    private const val ENG_ID = "team_eng"
    private const val PAK_ID = "team_pak"
    private const val SA_ID = "team_sa"
    private const val NZ_ID = "team_nz"
    private const val SL_ID = "team_sl"
    private const val WI_ID = "team_wi"
    private const val BAN_ID = "team_ban"
    private const val AFG_ID = "team_afg"
    private const val IRE_ID = "team_ire"
    private const val ZIM_ID = "team_zim"
    private const val NED_ID = "team_ned"
    private const val SCO_ID = "team_sco"
    private const val NEP_ID = "team_nep"
    private const val UAE_ID = "team_uae"
    private const val OMAN_ID = "team_oman"
    private const val USA_ID = "team_usa"

    private val TEAMS_DATA: List<Team> by lazy {
        listOf(
            Team(
                id = INDIA_ID, name = "India", countryCode = "IND",
                battingRating = 95, bowlingRating = 92, fieldingRating = 90,
                players = listOf(
                    // Rohit Sharma and Virat Kohli are deliberately given
                    // identical batting and bowling ratings (rather than
                    // their individually "accurate" values) so neither is
                    // a mechanically stronger pick.
                    createPlayer(INDIA_ID, "Rohit Sharma", BAT, 95, 22),
                    createPlayer(INDIA_ID, "Yashasvi Jaiswal", BAT, 88, 30),
                    createPlayer(INDIA_ID, "Virat Kohli", BAT, 95, 22, PACE, 90),
                    createPlayer(INDIA_ID, "Suryakumar Yadav", BAT, 92, 15, PACE, 90),
                    createPlayer(INDIA_ID, "Rishabh Pant", WK, 89, 10),
                    createPlayer(INDIA_ID, "Hardik Pandya", AR, 85, 84, PACE),
                    createPlayer(INDIA_ID, "Ravindra Jadeja", AR, 82, 86, SPIN, 94),
                    createPlayer(INDIA_ID, "Axar Patel", AR, 78, 82, SPIN),
                    createPlayer(INDIA_ID, "Kuldeep Yadav", BWL, 25, 89, SPIN),
                    createPlayer(INDIA_ID, "Jasprit Bumrah", BWL, 30, 97, PACE),
                    createPlayer(INDIA_ID, "Mohammed Siraj", BWL, 20, 88, PACE),
                    createPlayer(INDIA_ID, "Shubman Gill", BAT, 90, 10),
                    createPlayer(INDIA_ID, "Sanju Samson", WK, 84, 10),
                    createPlayer(INDIA_ID, "Abhishek Sharma", AR, 82, 55, SPIN),
                    createPlayer(INDIA_ID, "Mayank Yadav", BWL, 15, 88, PACE),
                    createPlayer(INDIA_ID, "KL Rahul", WK, 87, 10),
                    createPlayer(INDIA_ID, "Shreyas Iyer", BAT, 85, 15),
                    createPlayer(INDIA_ID, "Ruturaj Gaikwad", BAT, 84, 10),
                    createPlayer(INDIA_ID, "Shivam Dube", AR, 80, 60, PACE),
                    createPlayer(INDIA_ID, "Washington Sundar", AR, 70, 78, SPIN),
                    createPlayer(INDIA_ID, "Arshdeep Singh", BWL, 20, 87, PACE),
                    createPlayer(INDIA_ID, "Harshit Rana", BWL, 25, 82, PACE),
                    createPlayer(INDIA_ID, "Varun Chakravarthy", BWL, 15, 84, SPIN),
                    createPlayer(INDIA_ID, "Ravichandran Ashwin", AR, 65, 88, SPIN),
                    createPlayer(INDIA_ID, "Rinku Singh", BAT, 82, 10),
                    createPlayer(INDIA_ID, "Tilak Varma", BAT, 83, 15),
                    createPlayer(INDIA_ID, "Prasidh Krishna", BWL, 20, 83, PACE),
                    createPlayer(INDIA_ID, "Yuzvendra Chahal", BWL, 15, 87, SPIN),
                    createPlayer(INDIA_ID, "Ishan Kishan", WK, 80, 10),
                    createPlayer(INDIA_ID, "Deepak Chahar", BWL, 35, 80, PACE)
                )
            ),
            Team(
                id = AUS_ID, name = "Australia", countryCode = "AUS",
                battingRating = 93, bowlingRating = 94, fieldingRating = 95,
                players = listOf(
                    createPlayer(AUS_ID, "Travis Head", BAT, 91, 40),
                    // Warner is a genuinely excellent slip catcher, not
                    // just "good for a batsman".
                    createPlayer(AUS_ID, "David Warner", BAT, 89, 10, PACE, 87),
                    createPlayer(AUS_ID, "Mitchell Marsh", AR, 86, 75, PACE),
                    createPlayer(AUS_ID, "Steve Smith", BAT, 92, 30),
                    createPlayer(AUS_ID, "Glenn Maxwell", AR, 88, 78, SPIN),
                    createPlayer(AUS_ID, "Marcus Stoinis", AR, 84, 76, PACE),
                    createPlayer(AUS_ID, "Tim David", BAT, 85, 20),
                    createPlayer(AUS_ID, "Pat Cummins", BWL, 60, 95, PACE),
                    createPlayer(AUS_ID, "Mitchell Starc", BWL, 55, 93, PACE),
                    createPlayer(AUS_ID, "Adam Zampa", BWL, 20, 89, SPIN),
                    createPlayer(AUS_ID, "Josh Hazlewood", BWL, 25, 91, PACE),
                    createPlayer(AUS_ID, "Alex Carey", WK, 82, 10),
                    // Green's athleticism and ground coverage is a real
                    // part of his reputation, not an afterthought.
                    createPlayer(AUS_ID, "Cameron Green", AR, 82, 78, PACE, 92),
                    createPlayer(AUS_ID, "Marnus Labuschagne", BAT, 87, 30),
                    createPlayer(AUS_ID, "Josh Inglis", WK, 80, 10),
                    createPlayer(AUS_ID, "Matthew Short", AR, 78, 65, SPIN),
                    createPlayer(AUS_ID, "Aaron Hardie", AR, 75, 70, PACE),
                    createPlayer(AUS_ID, "Nathan Ellis", BWL, 25, 84, PACE),
                    createPlayer(AUS_ID, "Sean Abbott", BWL, 30, 82, PACE),
                    createPlayer(AUS_ID, "Spencer Johnson", BWL, 20, 86, PACE),
                    createPlayer(AUS_ID, "Xavier Bartlett", BWL, 20, 83, PACE),
                    createPlayer(AUS_ID, "Ben Dwarshuis", BWL, 25, 80, PACE),
                    createPlayer(AUS_ID, "Tanveer Sangha", BWL, 15, 80, SPIN),
                    createPlayer(AUS_ID, "Jake Fraser-McGurk", BAT, 84, 10),
                    createPlayer(AUS_ID, "Josh Philippe", WK, 76, 10),
                    createPlayer(AUS_ID, "Matthew Wade", WK, 78, 10),
                    createPlayer(AUS_ID, "Ashton Agar", AR, 65, 78, SPIN),
                    createPlayer(AUS_ID, "Nathan Lyon", BWL, 30, 84, SPIN),
                    createPlayer(AUS_ID, "Ben McDermott", WK, 76, 10),
                    createPlayer(AUS_ID, "Jason Behrendorff", BWL, 25, 82, PACE)
                )
            ),
            Team(
                id = ENG_ID, name = "England", countryCode = "ENG",
                battingRating = 91, bowlingRating = 88, fieldingRating = 89,
                players = listOf(
                    createPlayer(ENG_ID, "Jos Buttler", WK, 93, 10),
                    createPlayer(ENG_ID, "Phil Salt", BAT, 87, 10),
                    createPlayer(ENG_ID, "Will Jacks", BAT, 85, 60, SPIN),
                    createPlayer(ENG_ID, "Jonny Bairstow", BAT, 88, 15),
                    createPlayer(ENG_ID, "Harry Brook", BAT, 89, 10),
                    createPlayer(ENG_ID, "Moeen Ali", AR, 82, 80, SPIN),
                    createPlayer(ENG_ID, "Liam Livingstone", AR, 84, 75, SPIN),
                    createPlayer(ENG_ID, "Sam Curran", AR, 78, 82, PACE),
                    createPlayer(ENG_ID, "Jofra Archer", BWL, 40, 92, PACE),
                    createPlayer(ENG_ID, "Adil Rashid", BWL, 30, 88, SPIN),
                    createPlayer(ENG_ID, "Mark Wood", BWL, 25, 90, PACE),
                    createPlayer(ENG_ID, "Ben Duckett", BAT, 85, 10),
                    createPlayer(ENG_ID, "Joe Root", BAT, 90, 25),
                    createPlayer(ENG_ID, "Ben Stokes", AR, 85, 75, PACE),
                    createPlayer(ENG_ID, "Jacob Bethell", AR, 78, 55, SPIN),
                    createPlayer(ENG_ID, "Jamie Smith", WK, 80, 10),
                    createPlayer(ENG_ID, "Jordan Cox", WK, 74, 10),
                    createPlayer(ENG_ID, "Reece Topley", BWL, 15, 83, PACE),
                    createPlayer(ENG_ID, "Chris Jordan", BWL, 25, 80, PACE),
                    createPlayer(ENG_ID, "Brydon Carse", BWL, 30, 84, PACE),
                    createPlayer(ENG_ID, "Gus Atkinson", BWL, 25, 85, PACE),
                    createPlayer(ENG_ID, "Rehan Ahmed", BWL, 20, 78, SPIN),
                    createPlayer(ENG_ID, "Tom Hartley", BWL, 15, 76, SPIN),
                    createPlayer(ENG_ID, "Dawid Malan", BAT, 84, 10),
                    createPlayer(ENG_ID, "James Vince", BAT, 80, 10),
                    createPlayer(ENG_ID, "Sam Hain", BAT, 76, 10),
                    createPlayer(ENG_ID, "Olly Stone", BWL, 15, 82, PACE),
                    createPlayer(ENG_ID, "Saqib Mahmood", BWL, 20, 81, PACE),
                    createPlayer(ENG_ID, "Luke Wood", BWL, 20, 78, PACE),
                    createPlayer(ENG_ID, "Will Smeed", BAT, 76, 10)
                )
            ),
            Team(
                id = PAK_ID, name = "Pakistan", countryCode = "PAK",
                battingRating = 86, bowlingRating = 90, fieldingRating = 75,
                players = listOf(
                    createPlayer(PAK_ID, "Babar Azam", BAT, 92, 10),
                    createPlayer(PAK_ID, "Mohammad Rizwan", WK, 89, 10),
                    createPlayer(PAK_ID, "Saim Ayub", BAT, 80, 40),
                    createPlayer(PAK_ID, "Fakhar Zaman", BAT, 84, 20),
                    createPlayer(PAK_ID, "Iftikhar Ahmed", AR, 78, 65, SPIN),
                    createPlayer(PAK_ID, "Shadab Khan", AR, 75, 82, SPIN),
                    createPlayer(PAK_ID, "Imad Wasim", AR, 72, 80, SPIN),
                    createPlayer(PAK_ID, "Shaheen Afridi", BWL, 45, 93, PACE),
                    createPlayer(PAK_ID, "Naseem Shah", BWL, 30, 89, PACE),
                    createPlayer(PAK_ID, "Haris Rauf", BWL, 20, 88, PACE),
                    createPlayer(PAK_ID, "Mohammad Amir", BWL, 35, 87, PACE),
                    createPlayer(PAK_ID, "Abdullah Shafique", BAT, 80, 10),
                    createPlayer(PAK_ID, "Usman Khan", BAT, 78, 10),
                    createPlayer(PAK_ID, "Kamran Ghulam", BAT, 76, 15),
                    createPlayer(PAK_ID, "Salman Agha", AR, 78, 65, SPIN),
                    createPlayer(PAK_ID, "Khushdil Shah", AR, 78, 55, SPIN),
                    createPlayer(PAK_ID, "Mohammad Nawaz", AR, 68, 78, SPIN),
                    createPlayer(PAK_ID, "Faheem Ashraf", AR, 65, 76, PACE),
                    createPlayer(PAK_ID, "Abrar Ahmed", BWL, 15, 84, SPIN),
                    createPlayer(PAK_ID, "Zaman Khan", BWL, 20, 80, PACE),
                    createPlayer(PAK_ID, "Mohammad Wasim Jr", BWL, 20, 82, PACE),
                    createPlayer(PAK_ID, "Hasan Ali", BWL, 30, 80, PACE),
                    createPlayer(PAK_ID, "Shan Masood", BAT, 78, 10),
                    createPlayer(PAK_ID, "Sarfaraz Ahmed", WK, 76, 10),
                    createPlayer(PAK_ID, "Azam Khan", WK, 74, 10),
                    createPlayer(PAK_ID, "Imam-ul-Haq", BAT, 79, 10),
                    createPlayer(PAK_ID, "Mohammad Haris", WK, 76, 10),
                    createPlayer(PAK_ID, "Ihsanullah", BWL, 15, 83, PACE),
                    createPlayer(PAK_ID, "Aamer Jamal", AR, 70, 78, PACE),
                    createPlayer(PAK_ID, "Saud Shakeel", BAT, 79, 10)
                )
            ),
            Team(
                id = SA_ID, name = "South Africa", countryCode = "RSA",
                battingRating = 89, bowlingRating = 89, fieldingRating = 92,
                players = listOf(
                    createPlayer(SA_ID, "Quinton de Kock", WK, 90, 10),
                    createPlayer(SA_ID, "Reeza Hendricks", BAT, 82, 10),
                    createPlayer(SA_ID, "Aiden Markram", BAT, 87, 50, SPIN),
                    createPlayer(SA_ID, "Heinrich Klaasen", BAT, 91, 10),
                    createPlayer(SA_ID, "David Miller", BAT, 88, 10, PACE, 86),
                    createPlayer(SA_ID, "Tristan Stubbs", BAT, 84, 20),
                    createPlayer(SA_ID, "Marco Jansen", AR, 70, 85, PACE),
                    createPlayer(SA_ID, "Keshav Maharaj", BWL, 40, 86, SPIN),
                    createPlayer(SA_ID, "Kagiso Rabada", BWL, 35, 92, PACE),
                    createPlayer(SA_ID, "Anrich Nortje", BWL, 25, 90, PACE),
                    createPlayer(SA_ID, "Tabraiz Shamsi", BWL, 15, 85, SPIN),
                    createPlayer(SA_ID, "Temba Bavuma", BAT, 80, 10),
                    createPlayer(SA_ID, "Rassie van der Dussen", BAT, 84, 10),
                    createPlayer(SA_ID, "Ryan Rickelton", WK, 78, 10),
                    createPlayer(SA_ID, "Matthew Breetzke", BAT, 76, 10),
                    createPlayer(SA_ID, "Dewald Brevis", BAT, 80, 30),
                    createPlayer(SA_ID, "Wiaan Mulder", AR, 75, 70, PACE),
                    createPlayer(SA_ID, "Andile Phehlukwayo", AR, 72, 75, PACE),
                    createPlayer(SA_ID, "Gerald Coetzee", BWL, 25, 84, PACE),
                    createPlayer(SA_ID, "Lungi Ngidi", BWL, 20, 85, PACE),
                    createPlayer(SA_ID, "Nandre Burger", BWL, 15, 80, PACE),
                    createPlayer(SA_ID, "Bjorn Fortuin", BWL, 20, 76, SPIN),
                    createPlayer(SA_ID, "Ottniel Baartman", BWL, 15, 78, PACE),
                    createPlayer(SA_ID, "Lizaad Williams", BWL, 20, 76, PACE),
                    createPlayer(SA_ID, "Donovan Ferreira", WK, 74, 10),
                    createPlayer(SA_ID, "Corbin Bosch", AR, 68, 76, PACE),
                    createPlayer(SA_ID, "Kwena Maphaka", BWL, 15, 80, PACE),
                    createPlayer(SA_ID, "Tony de Zorzi", BAT, 78, 10),
                    createPlayer(SA_ID, "Prenelan Subrayen", BWL, 15, 74, SPIN),
                    createPlayer(SA_ID, "Migael Pretorius", BWL, 15, 76, PACE)
                )
            ),
            Team(
                id = NZ_ID, name = "New Zealand", countryCode = "NZ",
                battingRating = 87, bowlingRating = 88, fieldingRating = 94,
                players = listOf(
                    createPlayer(NZ_ID, "Finn Allen", BAT, 85, 10),
                    createPlayer(NZ_ID, "Devon Conway", WK, 88, 10),
                    createPlayer(NZ_ID, "Kane Williamson", BAT, 90, 20),
                    createPlayer(NZ_ID, "Daryl Mitchell", AR, 86, 60, PACE),
                    // Glenn Phillips is one of the most spectacular
                    // fielders in world cricket right now — elite-tier,
                    // not just "decent for an all-rounder".
                    createPlayer(NZ_ID, "Glenn Phillips", AR, 84, 55, SPIN, 96),
                    createPlayer(NZ_ID, "Mark Chapman", BAT, 80, 10),
                    createPlayer(NZ_ID, "Mitchell Santner", AR, 75, 84, SPIN),
                    createPlayer(NZ_ID, "Tim Southee", BWL, 40, 86, PACE),
                    createPlayer(NZ_ID, "Trent Boult", BWL, 35, 91, PACE),
                    createPlayer(NZ_ID, "Lockie Ferguson", BWL, 25, 89, PACE),
                    createPlayer(NZ_ID, "Ish Sodhi", BWL, 20, 83, SPIN),
                    createPlayer(NZ_ID, "Tom Latham", WK, 83, 10),
                    createPlayer(NZ_ID, "Will Young", BAT, 78, 10),
                    createPlayer(NZ_ID, "Rachin Ravindra", AR, 85, 60, SPIN),
                    createPlayer(NZ_ID, "Michael Bracewell", AR, 76, 70, SPIN),
                    createPlayer(NZ_ID, "James Neesham", AR, 78, 75, PACE),
                    createPlayer(NZ_ID, "Tim Robinson", WK, 74, 10),
                    createPlayer(NZ_ID, "Matt Henry", BWL, 25, 87, PACE),
                    createPlayer(NZ_ID, "Kyle Jamieson", BWL, 30, 85, PACE),
                    createPlayer(NZ_ID, "Adam Milne", BWL, 20, 84, PACE),
                    createPlayer(NZ_ID, "Ben Sears", BWL, 15, 80, PACE),
                    createPlayer(NZ_ID, "Jacob Duffy", BWL, 15, 78, PACE),
                    createPlayer(NZ_ID, "William O'Rourke", BWL, 15, 82, PACE),
                    createPlayer(NZ_ID, "Zak Foulkes", AR, 68, 74, PACE),
                    createPlayer(NZ_ID, "Chad Bowes", BAT, 74, 10),
                    createPlayer(NZ_ID, "Josh Clarkson", AR, 70, 68, PACE),
                    createPlayer(NZ_ID, "Dean Foxcroft", AR, 68, 66, SPIN),
                    createPlayer(NZ_ID, "Ajaz Patel", BWL, 20, 82, SPIN),
                    createPlayer(NZ_ID, "Cole McConchie", AR, 66, 68, SPIN),
                    createPlayer(NZ_ID, "Bevon Jacobs", BAT, 72, 10)
                )
            ),
            Team(
                id = SL_ID, name = "Sri Lanka", countryCode = "SL",
                battingRating = 80, bowlingRating = 82, fieldingRating = 80,
                players = listOf(
                    createPlayer(SL_ID, "Pathum Nissanka", BAT, 84, 10),
                    createPlayer(SL_ID, "Kusal Mendis", WK, 82, 10),
                    createPlayer(SL_ID, "Kamindu Mendis", BAT, 80, 40),
                    createPlayer(SL_ID, "Charith Asalanka", BAT, 81, 30),
                    createPlayer(SL_ID, "Angelo Mathews", AR, 78, 65, PACE),
                    createPlayer(SL_ID, "Dasun Shanaka", AR, 76, 70, PACE),
                    createPlayer(SL_ID, "Wanindu Hasaranga", AR, 75, 88, SPIN),
                    createPlayer(SL_ID, "Maheesh Theekshana", BWL, 30, 85, SPIN),
                    createPlayer(SL_ID, "Matheesha Pathirana", BWL, 15, 87, PACE),
                    createPlayer(SL_ID, "Nuwan Thushara", BWL, 15, 82, PACE),
                    createPlayer(SL_ID, "Dilshan Madushanka", BWL, 20, 84, PACE),
                    createPlayer(SL_ID, "Avishka Fernando", BAT, 78, 10),
                    createPlayer(SL_ID, "Kusal Perera", WK, 80, 10),
                    createPlayer(SL_ID, "Sadeera Samarawickrama", WK, 78, 10),
                    createPlayer(SL_ID, "Janith Liyanage", BAT, 72, 20),
                    createPlayer(SL_ID, "Dhananjaya de Silva", AR, 78, 65, SPIN),
                    createPlayer(SL_ID, "Dunith Wellalage", AR, 70, 76, SPIN),
                    createPlayer(SL_ID, "Chamika Karunaratne", AR, 72, 74, PACE),
                    createPlayer(SL_ID, "Kamil Mishara", WK, 70, 10),
                    createPlayer(SL_ID, "Pramod Madushan", BWL, 15, 80, PACE),
                    createPlayer(SL_ID, "Asitha Fernando", BWL, 20, 82, PACE),
                    createPlayer(SL_ID, "Lahiru Kumara", BWL, 20, 84, PACE),
                    createPlayer(SL_ID, "Jeffrey Vandersay", BWL, 15, 78, SPIN),
                    createPlayer(SL_ID, "Sahan Arachchige", AR, 68, 65, PACE),
                    createPlayer(SL_ID, "Lahiru Udara", BAT, 68, 10),
                    createPlayer(SL_ID, "Bhanuka Rajapaksa", BAT, 76, 10),
                    createPlayer(SL_ID, "Isuru Udana", AR, 65, 74, PACE),
                    createPlayer(SL_ID, "Ramesh Mendis", BWL, 20, 76, SPIN),
                    createPlayer(SL_ID, "Kasun Rajitha", BWL, 20, 80, PACE),
                    createPlayer(SL_ID, "Nuwanidu Fernando", BAT, 66, 10)
                )
            ),
            Team(
                id = WI_ID, name = "West Indies", countryCode = "WI",
                battingRating = 85, bowlingRating = 83, fieldingRating = 82,
                players = listOf(
                    createPlayer(WI_ID, "Brandon King", BAT, 83, 10),
                    createPlayer(WI_ID, "Johnson Charles", BAT, 81, 10),
                    createPlayer(WI_ID, "Nicholas Pooran", WK, 89, 10),
                    createPlayer(WI_ID, "Rovman Powell", BAT, 84, 20),
                    createPlayer(WI_ID, "Andre Russell", AR, 86, 82, PACE, 89),
                    createPlayer(WI_ID, "Sherfane Rutherford", BAT, 80, 30),
                    createPlayer(WI_ID, "Romario Shepherd", AR, 75, 78, PACE),
                    createPlayer(WI_ID, "Akeal Hosein", BWL, 40, 84, SPIN),
                    createPlayer(WI_ID, "Alzarri Joseph", BWL, 35, 86, PACE),
                    createPlayer(WI_ID, "Gudakesh Motie", BWL, 25, 82, SPIN),
                    createPlayer(WI_ID, "Obed McCoy", BWL, 15, 80, PACE),
                    createPlayer(WI_ID, "Shai Hope", WK, 85, 10),
                    createPlayer(WI_ID, "Kyle Mayers", AR, 80, 60, PACE),
                    createPlayer(WI_ID, "Evin Lewis", BAT, 78, 10),
                    createPlayer(WI_ID, "Shimron Hetmyer", BAT, 82, 10),
                    createPlayer(WI_ID, "Roston Chase", AR, 76, 65, SPIN),
                    createPlayer(WI_ID, "Jason Holder", AR, 78, 80, PACE),
                    createPlayer(WI_ID, "Keacy Carty", BAT, 74, 10),
                    createPlayer(WI_ID, "Justin Greaves", AR, 72, 72, PACE),
                    createPlayer(WI_ID, "Odean Smith", AR, 68, 72, PACE),
                    createPlayer(WI_ID, "Matthew Forde", BWL, 20, 78, PACE),
                    createPlayer(WI_ID, "Jayden Seales", BWL, 20, 82, PACE),
                    createPlayer(WI_ID, "Shamar Joseph", BWL, 25, 86, PACE),
                    createPlayer(WI_ID, "Yannic Cariah", BWL, 15, 76, SPIN),
                    createPlayer(WI_ID, "Akeem Jordan", BWL, 15, 74, PACE),
                    createPlayer(WI_ID, "Kevin Sinclair", AR, 68, 72, SPIN),
                    createPlayer(WI_ID, "Amir Jangoo", WK, 72, 10),
                    createPlayer(WI_ID, "Kirk McKenzie", BAT, 72, 10),
                    createPlayer(WI_ID, "Khary Pierre", BWL, 15, 74, SPIN),
                    createPlayer(WI_ID, "Alick Athanaze", BAT, 74, 20)
                )
            ),
            Team(
                id = BAN_ID, name = "Bangladesh", countryCode = "BAN",
                battingRating = 78, bowlingRating = 80, fieldingRating = 75,
                players = listOf(
                    createPlayer(BAN_ID, "Tanzid Hasan", BAT, 75, 10),
                    createPlayer(BAN_ID, "Litton Das", WK, 80, 10),
                    createPlayer(BAN_ID, "Najmul Hossain Shanto", BAT, 78, 20),
                    createPlayer(BAN_ID, "Towhid Hridoy", BAT, 79, 10),
                    createPlayer(BAN_ID, "Shakib Al Hasan", AR, 85, 86, SPIN),
                    createPlayer(BAN_ID, "Mahmudullah", BAT, 81, 40),
                    createPlayer(BAN_ID, "Rishad Hossain", BWL, 40, 78, SPIN),
                    createPlayer(BAN_ID, "Taskin Ahmed", BWL, 30, 84, PACE),
                    createPlayer(BAN_ID, "Mustafizur Rahman", BWL, 20, 86, PACE),
                    createPlayer(BAN_ID, "Tanzim Hasan Sakib", BWL, 25, 80, PACE),
                    createPlayer(BAN_ID, "Shoriful Islam", BWL, 20, 82, PACE),
                    createPlayer(BAN_ID, "Soumya Sarkar", BAT, 76, 30),
                    createPlayer(BAN_ID, "Mushfiqur Rahim", WK, 82, 10),
                    createPlayer(BAN_ID, "Jaker Ali", WK, 72, 10),
                    createPlayer(BAN_ID, "Mahmudul Hasan Joy", BAT, 72, 10),
                    createPlayer(BAN_ID, "Parvez Hossain Emon", BAT, 74, 10),
                    createPlayer(BAN_ID, "Afif Hossain", AR, 74, 55, SPIN),
                    createPlayer(BAN_ID, "Mehidy Hasan Miraz", AR, 72, 80, SPIN),
                    createPlayer(BAN_ID, "Nasum Ahmed", BWL, 20, 76, SPIN),
                    createPlayer(BAN_ID, "Shamim Hossain", BAT, 74, 20),
                    createPlayer(BAN_ID, "Hasan Mahmud", BWL, 25, 82, PACE),
                    createPlayer(BAN_ID, "Al-Amin Hossain", BWL, 20, 76, PACE),
                    createPlayer(BAN_ID, "Ebadot Hossain", BWL, 15, 78, PACE),
                    createPlayer(BAN_ID, "Nazmul Hossain", BWL, 15, 74, PACE),
                    createPlayer(BAN_ID, "Rony Talukdar", BAT, 74, 10),
                    createPlayer(BAN_ID, "Zakir Hasan", BAT, 70, 10),
                    createPlayer(BAN_ID, "Taijul Islam", BWL, 20, 80, SPIN),
                    createPlayer(BAN_ID, "Nurul Hasan Sohan", WK, 70, 10),
                    createPlayer(BAN_ID, "Tanvir Islam", BWL, 15, 76, SPIN),
                    createPlayer(BAN_ID, "Rakibul Hasan", AR, 66, 70, SPIN)
                )
            ),
            Team(
                id = AFG_ID, name = "Afghanistan", countryCode = "AFG",
                battingRating = 76, bowlingRating = 88, fieldingRating = 78,
                players = listOf(
                    createPlayer(AFG_ID, "Rahmanullah Gurbaz", WK, 84, 10),
                    createPlayer(AFG_ID, "Ibrahim Zadran", BAT, 82, 10),
                    createPlayer(AFG_ID, "Gulbadin Naib", AR, 75, 75, PACE),
                    createPlayer(AFG_ID, "Azmatullah Omarzai", AR, 78, 78, PACE),
                    createPlayer(AFG_ID, "Mohammad Nabi", AR, 76, 82, SPIN),
                    createPlayer(AFG_ID, "Najibullah Zadran", BAT, 79, 10),
                    createPlayer(AFG_ID, "Rashid Khan", AR, 70, 94, SPIN),
                    createPlayer(AFG_ID, "Karim Janat", AR, 65, 70, PACE),
                    createPlayer(AFG_ID, "Noor Ahmad", BWL, 15, 85, SPIN),
                    createPlayer(AFG_ID, "Naveen-ul-Haq", BWL, 20, 84, PACE),
                    createPlayer(AFG_ID, "Fazalhaq Farooqi", BWL, 15, 86, PACE),
                    createPlayer(AFG_ID, "Sediqullah Atal", BAT, 74, 10),
                    createPlayer(AFG_ID, "Rahmat Shah", BAT, 78, 10),
                    createPlayer(AFG_ID, "Hashmatullah Shahidi", BAT, 80, 10),
                    createPlayer(AFG_ID, "Riaz Hassan", BAT, 70, 10),
                    createPlayer(AFG_ID, "Darwish Rasooli", BAT, 68, 10),
                    createPlayer(AFG_ID, "Ikram Alikhil", WK, 72, 10),
                    createPlayer(AFG_ID, "Mohammad Ishaq", WK, 68, 10),
                    createPlayer(AFG_ID, "Sharafuddin Ashraf", AR, 65, 72, SPIN),
                    createPlayer(AFG_ID, "Nangeyalia Kharote", AR, 62, 66, SPIN),
                    createPlayer(AFG_ID, "Qais Ahmad", BWL, 15, 82, SPIN),
                    createPlayer(AFG_ID, "Zia-ur-Rehman", BWL, 15, 78, PACE),
                    createPlayer(AFG_ID, "Yamin Ahmadzai", BWL, 20, 76, PACE),
                    createPlayer(AFG_ID, "Abdul Rahman", BWL, 15, 78, PACE),
                    createPlayer(AFG_ID, "Wafadar Momand", BWL, 15, 74, PACE),
                    createPlayer(AFG_ID, "Mujeeb Ur Rahman", BWL, 15, 88, SPIN),
                    createPlayer(AFG_ID, "Allah Mohammad Ghazanfar", BWL, 15, 84, SPIN),
                    createPlayer(AFG_ID, "Bahir Shah", BAT, 66, 10),
                    createPlayer(AFG_ID, "Shahidullah Kamal", BAT, 64, 10),
                    createPlayer(AFG_ID, "Fareed Ahmad", BWL, 15, 76, PACE)
                )
            ),
            Team(
                id = IRE_ID, name = "Ireland", countryCode = "IRE",
                battingRating = 74, bowlingRating = 72, fieldingRating = 75,
                players = listOf(
                    createPlayer(IRE_ID, "Paul Stirling", BAT, 82, 40),
                    createPlayer(IRE_ID, "Andrew Balbirnie", BAT, 78, 10),
                    createPlayer(IRE_ID, "Lorcan Tucker", WK, 76, 10),
                    createPlayer(IRE_ID, "Harry Tector", BAT, 80, 30),
                    createPlayer(IRE_ID, "Curtis Campher", AR, 75, 72, PACE),
                    createPlayer(IRE_ID, "George Dockrell", AR, 70, 70, SPIN),
                    createPlayer(IRE_ID, "Gareth Delany", AR, 68, 68, SPIN),
                    createPlayer(IRE_ID, "Mark Adair", BWL, 50, 78, PACE),
                    createPlayer(IRE_ID, "Barry McCarthy", BWL, 40, 76, PACE),
                    createPlayer(IRE_ID, "Josh Little", BWL, 20, 82, PACE),
                    createPlayer(IRE_ID, "Craig Young", BWL, 25, 75, PACE),
                    createPlayer(IRE_ID, "Ross Adair", BAT, 70, 10),
                    createPlayer(IRE_ID, "Neil Rock", WK, 66, 10),
                    createPlayer(IRE_ID, "Fionn Hand", BWL, 15, 74, PACE),
                    createPlayer(IRE_ID, "Ben White", BWL, 15, 72, SPIN),
                    createPlayer(IRE_ID, "Graham Hume", BWL, 20, 76, PACE),
                    createPlayer(IRE_ID, "Matthew Humphreys", BWL, 15, 70, SPIN)
                )
            ),
            Team(
                id = ZIM_ID, name = "Zimbabwe", countryCode = "ZIM",
                battingRating = 70, bowlingRating = 70, fieldingRating = 70,
                players = listOf(
                    createPlayer(ZIM_ID, "Joylord Gumbie", WK, 68, 10),
                    createPlayer(ZIM_ID, "Wessly Madhevere", AR, 72, 65, SPIN),
                    createPlayer(ZIM_ID, "Brian Bennett", BAT, 70, 40),
                    createPlayer(ZIM_ID, "Sikandar Raza", AR, 85, 82, SPIN),
                    createPlayer(ZIM_ID, "Sean Williams", AR, 80, 75, SPIN),
                    createPlayer(ZIM_ID, "Ryan Burl", AR, 76, 72, SPIN),
                    createPlayer(ZIM_ID, "Clive Madande", BAT, 68, 10),
                    createPlayer(ZIM_ID, "Luke Jongwe", BWL, 50, 70, PACE),
                    createPlayer(ZIM_ID, "Wellington Masakadza", BWL, 40, 68, PACE),
                    createPlayer(ZIM_ID, "Richard Ngarava", BWL, 30, 78, PACE),
                    createPlayer(ZIM_ID, "Blessing Muzarabani", BWL, 25, 80, PACE),
                    createPlayer(ZIM_ID, "Tadiwanashe Marumani", BAT, 70, 10),
                    createPlayer(ZIM_ID, "Dion Myers", WK, 66, 10),
                    createPlayer(ZIM_ID, "Tony Munyonga", BAT, 68, 10),
                    createPlayer(ZIM_ID, "Tashinga Musekiwa", WK, 64, 10),
                    createPlayer(ZIM_ID, "Trevor Gwandu", BWL, 15, 72, SPIN),
                    createPlayer(ZIM_ID, "Newman Nyamhuri", BWL, 15, 74, PACE)
                )
            ),
            Team(
                id = NED_ID, name = "Netherlands", countryCode = "NED",
                battingRating = 72, bowlingRating = 74, fieldingRating = 76,
                players = listOf(
                    createPlayer(NED_ID, "Max O'Dowd", BAT, 75, 10),
                    createPlayer(NED_ID, "Michael Levitt", BAT, 72, 10),
                    createPlayer(NED_ID, "Vikramjit Singh", BAT, 74, 30),
                    createPlayer(NED_ID, "Scott Edwards", WK, 78, 10),
                    createPlayer(NED_ID, "Sybrand Engelbrecht", BAT, 76, 20),
                    createPlayer(NED_ID, "Bas de Leede", AR, 78, 78, PACE),
                    createPlayer(NED_ID, "Logan van Beek", AR, 65, 76, PACE),
                    createPlayer(NED_ID, "Tim Pringle", BWL, 40, 70, SPIN),
                    createPlayer(NED_ID, "Aryan Dutt", BWL, 30, 74, SPIN),
                    createPlayer(NED_ID, "Paul van Meekeren", BWL, 25, 76, PACE),
                    createPlayer(NED_ID, "Vivian Kingma", BWL, 20, 72, PACE),
                    createPlayer(NED_ID, "Wesley Barresi", WK, 70, 10),
                    createPlayer(NED_ID, "Teja Nidamanuru", BAT, 74, 10),
                    createPlayer(NED_ID, "Saqib Zulfiqar", BAT, 68, 15),
                    createPlayer(NED_ID, "Noah Croes", BWL, 15, 72, PACE),
                    createPlayer(NED_ID, "Kyle Klein", AR, 65, 68, PACE),
                    createPlayer(NED_ID, "Fred Klaassen", BWL, 20, 76, PACE)
                )
            ),
            Team(
                id = SCO_ID, name = "Scotland", countryCode = "SCO",
                battingRating = 73, bowlingRating = 71, fieldingRating = 72,
                players = listOf(
                    createPlayer(SCO_ID, "George Munsey", BAT, 78, 10),
                    createPlayer(SCO_ID, "Michael Jones", BAT, 74, 10),
                    createPlayer(SCO_ID, "Brandon McMullen", AR, 76, 65, PACE),
                    createPlayer(SCO_ID, "Richie Berrington", BAT, 77, 30),
                    createPlayer(SCO_ID, "Matthew Cross", WK, 75, 10),
                    createPlayer(SCO_ID, "Michael Leask", AR, 72, 70, SPIN),
                    createPlayer(SCO_ID, "Chris Greaves", AR, 68, 68, SPIN),
                    createPlayer(SCO_ID, "Mark Watt", BWL, 45, 76, SPIN),
                    createPlayer(SCO_ID, "Chris Sole", BWL, 20, 78, PACE),
                    createPlayer(SCO_ID, "Safyaan Sharif", BWL, 30, 74, PACE),
                    createPlayer(SCO_ID, "Brad Wheal", BWL, 25, 75, PACE),
                    createPlayer(SCO_ID, "Oli Hairs", BAT, 68, 10),
                    createPlayer(SCO_ID, "Charlie Tear", BAT, 68, 10),
                    createPlayer(SCO_ID, "Jack Jarvis", BWL, 15, 72, PACE),
                    createPlayer(SCO_ID, "Bradley Currie", BWL, 15, 74, PACE),
                    createPlayer(SCO_ID, "Alasdair Evans", BWL, 15, 76, PACE),
                    createPlayer(SCO_ID, "Hamza Tahir", BWL, 15, 72, SPIN)
                )
            ),
            Team(
                id = NEP_ID, name = "Nepal", countryCode = "NEP",
                battingRating = 65, bowlingRating = 68, fieldingRating = 65,
                players = listOf(
                    createPlayer(NEP_ID, "Kushal Bhurtel", BAT, 70, 30),
                    createPlayer(NEP_ID, "Aasif Sheikh", WK, 72, 10),
                    createPlayer(NEP_ID, "Rohit Paudel", BAT, 74, 40),
                    createPlayer(NEP_ID, "Anil Sah", BAT, 68, 10),
                    createPlayer(NEP_ID, "Dipendra Singh Airee", AR, 75, 70, PACE),
                    createPlayer(NEP_ID, "Kushal Malla", AR, 72, 65, PACE),
                    createPlayer(NEP_ID, "Gulshan Jha", AR, 68, 60, SPIN),
                    createPlayer(NEP_ID, "Sompal Kami", BWL, 50, 72, PACE),
                    createPlayer(NEP_ID, "Karan KC", BWL, 45, 70, PACE),
                    createPlayer(NEP_ID, "Sandeep Lamichhane", BWL, 30, 80, SPIN),
                    createPlayer(NEP_ID, "Abinash Bohara", BWL, 20, 68, PACE),
                    createPlayer(NEP_ID, "Bhim Sharki", BAT, 66, 10),
                    createPlayer(NEP_ID, "Aarif Sheikh", BAT, 68, 10),
                    createPlayer(NEP_ID, "Dev Khanal", WK, 64, 10),
                    createPlayer(NEP_ID, "Sundeep Jora", AR, 66, 62, PACE),
                    createPlayer(NEP_ID, "Lalit Rajbanshi", BWL, 15, 74, SPIN),
                    createPlayer(NEP_ID, "Mousom Dhakal", BWL, 15, 72, PACE)
                )
            ),
            Team(
                id = UAE_ID, name = "UAE", countryCode = "UAE",
                battingRating = 64, bowlingRating = 65, fieldingRating = 64,
                players = listOf(
                    createPlayer(UAE_ID, "Muhammad Waseem", BAT, 76, 20),
                    createPlayer(UAE_ID, "Aryan Lakra", BAT, 68, 40),
                    createPlayer(UAE_ID, "Vriitya Aravind", WK, 72, 10),
                    createPlayer(UAE_ID, "Rohan Mustafa", AR, 70, 68, SPIN),
                    createPlayer(UAE_ID, "Basil Hameed", AR, 68, 65, PACE),
                    createPlayer(UAE_ID, "Aayan Afzal Khan", AR, 65, 70, SPIN),
                    createPlayer(UAE_ID, "Ali Naseer", AR, 66, 66, PACE),
                    createPlayer(UAE_ID, "Zahoor Khan", BWL, 20, 74, PACE),
                    createPlayer(UAE_ID, "Junaid Siddique", BWL, 25, 72, PACE),
                    createPlayer(UAE_ID, "Muhammad Jawadullah", BWL, 20, 70, SPIN),
                    createPlayer(UAE_ID, "Akif Raja", BWL, 15, 68, PACE),
                    createPlayer(UAE_ID, "Alishan Sharafu", BAT, 74, 10),
                    createPlayer(UAE_ID, "Asif Khan", WK, 68, 10),
                    createPlayer(UAE_ID, "Rahul Bhatia", AR, 66, 62, SPIN),
                    createPlayer(UAE_ID, "Karthik Meiyappan", BWL, 15, 74, SPIN),
                    createPlayer(UAE_ID, "Simranjeet Singh", BWL, 15, 76, PACE),
                    createPlayer(UAE_ID, "Sanchit Sharma", AR, 62, 66, PACE)
                )
            ),
            Team(
                id = OMAN_ID, name = "Oman", countryCode = "OMA",
                battingRating = 66, bowlingRating = 67, fieldingRating = 65,
                players = listOf(
                    createPlayer(OMAN_ID, "Kashyap Prajapati", BAT, 70, 10),
                    createPlayer(OMAN_ID, "Naseem Khushi", WK, 68, 10),
                    createPlayer(OMAN_ID, "Aqib Ilyas", AR, 74, 70, PACE),
                    createPlayer(OMAN_ID, "Zeeshan Maqsood", AR, 76, 72, SPIN),
                    createPlayer(OMAN_ID, "Khalid Kail", BAT, 66, 10),
                    createPlayer(OMAN_ID, "Ayan Khan", AR, 68, 65, SPIN),
                    createPlayer(OMAN_ID, "Mehran Khan", AR, 60, 68, PACE),
                    createPlayer(OMAN_ID, "Rafiullah", BWL, 40, 66, PACE),
                    createPlayer(OMAN_ID, "Shakeel Ahmed", BWL, 30, 65, PACE),
                    createPlayer(OMAN_ID, "Kaleemullah", BWL, 25, 68, SPIN),
                    createPlayer(OMAN_ID, "Bilal Khan", BWL, 20, 75, PACE),
                    createPlayer(OMAN_ID, "Pratik Athavale", BAT, 66, 10),
                    createPlayer(OMAN_ID, "Vinayak Shukla", BAT, 64, 10),
                    createPlayer(OMAN_ID, "Wasim Ali", WK, 62, 10),
                    createPlayer(OMAN_ID, "Fayyaz Butt", BWL, 15, 70, SPIN),
                    createPlayer(OMAN_ID, "Nadeem Khan", AR, 60, 64, PACE),
                    createPlayer(OMAN_ID, "Samay Shrivastava", AR, 62, 62, SPIN)
                )
            ),
            Team(
                id = USA_ID, name = "USA", countryCode = "USA",
                battingRating = 70, bowlingRating = 72, fieldingRating = 70,
                players = listOf(
                    createPlayer(USA_ID, "Steven Taylor", BAT, 72, 40),
                    createPlayer(USA_ID, "Monank Patel", WK, 75, 10),
                    createPlayer(USA_ID, "Andries Gous", BAT, 78, 10),
                    createPlayer(USA_ID, "Aaron Jones", BAT, 80, 20),
                    createPlayer(USA_ID, "Nitish Kumar", BAT, 70, 30),
                    createPlayer(USA_ID, "Corey Anderson", AR, 76, 70, PACE),
                    createPlayer(USA_ID, "Harmeet Singh", AR, 65, 72, SPIN),
                    createPlayer(USA_ID, "Shadley van Schalkwyk", BWL, 40, 70, PACE),
                    createPlayer(USA_ID, "Jasdeep Singh", BWL, 30, 68, PACE),
                    createPlayer(USA_ID, "Ali Khan", BWL, 25, 78, PACE),
                    createPlayer(USA_ID, "Saurabh Netravalkar", BWL, 35, 82, PACE),
                    createPlayer(USA_ID, "Milind Kumar", BAT, 70, 10),
                    createPlayer(USA_ID, "Shayan Jahangir", WK, 68, 10),
                    createPlayer(USA_ID, "Kyle Phillip", BWL, 15, 74, PACE),
                    createPlayer(USA_ID, "Nosthush Kenjige", BWL, 15, 72, SPIN),
                    createPlayer(USA_ID, "Yasir Mohammad", BAT, 66, 10),
                    createPlayer(USA_ID, "Jessy Singh", BWL, 15, 76, PACE)
                )
            )
        )
    }

    // --- Exports ---

    fun getAllTeams(): List<Team> = TEAMS_DATA

    fun getTeamById(id: String): Team? = TEAMS_DATA.find { it.id == id }

    fun getPlayersByTeamId(teamId: String): List<Player> = getTeamById(teamId)?.players ?: emptyList()

    /**
     * Builds the 11-player Team object that actually takes the field,
     * from a full squad plus the chosen playing XI. Everything else
     * about the team (id/name/countryCode/the three overall ratings)
     * carries over unchanged from the full squad.
     */
    fun buildMatchSquad(
        fullTeam: Team,
        selectedPlayerIds: List<String>,
        captainId: String,
        viceCaptainId: String,
        wicketkeeperId: String
    ): Team {
        val playerById = fullTeam.players.associateBy { it.id }
        val players = selectedPlayerIds.mapNotNull { playerById[it] }
        return fullTeam.copy(
            players = players,
            captainId = captainId,
            viceCaptainId = viceCaptainId,
            wicketkeeperId = wicketkeeperId
        )
    }

    data class AutoXIResult(
        val players: List<Player>,
        val captainId: String,
        val viceCaptainId: String,
        val wicketkeeperId: String
    )

    /**
     * Auto-picks a reasonable playing XI for an AI-controlled team.
     * Takes the 11 highest-value players by a simple batting/bowling/
     * fielding blend, then makes sure at least one specialist
     * wicketkeeper (if the squad has one at all) ends up in the XI,
     * swapping out the weakest of the initial 11 if necessary rather
     * than leaving the side without a keeper.
     */
    fun autoSelectPlayingXI(team: Team): AutoXIResult {
        fun overallValue(p: Player): Double = maxOf(p.battingRating, p.bowlingRating) + p.fieldingRating * 0.1

        val sortedByValue = team.players.sortedByDescending { overallValue(it) }
        val xi = sortedByValue.take(11).toMutableList()

        val wicketkeeperId: String
        val keeperAlreadyInXi = xi.find { it.role == PlayerRole.WICKETKEEPER }
        if (keeperAlreadyInXi != null) {
            wicketkeeperId = keeperAlreadyInXi.id
        } else {
            val bestKeeperInSquad = sortedByValue.find { it.role == PlayerRole.WICKETKEEPER }
            if (bestKeeperInSquad != null) {
                xi[xi.size - 1] = bestKeeperInSquad
                wicketkeeperId = bestKeeperInSquad.id
            } else {
                // No specialist keeper anywhere in the squad — shouldn't
                // happen (every team above has at least one) — fall back
                // to the top pick.
                wicketkeeperId = xi[0].id
            }
        }

        val captainId = xi[0].id
        val viceCaptainId = (xi.find { it.id != captainId } ?: xi[0]).id

        return AutoXIResult(xi, captainId, viceCaptainId, wicketkeeperId)
    }
}
