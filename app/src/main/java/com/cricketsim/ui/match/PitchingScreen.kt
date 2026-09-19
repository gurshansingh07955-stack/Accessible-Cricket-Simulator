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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
 * fielding — see UI_NOTES.md). This is a FRESH design for TalkBack, not
 * a port of the web app's continuous press-and-drag PitcherScreen — a
 * fine-grained analog drag has no reliable non-visual equivalent, so
 * every dimension a real bowler chooses (angle, line, variation, target
 * length, speed) is instead a discrete, single-swipe list pick, same
 * pattern as the rest of this app's screens. See UI_NOTES.md's
 * accessibility principles for why this is preferred over inventing new
 * custom semantics.
 *
 * The one dimension that can't be a discrete list pick without losing
 * all sense of "skill" is EXECUTION — how well the delivery actually
 * came out, which BowlingSystem.computeBowlingQuality expects as a
 * continuous `verticalFraction` (where the release actually landed
 * along the pitch, 0 = bowler's end, 1 = batsman's end) plus a
 * `maxVerticalFractionReached` for overshoot detection. Rather than a
 * drag, this screen uses a REPEATING HAPTIC PULSE the player releases
 * on: a fixed sequence of vibration pulses plays, timed by
 * BattingSystem.computeTimingIntervalMs(speedKmh) — deliberately
 * reusing the exact same speed-to-interval mapping the batting timing
 * minigame uses, so "faster deliveries are harder to time" feels
 * consistent whichever side of the ball the player is on. Releasing
 * exactly on the final pulse lands dead in the center of the chosen
 * target length band; releasing early or late pushes the release point
 * away from center in proportion to the timing error (early -> shorter,
 * late -> fuller), potentially drifting into a neighboring band on a
 * bad enough miss — BowlingSystem.computeLengthPrecision (called
 * inside computeBowlingQuality) then scores whatever band the release
 * ACTUALLY lands in, exactly as it would for any other verticalFraction
 * source. This is a genuine skill mechanic, not a fixed-outcome
 * animation, and produces a real, inspectable QualityBreakdown.
 *
 * RELEASE STEP (rewritten to match BattingScreen's timing step — see
 * its doc comment for the full reasoning): the whole step is ONE
 * full-screen `clickable` node and the only focusable element on it, so
 * TalkBack focus lands there and a double-tap anywhere releases. The
 * error is measured in real milliseconds with
 * SystemClock.elapsedRealtime() against a fixed schedule (lead-in + 3
 * intervals = the final pulse), and pulses are scheduled against the
 * start time rather than chained. The previous version estimated
 * elapsed time as `pulsesFired * intervalMs`, which only changed when a
 * pulse fired: any tap between pulse 3 and 4 was scored as maximally
 * early and any tap after pulse 4 (up to the grace timeout) as exactly
 * perfect, so late releases were never penalised. It was also
 * effectively unplayable with TalkBack, because the Release button sat
 * several swipes away from the first focus stop while the whole rhythm
 * (and its auto-miss timeout) finished in about two seconds.
 * There is deliberately no Back on the release step — once the rhythm
 * starts the delivery is committed, same as BattingScreen's timing.
 *
 * KNOWN V1 SIMPLIFICATIONS (not permanent design decisions):
 * - `maxVerticalFractionReached` is always set equal to the final
 *   `verticalFraction` (the release point only ever moves toward its
 *   final value here, never overshoots and corrects), so the
 *   smoothness penalty is always 0 for a user-bowled delivery in this
 *   version. A future iteration could introduce genuine overshoot risk
 *   (e.g. a brief "hold too long and it drifts past" mechanic) if that
 *   proves worth the added complexity.
 * - Swing is not player-controlled yet (always SwingType.NONE) — the
 *   web app's swing came from the horizontal curve of its drag path,
 *   which this discrete redesign doesn't have an equivalent axis for.
 *   A future iteration could add a dedicated swing-direction list step
 *   if that's judged worth the extra complexity for stock deliveries.
 * - Haptic feedback is the only non-visual timing cue right now — no
 *   audio tone accompanies each pulse, since the audio/mixing layer is
 *   separate future work (see PORTING_NOTES.md's "Audio" entry). All
 *   pulses are the same Compose LongPress buzz.
 * - PITCH_INPUT_LATENCY_COMPENSATION_MS is 0 — touch-to-click latency
 *   under TalkBack is unmeasured; calibrate on a real device.
 * - The timing logic here duplicates BattingScreen's BatTimingStep. If
 *   a third timing surface ever appears, extract a shared composable.
 */

private enum class PitchStep { ANGLE, LINE, VARIATION, LENGTH, SPEED, RELEASE, RESULT }

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

@Composable
fun PitchingScreen(bowler: Player, onDeliveryResolved: (ResolvedBowlingDecision) -> Unit, onBack: () -> Unit) {
    var step by remember { mutableStateOf(PitchStep.ANGLE) }
    var angle by remember { mutableStateOf<BowlingAngle?>(null) }
    var line by remember { mutableStateOf(BowlingSystem.LINE_OPTIONS.first().value) }
    var variation by remember { mutableStateOf(BowlingVariation.STOCK) }
    var targetLength by remember { mutableStateOf(BowlingLength.GOOD_LENGTH) }
    var speedKmh by remember { mutableStateOf(0) }
    var decision by remember { mutableStateOf<ResolvedBowlingDecision?>(null) }

    when (step) {
        PitchStep.ANGLE -> AngleStep(
            onSelected = { chosen -> angle = chosen; step = PitchStep.LINE },
            onBack = onBack
        )
        PitchStep.LINE -> LineStep(
            onSelected = { chosen -> line = chosen; step = PitchStep.VARIATION },
            onBack = { step = PitchStep.ANGLE }
        )
        PitchStep.VARIATION -> VariationStep(
            bowler = bowler,
            onSelected = { chosen -> variation = chosen; step = PitchStep.LENGTH },
            onBack = { step = PitchStep.LINE }
        )
        PitchStep.LENGTH -> LengthStep(
            onSelected = { chosen -> targetLength = chosen; step = PitchStep.SPEED },
            onBack = { step = PitchStep.VARIATION }
        )
        PitchStep.SPEED -> SpeedStep(
            bowler = bowler,
            variation = variation,
            onSelected = { chosen -> speedKmh = chosen; step = PitchStep.RELEASE },
            onBack = { step = PitchStep.LENGTH }
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
                    angle = requireNotNull(angle) { "angle must be set before the release step" },
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

@Composable
private fun AngleStep(onSelected: (BowlingAngle) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Choose your angle",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        BowlingSystem.ANGLE_OPTIONS.forEach { option ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = "Select ${option.label}",
                        role = Role.Button,
                        onClick = { onSelected(option.value) }
                    )
                    .padding(vertical = 14.dp, horizontal = 8.dp)
            ) {
                Text(option.label, style = MaterialTheme.typography.titleMedium)
                Text(option.description, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun LineStep(onSelected: (BowlingLine) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Choose your line",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(BowlingSystem.LINE_OPTIONS) { option ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "Select ${option.label}",
                            role = Role.Button,
                            onClick = { onSelected(option.value) }
                        )
                        .padding(vertical = 14.dp, horizontal = 8.dp)
                ) {
                    Text(option.label, style = MaterialTheme.typography.titleMedium)
                    Text(option.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun VariationStep(bowler: Player, onSelected: (BowlingVariation) -> Unit, onBack: () -> Unit) {
    val options = remember(bowler.bowlingStyle) { BowlingSystem.getVariationOptions(bowler.bowlingStyle) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Choose your delivery",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(options) { option ->
                Text(
                    text = "${option.label} (${option.speedRangeKmh.min}-${option.speedRangeKmh.max} km/h)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "Select ${option.label}",
                            role = Role.Button,
                            onClick = { onSelected(option.value) }
                        )
                        .padding(vertical = 14.dp, horizontal = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

@Composable
private fun LengthStep(onSelected: (BowlingLength) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "Choose your target length",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(BowlingSystem.LENGTH_BANDS) { band ->
                Text(
                    text = band.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClickLabel = "Select ${band.label}",
                            role = Role.Button,
                            onClick = { onSelected(band.value) }
                        )
                        .padding(vertical = 14.dp, horizontal = 8.dp)
                )
            }
        }
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
            "Next you'll feel four buzzes. Double-tap anywhere on the fourth to release. " +
                "Early tends to go shorter, late tends to go fuller.",
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
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
