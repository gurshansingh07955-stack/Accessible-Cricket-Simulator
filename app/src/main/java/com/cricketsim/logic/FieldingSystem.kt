package com.cricketsim.logic

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ported from helpers/fieldingSystem.tsx (the Floot/React web app
 * remains the source of truth for gameplay design). Pure Kotlin, no
 * Android dependencies. Note: the `FieldingSector` enum itself was
 * already split out into its own file (FieldingSector.kt) since
 * BattingSystem.kt needed it earlier — everything else from the web
 * source lives here.
 *
 * All numeric constants (sector angles, depth radii, the 0.3 attacking/
 * containing situational-bias thresholds, the 3/5 deep-fielder legality
 * caps) are exact matches to the web source.
 */

// --- Types ---

enum class FieldingDepth { CLOSE, SHORT, DEEP }

/** Only meaningful for the slip_gully sector, which toggles between these two close-in names instead of a short/deep depth change. */
enum class SlipGullyVariant { SLIP, GULLY }

data class FieldPlacement(
    val playerId: String,
    val sector: FieldingSector,
    val depth: FieldingDepth,
    val variant: SlipGullyVariant? = null
)

data class FieldPositionCoords(val x: Double, val y: Double)

data class DropResolution(val sector: FieldingSector, val depth: FieldingDepth)

private data class SectorGeometry(val angleDeg: Double)

private data class FieldSlotSpec(val sector: FieldingSector, val depth: FieldingDepth, val variant: SlipGullyVariant? = null)

/** Keys into FieldingSystem's internal FIELD_TEMPLATES map — the web source used plain string keys ("powerplay", "attacking", etc.), modeled here as an enum for exhaustiveness. */
private enum class FieldTemplateKey { POWERPLAY, ATTACKING, BALANCED, CONTAINING, BOUNCER_TRAP, YORKER_TRAP }

object FieldingSystem {

    // Clockwise angle (degrees, 0 = straight down the ground toward the
    // bowler) for every sector. Used to lay fielders out on the visual
    // field map.
    private val SECTOR_GEOMETRY: Map<FieldingSector, SectorGeometry> = mapOf(
        FieldingSector.MID_OFF to SectorGeometry(20.0),
        FieldingSector.COVER to SectorGeometry(50.0),
        FieldingSector.POINT to SectorGeometry(80.0),
        FieldingSector.SLIP_GULLY to SectorGeometry(105.0),
        FieldingSector.THIRD_MAN to SectorGeometry(145.0),
        FieldingSector.LONG_STOP to SectorGeometry(180.0),
        FieldingSector.FINE_LEG to SectorGeometry(215.0),
        FieldingSector.LEG_SLIP to SectorGeometry(250.0),
        FieldingSector.SQUARE_LEG to SectorGeometry(270.0),
        FieldingSector.MID_WICKET to SectorGeometry(305.0),
        FieldingSector.COW_CORNER to SectorGeometry(322.0),
        FieldingSector.MID_ON to SectorGeometry(340.0)
    )

    private val DEPTH_RADIUS: Map<FieldingDepth, Double> = mapOf(
        FieldingDepth.CLOSE to 0.22,
        FieldingDepth.SHORT to 0.55,
        FieldingDepth.DEEP to 0.95
    )

    // The 9 sectors filled by default when a fresh field is created —
    // exactly matching the 9 outfielders on a side. Leg Slip, Long Stop,
    // and Cow Corner are extra choices a fielder can be moved to (via
    // the Change Position list) but aren't part of the starting
    // arrangement.
    val FIELDING_SECTORS: List<FieldingSector> = listOf(
        FieldingSector.SLIP_GULLY, FieldingSector.THIRD_MAN, FieldingSector.POINT, FieldingSector.COVER,
        FieldingSector.MID_OFF, FieldingSector.MID_ON, FieldingSector.MID_WICKET, FieldingSector.SQUARE_LEG,
        FieldingSector.FINE_LEG
    )

    // Every sector a fielder can be moved to, including the extra ones.
    val ALL_FIELDING_SECTORS: List<FieldingSector> = listOf(
        FieldingSector.SLIP_GULLY, FieldingSector.LEG_SLIP, FieldingSector.THIRD_MAN, FieldingSector.POINT,
        FieldingSector.COVER, FieldingSector.MID_OFF, FieldingSector.LONG_STOP, FieldingSector.MID_ON,
        FieldingSector.MID_WICKET, FieldingSector.COW_CORNER, FieldingSector.SQUARE_LEG, FieldingSector.FINE_LEG
    )

    // Which depths are actually valid for each sector (SLIP_GULLY
    // omitted — it uses `variant`, not depth, and every call site below
    // special-cases it before ever consulting this map, matching the web
    // source's `Exclude<FieldingSector, "slip_gully">` key type). Point
    // and square leg have a genuine third, very-close tier (Silly Point
    // / Short Leg) used by close catchers against spin; cow corner and
    // long stop only exist as single deep/backstop positions with no
    // "short" equivalent.
    private val SECTOR_DEPTHS: Map<FieldingSector, List<FieldingDepth>> = mapOf(
        FieldingSector.LEG_SLIP to listOf(FieldingDepth.CLOSE),
        FieldingSector.THIRD_MAN to listOf(FieldingDepth.SHORT, FieldingDepth.DEEP),
        FieldingSector.POINT to listOf(FieldingDepth.CLOSE, FieldingDepth.SHORT, FieldingDepth.DEEP),
        FieldingSector.COVER to listOf(FieldingDepth.SHORT, FieldingDepth.DEEP),
        FieldingSector.MID_OFF to listOf(FieldingDepth.SHORT, FieldingDepth.DEEP),
        FieldingSector.LONG_STOP to listOf(FieldingDepth.DEEP),
        FieldingSector.MID_ON to listOf(FieldingDepth.SHORT, FieldingDepth.DEEP),
        FieldingSector.MID_WICKET to listOf(FieldingDepth.SHORT, FieldingDepth.DEEP),
        FieldingSector.COW_CORNER to listOf(FieldingDepth.DEEP),
        FieldingSector.SQUARE_LEG to listOf(FieldingDepth.CLOSE, FieldingDepth.SHORT, FieldingDepth.DEEP),
        FieldingSector.FINE_LEG to listOf(FieldingDepth.SHORT, FieldingDepth.DEEP)
    )

    private val SECTOR_LABELS: Map<FieldingSector, Map<FieldingDepth, String>> = mapOf(
        FieldingSector.LEG_SLIP to mapOf(FieldingDepth.CLOSE to "Leg Slip"),
        FieldingSector.THIRD_MAN to mapOf(FieldingDepth.SHORT to "Short Third Man", FieldingDepth.DEEP to "Third Man"),
        FieldingSector.POINT to mapOf(FieldingDepth.CLOSE to "Silly Point", FieldingDepth.SHORT to "Point", FieldingDepth.DEEP to "Deep Point"),
        FieldingSector.COVER to mapOf(FieldingDepth.SHORT to "Cover", FieldingDepth.DEEP to "Long Cover"),
        FieldingSector.MID_OFF to mapOf(FieldingDepth.SHORT to "Mid-Off", FieldingDepth.DEEP to "Long-Off"),
        FieldingSector.LONG_STOP to mapOf(FieldingDepth.DEEP to "Long Stop"),
        FieldingSector.MID_ON to mapOf(FieldingDepth.SHORT to "Mid-On", FieldingDepth.DEEP to "Long-On"),
        FieldingSector.MID_WICKET to mapOf(FieldingDepth.SHORT to "Mid-Wicket", FieldingDepth.DEEP to "Deep Mid-Wicket"),
        FieldingSector.COW_CORNER to mapOf(FieldingDepth.DEEP to "Cow Corner"),
        FieldingSector.SQUARE_LEG to mapOf(FieldingDepth.CLOSE to "Short Leg", FieldingDepth.SHORT to "Square Leg", FieldingDepth.DEEP to "Deep Square Leg"),
        FieldingSector.FINE_LEG to mapOf(FieldingDepth.SHORT to "Short Fine Leg", FieldingDepth.DEEP to "Fine Leg")
    )

    /**
     * The human-readable name for a fielding spot, matching real cricket
     * terminology — e.g. a "short" mid_on is "Mid-On" while a "deep" one
     * is "Long-On", and "close" point/square_leg become "Silly Point"/
     * "Short Leg" — since that's what those positions are actually
     * called.
     */
    fun getPositionLabel(spot: FieldPlacement): String {
        if (spot.sector == FieldingSector.SLIP_GULLY) {
            return if (spot.variant == SlipGullyVariant.GULLY) "Gully" else "Slip"
        }
        val entry = SECTOR_LABELS.getValue(spot.sector)
        return entry[spot.depth] ?: entry.values.firstOrNull() ?: "Fielder"
    }

    /**
     * Normalized (0-1) x/y coordinates for rendering a fielding spot on
     * the circular field map. (0.5, 0.5) is the center of the ground;
     * the striker stands toward the bottom, the bowler toward the top.
     */
    fun getPositionCoords(spot: FieldPlacement): FieldPositionCoords {
        val angleDeg = SECTOR_GEOMETRY.getValue(spot.sector).angleDeg
        val radius = DEPTH_RADIUS.getValue(spot.depth) * 0.44
        val angleRad = angleDeg * PI / 180
        return FieldPositionCoords(
            x = 0.5 + radius * sin(angleRad),
            y = 0.5 - radius * cos(angleRad)
        )
    }

    /**
     * Finds the sector whose angle is closest to the given drop point
     * (relative to field center), and classifies the depth from how far
     * out the drop landed. Not currently used by the UI (free-drag
     * repositioning proved unreliable across screen readers, so the map
     * only supports tap-to-toggle and the Change Position list handles
     * full repositioning instead) — kept here in case a future
     * drag-based interaction wants it.
     */
    fun resolveDropToSpot(relX: Double, relY: Double): DropResolution {
        val dx = relX - 0.5
        val dy = relY - 0.5
        val distance = sqrt(dx * dx + dy * dy)
        var angleDeg = atan2(dx, -dy) * 180 / PI
        if (angleDeg < 0) angleDeg += 360

        var closestSector = ALL_FIELDING_SECTORS[0]
        var smallestDelta = Double.POSITIVE_INFINITY
        for (sector in ALL_FIELDING_SECTORS) {
            val sectorAngle = SECTOR_GEOMETRY.getValue(sector).angleDeg
            var delta = abs(angleDeg - sectorAngle)
            if (delta > 180) delta = 360 - delta
            if (delta < smallestDelta) {
                smallestDelta = delta
                closestSector = sector
            }
        }

        val validDepths = if (closestSector == FieldingSector.SLIP_GULLY) {
            listOf(FieldingDepth.CLOSE)
        } else {
            SECTOR_DEPTHS.getValue(closestSector)
        }
        if (validDepths.size == 1) {
            return DropResolution(closestSector, validDepths[0])
        }
        val normalizedDistance = distance / 0.44
        val depth = when {
            normalizedDistance < 0.4 -> validDepths[0]
            normalizedDistance < 0.7 -> validDepths[minOf(1, validDepths.size - 1)]
            else -> validDepths[validDepths.size - 1]
        }
        return DropResolution(closestSector, depth)
    }

    /**
     * The 9 outfielders a captain actually places: everyone on the
     * bowling side except the wicketkeeper (fixed behind the stumps)
     * and whoever is currently bowling. Since a full XI has exactly 11
     * players, this is always exactly 9 for a normal squad.
     *
     * The keeper is whoever the user (or CricketData.autoSelectPlayingXI,
     * for an AI-controlled side) actually designated as wicketkeeper on
     * the Playing XI screen — team.wicketkeeperId — NOT simply "whoever
     * has role == WICKETKEEPER". A squad can carry several
     * keeper-capable players, and the one actually keeping in this
     * particular XI is a per-match choice, not a fixed property of a
     * player. Falls back to the role-based lookup only for defensive
     * safety (e.g. an old saved match from before wicketkeeperId
     * existed).
     */
    fun getFieldingPlayers(team: Team, currentBowlerId: String): List<Player> {
        val keeperId = team.wicketkeeperId ?: team.players.find { it.role == PlayerRole.WICKETKEEPER }?.id
        return team.players.filter { it.id != keeperId && it.id != currentBowlerId }.take(9)
    }

    /**
     * A fresh, always-legal starting arrangement: everyone begins in a
     * short/close position (0 fielders in the deep, which is legal under
     * every format's restrictions), matching the given fielders to the 9
     * named sectors in order. The user is free to rearrange from here.
     */
    fun createDefaultFieldPlacements(fieldingPlayers: List<Player>): List<FieldPlacement> {
        return FIELDING_SECTORS.mapIndexed { i, sector ->
            FieldPlacement(
                playerId = fieldingPlayers.getOrNull(i)?.id ?: "",
                sector = sector,
                depth = if (sector == FieldingSector.SLIP_GULLY) FieldingDepth.CLOSE else FieldingDepth.SHORT,
                variant = if (sector == FieldingSector.SLIP_GULLY) SlipGullyVariant.SLIP else null
            )
        }
    }

    // ============================================================
    // --- AI-controlled field placement ---
    //
    // Auto-sets the fielding arrangement for an AI-controlled bowling
    // side — the AI equivalent of a human captain using the Field
    // screen. Named after how real captains actually talk about fields
    // ("an attacking ring", "a containing spread", a short-ball
    // "bouncer trap", a full-ball "yorker field") rather than trying to
    // freely optimize placement from scratch every time, which would be
    // both harder to reason about and harder to keep legal.
    // ============================================================

    // Every template is exactly 9 slots, matching the 9 outfielders
    // every side has, and is written in PRIORITY ORDER — the most
    // catching-critical position first. generateAiFieldPlacements()
    // below assigns fielders to a template in fieldingRating order —
    // best fielder to the template's highest-priority slot first — so
    // the best fielder on the team reliably lands in the slot that
    // matters most for that particular field, not wherever they happen
    // to fall in squad order.
    private val FIELD_TEMPLATES: Map<FieldTemplateKey, List<FieldSlotSpec>> = mapOf(
        // Real captains commit to an attacking ring during the
        // powerplay regardless of what's about to be bowled — the
        // fielding restriction itself already limits how defensive the
        // field can be.
        FieldTemplateKey.POWERPLAY to listOf(
            FieldSlotSpec(FieldingSector.SLIP_GULLY, FieldingDepth.CLOSE, SlipGullyVariant.SLIP),
            FieldSlotSpec(FieldingSector.SLIP_GULLY, FieldingDepth.CLOSE, SlipGullyVariant.GULLY),
            FieldSlotSpec(FieldingSector.POINT, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.COVER, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.MID_OFF, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.MID_ON, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.SQUARE_LEG, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.THIRD_MAN, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.FINE_LEG, FieldingDepth.DEEP)
        ),
        FieldTemplateKey.ATTACKING to listOf(
            FieldSlotSpec(FieldingSector.SLIP_GULLY, FieldingDepth.CLOSE, SlipGullyVariant.SLIP),
            FieldSlotSpec(FieldingSector.POINT, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.COVER, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.MID_OFF, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.MID_ON, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.MID_WICKET, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.SQUARE_LEG, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.THIRD_MAN, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.FINE_LEG, FieldingDepth.DEEP)
        ),
        FieldTemplateKey.BALANCED to listOf(
            FieldSlotSpec(FieldingSector.SLIP_GULLY, FieldingDepth.CLOSE, SlipGullyVariant.SLIP),
            FieldSlotSpec(FieldingSector.POINT, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.MID_OFF, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.MID_WICKET, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.FINE_LEG, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.COVER, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.MID_ON, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.SQUARE_LEG, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.THIRD_MAN, FieldingDepth.DEEP)
        ),
        // Maxes out at the legal cap of 5 in the deep — a genuine
        // defensive, boundary-protecting spread for the death overs or a
        // cruising chase.
        FieldTemplateKey.CONTAINING to listOf(
            FieldSlotSpec(FieldingSector.SLIP_GULLY, FieldingDepth.CLOSE, SlipGullyVariant.SLIP),
            FieldSlotSpec(FieldingSector.POINT, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.THIRD_MAN, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.FINE_LEG, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.COVER, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.MID_OFF, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.MID_ON, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.MID_WICKET, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.SQUARE_LEG, FieldingDepth.DEEP)
        ),
        // A real leg-side "bouncer trap": short leg for the top edge,
        // square leg and fine leg (short AND deep) to cover both the
        // mistimed hook that drops short and the one that clears the
        // infield, mid-wicket for the pull shot specifically. Set
        // BEFORE bowling a short ball, not reactively after — matching
        // how captains actually set a plan.
        FieldTemplateKey.BOUNCER_TRAP to listOf(
            FieldSlotSpec(FieldingSector.SQUARE_LEG, FieldingDepth.CLOSE),
            FieldSlotSpec(FieldingSector.MID_WICKET, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.SQUARE_LEG, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.FINE_LEG, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.FINE_LEG, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.SLIP_GULLY, FieldingDepth.CLOSE, SlipGullyVariant.SLIP),
            FieldSlotSpec(FieldingSector.POINT, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.COVER, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.MID_OFF, FieldingDepth.SHORT)
        ),
        // A straight "yorker field": both mid-on/mid-off manned at
        // short AND deep, since a well-executed yorker is defended
        // straight and a mis-executed one (a full toss) tends to get
        // driven straight too.
        FieldTemplateKey.YORKER_TRAP to listOf(
            FieldSlotSpec(FieldingSector.MID_ON, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.MID_OFF, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.MID_ON, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.MID_OFF, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.COVER, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.POINT, FieldingDepth.SHORT),
            FieldSlotSpec(FieldingSector.SLIP_GULLY, FieldingDepth.CLOSE, SlipGullyVariant.SLIP),
            FieldSlotSpec(FieldingSector.THIRD_MAN, FieldingDepth.DEEP),
            FieldSlotSpec(FieldingSector.FINE_LEG, FieldingDepth.DEEP)
        )
    )

    private val SHORT_ISH_LENGTHS: List<DeliveryLength> =
        listOf(BowlingLength.SHORT, BowlingLength.BOUNCER, BowlingLength.BACK_OF_LENGTH, MisExecutedLength.LONG_HOP)
    private val FULL_ISH_LENGTHS: List<DeliveryLength> =
        listOf(BowlingLength.FULL, BowlingLength.YORKER, MisExecutedLength.FULL_TOSS, MisExecutedLength.LOW_FULL_TOSS, BowlingLength.HALF_VOLLEY)

    /**
     * Picks which named field template the AI captain should use right
     * now. Powerplay always wins outright. Outside it, a specific
     * short-ball or full-ball plan for THIS delivery (when known — see
     * generateAiFieldPlacements' upcomingLength param) takes priority
     * over the general situational read, since a captain setting a
     * bouncer trap does so regardless of what the innings situation
     * would otherwise call for. With no strong length signal yet, the
     * situational bias (computed by the caller from live match state)
     * decides between an attacking, balanced, or containing spread.
     */
    private fun pickFieldTemplateKey(isPowerplay: Boolean, situationalBias: Double, upcomingLength: DeliveryLength?): FieldTemplateKey {
        if (isPowerplay) return FieldTemplateKey.POWERPLAY
        if (upcomingLength != null && upcomingLength in SHORT_ISH_LENGTHS) return FieldTemplateKey.BOUNCER_TRAP
        if (upcomingLength != null && upcomingLength in FULL_ISH_LENGTHS) return FieldTemplateKey.YORKER_TRAP
        if (situationalBias > 0.3) return FieldTemplateKey.ATTACKING
        if (situationalBias < -0.3) return FieldTemplateKey.CONTAINING
        return FieldTemplateKey.BALANCED
    }

    /**
     * Auto-generates a legal, realistic fielding arrangement for an
     * AI-controlled bowling side. Selects a named field template (see
     * FIELD_TEMPLATES above), then assigns fielders to it by
     * fieldingRating — best fielder to the template's highest-priority
     * (most catching-critical) slot first — so a genuinely great
     * fielder ends up in the position that matters most for that field,
     * not scattered randomly.
     *
     * Called at two different granularities by callers:
     * - Once whenever the bowler changes (the eventual MatchState.kt),
     *   with no `upcomingLength` — picks a general
     *   powerplay/attacking/balanced/containing spread for the over
     *   about to start.
     * - Again for every single ball the AI bowls (right after the AI's
     *   own bowling decision for THAT delivery is resolved), this time
     *   WITH `upcomingLength` — swaps in a real bouncer or yorker trap
     *   for deliveries that call for one, the same way a real captain
     *   sets a plan ball before it's bowled, not after.
     */
    fun generateAiFieldPlacements(
        fieldingPlayers: List<Player>,
        isPowerplay: Boolean,
        situationalBias: Double = 0.0,
        upcomingLength: DeliveryLength? = null
    ): List<FieldPlacement> {
        val templateKey = pickFieldTemplateKey(isPowerplay, situationalBias, upcomingLength)
        val template = FIELD_TEMPLATES.getValue(templateKey)
        val sortedByRating = fieldingPlayers.sortedByDescending { it.fieldingRating }

        return template.mapIndexed { i, slot ->
            FieldPlacement(
                playerId = sortedByRating.getOrNull(i)?.id ?: "",
                sector = slot.sector,
                depth = slot.depth,
                variant = slot.variant
            )
        }
    }

    /**
     * Tapping a fielder cycles through the valid depths for their
     * current sector — most sectors just toggle short <-> deep, point
     * and square leg cycle through their three tiers (e.g. Point ->
     * Deep Point -> Silly Point -> Point), single-depth sectors (Leg
     * Slip, Long Stop, Cow Corner) don't change, and Slip/Gully toggles
     * that pair's two names instead of a depth (neither has a "deep"
     * variant in real cricket).
     */
    fun toggleDepth(spot: FieldPlacement): FieldPlacement {
        if (spot.sector == FieldingSector.SLIP_GULLY) {
            return spot.copy(variant = if (spot.variant == SlipGullyVariant.GULLY) SlipGullyVariant.SLIP else SlipGullyVariant.GULLY)
        }
        val depths = SECTOR_DEPTHS.getValue(spot.sector)
        if (depths.size <= 1) return spot
        val currentIndex = depths.indexOf(spot.depth)
        val nextIndex = (currentIndex + 1) % depths.size
        return spot.copy(depth = depths[nextIndex])
    }

    // --- Legality ---

    /** T20 and ODI powerplays restrict how many fielders can stand outside the 30-yard circle (i.e. in the "deep"); Test cricket has no such restriction at any stage. */
    fun isPowerplayOver(format: MatchFormat, oversCompleted: Int): Boolean = when (format) {
        MatchFormat.T20 -> oversCompleted < 6
        MatchFormat.ODI -> oversCompleted < 10
        MatchFormat.TEST -> false
    }

    fun countDeepFielders(placements: List<FieldPlacement>): Int = placements.count { it.depth == FieldingDepth.DEEP }

    const val MAX_DEEP_FIELDERS_POWERPLAY = 3
    const val MAX_DEEP_FIELDERS_NORMAL = 5

    /**
     * Whether the given arrangement is legal right now. During a
     * powerplay, at most 3 fielders may be in the deep; outside the
     * powerplay (and always in Tests), the normal limited-overs cap of
     * 5 applies.
     */
    fun isFieldLegal(placements: List<FieldPlacement>, isPowerplay: Boolean): Boolean {
        val deepCount = countDeepFielders(placements)
        val cap = if (isPowerplay) MAX_DEEP_FIELDERS_POWERPLAY else MAX_DEEP_FIELDERS_NORMAL
        return deepCount <= cap
    }

    /**
     * A short explanation of why a field is illegal, for on-screen and
     * screen-reader display. Returns null when the field is legal.
     */
    fun getIllegalFieldReason(placements: List<FieldPlacement>, isPowerplay: Boolean): String? {
        val deepCount = countDeepFielders(placements)
        val cap = if (isPowerplay) MAX_DEEP_FIELDERS_POWERPLAY else MAX_DEEP_FIELDERS_NORMAL
        if (deepCount <= cap) return null
        val stage = if (isPowerplay) "During the powerplay" else "Outside the powerplay"
        return "$stage, at most $cap fielders are allowed in the deep — this field has $deepCount."
    }
}
