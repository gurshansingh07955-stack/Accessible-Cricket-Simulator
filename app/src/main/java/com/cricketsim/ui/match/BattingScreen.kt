package com.cricketsim.ui.match

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay

/**
 * The second of the three custom gesture surfaces (pitching/batting/
 * fielding — see UI_NOTES.md). Like PitchingScreen, this is a FRESH
 * TalkBack-native design, not a port of the web app's
 * BattingShotScreen (whose continuous press-and-drag shot/intent
 * gestures have no reliable non-visual equivalent on Android).
 *
 * FLOW — mirrors the web app's ORDER of decisions, which matters
 * mechanically, not just for feel:
 *   1. FOOTWORK — front foot or back foot, committed BLIND. The ball
 *      hasn't been revealed yet; `generateDelivery` is only called once
 *      this choice is made. (Web: a tap or a hold on "Play Ball".)
 *   2. SHOT — the delivery is revealed (ACTUAL length after any
 *      bowling mis-execution, line, variation, angle, speed — same set
 *      the web's summary line reveals; never the bowler's quality tier
 *      or intended length), AND any changes the AI captain has just made
 *      to its field for this delivery ("Kohli moved from Mid-On to
 *      Long-On."), then one of the 15 named shots is picked from a plain
 *      single-swipe list. The field matters: it is part of what the
 *      shot is chosen against, so a batter who can't see it must be TOLD
 *      it, as the web does.
 *   3. INTENT — aggressive aerial / aggressive grounded / step out /
 *      defensive. Skipped for the two defensive shots, exactly like
 *      the web.
 *   4. TIMING — the same rhythm minigame as the web: 5 pulses spaced by
 *      BattingSystem.computeTimingIntervalMs(speedKmh), one tap aimed
 *      at the 5th, scored by BattingSystem.computeTimingTier against the
 *      THEORETICAL schedule (not each pulse's actual fire time — see
 *      the web's BattingShotScreen for why a fixed target beats a
 *      drift-corrected one).
 *   5. RESULT — timing tier plus early/late feedback, then Continue.
 *      Feedback is deliberate: without it a player can't learn the
 *      rhythm. (No web equivalent — the web shows the outcome directly.)
 *
 * NO BACK AFTER THE REVEAL. Back is offered on the footwork step (leave
 * without committing) and from intent to shot (same delivery, no new
 * information). It is NOT offered on the shot step or later: going
 * back to re-pick footwork with knowledge of the ball would defeat the
 * whole point of a blind commit.
 *
 * TALKBACK SPECIFICS (all UNTESTED on a device — see UI_NOTES.md):
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
 *   unmeasured. Calibrate on a real device once measured.
 */

private enum class BatStep { FOOTWORK, SHOT, INTENT, TIMING, RESULT }

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

private data class BatIntentChoice(val value: IntentDirection, val description: String)

// Order: the two aggressive options, the step-out, then defensive.
private val BAT_INTENT_CHOICES = listOf(
    BatIntentChoice(IntentDirection.UP, "Hit in the air."),
    BatIntentChoice(IntentDirection.LEFT, "Hit along the ground."),
    BatIntentChoice(IntentDirection.RIGHT, "Advance down the pitch."),
    BatIntentChoice(IntentDirection.DOWN, "Play safe and rotate the strike.")
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
 * @param generateDelivery called exactly once, AFTER the footwork commit,
 *   to produce the delivery the batter then faces and the field the AI set
 *   for it (MatchSimulation.prepareAiDelivery today). The caller owns how,
 *   and is responsible for putting that field into the match state.
 * @param onBallPlayed the delivery that was revealed plus the batter's
 *   fully-resolved decision, ready to pass straight into
 *   MatchSimulation.simulateOneBall as its preset decisions.
 */
@Composable
fun BattingScreen(
    batsman: Player,
    generateDelivery: () -> DeliveryReveal,
    onBallPlayed: (ResolvedBowlingDecision, BattingDecision) -> Unit,
    onBack: () -> Unit
) {
    val services = LocalGameServices.current
    var step by remember { mutableStateOf(BatStep.FOOTWORK) }
    var footwork by remember { mutableStateOf(FootworkType.FRONT_FOOT) }
    var delivery by remember { mutableStateOf<ResolvedBowlingDecision?>(null) }
    var fieldNote by remember { mutableStateOf("") }
    var shot by remember { mutableStateOf(NamedShot.FORWARD_DEFENSE) }
    var intent by remember { mutableStateOf<IntentDirection?>(null) }
    var swing by remember { mutableStateOf<BatSwing?>(null) }

    when (step) {
        BatStep.FOOTWORK -> BatFootworkStep(
            batsmanName = batsman.name,
            onSelected = { chosen ->
                val reveal = generateDelivery()
                footwork = chosen
                delivery = reveal.bowling
                fieldNote = reveal.fieldNote
                // For someone not using a screen reader, read the reveal aloud.
                services?.announceSpoken(
                    "Delivery: ${deliverySummary(reveal.bowling)}. ${reveal.fieldNote}"
                )
                step = BatStep.SHOT
            },
            onBack = onBack
        )
        BatStep.SHOT -> {
            val revealed = delivery
            if (revealed != null) {
                BatShotStep(
                    deliverySummary = deliverySummary(revealed),
                    fieldNote = fieldNote,
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
            }
        }
        BatStep.INTENT -> BatIntentStep(
            shotName = BattingSystem.shotLabel(shot),
            onSelected = { chosen ->
                intent = chosen
                step = BatStep.TIMING
            },
            onBack = { step = BatStep.SHOT }
        )
        BatStep.TIMING -> {
            val revealed = delivery
            if (revealed != null) {
                BatTimingStep(
                    speedKmh = revealed.speedKmh,
                    onSwung = { result ->
                        swing = result
                        step = BatStep.RESULT
                    }
                )
            }
        }
        BatStep.RESULT -> {
            val revealed = delivery
            val finalSwing = swing
            if (revealed != null && finalSwing != null) {
                BatResultStep(
                    footwork = footwork,
                    shot = shot,
                    intent = intent,
                    swing = finalSwing,
                    onContinue = {
                        onBallPlayed(
                            revealed,
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

@Composable
private fun BatFootworkStep(batsmanName: String, onSelected: (FootworkType) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Choose your footwork",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "$batsmanName is on strike. You commit before you see the ball, and you can't take it back.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        listOf(
            FootworkType.FRONT_FOOT to "Move forward, toward the ball.",
            FootworkType.BACK_FOOT to "Move back, into the crease."
        ).forEach { (option, description) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = "Select ${footworkLabel(option)}",
                        role = Role.Button,
                        onClick = { onSelected(option) }
                    )
                    .padding(vertical = 14.dp, horizontal = 8.dp)
            ) {
                Text(footworkLabel(option), style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun BatShotStep(deliverySummary: String, fieldNote: String, onSelected: (NamedShot) -> Unit) {
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
        Text("Choose your shot", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(BattingSystem.SHOT_OPTIONS) { option ->
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "Play ${option.label}",
                            role = Role.Button,
                            onClick = { onSelected(option.value) }
                        )
                        .padding(vertical = 14.dp, horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun BatIntentStep(shotName: String, onSelected: (IntentDirection) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Choose your intent",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Shot: $shotName", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        BAT_INTENT_CHOICES.forEach { choice ->
            val label = BattingSystem.intentLabel(choice.value)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = "Select $label",
                        role = Role.Button,
                        onClick = { onSelected(choice.value) }
                    )
                    .padding(vertical = 14.dp, horizontal = 8.dp)
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(choice.description, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
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
