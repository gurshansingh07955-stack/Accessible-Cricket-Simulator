package com.cricketsim.logic

/**
 * A match format the player designs themselves (Home > Advanced Customisation > Create Custom Format).
 *
 * A custom format is NOT one of the game's [MatchFormat]s: it is never shown on the Play match format screen and
 * it works only inside tournaments (custom tournaments, or a game tournament given the format in Edit Tournament).
 *
 * ONE DAY (the default): a limited-overs match. [overs] is the overs each side bats; the fielding restriction
 * fields apply.
 *
 * MORE THAN ONE DAY ([days] 2 to 5): played like a Test match, with sessions (lunch, tea, stumps). [overs] is then the
 * overs bowled each DAY (6 to 90), the day being three sessions. The rest of the multi-day settings: [testInnings]
 * (2 = one innings each, 4 = two each), [declarations], [followOnEnabled] with [followOnLead] (only with four innings),
 * [newBallEnabled] with [newBallOvers]. DRS and rain are tournament settings and work for these formats too.
 * Old saved formats have days 0 (read as one day).
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
    val fieldersOutside: Int = 2,
    val days: Int = 1,
    val testInnings: Int = 4,
    val declarations: Boolean = true,
    val followOnEnabled: Boolean = true,
    val followOnLead: Int = 200,
    val newBallEnabled: Boolean = true,
    val newBallOvers: Int = 80
) {
    /** True for a format of two to five days, played like a Test match. */
    val isMultiDay: Boolean get() = days > 1

    /** One fifth of the innings, rounded up, as in the game's own formats (4 of 20, 10 of 50). A multi-day match has no limit. */
    val maxOversPerBowler: Int get() = if (isMultiDay) 999 else (overs + 4) / 5

    /** A plain-language description, spoken as it stands on the summary and in the saved list. */
    fun description(): String {
        if (isMultiDay) return multiDayDescription()
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

    private fun multiDayDescription(): String {
        val sb = StringBuilder()
        sb.append("$days days, ").append(if (overs == 1) "1 over a day" else "$overs overs a day")
            .append(", in three sessions: lunch, tea and stumps. ")
        sb.append(if (testInnings == 2) "Two innings: one for each side. " else "Four innings: two for each side. ")
        sb.append(if (declarations) "Declarations are allowed. " else "No declarations. ")
        if (testInnings == 4) {
            sb.append(
                if (followOnEnabled) "Follow-on: a side that bats first and leads by $followOnLead runs or more may make the other side follow on. "
                else "No follow-on. "
            )
        }
        sb.append(if (newBallEnabled) "A new ball is available after $newBallOvers overs with the old one. " else "No new ball. ")
        if (restrictionsEnabled) {
            val period = if (restrictionOvers == 1) "the first over" else "the first $restrictionOvers overs"
            sb.append("Fielding restrictions for $period of each innings: ").append(
                when (fieldersOutside) {
                    0 -> "no fielders may stand outside the circle. "
                    1 -> "1 fielder may stand outside the circle. "
                    else -> "$fieldersOutside fielders may stand outside the circle. "
                }
            )
        } else {
            sb.append("No fielding restrictions. ")
        }
        sb.append("The match is drawn if the time runs out.")
        return sb.toString()
    }

    companion object {
        const val MAX_NAME_LENGTH = 30
        const val MIN_OVERS = 1
        const val MAX_OVERS = 50
        /** Overs a day in a multi-day format: three sessions, so at least two overs each. */
        const val MIN_OVERS_PER_DAY = 6
        const val MAX_OVERS_PER_DAY = 90
        const val MAX_DAYS = 5
        const val MAX_FOLLOW_ON_LEAD = 500
        /** The most fielders the player can allow outside the circle (the game's own restrictions use 2 or 3). */
        const val MAX_FIELDERS_OUTSIDE = 5
    }
}
