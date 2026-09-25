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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.sqrt

/**
 * The first of the three custom gesture surfaces (pitching/batting/
 * fielding — see UI_NOTES.md). Built around a TWO-FINGER gesture
 * vocabulary (see GESTURE_REDESIGN.md, section 2). Quality is computed
 * the same way the web app computes it: directly from how precisely and
 * smoothly the length drag itself was performed, via the already-ported
 * BowlingSystem.computeBowlingQuality. There is no separate timing
 * mechanic. See "WHY THE TIMING MECHANIC IS GONE" and "WHY THIS ISN'T
 * LITERALLY TWO SIMULTANEOUS POINTERS" below for the two things that
 * changed after the first on-device pass, and "WHY DISCRETE SWIPES USED
 * TO STOP WORKING AFTER ONE FIRING" for a bug fixed in this revision.
 *
 * AIM STEP GESTURE VOCABULARY (angle/line/variation/length all live on
 * ONE screen at once, each assigned its own compass direction, exactly
 * because a real bowler sets all four before ever releasing the ball.
 * Speed lives on this same screen too, as a real Slider, matching the
 * web app's single continuous bowling-setup surface):
 * - Two-finger swipe LEFT cycles ANGLE forward through its (short, 2-item)
 *   list, wrapping around.
 * - Two-finger swipe RIGHT cycles LINE forward through its list, wrapping.
 * - Two-finger swipe UP cycles VARIATION forward through this bowler's
 *   pace-or-spin variation list, wrapping.
 * - Two-finger swipe DOWN is the one CONTINUOUS axis, and determines
 *   both the intended length AND the delivery's quality (see below):
 *   once net downward movement is recognized as the dominant direction,
 *   the gesture locks into a drag that maps live vertical position to a
 *   length band, with a continuously pitch-shifted tone
 *   (SoundEngine.startAimTone/updateAimTone) tracking position while the
 *   fingers move, and a SPOKEN announcement only when the drag crosses
 *   into a new length band — never a continuous spoken readout, which
 *   would overrun TalkBack's speech queue. Lifting the fingers commits
 *   whatever band the drag last landed in.
 * - SWING is read from the SHAPE of that same down-drag, not a separate
 *   gesture: if the path bows away from the straight line between where
 *   the drag started and where it ended, that peak sideways bow (as a
 *   fraction of the gesture area's width) is fed into the already-ported
 *   BowlingSystem.classifySwing — curving toward the left is IN_SWING,
 *   curving toward the right is OUT_SWING, matching the web app's own
 *   drag-curve-based swing mechanic exactly (see computeCurveFraction
 *   and BowlingSystem.classifySwing's own doc comment).
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
 * WHY DISCRETE SWIPES USED TO STOP WORKING AFTER ONE FIRING. AimStep's
 * pointerInput block is keyed ONLY on bowler.bowlingStyle, so the
 * detectAimGesture coroutine is launched exactly once for the whole
 * life of this screen and never restarts on recomposition — and since
 * awaitEachGesture loops forever internally, the lambdas passed into it
 * (onDiscrete's cycleAngle/cycleLine/cycleVariation) were captured
 * exactly once too. Each of those functions used to read the AimStep
 * composable's `angle`/`line`/`variation` PARAMETERS directly, which are
 * plain values frozen at whatever they were on that first composition —
 * so every subsequent swipe kept recomputing "the option after the
 * ORIGINAL value" instead of "the option after the CURRENT value,"
 * silently reproducing the same result forever after the first real
 * change (which is also why the spoken announcement appeared to fire
 * only once: the value never actually changed again, so there was
 * nothing new to announce). Fixed by reading through rememberUpdatedState
 * handles (currentAngle/currentLine/currentVariation/currentServices)
 * instead — a State object whose IDENTITY stays stable across
 * recompositions (so the once-captured closure keeps a valid reference
 * to it) but whose VALUE is refreshed every recomposition, so a read
 * through it always returns the latest value even from a long-frozen
 * closure.
 *
 * SPEED is a real Slider (Compose's Slider has first-class TalkBack
 * support out of the box — no custom gesture code needed), matching the
 * web's own continuous, one-km/h-at-a-time speed control. It now lives
 * on the same screen as the rest of the aim controls, set via the
 * Slider's `steps` parameter so it snaps to whole km/h exactly like the
 * web version (e.g. 132, 131, ...) rather than a separate step of its
 * own.
 *
 * GESTURE-AREA SIZING. LENGTH_DRAG_RANGE_FRACTION_OF_HEIGHT scales the
 * length drag's full 0..1 sweep to the ACTUAL measured height of the
 * gesture Box (via PointerInputScope.size) rather than a fixed guessed
 * pixel count, so every device gets close to the full available area to
 * bowl in, and DISCRETE_SWIPE_THRESHOLD_DP / AXIS_LOCK_THRESHOLD_DP are
 * both real dp values converted to px at gesture-detection time (not
 * raw, density-dependent pixel guesses), so swipe sensitivity is
 * consistent across devices with different screen densities.
 */

private enum class PitchStep { AIM, RESULT }

// --- AimStep gesture tuning ---

// How far the active pointer must move (in dp, converted to px at
// gesture-detection time) before a left/right/up swipe counts as one
// discrete "cycle to the next option" step. Deliberately re-fireable:
// each firing resets the reference point (see detectAimGesture), so
// continuing to hold and move keeps stepping.
private val DISCRETE_SWIPE_THRESHOLD_DP = 40.dp

// Total distance (in dp) the pointer must travel from the gesture's
// start before the gesture COMMITS to being either a discrete
// left/right/up swipe or a continuous length drag. Below this distance
// the direction is still ambiguous (jitter/hand-pivot noise), so no
// decision is made yet. Once committed, the gesture never reinterprets
// itself as the other kind for the rest of this touch.
private val AXIS_LOCK_THRESHOLD_DP = 28.dp

// How much more dominant the downward component must be than the
// horizontal one, at the moment of the axis-lock decision, to commit to
// a length drag rather than a discrete swipe. Requiring real dominance
// (not just "greater than") avoids misclassifying a near-diagonal swipe
// start from hand-pivot noise.
private const val AXIS_DOMINANCE_RATIO = 1.3f

// Fraction of the gesture Box's actual measured height used as the
// length drag's full 0..1 sweep range, so every ball gets close to the
// whole available screen area rather than a fixed, unmeasured guess.
private const val LENGTH_DRAG_RANGE_FRACTION_OF_HEIGHT = 0.92f

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
    // Read from the same down-drag's curvature — see the class doc
    // comment's swing paragraph and computeCurveFraction below.
    var swingType by remember { mutableStateOf(SwingType.NONE) }
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
            onLengthDragCommitted = { finalFraction, maxFraction, swing ->
                finalLengthFraction = finalFraction
                maxLengthFractionReached = maxFraction
                swingType = swing
            },
            onBowl = { chosenSpeed ->
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
                    swingType = swingType,
                    qualityScore = quality.finalScore,
                    qualityTier = quality.tier,
                    quality = quality,
                    forcedNoBall = resolvedLength.forcedNoBall
                )
                step = PitchStep.RESULT
            },
            onBack = onBack
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

private enum class GestureMode { UNDECIDED, DISCRETE, LENGTH_DRAG }

/**
 * Peak horizontal deviation of `path` from the straight line between its
 * first and last points, as a fraction of `widthPx` — feeds directly
 * into BowlingSystem.classifySwing (positive = curved right = OUT_SWING,
 * negative = curved left = IN_SWING). Only meaningful for a
 * predominantly-vertical path, which a committed length drag always is
 * by construction (see detectAimGesture's axis-lock check).
 */
private fun computeCurveFraction(path: List<Offset>, widthPx: Float): Double {
    if (path.size < 3 || widthPx <= 0f) return 0.0
    val start = path.first()
    val end = path.last()
    val totalDy = end.y - start.y
    if (abs(totalDy) < 1f) return 0.0
    var peakDeviation = 0f
    for (point in path) {
        val t = ((point.y - start.y) / totalDy).coerceIn(0f, 1f)
        val straightLineX = start.x + (end.x - start.x) * t
        val deviation = point.x - straightLineX
        if (abs(deviation) > abs(peakDeviation)) peakDeviation = deviation
    }
    return (peakDeviation / widthPx).toDouble()
}

/**
 * The shared low-level gesture tracker AimStep's gesture area is built
 * on — see the class doc comment's "WHY THIS ISN'T LITERALLY TWO
 * SIMULTANEOUS POINTERS" for why this tracks a single active pointer
 * rather than requiring two at once, and "AIM STEP GESTURE VOCABULARY"
 * for the full direction/mode behavior this implements.
 *
 * Direction is decided ONCE per touch, after the pointer has moved at
 * least AXIS_LOCK_THRESHOLD_DP from its start point and only once one
 * axis clearly dominates (see AXIS_DOMINANCE_RATIO) — this avoids the
 * early jitter/hand-pivot noise of a real swipe's first few pixels
 * flipping the gesture into the wrong mode. Once decided, the gesture
 * NEVER reinterprets itself as the other kind for the rest of this
 * touch: a discrete swipe that later curves downward stays a discrete
 * swipe, and a length drag that wanders horizontally stays a length
 * drag (its horizontal wander is exactly what feeds swing — see
 * computeCurveFraction).
 */
private suspend fun PointerInputScope.detectAimGesture(
    onDiscrete: (AimDiscreteDirection) -> Unit,
    onLengthDragStart: () -> Unit,
    onLengthDrag: (fraction: Float, maxFractionReached: Float) -> Unit,
    onLengthDragEnd: (fraction: Float, maxFractionReached: Float, swing: SwingType) -> Unit
) {
    val discreteThresholdPx = DISCRETE_SWIPE_THRESHOLD_DP.toPx()
    val axisLockThresholdPx = AXIS_LOCK_THRESHOLD_DP.toPx()
    val gestureWidthPx = size.width.toFloat()
    val lengthDragRangePx = size.height.toFloat() * LENGTH_DRAG_RANGE_FRACTION_OF_HEIGHT

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val gestureStart = down.position
        var mode = GestureMode.UNDECIDED
        var referencePoint = gestureStart
        var lengthDragOrigin = gestureStart
        var lastFraction = 0f
        var maxFractionReached = 0f
        val lengthPath = mutableListOf<Offset>()

        fun handleDiscretePoint(position: Offset) {
            val dx = position.x - referencePoint.x
            val dy = position.y - referencePoint.y
            if (abs(dx) >= abs(dy)) {
                if (abs(dx) >= discreteThresholdPx) {
                    onDiscrete(if (dx < 0) AimDiscreteDirection.LEFT else AimDiscreteDirection.RIGHT)
                    referencePoint = position
                }
            } else if (dy < 0 && abs(dy) >= discreteThresholdPx) {
                // Upward only — downward is claimed by the length-drag branch.
                onDiscrete(AimDiscreteDirection.UP)
                referencePoint = position
            }
        }

        while (true) {
            val event = awaitPointerEvent()
            val active = event.changes.firstOrNull { it.pressed }
            if (active == null) {
                if (mode == GestureMode.LENGTH_DRAG) {
                    val curveFraction = computeCurveFraction(lengthPath, gestureWidthPx)
                    onLengthDragEnd(lastFraction, maxFractionReached, BowlingSystem.classifySwing(curveFraction))
                }
                return@awaitEachGesture
            }
            active.consume()
            val position = active.position

            when (mode) {
                GestureMode.LENGTH_DRAG -> {
                    lengthPath.add(position)
                    val netDown = position.y - lengthDragOrigin.y
                    lastFraction = (netDown / lengthDragRangePx).coerceIn(0f, 1f)
                    maxFractionReached = maxOf(maxFractionReached, lastFraction)
                    onLengthDrag(lastFraction, maxFractionReached)
                }
                GestureMode.DISCRETE -> handleDiscretePoint(position)
                GestureMode.UNDECIDED -> {
                    val dxTotal = position.x - gestureStart.x
                    val dyTotal = position.y - gestureStart.y
                    // kotlin.math.hypot only has a Double overload; staying in
                    // Float here avoids a conversion and matches
                    // axisLockThresholdPx's type directly.
                    val totalDistance = sqrt(dxTotal * dxTotal + dyTotal * dyTotal)
                    if (totalDistance >= axisLockThresholdPx) {
                        val isDominantlyDown = dyTotal > 0 && dyTotal > abs(dxTotal) * AXIS_DOMINANCE_RATIO
                        if (isDominantlyDown) {
                            mode = GestureMode.LENGTH_DRAG
                            // Fresh origin at the lock-in point (not the
                            // original touch-down) so the full configured
                            // range is available from here, rather than
                            // losing part of it to the ambiguity phase.
                            lengthDragOrigin = position
                            lengthPath.add(position)
                            onLengthDragStart()
                            lastFraction = 0f
                            maxFractionReached = 0f
                            onLengthDrag(0f, 0f)
                        } else {
                            mode = GestureMode.DISCRETE
                            referencePoint = gestureStart
                            handleDiscretePoint(position)
                        }
                    }
                }
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
    onLengthDragCommitted: (finalFraction: Float, maxFractionReached: Float, swing: SwingType) -> Unit,
    onBowl: (speedKmh: Int) -> Unit,
    onBack: () -> Unit
) {
    val services = LocalGameServices.current
    val variationOptions = remember(bowler.bowlingStyle) { BowlingSystem.getVariationOptions(bowler.bowlingStyle) }

    // See the file's "WHY DISCRETE SWIPES USED TO STOP WORKING AFTER ONE
    // FIRING" doc comment: detectAimGesture's coroutine is captured once
    // and never restarts, so cycleAngle/cycleLine/cycleVariation must
    // read through a stable-identity, freshly-valued handle rather than
    // the raw parameter, or they keep recomputing "next" from a frozen
    // first-composition value forever.
    val currentAngle by rememberUpdatedState(angle)
    val currentLine by rememberUpdatedState(line)
    val currentVariation by rememberUpdatedState(variation)
    val currentServices by rememberUpdatedState(services)

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
        val next = options[(options.indexOf(currentAngle) + 1) % options.size]
        onAngleChanged(next)
        currentServices?.announceSpoken(BowlingSystem.angleLabel(next))
    }
    fun cycleLine() {
        val options = BowlingSystem.LINE_OPTIONS
        val next = options[(options.indexOfFirst { it.value == currentLine } + 1) % options.size]
        onLineChanged(next.value)
        currentServices?.announceSpoken(next.label)
    }
    fun cycleVariation() {
        val next = variationOptions[(variationOptions.indexOfFirst { it.value == currentVariation } + 1) % variationOptions.size]
        onVariationChanged(next.value)
        currentServices?.announceSpoken(next.label)
    }

    // Speed now lives on this same screen (see class doc comment) rather
    // than a separate step. Range is keyed on the variation so it snaps
    // to a sensible mid-range value whenever the swipe-up gesture cycles
    // to a variation with a different speed range.
    val speedRange = remember(bowler.bowlingStyle, variation) {
        BowlingSystem.getSpeedRangeForVariation(bowler.bowlingStyle, variation)
    }
    var speedKmh by remember(speedRange) { mutableStateOf((speedRange.min + speedRange.max) / 2) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Aim your delivery",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Two-finger swipe left for angle, right for line, up for variation. " +
                "Swipe down and hold to set length by ear, curving left for inswing or " +
                "right for outswing, then lift your fingers to set it.",
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
                        onLengthDragStart = { currentServices?.sound?.startAimTone() },
                        onLengthDrag = { fraction, _ ->
                            currentServices?.sound?.updateAimTone(fraction)
                            liveLengthFraction = fraction
                            val band = BowlingSystem.classifyLength(fraction.toDouble())
                            if (band != lastAnnouncedLength) {
                                lastAnnouncedLength = band
                                currentServices?.announceSpoken(BowlingSystem.lengthLabel(band))
                            }
                        },
                        onLengthDragEnd = { fraction, maxFraction, swing ->
                            currentServices?.sound?.stopAimTone()
                            liveLengthFraction = fraction
                            onLengthDragCommitted(fraction, maxFraction, swing)
                            val swingSuffix = when (swing) {
                                SwingType.IN_SWING -> ", inswing"
                                SwingType.OUT_SWING -> ", outswing"
                                SwingType.NONE -> ""
                            }
                            currentServices?.announceSpoken(
                                "Length set: ${BowlingSystem.lengthLabel(BowlingSystem.classifyLength(fraction.toDouble()))}$swingSuffix"
                            )
                        }
                    )
                }
                .semantics {
                    contentDescription = "Aim gesture area. Two-finger swipe left, right, up, or down."
                }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Speed: $speedKmh km/h",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Compose's Slider carries first-class TalkBack support (it's
        // announced as adjustable, with swipe up/down or the standard
        // increment/decrement actions). `steps` makes it snap to whole
        // km/h, matching the web version's 132, 131, ... behavior.
        Slider(
            value = speedKmh.toFloat(),
            onValueChange = { speedKmh = it.roundToInt() },
            valueRange = speedRange.min.toFloat()..speedRange.max.toFloat(),
            steps = (speedRange.max - speedRange.min - 1).coerceAtLeast(0),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Speed: $speedKmh kilometers per hour" }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onBowl(speedKmh) }, modifier = Modifier.fillMaxWidth()) { Text("Bowl") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun ResultStep(decision: ResolvedBowlingDecision, onContinue: () -> Unit) {
    val lengthName = BowlingSystem.lengthLabel(decision.actualLength)
    val tierLabel = BowlingSystem.qualityTierLabel(decision.qualityTier)
    val swingLabel = when (decision.swingType) {
        SwingType.IN_SWING -> "Inswing"
        SwingType.OUT_SWING -> "Outswing"
        SwingType.NONE -> null
    }

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
        if (swingLabel != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Swing: $swingLabel")
        }
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
