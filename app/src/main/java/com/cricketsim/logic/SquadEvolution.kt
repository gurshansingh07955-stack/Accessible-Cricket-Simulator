package com.cricketsim.logic

import kotlin.math.roundToInt

/**
 * My Player, part sixteen: the players who replace the computer squads' veterans. Players have no age in the game, so [CareerInternational.evolved] gives each one from his name
 * (24 to 37); when the career has gone on long enough for his age to reach [RETIRE_AGE] he retires and a young player of the same role and bowling style takes his
 * place, with a name from the country's list and ratings of 0.72 of the veteran's plus 14 (a 90 is replaced by a 79, a 50 by a 50), a little different for each. The same
 * veteran always has the same successor (everything is drawn from the veteran's id), so a saved competition and a new one agree.
 */
object SquadEvolution {

    const val RETIRE_AGE = 38

    private class Pool(val first: List<String>, val last: List<String>)

    private val POOLS: Map<String, Pool> = mapOf(
        "India" to Pool(
            listOf("Aarav", "Vihaan", "Arjun", "Rohan", "Karan", "Ishaan", "Dev", "Kabir", "Nikhil", "Yash", "Tanmay", "Pranav"),
            listOf("Sharma", "Verma", "Patel", "Reddy", "Nair", "Singh", "Gupta", "Joshi", "Kulkarni", "Menon", "Rao", "Chauhan")
        ),
        "Pakistan" to Pool(
            listOf("Hamza", "Bilal", "Zain", "Umar", "Faizan", "Shayan", "Danish", "Adnan", "Talha", "Rayyan", "Haider", "Moin"),
            listOf("Khan", "Malik", "Butt", "Ahmed", "Qureshi", "Chaudhry", "Siddiqui", "Raza", "Shah", "Iqbal", "Baig", "Mirza")
        ),
        "Australia" to Pool(
            listOf("Jack", "Oliver", "Noah", "Lachlan", "Mitchell", "Cooper", "Hunter", "Riley", "Beau", "Flynn", "Harry", "Max"),
            listOf("Taylor", "Walker", "Harris", "Cameron", "Kelly", "Murray", "Hughes", "Stewart", "Fraser", "Boyd", "Marsh", "Dawson")
        ),
        "England" to Pool(
            listOf("Harry", "George", "Oscar", "Freddie", "Archie", "Toby", "Callum", "Jamie", "Ollie", "Sam", "Rory", "Ben"),
            listOf("Clarke", "Wood", "Bennett", "Ward", "Palmer", "Holmes", "Fletcher", "Barker", "Gibson", "Hayes", "Lawson", "Pearce")
        ),
        "South Africa" to Pool(
            listOf("Thabo", "Pieter", "Johan", "Sipho", "Dewald", "Kyle", "Ruan", "Lwazi", "Jaco", "Tshepo", "Willem", "Zander"),
            listOf("Nkosi", "Botha", "Pretorius", "du Plessis", "Maseko", "Naidoo", "Steyn", "Venter", "Mokoena", "Jacobs", "Smit", "van Wyk")
        ),
        "New Zealand" to Pool(
            listOf("Liam", "Finn", "Tane", "Hamish", "Callum", "Ethan", "Mason", "Rhys", "Josh", "Cole", "Jacob", "Blair"),
            listOf("Anderson", "Tamihana", "Henare", "Reid", "Campbell", "Murphy", "Parata", "Bell", "Wilson", "Hogg", "Ngata", "Fraser")
        ),
        "Sri Lanka" to Pool(
            listOf("Kasun", "Dilshan", "Nuwan", "Pathum", "Chamika", "Sachith", "Lahiru", "Dhananjaya", "Thisara", "Ashen", "Janith", "Vimukthi"),
            listOf("Perera", "Fernando", "Silva", "Gunawardena", "Jayasuriya", "Rajapaksa", "Wickramasinghe", "Mendis", "Karunaratne", "Bandara", "Senanayake", "Dias")
        ),
        "West Indies" to Pool(
            listOf("Kemar", "Jason", "Devon", "Shamar", "Rovman", "Akeal", "Odean", "Jermaine", "Keacy", "Alzarri", "Kyle", "Brandon"),
            listOf("Brathwaite", "Holder", "Hope", "Pooran", "Joseph", "Lewis", "Allen", "Charles", "Walsh", "Seales", "Thomas", "Greaves")
        ),
        "Bangladesh" to Pool(
            listOf("Rahim", "Tamim", "Mehedi", "Nasum", "Shakib", "Litton", "Hasan", "Taskin", "Rubel", "Afif", "Saif", "Mahmud"),
            listOf("Hossain", "Rahman", "Islam", "Ahmed", "Das", "Mondol", "Chowdhury", "Sarkar", "Miah", "Khan", "Uddin", "Karim")
        ),
        "Afghanistan" to Pool(
            listOf("Rashid", "Mujeeb", "Najibullah", "Ikram", "Hazratullah", "Fazal", "Noor", "Gulbadin", "Abdul", "Karim", "Naveen", "Darwish"),
            listOf("Zadran", "Ahmadzai", "Khil", "Rahmani", "Omarzai", "Farooqi", "Shinwari", "Stanikzai", "Mohammadi", "Safi", "Noori", "Karimi")
        )
    )

    private fun nameFor(country: String?, h: Int): String {
        val pool = (if (country != null) POOLS[country] else null) ?: POOLS.values.toList()[h % POOLS.size]
        return pool.first[(h / 7) % pool.first.size] + " " + pool.last[(h / 91) % pool.last.size]
    }

    /**
     * The young player who takes a retired veteran's place in a squad. [nameCountry] picks the list of names (a national team's own name, or the country of a domestic team);
     * null or unknown mixes the lists, as a franchise squad does.
     */
    fun successor(team: Team, veteran: Player, nameCountry: String?): Player {
        val h = veteran.id.hashCode() and 0x7fffffff
        fun rate(r: Int): Int = (r * 0.72 + 14.0 + (h % 7) - 3).roundToInt().coerceIn(10, 99)
        return veteran.copy(
            id = "gen_${veteran.id}",
            name = nameFor(nameCountry ?: team.name, h),
            battingRating = rate(veteran.battingRating),
            bowlingRating = rate(veteran.bowlingRating),
            fieldingRating = rate(veteran.fieldingRating)
        )
    }
}
