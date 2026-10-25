package com.cricketsim.logic

/**
 * A match format the player designs themselves (Home > Advanced Customisation > Create Custom Format).
 *
 * A custom format is NOT one of the game's [MatchFormat]s: it is never shown on the Play match format screen and
 * it is meant to work only inside custom tournaments (still to come). Until then the player can create, read and
 * delete them.
 *
 * @param restrictionsEnabled whether the first [restrictionOvers] overs of each innings have a fielding restriction
 * @param restrictionOvers how many overs the restriction lasts (0 when there is none)
 * @param fieldersOutside how many fielders may stay outside the fielding circle during the restriction
 */
data class CustomFormat(
    val id: String,
    val name: String,
    val overs: Int,
    val restrictionsEnabled: Boolean = false,
    val restrictionOvers: Int = 0,
    val fieldersOutside: Int = 2
) {
    /** One fifth of the innings, rounded up, as in the game's own formats (4 of 20, 10 of 50). */
    val maxOversPerBowler: Int get() = (overs + 4) / 5

    /** A plain-language description, spoken as it stands on the summary and in the saved list. */
    fun description(): String {
        val oversText = if (overs == 1) "1 over a side" else "$overs overs a side"
        val restriction = if (restrictionsEnabled) {
            val period = if (restrictionOvers == 1) "the first over" else "the first $restrictionOvers overs"
            val fielders = when (fieldersOutside) {
                0 -> "no fielders may stand outside the circle"
                1 -> "1 fielder may stand outside the circle"
                else -> "$fieldersOutside fielders may stand outside the circle"
            }
            "Fielding restrictions for $period: $fielders."
        } else {
            "No fielding restrictions."
        }
        return "$oversText. $restriction"
    }

    companion object {
        const val MAX_NAME_LENGTH = 30
        const val MIN_OVERS = 1
        const val MAX_OVERS = 50
        /** The most fielders the player can allow outside the circle (the game's own restrictions use 2 or 3). */
        const val MAX_FIELDERS_OUTSIDE = 5
    }
}
