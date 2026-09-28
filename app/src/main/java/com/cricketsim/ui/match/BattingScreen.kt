package com.cricketsim.ui.match

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.BattingDecision
import com.cricketsim.logic.BattingSystem
import com.cricketsim.logic.BowlingQualityTier
import com.cricketsim.logic.BowlingSystem
import com.cricketsim.logic.FootworkType
import com.cricketsim.logic.IntentDirection
import com.cricketsim.logic.NamedShot
import com.cricketsim.logic.Player
import com.cricketsim.logic.ResolvedBowlingDecision
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * The second of the three custom gesture surfaces (pitching/batting/
 * fielding — see UI_NOTES.md). Like PitchingScreen, this is a FRESH
 * TalkBack-native design, not a port of the web app's
 * BattingShotScreen (whose continuous press-and-drag shot/intent
 * gestures have no reliable non-visual equivalent on Android).
 *
 * STATUS (see GESTURE_REDESIGN.md section 3): footwork, shot selection
 * and intent now use the two-finger gesture vocabulary from that
 * section, implemented directly on top of the same
 * BattingSystem.classifyShot / classifyIntentDirection functions the
 * logic layer already ported for exactly this purpose — see "GESTURE
 * IMPLEMENTATION" below. The timing step (unchanged from before this
 * pass) still uses its original tap-on-the-beat mechanic; the widened
 * Perfect-window fix planned in section 5.1 is DELIBERATELY NOT done
 * here, for the same reason the plan itself gives: it needs a real
 * on-device latency measurement first, not a guessed number.
 *
 * FLOW — mirrors the web app's ORDER of decisions, which matters
 * mechanically, not just for feel. FOOTWORK itself is chosen one level
 * up, in MatchScreen.kt's own "Face next ball" control (tap = front
 * foot, press-and-hold = back foot — see that file's
 * FaceNextBallControl), BEFORE this screen is even shown. That's also
 * where `generateDelivery` is actually invoked (via `remember` below,
 * the moment this screen enters composition) and its result handed
 * straight to the batter, so the web plan's blind-commit property (the
 * ball is only revealed once footwork is locked in) is preserved even
 * though this screen no longer owns that step itself:
 *   1. SHOT — the delivery is revealed (ACTUAL length after any
 *      bowling mis-execution, line, variation, angle, speed — same set
 *      the web's summary line reveals; never the bowler's quality tier
 *      or intended length), AND any changes the AI captain has just made
 *      to its field for this delivery ("Kohli moved from Mid-On to
 *      Long-On."), then one of the 15 named shots is picked via a
 *      continuous two-finger vertical swipe. Releasing the swipe both
 *      COMMITS the shot and advances the step immediately — see
 *      "GESTURE IMPLEMENTATION" below for why this differs from
 *      PitchingScreen's length drag. The field matters: it is part of
 *      what the shot is chosen against, so a batter who can't see it
 *      must be TOLD it, as the web does.
 *   2. INTENT — aggressive aerial / aggressive grounded / step out /
 *      defensive, picked with a single four-way two-finger swipe, which
 *      also commits and advances immediately. Skipped for the two
 *      defensive shots, exactly like the web.
 *   3. TIMING — the same rhythm minigame as the web: 5 pulses spaced by
 *      BattingSystem.computeTimingIntervalMs(speedKmh), one tap aimed
 *      at the 5th, scored by BattingSystem.computeTimingTier against the
 *      THEORETICAL schedule (not each pulse's actual fire time — see
 *      the web's BattingShotScreen for why a fixed target beats a
 *      drift-corrected one).
 *   4. RESULT — timing tier plus early/late feedback, then Continue.
 *      Feedback is deliberate: without it a player can't learn the
 *      rhythm. (No web equivalent — the web shows the outcome directly.)
 *
 * NO BACK ONCE THIS SCREEN IS SHOWING. By the time BattingScreen
 * appears, footwork is already committed and the ball already revealed
 * (see FLOW above), so there is nothing left to safely back out of:
 * going back to re-pick footwork with knowledge of the ball would
 * defeat the whole point of a blind commit. The one exception is INTENT
 * → SHOT (same delivery, no new information, so nothing is lost by
 * re-picking the shot) — a purely local step change, not a callback out
 * of this screen. Leaving a ball in progress entirely (the system back
 * button) is the CALLER's job — see MatchScreen.kt's BackHandler around
 * showBattingScreen — which is why this screen takes no onBack of its
 * own.
 *
 * GESTURE IMPLEMENTATION (see GESTURE_REDESIGN.md section 3 and section
 * 1's shared vocabulary — single-finger tap commits, two-finger swipe
 * sets/cycles a value):
 * - FOOTWORK lives one level up now — see MatchScreen.kt's
 *   FaceNextBallControl, which merges "start the next ball" and "pick
 *   footwork" into the single control the web plan called "Next ball"
 *   (tap vs double-tap-and-hold) instead of this screen showing its own
 *   separate footwork step first, as an earlier pass did.
 * - SHOT SELECTION mirrors PitchingScreen's length-drag mechanically —
 *   a single active pointer (see that file's "WHY THIS ISN'T LITERALLY
 *   TWO SIMULTANEOUS POINTERS" for the full TalkBack-passthrough
 *   reasoning, which applies identically here) tracked via
 *   detectVerticalDragGesture below, mapping net vertical movement
 *   across the gesture Box's own measured height to a 0..1 fraction fed
 *   straight into BattingSystem.classifyShot (the already-ported,
 *   15-equal-band function this exact gesture was designed for). A
 *   continuous tone tracks the live position; a spoken announcement
 *   fires only when the drag crosses into a new shot's band — never on
 *   every move event. Unlike PitchingScreen's length drag (which is one
 *   axis among several still waiting on a single shared Bowl tap), THIS
 *   Box has only one gesture role and nothing else to set on this step,
 *   so lifting the fingers both COMMITS the hovered shot AND advances
 *   the step immediately — there is no separate confirm tap here.
 * - INTENT is a single four-way discrete swipe, structurally identical
 *   to PitchingScreen's discrete angle/line/variation swipes: ONE
 *   decision per touch (detectFourWayGesture below), re-evaluated on
 *   every event until AXIS_LOCK_THRESHOLD_DP is cleared, with the same
 *   decision re-run at the release event if the touch is still
 *   undecided by then, so a fast flick is never silently dropped — see
 *   PitchingScreen's "WHY GESTURES WERE STILL INCONSISTENT AFTER THE
 *   FIRST FIX" for why that release-time fallback matters.
 *   BattingSystem.classifyIntentDirection (already ported, sharing its
 *   sign convention with this gesture's raw dx/dy) is the single source
 *   of truth for which of the four directions a given swipe resolves
 *   to. Like shot selection, the swipe itself both sets AND commits,
 *   firing onSelected and advancing the step immediately.
 *
 * TALKBACK SPECIFICS (all UNTESTED on a device — see UI_NOTES.md,
 * whose "Known issues / needs a real device" section this inherits
 * for the shot/intent gesture areas exactly as it already applies to
 * PitchingScreen's):
 * - The timing surface is a single full-screen node that is the ONLY
 *   focusable element on that step, so TalkBack's focus lands on it and
 *   a double-tap anywhere on screen activates it. Raw touch events
 *   don't reach Compose pointer handlers under touch exploration; the
 *   accessibility click action is what a double-tap delivers, so this
 *   uses `clickable`, not `pointerInput`.
 * - Its spoken label is deliberately short ("Timing") and the actual
 *   instruction rides on the click label, so TalkBack finishes speaking
 *   quickly. When touch exploration is on, the lead-in before the first
 *   pulse is also longer (BAT_LEAD_IN_MS_SCREEN_READER vs the web's
 *   550ms) so speech isn't still running over the first buzz. This is
 *   a deliberate deviation from the web and needs tuning on a device.
 * - The visible "Buzz n of 5" text is decorative: its semantics are
 *   cleared so it never becomes a second focus stop or a live region
 *   talking over the rhythm.
 *
 * EVERY PULSE is a buzz AND an audible tick from the sound engine, with
 * the FINAL pulse accented (higher and louder) so the beat to swing on
 * can be found by ear — the web does the same. The buzzes themselves
 * are all identical. The buzz is SoundEngine.vibratePulse() — a direct
 * VibrationEffect call, not Compose's
 * HapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress),
 * which an earlier version of this screen used. See vibratePulse's own
 * doc comment for why: HapticFeedbackType.LongPress dispatches to a
 * per-OEM SEMANTIC "a long press was recognized" system haptic rather
 * than a raw timed buzz, which lines up with reports of the rhythm
 * feeling laggy and inconsistent specifically on some cheaper-motor
 * devices — exactly the kind of device where a rhythm-timing cue is
 * most sensitive to any extra, unpredictable latency.
 *
 * KNOWN V1 SIMPLIFICATIONS (not permanent design decisions):
 * - BAT_INPUT_LATENCY_COMPENSATION_MS is 0 — touch-to-click latency
 *   under TalkBack, and audio output latency for the tick, are both
 *   unmeasured. Calibrate on a real device once measured. Also covers
 *   the still-not-widened Perfect timing window (section 5.1) — see the
 *   STATUS paragraph above.
 * - The shot/intent gesture areas are written to the best of the
 *   writer's understanding of TalkBack's raw-pointer passthrough
 *   behavior (same mechanism PitchingScreen relies on, already reasoned
 *   through there) but have not themselves been heard by an actual
 *   screen reader on an actual device yet.
 */

private enum class BatStep { SHOT, INTENT, TIMING, RESULT }

// Five pulses, tap on the fifth — same as the web.
private const val BAT_PULSE_COUNT = 5

// Delay before the first pulse. 550ms matches the web; the longer value
// is used when TalkBack-style touch exploration is on, so its speech
// has time to finish before the rhythm starts.
private const val BAT_LEAD_IN_MS_STANDARD = 550L
private const val BAT_LEAD_IN_MS_SCREEN_READER = 1400L

// How long after the 5th pulse to wait for a swing before scoring it
// as a miss, in inter-pulse intervals.
private const val BAT_NO_SWING_GRACE_INTERVALS = 2.0

// Subtracted from the measured tap time. Zero until measured on a
// device — see the doc comment above.
private const val BAT_INPUT_LATENCY_COMPENSATION_MS = 0.0

// Total distance (in dp, converted to px at gesture-detection time) the
// intent gesture's pointer must travel from touch-down before ONE
// decision is made for the whole touch — see detectFourWayGesture and
// the class doc comment's "GESTURE IMPLEMENTATION". Matches
// PitchingScreen's own AXIS_LOCK_THRESHOLD_DP value for a consistent
// swipe feel across both gesture surfaces.
private val AXIS_LOCK_THRESHOLD_DP = 24.dp

/**
 * What the batter is shown when the ball is revealed: the delivery itself
 * and a sentence on how the AI captain re-set its field for it (empty if
 * nobody moved).
 */
data class DeliveryReveal(val bowling: ResolvedBowlingDecision, val fieldNote: String)

private data class BatSwing(
    val tier: BowlingQualityTier,
    val deltaFraction: Double,
    // Negative = early, positive = late, in ms relative to the 5th pulse.
    val signedErrorMs: Double,
    val missed: Boolean
)

private fun footworkLabel(footwork: FootworkType): String =
    if (footwork == FootworkType.FRONT_FOOT) "Front foot" else "Back foot"

/** One spoken line describing the revealed delivery — the same facts the web's summary line gives. */
private fun deliverySummary(delivery: ResolvedBowlingDecision): String {
    val length = BowlingSystem.lengthLabel(delivery.actualLength)
    val line = BowlingSystem.LINE_OPTIONS.firstOrNull { it.value == delivery.line }?.label
    val variation = BowlingSystem.getVariationOptions(delivery.bowlingStyle).firstOrNull { it.value == delivery.variation }?.label
    val angle = BowlingSystem.ANGLE_OPTIONS.firstOrNull { it.value == delivery.angle }?.label
    return listOfNotNull(length, line, variation, angle, "${delivery.speedKmh} km/h").joinToString(", ")
}

private fun swingFeedback(swing: BatSwing): String = when {
    swing.missed -> "You didn't swing in time."
    swing.tier == BowlingQualityTier.PERFECT -> "Right on the beat."
    swing.signedErrorMs < 0 -> "You swung early."
    else -> "You swung late."
}

/**
 * @param batsman the striker, for display only.
 * @param footwork already chosen by the caller (MatchScreen.kt's
 *   FaceNextBallControl) before this screen was ever shown — see the
 *   class doc comment's FLOW.
 * @param generateDelivery called exactly once — via `remember` below, the
 *   moment this screen enters composition, which by construction is
 *   already AFTER the footwork commit — to produce the delivery the
 *   batter then faces and the field the AI set for it
 *   (MatchSimulation.prepareAiDelivery today). The caller owns how, and
 *   is responsible for putting that field into the match state.
 * @param onBallPlayed the delivery that was revealed plus the batter's
 *   fully-resolved decision, ready to pass straight into
 *   MatchSimulation.simulateOneBall as its preset decisions.
 */
@Composable
fun BattingScreen(
    batsman: Player,
    footwork: FootworkType,
    generateDelivery: () -> DeliveryReveal,
    onBallPlayed: (ResolvedBowlingDecision, BattingDecision) -> Unit
) {
    val services = LocalGameServices.current
    var step by remember { mutableStateOf(BatStep.SHOT) }
    // Computed exactly once for this screen's lifetime, right as it
    // enters composition — see the @param doc above and the class doc
    // comment's FLOW for why that already satisfies "called exactly
    // once, after the footwork commit".
    val reveal = remember { generateDelivery() }
    val delivery = reveal.bowling
    var shot by remember { mutableStateOf(NamedShot.FORWARD_DEFENSE) }
    var intent by remember { mutableStateOf<IntentDirection?>(null) }
    var swing by remember { mutableStateOf<BatSwing?>(null) }

    // A side effect (speaking aloud) belongs in an effect, not in the
    // `remember` calculation above which runs during composition itself.
    LaunchedEffect(reveal) {
        services?.announceSpoken("Delivery: ${deliverySummary(reveal.bowling)}. ${reveal.fieldNote}")
    }

    when (step) {
        BatStep.SHOT -> BatShotStep(
            deliverySummary = deliverySummary(delivery),
            fieldNote = reveal.fieldNote,
            onSelected = { chosen ->
                shot = chosen
                if (BattingSystem.isDefensiveShot(chosen)) {
                    intent = null
                    step = BatStep.TIMING
                } else {
                    step = BatStep.INTENT
                }
            }
        )
        BatStep.INTENT -> BatIntentStep(
            shotName = BattingSystem.shotLabel(shot),
            onSelected = { chosen ->
                intent = chosen
                step = BatStep.TIMING
            },
            onBack = { step = BatStep.SHOT }
        )
        BatStep.TIMING -> BatTimingStep(
            speedKmh = delivery.speedKmh,
            onSwung = { result ->
                swing = result
                step = BatStep.RESULT
            }
        )
        BatStep.RESULT -> {
            val finalSwing = swing
            if (finalSwing != null) {
                BatResultStep(
                    footwork = footwork,
                    shot = shot,
                    intent = intent,
                    swing = finalSwing,
                    onContinue = {
                        onBallPlayed(
                            delivery,
                            BattingDecision(
                                footwork = footwork,
                                shot = shot,
                                intent = intent,
                                timingTier = finalSwing.tier,
                                timingDeltaFraction = finalSwing.deltaFraction
                            )
                        )
                    }
                )
            }
        }
    }
}

/**
 * Continuous single-axis (vertical) drag tracker used by the shot-
 * selection gesture area — see the class doc comment's "GESTURE
 * IMPLEMENTATION". Deliberately simpler than PitchingScreen's
 * detectAimGesture: this Box has only ONE gesture role (there's no
 * discrete left/right/up meaning on this screen), so there's no
 * axis-lock decision to make — every touch immediately begins tracking
 * net vertical movement from wherever it started, clamped to the
 * gesture Box's own measured height.
 *
 * Tracks a SINGLE ACTIVE POINTER, not two simultaneous ones, for the
 * same reason PitchingScreen's detectAimGesture does — see that file's
 * "WHY THIS ISN'T LITERALLY TWO SIMULTANEOUS POINTERS" doc comment for
 * the full TalkBack-passthrough explanation, which applies identically
 * here. The active pointer is looked up by id regardless of `pressed`,
 * so the release event's true final position always reaches onDragEnd
 * rather than being silently dropped.
 */
private suspend fun PointerInputScope.detectVerticalDragGesture(
    onDragStart: () -> Unit,
    onDrag: (fraction: Float) -> Unit,
    onDragEnd: (fraction: Float) -> Unit
) {
    val gestureHeightPx = size.height.toFloat()
    if (gestureHeightPx <= 0f) return

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val pointerId = down.id
        val originY = down.position.y
        onDragStart()

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
            if (change == null) return@awaitEachGesture
            change.consume()
            val fraction = ((change.position.y - originY) / gestureHeightPx).coerceIn(0f, 1f)
            if (!change.pressed) {
                onDragEnd(fraction)
                return@awaitEachGesture
            }
            onDrag(fraction)
        }
    }
}

/**
 * Discrete four-way swipe tracker used by the intent gesture area — see
 * the class doc comment's "GESTURE IMPLEMENTATION" and
 * GESTURE_REDESIGN.md section 3's intent table. Structurally the same
 * one-decision-per-touch approach as PitchingScreen's detectAimGesture:
 * total displacement from the touch's start is re-checked on every
 * event until AXIS_LOCK_THRESHOLD_DP is cleared, and that SAME decision
 * runs again at the release event if the touch is still undecided by
 * then, so a fast flick that only crosses the threshold in the gap
 * before lift isn't silently dropped — see PitchingScreen's "WHY
 * GESTURES WERE STILL INCONSISTENT AFTER THE FIRST FIX" for the full
 * reasoning behind that fallback.
 *
 * BattingSystem.classifyIntentDirection is the single source of truth
 * for which of the four directions a given dx/dy resolves to, so the
 * sign convention here (screen coordinates: dy negative = up) is never
 * duplicated or re-decided locally.
 */
private suspend fun PointerInputScope.detectFourWayGesture(
    onDirection: (IntentDirection) -> Unit
) {
    val axisLockThresholdPx = AXIS_LOCK_THRESHOLD_DP.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val pointerId = down.id
        val gestureStart = down.position
        var decided = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
            if (change == null) return@awaitEachGesture
            change.consume()
            val position = change.position
            val dxTotal = (position.x - gestureStart.x).toDouble()
            val dyTotal = (position.y - gestureStart.y).toDouble()
            val totalDistance = sqrt(dxTotal * dxTotal + dyTotal * dyTotal)

            if (!change.pressed) {
                if (!decided && totalDistance >= axisLockThresholdPx) {
                    onDirection(BattingSystem.classifyIntentDirection(dxTotal, dyTotal))
                }
                return@awaitEachGesture
            }

            if (!decided && totalDistance >= axisLockThresholdPx) {
                decided = true
                onDirection(BattingSystem.classifyIntentDirection(dxTotal, dyTotal))
            }
        }
    }
}

@Composable
private fun BatShotStep(deliverySummary: String, fieldNote: String, onSelected: (NamedShot) -> Unit) {
    val services = LocalGameServices.current
    val currentServices by rememberUpdatedState(services)
    val currentOnSelected by rememberUpdatedState(onSelected)

    // Live text while dragging, for a sighted player watching the screen —
    // TalkBack users get the same information from the zone-crossing
    // announcements instead. There is no separate "committed" state:
    // releasing the drag commits and advances immediately (see the class
    // doc comment's "GESTURE IMPLEMENTATION"), so this is purely transient.
    var liveShot by remember { mutableStateOf<NamedShot?>(null) }
    // Only used to detect a genuine zone CROSSING while dragging, so the
    // spoken announcement never fires on every touch-move event.
    var lastAnnouncedShot by remember { mutableStateOf<NamedShot?>(null) }
    // Guards against a stray extra event after the shot has already been
    // committed and the step has started to advance away from this one.
    var committed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // The delivery is the single most important thing on this step, so
        // it is the heading (read first) AND a polite live region in case
        // TalkBack doesn't move focus to it when the step appears.
        Text(
            text = "Delivery: $deliverySummary",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        if (fieldNote.isNotEmpty()) {
            // How the AI captain just re-set its field for this ball. Empty
            // (and so absent) when nobody moved.
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Field changes: $fieldNote",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose your shot",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            "Two-finger swipe up or down. Releasing picks the shot.",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = liveShot?.let { "Shot: ${BattingSystem.shotLabel(it)}" } ?: "Shot: swipe to choose",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGesture(
                        onDragStart = { currentServices?.sound?.startAimTone() },
                        onDrag = { fraction ->
                            currentServices?.sound?.updateAimTone(fraction)
                            val hovered = BattingSystem.classifyShot(fraction.toDouble())
                            liveShot = hovered
                            if (hovered != lastAnnouncedShot) {
                                lastAnnouncedShot = hovered
                                currentServices?.announceSpoken(BattingSystem.shotLabel(hovered))
                            }
                        },
                        onDragEnd = { fraction ->
                            currentServices?.sound?.stopAimTone()
                            val chosen = BattingSystem.classifyShot(fraction.toDouble())
                            liveShot = chosen
                            if (!committed) {
                                committed = true
                                currentServices?.announceSpoken("Shot: ${BattingSystem.shotLabel(chosen)}")
                                currentOnSelected(chosen)
                            }
                        }
                    )
                }
                .semantics {
                    contentDescription = "Shot selection gesture area. Two-finger swipe up or down."
                }
        )
    }
}

@Composable
private fun BatIntentStep(shotName: String, onSelected: (IntentDirection) -> Unit, onBack: () -> Unit) {
    val services = LocalGameServices.current
    val currentServices by rememberUpdatedState(services)
    val currentOnSelected by rememberUpdatedState(onSelected)
    // Guards against a stray extra event after the direction has
    // already resolved (e.g. the tail of the same gesture) firing a
    // second time and double-advancing the step.
    var fired by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Choose your intent",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Shot: $shotName", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Two-finger swipe: up = aggressive (aerial), down = defensive, " +
                "left = aggressive (grounded), right = step out.",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectFourWayGesture { direction ->
                        if (!fired) {
                            fired = true
                            currentServices?.announceSpoken(BattingSystem.intentLabel(direction))
                            currentOnSelected(direction)
                        }
                    }
                }
                .semantics {
                    contentDescription = "Intent gesture area. Two-finger swipe up, down, left, or right."
                }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun BatTimingStep(speedKmh: Int, onSwung: (BatSwing) -> Unit) {
    val context = LocalContext.current
    val services = LocalGameServices.current
    val currentOnSwung by rememberUpdatedState(onSwung)

    val screenReaderActive = remember {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        manager?.isTouchExplorationEnabled == true
    }
    val intervalMs = remember(speedKmh) { BattingSystem.computeTimingIntervalMs(speedKmh) }
    val leadInMs = if (screenReaderActive) BAT_LEAD_IN_MS_SCREEN_READER else BAT_LEAD_IN_MS_STANDARD
    // Milliseconds after the timer starts at which the 5th pulse lands.
    val expectedMs = leadInMs + (BAT_PULSE_COUNT - 1) * intervalMs

    var startMs by remember { mutableStateOf(0L) }
    var pulsesFired by remember { mutableStateOf(0) }
    var resolved by remember { mutableStateOf(false) }

    fun resolve(actualMs: Double, missed: Boolean) {
        if (resolved) return
        resolved = true
        val result = BattingSystem.computeTimingTier(actualMs, expectedMs, intervalMs)
        currentOnSwung(BatSwing(result.tier, result.deltaFraction, actualMs - expectedMs, missed))
    }

    LaunchedEffect(intervalMs) {
        val start = SystemClock.elapsedRealtime()
        startMs = start
        for (i in 0 until BAT_PULSE_COUNT) {
            // Each pulse is scheduled against the start time, not chained
            // off the previous delay, so timer drift never accumulates.
            val target = start + leadInMs + (i * intervalMs).toLong()
            val wait = target - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
            if (resolved) return@LaunchedEffect
            pulsesFired = i + 1
            // A direct, low-latency buzz rather than Compose's semantic
            // HapticFeedbackType.LongPress -- see the class doc comment's
            // paragraph on EVERY PULSE for why. vibratePulse() checks the
            // vibration setting itself. The tick is gated by Sound
            // effects in the engine. The final pulse's tick is accented.
            services?.sound?.vibratePulse()
            services?.sound?.playTimingTick(accent = i == BAT_PULSE_COUNT - 1)
        }
        // Never swung: wait a grace window, then score it as a clear miss
        // (two intervals late is well past the Very Bad threshold).
        delay((intervalMs * BAT_NO_SWING_GRACE_INTERVALS).toLong())
        resolve(actualMs = expectedMs + intervalMs * 2, missed = true)
    }

    fun handleSwing() {
        if (startMs == 0L) return
        val actualMs = (SystemClock.elapsedRealtime() - startMs).toDouble() - BAT_INPUT_LATENCY_COMPENSATION_MS
        resolve(actualMs = actualMs, missed = false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClickLabel = "swing on the fifth buzz",
                role = Role.Button,
                onClick = { handleSwing() }
            )
            .semantics { contentDescription = "Timing" },
        contentAlignment = Alignment.Center
    ) {
        // Purely visual — cleared semantics keep this from becoming a
        // second focus stop or a live region talking over the rhythm.
        Column(
            modifier = Modifier.clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (pulsesFired == 0) "Get ready\u2026" else "Buzz $pulsesFired of $BAT_PULSE_COUNT",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Tap anywhere to swing", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BatResultStep(
    footwork: FootworkType,
    shot: NamedShot,
    intent: IntentDirection?,
    swing: BatSwing,
    onContinue: () -> Unit
) {
    val tierLabel = BowlingSystem.qualityTierLabel(swing.tier)
    val played = buildString {
        append(footworkLabel(footwork))
        append(", ")
        append(BattingSystem.shotLabel(shot))
        if (intent != null) {
            append(", ")
            append(BattingSystem.intentLabel(intent))
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "$tierLabel timing",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(swingFeedback(swing))
        Spacer(modifier = Modifier.height(8.dp))
        Text(played, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}
