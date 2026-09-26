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
 * changed after the first on-device pass, "WHY THE BOWLING SETUP
 * CARRIES BETWEEN BALLS" for how angle/line/variation/speed persist
 * within an over, and "WHY GESTURES USED TO BE INCONSISTENT, AND WHY
 * LENGTH USED TO SILENTLY DEFAULT TO A PERFECT GOOD-LENGTH BALL" for
 * two bugs fixed in this revision.
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
 *   whatever band the drag last landed in. Moving outside the gesture
 *   area entirely is announced once per exit (see "outside the bowling
 *   area" below), matching the web app.
 * - SWING is read from the SHAPE of that same down-drag, not a separate
 *   gesture: if the path bows away from the straight line between where
 *   the drag started and where it ended, that peak sideways bow (as a
 *   fraction of the gesture area's width) is fed into the already-ported
 *   BowlingSystem.classifySwing — curving toward the left is IN_SWING,
 *   curving toward the right is OUT_SWING, matching the web app's own
 *   drag-curve-based swing mechanic exactly (see computeCurveFraction
 *   and BowlingSystem.classifySwing's own doc comment).
 * EACH swipe (however far it travels) fires angle/line/variation
 * EXACTLY ONCE — see "WHY GESTURES USED TO BE INCONSISTENT" below for
 * why this changed from the earlier "hold and keep dragging to keep
 * stepping" behavior. To cycle again, lift and swipe again, the same
 * way flipping through photos or list pages works elsewhere on the
 * platform.
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
 * WHY GESTURES USED TO BE INCONSISTENT, AND WHY LENGTH USED TO SILENTLY
 * DEFAULT TO A PERFECT GOOD-LENGTH BALL. Two real bugs, one shared root
 * cause: detectAimGesture used to make TWO SEPARATE, DISAGREEING checks
 * to classify one touch — a ratio test to decide "is this a down-drag"
 * versus a totally different distance-threshold test, run independently
 * INSIDE the discrete-swipe branch, to decide left/right versus up. A
 * single real swipe could satisfy one check's idea of "yes" while
 * failing the other's, and a discrete swipe's own threshold could be
 * crossed more than once during ONE continued swipe (each firing reset
 * its own reference point, "hold and keep dragging to cycle further" by
 * design) — so a longer-than-intended swipe could fire twice and look
 * like it "skipped" an option, while a shorter or more diagonal one
 * could fail either check and do nothing at all. This hit the down-drag
 * hardest of all: if it never crossed its own separate, stricter
 * dominance threshold before the fingers lifted, the gesture was
 * silently abandoned — and finalLengthFraction, defaulted to 0.5 (dead
 * center of Good Length, zero smoothness penalty), was never updated,
 * so an unrecognized swipe quietly bowled a "Perfect Ball" at Good
 * Length instead of visibly failing.
 *
 * Fixed with ONE unified decision per touch instead of two disagreeing
 * ones: detectAimGesture keeps re-evaluating the SAME dxTotal/dyTotal
 * ratio, from the same fixed start point, on every event, until the
 * total distance clears AXIS_LOCK_THRESHOLD_PX — then whichever axis is
 * dominant AT THAT MOMENT decides the whole touch, once, with no second,
 * separately-thresholded check to disagree with it: dominantly down
 * commits to the continuous length drag; otherwise the larger of
 * horizontal/vertical fires LEFT/RIGHT or UP immediately and the rest of
 * the touch is ignored (mode becomes DISCRETE_DONE) — no more re-firing
 * on one long continued swipe. AXIS_DOMINANCE_RATIO was also loosened
 * (1.3 -> 1.15) specifically for the down-drag, since a real downward
 * swipe often carries some sideways drift from a hand pivoting at the
 * wrist, and the old, stricter ratio made that legitimate case fail the
 * "is this down" check too often.
 *
 * And length no longer has a silent, automatically-selected default at
 * all: finalLengthFraction is nullable, starts at null, and the Bowl
 * button is disabled until a real length drag actually commits one — an
 * unrecognized or skipped swipe now visibly blocks bowling (with a
 * stated reason) instead of quietly submitting an invented Perfect
 * Good-Length ball. See AimStep's committedLengthFraction/canBowl below.
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
 * bowl in, and AXIS_LOCK_THRESHOLD_DP is a real dp value converted to
 * px at gesture-detection time (not a raw, density-dependent pixel
 * guess), so swipe sensitivity is consistent across devices with
 * different screen densities. AimStep's own layout also deliberately
 * keeps every OTHER element on this screen as compact as it reasonably
 * can (smaller text styles, tighter spacers, a single combined
 * angle/line/variation line instead of three separate ones)
 * specifically so the gesture Box's weight(1f) has as much leftover
 * vertical space as possible to claim — the Box only ever gets whatever
 * the Column's OTHER, non-weighted children don't use, so shrinking
 * them is what actually grows the bowling area, not a further increase
 * to LENGTH_DRAG_RANGE_FRACTION_OF_HEIGHT itself (already close to the
 * Box's own full height).
 *
 * WHY THE BOWLING SETUP CARRIES BETWEEN BALLS. A real bowler doesn't
 * re-decide their angle, line, variation and pace from scratch before
 * every single ball of an over — they bowl a plan across the six balls
 * and adjust it. MatchScreen.kt now remembers the LAST bowling setup
 * actually used (a BowlingSetupMemory, captured at the moment "Bowl" is
 * tapped) and passes it back in as this screen's initialSetup for the
 * next ball, so angle/line/variation/speed all start from where the
 * previous ball left them rather than resetting to defaults every time
 * — but only within the SAME over: MatchScreen clears that memory the
 * moment matchState.score.overs changes, so a new over always starts
 * fresh. Line and angle are free to be changed for any given ball
 * without that change disturbing speed or variation — they're
 * independent pieces of state here, and always have been; the only
 * place speed and variation are actually coupled to each other is
 * variation's own speed RANGE (a slower variation simply can't be
 * bowled at pace speeds), which is a physical constraint, not
 * incidental state coupling.
 */

private enum class PitchStep { AIM, RESULT }

/**
 * The bowling setup actually used for one delivery — captured when
 * "Bowl" is tapped (see AimStep's onBowl) and handed back in as the
 * next ball's starting point within the same over. See the class doc
 * comment's "WHY THE BOWLING SETUP CARRIES BETWEEN BALLS".
 */
data class BowlingSetupMemory(
    val angle: BowlingAngle,
    val line: BowlingLine,
    val variation: BowlingVariation,
    val speedKmh: Int
)

// --- AimStep gesture tuning ---

// Total distance (in dp, converted to px at gesture-detection time) the
// pointer must travel from the gesture's start before ONE decision is
// made for the whole touch: dominantly down commits to the continuous
// length drag; otherwise the larger of horizontal/vertical fires
// LEFT/RIGHT or UP immediately. See the class doc comment's "WHY
// GESTURES USED TO BE INCONSISTENT" for why this replaced two separate,
// disagreeing thresholds.
private val AXIS_LOCK_THRESHOLD_DP = 24.dp

// How much more dominant the downward component must be than the
// horizontal one, at the moment of the axis-lock decision, to commit to
// a length drag rather than a discrete swipe. Loosened from 1.3 -- see
// the class doc comment's "WHY GESTURES USED TO BE INCONSISTENT" for
// why a real downward swipe's natural sideways drift made the stricter
// ratio fail too often.
private const val AXIS_DOMINANCE_RATIO = 1.15f

// Fraction of the gesture Box's actual measured height used as the
// length drag's full 0..1 sweep range, so every ball gets close to the
// whole available screen area rather than a fixed, unmeasured guess.
// See the class doc comment's "GESTURE-AREA SIZING" — the Box's own
// actual height is the real lever here, not this fraction.
private const val LENGTH_DRAG_RANGE_FRACTION_OF_HEIGHT = 0.95f

@Composable
fun PitchingScreen(
    bowler: Player,
    onDeliveryResolved: (ResolvedBowlingDecision) -> Unit,
    onBack: () -> Unit,
    // See the class doc comment's "WHY THE BOWLING SETUP CARRIES BETWEEN
    // BALLS". Both default so any other call site keeps compiling as a
    // single-ball setup with no memory.
    initialSetup: BowlingSetupMemory? = null,
    onSetupChanged: (BowlingSetupMemory) -> Unit = {}
) {
    var step by remember { mutableStateOf(PitchStep.AIM) }
    var angle by remember { mutableStateOf(initialSetup?.angle ?: BowlingSystem.ANGLE_OPTIONS.first().value) }
    var line by remember { mutableStateOf(initialSetup?.line ?: BowlingSystem.LINE_OPTIONS.first().value) }
    var variation by remember { mutableStateOf(initialSetup?.variation ?: BowlingVariation.STOCK) }
    // The length drag IS both the intended length and its own execution
    // precision (see the class doc comment) — these are the only
    // length-related state; the intended BowlingLength is derived from
    // finalLengthFraction via BowlingSystem.classifyLength, never stored
    // separately. NULL until a real length drag commits one -- see the
    // class doc comment's "WHY LENGTH USED TO SILENTLY DEFAULT..."; this
    // is what lets AimStep refuse to bowl until it's genuinely set.
    // Deliberately NOT carried over between balls even within the same
    // over — length is the one thing a bowler genuinely re-aims fresh
    // delivery to delivery, unlike the other three, more strategy-level
    // choices.
    var finalLengthFraction by remember { mutableStateOf<Float?>(null) }
    var maxLengthFractionReached by remember { mutableStateOf(0f) }
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
            committedLengthFraction = finalLengthFraction,
            initialSpeedKmh = initialSetup?.speedKmh,
            onAngleChanged = { angle = it },
            onLineChanged = { line = it },
            onVariationChanged = { variation = it },
            onLengthDragCommitted = { finalFraction, maxFraction, swing ->
                finalLengthFraction = finalFraction
                maxLengthFractionReached = maxFraction
                swingType = swing
            },
            onBowl = { chosenSpeed ->
                // Guaranteed non-null here: AimStep's Bowl button is
                // disabled until committedLengthFraction is set (see the
                // class doc comment). Falls back to a safe default only
                // as defensive insurance against a future call-site bug,
                // never expected to actually run.
                val lengthFraction = finalLengthFraction ?: 0.5f
                // Captured here, at the moment this delivery's setup is
                // actually finalized — see the class doc comment's "WHY
                // THE BOWLING SETUP CARRIES BETWEEN BALLS".
                onSetupChanged(BowlingSetupMemory(angle, line, variation, chosenSpeed))
                val speedRange = BowlingSystem.getSpeedRangeForVariation(bowler.bowlingStyle, variation)
                // Exactly the web's own quality rubric, fed by the length
                // drag's own live-tracked precision/smoothness — see the
                // class doc comment's "WHY THE TIMING MECHANIC IS GONE".
                val quality = BowlingSystem.computeBowlingQuality(
                    verticalFraction = lengthFraction.toDouble(),
                    maxVerticalFractionReached = maxLengthFractionReached.toDouble(),
                    speedKmh = chosenSpeed,
                    speedRange = speedRange,
                    bowlingRating = bowler.bowlingRating
                )
                val targetLength = BowlingSystem.classifyLength(lengthFraction.toDouble())
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

// DISCRETE_DONE: a left/right/up swipe already fired for this touch;
// further movement before lift is ignored. See the class doc comment's
// "WHY GESTURES USED TO BE INCONSISTENT" for why a discrete swipe now
// fires exactly once per touch instead of being re-fireable mid-drag.
private enum class GestureMode { UNDECIDED, DISCRETE_DONE, LENGTH_DRAG }

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
 * rather than requiring two at once, "AIM STEP GESTURE VOCABULARY" for
 * the full direction/mode behavior this implements, and "WHY GESTURES
 * USED TO BE INCONSISTENT" for the bug this fixes.
 *
 * ONE decision is made per touch: while still UNDECIDED, dxTotal/dyTotal
 * (both measured from the SAME fixed gestureStart) are re-evaluated on
 * EVERY event — not just once, at the instant the lock threshold is
 * first crossed, which would lock in a decision from a single,
 * possibly-noisy data point. The moment total distance clears
 * AXIS_LOCK_THRESHOLD_PX, whichever axis is dominant AT THAT EVENT wins,
 * once, for the whole rest of the touch: dominantly down commits to a
 * continuous length drag; otherwise the larger of horizontal/vertical
 * fires LEFT/RIGHT or UP immediately and the touch moves to
 * DISCRETE_DONE, where further movement before lift is simply ignored.
 * There is no second, separately-thresholded check anywhere that could
 * disagree with this one.
 */
private suspend fun PointerInputScope.detectAimGesture(
    onDiscrete: (AimDiscreteDirection) -> Unit,
    onLengthDragStart: () -> Unit,
    onLengthDrag: (fraction: Float, maxFractionReached: Float) -> Unit,
    onLengthDragEnd: (fraction: Float, maxFractionReached: Float, swing: SwingType) -> Unit,
    // Called with true the moment the active pointer first leaves the
    // gesture area's bounds, and with false the moment it comes back
    // in — see AimStep's usage for the spoken "outside the bowling
    // area" announcement, matching the web app.
    onBoundsChanged: (outside: Boolean) -> Unit
) {
    val axisLockThresholdPx = AXIS_LOCK_THRESHOLD_DP.toPx()
    val gestureWidthPx = size.width.toFloat()
    val gestureHeightPx = size.height.toFloat()
    val lengthDragRangePx = gestureHeightPx * LENGTH_DRAG_RANGE_FRACTION_OF_HEIGHT

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val gestureStart = down.position
        var mode = GestureMode.UNDECIDED
        var lengthDragOrigin = gestureStart
        var lastFraction = 0f
        var maxFractionReached = 0f
        var isOutside = false
        val lengthPath = mutableListOf<Offset>()

        while (true) {
            val event = awaitPointerEvent()
            val active = event.changes.firstOrNull { it.pressed }
            if (active == null) {
                if (isOutside) {
                    isOutside = false
                    onBoundsChanged(false)
                }
                if (mode == GestureMode.LENGTH_DRAG) {
                    val curveFraction = computeCurveFraction(lengthPath, gestureWidthPx)
                    onLengthDragEnd(lastFraction, maxFractionReached, BowlingSystem.classifySwing(curveFraction))
                }
                return@awaitEachGesture
            }
            active.consume()
            val position = active.position

            val nowOutside = position.x < 0f || position.x > gestureWidthPx || position.y < 0f || position.y > gestureHeightPx
            if (nowOutside != isOutside) {
                isOutside = nowOutside
                onBoundsChanged(isOutside)
            }

            when (mode) {
                GestureMode.LENGTH_DRAG -> {
                    lengthPath.add(position)
                    val netDown = position.y - lengthDragOrigin.y
                    lastFraction = (netDown / lengthDragRangePx).coerceIn(0f, 1f)
                    maxFractionReached = maxOf(maxFractionReached, lastFraction)
                    onLengthDrag(lastFraction, maxFractionReached)
                }
                GestureMode.DISCRETE_DONE -> { /* already fired for this touch; ignore until lift */ }
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
                            // Larger of the two axes wins, once, for this
                            // whole touch -- see the class doc comment's
                            // "WHY GESTURES USED TO BE INCONSISTENT".
                            if (abs(dxTotal) >= abs(dyTotal)) {
                                onDiscrete(if (dxTotal < 0) AimDiscreteDirection.LEFT else AimDiscreteDirection.RIGHT)
                            } else {
                                onDiscrete(AimDiscreteDirection.UP)
                            }
                            mode = GestureMode.DISCRETE_DONE
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
    // Null until a real length drag actually commits one — see the file
    // doc comment's "WHY LENGTH USED TO SILENTLY DEFAULT...". Drives
    // both the displayed length text and whether Bowl is enabled.
    committedLengthFraction: Float?,
    // Seeds this ball's speed from the previous ball's setup within the
    // same over — see the class doc comment's "WHY THE BOWLING SETUP
    // CARRIES BETWEEN BALLS". Null for the first ball of an over.
    initialSpeedKmh: Int?,
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
    // FIRING" doc comment (an earlier fix, kept here): detectAimGesture's
    // coroutine is captured once and never restarts, so
    // cycleAngle/cycleLine/cycleVariation must read through a
    // stable-identity, freshly-valued handle rather than the raw
    // parameter, or they keep recomputing "next" from a frozen
    // first-composition value forever.
    val currentAngle by rememberUpdatedState(angle)
    val currentLine by rememberUpdatedState(line)
    val currentVariation by rememberUpdatedState(variation)
    val currentServices by rememberUpdatedState(services)

    // Tracks the live drag position while dragging; committed back up
    // (via onLengthDragCommitted) only once the fingers lift. Null until
    // the length drag actually starts -- see the class doc comment.
    var liveLengthFraction by remember(committedLengthFraction) { mutableStateOf(committedLengthFraction) }
    // Only used to detect a zone CROSSING (a genuine value change), so the
    // spoken announcement never fires on every touch-move event.
    var lastAnnouncedLength by remember(committedLengthFraction) {
        mutableStateOf(committedLengthFraction?.let { BowlingSystem.classifyLength(it.toDouble()) })
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
    // to a variation with a different speed range. initialSpeedKmh (the
    // previous ball's speed within this same over) seeds it instead of
    // the range's midpoint whenever it's present AND still fits the
    // current range — see the class doc comment's "WHY THE BOWLING SETUP
    // CARRIES BETWEEN BALLS".
    val speedRange = remember(bowler.bowlingStyle, variation) {
        BowlingSystem.getSpeedRangeForVariation(bowler.bowlingStyle, variation)
    }
    var speedKmh by remember(speedRange) {
        val seeded = initialSpeedKmh?.takeIf { it in speedRange.min..speedRange.max }
        mutableStateOf(seeded ?: (speedRange.min + speedRange.max) / 2)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Aim your delivery",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Kept to one short line (see the class doc comment's
        // "GESTURE-AREA SIZING") — the full gesture vocabulary is
        // documented on first use elsewhere; a returning player doesn't
        // need three sentences of instructions eating into the gesture
        // area on every single delivery.
        Text(
            "Swipe: left = angle, right = line, up = variation, down+hold = length " +
                "(curve for swing) \u2014 length is required before you can bowl.",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Angle/line/variation combined into ONE line (was three
        // separate bodyLarge lines) — see the class doc comment's
        // "GESTURE-AREA SIZING" for why: every line reclaimed here is a
        // line handed straight to the gesture Box below.
        Text(
            text = "${BowlingSystem.angleLabel(angle)} \u00b7 " +
                "${BowlingSystem.LINE_OPTIONS.first { it.value == line }.label} \u00b7 " +
                variationOptions.first { it.value == variation }.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = liveLengthFraction?.let {
                "Length: ${BowlingSystem.lengthLabel(BowlingSystem.classifyLength(it.toDouble()))}"
            } ?: "Length: not set \u2014 swipe down to choose",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )

        Spacer(modifier = Modifier.height(8.dp))

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
                        },
                        onBoundsChanged = { outside ->
                            // Matches the web app: announced once on
                            // exit, not repeated while it stays outside.
                            if (outside) currentServices?.announceSpoken("Outside the bowling area")
                        }
                    )
                }
                .semantics {
                    contentDescription = "Aim gesture area. Two-finger swipe left, right, up, or down."
                }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Speed: $speedKmh km/h",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(2.dp))
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

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { onBowl(speedKmh) },
            enabled = committedLengthFraction != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (committedLengthFraction != null) "Bowl" else "Bowl (set length first)")
        }
        Spacer(modifier = Modifier.height(6.dp))
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
