package com.cricketsim.ui.match

import com.cricketsim.logic.FieldPlacement
import com.cricketsim.logic.FieldingDepth
import com.cricketsim.logic.FieldingSystem
import com.cricketsim.logic.Player

/**
 * Three ready-made fields for the user's own side, so rearranging a whole
 * field doesn't cost two picks per fielder. NOT in the web app (its
 * captain places every fielder by hand); added on request.
 *
 *   NORMAL  — the AI captain's balanced ring (4 in the deep outside the
 *             powerplay).
 *   DEFEND  — its containing spread (the legal maximum of 5 in the deep).
 *   ATTACK  — its attacking ring (only 2 in the deep, catchers close in).
 *
 * They are built with FieldingSystem.generateAiFieldPlacements, i.e. the
 * same templates the AI uses, which also assigns the team's best
 * fielders to the template's most catching-critical slots first. That is
 * a real gameplay effect: a preset hands the user the AI captain's
 * arrangement.
 *
 * LEGALITY. The balanced and containing templates have 4 and 5 fielders
 * in the deep, but a powerplay allows only 3, so applying them as-is
 * would give an illegal field — every ball a no-ball — the moment the user
 * picked one. `generateAiFieldPlacements` avoids this for the AI by
 * switching to its own powerplay template, but that would make all three
 * presets identical during a powerplay. Instead the template is asked
 * for outside the powerplay and then TRIMMED: templates list their
 * slots in priority order (most important first), so the least
 * important boundary riders, from the END of the list, are moved from
 * deep to short until the field is legal. Attack (2 in the deep) is
 * never trimmed; Normal loses one rider and Defend two, so the three
 * stay different.
 */
enum class FieldPreset(val label: String, val description: String, val bias: Double) {
    NORMAL("Normal field", "Balanced. A mix of catchers and boundary riders.", 0.0),
    DEFEND("Defensive field", "Protect the boundary. More riders in the deep, fewer catchers.", -1.0),
    ATTACK("Attacking field", "Go for wickets. Catchers close in, fewer riders in the deep.", 1.0)
}

data class PresetResult(val placements: List<FieldPlacement>, val trimmedForPowerplay: Boolean)

object FieldPresets {

    /**
     * @param fielders the nine current fielders (everyone but the keeper
     *   and the bowler), in any order — they are re-assigned by fielding
     *   rating.
     */
    fun build(preset: FieldPreset, fielders: List<Player>, isPowerplay: Boolean): PresetResult {
        val template = FieldingSystem.generateAiFieldPlacements(
            fieldingPlayers = fielders,
            isPowerplay = false,
            situationalBias = preset.bias
        )
        val cap = if (isPowerplay) FieldingSystem.MAX_DEEP_FIELDERS_POWERPLAY else FieldingSystem.MAX_DEEP_FIELDERS_NORMAL
        var excess = FieldingSystem.countDeepFielders(template) - cap
        if (excess <= 0) return PresetResult(template, false)

        // Every sector these templates put a rider in also has a valid
        // SHORT spot, so SHORT is always a legal place to pull one in to.
        val trimmed = template.toMutableList()
        for (index in trimmed.indices.reversed()) {
            if (excess <= 0) break
            if (trimmed[index].depth == FieldingDepth.DEEP) {
                trimmed[index] = trimmed[index].copy(depth = FieldingDepth.SHORT)
                excess--
            }
        }
        return PresetResult(trimmed, true)
    }
}
