package com.cricketsim.ui.match

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.BowlingAngle
import com.cricketsim.logic.BowlingLine
import com.cricketsim.logic.BowlingSystem
import com.cricketsim.logic.BowlingVariation
import com.cricketsim.logic.Player
import com.cricketsim.logic.ResolvedBowlingDecision
import com.cricketsim.logic.SwingType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The first of the three custom gesture surfaces (pitching/batting/
 * fielding — see UI_NOTES.md). Built around a TWO-FINGER gesture
 * vocabulary (see GESTURE_REDESIGN.md, section 2), and — as of this
 * revision — quality is computed the same way the web app computes it:
 * directly from how precisely and smoothly the length drag itself was
 * performed, via the already-ported BowlingSystem.computeBowlingQuality.
 * There is no separate timing mechanic. See "WHY THE TIMING MECHANIC IS
 * GONE" and "WHY THIS ISN'T LITERALLY TWO SIMULTANEOUS POINTERS" below
 * for the two things that changed after the first on-device pass.
 *
 * AIM STEP GESTURE VOCABULARY (angle/line/variation/length all live on
 * ONE screen at once, each assigned its own compass direction, exactly
 * because a real bowler sets all four before ever releasing the ball):
 * - Two-finger swipe LEFT cycles ANGLE forward through its (short, 2-item)
 *   list, wrapping around.
 * - Two-finger swipe RIGHT cycles LINE forward through its list, wrapping.
 * - Two-finger swipe UP cycles VARIATION forward through this bowler's
 *   pace-or-spin variation list, wrapping.
 * - Two-finger swipe DOWN is the one CONTINUOUS axis, and now the ONLY
 *   thing that determines both the intended length AND the delivery's
 *   quality (see below): once net downward movement is recognized as
 *   the dominant direction, the gesture locks into a drag that maps
 *   live vertical position to a length band, with a continuously
 *   pitch-shifted tone (SoundEngine.startAimTone/updateAimTone)
 *   tracking position while the fingers move, and a SPOKEN announcement
 *   only when the drag crosses into a new length band — never a
 *   continuous spoken readout, which would overrun TalkBack's speech
 *   queue. Lifting the fingers commits whatever band the drag last
 *   landed in.
 * Angle/line/variation each fire repeatedly while the same swipe
 * continues (each firing resets the gesture's reference point), so
 * holding and continuing to drag left, say, cycles through angle
 * options one at a time — same "keep pushing to keep stepping" feel as
 * a hardware volume rocker.
 *
 * WHY THIS ISN'T LITERALLY TWO SIMULTANEOUS POINTERS. The first version
 * of this screen waited for two simultaneously-pressed pointers before
 * tracking anything, which is how a genuine two-finger touch looks
 * WITHOUT a screen reader running. With TalkBack's touch exploration
 * active, that condition never arrives: Android's touch exploration
 * controller only enters "passthrough" once a second finger touches
 * down, and passthrough DROPS the first finger and forwards only
 * subsequent fingers' movement, rewritten as an ordinary single-pointer
 * stream — a real two-finger swipe is delivered to the app looking like
 * a ONE-finger drag. detectAimGesture below tracks whichever single
 * pointer is currently active rather than requiring two at once, which
 * is correct in both cases: under TalkBack, that lone pointer already
 * IS the translated second finger (the user still swipes with two
 * fingers; only the plumbing changes what the app receives), and
 * without TalkBack running, a single real finger works identically,
 * which is a reasonable, low-risk fallback for sighted testing rather
 * than a design compromise. TalkBack itself fully owns genuine
 * single-finger touch (speaking whatever's under the finger) UNLESS a
 * second finger is added, so this handler never fights TalkBack's own
 * touch exploration for a real one-finger explore gesture — it only
 * ever receives events once real passthrough (i.e. a genuine two-finger
 * touch) has begun.
 *
 * WHY THE TIMING MECHANIC IS GONE. The previous release step asked the
 * player to double-tap on a haptic/audible beat, and used the tap's
 * timing error to synthesize a `verticalFraction` for
 * BowlingSystem.computeBowlingQuality — a screen-reader-friendly
 * INVENTED substitute for the web's own mechanic, which has no timing
 * step at all: the web computes quality directly from how precisely and
 * smoothly the player's drag itself lands relative to their intended
 * length zone (LENGTH PRECISION: distance from the drag's release point
 * to the zone's center; SMOOTHNESS PENALTY: whether the drag overshot
 * past its own final resting point and had to be pulled back). Now that
 * length is set by a real two-finger drag rather than a discrete pick,
 * that same web mechanic has a genuine non-visual equivalent again: the
 * length drag simultaneously expresses BOTH the intended length (via
 * classifyLength on where it lands) AND its own execution quality (via
 * how centered on the band, and how smooth, the drag was) — no separate
 * timing step needed, exactly matching the web. See computeBowlingQuality
 * below for how the drag's live-tracked verticalFraction/
 * maxVerticalFractionReached feed it.
 *
 * SPEED is a real Slider (Compose's Slider has first-class TalkBack
 * support out of the box — no custom gesture code needed), matching the
 * web's own continuous speed control, rather than the three-tier list
 * pick this screen used before.
 *
 * KNOWN V1 SIMPLIFICATIONS (not permanent design decisions):
 * - Swing is not player-controlled yet (always SwingType.NONE) — the
 *   web app's swing came from the horizontal curve of its drag path,
 *   which this discrete-direction gesture vocabulary doesn't have an
 *   equivalent axis for yet.
 * - DISCRETE_SWIPE_THRESHOLD_PX / LENGTH_DRAG_ENTRY_THRESHOLD_PX /
 *   LENGTH_DRAG_FULL_RANGE_PX are all unmeasured guesses pending a
 *   real-device calibration pass.
 */

private enum class PitchStep { AIM, SPEED, RESULT }

// --- AimStep gesture tuning (all unmeasured on a real device — see the
// class doc comment's KNOWN V1 SIMPLIFICATIONS) ---

// How far the active pointer must move, in raw pixels, before a
// left/right/up swipe counts as one discrete "cycle to the next option"
// step. Deliberately re-fireable: each firing resets the reference point
// (see detectAimGesture), so continuing to hold and move keeps stepping.
private const val DISCRETE_SWIPE_THRESHOLD_PX = 56f

// How far net DOWNWARD movement (from the gesture's start point) must
// reach, while still the dominant axis, before the gesture locks into
// continuous length-drag mode for the rest of this touch.
private const val LENGTH_DRAG_ENTRY_THRESHOLD_PX = 24f

// Downward distance from the gesture's start point that maps to a full
// 0..1 sweep across the whole pitch length. A guess pending real-device
// testing across different screen sizes/densities.
private const val LENGTH_DRAG_FULL_RANGE_PX = 700f

// GOOD_LENGTH's band center (see BowlingSystem.LENGTH_BANDS) — the
// sensible default verticalFraction if the length drag is never
// touched at all: dead-center precision, no smoothness penalty.
private const val DEFAULT_LENGTH_FRACTION = 0.5f

@Composable
fun PitchingScreen(bowler: Player, onDeliveryResolved: (ResolvedBowlingDecision) -> Unit, onBack: () -> Unit) {
    var step by remember { mutableStateOf(PitchStep.AIM) }
    var angle by remember { mutableStateOf(BowlingSystem.ANGLE_OPTIONS.first().value) }
    var line by remember { mutableStateOf(BowlingSystem.LINE_OPTIONS.first().value) }
    var variation by remember { mutableStateOf(BowlingVariation.STOCK) }
    // The length drag IS both the intended length and its own execution
    // precision (see the class doc comment) — these two floats are the
    // only length-related state; the intended BowlingLength is derived
    // from finalLengthFraction via BowlingSystem.classifyLength, never
    // stored separately.
    var finalLengthFraction by remember { mutableStateOf(DEFAULT_LENGTH_FRACTION) }
    var maxLengthFractionReached by remember { mutableStateOf(DEFAULT_LENGTH_FRACTION) }
    var speedKmh by remember { mutableStateOf(0) }
    var decision by remember { mutableStateOf<ResolvedBowlingDecision?>(null) }

    when (step) {
        PitchStep.AIM -> AimStep(
            bowler = bowler,
            angle = angle,
            line = line,
            variation = variation,
            finalLengthFraction = finalLengthFraction,
            onAngleChanged = { angle = it },
            onLineChanged = { line = it },
            onVariationChanged = { variation = it },
            onLengthDragCommitted = { finalFraction, maxFraction ->
                finalLengthFraction = finalFraction
                maxLengthFractionReached = maxFraction
            },
            onContinue = { step = PitchStep.SPEED },
            onBack = onBack
        )
        PitchStep.SPEED -> SpeedStep(
            bowler = bowler,
            variation = variation,
            initialSpeedKmh = speedKmh,
            onBowl = { chosenSpeed ->
                speedKmh = chosenSpeed
                val speedRange = BowlingSystem.getSpeedRangeForVariation(bowler.bowlingStyle, variation)
                // Exactly the web's own quality rubric, fed by the length
                // drag's own live-tracked precision/smoothness — see the
                // class doc comment's "WHY THE TIMING MECHANIC IS GONE".
                val quality = BowlingSystem.computeBowlingQuality(
                    verticalFraction = finalLengthFraction.toDouble(),
                    maxVerticalFractionReached = maxLengthFractionReached.toDouble(),
                    speedKmh = chosenSpeed,
                    speedRange = speedRange,
                    bowlingRating = bowler.bowlingRating
                )
                val targetLength = BowlingSystem.classifyLength(finalLengthFraction.toDouble())
                val resolvedLength = BowlingSystem.resolveActualLength(targetLength, quality.tier)
                decision = ResolvedBowlingDecision(
                    angle = angle,
                    line = line,
                    variation = variation,
                    bowlingStyle = bowler.bowlingStyle,
                    speedKmh = chosenSpeed,
                    intendedLength = targetLength,
                    actualLength = resolvedLength.actualLength,
                    swingType = SwingType.NONE, // See KNOWN V1 SIMPLIFICATIONS above.
                    qualityScore = quality.finalScore,
                    qualityTier = quality.tier,
                    quality = quality,
                    forcedNoBall = resolvedLength.forcedNoBall
                )
                step = PitchStep.RESULT
            },
            onBack = { step = PitchStep.AIM }
        )
        PitchStep.RESULT -> {
            val finalDecision = decision
            if (finalDecision != null) {
                ResultStep(decision = finalDecision, onContinue = { onDeliveryResolved(finalDecision) })
            }
        }
    }
}

private enum class AimDiscreteDirection { LEFT, RIGHT, UP }

/**
 * The shared low-level gesture tracker AimStep's gesture area is built
 * on — see the class doc comment's "WHY THIS ISN'T LITERALLY TWO
 * SIMULTANEOUS POINTERS" for why this tracks a single active pointer
 * rather than requiring two at once, and "AIM STEP GESTURE VOCABULARY"
 * for the full direction/mode behavior this implements.
 */
private suspend fun PointerInputScope.detectAimGesture(
    onDiscrete: (AimDiscreteDirection) -> Unit,
    onLengthDragStart: () -> Unit,
    onLengthDrag: (fraction: Float, maxFractionReached: Float) -> Unit,
    onLengthDragEnd: (fraction: Float, maxFractionReached: Float) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val gestureStart = down.position
        var referencePoint = gestureStart
        var lengthDragActive = false
        var lastFraction = 0f
        var maxFractionReached = 0f

        while (true) {
            val event = awaitPointerEvent()
            val active = event.changes.firstOrNull { it.pressed }
            if (active == null) {
                if (lengthDragActive) onLengthDragEnd(lastFraction, maxFractionReached)
                return@awaitEachGesture
            }
            active.consume()
            val position = active.position

            if (lengthDragActive) {
                val netDown = position.y - gestureStart.y
                lastFraction = (netDown / LENGTH_DRAG_FULL_RANGE_PX).coerceIn(0f, 1f)
                maxFractionReached = maxOf(maxFractionReached, lastFraction)
                onLengthDrag(lastFraction, maxFractionReached)
                continue
            }

            val netDownFromStart = position.y - gestureStart.y
            val netRightFromStart = position.x - gestureStart.x
            // Downward movement locks into continuous length mode for the
            // rest of this touch — it never reverts to discrete swiping.
            if (netDownFromStart > LENGTH_DRAG_ENTRY_THRESHOLD_PX && netDownFromStart > abs(netRightFromStart)) {
                lengthDragActive = true
                onLengthDragStart()
                lastFraction = (netDownFromStart / LENGTH_DRAG_FULL_RANGE_PX).coerceIn(0f, 1f)
                maxFractionReached = lastFraction
                onLengthDrag(lastFraction, maxFractionReached)
                continue
            }

            val dx = position.x - referencePoint.x
            val dy = position.y - referencePoint.y
            if (abs(dx) >= abs(dy)) {
                if (abs(dx) >= DISCRETE_SWIPE_THRESHOLD_PX) {
                    onDiscrete(if (dx < 0) AimDiscreteDirection.LEFT else AimDiscreteDirection.RIGHT)
                    referencePoint = position
                }
            } else if (dy < 0 && abs(dy) >= DISCRETE_SWIPE_THRESHOLD_PX) {
                // Upward only — downward is claimed by the length-drag check above.
                onDiscrete(AimDiscreteDirection.UP)
                referencePoint = position
            }
        }
    }
}

@Composable
private fun AimStep(
    bowler: Player,
    angle: BowlingAngle,
    line: BowlingLine,
    variation: BowlingVariation,
    finalLengthFraction: Float,
    onAngleChanged: (BowlingAngle) -> Unit,
    onLineChanged: (BowlingLine) -> Unit,
    onVariationChanged: (BowlingVariation) -> Unit,
    onLengthDragCommitted: (finalFraction: Float, maxFractionReached: Float) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val services = LocalGameServices.current
    val variationOptions = remember(bowler.bowlingStyle) { BowlingSystem.getVariationOptions(bowler.bowlingStyle) }
    // Tracks the live drag position while dragging; committed back up
    // (via onLengthDragCommitted) only once the fingers lift.
    var liveLengthFraction by remember(finalLengthFraction) { mutableStateOf(finalLengthFraction) }
    // Only used to detect a zone CROSSING (a genuine value change), so the
    // spoken announcement never fires on every touch-move event.
    var lastAnnouncedLength by remember(finalLengthFraction) {
        mutableStateOf(BowlingSystem.classifyLength(finalLengthFraction.toDouble()))
    }

    fun cycleAngle() {
        val options = BowlingSystem.ANGLE_OPTIONS.map { it.value }
        val next = options[(options.indexOf(angle) + 1) % options.size]
        onAngleChanged(next)
        services?.announceSpoken(BowlingSystem.angleLabel(next))
    }
    fun cycleLine() {
        val options = BowlingSystem.LINE_OPTIONS
        val next = options[(options.indexOfFirst { it.value == line } + 1) % options.size]
        onLineChanged(next.value)
        services?.announceSpoken(next.label)
    }
    fun cycleVariation() {
        val next = variationOptions[(variationOptions.indexOfFirst { it.value == variation } + 1) % variationOptions.size]
        onVariationChanged(next.value)
        services?.announceSpoken(next.label)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Aim your delivery",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Two-finger swipe left for angle, right for line, up for variation. " +
                "Two-finger swipe down and hold to set length by ear, then lift your fingers to set it.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Angle: ${BowlingSystem.angleLabel(angle)}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = "Line: ${BowlingSystem.LINE_OPTIONS.first { it.value == line }.label}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = "Variation: ${variationOptions.first { it.value == variation }.label}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = "Length: ${BowlingSystem.lengthLabel(BowlingSystem.classifyLength(liveLengthFraction.toDouble()))}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(bowler.bowlingStyle) {
                    detectAimGesture(
                        onDiscrete = { direction ->
                            when (direction) {
                                AimDiscreteDirection.LEFT -> cycleAngle()
                                AimDiscreteDirection.RIGHT -> cycleLine()
                                AimDiscreteDirection.UP -> cycleVariation()
                            }
                        },
                        onLengthDragStart = { services?.sound?.startAimTone() },
                        onLengthDrag = { fraction, _ ->
                            services?.sound?.updateAimTone(fraction)
                            liveLengthFraction = fraction
                            val band = BowlingSystem.classifyLength(fraction.toDouble())
                            if (band != lastAnnouncedLength) {
                                lastAnnouncedLength = band
                                services?.announceSpoken(BowlingSystem.lengthLabel(band))
                            }
                        },
                        onLengthDragEnd = { fraction, maxFraction ->
                            services?.sound?.stopAimTone()
                            liveLengthFraction = fraction
                            onLengthDragCommitted(fraction, maxFraction)
                            services?.announceSpoken(
                                "Length set: ${BowlingSystem.lengthLabel(BowlingSystem.classifyLength(fraction.toDouble()))}"
                            )
                        }
                    )
                }
                .semantics {
                    contentDescription = "Aim gesture area. Two-finger swipe left, right, up, or down."
                }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue to speed") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun SpeedStep(
    bowler: Player,
    variation: BowlingVariation,
    initialSpeedKmh: Int,
    onBowl: (Int) -> Unit,
    onBack: () -> Unit
) {
    val range = remember(variation) { BowlingSystem.getSpeedRangeForVariation(bowler.bowlingStyle, variation) }
    var speedKmh by remember(range) {
        mutableStateOf(initialSpeedKmh.takeIf { it in range.min..range.max } ?: ((range.min + range.max) / 2))
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Choose your speed",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Faster is riskier \u2014 it's harder to control near your top pace.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "$speedKmh km/h",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Compose's Slider carries first-class TalkBack support (it's
        // announced as adjustable, with swipe up/down or the standard
        // increment/decrement actions) — no custom gesture code needed,
        // unlike the discrete axes above.
        Slider(
            value = speedKmh.toFloat(),
            onValueChange = { speedKmh = it.roundToInt() },
            valueRange = range.min.toFloat()..range.max.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Speed: $speedKmh kilometers per hour" }
        )

        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = { onBowl(speedKmh) }, modifier = Modifier.fillMaxWidth()) { Text("Bowl") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun ResultStep(decision: ResolvedBowlingDecision, onContinue: () -> Unit) {
    val lengthName = BowlingSystem.lengthLabel(decision.actualLength)
    val tierLabel = BowlingSystem.qualityTierLabel(decision.qualityTier)

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = tierLabel,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Actual length: $lengthName")
        if (decision.forcedNoBall) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("That's a beamer \u2014 automatic no ball.")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}
