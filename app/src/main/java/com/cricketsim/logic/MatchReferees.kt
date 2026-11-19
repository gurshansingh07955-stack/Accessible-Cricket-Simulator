package com.cricketsim.logic

import kotlin.random.Random

/**
 * The match referees (patch 132). Every match in the game has one: Match info says "Match referee: X.", and in a career match of an international series or tournament
 * or of a franchise league the toss presenter names him in his welcome. The names are real match referees (a name and nothing else is used). A career match always
 * gets the same referee ([forMatch], from the competition and the fixture, so the toss and Match info agree); any other match picks one at random ([pick]).
 * The toss presenter has one recorded line for each name ("Today's match referee is ..."), numbered from 1 in the order of [NAMES] (clip toss_ref_N).
 */
object MatchReferees {
    val NAMES: List<String> = listOf(
        "Ranjan Madugalle",
        "Javagal Srinath",
        "Andy Pycroft",
        "Richie Richardson",
        "Chris Broad",
        "David Boon",
        "Jeff Crowe",
        "Roshan Mahanama",
        "Graeme Labrooy",
        "Mike Procter",
        "Clive Lloyd",
        "Gundappa Viswanath",
        "Raman Subba Row",
        "John Reid"
    )

    fun pick(): String = NAMES[Random.nextInt(NAMES.size)]

    /** The same referee every time for the same [key] (a competition and a fixture). */
    fun forMatch(key: String): String = NAMES[Math.floorMod(key.hashCode(), NAMES.size)]

    /** The clip number of a referee (1 to [NAMES].size), 0 if the name is not one of them. */
    fun indexOf(name: String): Int = NAMES.indexOf(name) + 1
}
