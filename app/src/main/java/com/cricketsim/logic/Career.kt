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
    val createdAt: Long
) {
    val fullName: String get() = "$firstName $lastName"

    /** One spoken line about this career. */
    fun describe(): String = "$fullName, age $age. $country, $state. $club. Jersey number $jersey."
}

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
