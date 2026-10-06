package com.cricketsim.logic

/**
 * National anthems, played after the toss: the home side's first, then the visitors'
 * (random order at a neutral ground). Each is a real recording, cut to its first ten seconds
 * with a fade-out.
 *
 * WHERE THE RECORDINGS COME FROM: Wikimedia Commons, each one listed there as public domain
 * (mostly United States Navy Band and other military-band recordings). They are NOT stored in
 * the repository. tools/fetch_audio.sh reads the `Anthem("CODE", "url", ...)` lines below,
 * downloads each file, trims and levels it with ffmpeg, and packs it into the audio pack as
 * anthem_<code>.mp3 (see the "Fetch audio into the app" workflow). Until that has been run, the
 * anthems are missing from the pack and are simply skipped.
 *
 * Teams with no recording here (West Indies, Scotland, Nepal, UAE) have no anthem step.
 * To add one, add an Anthem line with a public-domain or licensed recording's direct URL.
 */
object Anthems {

    class Anthem(val code: String, val sourceUrl: String, val credit: String)

    val ALL: List<Anthem> = listOf(
        Anthem("IND", "https://upload.wikimedia.org/wikipedia/commons/f/f3/Jana_Gana_Mana_%28instrumental%29_-_Indian_Armed_Forces_Orchestra.ogg", "Indian Armed Forces Orchestra"),
        Anthem("ENG", "https://upload.wikimedia.org/wikipedia/commons/0/03/United_States_Navy_Band_-_God_Save_the_Queen.oga", "United States Navy Band"),
        Anthem("AUS", "https://upload.wikimedia.org/wikipedia/commons/a/a6/U.S._Navy_Band%2C_Advance_Australia_Fair_%28instrumental%29.ogg", "United States Navy Band"),
        Anthem("PAK", "https://upload.wikimedia.org/wikipedia/commons/3/3a/Pakistani_national_anthem_-_United_States_Navy_Band.ogg", "United States Navy Band"),
        Anthem("RSA", "https://upload.wikimedia.org/wikipedia/commons/f/f0/%22The_Call_of_South_Africa%22_performed_by_the_ASAF_Choir_and_the_Cantare_Male_Voice_Choir.oga", "ASAF Choir and Cantare Male Voice Choir"),
        Anthem("NZ", "https://upload.wikimedia.org/wikipedia/commons/d/d6/God_Defend_New_Zealand_instrumental.ogg", "instrumental recording"),
        Anthem("SL", "https://upload.wikimedia.org/wikipedia/commons/8/8c/Sri_Lankan_national_anthem%2C_performed_by_the_United_States_Navy_Band.oga", "United States Navy Band"),
        Anthem("BAN", "https://upload.wikimedia.org/wikipedia/commons/7/7d/Amar_Sonar_Bangla_instrumental_by_US_Navy_Band.oga", "United States Navy Band"),
        Anthem("AFG", "https://upload.wikimedia.org/wikipedia/commons/0/0a/National_Anthem_of_Afghanistan_%28Instrumental%29.ogg", "instrumental recording"),
        Anthem("IRE", "https://upload.wikimedia.org/wikipedia/commons/7/7c/United_States_Navy_Band_-_Amhr%C3%A1n_na_bhFiann.ogg", "United States Navy Band"),
        Anthem("ZIM", "https://upload.wikimedia.org/wikipedia/commons/e/e5/National_Anthem_of_Zimbabwe%2C_performed_by_the_Royal_Australian_Navy_Band.ogg", "Royal Australian Navy Band"),
        Anthem("NED", "https://upload.wikimedia.org/wikipedia/commons/2/2e/United_States_Navy_Band_-_Het_Wilhelmus.ogg", "United States Navy Band"),
        Anthem("OMA", "https://upload.wikimedia.org/wikipedia/commons/5/5a/Peace_to_the_Sultan_%28%D9%86%D8%B4%D9%8A%D8%AF_%D8%A7%D9%84%D8%B3%D9%84%D8%A7%D9%85_%D8%A7%D9%84%D8%B3%D9%84%D8%B7%D8%A7%D9%86%D9%8A%29.ogg", "recording from Wikimedia Commons"),
        Anthem("USA", "https://upload.wikimedia.org/wikipedia/commons/2/25/%22The_Star-Spangled_Banner%22_performed_by_the_United_States_Navy_Band.mp3", "United States Navy Band")
    )

    /** The packed file name for a team's anthem. */
    fun fileName(code: String): String = "anthem_${code.lowercase()}.mp3"

    fun forTeam(team: Team): Anthem? = ALL.firstOrNull { it.code == team.countryCode }

    fun hasAny(a: Team, b: Team): Boolean = forTeam(a) != null || forTeam(b) != null

    /**
     * The order the two teams' anthems are played in: the home side first. A team is "at home"
     * when the stadium is listed as one of its home grounds; at a neutral ground (neither side
     * at home, or both) the order is random.
     */
    fun order(userTeam: Team, opponentTeam: Team, stadium: Stadium): List<Team> {
        val userHome = userTeam.id in stadium.homeTeamIds
        val opponentHome = opponentTeam.id in stadium.homeTeamIds
        return when {
            userHome && !opponentHome -> listOf(userTeam, opponentTeam)
            opponentHome && !userHome -> listOf(opponentTeam, userTeam)
            else -> listOf(userTeam, opponentTeam).shuffled()
        }
    }
}
