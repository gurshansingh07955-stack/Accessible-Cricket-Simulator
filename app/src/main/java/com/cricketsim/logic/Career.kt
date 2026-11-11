package com.cricketsim.logic

/**
 * One saved career of the My Player mode (Home > My Player). Part one holds only what the player chose while starting it;
 * the club's squad (players made up at random, with their own batting, bowling and fielding ratings), the matches and the
 * progress come in later parts.
 *
 * @param gender "Male" for now (the game has no women's cricket yet)
 * @param state the state, county, province or region the player chose (the word the screens use is "state")
 * @param domesticTeams the game's domestic teams of that state (one, or several: Maharashtra, Mumbai and Vidarbha)
 * @param club the district club
 */
data class CareerProfile(
    val id: String,
    val firstName: String,
    val lastName: String,
    val age: Int,
    val gender: String,
    val jersey: Int,
    val country: String,
    val state: String,
    val domesticTeams: List<String>,
    val club: String,
    val createdAt: Long,
    // Chosen after the club (part two). Empty on a career saved by part one.
    val role: String = "",
    val battingHand: String = "",
    /** Empty for a batsman or wicketkeeper-batsman, who does not bowl. */
    val bowlingHand: String = "",
    val bowlingStyle: String = "",
    val battingPosition: String = "",
    val bowlingPhase: String = "",
    /** Whether a profile photo is saved for this career (files/career_photos/<id>.png). */
    val hasPhoto: Boolean = false,
    // Part three: the choices after the playing style, the starting ratings and the random club teams of the state.
    /** The batting number chosen inside the batting position (1 to 11); 0 on an older career. */
    val battingNumber: Int = 0,
    /** The preferred fielding position; "Wicketkeeper" for a wicketkeeper-batsman. */
    val fieldingPosition: String = "",
    /** Very easy, Easy, Medium, Hard or Legend: how hard selection is (see [CareerWorld.selectionFactor]). */
    val difficulty: String = "",
    /** The player's own ratings, 1 to 95 like the game's players; they start modest and grow as he plays. */
    val batting: Int = 0,
    val bowling: Int = 0,
    val fielding: Int = 0,
    /** The district clubs of the state with their made-up squads; null on a career made before they existed. */
    val clubTeams: List<CareerClub>? = null,
    // Part five: the career's progress.
    /** The impact points of the player's last matches in the XI, oldest first (at most five); the selectors read his form from them. */
    val form: List<Double>? = null,
    /** Matches played in the XI. */
    val matchesPlayed: Int = 0,
    /** The ratings [batting, bowling, fielding] the career started with; null on an older career. */
    val startRatings: List<Int>? = null,
    // Part six: leadership. "" (a player), "Vice-captain" or "Captain" of his club; promoted for his form (see CareerLeadership).
    val leadership: String = "",
    /** Matches played in the XI when the player was last promoted. */
    val leadershipSince: Int = 0,
    // Part seven: the domestic level.
    /** "" or "District" = playing for a district club; "State" = called up to the state squad. */
    val level: String = "",
    /** The season he is in (1 = the first); 0 on an older career means the first. */
    val season: Int = 0,
    /** The competitions of the current season (their file keys); null = the three club leagues. */
    val seasonKeys: List<String>? = null,
    /** Matches played for the state. */
    val stateMatches: Int = 0,
    // Part eight: the international level.
    /** Matches played for the national team. */
    val internationalMatches: Int = 0,
    /** The impact of his last five matches in each international format ("t20", "odi", "test"): the selectors judge a Test batsman by his Test form. */
    val formByFormat: Map<String, List<Double>>? = null,
    // Part ten: franchise leagues.
    /** The franchise offers on the table (null until he has asked for them). */
    val leagueOffers: List<LeagueOffer>? = null,
    /** The season the offers were made for. */
    val leagueOffersSeason: Int = 0,
    /** The season in which he settled his league (signed, or turned every offer down). */
    val leagueDecidedSeason: Int = 0,
    /** One sentence about this season's league, for the Home tab. */
    val leagueLine: String? = null,
    /** Matches played in franchise leagues. */
    val leagueMatches: Int = 0,
    // Part eleven: the record, the honours and the training focus.
    /** What he has done in each kind of cricket ("Club", "State", "Test", "ODI", "T20I", "League"). */
    val records: Map<String, CareerRecord>? = null,
    /** The honours he has won, oldest first. */
    val honours: List<String>? = null,
    /** "Batting", "Bowling" or "Fielding": the skill he works on (null = balanced). */
    val trainingFocus: String? = null,
    // Part twelve: fitness, ageing and retirement.
    /** Matches he still has to miss through injury (0 = fit). */
    val injuredMatches: Int = 0,
    /** What is wrong with him ("a hamstring strain"). */
    val injury: String? = null,
    /** Matches played this season: a tired player is hurt more easily. */
    val workload: Int = 0,
    /** True once he has retired: the career is over and only the summary is shown. */
    val retired: Boolean = false,
    /** The season in which he retired. */
    val retiredSeason: Int = 0,
    // Part thirteen: national captaincy and the format he specialises in.
    /** "Vice-captain" or "Captain" of his country (null = a player). */
    val nationalRole: String? = null,
    /** "Test" or "White-ball" (null = all formats). */
    val specialism: String? = null,
    // Part fifteen: money, the franchise he played for, and the papers.
    /** What he has earned in his career, in dollars. */
    val money: Long = 0L,
    /** What he has earned this season (match fees as he plays; contracts and prizes added when the season is settled). */
    val seasonEarnings: Long = 0L,
    /** What his league contract this season is worth (0 = none). */
    val leagueContract: Int = 0,
    /** The league and the franchise he last signed for: that franchise wants to keep him. */
    val lastLeagueId: String? = null,
    val lastLeagueTeamId: String? = null,
    /** What the papers have written about him (the last fifteen lines). */
    val media: List<String>? = null,
    // Part sixteen: international affiliation.
    /** The country whose national team he is aiming for when it is not his own (null = his own country). */
    val represents: String? = null,
    // Part sixteen (second version): moving to another country.
    /** A move to a district club abroad, planned for the start of the next season. */
    val pendingMove: MoveRequest? = null,
    /** The season he moved to his present country (0 = he never moved). */
    val movedSeason: Int = 0
) {
    val clubs: List<CareerClub> get() = clubTeams ?: emptyList()

    /** The club captain makes the team decisions in his matches: the XI, the toss, the bowlers, the field, the batting order. */
    val isCaptain: Boolean get() = (leadership == "Captain" && !isState && !isInternational) || (isInternational && nationalRole == "Captain")

    /** True once he has been called up to the state squad. */
    val isState: Boolean get() = level == "State"

    /** True once he has been picked for the national team ("International" level). */
    val isInternational: Boolean get() = level == "International"

    /** At the state squad or above it. */
    val atOrAboveState: Boolean get() = isState || isInternational

    /** The country of the national team he plays for or aims for. */
    val nation: String get() = represents ?: country

    /** One spoken line about the level he plays at. */
    fun levelLine(): String {
        val n = maxOf(1, season)
        if (isInternational) return "You play for the $nation national squad. Season $n, age $age. International matches: $internationalMatches."
        return if (isState) "You play for the ${domesticTeams.firstOrNull() ?: state} squad. Season $n, age $age."
        else "You play district club cricket for $club. Season $n, age $age."
    }

    /** One spoken line about his place in the club's leadership. */
    fun leadershipLine(): String = when (leadership) {
        "Captain" -> "You are the captain of $club."
        "Vice-captain" -> "You are the vice-captain of $club."
        else -> ""
    }

    /** The squad of the player's own district club. */
    val myClub: CareerClub? get() = clubs.find { it.name == club }

    val fullName: String get() = "$firstName $lastName"

    /** One spoken line about this career. */
    fun describe(): String = "$fullName, age $age. $country, $state. $club. Jersey number $jersey."

    /** One spoken line about the role and the playing style. */
    fun playingStyle(): String {
        if (role.isEmpty()) return "Role and playing style not chosen yet."
        val bowling = if (bowlingStyle.isNotEmpty()) {
            "Bowling: ${bowlingHand.lowercase()} ${bowlingStyle.lowercase()}, in the ${bowlingPhase.lowercase()}."
        } else {
            "Does not bowl."
        }
        return "Role: $role. Batting: ${battingHand.lowercase()}, ${battingPosition.lowercase()}. $bowling"
    }

    /** How far the ratings have grown since the start, and the matches played. */
    fun growthLine(): String {
        val s = startRatings ?: return ""
        fun d(now: Int, then: Int) = if (now >= then) "+${now - then}" else "${now - then}"
        return "Growth since the start: batting ${d(batting, s[0])}, bowling ${d(bowling, s[1])}, fielding ${d(fielding, s[2])}. Matches played in the XI: $matchesPlayed."
    }

    /** One spoken line about the batting number, the fielding position, the difficulty and the ratings. */
    fun plan(): String {
        if (difficulty.isEmpty()) return ""
        val number = if (battingNumber > 0) "Batting number $battingNumber. " else ""
        val field = if (fieldingPosition.isNotEmpty()) "Fielding: ${fieldingPosition.lowercase()}. " else ""
        return "$number${field}Difficulty: ${difficulty.lowercase()}. Ratings: batting $batting, bowling $bowling, fielding $fielding."
    }
}

/** One player of a district club, made up when the career starts. Ratings run 1 to 95 like the game's players. */
data class CareerPlayer(
    val name: String,
    val role: String,
    val battingHand: String,
    /** Empty for a player who does not bowl. */
    val bowlingHand: String,
    val bowlingStyle: String,
    val batting: Int,
    val bowling: Int,
    val fielding: Int,
    /** True for the player of the career. */
    val isUser: Boolean = false
) {
    fun describe(): String {
        val bowl = if (bowlingStyle.isNotEmpty()) "${bowlingHand.lowercase()} ${bowlingStyle.lowercase()}" else "does not bowl"
        return "$name${if (isUser) " (you)" else ""}, ${role.lowercase()}. ${battingHand.lowercase()}, $bowl. Batting $batting, bowling $bowling, fielding $fielding."
    }
}

/** A district club and its squad. */
data class CareerClub(val name: String, val players: List<CareerPlayer>)

/** One state (or county, province, region) with its domestic teams and the places of its district clubs. */
data class CareerState(val name: String, val domesticTeams: List<String>, val places: List<String>)

/**
 * The countries a career can be played in and their states. India lists its real states and union territories; the
 * other countries list exactly the teams that have domestic tournaments in the game. District clubs are made up from
 * real places of each state: they are only names, the players of a club are generated when a career starts.
 */
object CareerWorld {
    const val MAX_CAREERS = 5
    const val MIN_AGE = 16
    const val MAX_AGE = 30
    /** Ages up to this are recommended: the longest careers. */
    const val RECOMMENDED_MAX_AGE = 19
    const val MAX_JERSEY = 999
    const val MAX_NAME_LENGTH = 20

    /** One choice of a career menu: what it is called, how it is described, and (for a role) whether the player bowls. */
    data class Option(val label: String, val description: String, val bowls: Boolean = false)

    val ROLES = listOf(
        Option("Batsman", "Scores the runs. Does not bowl.", false),
        Option("Wicketkeeper-batsman", "Keeps wicket and bats. Does not bowl.", false),
        Option("Bowler", "Takes the wickets. Bats low in the order.", true),
        Option("Batting all-rounder", "Bats first and bowls some overs.", true),
        Option("Bowling all-rounder", "Bowls first and bats usefully.", true)
    )

    fun bowls(role: String?): Boolean = ROLES.find { it.label == role }?.bowls == true

    val BATTING_HANDS = listOf("Right-handed", "Left-handed")
    val BOWLING_HANDS = listOf("Right-arm", "Left-arm")

    /** The bowling styles of an arm: pace and medium, then every kind of spin. */
    fun bowlingStyles(hand: String?): List<Option> {
        val pace = listOf(
            Option("Fast", "Express pace, with the new ball or in a short burst."),
            Option("Medium-fast", "Brisk, a little below the fastest."),
            Option("Medium", "Steady medium pace, wicket to wicket.")
        )
        val spin = if (hand == "Left-arm") {
            listOf(
                Option("Orthodox spin", "Slow left-arm orthodox: turns the ball away from the right-hander's pads."),
                Option("Wrist spin", "Left-arm wrist spin, the chinaman: turns the other way.")
            )
        } else {
            listOf(
                Option("Off break", "Right-arm off spin: turns in to the right-hander."),
                Option("Leg break", "Right-arm leg spin, with the googly: turns away from the right-hander.")
            )
        }
        return pace + spin
    }

    val BATTING_POSITIONS = listOf(
        Option("Top order (1 to 3)", "Opens the innings or bats at three."),
        Option("Middle order (4 to 6)", "Builds the innings and finishes it."),
        Option("Low order (7 to 9)", "Bats late, with the tail."),
        Option("Lower order (10 and 11)", "The last two to bat.")
    )

    val BOWLING_PHASES = listOf(
        Option("Starting overs", "Bowls with the new ball, in the first overs."),
        Option("Middle overs", "Bowls through the middle of the innings."),
        Option("Death overs", "Bowls the last overs, when the batters attack.")
    )

    /** The batting numbers inside a chosen batting position: Top order 1 to 3, Middle order 4 to 6, Low order 7 to 9, Lower order 10 and 11. */
    fun battingNumbers(position: String?): List<Int> = when {
        position?.startsWith("Top") == true -> listOf(1, 2, 3)
        position?.startsWith("Middle") == true -> listOf(4, 5, 6)
        position?.startsWith("Low order") == true -> listOf(7, 8, 9)
        else -> listOf(10, 11)
    }

    fun numberDescription(n: Int): String = when (n) {
        1 -> "Opens the innings and faces the first ball."
        2 -> "Opens the innings, the second opener."
        3 -> "The first wicket-down batter."
        4 -> "The first of the middle order."
        5 -> "Builds the innings in the middle."
        6 -> "Finishes the innings."
        7 -> "The first of the tail, often a keeper or an all-rounder."
        8 -> "Bats with the tail."
        9 -> "Bats late, usually a bowler."
        10 -> "Bats at ten."
        else -> "The last man in."
    }

    /** The positions a fielder can prefer (a wicketkeeper-batsman keeps wicket and is not asked). */
    val FIELDING_POSITIONS = listOf(
        Option("First slip", "Close catching: edges off the fast bowlers."),
        Option("Gully", "Close catching: the fast, square catches."),
        Option("Point", "Saves the cut and the square drive, close to the bat."),
        Option("Cover", "The classic inner-ring position on the off side."),
        Option("Mid-off", "Straight on the off side, near the bowler."),
        Option("Mid-on", "Straight on the leg side, near the bowler."),
        Option("Mid-wicket", "Saves the flick and the pull on the leg side."),
        Option("Square leg", "Square on the leg side."),
        Option("Short leg", "Close catching on the leg side, brave and quick."),
        Option("Fine leg", "Behind square on the leg side, near the boundary."),
        Option("Third man", "Behind square on the off side, near the boundary."),
        Option("Deep cover", "On the boundary, covering the off-side drive."),
        Option("Long-off", "On the boundary, straight on the off side."),
        Option("Long-on", "On the boundary, straight on the leg side."),
        Option("Deep mid-wicket", "On the boundary, covering the big leg-side hits."),
        Option("Deep square leg", "On the boundary, square on the leg side.")
    )

    const val WICKETKEEPER = "Wicketkeeper"

    /** The career difficulties, easiest first. They decide how hard it is to be selected as the player moves up the levels. */
    val DIFFICULTIES = listOf(
        Option("Very easy", "Selection is very easy: the selectors pick you readily, even after a quiet spell."),
        Option("Easy", "Selection is easy: steady form keeps you in the team."),
        Option("Medium", "Selection is fair: you need regular good form."),
        Option("Hard", "Selection is hard: you need strong, consistent performances to move up."),
        Option("Legend", "Selection is very hard: only outstanding performances move you up the levels.")
    )

    /**
     * How demanding the selectors are, from 0.6 (Very easy) to 1.6 (Legend). The later parts of career mode (selection for the
     * district club, the state and the national side) will scale the form a player needs by this number. A dial.
     */
    fun selectionFactor(difficulty: String?): Double = when (difficulty) {
        "Very easy" -> 0.6
        "Easy" -> 0.8
        "Hard" -> 1.25
        "Legend" -> 1.6
        else -> 1.0
    }

    val COUNTRIES = listOf("India", "Pakistan", "Australia", "England", "New Zealand", "South Africa")

    private fun parse(text: String): List<CareerState> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val p = line.split("|")
        CareerState(p[0], p[1].split(","), p[2].split(";"))
    }

    private val DATA: Map<String, List<CareerState>> by lazy {
        mapOf(
        "India" to """
Andhra Pradesh|Andhra|Visakhapatnam;Vijayawada;Guntur;Tirupati
Arunachal Pradesh|Arunachal Pradesh|Itanagar;Naharlagun;Pasighat;Tawang
Assam|Assam|Guwahati;Dibrugarh;Silchar;Jorhat
Bihar|Bihar|Patna;Gaya;Bhagalpur;Muzaffarpur
Chhattisgarh|Chhattisgarh|Raipur;Bilaspur;Bhilai;Korba
Goa|Goa|Panaji;Margao;Vasco;Mapusa
Gujarat|Gujarat,Baroda,Saurashtra|Ahmedabad;Surat;Vadodara;Rajkot
Haryana|Haryana|Rohtak;Faridabad;Gurugram;Karnal
Himachal Pradesh|Himachal Pradesh|Shimla;Dharamshala;Mandi;Solan
Jharkhand|Jharkhand|Ranchi;Jamshedpur;Dhanbad;Bokaro
Karnataka|Karnataka|Bengaluru;Mysuru;Hubballi;Mangaluru
Kerala|Kerala|Thiruvananthapuram;Kochi;Kozhikode;Thrissur
Madhya Pradesh|Madhya Pradesh|Indore;Bhopal;Gwalior;Jabalpur
Maharashtra|Maharashtra,Mumbai,Vidarbha|Pune;Nagpur;Nashik;Kolhapur
Manipur|Manipur|Imphal;Thoubal;Bishnupur;Churachandpur
Meghalaya|Meghalaya|Shillong;Tura;Jowai;Nongpoh
Mizoram|Mizoram|Aizawl;Lunglei;Champhai;Serchhip
Nagaland|Nagaland|Kohima;Dimapur;Mokokchung;Tuensang
Odisha|Odisha|Bhubaneswar;Cuttack;Rourkela;Berhampur
Punjab|Punjab|Mohali;Ludhiana;Amritsar;Jalandhar
Rajasthan|Rajasthan|Jaipur;Jodhpur;Udaipur;Kota
Sikkim|Sikkim|Gangtok;Namchi;Gyalshing;Mangan
Tamil Nadu|Tamil Nadu|Chennai;Coimbatore;Madurai;Tiruchirappalli
Telangana|Hyderabad|Hyderabad;Warangal;Karimnagar;Nizamabad
Tripura|Tripura|Agartala;Dharmanagar;Udaipur;Kailashahar
Uttar Pradesh|Uttar Pradesh|Lucknow;Kanpur;Varanasi;Meerut
Uttarakhand|Uttarakhand|Dehradun;Haldwani;Haridwar;Roorkee
West Bengal|Bengal|Kolkata;Siliguri;Durgapur;Howrah
Delhi|Delhi|New Delhi;Dwarka;Rohini;Najafgarh
Jammu and Kashmir|Jammu & Kashmir|Srinagar;Jammu;Anantnag;Baramulla
Chandigarh|Chandigarh|Sector 16;Sector 32;Manimajra;Sector 17
Puducherry|Puducherry|Puducherry;Karaikal;Mahe;Yanam
""".let { parse(it) },
        "Pakistan" to """
Faisalabad|Faisalabad|Faisalabad;Jhang;Chiniot;Toba Tek Singh
FATA|FATA|Miranshah;Khar;Parachinar;Wana
Islamabad|Islamabad|Islamabad;Bhara Kahu;Tarnol;Golra
Karachi|Karachi Blues,Karachi Whites|Clifton;Gulshan-e-Iqbal;Malir;North Nazimabad
Lahore|Lahore Blues,Lahore Whites|Gulberg;Model Town;Johar Town;Shalimar
Multan|Multan|Multan;Khanewal;Vehari;Lodhran
Peshawar|Peshawar Blues,Peshawar Whites|Peshawar;Charsadda;Nowshera;Mardan
Sialkot|Sialkot Blues,Sialkot Whites|Sialkot;Daska;Pasrur;Gujranwala
Abbottabad|Abbottabad|Abbottabad;Mansehra;Haripur;Batagram
Azad Jammu and Kashmir|Azad Jammu and Kashmir|Muzaffarabad;Mirpur;Kotli;Bagh
Bahawalpur|Bahawalpur|Bahawalpur;Rahim Yar Khan;Bahawalnagar;Ahmadpur East
Dera Murad Jamali|Dera Murad Jamali|Dera Murad Jamali;Jacobabad;Sibi;Usta Muhammad
Hyderabad|Hyderabad|Hyderabad;Tando Allahyar;Badin;Mirpur Khas
Larkana|Larkana|Larkana;Dadu;Shikarpur;Sukkur
Quetta|Quetta|Quetta;Pishin;Chaman;Zhob
Rawalpindi|Rawalpindi|Rawalpindi;Attock;Chakwal;Jhelum
""".let { parse(it) },
        "Australia" to """
New South Wales|New South Wales|Parramatta;Penrith;Newcastle;Wollongong
Victoria|Victoria|Melbourne;Geelong;Ballarat;Bendigo
Queensland|Queensland|Brisbane;Gold Coast;Townsville;Cairns
South Australia|South Australia|Adelaide;Glenelg;Port Adelaide;Mount Gambier
Tasmania|Tasmania|Hobart;Launceston;Devonport;Burnie
Western Australia|Western Australia|Perth;Fremantle;Joondalup;Bunbury
""".let { parse(it) },
        "England" to """
Derbyshire|Derbyshire|Chesterfield;Derby;Buxton
Durham|Durham|Chester-le-Street;Sunderland;Stockton
Essex|Essex|Chelmsford;Colchester;Southend
Glamorgan|Glamorgan|Cardiff;Swansea;Newport
Gloucestershire|Gloucestershire|Bristol;Cheltenham;Gloucester
Hampshire|Hampshire|Southampton;Portsmouth;Winchester
Kent|Kent|Canterbury;Tunbridge Wells;Maidstone
Lancashire|Lancashire|Manchester;Blackpool;Preston
Leicestershire|Leicestershire|Leicester;Loughborough;Market Harborough
Middlesex|Middlesex|Ealing;Harrow;Enfield;Uxbridge
Northamptonshire|Northamptonshire|Northampton;Kettering;Wellingborough
Nottinghamshire|Nottinghamshire|Nottingham;Newark;Mansfield
Somerset|Somerset|Taunton;Bath;Bridgwater
Surrey|Surrey|Guildford;Croydon;Kingston
Sussex|Sussex|Hove;Brighton;Horsham
Warwickshire|Warwickshire|Birmingham;Coventry;Leamington
Worcestershire|Worcestershire|Worcester;Kidderminster;Malvern
Yorkshire|Yorkshire|Leeds;Sheffield;Scarborough;York
""".let { parse(it) },
        "New Zealand" to """
Auckland Aces|Auckland Aces|Takapuna;Manukau;Howick;Papatoetoe
Canterbury Kings|Canterbury Kings|Christchurch;Timaru;Rangiora;Ashburton
Central Stags|Central Stags|Palmerston North;Napier;Hastings;Whanganui
Northern Districts|Northern Districts|Hamilton;Tauranga;Whangarei;Rotorua
Otago Volts|Otago Volts|Dunedin;Queenstown;Invercargill;Oamaru
Wellington Firebirds|Wellington Firebirds|Lower Hutt;Porirua;Upper Hutt;Kapiti
""".let { parse(it) },
        "South Africa" to """
Titans|Titans|Pretoria;Centurion;Soshanguve
Lions|Lions|Johannesburg;Soweto;Roodepoort;Randburg
Dolphins|Dolphins|Durban;Pietermaritzburg;Umhlanga;Pinetown
Warriors|Warriors|Gqeberha;East London;Kariega;Mthatha
Knights|Knights|Bloemfontein;Welkom;Kroonstad;Bethlehem
Western Province|Western Province|Cape Town;Claremont;Stellenbosch;Somerset West
Boland|Boland|Paarl;Worcester;Wellington;Franschhoek
North West|North West|Potchefstroom;Rustenburg;Mahikeng;Klerksdorp
Border|Border|Komani;Gonubie;Bhisho;King William's Town
Limpopo|Limpopo|Polokwane;Tzaneen;Thohoyandou;Mokopane
KZN Inland|KZN Inland|Newcastle;Ladysmith;Estcourt;Howick
Easterns|Easterns|Benoni;Springs;Brakpan;Boksburg
South Western Districts|South Western Districts|George;Oudtshoorn;Mossel Bay;Knysna
Mpumalanga|Mpumalanga|Nelspruit;Witbank;Secunda;Middelburg
Northern Cape|Northern Cape|Kimberley;Upington;Springbok;De Aar
""".let { parse(it) },
        )
    }

    fun states(country: String): List<CareerState> = DATA[country] ?: emptyList()

    fun findState(country: String, state: String): CareerState? = states(country).find { it.name == state }

    /** Three or four district clubs of a state. India and Pakistan call them district clubs; the others, plain cricket clubs. */
    fun clubs(country: String, state: String): List<String> {
        val places = findState(country, state)?.places ?: return emptyList()
        val suffix = if (country == "India" || country == "Pakistan") "District Cricket Club" else "Cricket Club"
        return places.map { "$it $suffix" }
    }
}
