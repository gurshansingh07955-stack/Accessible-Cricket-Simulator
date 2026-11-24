package com.cricketsim.logic

/**
 * My Player > Player profile > Edit career (patch 143). The player can change everything he chose when he made his player EXCEPT his
 * name, age, country, state, club, school and college (those stay as they are): his jersey number, his role, his batting hand, his
 * bowling arm, style and phase, his batting position and number, his fielding position and the career difficulty. His RATINGS cannot be edited: they
 * are earned by playing (patch 145).
 *
 * A [Draft] holds the choices while they are being edited; [fix] makes them agree with each other (a batsman does not bowl, a
 * wicketkeeper-batsman keeps wicket, a batting number belongs to its position, a bowling style to its arm); [apply] writes them into the
 * career AND into his own entry of his club's squad, so the squad pages, the selectors and the matches see the same player.
 * A competition already under way picks the new role and style up before each of his matches ([CareerSeason.withUserPlayer]).
 */
object CareerEdit {
    /** Switches the Edit career button on the Player profile page off (the old behaviour: no editing). */
    const val ENABLED = true

    /** The things the player may change. */
    data class Draft(
        val jersey: Int,
        val role: String,
        val battingHand: String,
        val bowlingHand: String,
        val bowlingStyle: String,
        val battingPosition: String,
        val battingNumber: Int,
        val bowlingPhase: String,
        val fieldingPosition: String,
        val difficulty: String
    )

    /** The choices the career holds now. */
    fun draftOf(c: CareerProfile): Draft = Draft(
        jersey = c.jersey,
        role = c.role,
        battingHand = c.battingHand,
        bowlingHand = c.bowlingHand,
        bowlingStyle = c.bowlingStyle,
        battingPosition = c.battingPosition,
        battingNumber = c.battingNumber,
        bowlingPhase = c.bowlingPhase,
        fieldingPosition = c.fieldingPosition,
        difficulty = c.difficulty
    )

    /** The choices made to agree with each other; anything missing or impossible gets a sensible value. */
    fun fix(d: Draft): Draft {
        val role = if (CareerWorld.ROLES.any { it.label == d.role }) d.role else CareerWorld.ROLES.first().label
        val bowls = CareerWorld.bowls(role)
        val hand = if (!bowls) "" else if (d.bowlingHand in CareerWorld.BOWLING_HANDS) d.bowlingHand else CareerWorld.BOWLING_HANDS.first()
        val styles = CareerWorld.bowlingStyles(hand)
        val style = if (!bowls) "" else if (styles.any { it.label == d.bowlingStyle }) d.bowlingStyle else styles.first().label
        val phase = if (!bowls) "" else if (CareerWorld.BOWLING_PHASES.any { it.label == d.bowlingPhase }) d.bowlingPhase else CareerWorld.BOWLING_PHASES[1].label
        val battingHand = if (d.battingHand in CareerWorld.BATTING_HANDS) d.battingHand else CareerWorld.BATTING_HANDS.first()
        val position = if (CareerWorld.BATTING_POSITIONS.any { it.label == d.battingPosition }) d.battingPosition else CareerWorld.BATTING_POSITIONS[1].label
        val numbers = CareerWorld.battingNumbers(position)
        val number = if (d.battingNumber in numbers) d.battingNumber else numbers.first()
        val field = when {
            role == "Wicketkeeper-batsman" -> CareerWorld.WICKETKEEPER
            CareerWorld.FIELDING_POSITIONS.any { it.label == d.fieldingPosition } -> d.fieldingPosition
            else -> "Cover"
        }
        val difficulty = if (CareerWorld.DIFFICULTIES.any { it.label == d.difficulty }) d.difficulty else "Medium"
        return Draft(
            jersey = d.jersey.coerceIn(0, CareerWorld.MAX_JERSEY),
            role = role,
            battingHand = battingHand,
            bowlingHand = hand,
            bowlingStyle = style,
            battingPosition = position,
            battingNumber = number,
            bowlingPhase = phase,
            fieldingPosition = field,
            difficulty = difficulty
        )
    }

    /**
     * The career with the edited choices. His own entry in his club's squad follows (same place in the list, so his player id in the
     * competitions does not move). His ratings are left exactly as they are.
     */
    fun apply(c: CareerProfile, draft: Draft): CareerProfile {
        val d = fix(draft)
        val clubs = c.clubTeams?.map { club ->
            club.copy(players = club.players.map { p ->
                if (p.isUser) p.copy(
                    role = d.role,
                    battingHand = d.battingHand,
                    bowlingHand = d.bowlingHand,
                    bowlingStyle = d.bowlingStyle
                ) else p
            })
        }
        return c.copy(
            jersey = d.jersey,
            role = d.role,
            battingHand = d.battingHand,
            bowlingHand = d.bowlingHand,
            bowlingStyle = d.bowlingStyle,
            battingPosition = d.battingPosition,
            battingNumber = d.battingNumber,
            bowlingPhase = d.bowlingPhase,
            fieldingPosition = d.fieldingPosition,
            difficulty = d.difficulty,
            clubTeams = clubs
        )
    }
}
