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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The second of the three custom gesture surfaces (pitching/batting/
 * fielding — see UI_NOTES.md). Like PitchingScreen, this is a FRESH
 * TalkBack-native design, not a port of the web app's
 * BattingShotScreen (whose continuous press-and-drag shot/intent
 * gestures have no reliable non-visual equivalent on Android).
 *
 * FLOW — FOOTWORK is chosen one level up, in MatchScreen.kt's "Face next
 * ball" control (tap = front foot, press-and-hold = back foot), BEFORE
 * this screen is shown. That is also where `generateDelivery` runs (via
 * `remember` below, the moment this screen enters composition), so the
 * web plan's blind-commit property (the ball is only revealed once
 * footwork is locked in) is preserved:
 *   1. SHOT — the delivery is revealed (ACTUAL length after any bowling
 *      mis-execution, line, variation, angle, speed; never the bowler's
 *      quality tier or intended length), AND any changes the AI captain
 *      has just made to its field for this delivery, then one of the 15
 *      named shots is picked by the HEIGHT of a touch on a full-screen
 *      gesture area (the touch-down point already selects a shot;
 *      sliding up or down changes it). Releasing COMMITS the shot and
 *      advances immediately.
 *   2. INTENT — aggressive aerial / aggressive grounded / step out /
 *      defensive, picked with a single four-way swipe, which also
 *      commits and advances immediately. Skipped for the two defensive
 *      shots, exactly like the web.
 *   3. TIMING — the same rhythm minigame as the web: 5 pulses spaced by
 *      BattingSystem.computeTimingIntervalMs(speedKmh), one tap aimed at
 *      the 5th, scored by BattingSystem.computeTimingTier against the
 *      THEORETICAL schedule (not each pulse's actual fire time).
 *   4. HAND-OFF — there is deliberately NO result/Continue step after the
 *      swing. The moment the swing is scored, onBallPlayed is called with
 *      the delivery, the batter's decision and a short early/late note
 *      (see swingFeedback). The caller simulates the ball and the note
 *      becomes part of the last-ball summary (MatchLines.ballSummary),
 *      which already carries the shot played and the timing tier.
 *
 * NO BACK ONCE THIS SCREEN IS SHOWING. Footwork is already committed and
 * the ball already revealed, so there is nothing left to safely back out
 * of. The one exception is INTENT -> SHOT (same delivery, no new
 * information) — a purely local step change. Leaving a ball in progress
 * entirely (the system back button) is the CALLER's job — see
 * MatchScreen.kt's BackHandler around showBattingScreen.
 *
 * GESTURE IMPLEMENTATION:
 * - SHOT SELECTION uses an ABSOLUTE vertical position, not movement
 *   relative to where the touch began. The gesture area fills the whole
 *   screen (the delivery text is drawn under it and takes no touches),
 *   and the finger's height as a 0..1 fraction of that area is fed
 *   straight into BattingSystem.classifyShot (15 equal bands). So a touch
 *   that LANDS in the Cover Drive band is already Cover Drive. Sliding up
 *   or down moves to the neighbouring shots, in either direction. A
 *   continuous tone tracks the live position; the shot the finger is on
 *   is spoken SHOT_LANDING_ANNOUNCE_DELAY_MS after the touch lands (the
 *   screen reader interrupts speech at the start of a touch, so anything
 *   spoken in that first instant is lost -- moves are silent until then),
 *   and after that a new shot is spoken whenever the touch crosses into
 *   its band — never on every move event. A quick tap that is ignored as a stray still
 *   says which shot it touched.
 * - WHAT COUNTS AS A SELECTION (see detectVerticalDragGesture): the shot
 *   is committed only when ALL fingers are up, at the height the touch
 *   last reached, and only if the touch was deliberate — it slid at
 *   least SHOT_TAP_SLOP_DP or was held at least SHOT_MIN_HOLD_MS. A
 *   brief, still touch (a stray tap, a palm, the tail of a previous
 *   gesture) is ignored, and so is a touch the system cancels. Lifting
 *   one finger of a two-finger touch before the other does NOT commit:
 *   the remaining finger simply takes over. These rules exist because
 *   shots used to be picked "on their own": the first finger up
 *   committed wherever it happened to be, and any touch at all counted.
 * - WHAT THE SURFACE SAYS. Being full-screen and on top, the gesture
 *   surface is what touch exploration lands on wherever the finger is,
 *   so it carries the delivery details (and field changes) as its own
 *   spoken description; the visible texts are cleared from the
 *   accessibility tree so nothing is read twice. There is deliberately no
 *   written gesture instruction on this step — the delivery is what the
 *   batter needs in order to choose a shot.
 * - The shot tone can never outlive its touch: it is stopped on release,
 *   on cancel, when this step leaves composition, and (in SoundEngine) if
 *   the app goes to the background or nothing has touched it for a while.
 * - INTENT is a single four-way discrete swipe: ONE decision per touch
 *   (detectFourWayGesture), re-evaluated on every event until
 *   AXIS_LOCK_THRESHOLD_DP is cleared, with the same decision re-run at
 *   the release event if the touch is still undecided by then, so a fast
 *   flick is never silently dropped. BattingSystem.classifyIntentDirection
 *   is the single source of truth for which of the four directions a
 *   given swipe resolves to.
 *
 * TALKBACK SPECIFICS (all UNTESTED on a device — see UI_NOTES.md):
 * - The timing surface is a single full-screen node that is the ONLY
 *   focusable element on that step, so TalkBack's focus lands on it and a
 *   double-tap anywhere activates it. It uses `clickable`, not
 *   `pointerInput`, because the accessibility click action is what a
 *   double-tap delivers.
 * - Its spoken label is deliberately short ("Timing"), and when touch
 *   exploration is on, the lead-in before the first pulse is longer
 *   (BAT_LEAD_IN_MS_SCREEN_READER vs the web's 550ms) so speech isn't
 *   still running over the first buzz. Needs tuning on a device.
 * - The visible "Buzz n of 5" text is decorative: its semantics are
 *   cleared so it never becomes a second focus stop.
 *
 * EVERY PULSE is a buzz AND an audible tick from the sound engine, with
 * the FINAL pulse accented so the beat to swing on can be found by ear.
 * The buzz is SoundEngine.vibratePulse() — a direct VibrationEffect call,
 * not Compose's HapticFeedbackType.LongPress, which dispatches to a
 * per-OEM semantic haptic with unpredictable latency (see vibratePulse's
 * own doc comment).
 *
 * KNOWN V1 SIMPLIFICATIONS (not permanent design decisions):
 * - BAT_INPUT_LATENCY_COMPENSATION_MS is 0 — touch-to-click latency under
 *   TalkBack, and audio output latency for the tick, are unmeasured. Also
 *   covers the still-not-widened Perfect timing window (GESTURE_REDESIGN.md
 *   section 5.1), which needs a real latency measurement first.
 * - The shot/intent gesture areas have not been heard by an actual screen
 *   reader on an actual device in this form yet.
 */

private enum class BatStep { SHOT, INTENT, TIMING }

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
// decision is made for the whole touch. Matches PitchingScreen's own
// AXIS_LOCK_THRESHOLD_DP value for a consistent swipe feel.
private val AXIS_LOCK_THRESHOLD_DP = 24.dp

// A shot-selection touch only counts as deliberate if it slid at least
// this far (dp) ...
private val SHOT_TAP_SLOP_DP = 10.dp

// ... or was held at least this long (ms) before the fingers lifted. A
// shorter, stiller touch is treated as a stray tap and ignored.
private const val SHOT_MIN_HOLD_MS = 150L

// How long after a touch lands before the shot it landed on is spoken. With
// touch exploration on, a touch on the surface makes the screen reader read
// the surface's own description (the delivery) at the same instant, which
// swallowed an announcement made at touch-down. Waiting this long lets that
// settle, so a touch that goes straight to a shot still hears its name. A
// touch that slides on before the delay hears the new shot at once instead.
private const val SHOT_LANDING_ANNOUNCE_DELAY_MS = 250L

// When every finger seems to have lifted, how long to wait for one to come
// straight back down before treating the touch as really over. Under a
// screen reader, the system re-organises a two-finger touch part-way
// through (for example when the fingers drift slightly apart) by ending
// the touch it was passing on and immediately starting another. Without
// this wait that moment looked like a release and played a shot the player
// never chose.
private const val SHOT_RELEASE_GRACE_MS = 150L

/**
 * What the batter is shown when the ball is revealed: the delivery itself
 * and a sentence on how the AI captain re-set its field for it (empty if
 * nobody moved).
 */
data class DeliveryReveal(val bowling: ResolvedBowlingDecision, val fieldNote: String, val freeHit: Boolean = false)

private data class BatSwing(
    val tier: BowlingQualityTier,
    val deltaFraction: Double,
    // Negative = early, positive = late, in ms relative to the 5th pulse.
    val signedErrorMs: Double,
    val missed: Boolean
)

/** One spoken line describing the revealed delivery — the same facts the web's summary line gives. */
private fun deliverySummary(delivery: ResolvedBowlingDecision): String {
    val length = BowlingSystem.lengthLabel(delivery.actualLength)
    val line = BowlingSystem.LINE_OPTIONS.firstOrNull { it.value == delivery.line }?.label
    val variation = BowlingSystem.getVariationOptions(delivery.bowlingStyle).firstOrNull { it.value == delivery.variation }?.label
    val angle = BowlingSystem.ANGLE_OPTIONS.firstOrNull { it.value == delivery.angle }?.label
    return listOfNotNull(length, line, variation, angle, "${delivery.speedKmh} km/h").joinToString(", ")
}

/**
 * The early/late note that goes into the last-ball summary, or null when
 * there is nothing to add (a Perfect swing — the summary's own
 * "Timing: Perfect" already says so).
 */
private fun swingFeedback(swing: BatSwing): String? = when {
    swing.missed -> "You didn't swing in time."
    swing.tier == BowlingQualityTier.PERFECT -> null
    swing.signedErrorMs < 0 -> "You swung early."
    else -> "You swung late."
}

/**
 * @param batsman the striker, for display only.
 * @param footwork already chosen by the caller (MatchScreen.kt's
 *   FaceNextBallControl) before this screen was ever shown.
 * @param generateDelivery called exactly once — via `remember` below, the
 *   moment this screen enters composition, which by construction is
 *   already AFTER the footwork commit — to produce the delivery the
 *   batter then faces and the field the AI set for it
 *   (MatchSimulation.prepareAiDelivery today). The caller owns how, and
 *   is responsible for putting that field into the match state.
 * @param onBallPlayed called the moment the swing is scored, with the
 *   delivery that was revealed, the batter's fully-resolved decision
 *   (ready to pass straight into MatchSimulation.simulateOneBall as its
 *   preset decisions) and the early/late note to add to the last-ball
 *   summary (null when there is nothing to add).
 */
@Composable
fun BattingScreen(
    batsman: Player,
    footwork: FootworkType,
    generateDelivery: () -> DeliveryReveal,
    onBallPlayed: (ResolvedBowlingDecision, BattingDecision, String?) -> Unit
) {
    val services = LocalGameServices.current
    var step by remember { mutableStateOf(BatStep.SHOT) }
    // Computed exactly once for this screen's lifetime, right as it
    // enters composition.
    val reveal = remember { generateDelivery() }
    val delivery = reveal.bowling
    var shot by remember { mutableStateOf(NamedShot.FORWARD_DEFENSE) }
    var intent by remember { mutableStateOf<IntentDirection?>(null) }

    // A side effect (speaking aloud) belongs in an effect, not in the
    // `remember` calculation above which runs during composition itself.
    // What the umpire has already called, said before the delivery itself: a no-ball (with
    // the siren, so the batter can play it knowing it cannot get him out) and/or a free hit.
    val noBallCalled = reveal.bowling.calledNoBall || reveal.bowling.forcedNoBall
    val callout = (if (noBallCalled) "No ball! " else "") + (if (reveal.freeHit) "Free hit! " else "")

    LaunchedEffect(reveal) {
        if (noBallCalled) services?.sound?.playNoBallSiren()
        services?.announceSpoken(callout + "Delivery: ${deliverySummary(reveal.bowling)}. ${reveal.fieldNote}")
    }

    when (step) {
        BatStep.SHOT -> BatShotStep(
            deliverySummary = deliverySummary(delivery),
            fieldNote = reveal.fieldNote,
            callout = callout,
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
                onBallPlayed(
                    delivery,
                    BattingDecision(
                        footwork = footwork,
                        shot = shot,
                        intent = intent,
                        timingTier = result.tier,
                        timingDeltaFraction = result.deltaFraction
                    ),
                    swingFeedback(result)
                )
            }
        )
    }
}

/**
 * Continuous single-axis (vertical) tracker used by the shot-selection
 * gesture area — see the class doc comment's "GESTURE IMPLEMENTATION".
 *
 * The reported fraction is the finger's ABSOLUTE height within the
 * gesture area (0 = top edge, 1 = bottom edge), NOT how far it has moved
 * since touch-down. That is what lets a touch which lands in the middle
 * of the shot list start on that shot immediately. [onDragStart]
 * therefore receives the touch-down fraction too, so the caller can
 * announce the starting shot.
 *
 * HOW A TOUCH ENDS (this is what stops shots being picked "on their
 * own"):
 * - It ends only when EVERY pointer is up. While the tracked finger is
 *   lifted but another is still down, the other one takes over, so a
 *   two-finger slide whose fingers don't lift at the same instant is not
 *   cut short by the first one.
 * - A release is not final until SHOT_RELEASE_GRACE_MS has passed with no
 *   finger coming back down. The screen reader's two-finger handling can
 *   end the touch it is passing on and start another in the same instant
 *   (that is how a shot used to be played mid-slide); a finger returning
 *   inside the grace period just continues the same touch.
 * - The height committed is the last one reached while a finger was
 *   still down (not the lift event's own position, which can jitter).
 * - A touch that never slid [SHOT_TAP_SLOP_DP] and was held less than
 *   [SHOT_MIN_HOLD_MS] is reported through [onDragCancel], not
 *   [onDragEnd]: a stray tap must not play a shot. So is a touch the
 *   system cancels (an event with no pointers left in it).
 * Every [onDragStart] is therefore matched by exactly one of
 * [onDragEnd] / [onDragCancel] unless the composable is torn down
 * mid-touch, which the caller covers with a DisposableEffect.
 *
 * The area's height is read per gesture (not once when the pointer-input
 * block starts), so a size change such as a rotation can't leave a stale
 * height behind, and a not-yet-measured area is simply ignored.
 *
 * Tracks one ACTIVE pointer at a time rather than requiring two
 * simultaneous ones, for the same reason PitchingScreen's
 * detectAimGesture does — see that file's "WHY THIS ISN'T LITERALLY TWO
 * SIMULTANEOUS POINTERS" doc comment.
 */
private suspend fun PointerInputScope.detectVerticalDragGesture(
    onDragStart: (fraction: Float) -> Unit,
    onDrag: (fraction: Float) -> Unit,
    onDragEnd: (fraction: Float) -> Unit,
    onDragCancel: () -> Unit
) {
    val slopPx = SHOT_TAP_SLOP_DP.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val gestureHeightPx = size.height.toFloat()
        if (gestureHeightPx <= 0f) return@awaitEachGesture

        fun fractionOf(y: Float): Float = (y / gestureHeightPx).coerceIn(0f, 1f)

        val startY = down.position.y
        val startTimeMs = down.uptimeMillis
        var activeId = down.id
        var lastFraction = fractionOf(startY)
        var moved = false
        onDragStart(lastFraction)

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.isEmpty()) {
                onDragCancel()
                return@awaitEachGesture
            }
            event.changes.forEach { it.consume() }
            val pressed = event.changes.filter { it.pressed }

            if (pressed.isEmpty()) {
                // Every finger is up -- or the system briefly ended the touch
                // while re-organising a two-finger gesture. Give a finger a
                // moment to come straight back down: if one does, this is the
                // same touch carrying on, not a release.
                val release = event.changes.firstOrNull { it.id == activeId } ?: event.changes.first()
                val heldMs = release.uptimeMillis - startTimeMs
                val resumed = withTimeoutOrNull(SHOT_RELEASE_GRACE_MS) {
                    awaitFirstDown(requireUnconsumed = false)
                }
                if (resumed != null) {
                    resumed.consume()
                    activeId = resumed.id
                    lastFraction = fractionOf(resumed.position.y)
                    if (!moved && abs(resumed.position.y - startY) >= slopPx) moved = true
                    onDrag(lastFraction)
                    continue
                }
                if (moved || heldMs >= SHOT_MIN_HOLD_MS) onDragEnd(lastFraction) else onDragCancel()
                return@awaitEachGesture
            }

            // Follow the active finger; if it has lifted but another is
            // still down, that one takes over.
            val tracked = pressed.firstOrNull { it.id == activeId } ?: pressed.first().also { activeId = it.id }
            val y = tracked.position.y
            lastFraction = fractionOf(y)
            if (!moved && abs(y - startY) >= slopPx) moved = true
            onDrag(lastFraction)
        }
    }
}

/**
 * Discrete four-way swipe tracker used by the intent gesture area. One
 * decision per touch: total displacement from the touch's start is
 * re-checked on every event until AXIS_LOCK_THRESHOLD_DP is cleared, and
 * that SAME decision runs again at the release event if the touch is
 * still undecided by then, so a fast flick that only crosses the
 * threshold in the gap before lift isn't silently dropped.
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
private fun BatShotStep(deliverySummary: String, fieldNote: String, callout: String, onSelected: (NamedShot) -> Unit) {
    val services = LocalGameServices.current
    val currentServices by rememberUpdatedState(services)
    val currentOnSelected by rememberUpdatedState(onSelected)

    // Live text while touching, for a sighted player watching the screen —
    // TalkBack users get the same information from the zone-crossing
    // announcements instead. Releasing commits and advances immediately,
    // so this is purely transient.
    var liveShot by remember { mutableStateOf<NamedShot?>(null) }
    // Only used to detect a genuine zone CROSSING while touching, so the
    // spoken announcement never fires on every touch-move event.
    var lastAnnouncedShot by remember { mutableStateOf<NamedShot?>(null) }
    // Guards against a stray extra event after the shot has already been
    // committed and the step has started to advance away from this one.
    var committed by remember { mutableStateOf(false) }

    // The shot tone must never outlive this step, whatever ended the
    // touch (a release, a cancel, or this step simply leaving composition
    // mid-touch). stopAimTone is safe to call when nothing is playing.
    DisposableEffect(Unit) {
        onDispose { currentServices?.sound?.stopAimTone() }
    }

    val scope = rememberCoroutineScope()
    // The pending "you landed on ..." announcement, see
    // SHOT_LANDING_ANNOUNCE_DELAY_MS.
    var landingJob by remember { mutableStateOf<Job?>(null) }
    // False for the first moments of every touch. Until the landing
    // announcement has been made, moving between shots stays silent: the
    // screen reader interrupts speech at the start of a touch, so anything
    // spoken in that first instant (including by the first move event) was
    // lost -- which is why the shot you went straight to was never heard
    // until you slid away and came back.
    var landingSettled by remember { mutableStateOf(false) }

    // Shared by touch-down and every move: updates the tone and the live
    // text, and (when speakNow) speaks the shot only if it differs from the
    // last one spoken. Touch-down passes speakNow = false and schedules the
    // announcement itself, after a short delay; a slide that crosses into
    // another shot before that delay speaks the new shot straight away and
    // cancels the pending one.
    fun reportHover(fraction: Float, speakNow: Boolean) {
        currentServices?.sound?.updateAimTone(fraction)
        val hovered = BattingSystem.classifyShot(fraction.toDouble())
        liveShot = hovered
        if (speakNow && landingSettled && hovered != lastAnnouncedShot) {
            lastAnnouncedShot = hovered
            landingJob?.cancel()
            currentServices?.announceSpoken(BattingSystem.shotLabel(hovered))
        }
    }

    // The gesture area is the WHOLE screen: the texts are drawn under the
    // surface and take no touches, so touches anywhere reach the gesture
    // surface declared last in this Box.
    Box(modifier = Modifier.fillMaxSize()) {
        // Visual only. The surface below is full-screen and on top, so it is
        // what touch exploration lands on; it speaks the delivery itself
        // (see its description), so these texts are cleared from the
        // accessibility tree rather than being read a second time.
        Column(modifier = Modifier.padding(24.dp).clearAndSetSemantics { }) {
            if (callout.isNotEmpty()) {
                Text(text = callout.trim(), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = "Delivery: $deliverySummary",
                style = MaterialTheme.typography.titleMedium
            )
            if (fieldNote.isNotEmpty()) {
                // How the AI captain just re-set its field for this ball. Empty
                // (and so absent) when nobody moved.
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Field changes: $fieldNote",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose your shot",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = liveShot?.let { "Shot: ${BattingSystem.shotLabel(it)}" } ?: "Shot: touch to choose",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGesture(
                        onDragStart = { fraction ->
                            lastAnnouncedShot = null
                            landingJob?.cancel()
                            landingSettled = false
                            currentServices?.sound?.startAimTone()
                            reportHover(fraction, speakNow = false)
                            // Speak the shot the finger is on shortly after
                            // landing (wherever it has got to by then), and
                            // only then start announcing crossings.
                            landingJob = scope.launch {
                                delay(SHOT_LANDING_ANNOUNCE_DELAY_MS)
                                landingSettled = true
                                val here = liveShot
                                if (here != null && here != lastAnnouncedShot) {
                                    lastAnnouncedShot = here
                                    currentServices?.announceSpoken(BattingSystem.shotLabel(here))
                                }
                            }
                        },
                        onDrag = { fraction -> reportHover(fraction, speakNow = true) },
                        onDragEnd = { fraction ->
                            landingJob?.cancel()
                            currentServices?.sound?.stopAimTone()
                            val chosen = BattingSystem.classifyShot(fraction.toDouble())
                            liveShot = chosen
                            if (!committed) {
                                committed = true
                                currentServices?.announceSpoken("Shot: ${BattingSystem.shotLabel(chosen)}")
                                currentOnSelected(chosen)
                            }
                        },
                        onDragCancel = {
                            // A quick tap or a cancelled touch: no shot is
                            // played and the tone is silenced -- but a quick
                            // tap on a shot still says which shot it was, so
                            // the screen can be explored by tapping.
                            landingJob?.cancel()
                            currentServices?.sound?.stopAimTone()
                            val touched = liveShot
                            if (touched != null && lastAnnouncedShot == null) {
                                currentServices?.announceSpoken(BattingSystem.shotLabel(touched))
                            }
                            liveShot = null
                            lastAnnouncedShot = null
                            landingSettled = false
                        }
                    )
                }
                .semantics {
                    contentDescription = buildString {
                        append(callout)
                        append("Delivery: ")
                        append(deliverySummary)
                        append(".")
                        if (fieldNote.isNotEmpty()) {
                            append(" Field changes: ")
                            append(fieldNote)
                        }
                        append(" Choose your shot.")
                    }
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
