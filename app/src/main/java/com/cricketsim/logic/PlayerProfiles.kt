package com.cricketsim.logic

/**
 * Real-world profiles of the players: which hand they bat with, how they bowl, and where the
 * game's own role for a player was wrong (a wicketkeeper filed as a plain batsman, an all-rounder
 * filed as a bowler, and so on).
 *
 * The table below was built from each player's Wikipedia cricket infobox (batting hand, bowling
 * style, role), checked against the game's own ratings, with a few entries added by hand where
 * no page was found. Players with no entry simply show no hand or style (the screens say what
 * they know). Lines look like: Name|hand|bowling code|role override|style override
 *   hand: R, L or - (unknown)    bowling code: b0..b12 (see BOWL_LABELS) or - (none)
 *   role override: B batsman, W wicketkeeper, R all-rounder, L bowler, - (keep the game's role)
 *   style override: S spin, P pace, - (keep the game's flag)
 *
 * Roles and pace/spin flags are applied when a player is created (CricketData and every league
 * file call [role] and [style]). Switch them all off with [USE_PROFILE_ROLES] = false; the hand
 * and bowling-style text on the screens stays.
 */
object PlayerProfiles {
    /** Off = the game's old roles and pace/spin flags come back (the display text stays). */
    const val USE_PROFILE_ROLES = true

    private class Profile(val hand: Char, val bowl: String?, val role: PlayerRole?, val style: BowlingStyle?)

    private val BOWL_LABELS = arrayOf(
        "Left-arm fast", "Left-arm fast-medium", "Left-arm medium", "Left-arm medium-fast",
        "Left-arm orthodox", "Left-arm wrist spin", "Right-arm fast", "Right-arm fast-medium",
        "Right-arm leg break", "Right-arm medium", "Right-arm medium-fast", "Right-arm off break",
        "Right-arm spin", "Left-arm off break", "Left-arm leg break"
    )

    private fun key(name: String): String {
        val sb = StringBuilder()
        for (c in name.lowercase()) if (c in 'a'..'z') sb.append(c)
        return sb.toString()
    }

    private val table: Map<String, Profile> by lazy { build() }

    private fun build(): Map<String, Profile> {
        val m = HashMap<String, Profile>(1200)
        for (chunk in CHUNKS) {
            for (line in chunk.lineSequence()) {
                val f = line.trim().split('|')
                if (f.size < 5) continue
                val hand = if (f[1] == "R" || f[1] == "L") f[1][0] else '-'
                val bowl = if (f[2].startsWith("b")) BOWL_LABELS.getOrNull(f[2].substring(1).toIntOrNull() ?: -1) else null
                val role = when (f[3]) {
                    "B" -> PlayerRole.BATSMAN
                    "W" -> PlayerRole.WICKETKEEPER
                    "R" -> PlayerRole.ALL_ROUNDER
                    "L" -> PlayerRole.BOWLER
                    else -> null
                }
                val style = when (f[4]) {
                    "S" -> BowlingStyle.SPIN
                    "P" -> BowlingStyle.PACE
                    else -> null
                }
                m[key(f[0])] = Profile(hand, bowl, role, style)
            }
        }
        return m
    }

    /** The corrected role for a new player (used where players are created). */
    fun role(name: String, gameRole: PlayerRole): PlayerRole =
        if (USE_PROFILE_ROLES) table[key(name)]?.role ?: gameRole else gameRole

    /** The corrected pace/spin flag for a new player. */
    fun style(name: String, gameStyle: BowlingStyle): BowlingStyle =
        if (USE_PROFILE_ROLES) table[key(name)]?.style ?: gameStyle else gameStyle

    /** The role to show: also right for players saved before this table existed. */
    fun displayRole(p: Player): PlayerRole = role(p.name, p.role)

    fun roleLabel(r: PlayerRole): String = when (r) {
        PlayerRole.BATSMAN -> "Batsman"
        PlayerRole.WICKETKEEPER -> "Wicketkeeper"
        PlayerRole.ALL_ROUNDER -> "All-rounder"
        PlayerRole.BOWLER -> "Bowler"
    }

    private fun groupTitle(r: PlayerRole): String = when (r) {
        PlayerRole.BATSMAN -> "Batsmen"
        PlayerRole.WICKETKEEPER -> "Wicketkeepers"
        PlayerRole.ALL_ROUNDER -> "All-rounders"
        PlayerRole.BOWLER -> "Bowlers"
    }

    /** Squad lists are split under these headings, in this order (like a squad page on Cricbuzz). */
    private val GROUP_ORDER = listOf(
        PlayerRole.BATSMAN, PlayerRole.WICKETKEEPER, PlayerRole.ALL_ROUNDER, PlayerRole.BOWLER
    )

    /** Splits any list into (heading, items) groups; empty groups are left out, order inside is kept. */
    fun <T> groupedBy(items: List<T>, player: (T) -> Player): List<Pair<String, List<T>>> =
        GROUP_ORDER.mapNotNull { r ->
            val list = items.filter { displayRole(player(it)) == r }
            if (list.isEmpty()) null else groupTitle(r) to list
        }

    fun grouped(players: List<Player>): List<Pair<String, List<Player>>> = groupedBy(players) { it }

    /** "Batting: right-handed, rating 95." (hand left out when it is not known). */
    fun battingText(p: Player): String {
        val rating = p.battingRating
        return when (table[key(p.name)]?.hand) {
            'R' -> "Batting: right-handed, rating $rating."
            'L' -> "Batting: left-handed, rating $rating."
            else -> "Batting: rating $rating."
        }
    }

    /** "Bowling: right-arm off break, rating 22." */
    fun bowlingText(p: Player): String {
        val rating = p.bowlingRating
        val label = table[key(p.name)]?.bowl
        if (label != null) return "Bowling: ${label.replaceFirstChar { it.lowercase() }}, rating $rating."
        val r = displayRole(p)
        if ((r == PlayerRole.BATSMAN || r == PlayerRole.WICKETKEEPER) && rating <= 25) {
            return "Bowling: rarely bowls, rating $rating."
        }
        val kind = if (p.bowlingStyle == BowlingStyle.SPIN) "spin" else "pace"
        return "Bowling: $kind, rating $rating."
    }

    private val CHUNKS: List<String> = listOf(
        """
Saif Hassan|R|b11|-|-
Mohammad Saifuddin|L|b10|-|-
Babar Hayat|R|b9|-|-
Haroon Arshad|R|b10|-|-
Martin Coetzee|R|-|-|-
Aizaz Khan|R|b10|-|-
Anas Khan|L|b4|-|-
Ehsan Khan|R|b11|-|-
Nizakat Khan|R|b8|-|-
Nasrulla Rana|R|b10|-|-
Anshuman Rath|L|b4|-|-
Kinchit Shah|L|b11|-|-
Ayush Shukla|R|b9|-|-
Mohammad Waheed|L|b4|-|-
Shahid Wasif|R|b11|-|-
Hussain Talat|L|b10|-|-
Rahul Chopra|R|-|-|-
Ethan D'Souza|L|-|-|-
""",
        """
Mohammad Hasnain|R|b6|-|-
Tayyab Tahir|R|b8|-|-
Nahid Rana|R|b6|-|-
Nathan Smith|R|b10|-|-
Naveed Zadran|R|b9|-|-
""",
        """
Bernard Scholtz|R|b4|-|-
Gerhard Erasmus|R|b11|-|-
JJ Smit|R|b3|-|-
Jan Nicol Loftie-Eaton|L|b2|-|-
Malan Kruger|R|-|-|-
Dylan Leicher|R|b9|-|-
Willem Myburgh|R|b6|-|-
Jack Brassell|R|b9|-|-
Louren Steenkamp|R|b11|-|-
Zane Green|L|-|-|-
Jan Frylinck|L|b3|-|-
Max Heingo|R|b9|-|-
Jan Balt|L|b13|-|-
Ruben Trumpelmann|R|b1|-|-
Alexander Volschenk|R|b2|-|-
Timm van der Gugten|R|b10|-|-
Zach Lion-Cachet|R|b11|-|-
Colin Ackermann|R|b11|-|-
Roelof van der Merwe|R|b4|-|-
Salman Mirza|R|b2|-|-
Tim Tector|R|b11|-|-
Sam Topping|L|-|-|-
Ben Calitz|L|b12|-|-
Hammad Mirza|R|-|-|-
Jay Odedra|R|b11|-|-
Aamir Kaleem|L|b4|-|-
Jatinder Singh|R|b11|-|-
Shah Faisal|L|b3|-|-
Karan Sonavale|R|b11|-|-
Sufyan Mehmood|L|b9|-|-
Ashish Odedara|R|b5|-|-
Shafiq Jan|R|b9|-|-
Jiten Ramanandi|L|b2|-|-
Mohammad Nadeem|R|b10|-|-
Graeme Cremer|R|b8|-|-
Ben Curran|L|b11|-|-
Tinotenda Maposa|R|b9|-|-
Brad Evans|R|b6|-|-
Josh Tongue|R|b7|-|-
Liam Dawson|R|b4|-|-
Grant Stewart|R|b10|-|-
Gian-Piero Meade|R|b10|-|-
Jaspreet Singh|R|b7|-|-
Anthony Mosca|R|-|-|-
Syed Naqvi|R|b10|-|-
Zain Ali|R|b9|-|-
JJ Smuts|R|b4|-|-
Marcus Campopiano|R|b11|-|-
Harry Manenti|R|b10|-|-
Ali Hasan|L|b1|-|-
Thomas Draca|R|b9|-|-
Justin Mosca|L|-|-|-
Wayne Madsen|R|b11|-|-
Crishan Kalugamage|R|b8|-|-
Lokesh Bam|R|b9|-|-
Nandan Yadav|R|b10|-|-
Sher Malla|L|b11|-|-
Basir Ahamad|L|b4|-|-
Zainullah Ihsan|R|b9|-|-
Oliver Davidson|L|b4|-|-
Finlay McCreath|R|b10|-|-
Tom Bruce|R|b11|-|-
Jasper Davidson|R|b6|-|-
Ijaz Ahmad Ahmadzai|R|b9|-|-
Shreyas Movva|R|-|-|-
Kaleem Sana|R|b3|-|-
Ansh Patel|R|b5|-|-
Ajayveer Hundal|R|b9|-|-
Jaskaran Singh|R|b10|-|-
Harsh Thaker|R|b11|-|-
Dillon Heyliger|R|b10|-|-
Saad Bin Zafar|L|b4|-|-
Navneet Dhaliwal|R|b9|-|-
Ravinderpal Singh|R|b9|-|-
Shivam Sharma|R|b11|-|-
Kanwarpal Tathgur|R|-|-|-
Nicholas Kirton|L|b11|-|-
Dilpreet Bajwa|R|b11|-|-
Yuvraj Samra|L|b9|-|-
Aryansh Sharma|R|-|-|-
Mayank Kumar|R|-|-|-
Sohaib Khan|R|-|-|-
Syed Haider|R|-|-|-
Muhammad Farooq|R|b8|-|-
""",
        """
Rohit Sharma|R|b11|-|-
Yashasvi Jaiswal|L|b8|-|S
Virat Kohli|R|b9|-|-
Suryakumar Yadav|R|b11|-|-
Rishabh Pant|L|b9|-|-
Hardik Pandya|R|b10|-|-
Ravindra Jadeja|L|b4|-|-
Axar Patel|L|b4|-|-
Kuldeep Yadav|L|b5|-|-
Jasprit Bumrah|R|b6|-|-
Mohammed Siraj|R|b6|-|-
Shubman Gill|R|b11|-|-
Sanju Samson|R|b11|-|-
Abhishek Sharma|L|b4|-|-
Mayank Yadav|R|b6|-|-
KL Rahul|R|-|-|-
Shreyas Iyer|R|b8|-|-
Ruturaj Gaikwad|R|b11|-|-
Shivam Dube|L|b9|-|-
Washington Sundar|L|b11|-|-
Arshdeep Singh|L|b3|-|-
Harshit Rana|R|b6|-|-
Varun Chakravarthy|R|b8|-|-
Ravichandran Ashwin|R|b11|-|-
Rinku Singh|L|b11|-|-
Tilak Varma|L|b11|-|-
Prasidh Krishna|R|b7|-|-
Yuzvendra Chahal|R|b8|-|-
Ishan Kishan|L|b11|-|-
Deepak Chahar|R|b9|-|-
Travis Head|L|b11|-|S
David Warner|L|b8|-|-
Mitchell Marsh|R|b9|-|-
Steve Smith|R|b8|-|S
Glenn Maxwell|R|b11|-|-
Marcus Stoinis|R|b9|-|-
Tim David|R|b11|-|-
Pat Cummins|R|b6|-|-
Mitchell Starc|L|b0|-|-
Adam Zampa|R|b8|-|-
Josh Hazlewood|L|b7|-|-
Alex Carey|L|-|-|-
Cameron Green|R|b7|-|-
Marnus Labuschagne|R|b8|-|S
Josh Inglis|R|-|-|-
Matthew Short|R|b11|-|-
Aaron Hardie|R|b10|-|-
Nathan Ellis|R|b7|-|-
Sean Abbott|R|b7|-|-
Spencer Johnson|L|b0|-|-
Xavier Bartlett|R|b7|-|-
Ben Dwarshuis|L|b1|-|-
Tanveer Sangha|R|b8|-|-
Jake Fraser-McGurk|R|b8|-|-
Josh Philippe|R|-|-|-
Matthew Wade|L|b9|-|-
Ashton Agar|L|b4|-|-
Nathan Lyon|R|b11|-|-
Ben McDermott|R|b9|-|-
Jason Behrendorff|R|b1|-|-
Jos Buttler|R|-|-|-
Phil Salt|R|b11|W|-
Will Jacks|R|b11|R|-
Jonny Bairstow|R|-|W|-
Harry Brook|R|b9|-|-
Moeen Ali|L|b11|-|-
Liam Livingstone|R|b8|-|-
Sam Curran|L|b3|-|-
Jofra Archer|R|b6|-|-
Adil Rashid|R|b8|-|-
Mark Wood|R|b6|-|-
Ben Duckett|L|b11|-|-
Joe Root|R|b11|-|-
Ben Stokes|L|b7|-|-
Jacob Bethell|L|b4|-|-
Jamie Smith|R|-|-|-
Jordan Cox|R|b11|-|-
Reece Topley|R|b1|-|-
Chris Jordan|R|b7|-|-
Brydon Carse|R|b6|-|-
Gus Atkinson|R|b7|-|-
Rehan Ahmed|R|b8|-|-
Tom Hartley|L|b4|-|-
Dawid Malan|L|b8|-|-
James Vince|R|b9|-|-
Sam Hain|R|b11|-|-
Olly Stone|R|b6|-|-
Saqib Mahmood|R|b6|-|-
Luke Wood|L|b1|-|-
Will Smeed|R|b11|-|-
Babar Azam|R|b11|-|-
Mohammad Rizwan|R|b9|-|-
Saim Ayub|L|b11|R|S
Fakhar Zaman|L|b4|-|-
Iftikhar Ahmed|R|b11|-|-
Shadab Khan|R|b8|-|-
Imad Wasim|L|b4|-|-
Shaheen Afridi|L|b0|-|-
Naseem Shah|R|b6|-|-
Haris Rauf|R|b6|-|-
Mohammad Amir|L|b0|-|-
Abdullah Shafique|R|b11|-|-
Usman Khan|R|b11|-|-
Kamran Ghulam|R|b4|-|-
Salman Agha|R|b11|-|-
Khushdil Shah|L|b4|-|-
Mohammad Nawaz|L|b4|-|-
Faheem Ashraf|L|b9|-|-
Abrar Ahmed|R|b8|-|-
Zaman Khan|R|b6|-|-
Mohammad Wasim Jr|R|b7|-|-
Hasan Ali|R|b10|-|-
Shan Masood|L|b7|-|-
Sarfaraz Ahmed|R|b11|-|-
Azam Khan|R|-|-|-
Imam-ul-Haq|L|b8|-|-
Mohammad Haris|R|b11|-|-
Ihsanullah|R|b6|-|-
Aamer Jamal|R|b7|-|-
Saud Shakeel|L|-|-|-
Quinton de Kock|L|-|-|-
Reeza Hendricks|R|b11|-|-
Aiden Markram|R|b11|-|-
Heinrich Klaasen|R|b11|W|-
David Miller|L|b11|-|-
Tristan Stubbs|R|b11|-|-
Marco Jansen|R|b0|-|-
Keshav Maharaj|R|b4|-|-
Kagiso Rabada|L|b6|-|-
Anrich Nortje|R|b6|-|-
Tabraiz Shamsi|R|b4|-|-
Temba Bavuma|R|b9|-|-
Rassie van der Dussen|R|b8|-|-
Ryan Rickelton|L|b4|-|-
Matthew Breetzke|R|b9|-|-
Dewald Brevis|R|b8|-|S
Wiaan Mulder|R|b9|-|-
Andile Phehlukwayo|L|b10|-|-
Gerald Coetzee|R|b6|-|-
Lungi Ngidi|R|b7|-|-
Nandre Burger|L|b1|-|-
Bjorn Fortuin|R|b4|-|-
Ottniel Baartman|R|b7|-|-
Lizaad Williams|L|b10|-|-
Donovan Ferreira|R|b11|-|-
Corbin Bosch|R|b7|-|-
Kwena Maphaka|L|b0|-|-
Tony de Zorzi|L|b11|-|-
Prenelan Subrayen|R|b11|-|-
Migael Pretorius|R|b6|-|-
""",
        """
Finn Allen|R|b11|-|-
Devon Conway|L|b9|-|-
Kane Williamson|R|b11|-|-
Daryl Mitchell|R|b9|-|-
Glenn Phillips|R|b11|-|-
Mark Chapman|L|b4|-|-
Mitchell Santner|L|b4|-|-
Tim Southee|R|b10|-|-
Trent Boult|R|b1|-|-
Lockie Ferguson|R|b6|-|-
Ish Sodhi|R|b8|-|-
Tom Latham|L|b9|-|-
Will Young|R|b11|-|-
Rachin Ravindra|L|b4|-|-
Michael Bracewell|L|b11|-|-
James Neesham|L|b10|-|-
Tim Robinson|R|b9|-|-
Matt Henry|R|b7|-|-
Kyle Jamieson|R|b7|-|-
Adam Milne|R|b6|-|-
Ben Sears|R|b10|-|-
Jacob Duffy|R|b7|-|-
William O'Rourke|R|b6|-|-
Zak Foulkes|R|b9|-|-
Chad Bowes|R|b10|-|-
Josh Clarkson|R|b9|-|-
Dean Foxcroft|R|b11|-|-
Ajaz Patel|L|b4|-|-
Cole McConchie|R|b11|-|-
Bevon Jacobs|R|b9|-|-
Pathum Nissanka|R|-|-|-
Kusal Mendis|R|b8|-|-
Kamindu Mendis|L|-|R|-
Charith Asalanka|L|b11|-|S
Angelo Mathews|R|b9|-|-
Dasun Shanaka|R|b9|-|-
Wanindu Hasaranga|R|b8|-|-
Maheesh Theekshana|R|b11|-|-
Matheesha Pathirana|R|b6|-|-
Nuwan Thushara|R|b10|-|-
Dilshan Madushanka|R|b1|-|-
Avishka Fernando|R|b10|-|-
Kusal Perera|L|b9|-|-
Sadeera Samarawickrama|R|-|-|-
Janith Liyanage|R|b7|-|-
Dhananjaya de Silva|R|b11|-|-
Dunith Wellalage|L|b4|-|-
Chamika Karunaratne|R|b7|-|-
Kamil Mishara|L|b11|-|-
Pramod Madushan|R|b10|-|-
Asitha Fernando|R|b10|-|-
Lahiru Kumara|L|b6|-|-
Jeffrey Vandersay|R|b8|-|-
Sahan Arachchige|L|b11|-|S
Lahiru Udara|R|-|W|-
Bhanuka Rajapaksa|L|b9|-|-
Isuru Udana|R|b2|-|-
Ramesh Mendis|R|b11|-|-
Kasun Rajitha|R|b10|-|-
Nuwanidu Fernando|R|b11|-|-
Brandon King|R|-|-|-
Johnson Charles|R|-|W|-
Nicholas Pooran|L|b11|-|-
Rovman Powell|R|b10|-|-
Andre Russell|R|b6|-|-
Sherfane Rutherford|L|b7|-|-
Romario Shepherd|R|b7|-|-
Akeal Hosein|L|b4|-|-
Alzarri Joseph|R|b6|-|-
Gudakesh Motie|L|b4|-|-
Obed McCoy|L|b1|-|-
Shai Hope|R|b2|-|-
Kyle Mayers|L|b9|-|-
Evin Lewis|L|b9|-|-
Shimron Hetmyer|L|-|-|-
Roston Chase|R|b11|-|-
Jason Holder|R|b10|-|-
Keacy Carty|R|b9|-|-
Justin Greaves|R|b10|-|-
Odean Smith|R|b7|-|-
Matthew Forde|R|b9|-|-
Jayden Seales|L|b7|-|-
Shamar Joseph|L|b6|-|-
Yannic Cariah|L|b8|-|-
Akeem Jordan|R|b7|-|-
Kevin Sinclair|R|b11|-|-
Amir Jangoo|L|-|-|-
Kirk McKenzie|L|b11|-|-
Khary Pierre|L|b4|-|-
Alick Athanaze|L|b11|-|-
Tanzid Hasan|L|-|-|-
Litton Das|R|b11|-|-
Najmul Hossain Shanto|L|b11|-|-
Towhid Hridoy|R|b11|-|-
Shakib Al Hasan|L|b4|-|-
Mahmudullah|R|b11|-|S
Rishad Hossain|R|b8|R|-
Taskin Ahmed|L|b6|-|-
Mustafizur Rahman|L|b1|-|-
Tanzim Hasan Sakib|R|b7|-|-
Shoriful Islam|L|b3|-|-
Soumya Sarkar|L|b10|-|-
Mushfiqur Rahim|R|b11|-|-
Jaker Ali|R|-|-|-
Mahmudul Hasan Joy|R|b11|-|-
Parvez Hossain Emon|L|-|-|-
Afif Hossain|L|b11|-|-
Mehidy Hasan Miraz|R|b11|-|-
Nasum Ahmed|L|b4|-|-
Shamim Hossain|L|b11|-|-
Hasan Mahmud|R|b7|-|-
Al-Amin Hossain|R|b10|-|-
Ebadot Hossain|R|b6|-|-
Nazmul Hossain|R|b6|-|-
Rony Talukdar|R|b10|-|-
Zakir Hasan|L|-|-|-
Taijul Islam|L|b4|-|-
Nurul Hasan Sohan|R|-|-|-
Tanvir Islam|L|b4|-|-
Rakibul Hasan|L|b4|-|-
Rahmanullah Gurbaz|R|-|-|-
Ibrahim Zadran|R|b10|-|-
Gulbadin Naib|R|b7|-|-
Azmatullah Omarzai|R|b7|-|-
Mohammad Nabi|R|b11|-|-
Najibullah Zadran|L|b11|-|-
Rashid Khan|R|b8|-|-
Karim Janat|R|b10|-|-
Noor Ahmad|R|b4|-|-
Naveen-ul-Haq|R|b10|-|-
Fazalhaq Farooqi|R|b0|-|-
Sediqullah Atal|L|-|-|-
Rahmat Shah|R|b8|-|-
Hashmatullah Shahidi|L|b11|-|-
Riaz Hassan|R|-|-|-
Darwish Rasooli|R|b11|-|-
Ikram Alikhil|L|-|-|-
Mohammad Ishaq|R|-|-|-
Sharafuddin Ashraf|R|b4|-|-
Qais Ahmad|R|b8|-|-
Zia-ur-Rehman|R|b4|-|S
Yamin Ahmadzai|R|b10|-|-
Abdul Rahman|L|b4|-|S
Wafadar Momand|R|b9|-|-
Mujeeb Ur Rahman|R|b11|-|-
Allah Mohammad Ghazanfar|R|b11|-|-
Bahir Shah|R|b11|-|-
Fareed Ahmad|L|b1|-|-
Paul Stirling|R|b11|-|S
Andrew Balbirnie|R|b11|-|-
""",
        """
Lorcan Tucker|R|-|-|-
Harry Tector|R|b11|-|S
Curtis Campher|R|b10|-|-
George Dockrell|R|b4|-|-
Gareth Delany|R|b8|-|-
Mark Adair|R|b7|R|-
Barry McCarthy|R|b7|-|-
Josh Little|R|b0|-|-
Craig Young|R|b7|-|-
Ross Adair|R|b4|-|-
Neil Rock|L|-|-|-
Fionn Hand|R|b9|-|-
Ben White|R|b8|-|-
Graham Hume|L|b7|-|-
Matthew Humphreys|R|b4|-|-
Joylord Gumbie|R|-|-|-
Wessly Madhevere|R|b11|-|-
Brian Bennett|R|b11|R|S
Sikandar Raza|R|b11|-|-
Sean Williams|L|b4|-|-
Ryan Burl|L|b8|-|-
Clive Madande|R|-|W|-
Luke Jongwe|R|b9|-|-
Wellington Masakadza|L|b4|-|S
Richard Ngarava|L|b1|-|-
Blessing Muzarabani|R|b7|-|-
Tadiwanashe Marumani|L|b11|W|-
Dion Myers|R|b9|-|-
Tony Munyonga|R|b11|-|-
Tashinga Musekiwa|R|b9|-|-
Trevor Gwandu|R|b9|-|P
Newman Nyamhuri|R|b3|-|-
Max O'Dowd|R|b11|-|-
Michael Levitt|R|b7|-|-
Vikramjit Singh|L|b10|-|-
Scott Edwards|R|-|-|-
Sybrand Engelbrecht|R|b11|-|-
Bas de Leede|R|b9|-|-
Logan van Beek|R|b10|-|-
Tim Pringle|R|b4|R|-
Aryan Dutt|R|b11|-|-
Paul van Meekeren|R|b7|-|-
Vivian Kingma|R|b10|-|-
Wesley Barresi|R|b11|-|-
Teja Nidamanuru|R|b11|-|-
Saqib Zulfiqar|R|b4|-|-
Noah Croes|R|b9|-|-
Kyle Klein|R|b9|-|-
Fred Klaassen|R|b3|-|-
George Munsey|L|b10|-|-
Michael Jones|R|b11|-|-
Brandon McMullen|R|b9|-|-
Richie Berrington|R|b10|-|-
Matthew Cross|R|-|-|-
Michael Leask|R|b11|-|-
Chris Greaves|R|b8|-|-
Mark Watt|L|b4|-|-
Chris Sole|R|b6|-|-
Safyaan Sharif|R|b7|-|-
Brad Wheal|R|b7|-|-
Oli Hairs|L|b11|-|-
Charlie Tear|R|-|W|-
Jack Jarvis|R|b10|-|-
Bradley Currie|R|b3|-|-
Alasdair Evans|R|b7|-|-
Hamza Tahir|R|b4|-|-
Kushal Bhurtel|R|b8|-|S
Aasif Sheikh|R|-|-|-
Rohit Paudel|R|b11|R|S
Anil Sah|R|b11|W|-
Dipendra Singh Airee|R|b11|-|S
Kushal Malla|L|b4|-|S
Gulshan Jha|L|b9|-|P
Sompal Kami|R|b7|R|-
Karan KC|R|b7|R|-
Sandeep Lamichhane|R|b8|-|-
Abinash Bohara|R|b9|-|-
Bhim Sharki|R|b11|-|-
Aarif Sheikh|R|b9|-|-
Dev Khanal|R|b11|-|-
Sundeep Jora|R|-|-|-
Lalit Rajbanshi|R|b4|-|-
Mousom Dhakal|R|b11|-|S
Muhammad Waseem|R|b9|-|-
Aryan Lakra|L|b4|R|S
Vriitya Aravind|R|-|-|-
Rohan Mustafa|L|b11|-|-
Basil Hameed|R|b11|-|S
Aayan Afzal Khan|R|b4|-|-
Ali Naseer|L|b3|-|-
Zahoor Khan|R|b10|-|-
Junaid Siddique|L|b11|-|S
Muhammad Jawadullah|L|b1|-|P
Akif Raja|L|b10|-|-
Alishan Sharafu|R|b7|-|-
Asif Khan|R|-|-|-
Rahul Bhatia|L|b4|-|-
Karthik Meiyappan|R|b8|-|-
Sanchit Sharma|R|b9|-|-
Kashyap Prajapati|R|b11|-|-
Naseem Khushi|R|-|-|-
Aqib Ilyas|R|b11|-|S
Zeeshan Maqsood|L|b4|-|-
Khalid Kail|R|-|-|-
Ayan Khan|L|-|-|-
Rafiullah|R|b9|R|-
Shakeel Ahmed|R|b11|-|S
Kaleemullah|R|b9|-|P
Bilal Khan|L|b1|-|-
Pratik Athavale|R|-|-|-
Vinayak Shukla|R|-|-|-
Fayyaz Butt|-|b10|-|P
Nadeem Khan|R|b4|-|S
Steven Taylor|L|b11|-|S
Monank Patel|R|-|-|-
Andries Gous|R|-|W|-
Aaron Jones|R|b8|-|-
Nitish Kumar|R|b11|-|S
Corey Anderson|L|b3|-|-
Harmeet Singh|L|b4|-|-
Shadley van Schalkwyk|L|b9|R|-
Ali Khan|R|b7|-|-
Saurabh Netravalkar|R|b1|-|-
Milind Kumar|R|b11|-|-
Shayan Jahangir|R|b7|-|-
Kyle Phillip|R|b9|-|-
Nosthush Kenjige|L|b4|-|-
Yasir Mohammad|L|b8|-|-
Jessy Singh|R|b9|-|-
Fabian Allen|R|b4|-|-
Shamar Springer|R|b10|-|-
Jahmar Hamilton|R|-|-|-
Rahkeem Cornwall|R|b11|-|-
Anderson Phillip|R|b7|-|-
Karima Gore|R|b4|-|-
Joshua James|R|b9|-|-
Sufiyan Muqeem|-|b5|-|-
Tajinder Singh|R|b11|-|S
Zachary Carter|L|b4|-|-
Kadeem Alleyne|R|b9|-|-
Ramon Simmonds|-|b1|-|-
Shadrack Descarte|L|b10|-|-
Zishan Motara|R|b8|-|-
Rivaldo Clarke|R|b9|-|P
Johann Layne|R|b7|-|-
Kofi James|R|b11|-|S
Chris Green|R|b11|-|-
George Linde|L|b4|-|-
Daniel Sams|R|b1|-|-
Ronaldo Alimohamed|R|b7|-|P
""",
        """
Veerasammy Permaul|R|b4|-|-
Matthew Nandu|L|b11|-|-
Jonathan van Lange|R|b9|-|-
Isai Thorne|R|b9|-|-
Quentin Sampson|R|b10|-|-
Imran Tahir|R|b8|-|-
Dwaine Pretorius|R|b10|-|-
Keemo Paul|R|b7|-|-
Jediah Blades|R|b1|-|-
Shaqkere Parris|R|-|-|-
Vitel Lawes|L|b5|-|-
Kelvin Pitman|R|b6|-|-
Jeavor Royal|R|b4|-|-
Maaz Sadaqat|L|b4|-|-
Hassan Khan|R|b4|-|-
Hunain Shah|R|b7|-|-
Andre Fletcher|R|-|-|-
Kevin Wickham|R|b11|-|-
Ashmead Nedd|R|b4|-|-
Jeremiah Louis|R|b6|-|-
Navin Bidaisee|L|b8|-|-
Mikyle Louis|R|b8|-|S
Waqar Salamkheil|R|b4|-|-
Nikhil Chaudhary|R|b11|-|-
Jewel Andrew|R|-|W|-
Kamil Pooran|R|b9|-|-
McKenny Clarke|R|b7|-|P
Joshua Bishop|R|b4|-|S
Amari Goodridge|R|b9|-|-
Johann Jeremiah|L|b11|-|-
Ackeem Auguste|L|b8|-|S
Tim Seifert|R|-|-|-
Sunil Narine|L|b11|-|-
Kieron Pollard|R|b9|-|-
Dominic Drakes|L|b3|-|-
Jyd Goolie|L|b11|-|-
Terrance Hinds|R|b10|-|P
Nathan Edward|L|b1|-|-
Joshua da Silva|R|-|-|-
Alex Hales|R|b9|-|-
Usman Tariq|R|b11|-|-
Colin Munro|L|b10|-|-
Amshi de Silva|R|b10|-|P
Ashen Bandara|R|b8|-|-
Binura Fernando|R|b3|-|-
Milan Rathnayake|L|b10|-|-
Thanuka Dabare|-|-|-|-
Movin Subasingha|R|b11|-|-
Malsha Tharupathi|R|b8|-|-
Sharujan Shanmuganathan|L|-|-|-
Wanuja Sahan|L|b4|-|S
Shahnawaz Dahani|R|b7|-|-
Rubin Hermann|L|-|W|-
Ripon Mondol|R|b9|-|P
Dinesh Chandimal|R|b11|-|-
Sahibzada Farhan|R|-|-|-
Niroshan Dickwella|L|-|-|-
Pavan Rathnayake|R|b11|-|-
Akila Dananjaya|L|b11|-|-
Dhananjaya Lakshan|L|b10|-|-
Sachitha Jayathilake|R|b8|-|S
Marques Ackerman|L|b11|-|-
Chris Lynn|R|b4|-|-
Lasith Croospulle|R|b11|B|-
Vijayakanth Viyaskanth|R|b8|-|-
Tharindu Rathnayake|L|b4|-|S
Dinura Kalupahana|R|b9|-|P
Sachindu Colombage|R|b8|-|-
Sam Harper|R|-|-|-
Tom Rogers|L|b9|-|P
Virandeep Singh|R|b4|-|-
Mohamed Shiraz|R|b10|L|P
Chamindu Wickramasinghe|L|b7|-|P
Kugathas Mathulan|R|b10|-|P
Nishan Madushka|R|-|W|-
Vijay Shankar|R|b9|-|-
Garuka Sanketh|L|b10|-|-
Muditha Lakshan|R|b4|-|-
Dushan Hemantha|R|b8|-|-
Isitha Wijesundara|L|b7|-|-
Dale Phillips|R|b7|-|-
Zahir Khan|L|b4|-|-
Abdullah Tarakhail|-|-|-|-
Michael Pepper|R|-|-|-
Ravija Sandaruwan|R|b11|-|S
Abdullah Ahmadzai|R|b10|-|-
Dan Lawrence|R|b11|-|S
David Payne|R|b1|-|-
Ishtiaq Ahmed|R|b11|-|S
Joe Clarke|R|-|-|-
Sanjay Pahal|R|b9|-|-
Faf du Plessis|R|b8|-|-
Matt Potts|R|b7|-|-
Max Holden|L|b11|-|-
Imran Mir|R|-|-|-
Matiullah Khan|R|b7|-|-
Shubham Ranjane|-|-|-|-
Asa Tribe|R|b11|-|-
James Coles|R|b4|-|-
Jordan Thompson|L|b10|-|-
Wasim Akram|L|b0|-|-
Craig Overton|R|b10|-|-
James Rew|L|b4|-|-
Tanish Suri|R|-|W|-
Tom Banton|R|b11|-|-
Mackenzie Harvey|L|b9|-|-
Thomas Kelly|R|-|-|-
Lloyd Pope|R|b8|-|-
Jason Sangha|R|b8|-|-
Liam Scott|R|b7|-|-
Tom Straker|R|b9|-|-
Henry Thornton|R|b6|-|-
Jerrssis Wadia|L|b4|-|-
Tom Curran|R|b7|-|-
Lachlan Hearne|L|b11|-|-
Usman Khawaja|L|b11|-|-
Matthew Kuhnemann|L|b4|-|-
Nathan McSweeney|R|b11|-|-
Michael Neser|R|b10|-|-
Matt Renshaw|L|b11|-|-
Callum Vidler|R|b6|-|-
Hugh Weibgen|R|b11|-|-
Jack Wildermuth|R|b10|-|-
Marcus Bean|L|b1|-|P
Nick Hobson|L|b9|-|-
Iain Carlisle|L|b6|-|-
Riley Meredith|R|b6|-|-
Mitchell Owen|R|b9|-|-
Will Prestwidge|L|b6|-|-
Billy Stanlake|L|b6|-|-
Tim Ward|L|-|-|-
Jake Weatherald|L|b8|-|-
Beau Webster|R|b11|-|-
Ben Manenti|R|b11|-|-
Josh Brown|R|b9|-|-
Harry Dixon|L|b11|-|-
Brendan Doggett|R|b7|-|-
Sam Elliott|R|b7|-|-
Caleb Jewell|L|-|-|-
Oliver Peake|L|b11|-|-
Callum Stow|R|b5|L|S
Will Sutherland|R|b10|-|-
Austin Anlezark|L|b7|L|-
Hilton Cartwright|R|b9|-|-
Liam Hatcher|R|b10|-|-
Campbell Kellaway|L|b4|-|-
Todd Murphy|L|b11|-|-
Jamie Overton|R|b6|-|-
Peter Siddle|R|b7|-|-
Mitchell Swepson|R|b8|-|-
""",
        """
Mahli Beardman|R|b7|-|-
Cooper Connolly|L|b4|-|-
Brody Couch|L|b6|-|-
Zak Crawley|R|b11|-|-
Joel Curtis|L|-|-|-
Calvin Harrison|R|b8|-|-
Will Malajczuk|R|b11|-|S
Lance Morris|R|b6|-|-
Joel Paris|L|b1|-|-
Jhye Richardson|R|b6|-|-
Ashton Turner|R|b11|-|-
Jack Edwards|R|b9|-|-
Joel Davies|L|b4|-|-
Oliver Davies|R|b11|-|-
Hayden Kerr|R|b1|-|P
Riley Kingsell|R|b9|-|-
Fergus O'Neill|R|b7|-|-
Corey Rocchiccioli|R|b11|-|-
Jake Scott|L|b7|-|-
Lachlan Shaw|R|-|-|-
Jordan Silk|R|b9|-|-
Cameron Bancroft|R|b11|-|-
Matthew Gilkes|L|b9|-|-
Ryan Hadley|R|b10|-|-
Sam Konstas|R|-|-|-
Aidan O'Connor|R|b10|-|-
Nic Maddinson|L|b4|-|-
Unmukt Chand|R|b11|-|-
Saif Badar|R|b8|-|-
Carmi le Roux|R|b1|-|-
Kristopher Ramsaran|R|b5|-|S
Ehsan Adil|-|b7|-|-
Agni Chopra|L|b11|-|-
Rushil Ugarkar|R|b7|-|-
Lhuan-dre Pretorius|L|-|W|-
Hammad Azam|R|b7|-|-
Zia Ul-Haq|R|b1|-|-
Mohammad Ilyas|R|b8|-|S
Raymon Reifer|L|b3|-|-
Rahul Jariwala|R|-|-|-
Cameron Gannon|R|b7|-|-
Sujit Nayak|L|b4|-|-
Shehan Jayasuriya|L|b11|-|-
Sharad Lumba|-|-|-|-
Rilee Rossouw|L|b11|-|-
Hardus Viljoen|R|b6|-|-
Saiteja Mukkamalla|R|b11|-|-
Mohammad Mohsin|L|b8|-|S
Calvin Savage|R|b6|-|-
Smit Patel|R|-|-|-
Obus Pienaar|-|-|-|-
Mukhtar Ahmed|R|b8|-|-
Ian Holland|R|b9|-|-
Lahiru Milantha|L|-|-|-
Amila Aponso|R|b4|-|-
Abhishek Paradkar|L|b3|-|P
Asif Mehmood|R|b9|L|-
David Bedingham|R|b11|-|-
Eathan Bosch|R|b6|-|-
Daryn Dupavillon|R|b6|-|-
Simon Harmer|R|b11|-|-
Evan Jones|-|-|-|-
Kyle Simmonds|-|-|-|-
Andile Simelane|R|b6|-|-
Leus du Plooy|L|b4|-|-
Matthew de Villiers|R|b9|-|-
Dian Forrester|L|b6|-|-
Duan Jansen|R|b0|-|-
Janco Smit|R|b6|L|-
Dayyaan Galiem|R|b10|-|-
Jason Rowles|L|b4|-|-
Thomas Kaber|-|-|-|-
Dane Piedt|R|b11|-|-
Jason Smith|R|b10|-|-
Tiaan van Vuuren|R|-|-|-
Daniel Smith|L|-|-|-
Thomas Rew|R|-|-|-
Delano Potgieter|L|b9|-|P
Kyle Verreynne|R|-|-|-
Nqobani Mokoena|R|b7|-|-
Connor Esterhuizen|R|-|-|-
Wihan Lubbe|L|b11|-|-
Bryce Parsons|L|b4|-|S
Gideon Peters|R|b6|-|-
Codi Yusuf|R|b9|-|-
Josh Breed|R|b8|-|-
Mitchell van Buuren|-|-|-|-
Jordan Hermann|L|b11|-|-
Lewis Gregory|R|b7|-|-
Senuran Muthusamy|L|b4|-|-
Patrick Kruger|R|b9|-|-
Lutho Sipamla|R|b10|-|-
Mohammed Shami|R|b6|-|-
Devdutt Padikkal|L|b11|-|-
Rahul Tripathi|R|b9|-|-
Venkatesh Iyer|L|b9|-|-
Harshal Patel|R|b7|-|-
Jitesh Sharma|R|-|-|-
Khaleel Ahmed|R|b1|-|-
Avesh Khan|R|b7|-|-
T. Natarajan|L|b2|-|-
Rahul Chahar|R|b8|-|-
Abhinav Manohar|R|b8|-|-
Karun Nair|R|b11|-|-
Angkrish Raghuvanshi|R|b4|-|-
Atharva Taide|L|b4|-|-
Nehal Wadhera|L|b8|-|-
Harpreet Brar|L|b4|-|-
Naman Dhir|R|b11|-|S
Mahipal Lomror|L|b4|-|S
Sameer Rizvi|R|b11|-|S
Abdul Samad|R|b8|-|S
Ashutosh Sharma|R|b9|-|-
Nishant Sindhu|L|b4|-|S
Aryan Juyal|R|-|-|-
Kumar Kushagra|R|-|-|-
Robin Minz|L|b4|-|-
Anuj Rawat|L|-|-|-
Luvnith Sisodia|L|b2|-|-
Vishnu Vinod|R|b10|-|-
Vaibhav Arora|R|b10|-|-
Rasikh Salam Dar|R|b7|-|-
Akash Madhwal|R|b10|-|-
Mohit Sharma|R|b7|-|-
Simarjeet Singh|R|b10|-|-
Yash Thakur|-|b7|-|-
Vijaykumar Vyshak|R|b9|-|-
Shreyas Gopal|R|b8|-|-
Mayank Markande|R|b8|-|-
Suyash Sharma|R|b8|-|-
Karn Sharma|L|b8|-|-
Kumar Kartikeya|R|b4|-|-
Manav Suthar|L|b4|-|-
Ajinkya Rahane|R|b11|-|-
Krunal Pandya|L|b4|-|-
Nitish Rana|L|b11|-|S
Akash Deep|R|b7|-|-
Tushar Deshpande|L|b9|-|-
Bhuvneshwar Kumar|R|b9|-|-
Mukesh Kumar|R|b10|-|-
Allah Ghazanfar|R|b11|-|-
Shubham Dubey|L|b11|-|-
Shaik Rasheed|R|b8|-|-
Himmat Singh|R|b11|-|-
Anshul Kamboj|R|b9|-|-
Arshad Khan|L|b2|-|-
Darshan Nalkande|R|b7|L|-
Anukul Roy|L|b4|-|S
Swapnil Singh|R|b4|L|S
Gurnoor Brar|L|b6|-|-
""",
        """
Mukesh Choudhary|L|b2|-|-
Arjun Tendulkar|L|b3|-|-
Zeeshan Ansari|R|b8|-|S
Manimaran Siddharth|R|b4|-|S
Digvesh Singh|R|b8|-|-
Manish Pandey|R|b9|-|-
Shahbaz Ahmed|L|b4|-|-
Deepak Hooda|R|b11|-|S
Sai Kishore|L|b4|-|-
Umran Malik|R|b6|-|-
Ishant Sharma|R|b7|-|-
Jaydev Unadkat|R|b2|-|-
Sachin Baby|L|b11|-|-
Yudhvir Singh|R|b10|L|-
Rajvardhan Hangargekar|R|b6|-|-
Ashwani Kumar|L|b3|-|-
Akash Singh|R|b3|-|-
Gurjapneet Singh|R|b1|-|-
Jayant Yadav|R|b11|-|S
Kuldeep Sen|R|b6|-|P
Priyansh Arya|L|b11|-|S
Manoj Bhandage|L|b10|-|-
Praveen Dubey|R|b8|-|S
Ajay Mandal|L|b4|-|S
Vipraj Nigam|R|b8|L|S
Krishnan Shrijith|L|-|-|-
Mohit Rathee|R|b8|-|S
Dushmantha Chameera|R|b6|-|-
Aniket Verma|R|b10|-|-
Raj Angad Bawa|L|b10|-|-
Musheer Khan|R|b4|-|S
Suryansh Shedge|R|b9|-|-
Kulwant Khejroliya|L|b3|-|-
Prince Yadav|R|b6|L|-
Yuvraj Chaudhary|L|b4|-|S
Kamlesh Nagarkoti|R|b6|L|-
Ramakrishna Ghosh|R|b10|-|-
Vaibhav Sooryavanshi|L|b4|-|-
Eshan Malinga|L|b10|-|-
Vignesh Puthur|-|b5|L|-
MS Dhoni|R|b9|-|-
Abishek Porel|L|-|-|-
Sai Sudharsan|L|b8|-|-
Rahul Tewatia|L|b8|-|-
Shahrukh Khan|R|b11|-|-
Varun Chakaravarthy|R|b8|-|-
Ramandeep Singh|R|b9|-|-
Ravi Bishnoi|R|b8|-|-
Mohsin Khan|L|b3|-|-
Ayush Badoni|R|b11|-|-
Shashank Singh|R|-|-|-
Prabhsimran Singh|R|-|-|-
Riyan Parag|R|b11|-|-
Dhruv Jurel|R|-|-|-
Sandeep Sharma|R|b7|-|-
Rajat Patidar|R|b11|-|-
Yash Dayal|R|b1|-|-
Nitish Kumar Reddy|R|b10|-|-
Prithvi Shaw|R|b11|-|-
Mayank Agarwal|R|b11|-|-
Sarfaraz Khan|R|b8|-|-
Wriddhiman Saha|R|b11|-|-
Shardul Thakur|R|b9|-|-
Umesh Yadav|R|b6|-|-
Piyush Chawla|L|b8|-|-
Navdeep Saini|R|b6|-|-
Chetan Sakariya|L|b3|-|-
Ayush Mhatre|R|b11|-|-
KS Bharat|R|-|-|-
James Anderson|L|b7|-|-
Mujeeb ur Rahman|R|b11|-|S
Aneurin Donald|R|b11|-|-
Khwaja Nafay|R|b11|-|-
Max Bryant|R|b9|-|-
David Willey|L|b1|-|-
Ansh Tandon|L|b7|-|-
Sanjay Krishnamurthi|R|b4|-|-
Tom Moores|L|-|-|-
Haider Ali|R|-|-|-
Dhruv Parashar|R|b11|-|S
David Wiese|R|b10|-|-
Akif Javed|R|b3|-|-
Hassan Nawaz|R|-|-|-
Ali Raza|R|b10|-|-
Toby Albert|R|b11|-|-
Abbas Afridi|R|b10|-|-
Habibur Rahman Sohan|R|-|-|-
Tom Kohler-Cadmore|R|b11|-|-
Richard Gleeson|R|b7|-|-
Sam Billings|R|-|-|-
Paul Walter|L|b3|-|-
Salman Irshad|R|b7|-|-
"""
    )
}
