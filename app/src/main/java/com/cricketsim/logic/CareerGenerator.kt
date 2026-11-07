package com.cricketsim.logic

import java.util.Random

/**
 * Makes the district clubs of a career: for the player's state, every district club gets a squad of 15 players made up at
 * random, with names that suit the state (a Punjabi, Tamil, Welsh or Zulu name pool, as the state asks), a role, hands, a bowling
 * style and batting, bowling and fielding ratings. The player's own club carries the player himself in the place of a team-mate
 * of his role. Club cricket is below the game's domestic level, so the ratings run roughly 20 to 62 (the game's players go to 95).
 * The player starts a little below the middle of his club and grows as he plays (later parts).
 */
object CareerGenerator {
    const val SQUAD_SIZE = 15

    /** Which name pool a state uses: country, state to pool. Anything not listed uses the country's main pool. */
    private fun region(country: String, state: String): String = when (country) {
        "India" -> when (state) {
            "Punjab", "Haryana", "Chandigarh", "Himachal Pradesh", "Delhi" -> "punjabi"
            "Uttar Pradesh", "Bihar", "Madhya Pradesh", "Rajasthan", "Chhattisgarh", "Jharkhand", "Uttarakhand" -> "hindi"
            "Maharashtra" -> "marathi"
            "Gujarat" -> "gujarati"
            "West Bengal", "Tripura" -> "bengali"
            "Odisha" -> "odia"
            "Assam", "Arunachal Pradesh", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Sikkim" -> "northeast"
            "Andhra Pradesh", "Telangana" -> "telugu"
            "Karnataka" -> "kannada"
            "Tamil Nadu", "Puducherry" -> "tamil"
            "Kerala" -> "malayali"
            "Goa" -> "konkani"
            "Jammu and Kashmir" -> "kashmiri"
            else -> "hindi"
        }
        "England" -> if (state == "Glamorgan") "welsh" else "english"
        "Australia" -> "australian"
        "New Zealand" -> "newzealand"
        "South Africa" -> when (state) {
            "Western Province", "Boland", "South Western Districts", "Northern Cape", "Knights" -> "afrikaans"
            "Dolphins", "Warriors", "Border", "KZN Inland", "Limpopo", "Mpumalanga", "North West" -> "african"
            else -> "mixedsa"
        }
        "Pakistan" -> when (state) {
            "Peshawar", "FATA", "Abbottabad" -> "pashtun"
            "Karachi", "Hyderabad", "Larkana" -> "sindhi"
            "Quetta", "Dera Murad Jamali" -> "baloch"
            "Azad Jammu and Kashmir" -> "kashmiri_pk"
            else -> "pk_punjabi"
        }
        else -> "english"
    }

    // pool | first names | surnames
    private val POOLS: Map<String, Pair<List<String>, List<String>>> by lazy {
        """
punjabi|Gurpreet,Harpreet,Jaspreet,Manpreet,Arshdeep,Simranjeet,Gagandeep,Harjot,Navdeep,Kuldeep,Rajveer,Amritpal,Sukhdev,Baljit,Karanveer,Taranjeet,Ravneet,Harman|Singh,Gill,Sandhu,Dhillon,Brar,Sidhu,Bajwa,Grewal,Cheema,Randhawa,Mann,Virk,Bedi,Kohli,Malhotra,Sethi,Chauhan,Dhaliwal
hindi|Aman,Rohit,Vikas,Ankit,Deepak,Sachin,Mohit,Rahul,Nitin,Sumit,Ravi,Piyush,Shivam,Abhishek,Yash,Kartik,Ayush,Harsh|Sharma,Verma,Yadav,Gupta,Mishra,Tiwari,Pandey,Chaudhary,Kumar,Prasad,Jha,Singh,Rawat,Joshi,Bisht,Mehra,Saxena,Dubey
marathi|Aniket,Omkar,Sagar,Swapnil,Prathamesh,Rohan,Siddhesh,Tushar,Akshay,Chinmay,Shubham,Vaibhav,Pranav,Ruturaj,Ketan,Mandar,Nikhil,Atharva|Patil,Jadhav,Pawar,Shinde,Kulkarni,Deshmukh,More,Bhosale,Gaikwad,Kadam,Joshi,Chavan,Sawant,Naik,Phadke,Rane,Kale,Thakur
gujarati|Hardik,Jaydev,Chirag,Parth,Axar,Dhruv,Krunal,Bhargav,Smit,Yash,Nirav,Harshil,Jignesh,Kishan,Priyank,Meet,Vatsal,Rajan|Patel,Shah,Mehta,Desai,Joshi,Trivedi,Parmar,Solanki,Vyas,Thakkar,Chauhan,Dave,Pandya,Modi,Bhatt,Raval,Gandhi,Sheth
bengali|Abhimanyu,Sourav,Wriddhiman,Shahbaz,Anustup,Ritwik,Subhajit,Debashis,Arnab,Soumya,Sayan,Pritam,Koushik,Anirban,Rajarshi,Tanmoy,Sudip,Indrajit|Banerjee,Chatterjee,Mukherjee,Ganguly,Dutta,Ghosh,Das,Roy,Sen,Bose,Mondal,Chakraborty,Saha,Majumdar,Paul,Dey,Biswas,Sarkar
odia|Biplab,Subhranshu,Rajesh,Debasis,Suryakant,Sandeep,Abhijit,Basant,Ashutosh,Prasanta,Anurag,Siddhant,Deepak,Rakesh,Santosh,Swastik,Lipun,Amit|Mohanty,Behera,Panda,Sahoo,Nayak,Mishra,Das,Pradhan,Swain,Rout,Patra,Jena,Dash,Samal,Parida,Barik,Mahapatra,Sethy
northeast|Rongsen,Kishan,Lalremruata,Imliwati,Hrishikesh,Nabam,Tashi,Rex,Sentinaro,Khrievitso,Pranjal,Riyan,Abhilash,Denish,Tenzing,Bikash,Kamal,Jonathan|Das,Borah,Gogoi,Hazarika,Bora,Sangma,Marak,Lyngdoh,Khongsit,Lalthazuala,Zote,Singh,Ao,Sema,Angami,Lepcha,Bhutia,Tamang
telugu|Hanuma,Ambati,Venkat,Srikar,Ricky,Nitish,Tilak,Prasidh,Kona,Ashwin,Sai,Mohammed,Ravi,Harsha,Vamsi,Bhargav,Karthik,Pavan|Reddy,Naidu,Rao,Varma,Raju,Chowdary,Goud,Yadav,Babu,Kumar,Prasad,Murthy,Sharma,Verma,Swamy,Krishna,Teja,Das
kannada|Mayank,Devdutt,Manish,Karun,Shreyas,Vidwath,Abhishek,Krishnappa,Rohan,Pavan,Praveen,Anirudh,Sharath,Nikhil,Gautham,Vyshak,Kishore,Harsha|Agarwal,Padikkal,Pandey,Nair,Gopal,Kaverappa,Shetty,Gowda,Hegde,Rao,Bhat,Kamath,Prasad,Naik,Kumar,Murthy,Achar,Pai
tamil|Murali,Washington,Ravichandran,Dinesh,Sai,Varun,Natarajan,Sandeep,Baba,Jagadeesan,Sanjay,Vijay,Kannan,Suresh,Arun,Prasanth,Karthik,Mukund|Vijay,Sundar,Ashwin,Karthik,Sudharsan,Chakravarthy,Warrior,Indrajith,Shankar,Subramanian,Krishnan,Iyer,Rajan,Kumar,Balaji,Murugan,Pandian,Selvam
malayali|Sanju,Sachin,Basil,Vishnu,Rohan,Jalaj,Sreesanth,Krishna,Akshay,Midhun,Abhijith,Anand,Arun,Nidheesh,Vinoop,Sijomon,Rahul,Jithin|Samson,Baby,Thampi,Vinod,Kunnummal,Saxena,Menon,Nair,Pillai,Varma,Panicker,Kurup,Joseph,Mathew,Thomas,Krishnan,Ravi,Kumar
konkani|Snehal,Suyash,Darshan,Amogh,Lakshay,Mohit,Ekanath,Sagun,Siddesh,Sumiran,Vasuki,Ketan,Abhishek,Rahul,Shubham,Mandar,Sanket,Nilesh|Kauthankar,Prabhu,Desai,Naik,Gaonkar,Fernandes,D'Souza,Pereira,Dessai,Kamat,Shirodkar,Parab,Borkar,Gaude,Rodrigues,Mhambre,Salgaonkar,Dias
kashmiri|Abdul,Umran,Auqib,Rasikh,Parvez,Shubham,Yawar,Mujtaba,Basit,Waseem,Irfan,Zahid,Bilal,Aamir,Faisal,Tariq,Owais,Danish|Malik,Dar,Bhat,Rasool,Lone,Wani,Khan,Mir,Sheikh,Ganai,Ahmad,Qureshi,Reshi,Peer,Khanday,Rather,Magray,Naik
english|Jack,Harry,Oliver,George,Charlie,Tom,James,Will,Sam,Ben,Joe,Luke,Ollie,Matthew,Callum,Ryan,Dan,Alex|Smith,Taylor,Brown,Wilson,Evans,Walker,Hughes,Wright,Roberts,Clarke,Turner,Hall,Cooper,Ward,Bennett,Morgan,Fox,Pearce
welsh|Rhys,Owain,Gareth,Dafydd,Iwan,Sion,Cai,Gethin,Huw,Callum,Tom,Jack,Evan,Morgan,Aled,Elis,Ioan,Llew|Jones,Williams,Davies,Evans,Thomas,Roberts,Hughes,Lewis,Morgan,Griffiths,Edwards,Owen,Price,Rees,Powell,Jenkins,Phillips,Bevan
australian|Jack,Mitch,Cameron,Josh,Matt,Nathan,Tim,Aaron,Marcus,Riley,Sean,Liam,Ben,Jake,Callum,Will,Hayden,Brendan|Smith,Jones,Williams,Brown,Wilson,Taylor,Anderson,Thompson,Walker,White,Harris,Martin,Cooper,Mitchell,Marsh,Hughes,Stewart,O'Brien
newzealand|Finn,Tim,Kane,Daryl,Henry,Will,Ben,Matt,Blair,Cole,Jacob,Luke,Rhys,Nic,Hamish,Tom,Mark,Ajaz|Williamson,Taylor,Latham,Conway,Young,Nicholls,Phillips,Boult,Southee,Wagner,Henry,Mitchell,Santner,Bracewell,Jamieson,Ferguson,Patel,Manu
afrikaans|Pieter,Johan,Hendrik,Willem,Jacques,Francois,Dewald,Wiaan,Ruan,Gerald,Riaan,Marco,Rassie,Quinton,Dane,Tristan,Heinrich,Janneman|van der Merwe,Botha,Pretorius,du Plessis,van Zyl,Steyn,Viljoen,Coetzee,Kruger,Nel,Joubert,Smit,Louw,Els,van Wyk,Bosman,Erasmus,Jansen
african|Thabo,Sipho,Lungi,Temba,Zakhele,Kagiso,Siya,Andile,Themba,Mpho,Tshepo,Lwazi,Sizwe,Bongani,Luthando,Khaya,Aphiwe,Thando|Dlamini,Nkosi,Mthembu,Ngidi,Maseko,Sithole,Mokoena,Khumalo,Zulu,Ndlovu,Mahlangu,Mabaso,Mkhize,Tshabalala,Cele,Mbatha,Radebe,Phiri
mixedsa|Kagiso,Aiden,Temba,Reeza,Dean,Keshav,Tony,Zubayr,Wayne,Ryan,Sisanda,Tabraiz,Lutho,Marco,Beuran,Pieter,Dwaine,Anrich|Rabada,Markram,Bavuma,Maharaj,Hendricks,Nortje,Shamsi,Elgar,Parnell,van der Dussen,Phehlukwayo,Miller,Jansen,Mulder,Hamza,Mpongwana,Ntini,Abrahams
pk_punjabi|Muhammad,Ahmed,Ali,Usman,Hassan,Hamza,Bilal,Imran,Faisal,Kamran,Shoaib,Zeeshan,Saad,Waqas,Adnan,Rizwan,Asif,Naseem|Khan,Butt,Malik,Chaudhry,Cheema,Gill,Bajwa,Warraich,Ahmed,Sheikh,Mirza,Ansari,Javed,Iqbal,Akhtar,Hussain,Raza,Rana
pashtun|Shahid,Sajid,Umar,Zaman,Fakhar,Haris,Naseem,Wahab,Younis,Shinwari,Gul,Rahim,Farhan,Asad,Junaid,Noman,Tahir,Rehan|Afridi,Yousafzai,Khattak,Mohmand,Shinwari,Orakzai,Wazir,Bangash,Durrani,Khalil,Mehsud,Tanoli,Swati,Gul,Marwat,Kakar,Niazi,Bhittani
sindhi|Sarfaraz,Jamal,Shahzaib,Zulfiqar,Imam,Ghulam,Wazir,Anwar,Ayaz,Hammad,Asadullah,Dilawar,Qasim,Farooq,Mubarak,Fahad,Taimoor,Abdullah|Memon,Soomro,Bhutto,Chandio,Jatoi,Mirani,Shaikh,Siddiqui,Ansari,Lashari,Qureshi,Mughal,Rajput,Khoso,Panhwar,Abro,Mangi,Junejo
baloch|Abdul,Mir,Jamil,Nasir,Salman,Dost,Hakeem,Sher,Yar,Mastoi,Zahoor,Gul,Shah,Khuda,Bakhsh,Habib,Rehmat,Noor|Baloch,Bugti,Raisani,Marri,Mengal,Jamali,Magsi,Rind,Lashari,Zehri,Bizenjo,Kurd,Khosa,Leghari,Gishkori,Dehwar,Bangulzai,Mandokhail
kashmiri_pk|Tariq,Zahid,Aamir,Nadeem,Shakeel,Arif,Waheed,Yasir,Khalid,Sajjad,Javed,Rafiq,Shahzad,Sohail,Mushtaq,Naveed,Bashir,Ejaz|Mir,Dar,Butt,Khawaja,Lone,Malik,Abbasi,Mughal,Awan,Chaudhry,Raja,Satti,Qureshi,Kiani,Sudhan,Gilani,Ghani,Rathore
        """.trimIndent().lines().filter { it.isNotBlank() }.associate { line ->
            val p = line.split("|")
            p[0] to (p[1].split(",") to p[2].split(","))
        }
    }

    private fun between(random: Random, low: Int, high: Int): Int = low + random.nextInt(high - low + 1)

    private fun makeName(pool: Pair<List<String>, List<String>>, random: Random, used: MutableSet<String>): String {
        var name: String
        var tries = 0
        do {
            name = pool.first[random.nextInt(pool.first.size)] + " " + pool.second[random.nextInt(pool.second.size)]
            tries++
        } while (name in used && tries < 60)
        // The pools are large but finite: a rare repeat gets an initial so two players never share a name.
        if (name in used) name = pool.first[random.nextInt(pool.first.size)].first() + ". " + name.substringAfter(" ")
        used.add(name)
        return name
    }

    /** The player's own starting ratings [batting, bowling, fielding]: modest, with his role's strength first. */
    fun startingRatings(role: String, random: Random): List<Int> = when (role) {
        "Batsman" -> listOf(between(random, 41, 47), between(random, 8, 14), between(random, 36, 44))
        "Wicketkeeper-batsman" -> listOf(between(random, 40, 46), between(random, 6, 10), between(random, 40, 46))
        "Bowler" -> listOf(between(random, 14, 20), between(random, 42, 48), between(random, 32, 40))
        "Batting all-rounder" -> listOf(between(random, 40, 46), between(random, 28, 34), between(random, 36, 42))
        else -> listOf(between(random, 28, 34), between(random, 40, 46), between(random, 36, 42))
    }

    private fun makePlayer(role: String, pool: Pair<List<String>, List<String>>, random: Random, used: MutableSet<String>): CareerPlayer {
        val bowls = CareerWorld.bowls(role)
        val hand = if (random.nextInt(100) < 22) "Left-arm" else "Right-arm"
        val style = if (bowls) {
            val styles = CareerWorld.bowlingStyles(hand)
            // Pace and medium are the more common; spin makes up about two in five.
            val pace = styles.take(3)
            val spin = styles.drop(3)
            (if (random.nextInt(100) < 60) pace[random.nextInt(pace.size)] else spin[random.nextInt(spin.size)]).label
        } else ""
        val (batting, bowling, fielding) = when (role) {
            "Batsman" -> Triple(between(random, 38, 62), between(random, 5, 18), between(random, 28, 60))
            "Wicketkeeper-batsman" -> Triple(between(random, 36, 58), between(random, 3, 10), between(random, 40, 62))
            "Bowler" -> Triple(between(random, 8, 26), between(random, 38, 62), between(random, 25, 55))
            "Batting all-rounder" -> Triple(between(random, 36, 56), between(random, 26, 46), between(random, 30, 58))
            else -> Triple(between(random, 26, 46), between(random, 36, 56), between(random, 30, 58))
        }
        return CareerPlayer(
            name = makeName(pool, random, used),
            role = role,
            battingHand = if (random.nextInt(100) < 24) "Left-handed" else "Right-handed",
            bowlingHand = if (bowls) hand else "",
            bowlingStyle = style,
            batting = batting,
            bowling = bowling,
            fielding = fielding
        )
    }

    /** One squad: two keepers, four batsmen, two batting and two bowling all-rounders and five bowlers (15). */
    private val SQUAD_ROLES = listOf(
        "Wicketkeeper-batsman", "Wicketkeeper-batsman",
        "Batsman", "Batsman", "Batsman", "Batsman",
        "Batting all-rounder", "Batting all-rounder",
        "Bowling all-rounder", "Bowling all-rounder",
        "Bowler", "Bowler", "Bowler", "Bowler", "Bowler"
    )

    /**
     * Every district club of the state with its squad; [user] takes the first place of his role (a bowler's role is the last
     * place) in the squad of his own club. All the names of a state are different.
     */
    fun generateClubs(country: String, state: String, userClub: String, user: CareerPlayer, random: Random): List<CareerClub> {
        val pool = POOLS[region(country, state)] ?: POOLS.getValue("english")
        val used = HashSet<String>()
        used.add(user.name)
        return CareerWorld.clubs(country, state).map { clubName ->
            val players = SQUAD_ROLES.map { makePlayer(it, pool, random, used) }.toMutableList()
            if (clubName == userClub) {
                val slot = players.indexOfFirst { it.role == user.role }.let { if (it >= 0) it else players.lastIndex }
                players[slot] = user
            }
            CareerClub(clubName, players.sortedBy { p -> SQUAD_ROLES_ORDER.indexOf(p.role) })
        }
    }

    private val SQUAD_ROLES_ORDER = CareerWorld.ROLES.map { it.label }
}
