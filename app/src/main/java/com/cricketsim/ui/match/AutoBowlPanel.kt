package com.cricketsim.ui.match

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import com.cricketsim.audio.LocalGameServices
import com.cricketsim.logic.FootworkType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * "Face next ball" switched off (Settings > Batting controls): the ball arrives by itself after a
 * countdown, and the footwork is chosen by tilting the phone: left for front foot, right for back
 * foot. If the phone is never tilted, it is front foot.
 *
 * The countdown only runs while it is fair to:
 *  - it waits until the last ball's commentary has finished (plus a moment to take it in);
 *  - it stops when the app is not in front, and starts again from the full time when it returns;
 *  - it is only on screen when the match screen is ready for the next ball, so any other screen
 *    (drinks break, review, new batter, the leave-match question, a menu) removes it, and it
 *    starts afresh when that screen is gone. [holdTimer] pauses it under a dialog.
 * It speaks "Ball in five" and ticks over the last three seconds. The batter still chooses the
 * shot; only the delivery is automatic.
 */
@Composable
fun AutoBowlPanel(seconds: Int, holdTimer: Boolean, onBowl: (FootworkType) -> Unit) {
    val context = LocalContext.current
    val services = LocalGameServices.current
    val sound = services?.sound
    val haptics = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var footwork by remember { mutableStateOf(FootworkType.FRONT_FOOT) }
    var remaining by remember { mutableStateOf(seconds) }
    var waiting by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    val latestOnBowl by rememberUpdatedState(onBowl)
    val latestHold by rememberUpdatedState(holdTimer)
    val tilt = remember { TiltTracker() }

    // Tilt sensing runs only while the app is in front, and is re-centred each time it comes back.
    DisposableEffect(lifecycle) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                tilt.onReading(event.values[0], event.values[1], event.values[2])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    tilt.recentre()
                    if (manager != null && sensor != null) {
                        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> manager?.unregisterListener(listener)
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            manager?.unregisterListener(listener)
        }
    }

    // A tilt must be held for a moment to count, so a wobble does not change the footwork.
    LaunchedEffect(Unit) {
        var candidate = 0
        var heldMs = 0
        while (true) {
            delay(100)
            val direction = tilt.direction
            if (direction == 0) {
                candidate = 0
                heldMs = 0
                continue
            }
            if (direction == candidate) heldMs += 100 else {
                candidate = direction
                heldMs = 0
            }
            if (heldMs >= HOLD_MS) {
                val wanted = if (direction > 0) FootworkType.FRONT_FOOT else FootworkType.BACK_FOOT
                if (wanted != footwork) {
                    footwork = wanted
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    services?.announceSpoken(if (wanted == FootworkType.FRONT_FOOT) "Front foot." else "Back foot.")
                }
            }
        }
    }

    LaunchedEffect(lifecycle, seconds) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            remaining = seconds
            waiting = true
            // Let the last ball's result be heard first, then wait for the commentary to finish.
            delay(2500)
            while (sound?.isCommentaryBusy() == true) delay(200)
            waiting = false
            while (remaining > 0) {
                if (paused || latestHold) {
                    delay(200)
                    continue
                }
                if (remaining == 5) services?.announceSpoken("Ball in five.")
                if (remaining <= 3) sound?.playCountdownTick()
                delay(1000)
                remaining--
            }
            latestOnBowl(footwork)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when {
                waiting -> "Get ready for the next ball."
                paused -> "Timer paused."
                else -> "Next ball in $remaining seconds."
            },
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Footwork: ${if (footwork == FootworkType.FRONT_FOOT) "front foot" else "back foot"}. " +
                "Tilt your phone left for front foot, right for back foot.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { paused = !paused }, modifier = Modifier.fillMaxWidth()) {
            Text(if (paused) "Resume timer" else "Pause timer")
        }
    }
}

private const val HOLD_MS = 300
private const val TILT_DEGREES = 25f
private const val NEUTRAL_DEGREES = 12f

/**
 * Reads the accelerometer as a left/right roll angle. The first half second after the countdown
 * starts sets the neutral position, so the phone can be held however is comfortable; tilting
 * about 25 degrees either way from there counts as left (+1) or right (-1), and it must come
 * back within 12 degrees of neutral before the other side is read cleanly.
 */
private class TiltTracker {
    @Volatile var direction: Int = 0
        private set
    private var smoothed = 0f
    private var baseline: Float? = null
    private var baselineSum = 0f
    private var baselineCount = 0

    fun recentre() {
        baseline = null
        baselineSum = 0f
        baselineCount = 0
        smoothed = 0f
        direction = 0
    }

    fun onReading(ax: Float, ay: Float, az: Float) {
        // Left edge down makes the x reading positive, right edge down negative.
        val roll = Math.toDegrees(atan2(ax.toDouble(), sqrt((ay * ay + az * az).toDouble()))).toFloat()
        val reference = baseline
        if (reference == null) {
            smoothed = if (baselineCount == 0) roll else smoothed + 0.2f * (roll - smoothed)
            baselineSum += smoothed
            baselineCount++
            if (baselineCount >= 25) baseline = baselineSum / baselineCount
            return
        }
        smoothed += 0.2f * (roll - smoothed)
        val delta = smoothed - reference
        direction = when {
            delta > TILT_DEGREES -> 1
            delta < -TILT_DEGREES -> -1
            abs(delta) < NEUTRAL_DEGREES -> 0
            else -> direction
        }
    }
}
