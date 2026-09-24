package com.cricketsim.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every sound this game can make with no audio file at all, generated as
 * 16-bit mono PCM. It is a port of helpers/audioManager.tsx's
 * synthesized effects (the timing tick with its accented final pulse, the
 * bat hit, the boundary arpeggio, the wicket sweep, the delivery whoosh,
 * the noise crowd cheer) — same waveforms, frequencies, sweeps and
 * envelope shapes, with Web Audio's oscillators, gain ramps and biquad
 * filters replaced by plain sample loops — plus a few additions the web
 * doesn't have because it always has its recordings: a coin clink, thunder,
 * a rain bed and a crowd murmur bed, so that every sound has SOME
 * stand-in when the real recording hasn't been downloaded yet or is
 * offline. The stand-ins are deliberately plain; the recordings are
 * better and are used whenever they are available.
 *
 * The one deliberate difference from the web is the timing tick: it is
 * generated much louder (the web's is very quiet, since it sits under a
 * 0.5 master gain and is only a backup to vibration on iOS). On Android it
 * is the audible timing channel for both rhythm minigames, and its
 * accented final pulse is what tells you by ear which pulse to act on.
 *
 * All buffers are generated once and cached by SoundEngine.
 */
internal object Synth {
    const val SAMPLE_RATE = 22050

    private enum class Wave { SINE, TRIANGLE, SAWTOOTH, SQUARE }

    /**
     * A state-variable filter (Chamberlin). Unlike a fixed biquad it takes
     * its cutoff per sample, which the whoosh, cheer and thunder sweeps
     * need. Read the output you want after each step: lowPass, bandPass
     * or high.
     */
    private class Filter {
        private var low = 0.0
        private var band = 0.0
        var high = 0.0
            private set
        val lowPass: Double get() = low
        val bandPass: Double get() = band

        fun step(input: Double, cutoffHz: Double, q: Double = 0.707) {
            // Clamped: the Chamberlin form goes unstable as the cutoff
            // approaches a sixth of the sample rate.
            val f = 2.0 * sin(PI * min(cutoffHz, SAMPLE_RATE / 6.0) / SAMPLE_RATE)
            low += f * band
            high = input - low - band / q
            band += f * high
        }
    }

    private fun frames(seconds: Double): Int = (seconds * SAMPLE_RATE).toInt()

    private fun noise(random: Random): Double = random.nextDouble() * 2.0 - 1.0

    private fun waveAt(wave: Wave, phase: Double): Double = when (wave) {
        Wave.SINE -> sin(phase)
        Wave.TRIANGLE -> (2.0 / PI) * asin(sin(phase))
        Wave.SAWTOOTH -> {
            val cycles = phase / (2.0 * PI)
            2.0 * (cycles - floor(cycles)) - 1.0
        }
        Wave.SQUARE -> if (sin(phase) >= 0.0) 1.0 else -1.0
    }

    private fun expRamp(from: Double, to: Double, t: Double, duration: Double): Double =
        if (t >= duration) to else from * (to / from).pow(t / duration)

    private fun linRamp(from: Double, to: Double, t: Double, duration: Double): Double =
        if (t >= duration) to else from + (to - from) * (t / duration)

    /** Adds one oscillator with a per-sample frequency and gain into `out`, starting `startSec` in. */
    private fun addTone(
        out: FloatArray,
        startSec: Double,
        durSec: Double,
        wave: Wave,
        freqAt: (Double) -> Double,
        gainAt: (Double) -> Double
    ) {
        val first = frames(startSec)
        val count = frames(durSec)
        var phase = 0.0
        for (i in 0 until count) {
            val index = first + i
            if (index >= out.size) break
            val t = i.toDouble() / SAMPLE_RATE
            phase += 2.0 * PI * freqAt(t) / SAMPLE_RATE
            out[index] += (waveAt(wave, phase) * gainAt(t)).toFloat()
        }
    }

    private fun toPcm(buffer: FloatArray, scale: Float = 1f): ShortArray =
        ShortArray(buffer.size) { i ->
            ((buffer[i] * scale).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }

    private fun normalize(buffer: FloatArray, peak: Float): FloatArray {
        var loudest = 0f
        for (v in buffer) loudest = max(loudest, abs(v))
        if (loudest == 0f) return buffer
        val factor = peak / loudest
        return FloatArray(buffer.size) { i -> buffer[i] * factor }
    }

    /**
     * Crossfades the tail into the head so a looped buffer has no click at
     * the wrap point. The result is `fade` samples shorter than the input.
     */
    private fun makeLoopable(buffer: FloatArray, fade: Int): FloatArray {
        val length = buffer.size - fade
        return FloatArray(length) { i ->
            if (i < fade) {
                val w = i.toFloat() / fade
                buffer[i] * w + buffer[length + i] * (1f - w)
            } else {
                buffer[i]
            }
        }
    }

    // --- The timing channel ---

    /** One pulse of the timing rhythm. The accented final pulse is higher and louder. */
    fun tick(accent: Boolean): ShortArray {
        val out = FloatArray(frames(0.08))
        val peak = if (accent) 0.9 else 0.6
        val freq = if (accent) 1400.0 else 900.0
        addTone(out, 0.0, 0.07, Wave.SQUARE, { freq }) { t -> expRamp(peak, 0.001, t, 0.06) }
        return toPcm(out)
    }

    // --- One-shots (web: playBatHit / playBoundary / playWicket / playBallDelivery / playCrowdCheer) ---

    fun batHit(): ShortArray {
        val out = FloatArray(frames(0.2))
        // The "thwack": a triangle sweeping 300 -> 50 Hz.
        addTone(out, 0.0, 0.15, Wave.TRIANGLE, { t -> expRamp(300.0, 50.0, t, 0.1) }) { t -> expRamp(0.8, 0.01, t, 0.1) }
        // The "crack": a short burst of high-passed noise.
        val random = Random(7)
        val filter = Filter()
        for (i in 0 until frames(0.1)) {
            val t = i.toDouble() / SAMPLE_RATE
            filter.step(noise(random), 1000.0)
            out[i] += (filter.high * expRamp(0.5, 0.01, t, 0.05)).toFloat()
        }
        return toPcm(out, 0.7f)
    }

    /** A major arpeggio, C5 E5 G5 C6. */
    fun boundary(): ShortArray {
        val out = FloatArray(frames(0.5))
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        notes.forEachIndexed { index, freq ->
            addTone(out, index * 0.08, 0.2, Wave.SINE, { freq }) { t ->
                if (t < 0.02) linRamp(0.0, 0.3, t, 0.02) else expRamp(0.3, 0.01, t - 0.02, 0.08)
            }
        }
        return toPcm(out)
    }

    /** A dramatic descending sawtooth. */
    fun wicket(): ShortArray {
        val out = FloatArray(frames(0.9))
        addTone(out, 0.0, 0.9, Wave.SAWTOOTH, { t -> expRamp(400.0, 50.0, t, 0.8) }) { t -> linRamp(0.3, 0.0, t, 0.8) }
        return toPcm(out)
    }

    /** Band-passed noise sweeping 200 -> 800 -> 100 Hz. */
    fun whoosh(): ShortArray {
        val out = FloatArray(frames(0.5))
        val random = Random(9)
        val filter = Filter()
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val cutoff = when {
                t < 0.2 -> linRamp(200.0, 800.0, t, 0.2)
                t < 0.4 -> linRamp(800.0, 100.0, t - 0.2, 0.2)
                else -> 100.0
            }
            filter.step(noise(random), cutoff, 1.0)
            val gain = when {
                t < 0.2 -> linRamp(0.0, 0.4, t, 0.2)
                t < 0.4 -> linRamp(0.4, 0.0, t - 0.2, 0.2)
                else -> 0.0
            }
            out[i] = (filter.bandPass * gain * 3.0).toFloat()
        }
        return toPcm(out)
    }

    /** The web's noise-based crowd cheer (a low-pass sweep that swells then decays). `big` is the longer roar. */
    fun cheer(big: Boolean): ShortArray {
        val total = if (big) 3.5 else 2.2
        val peak = if (big) 0.5 else 0.35
        val out = FloatArray(frames(total))
        val random = Random(if (big) 3 else 4)
        val filter = Filter()
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            filter.step(noise(random), 500.0 + 1500.0 * min(1.0, t / 0.5), 0.8)
            val gain = if (t < 0.5) linRamp(0.0, peak, t, 0.5) else expRamp(peak, 0.01, t - 0.5, total - 1.0)
            out[i] = (filter.lowPass * gain * 2.5).toFloat()
        }
        return toPcm(out)
    }

    // --- Stand-ins for the recordings the web always has (not in the web) ---

    /** A short metallic clink for the coin flip. */
    fun coin(): ShortArray {
        val out = FloatArray(frames(0.5))
        addTone(out, 0.0, 0.45, Wave.SINE, { 2400.0 }) { t -> expRamp(0.4, 0.001, t, 0.4) }
        addTone(out, 0.0, 0.35, Wave.SINE, { 3600.0 }) { t -> expRamp(0.25, 0.001, t, 0.3) }
        return toPcm(out)
    }

    /** A low rumbling crack of thunder. */
    fun thunder(): ShortArray {
        val out = FloatArray(frames(3.4))
        val random = Random(5)
        val rumble = Filter()
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            rumble.step(noise(random), 200.0 - 130.0 * min(1.0, t / 3.0), 0.8)
            val attack = if (t < 0.04) t / 0.04 else 1.0
            val main = attack * expRamp(0.9, 0.01, max(0.0, t - 0.04), 2.6)
            val second = if (t >= 0.9) expRamp(0.5, 0.01, t - 0.9, 2.0) else 0.0
            out[i] = (rumble.lowPass * (main + second) * 3.0).toFloat()
        }
        return toPcm(normalize(out, 0.85f))
    }

    // --- Loopable beds ---

    /** Steady rain: hiss over a soft low body, crossfaded so it loops without a click. */
    fun rainLoop(): ShortArray {
        val fade = frames(0.25)
        val raw = FloatArray(frames(4.0) + fade)
        val random = Random(11)
        val hiss = Filter()
        val body = Filter()
        for (i in raw.indices) {
            val n = noise(random)
            hiss.step(n, 2500.0)
            body.step(n * 0.7, 700.0)
            raw[i] = (hiss.high * 0.6 + body.lowPass * 0.5).toFloat()
        }
        return toPcm(normalize(makeLoopable(raw, fade), 0.9f))
    }

    /**
     * A murmur of crowd: low-passed noise plus a band of "chatter", slowly
     * swelling. The swell frequencies complete whole cycles over the loop,
     * so the amplitude wraps as seamlessly as the noise does.
     */
    fun crowdBed(): ShortArray {
        val seconds = 6.0
        val fade = frames(0.25)
        val raw = FloatArray(frames(seconds) + fade)
        val random = Random(21)
        val murmur = Filter()
        val chatter = Filter()
        for (i in raw.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val n = noise(random)
            murmur.step(n, 900.0)
            chatter.step(n, 350.0, 1.5)
            val body = murmur.lowPass + chatter.bandPass * 0.8
            val swell = 1.0 +
                0.25 * sin(2.0 * PI * 0.5 * t) +
                0.15 * sin(2.0 * PI * t / seconds + 1.0) +
                0.10 * sin(2.0 * PI * 1.0 * t + 2.0)
            raw[i] = (body * swell).toFloat()
        }
        return toPcm(normalize(makeLoopable(raw, fade), 0.9f))
    }

    // --- Continuous aim-drag feedback (pitching/batting two-finger gestures) ---

    /**
     * A short, cleanly loopable pure sine tone at a fixed reference pitch.
     * Played through SoundEngine's looping channel machinery (the same
     * SynthLoop/AudioTrack.setPlaybackRate path the crowd bed's tension
     * creep already uses) and pitch-shifted LIVE as a two-finger drag
     * moves, giving continuous, non-speech feedback that tracks a value
     * by ear — see SoundEngine.updateAimTone. A few whole cycles at the
     * reference pitch, THEN crossfaded, so there is no seam even before
     * the crossfade blends it.
     */
    fun aimTone(): ShortArray {
        val fade = frames(0.02)
        val raw = FloatArray(frames(0.25) + fade)
        for (i in raw.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            raw[i] = (0.5 * sin(2.0 * PI * AIM_TONE_REFERENCE_HZ * t)).toFloat()
        }
        return toPcm(makeLoopable(raw, fade))
    }

    // A4. Arbitrary but clearly audible and comfortably centered in
    // SoundEngine's AIM_TONE_MIN_RATE..AIM_TONE_MAX_RATE sweep range.
    private const val AIM_TONE_REFERENCE_HZ = 440.0
}
