package com.cricketsim.ui.match

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.BattingSystem
import com.cricketsim.logic.BowlingAngle
import com.cricketsim.logic.BowlingLength
import com.cricketsim.logic.BowlingLine
import com.cricketsim.logic.BowlingSystem
import com.cricketsim.logic.BowlingVariation
import com.cricketsim.logic.Player
import com.cricketsim.logic.ResolvedBowlingDecision
import com.cricketsim.logic.SwingType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The first of the three custom gesture surfaces (pitching/batting/
 * fielding — see UI_NOTES.md). Redesigned around a TWO-FINGER gesture
 * vocabulary (see GESTURE_REDESIGN.md, section 2) rather than the
 * original discrete list-pick-per-axis design: TalkBack intercepts
 * single-finger touch for its own explore/double-tap model, but largely
 * leaves two-finger gestures untouched, so this screen's AimStep reads
 * every one of angle, line and variation as a distinct two-finger swipe
 * direction, and length as a genuine continuous two-finger drag with
 * live TONE feedback — the closest a screen-reader-first redesign can
 * get to the web app's original continuous press-and-drag PitcherScreen,
 * while staying entirely usable with TalkBack running. Speed keeps its
 * existing slider-style list pick unchanged (already TalkBack-safe as a
 * single-finger adjustable control), and RELEASE keeps the existing
 * haptic-pulse timing minigame unchanged FOR NOW — see the KNOWN
 * SIMPLIFICATIONS below for the open question the redesign plan flags
 * about what RELEASE should mean once aim is set by swipe rather than by
 * timing.
 *
 * AIM STEP GESTURE VOCABULARY (angle/line/variation/length all live on
 * ONE screen at once, each assigned its own compass direction, exactly
 * because a real bowler sets all four before ever releasing the ball):
 * - Two-finger swipe LEFT cycles ANGLE forward through its (short, 2-item)
 *   list, wrapping around.
 * - Two-finger swipe RIGHT cycles LINE forward through its list, wrapping.
 * - Two-finger swipe UP cycles VARIATION forward through this bowler's
 *   pace-or-spin variation list, wrapping.
 * - Two-finger swipe DOWN is the one CONTINUOUS axis: once net downward
 *   movement is recognized as the dominant direction, the gesture locks
 *   into a drag that maps live vertical position to a length band, with
 *   a continuously pitch-shifted tone (SoundEngine.startAimTone /
 *   updateAimTone — see its own doc comment) tracking position while the
 *   fingers move, and a SPOKEN announcement only when the drag crosses
 *   into a new length band (debounced by construction: it only fires
 *   when the classified band actually changes, never on every move
 *   event) — never a continuous spoken readout, which would overrun
 *   TalkBack's speech queue. Lifting the fingers commits whatever band
 *   the drag last landed in.
 * Angle/line/variation each fire repeatedly while the same swipe
 * continues (each firing resets the gesture's reference point), so
 * holding two fingers and continuing to drag left, say, cycles through
 * angle options one at a time rather than needing a fresh swipe per
 * step — same "keep pushing to keep stepping" feel as a hardware
 * volume rocker. detectAimGesture below is the shared low-level
 * two-finger tracker both this discrete-cycling behavior and the
 * continuous length drag are built on; batting's shot-selection/intent
 * gestures (GESTURE_REDESIGN.md section 3, not yet built) will need
 * something structurally similar.
 *
 * The one dimension that isn't set on the AimStep is EXECUTION — how
 * well the delivery actually came out — which BowlingSystem.
 * computeBowlingQuality expects as a continuous `verticalFraction`
 * (where the release actually landed along the pitch) plus a
 * `maxVerticalFractionReached` for overshoot detection. This screen
 * still uses a REPEATING HAPTIC PULSE the player releases on: a fixed
 * sequence of vibration pulses plays, timed by
 * BattingSystem.computeTimingIntervalMs(speedKmh) — deliberately
 * reusing the exact same speed-to-interval mapping the batting timing
 * minigame uses, so "faster deliveries are harder to time" feels
 * consistent whichever side of the ball the player is on. Releasing
 * exactly on the final pulse lands dead in the center of the chosen
 * target length band; releasing early or late pushes the release point
 * away from center in proportion to the timing error (early -> shorter,
 * late -> fuller), potentially drifting into a neighboring band on a
 * bad enough miss.
 *
 * KNOWN OPEN QUESTION (GESTURE_REDESIGN.md section 2.1, not yet
 * resolved): now that length is a deliberate swipe-set aim rather than
 * something to "aim for" via timing, RELEASE's original role no longer
 * quite makes sense as-is. The redesign plan's default assumption is to
 * repurpose it as an EXECUTION-QUALITY layer independent of aim (timing
 * quality affecting pace variance, swing/seam control, and a small
 * chance of drifting off the intended line/length on a bad release)
 * rather than the aim mechanic it currently still is below — that
 * rework is intentionally NOT part of this pass; RELEASE/ResultStep
 * below are unchanged from before the gesture redesign.
 *
 * RELEASE STEP (unchanged): the whole step is ONE full-screen `clickable`
 * node and the only focusable element on it, so TalkBack focus lands
 * there and a double-tap anywhere releases. The error is measured in
 * real milliseconds with SystemClock.elapsedRealtime() against a fixed
 * schedule (lead-in + 3 intervals = the final pulse), and pulses are
 * scheduled against the start time rather than chained. There is
 * deliberately no Back on the release step — once the rhythm starts the
 * delivery is committed, same as BattingScreen's timing.
 *
 * EVERY PULSE is a buzz (Compose's LongPress haptic, if vibration is on in
 * Settings) AND an audible tick from the sound engine, with the FINAL
 * pulse accented (higher and louder) so the beat to release on can be
 * found by ear — the web does the same. The buzzes themselves are all
 * identical.
 *
 * KNOWN V1 SIMPLIFICATIONS (not permanent design decisions):
 * - `maxVerticalFractionReached` is always set equal to the final
 *   `verticalFraction` (the release point only ever moves toward its
 *   final value here, never overshoots and corrects), so the
 *   smoothness penalty is always 0 for a user-bowled delivery in this
 *   version.
 * - Swing is not player-controlled yet (always SwingType.NONE).
 * - PITCH_INPUT_LATENCY_COMPENSATION_MS is 0, and the AimStep's own
 *   DISCRETE_SWIPE_THRESHOLD_PX / LENGTH_DRAG_ENTRY_THRESHOLD_PX /
 *   LENGTH_DRAG_FULL_RANGE_PX are all unmeasured guesses — every one of
 *   these needs a real-device calibration pass (GESTURE_REDESIGN.md
 *   section 5.1 flags the same requirement for the timing windows).
 * - The timing logic in ReleaseStep duplicates BattingScreen's
 *   BatTimingStep. If a third timing surface ever appears, extract a
 *   shared composable.
 */

private enum class PitchStep { AIM, SPEED, RELEASE, RESULT }

// Number of haptic pulses in the release rhythm; the player releases on
// the LAST one. Kept small and fixed — a short rhythm is easier to
// internalize than a long one, and BattingSystem.computeTimingIntervalMs
// already does the real work of making faster deliveries harder by
// shortening the gap between pulses, not by adding more of them.
private const val PULSE_COUNT = 4

// How far a release point can drift from the target band's center on a
// maximally-mistimed tap, as a fraction of the whole 0-1 pitch length —
// large enough that a bad miss can plausibly land in a neighboring band
// (each band is 1/7 wide) or even the one beyond it, matching how
// forgiving-but-punishing BowlingSystem.resolveActualLength already is
// about a Bad/Very Bad tier.
private const val MAX_DEVIATION = 0.5

// Delay before the first pulse. Matches BattingScreen: the longer value
// is used under touch exploration so TalkBack's speech finishes before
// the rhythm starts.
private const val PITCH_LEAD_IN_MS_STANDARD = 550L
private const val PITCH_LEAD_IN_MS_SCREEN_READER = 1400L

// How long after the final pulse to wait for a release before scoring it
// as a very late one, in inter-pulse intervals.
private const val PITCH_NO_RELEASE_GRACE_INTERVALS = 2.0

// Subtracted from the measured tap time. Zero until measured on a device.
private const val PITCH_INPUT_LATENCY_COMPENSATION_MS = 0.0

// --- AimStep gesture tuning (all unmeasured on a real device — see the
// class doc comment's KNOWN V1 SIMPLIFICATIONS) ---

// How far the two-finger centroid must move, in raw pixels, before a
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

@Composable
fun PitchingScreen(bowler: Player, onDeliveryResolved: (ResolvedBowlingDecision) -> Unit, onBack: () -> Unit) {
    var step by remember { mutableStateOf(PitchStep.AIM) }
    var angle by remember { mutableStateOf(BowlingSystem.ANGLE_OPTIONS.first().value) }
    var line by remember { mutableStateOf(BowlingSystem.LINE_OPTIONS.first().value) }
    var variation by remember { mutableStateOf(BowlingVariation.STOCK) }
    var targetLength by remember { mutableStateOf(BowlingLength.GOOD_LENGTH) }
    var speedKmh by remember { mutableStateOf(0) }
    var decision by remember { mutableStateOf<ResolvedBowlingDecision?>(null) }

    when (step) {
        PitchStep.AIM -> AimStep(
            bowler = bowler,
            angle = angle,
            line = line,
            variation = variation,
            targetLength = targetLength,
            onAngleChanged = { angle = it },
            onLineChanged = { line = it },
            onVariationChanged = { variation = it },
            onLengthChanged = { targetLength = it },
            onContinue = { step = PitchStep.SPEED },
            onBack = onBack
        )
        PitchStep.SPEED -> SpeedStep(
            bowler = bowler,
            variation = variation,
            onSelected = { chosen -> speedKmh = chosen; step = PitchStep.RELEASE },
            onBack = { step = PitchStep.AIM }
        )
        PitchStep.RELEASE -> ReleaseStep(
            speedKmh = speedKmh,
            targetLength = targetLength,
            onReleased = { verticalFraction ->
                val speedRange = BowlingSystem.getSpeedRangeForVariation(bowler.bowlingStyle, variation)
                val quality = BowlingSystem.computeBowlingQuality(
                    verticalFraction = verticalFraction,
                    maxVerticalFractionReached = verticalFraction, // See KNOWN V1 SIMPLIFICATIONS above.
                    speedKmh = speedKmh,
                    speedRange = speedRange,
                    bowlingRating = bowler.bowlingRating
                )
                val resolvedLength = BowlingSystem.resolveActualLength(targetLength, quality.tier)
                decision = ResolvedBowlingDecision(
                    angle = angle,
                    line = line,
                    variation = variation,
                    bowlingStyle = bowler.bowlingStyle,
                    speedKmh = speedKmh,
                    intendedLength = targetLength,
                    actualLength = resolvedLength.actualLength,
                    swingType = SwingType.NONE, // See KNOWN V1 SIMPLIFICATIONS above.
                    qualityScore = quality.finalScore,
                    qualityTier = quality.tier,
                    quality = quality,
                    forcedNoBall = resolvedLength.forcedNoBall
                )
                step = PitchStep.RESULT
            }
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
 * The shared low-level two-finger tracker AimStep's gesture area is built
 * on — see the class doc comment's "AIM STEP GESTURE VOCABULARY" section
 * for the full behavior this implements. Never engages until at least two
 * fingers are down, so single-finger TalkBack touch exploration is always
 * left completely untouched.
 */
private suspend fun PointerInputScope.detectAimGesture(
    onDiscrete: (AimDiscreteDirection) -> Unit,
    onLengthDragStart: () -> Unit,
    onLengthDrag: (fraction: Float) -> Unit,
    onLengthDragEnd: (fraction: Float) -> Unit
) {
    fun centroidOf(event: PointerEvent): Offset {
        val points = event.changes.filter { it.pressed }.take(2).map { it.position }
        if (points.isEmpty()) return Offset.Zero
        return Offset(
            points.sumOf { it.x.toDouble() }.toFloat() / points.size,
            points.sumOf { it.y.toDouble() }.toFloat() / points.size
        )
    }

    awaitEachGesture {
        // Wait for at least two fingers down before tracking anything.
        var event = awaitPointerEvent()
        while (event.changes.count { it.pressed } < 2) {
            if (event.changes.none { it.pressed }) return@awaitEachGesture
            event = awaitPointerEvent()
        }

        val gestureStart = centroidOf(event)
        var referencePoint = gestureStart
        var lengthDragActive = false
        var lastFraction = 0f

        while (true) {
            event.changes.forEach { if (it.pressed) it.consume() }
            event = awaitPointerEvent()
            if (event.changes.count { it.pressed } < 2) {
                if (lengthDragActive) onLengthDragEnd(lastFraction)
                return@awaitEachGesture
            }
            val centroid = centroidOf(event)

            if (lengthDragActive) {
                val netDown = centroid.y - gestureStart.y
                lastFraction = (netDown / LENGTH_DRAG_FULL_RANGE_PX).coerceIn(0f, 1f)
                onLengthDrag(lastFraction)
                continue
            }

            val netDownFromStart = centroid.y - gestureStart.y
            val netRightFromStart = centroid.x - gestureStart.x
            // Downward movement locks into continuous length mode for the
            // rest of this touch — it never reverts to discrete swiping.
            if (netDownFromStart > LENGTH_DRAG_ENTRY_THRESHOLD_PX && netDownFromStart > abs(netRightFromStart)) {
                lengthDragActive = true
                onLengthDragStart()
                lastFraction = (netDownFromStart / LENGTH_DRAG_FULL_RANGE_PX).coerceIn(0f, 1f)
                onLengthDrag(lastFraction)
                continue
            }

            val dx = centroid.x - referencePoint.x
            val dy = centroid.y - referencePoint.y
            if (abs(dx) >= abs(dy)) {
                if (abs(dx) >= DISCRETE_SWIPE_THRESHOLD_PX) {
                    onDiscrete(if (dx < 0) AimDiscreteDirection.LEFT else AimDiscreteDirection.RIGHT)
                    referencePoint = centroid
                }
            } else if (dy < 0 && abs(dy) >= DISCRETE_SWIPE_THRESHOLD_PX) {
                // Upward only — downward is claimed by the length-drag check above.
                onDiscrete(AimDiscreteDirection.UP)
                referencePoint = centroid
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
    targetLength: BowlingLength,
    onAngleChanged: (BowlingAngle) -> Unit,
    onLineChanged: (BowlingLine) -> Unit,
    onVariationChanged: (BowlingVariation) -> Unit,
    onLengthChanged: (BowlingLength) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val services = LocalGameServices.current
    val variationOptions = remember(bowler.bowlingStyle) { BowlingSystem.getVariationOptions(bowler.bowlingStyle) }
    // Tracks the live drag position while dragging; committed back into
    // targetLength (via onLengthChanged) only once the fingers lift.
    var liveLength by remember(targetLength) { mutableStateOf(targetLength) }

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
            text = "Length: ${BowlingSystem.lengthLabel(liveLength)}",
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
                        onLengthDrag = { fraction ->
                            services?.sound?.updateAimTone(fraction)
                            val band = BowlingSystem.classifyLength(fraction.toDouble())
                            if (band != liveLength) {
                                liveLength = band
                                services?.announceSpoken(BowlingSystem.lengthLabel(band))
                            }
                        },
                        onLengthDragEnd = { fraction ->
                            services?.sound?.stopAimTone()
                            val band = BowlingSystem.classifyLength(fraction.toDouble())
                            liveLength = band
                            onLengthChanged(band)
                            services?.announceSpoken("Length set: ${BowlingSystem.lengthLabel(band)}")
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
private fun SpeedStep(bowler: Player, variation: BowlingVariation, onSelected: (Int) -> Unit, onBack: () -> Unit) {
    val range = remember(variation) { BowlingSystem.getSpeedRangeForVariation(bowler.bowlingStyle, variation) }
    val span = range.max - range.min
    val tiers = remember(range) {
        listOf(
            "Back off the pace" to (range.min + (span * 0.15)).roundToInt(),
            "Standard pace" to (range.min + (span * 0.5)).roundToInt(),
            "Full pace" to (range.min + (span * 0.85)).roundToInt()
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Choose your speed",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Faster is riskier \u2014 it's harder to time your release well.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        // The release step has almost no spoken text on purpose (so it
        // never talks over the rhythm), so the instructions live here.
        Text(
            "Next you'll feel four buzzes and hear four ticks. Double-tap anywhere on the fourth, " +
                "the higher, louder tick, to release. Early tends to go shorter, late tends to go fuller.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        tiers.forEach { (label, kmh) ->
            Text(
                text = "$label ($kmh km/h)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = "Select $label, $kmh kilometers per hour",
                        role = Role.Button,
                        onClick = { onSelected(kmh) }
                    )
                    .padding(vertical = 14.dp, horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

/** Maps a signed timing error (ms, negative = early) to a release-point verticalFraction around the target band's center. */
private fun computeVerticalFraction(errorMs: Double, intervalMs: Double, targetLength: BowlingLength): Double {
    val band = BowlingSystem.LENGTH_BANDS.first { it.value == targetLength }
    val center = (band.rangeStart + band.rangeEnd) / 2
    val halfWindow = intervalMs / 2.0
    val errorFraction = (abs(errorMs) / halfWindow).coerceIn(0.0, 1.0)
    val direction = if (errorMs < 0) -1.0 else 1.0 // early -> shorter (toward 0), late -> fuller (toward 1)
    return (center + direction * errorFraction * MAX_DEVIATION).coerceIn(0.0, 1.0)
}

@Composable
private fun ReleaseStep(speedKmh: Int, targetLength: BowlingLength, onReleased: (Double) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val services = LocalGameServices.current
    val currentOnReleased by rememberUpdatedState(onReleased)

    val screenReaderActive = remember {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        manager?.isTouchExplorationEnabled == true
    }
    val intervalMs = remember(speedKmh) { BattingSystem.computeTimingIntervalMs(speedKmh) }
    val leadInMs = if (screenReaderActive) PITCH_LEAD_IN_MS_SCREEN_READER else PITCH_LEAD_IN_MS_STANDARD
    // Milliseconds after the timer starts at which the final pulse lands.
    val expectedMs = leadInMs + (PULSE_COUNT - 1) * intervalMs

    var startMs by remember { mutableStateOf(0L) }
    var pulsesFired by remember { mutableStateOf(0) }
    var resolved by remember { mutableStateOf(false) }

    fun resolve(errorMs: Double) {
        if (resolved) return
        resolved = true
        currentOnReleased(computeVerticalFraction(errorMs, intervalMs, targetLength))
    }

    LaunchedEffect(intervalMs) {
        val start = SystemClock.elapsedRealtime()
        startMs = start
        for (i in 0 until PULSE_COUNT) {
            // Scheduled against the start time, not chained off the
            // previous delay, so timer drift never accumulates.
            val target = start + leadInMs + (i * intervalMs).toLong()
            val wait = target - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            if (resolved) return@LaunchedEffect
            pulsesFired = i + 1
            // Vibration is a setting; the tick is gated by Sound effects in
            // the engine. The final pulse's tick is the accented one.
            if (services?.settings?.vibration != false) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            services?.sound?.playTimingTick(accent = i == PULSE_COUNT - 1)
        }
        // Never released: wait a grace window, then score it as a very
        // late release rather than hanging forever.
        delay((intervalMs * PITCH_NO_RELEASE_GRACE_INTERVALS).toLong())
        resolve(errorMs = intervalMs * 2)
    }

    fun handleRelease() {
        if (startMs == 0L) return
        val elapsedMs = (SystemClock.elapsedRealtime() - startMs).toDouble() - PITCH_INPUT_LATENCY_COMPENSATION_MS
        resolve(errorMs = elapsedMs - expectedMs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClickLabel = "release on the fourth buzz",
                role = Role.Button,
                onClick = { handleRelease() }
            )
            .semantics { contentDescription = "Release" },
        contentAlignment = Alignment.Center
    ) {
        // Purely visual — cleared semantics keep this from becoming a
        // second focus stop or a live region talking over the rhythm.
        Column(
            modifier = Modifier.clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (pulsesFired == 0) "Get ready\u2026" else "Buzz $pulsesFired of $PULSE_COUNT",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tap anywhere to release", style = MaterialTheme.typography.bodyMedium)
        }
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
