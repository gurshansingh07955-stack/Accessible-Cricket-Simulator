package com.cricketsim.logic

/**
 * The IPL auction: the ten franchises and the player pool.
 *
 * POOL. About 550 real players: every player sold at the 2025 mega auction (with their real set,
 * category, base price and country, from the published auction results), the players the ten
 * franchises retained, a list of well-known players who went unsold or were not in that auction,
 * and every other player already in the game's own roster (Pakistan excluded, as in the IPL).
 * Players already in the game keep their game ratings (copied from CricketData, never recreated);
 * the rest are NEW and their ratings are estimates scaled from what the market paid for them.
 * Ids are fixed ("ipl_17"), and a saved auction keeps its own copy of everyone, so ratings may be
 * tuned later without breaking saves.
 *
 * DATA FORMAT. POOL_DATA lines, split by "|":
 *   e|Name|Country|Role|C or U|BaseLakhs|SetCode               (already in the game: ratings from the roster)
 *   n|Name|Country|Role|C or U|BaseLakhs|SetCode|Bat|Bowl|P or S   (new player)
 * Role: B batter, W wicketkeeper, R all-rounder, L bowler. SetCode is the real 2025 set (M1, M2,
 * BA1 ...) or empty. ROSTER_EXTRAS are names looked up in the game roster only.
 */
data class IplTeamInfo(val id: String, val name: String, val shortName: String, val homeStadiumId: String)

data class AuctionPlayer(
    val player: Player,
    val country: String,
    val capped: Boolean,
    val baseLakhs: Int,
    val setCode: String
) {
    val id: String get() = player.id
    val overseas: Boolean get() = country != "IND"
}

object IplData {

    const val SAVE_VERSION = 1
    const val PURSE_LAKHS = 12000
    const val MAX_SQUAD = 25
    const val MIN_SQUAD = 22
    const val MAX_OVERSEAS = 8

    val TEAMS: List<IplTeamInfo> = listOf(
        IplTeamInfo("ipl_csk", "Chennai Super Kings", "Super Kings", "chepauk"),
        IplTeamInfo("ipl_dc", "Delhi Capitals", "Capitals", "arun-jaitley"),
        IplTeamInfo("ipl_gt", "Gujarat Titans", "Titans", "narendra-modi-ahmedabad"),
        IplTeamInfo("ipl_kkr", "Kolkata Knight Riders", "Knight Riders", "eden-gardens"),
        IplTeamInfo("ipl_lsg", "Lucknow Super Giants", "Super Giants", "ekana-lucknow"),
        IplTeamInfo("ipl_mi", "Mumbai Indians", "Mumbai", "wankhede"),
        IplTeamInfo("ipl_pbks", "Punjab Kings", "Punjab", "mohali"),
        IplTeamInfo("ipl_rr", "Rajasthan Royals", "Royals", "sawai-mansingh-jaipur"),
        IplTeamInfo("ipl_rcb", "Royal Challengers Bengaluru", "Bengaluru", "chinnaswamy"),
        IplTeamInfo("ipl_srh", "Sunrisers Hyderabad", "Sunrisers", "uppal")
    )

    fun team(id: String): IplTeamInfo? = TEAMS.find { it.id == id }

    fun shortName(id: String): String = team(id)?.shortName ?: id

    private fun norm(s: String) = s.lowercase().filter { it in 'a'..'z' }

    private fun roleOf(letter: String): PlayerRole = when (letter) {
        "W" -> PlayerRole.WICKETKEEPER
        "R" -> PlayerRole.ALL_ROUNDER
        "L" -> PlayerRole.BOWLER
        else -> PlayerRole.BATSMAN
    }

    /** The base price a roster player asks, from how good he is. */
    private fun baseFor(p: Player): Int {
        val best = maxOf(p.battingRating, p.bowlingRating)
        return when {
            best >= 85 -> 200
            best >= 78 -> 150
            best >= 72 -> 100
            best >= 66 -> 75
            best >= 58 -> 50
            else -> 30
        }
    }

    fun buildPool(): List<AuctionPlayer> {
        val roster = HashMap<String, Pair<Player, String>>()
        CricketData.getAllTeams().forEach { team -> team.players.forEach { roster[norm(it.name)] = it to team.countryCode } }
        val out = ArrayList<AuctionPlayer>()
        val seen = HashSet<String>()
        fun add(player: Player, country: String, capped: Boolean, base: Int, set: String) {
            if (!seen.add(norm(player.name))) return
            val id = "ipl_${out.size + 1}"
            out.add(AuctionPlayer(player.copy(id = id, teamId = "ipl_pool"), country, capped, maxOf(30, base), set))
        }
        for (line in POOL_DATA.lines()) {
            val f = line.trim().split("|")
            if (f.size < 7) continue
            val capped = f[4] == "C"
            val base = f[5].toIntOrNull() ?: 30
            if (f[0] == "e") {
                val hit = roster[norm(f[1])]
                val player = hit?.first ?: Player(
                    id = "", teamId = "", name = f[1], role = roleOf(f[3]), battingRating = 60, bowlingRating = 20,
                    fieldingRating = CricketData.deriveFieldingRating(roleOf(f[3]), 60, 20), bowlingStyle = BowlingStyle.PACE
                )
                add(player, f[2], capped, base, f[6])
            } else if (f.size >= 10) {
                val role = PlayerProfiles.role(f[1], roleOf(f[3]))
                val bat = f[7].toIntOrNull() ?: 50
                val bowl = f[8].toIntOrNull() ?: 20
                val player = Player(
                    id = "", teamId = "", name = f[1], role = role, battingRating = bat, bowlingRating = bowl,
                    fieldingRating = CricketData.deriveFieldingRating(role, bat, bowl),
                    bowlingStyle = PlayerProfiles.style(f[1], if (f[9] == "S") BowlingStyle.SPIN else BowlingStyle.PACE)
                )
                add(player, f[2], capped, base, f[6])
            }
        }
        for (name in ROSTER_EXTRAS.split("|")) {
            val hit = roster[norm(name)] ?: continue
            add(hit.first, hit.second, true, baseFor(hit.first), "")
        }
        return out
    }

    private val POOL_DATA = """
e|Jos Buttler|ENG|W|C|200|M1
e|Shreyas Iyer|IND|B|C|200|M1
e|Rishabh Pant|IND|B|C|200|M1
e|Kagiso Rabada|RSA|L|C|200|M1
e|Arshdeep Singh|IND|L|C|200|M1
e|Mitchell Starc|AUS|L|C|200|M1
e|Yuzvendra Chahal|IND|L|C|200|M2
e|Liam Livingstone|ENG|R|C|200|M2
e|David Miller|RSA|B|C|150|M2
e|KL Rahul|IND|W|C|200|M2
n|Mohammed Shami|IND|L|C|200|M2|20|80|P
e|Mohammed Siraj|IND|L|C|200|M2
e|Harry Brook|ENG|B|C|200|BA1
e|Devon Conway|NZ|B|C|200|BA1
e|Jake Fraser-McGurk|AUS|B|C|200|BA1
e|Aiden Markram|RSA|B|C|200|BA1
n|Devdutt Padikkal|IND|B|C|200|BA1|72|12|P
n|Rahul Tripathi|IND|B|C|75|BA1|64|12|P
e|Ravichandran Ashwin|IND|R|C|200|AL1
n|Venkatesh Iyer|IND|R|C|200|AL1|72|52|P
e|Mitchell Marsh|AUS|R|C|200|AL1
e|Glenn Maxwell|AUS|R|C|200|AL1
n|Harshal Patel|IND|L|C|200|AL1|22|76|P
e|Rachin Ravindra|NZ|R|C|150|AL1
e|Marcus Stoinis|AUS|R|C|200|AL1
e|Quinton de Kock|RSA|W|C|200|WK1
e|Rahmanullah Gurbaz|AFG|W|C|200|WK1
e|Ishan Kishan|IND|W|C|200|WK1
e|Phil Salt|ENG|W|C|200|WK1
n|Jitesh Sharma|IND|W|C|100|WK1|72|8|P
n|Khaleel Ahmed|IND|L|C|200|FA1|19|72|P
e|Jofra Archer|ENG|L|C|200|FA1
e|Trent Boult|NZ|L|C|200|FA1
e|Josh Hazlewood|AUS|L|C|200|FA1
n|Avesh Khan|IND|L|C|200|FA1|20|77|P
e|Prasidh Krishna|IND|L|C|200|FA1
n|T. Natarajan|IND|L|C|200|FA1|20|78|P
e|Anrich Nortje|RSA|L|C|200|FA1
e|Noor Ahmad|AFG|L|C|200|SP1
n|Rahul Chahar|IND|L|C|100|SP1|17|63|S
e|Wanindu Hasaranga|SL|L|C|200|SP1
e|Maheesh Theekshana|SL|L|C|200|SP1
e|Adam Zampa|AUS|L|C|200|SP1
n|Abhinav Manohar|IND|B|U|30|UBA1|63|12|P
n|Karun Nair|IND|B|U|30|UBA1|54|12|P
n|Angkrish Raghuvanshi|IND|B|U|30|UBA1|63|12|P
n|Atharva Taide|IND|B|U|30|UBA1|53|12|P
n|Nehal Wadhera|IND|B|U|30|UBA1|66|12|P
n|Harpreet Brar|IND|R|U|30|UAL1|55|50|S
n|Naman Dhir|IND|R|U|30|UAL1|65|60|P
n|Mahipal Lomror|IND|R|U|30|UAL1|56|51|P
n|Sameer Rizvi|IND|R|U|30|UAL1|53|48|P
n|Abdul Samad|IND|R|U|30|UAL1|63|58|P
n|Vijay Shankar|IND|R|U|30|UAL1|54|49|P
n|Ashutosh Sharma|IND|R|U|30|UAL1|62|57|P
n|Nishant Sindhu|IND|R|U|30|UAL1|50|45|P
n|Aryan Juyal|IND|W|U|30|UWK1|53|8|P
n|Kumar Kushagra|IND|W|U|30|UWK1|55|8|P
n|Robin Minz|IND|W|U|30|UWK1|55|8|P
n|Anuj Rawat|IND|W|U|30|UWK1|53|8|P
n|Luvnith Sisodia|IND|W|U|30|UWK1|53|8|P
n|Vishnu Vinod|IND|W|U|30|UWK1|56|8|P
n|Vaibhav Arora|IND|L|U|30|UFA1|16|59|P
n|Rasikh Salam Dar|IND|L|U|30|UFA1|18|68|P
n|Akash Madhwal|IND|L|U|30|UFA1|16|57|P
n|Mohit Sharma|IND|L|U|50|UFA1|17|60|P
n|Simarjeet Singh|IND|L|U|30|UFA1|16|58|P
n|Yash Thakur|IND|L|U|30|UFA1|16|58|P
n|Vijaykumar Vyshak|IND|L|U|30|UFA1|16|59|P
n|Shreyas Gopal|IND|L|U|30|USP1|15|53|S
n|Mayank Markande|IND|L|U|30|USP1|15|53|S
n|Suyash Sharma|IND|L|U|30|USP1|17|62|S
n|Karn Sharma|IND|L|U|50|USP1|16|55|S
n|Kumar Kartikeya|IND|L|U|30|USP1|15|53|S
n|Manav Suthar|IND|L|U|30|USP1|15|53|S
n|Faf du Plessis|RSA|B|C|200|BA2|72|12|P
e|Glenn Phillips|NZ|B|C|200|BA2
e|Rovman Powell|WI|B|C|150|BA2
n|Ajinkya Rahane|IND|B|C|150|BA2|68|12|P
e|Sam Curran|ENG|R|C|200|AL2
e|Marco Jansen|RSA|R|C|125|AL2
n|Krunal Pandya|IND|R|C|200|AL2|69|64|S
n|Nitish Rana|IND|R|C|150|AL2|65|60|P
e|Washington Sundar|IND|R|C|200|AL2
e|Donovan Ferreira|RSA|W|C|75|WK2
e|Josh Inglis|AUS|W|C|200|WK2
e|Ryan Rickelton|RSA|W|C|100|WK2
e|Deepak Chahar|IND|L|C|200|FA2
e|Gerald Coetzee|RSA|L|C|125|FA2
n|Akash Deep|IND|L|C|100|FA2|19|74|P
n|Tushar Deshpande|IND|L|C|100|FA2|19|72|P
e|Lockie Ferguson|NZ|L|C|200|FA2
n|Bhuvneshwar Kumar|IND|L|C|200|FA2|20|78|P
n|Mukesh Kumar|IND|L|C|200|FA2|19|74|P
n|Allah Ghazanfar|AFG|L|C|75|SP2|18|68|S
n|Swastik Chikara|IND|B|U|30||53|12|P
n|Shubham Dubey|IND|B|U|30||55|12|P
n|Shaik Rasheed|IND|B|U|30||53|12|P
n|Himmat Singh|IND|B|U|30||53|12|P
n|Anshul Kamboj|IND|R|U|30||61|56|P
n|Arshad Khan|IND|R|U|30||54|49|P
n|Darshan Nalkande|IND|R|U|30||50|45|P
n|Anukul Roy|IND|R|U|30||51|46|P
n|Swapnil Singh|IND|R|U|30||51|46|P
n|Vansh Bedi|IND|W|U|30||54|8|P
n|Kunal Rathore|IND|W|U|30||53|8|P
n|Gurnoor Brar|IND|L|U|30||16|57|P
n|Mukesh Choudhary|IND|L|U|30||15|53|P
n|Arjun Tendulkar|IND|L|U|30||15|53|P
n|Zeeshan Ansari|IND|L|U|30||15|54|P
n|Manimaran Siddharth|IND|L|U|30||16|55|P
n|Digvesh Singh|IND|L|U|30||15|53|S
n|Manish Pandey|IND|B|C|75||58|12|P
e|Sherfane Rutherford|WI|B|C|150|
n|Shahbaz Ahmed|IND|R|C|100||60|55|S
e|Tim David|AUS|R|C|200|
n|Deepak Hooda|IND|R|C|75||56|51|P
e|Will Jacks|ENG|R|C|200|
e|Azmatullah Omarzai|AFG|R|C|150|
n|Sai Kishore|IND|R|C|75||57|52|S
e|Romario Shepherd|WI|R|C|150|
e|Spencer Johnson|AUS|L|C|200|
n|Umran Malik|IND|L|C|75||16|58|P
n|Ishant Sharma|IND|L|C|75||16|58|P
e|Nuwan Thushara|SL|L|C|75|
n|Jaydev Unadkat|IND|L|C|100||17|63|P
n|Sachin Baby|IND|B|U|30||53|12|P
n|Harnoor Singh|IND|B|U|30||53|12|P
n|Andre Siddarth|IND|B|U|30||53|12|P
n|Yudhvir Singh|IND|R|U|30||50|45|P
n|Rajvardhan Hangargekar|IND|R|U|30||50|45|P
n|Arshin Kulkarni|IND|R|U|30||50|45|P
n|Ashwani Kumar|IND|L|U|30||15|53|P
n|Akash Singh|IND|L|U|30||15|53|P
n|Gurjapneet Singh|IND|L|U|30||17|60|P
e|Matthew Breetzke|RSA|B|C|30|
e|Mitchell Santner|NZ|R|C|200|
n|Jayant Yadav|IND|R|C|75||55|50|P
e|Fazalhaq Farooqi|AFG|L|C|200|
e|Kwena Maphaka|RSA|L|C|75|
n|Kuldeep Sen|IND|L|C|75||16|58|S
e|Reece Topley|ENG|L|C|75|
e|Lizaad Williams|RSA|L|C|75|
n|Priyansh Arya|IND|R|U|30||62|57|P
n|Manoj Bhandage|IND|R|U|30||50|45|P
n|Praveen Dubey|IND|R|U|30||50|45|P
n|Ajay Mandal|IND|R|U|30||50|45|P
n|Vipraj Nigam|IND|R|U|30||51|46|P
n|Krishnan Shrijith|IND|R|U|30||50|45|P
n|Mohit Rathee|IND|L|U|30||15|53|P
e|Jacob Bethell|ENG|R|C|125|
e|Brydon Carse|ENG|R|C|100|
e|Aaron Hardie|AUS|R|C|125|
e|Kamindu Mendis|SL|R|C|75|
n|Dushmantha Chameera|SL|L|C|75||16|58|P
e|Nathan Ellis|AUS|L|C|100|
e|Shamar Joseph|WI|L|C|75|
n|Aniket Verma|IND|B|U|30||53|12|P
n|Raj Angad Bawa|IND|R|U|30||50|45|P
n|Musheer Khan|IND|R|U|30||50|45|P
n|Manvanth Kumar L|IND|R|U|30||50|45|P
n|Suryansh Shedge|IND|R|U|30||50|45|P
n|Kulwant Khejroliya|IND|L|U|30||15|53|P
n|Prince Yadav|IND|R|U|30||50|45|P
n|Jamie Overton|ENG|R|C|150||65|60|P
e|Xavier Bartlett|AUS|L|C|75|
e|Lungi Ngidi|RSA|L|C|30|
n|Yuvraj Chaudhary|IND|R|U|30||50|45|P
n|Kamlesh Nagarkoti|IND|R|U|30||50|45|P
n|Abhinandan Singh|IND|L|U|30||15|53|P
e|Karim Janat|AFG|R|C|30|
n|Pyla Avinash|IND|B|U|30||53|12|P
n|Ramakrishna Ghosh|IND|R|U|30||50|45|P
e|Bevon Jacobs|NZ|B|U|30|
n|Ashok Sharma|IND|L|U|30||15|53|P
n|Vaibhav Sooryavanshi|IND|B|U|30||56|12|P
n|Eshan Malinga|SL|L|U|30||16|57|P
n|Tripurana Vijay|IND|R|U|30||50|45|P
n|Madhav Tiwari|IND|R|U|30||51|46|P
n|Vignesh Puthur|IND|R|U|30||50|45|S
e|Ruturaj Gaikwad|IND|B|C|200|
e|Ravindra Jadeja|IND|R|C|200|
e|Matheesha Pathirana|SL|L|C|200|
e|Shivam Dube|IND|R|C|150|
n|MS Dhoni|IND|W|C|100||66|8|P
e|Axar Patel|IND|R|C|200|
e|Kuldeep Yadav|IND|L|C|200|
e|Tristan Stubbs|RSA|B|C|150|
n|Abishek Porel|IND|W|U|30||66|8|P
e|Rashid Khan|AFG|L|C|200|
e|Shubman Gill|IND|B|C|200|
n|Sai Sudharsan|IND|B|C|150||75|12|P
n|Rahul Tewatia|IND|R|C|100||63|58|S
n|Shahrukh Khan|IND|B|U|75||66|12|P
e|Rinku Singh|IND|B|C|200|
n|Varun Chakaravarthy|IND|L|C|200||21|80|S
n|Sunil Narine|WI|R|C|200||77|72|S
e|Andre Russell|WI|R|C|200|
e|Harshit Rana|IND|L|C|100|
n|Ramandeep Singh|IND|R|C|50||63|58|P
e|Nicholas Pooran|WI|W|C|200|
n|Ravi Bishnoi|IND|L|C|150||20|79|S
e|Mayank Yadav|IND|L|C|100|
n|Mohsin Khan|IND|L|U|30||18|66|P
n|Ayush Badoni|IND|B|C|50||66|12|P
e|Jasprit Bumrah|IND|L|C|200|
e|Suryakumar Yadav|IND|B|C|200|
e|Hardik Pandya|IND|R|C|200|
e|Rohit Sharma|IND|B|C|200|
e|Tilak Varma|IND|B|C|150|
n|Shashank Singh|IND|B|U|50||68|12|P
n|Prabhsimran Singh|IND|W|U|30||66|8|P
e|Sanju Samson|IND|W|C|200|
e|Yashasvi Jaiswal|IND|B|C|200|
n|Riyan Parag|IND|R|C|150||79|74|S
n|Dhruv Jurel|IND|W|C|100||82|8|P
e|Shimron Hetmyer|WI|B|C|150|
n|Sandeep Sharma|IND|L|C|100||18|66|P
e|Virat Kohli|IND|B|C|200|
n|Rajat Patidar|IND|B|C|100||79|12|P
n|Yash Dayal|IND|L|C|75||18|68|P
e|Heinrich Klaasen|RSA|W|C|200|
e|Pat Cummins|AUS|L|C|200|
e|Abhishek Sharma|IND|R|C|150|
e|Travis Head|AUS|B|C|200|
n|Nitish Kumar Reddy|IND|R|C|100||67|62|P
n|Prithvi Shaw|IND|B|C|75||70|12|P
n|Mayank Agarwal|IND|B|C|100||70|12|P
n|Sarfaraz Khan|IND|B|C|75||70|12|P
n|Wriddhiman Saha|IND|W|C|100||63|8|P
n|Shardul Thakur|IND|R|C|200||67|62|P
n|Umesh Yadav|IND|L|C|200||19|70|P
n|Piyush Chawla|IND|L|C|50||17|63|S
n|Navdeep Saini|IND|L|C|75||17|63|P
n|Chetan Sakariya|IND|L|C|75||17|63|P
n|Ayush Mhatre|IND|B|U|30||68|12|P
n|KS Bharat|IND|W|C|75||63|8|P
e|Kane Williamson|NZ|B|C|200|
e|Jonny Bairstow|ENG|W|C|200|
e|Daryl Mitchell|NZ|R|C|200|
n|Alex Hales|ENG|B|C|150||70|12|P
e|Moeen Ali|ENG|R|C|200|
e|Shakib Al Hasan|BAN|R|C|100|
e|Mustafizur Rahman|BAN|L|C|200|
n|James Anderson|ENG|L|C|125||17|63|P
n|Rilee Rossouw|RSA|B|C|200||70|12|P
e|Cameron Green|AUS|R|C|200|
e|Steve Smith|AUS|B|C|200|
e|David Warner|AUS|B|C|200|
e|Alex Carey|AUS|W|C|100|
e|Nathan Lyon|AUS|L|C|100|
e|Sean Abbott|AUS|R|C|75|
n|Tom Curran|ENG|R|C|200||67|62|P
e|Adil Rashid|ENG|L|C|200|
e|Mark Wood|ENG|L|C|200|
e|Chris Jordan|ENG|R|C|200|
e|Tim Southee|NZ|L|C|200|
e|Kyle Jamieson|NZ|R|C|150|
e|Evin Lewis|WI|B|C|200|
e|Alzarri Joseph|WI|L|C|150|
e|Obed McCoy|WI|L|C|100|
e|Kyle Mayers|WI|R|C|150|
e|Brandon King|WI|B|C|100|
e|Johnson Charles|WI|W|C|100|
e|Akeal Hosein|WI|L|C|100|
e|Gudakesh Motie|WI|L|C|75|
e|Shai Hope|WI|W|C|100|
e|Tabraiz Shamsi|RSA|L|C|200|
e|Reeza Hendricks|RSA|B|C|150|
e|Wiaan Mulder|RSA|R|C|100|
e|Keshav Maharaj|RSA|L|C|75|
e|Mujeeb ur Rahman|AFG|L|C|200|
e|Mohammad Nabi|AFG|R|C|150|
e|Kusal Perera|SL|W|C|100|
e|Dasun Shanaka|SL|R|C|75|
e|Charith Asalanka|SL|B|C|75|
e|Kusal Mendis|SL|W|C|75|
e|Pathum Nissanka|SL|B|C|75|
e|Dilshan Madushanka|SL|L|C|75|
e|Litton Das|BAN|W|C|75|
e|Taskin Ahmed|BAN|L|C|75|
e|Mehidy Hasan Miraz|BAN|R|C|100|
e|Sikandar Raza|ZIM|R|C|100|
e|Blessing Muzarabani|ZIM|L|C|75|
e|Saurabh Netravalkar|USA|L|C|75|
e|Corey Anderson|USA|R|C|75|
e|Matthew Short|AUS|B|C|75|
""".trimIndent()

    private val ROSTER_EXTRAS =
        "Marnus Labuschagne|Ben Dwarshuis|Tanveer Sangha|Josh Philippe|Matthew Wade|Ashton Agar|Ben McDermott|Jason Behrendorff|Ben Duckett|Joe Root|Ben Stokes|Jamie Smith|Jordan Cox|Gus Atkinson|Rehan Ahmed|Tom Hartley|Dawid Malan|James Vince|Sam Hain|Olly Stone|Saqib Mahmood|Luke Wood|Will Smeed|Temba Bavuma|Rassie van der Dussen|Dewald Brevis|Andile Phehlukwayo|Nandre Burger|Bjorn Fortuin|Ottniel Baartman|Corbin Bosch|Tony de Zorzi|Prenelan Subrayen|Migael Pretorius|Finn Allen|Mark Chapman|Ish Sodhi|Tom Latham|Will Young|Michael Bracewell|James Neesham|Tim Robinson|Matt Henry|Adam Milne|Ben Sears|Jacob Duffy|William O'Rourke|Zak Foulkes|Chad Bowes|Josh Clarkson|Dean Foxcroft|Ajaz Patel|Cole McConchie|Angelo Mathews|Avishka Fernando|Sadeera Samarawickrama|Janith Liyanage|Dhananjaya de Silva|Dunith Wellalage|Chamika Karunaratne|Kamil Mishara|Pramod Madushan|Asitha Fernando|Lahiru Kumara|Jeffrey Vandersay|Sahan Arachchige|Lahiru Udara|Bhanuka Rajapaksa|Isuru Udana|Ramesh Mendis|Kasun Rajitha|Nuwanidu Fernando|Roston Chase|Jason Holder|Keacy Carty|Justin Greaves|Odean Smith|Matthew Forde|Jayden Seales|Yannic Cariah|Akeem Jordan|Kevin Sinclair|Amir Jangoo|Kirk McKenzie|Khary Pierre|Alick Athanaze|Tanzid Hasan|Najmul Hossain Shanto|Towhid Hridoy|Mahmudullah|Rishad Hossain|Tanzim Hasan Sakib|Shoriful Islam|Soumya Sarkar|Mushfiqur Rahim|Jaker Ali|Mahmudul Hasan Joy|Parvez Hossain Emon|Afif Hossain|Nasum Ahmed|Shamim Hossain|Hasan Mahmud|Al-Amin Hossain|Ebadot Hossain|Nazmul Hossain|Rony Talukdar|Zakir Hasan|Taijul Islam|Nurul Hasan Sohan|Tanvir Islam|Rakibul Hasan|Ibrahim Zadran|Gulbadin Naib|Najibullah Zadran|Naveen-ul-Haq|Sediqullah Atal|Rahmat Shah|Hashmatullah Shahidi|Riaz Hassan|Darwish Rasooli|Ikram Alikhil|Mohammad Ishaq|Sharafuddin Ashraf|Nangeyalia Kharote|Qais Ahmad|Zia-ur-Rehman|Yamin Ahmadzai|Abdul Rahman|Wafadar Momand|Bahir Shah|Shahidullah Kamal|Fareed Ahmad|Paul Stirling|Andrew Balbirnie|Lorcan Tucker|Harry Tector|Curtis Campher|George Dockrell|Gareth Delany|Mark Adair|Barry McCarthy|Josh Little|Craig Young|Ross Adair|Neil Rock|Fionn Hand|Ben White|Graham Hume|Matthew Humphreys|Joylord Gumbie|Wessly Madhevere|Brian Bennett|Sean Williams|Ryan Burl|Clive Madande|Luke Jongwe|Wellington Masakadza|Richard Ngarava|Tadiwanashe Marumani|Dion Myers|Tony Munyonga|Tashinga Musekiwa|Trevor Gwandu|Newman Nyamhuri|Max O'Dowd|Michael Levitt|Vikramjit Singh|Scott Edwards|Sybrand Engelbrecht|Bas de Leede|Logan van Beek|Tim Pringle|Aryan Dutt|Paul van Meekeren|Vivian Kingma|Wesley Barresi|Teja Nidamanuru|Saqib Zulfiqar|Noah Croes|Kyle Klein|Fred Klaassen|George Munsey|Michael Jones|Brandon McMullen|Richie Berrington|Matthew Cross|Michael Leask|Chris Greaves|Mark Watt|Chris Sole|Safyaan Sharif|Brad Wheal|Oli Hairs|Charlie Tear|Jack Jarvis|Bradley Currie|Alasdair Evans|Hamza Tahir|Kushal Bhurtel|Aasif Sheikh|Rohit Paudel|Anil Sah|Dipendra Singh Airee|Kushal Malla|Gulshan Jha|Sompal Kami|Karan KC|Sandeep Lamichhane|Abinash Bohara|Bhim Sharki|Aarif Sheikh|Dev Khanal|Sundeep Jora|Lalit Rajbanshi|Mousom Dhakal|Muhammad Waseem|Aryan Lakra|Vriitya Aravind|Rohan Mustafa|Basil Hameed|Aayan Afzal Khan|Ali Naseer|Zahoor Khan|Junaid Siddique|Muhammad Jawadullah|Akif Raja|Alishan Sharafu|Asif Khan|Rahul Bhatia|Karthik Meiyappan|Simranjeet Singh|Sanchit Sharma|Kashyap Prajapati|Naseem Khushi|Aqib Ilyas|Zeeshan Maqsood|Khalid Kail|Ayan Khan|Mehran Khan|Rafiullah|Shakeel Ahmed|Kaleemullah|Bilal Khan|Pratik Athavale|Vinayak Shukla|Wasim Ali|Fayyaz Butt|Nadeem Khan|Samay Shrivastava|Steven Taylor|Monank Patel|Andries Gous|Aaron Jones|Nitish Kumar|Harmeet Singh|Shadley van Schalkwyk|Jasdeep Singh|Ali Khan|Milind Kumar|Shayan Jahangir|Kyle Phillip|Nosthush Kenjige|Yasir Mohammad|Jessy Singh"
}
