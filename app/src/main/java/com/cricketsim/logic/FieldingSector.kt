package com.cricketsim.logic

/**
 * Ported from a type alias in helpers/fieldingSystem.tsx. Pulled out
 * into its own file since BattingSystem.kt needs it now (for
 * ShotDirection) ahead of the full FieldingSystem.kt port, following
 * the same split-out pattern as PitchType.kt and MatchFormat.kt.
 *
 * The fielding "directions" a captain can place fielders in, arranged
 * clockwise around the field starting from straight down the ground on
 * the off side. Slip/gully is a single close-catching sector (it never
 * has a "deep" variant in real cricket); most other sectors have a
 * short (inside the 30-yard circle) and deep (boundary) variant, and a
 * couple (point, square leg) additionally have a very close "silly"/
 * "short leg" variant. Leg Slip, Long Stop, and Cow Corner are
 * additional single-depth positions available to move a fielder to,
 * beyond the 9 that are filled by default.
 */
enum class FieldingSector {
    SLIP_GULLY, LEG_SLIP, THIRD_MAN, POINT, COVER, MID_OFF,
    LONG_STOP, MID_ON, MID_WICKET, COW_CORNER, SQUARE_LEG, FINE_LEG
}
