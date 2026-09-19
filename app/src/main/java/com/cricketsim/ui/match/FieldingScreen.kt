package com.cricketsim.ui.match

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.logic.FieldPlacement
import com.cricketsim.logic.FieldingDepth
import com.cricketsim.logic.FieldingSector
import com.cricketsim.logic.FieldingSystem
import com.cricketsim.logic.Player
import com.cricketsim.logic.SlipGullyVariant

/**
 * The third and last custom gesture surface (pitching/batting/fielding
 * — see UI_NOTES.md). Unlike the other two there is no timing skill
 * here, so this is a pure single-swipe-list design with no custom
 * gesture at all, which is exactly what UI_NOTES.md's principles ask
 * for wherever a list can do the job. The web app's FieldingScreen is
 * also a two-phase PICK (which fielder, then where) — it only used a
 * press-and-drag gesture to move through the lists — so the mechanics
 * carry over one for one:
 *
 *   OVERVIEW — the nine fielders as "Position — Name" rows with their
 *     fielding rating, a legality line, and Set field. Pick a fielder to
 *     move.
 *   SECTOR — the 12 sectors (web: one flat list of 23 slots). Two
 *     levels here instead of one flat list: 12 + at most 3 swipes
 *     rather than up to 23, which matters a lot for linear TalkBack
 *     navigation. Each sector shows who is standing in it.
 *   DEPTH — only shown when the sector has more than one valid slot
 *     (e.g. Mid-On vs Long-On; Slip vs Gully). Each option says whether
 *     it's empty or who you'd swap with.
 *
 * Moving onto an occupied slot SWAPS the two fielders, exactly like the
 * web. The move is announced in full (who went where, who they swapped
 * with, whether the field is now legal), through one polite live
 * region on the overview.
 *
 * An illegal field can still be set — same as the web — but is called out
 * both here and on the match screen: every delivery is a no-ball until
 * it is fixed (MatchSimulation passes `illegalField` to the engine).
 *
 * READ-ONLY MODE is the web's "browse the AI's field" view: when the
 * user is batting, the same list is shown with nothing actionable.
 *
 * Back on the editable overview DISCARDS unsaved changes (its label
 * says so). Set field commits them.
 *
 * KNOWN V1 SIMPLIFICATIONS:
 * - The valid depths per sector come from FieldingSystem.toggleDepth,
 *   because the source table (SECTOR_DEPTHS) is private there. See
 *   validDepths() below. If FieldingSystem ever exposes it, use that.
 * - No presets. The web has none either, but rearranging a whole field
 *   costs two picks per fielder; an "attacking / balanced / containing"
 *   starting point built on FieldingSystem.generateAiFieldPlacements
 *   would cut that a lot. Deliberately not added without asking — it
 *   would hand the user the AI captain's field templates.
 * - All of it is UNTESTED with TalkBack.
 */

private enum class FieldStep { OVERVIEW, SECTOR, DEPTH }

private data class FieldSlot(val sector: FieldingSector, val depth: FieldingDepth, val variant: SlipGullyVariant?)

private data class FielderRow(val playerId: String, val position: String, val name: String, val rating: String)

private data class SectorRow(val sector: FieldingSector, val name: String, val occupants: String)

private data class DepthRow(val slot: FieldSlot, val label: String, val note: String)

private data class MoveResult(val placements: List<FieldPlacement>, val swappedWithId: String?)

private val SECTOR_NAMES: Map<FieldingSector, String> = mapOf(
    FieldingSector.SLIP_GULLY to "Slip and Gully",
    FieldingSector.LEG_SLIP to "Leg Slip",
    FieldingSector.THIRD_MAN to "Third Man",
    FieldingSector.POINT to "Point",
    FieldingSector.COVER to "Cover",
    FieldingSector.MID_OFF to "Mid-Off",
    FieldingSector.LONG_STOP to "Long Stop",
    FieldingSector.MID_ON to "Mid-On",
    FieldingSector.MID_WICKET to "Mid-Wicket",
    FieldingSector.COW_CORNER to "Cow Corner",
    FieldingSector.SQUARE_LEG to "Square Leg",
    FieldingSector.FINE_LEG to "Fine Leg"
)

private fun sectorName(sector: FieldingSector): String = SECTOR_NAMES[sector] ?: sector.name

/**
 * The valid depths for a (non slip/gully) sector, derived from
 * FieldingSystem.toggleDepth, which cycles through exactly the private
 * SECTOR_DEPTHS list. Probing with CLOSE lands on a valid depth whether
 * or not CLOSE itself is valid (an unknown depth has indexOf == -1, so
 * the next index is 0); cycling from there until we return to the start
 * visits every valid depth once. Sorted into CLOSE < SHORT < DEEP order,
 * which is how every list in the source is written.
 */
private fun validDepths(sector: FieldingSector): List<FieldingDepth> {
    if (sector == FieldingSector.SLIP_GULLY) return listOf(FieldingDepth.CLOSE)
    val start = FieldingSystem.toggleDepth(FieldPlacement("", sector, FieldingDepth.CLOSE)).depth
    val depths = mutableListOf(start)
    var current = FieldingSystem.toggleDepth(FieldPlacement("", sector, start)).depth
    var guard = 0
    while (current != start && guard < FieldingDepth.values().size) {
        depths.add(current)
        current = FieldingSystem.toggleDepth(FieldPlacement("", sector, current)).depth
        guard++
    }
    return depths.sortedBy { it.ordinal }
}

private fun slotsFor(sector: FieldingSector): List<FieldSlot> =
    if (sector == FieldingSector.SLIP_GULLY) {
        listOf(
            FieldSlot(sector, FieldingDepth.CLOSE, SlipGullyVariant.SLIP),
            FieldSlot(sector, FieldingDepth.CLOSE, SlipGullyVariant.GULLY)
        )
    } else {
        validDepths(sector).map { FieldSlot(sector, it, null) }
    }

private fun slotLabel(slot: FieldSlot): String =
    FieldingSystem.getPositionLabel(FieldPlacement("", slot.sector, slot.depth, slot.variant))

/** Slip/gully placements with no variant recorded are treated as Slip, matching getPositionLabel. */
private fun FieldPlacement.occupies(slot: FieldSlot): Boolean {
    val effectiveVariant = if (sector == FieldingSector.SLIP_GULLY) (variant ?: SlipGullyVariant.SLIP) else null
    return sector == slot.sector && depth == slot.depth && effectiveVariant == slot.variant
}

/** Moves one fielder to a slot. If another fielder already stands there the two swap, never stack. */
private fun moveFielder(placements: List<FieldPlacement>, playerId: String, slot: FieldSlot): MoveResult {
    val moving = placements.firstOrNull { it.playerId == playerId } ?: return MoveResult(placements, null)
    val occupant = placements.firstOrNull { it.playerId != playerId && it.occupies(slot) }
    val updated = placements.map { p ->
        when {
            p.playerId == playerId -> p.copy(sector = slot.sector, depth = slot.depth, variant = slot.variant)
            occupant != null && p.playerId == occupant.playerId ->
                p.copy(sector = moving.sector, depth = moving.depth, variant = moving.variant)
            else -> p
        }
    }
    return MoveResult(updated, occupant?.playerId)
}

private fun legalityText(placements: List<FieldPlacement>, isPowerplay: Boolean): String {
    if (FieldingSystem.isFieldLegal(placements, isPowerplay)) {
        val deep = FieldingSystem.countDeepFielders(placements)
        val plural = if (deep == 1) "" else "s"
        val stage = if (isPowerplay) " (powerplay)" else ""
        return "Field is legal. $deep fielder$plural in the deep$stage."
    }
    return "Field is illegal! ${FieldingSystem.getIllegalFieldReason(placements, isPowerplay).orEmpty()}"
}

/** The message MatchScreen announces once the user confirms a field. Same wording as the web's confirm announcement. */
fun fieldSetMessage(placements: List<FieldPlacement>, isPowerplay: Boolean): String {
    if (FieldingSystem.isFieldLegal(placements, isPowerplay)) return "Field set. This arrangement is legal."
    val reason = FieldingSystem.getIllegalFieldReason(placements, isPowerplay).orEmpty()
    return "Field set, but it is illegal. $reason Every delivery will be a no-ball until it is fixed."
}

/**
 * @param teamName the bowling (fielding) side.
 * @param players everyone who could be in `placements`, for names and ratings.
 * @param placements the current arrangement (the 9 outfielders).
 * @param isReadOnly true while the AI side is fielding — browse only.
 * @param onConfirm the user's edited arrangement, on Set field.
 */
@Composable
fun FieldingScreen(
    teamName: String,
    players: List<Player>,
    placements: List<FieldPlacement>,
    isPowerplay: Boolean,
    isReadOnly: Boolean,
    onConfirm: (List<FieldPlacement>) -> Unit,
    onBack: () -> Unit
) {
    var local by remember { mutableStateOf(placements) }
    var step by remember { mutableStateOf(FieldStep.OVERVIEW) }
    var movingId by remember { mutableStateOf<String?>(null) }
    var pendingSector by remember { mutableStateOf<FieldingSector?>(null) }
    var status by remember { mutableStateOf("") }

    val shown = if (isReadOnly) placements else local

    fun nameOf(id: String): String = players.firstOrNull { it.id == id }?.name ?: "Fielder"

    fun finishMove(slot: FieldSlot) {
        val id = movingId
        val previous = local.firstOrNull { it.playerId == id }
        movingId = null
        pendingSector = null
        step = FieldStep.OVERVIEW
        if (id == null || previous == null) return

        val name = nameOf(id)
        val destination = slotLabel(slot)
        if (previous.occupies(slot)) {
            status = "$name is already at $destination."
            return
        }

        val wasLegal = FieldingSystem.isFieldLegal(local, isPowerplay)
        val previousLabel = FieldingSystem.getPositionLabel(previous)
        val result = moveFielder(local, id, slot)
        local = result.placements
        val isLegal = FieldingSystem.isFieldLegal(result.placements, isPowerplay)
        val rating = players.firstOrNull { it.id == id }?.fieldingRating

        status = buildString {
            append("$name moves to $destination")
            if (result.swappedWithId != null) {
                append(", swapping with ${nameOf(result.swappedWithId)}, who goes to $previousLabel")
            }
            append(".")
            if (rating != null) append(" Fielding rating $rating out of 100.")
            if (wasLegal && !isLegal) {
                append(" The field is now illegal: ${FieldingSystem.getIllegalFieldReason(result.placements, isPowerplay).orEmpty()}")
            } else if (!wasLegal && isLegal) {
                append(" The field is legal again.")
            }
        }
    }

    when (step) {
        FieldStep.OVERVIEW -> {
            val rows = shown.filter { it.playerId.isNotBlank() }.map {
                FielderRow(
                    playerId = it.playerId,
                    position = FieldingSystem.getPositionLabel(it),
                    name = nameOf(it.playerId),
                    rating = players.firstOrNull { p -> p.id == it.playerId }?.fieldingRating?.toString() ?: "unknown"
                )
            }
            FieldOverviewStep(
                teamName = teamName,
                legality = legalityText(shown, isPowerplay),
                status = status,
                isReadOnly = isReadOnly,
                rows = rows,
                onPick = { id ->
                    movingId = id
                    step = FieldStep.SECTOR
                },
                onSet = { onConfirm(local) },
                onBack = onBack
            )
        }
        FieldStep.SECTOR -> {
            val id = movingId
            val mover = local.firstOrNull { it.playerId == id }
            if (id == null || mover == null) {
                step = FieldStep.OVERVIEW
            } else {
                val rows = FieldingSystem.ALL_FIELDING_SECTORS.map { sector ->
                    val here = local.filter { it.sector == sector && it.playerId != id && it.playerId.isNotBlank() }
                    SectorRow(
                        sector = sector,
                        name = sectorName(sector),
                        occupants = if (here.isEmpty()) {
                            "Nobody there."
                        } else {
                            here.joinToString(". ") { "${nameOf(it.playerId)} at ${FieldingSystem.getPositionLabel(it)}" } + "."
                        }
                    )
                }
                FieldSectorStep(
                    fielderName = nameOf(id),
                    currentLabel = FieldingSystem.getPositionLabel(mover),
                    rows = rows,
                    onSelected = { sector ->
                        val slots = slotsFor(sector)
                        if (slots.size == 1) {
                            finishMove(slots[0])
                        } else {
                            pendingSector = sector
                            step = FieldStep.DEPTH
                        }
                    },
                    onCancel = {
                        status = "Cancelled moving ${nameOf(id)}. Choose a fielder to move."
                        movingId = null
                        step = FieldStep.OVERVIEW
                    }
                )
            }
        }
        FieldStep.DEPTH -> {
            val id = movingId
            val sector = pendingSector
            if (id == null || sector == null) {
                step = FieldStep.OVERVIEW
            } else {
                val rows = slotsFor(sector).map { slot ->
                    val occupant = local.firstOrNull { it.playerId.isNotBlank() && it.occupies(slot) }
                    val note = when {
                        occupant == null -> "Empty."
                        occupant.playerId == id -> "${nameOf(id)} is already here."
                        else -> "${nameOf(occupant.playerId)} is there. Choosing this swaps them."
                    }
                    DepthRow(slot = slot, label = slotLabel(slot), note = note)
                }
                FieldDepthStep(
                    fielderName = nameOf(id),
                    sectorName = sectorName(sector),
                    rows = rows,
                    onSelected = { slot -> finishMove(slot) },
                    onBack = {
                        pendingSector = null
                        step = FieldStep.SECTOR
                    }
                )
            }
        }
    }
}

@Composable
private fun FieldOverviewStep(
    teamName: String,
    legality: String,
    status: String,
    isReadOnly: Boolean,
    rows: List<FielderRow>,
    onPick: (String) -> Unit,
    onSet: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "$teamName field",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        // One polite live region for the outcome of the last move. The
        // legality line below is deliberately NOT live: a change of
        // legality is folded into this message instead, so a single move
        // isn't announced twice.
        if (status.isNotEmpty()) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(legality, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (isReadOnly) {
            Text(
                "The other captain sets this field, so it can't be changed here.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            // Above the list on purpose: after a few moves a TalkBack user
            // shouldn't have to swipe past nine fielders to save.
            Button(onClick = onSet, modifier = Modifier.fillMaxWidth()) { Text("Set field") }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Choose a fielder to move.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rows) { row ->
                val rowModifier = if (isReadOnly) {
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { }
                } else {
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "Move ${row.name}",
                            role = Role.Button,
                            onClick = { onPick(row.playerId) }
                        )
                }
                Column(modifier = rowModifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
                    Text("${row.position} \u2014 ${row.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Fielding rating ${row.rating}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(if (isReadOnly) "Back" else "Back (discard changes)")
        }
    }
}

@Composable
private fun FieldSectorStep(
    fielderName: String,
    currentLabel: String,
    rows: List<SectorRow>,
    onSelected: (FieldingSector) -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Move $fielderName",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Currently at $currentLabel. Choose an area.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        // Cancel sits above the 12-item list for the same reason Set
        // field sits above the fielders.
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rows) { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "Choose ${row.name}",
                            role = Role.Button,
                            onClick = { onSelected(row.sector) }
                        )
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    Text(row.occupants, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun FieldDepthStep(
    fielderName: String,
    sectorName: String,
    rows: List<DepthRow>,
    onSelected: (FieldSlot) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Where in $sectorName?",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Moving $fielderName.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        rows.forEach { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = "Place at ${row.label}",
                        role = Role.Button,
                        onClick = { onSelected(row.slot) }
                    )
                    .padding(vertical = 14.dp, horizontal = 8.dp)
            ) {
                Text(row.label, style = MaterialTheme.typography.titleMedium)
                Text(row.note, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
