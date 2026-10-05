package com.cricketsim.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * A live, stereo, 44.1 kHz stadium crowd that is SYNTHESISED while the match runs,
 * instead of looping one short recording.
 *
 * WHY: the old crowd was a single football-stadium recording played in a loop. It
 * could only get louder or quieter (and, worse, was sped up to feel "tense", which
 * raises its pitch like a tape machine). It never changed character, it repeated
 * every few seconds, and it sat in the middle of the head.
 *
 * WHAT IT DOES INSTEAD
 *  - Babble: several bands of noise shaped like the vowel sounds of many people
 *    talking, each wandering in loudness at its own random pace, with the left and
 *    right ears fed different noise so the crowd surrounds you instead of sitting
 *    in one spot. Nothing repeats.
 *  - Excitement (0..1, from the match tension): the whole crowd's pitch and
 *    brightness rise and the chatter gets busier, the way a real crowd leans in
 *    during a close chase. It is done by moving filters, not by speeding a loop up.
 *  - Reactions: a six, a four or a wicket makes a roar swell up (rise fast, hold,
 *    die away slowly) with a burst of applause scattered across the stereo field;
 *    a wicket or a near thing also gets a group "ooh" of several voices.
 *  - Scattered claps and shouts come and go at random all the time.
 *
 * The caller sets the overall loudness (setVolume); the engine only decides the
 * sound itself. One instance = one crowd; stop() it when finished.
 */
internal class CrowdEngine(private val attributes: AudioAttributes) {

    enum class Reaction { SIX, FOUR, WICKET, GASP }

    @Volatile private var excitement = 0f
    @Volatile private var running = false
    @Volatile private var wantPlay = false
    private var track: AudioTrack? = null
    private val reactions = ConcurrentLinkedQueue<Reaction>()

    fun setExcitement(value: Float) {
        excitement = value.coerceIn(0f, 1f)
    }

    fun setVolume(value: Float) {
        runCatching { track?.setVolume(value.coerceIn(0f, 1f)) }
    }

    fun react(reaction: Reaction) {
        if (running) reactions.add(reaction)
    }

    fun pause() {
        wantPlay = false
        runCatching { track?.pause() }
    }

    fun resume() {
        wantPlay = true
        if (running) runCatching { track?.play() }
    }

    /** Creates the stereo stream and starts the generator thread. False means this phone refused it (the caller then falls back). */
    fun start(): Boolean {
        if (running) return true
        val blockBytes = BLOCK_FRAMES * 2 * 2
        val minBytes = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return false
        val created = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBytes, blockBytes * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            return false
        }
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { created.release() }
            return false
        }
        created.setVolume(0f)
        track = created
        running = true
        val worker = Thread({ render(created) }, "crowd-engine")
        worker.isDaemon = true
        worker.start()
        return true
    }

    /**
     * Ends the crowd. The generator thread notices, and releases the stream itself.
     * The pause + flush frees a blocked write so the thread can never be left hanging.
     */
    fun stop() {
        if (!running) return
        running = false
        wantPlay = false
        reactions.clear()
        runCatching { track?.pause() }
        runCatching { track?.flush() }
    }

    private fun render(audio: AudioTrack) {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
        val dsp = Dsp(System.nanoTime(), reactions)
        val block = ShortArray(BLOCK_FRAMES * 2)
        try {
            var primed = 0
            while (running) {
                dsp.fill(block, excitement)
                var offset = 0
                while (offset < block.size && running) {
                    val written = audio.write(block, offset, block.size - offset)
                    if (written < 0) return
                    if (written == 0) Thread.sleep(2) else offset += written
                }
                // Two blocks are queued before playback starts, so it never begins on an empty buffer.
                if (primed < 2) {
                    primed++
                    if (primed == 2 && wantPlay) runCatching { audio.play() }
                }
            }
        } finally {
            runCatching { audio.pause() }
            runCatching { audio.flush() }
            runCatching { audio.release() }
        }
    }

    /** One state-variable filter (the same kind Synth uses): low-pass, band-pass and high-pass taps from one pass. */
    private class Svf {
        var low = 0f
        var band = 0f
        var high = 0f

        fun run(input: Float, f: Float, damp: Float) {
            low += f * band
            high = input - low - damp * band
            band += f * high
        }

        fun reset() {
            low = 0f
            band = 0f
            high = 0f
        }
    }

    private class Dsp(seed: Long, private val queue: ConcurrentLinkedQueue<Reaction>) {

        private var rng = seed or 1L

        /** Fast white noise in [-1, 1). */
        private fun rnd(): Float {
            rng = rng xor (rng shl 13)
            rng = rng xor (rng ushr 7)
            rng = rng xor (rng shl 17)
            return ((rng ushr 40).toInt() / 8388608f) - 1f
        }

        private fun rnd01(): Float = (rnd() + 1f) * 0.5f

        /** A value that drifts to a new random target every so often: the loudness of one band of the crowd. */
        private inner class Wander(val lo: Float, val hi: Float, val minMs: Float, val maxMs: Float) {
            private var value = (lo + hi) * 0.5f
            private var target = value
            private var left = 0

            fun next(): Float {
                left--
                if (left <= 0) {
                    target = lo + (hi - lo) * rnd01()
                    left = ((minMs + (maxMs - minMs) * rnd01()) * SAMPLE_RATE / 1000f).toInt()
                }
                value += (target - value) * WANDER_SLEW
                return value
            }
        }

        /** Everything one ear needs: its own filters and its own random loudness for each band. */
        private inner class Ear {
            val rumble = Svf()
            val b1 = Svf()
            val b2 = Svf()
            val b3 = Svf()
            val air = Svf()
            val roar = Svf()
            val ooh1 = Svf()
            val ooh2 = Svf()
            val e1 = Wander(0.35f, 1.25f, 140f, 520f)
            val e2 = Wander(0.30f, 1.30f, 110f, 420f)
            val e3 = Wander(0.20f, 1.40f, 80f, 300f)
            val eRoar = Wander(0.70f, 1.20f, 200f, 600f)

            fun reset() {
                rumble.reset(); b1.reset(); b2.reset(); b3.reset(); air.reset(); roar.reset(); ooh1.reset(); ooh2.reset()
            }
        }

        private val leftEar = Ear()
        private val rightEar = Ear()

        // Smoothed state
        private var exc = 0f
        private var swell = 0f
        private var swellTarget = 0f
        private var swellHold = 0
        private var applause = 0f

        // Per-block filter settings
        private var fRumble = 0f
        private var f1 = 0f
        private var f2 = 0f
        private var f3 = 0f
        private var fAir = 0f
        private var fRoar = 0f
        private var comp = 1f
        private var w2 = 0f
        private var w3 = 0f
        private var wAir = 0f

        // Claps: short noise ticks, each with its own place in the stereo field
        private val clickLeft = IntArray(CLICKS)
        private val clickLen = IntArray(CLICKS)
        private val clickAmp = FloatArray(CLICKS)
        private val clickToL = FloatArray(CLICKS)
        private val clickToR = FloatArray(CLICKS)

        // The group "ooh": a few voices a little apart in pitch, each in its own place
        private val voiceAge = IntArray(VOICES) { -1 }
        private val voicePhase = FloatArray(VOICES)
        private val voiceFreq = FloatArray(VOICES)
        private val voiceToL = FloatArray(VOICES)
        private val voiceToR = FloatArray(VOICES)
        private var voicesActive = 0

        private fun trigger(reaction: Reaction) {
            when (reaction) {
                Reaction.SIX -> { boost(1.0f, 1.7f); applause = 1.0f }
                Reaction.FOUR -> { boost(0.65f, 1.1f); applause = 0.8f }
                Reaction.WICKET -> { boost(0.9f, 1.4f); applause = 0.7f; startOoh() }
                Reaction.GASP -> startOoh()
            }
        }

        private fun boost(target: Float, holdSeconds: Float) {
            swellTarget = max(swellTarget, target)
            swellHold = max(swellHold, (holdSeconds * SAMPLE_RATE).toInt())
        }

        private fun startOoh() {
            for (v in 0 until VOICES) {
                voiceAge[v] = 0
                voicePhase[v] = rnd01()
                voiceFreq[v] = 150f + 130f * rnd01()
                val pan = rnd01()
                voiceToL[v] = cosPan(pan)
                voiceToR[v] = sinPan(pan)
            }
            voicesActive = VOICES
        }

        private fun cosPan(p: Float): Float = kotlin.math.cos(p * (PI.toFloat() / 2f))
        private fun sinPan(p: Float): Float = sin(p * (PI.toFloat() / 2f))

        private fun spawnClick() {
            for (k in 0 until CLICKS) {
                if (clickLeft[k] <= 0) {
                    val len = 90 + (170 * rnd01()).toInt()
                    clickLen[k] = len
                    clickLeft[k] = len
                    clickAmp[k] = 0.25f + 0.65f * rnd01()
                    val pan = rnd01()
                    clickToL[k] = cosPan(pan)
                    clickToR[k] = sinPan(pan)
                    return
                }
            }
        }

        private fun coef(hz: Float): Float = 2f * sin(PI.toFloat() * min(hz, 7000f) / SAMPLE_RATE)

        private fun hear(ear: Ear, noise: Float, rumbleIn: Float): Float {
            ear.rumble.run(rumbleIn, fRumble, 1.4f)
            ear.b1.run(noise, f1, 0.55f)
            ear.b2.run(noise, f2, 0.50f)
            ear.b3.run(noise, f3, 0.50f)
            ear.air.run(noise, fAir, 0.80f)
            ear.roar.run(noise, fRoar, 0.70f)
            var s = ear.rumble.low * A_RUMBLE
            s += ear.b1.band * A1 * comp * ear.e1.next()
            s += ear.b2.band * A2 * comp * w2 * ear.e2.next()
            s += ear.b3.band * A3 * comp * w3 * ear.e3.next()
            s += ear.air.band * A_AIR * wAir
            s += ear.roar.band * A_ROAR * swell * ear.eRoar.next()
            return s
        }

        fun fill(out: ShortArray, excitementTarget: Float) {
            while (true) {
                val next = queue.poll() ?: break
                trigger(next)
            }
            exc += (excitementTarget - exc) * EXC_SLEW
            val shift = 1f + 0.30f * exc + 0.25f * swell
            comp = 1f / sqrt(shift)
            fRumble = coef(170f)
            f1 = coef(520f * shift)
            f2 = coef(1500f * shift)
            f3 = coef(2700f * shift)
            fAir = coef(4800f * (1f + 0.1f * exc))
            fRoar = coef(900f + 1400f * swell)
            w2 = 0.65f + 0.45f * exc + 0.40f * swell
            w3 = 0.25f + 0.55f * exc + 0.60f * swell
            wAir = 0.03f + 0.35f * exc * exc + 0.35f * swell

            for (i in 0 until BLOCK_FRAMES) {
                if (swellHold > 0) {
                    swellHold--
                    swell += (swellTarget - swell) * SWELL_ATTACK
                } else {
                    swell -= swell * SWELL_RELEASE
                    swellTarget = 0f
                }
                if (applause > 0f) applause = max(0f, applause - APPLAUSE_DECAY)

                val shared = rnd()
                val noiseL = 0.9f * rnd() + 0.3f * shared
                val noiseR = 0.9f * rnd() + 0.3f * shared
                val rumbleL = 0.5f * shared + 0.5f * rnd()
                val rumbleR = 0.5f * shared + 0.5f * rnd()
                var l = hear(leftEar, noiseL, rumbleL)
                var r = hear(rightEar, noiseR, rumbleR)

                // Claps: a steady trickle, and a flurry after a boundary or a wicket.
                val clapRate = 0.8f + 6f * exc + 70f * applause
                if (rnd01() < clapRate / SAMPLE_RATE) spawnClick()
                for (k in 0 until CLICKS) {
                    val left = clickLeft[k]
                    if (left > 0) {
                        val shape = left.toFloat() / clickLen[k]
                        val x = rnd() * clickAmp[k] * shape * shape * CLAP_GAIN
                        l += x * clickToL[k]
                        r += x * clickToR[k]
                        clickLeft[k] = left - 1
                    }
                }

                // The group "ooh"
                if (voicesActive > 0) {
                    var busL = 0f
                    var busR = 0f
                    for (v in 0 until VOICES) {
                        val age = voiceAge[v]
                        if (age < 0) continue
                        val t = age / SAMPLE_RATE.toFloat()
                        val env = if (t < 0.18f) t / 0.18f else exp(-(t - 0.18f) * 2.6f)
                        val freq = voiceFreq[v] * (1f + 0.3f * min(1f, t * 2f))
                        var phase = voicePhase[v] + freq / SAMPLE_RATE
                        if (phase >= 1f) phase -= 1f
                        voicePhase[v] = phase
                        val saw = (2f * phase - 1f) * env * 0.5f
                        busL += saw * voiceToL[v]
                        busR += saw * voiceToR[v]
                        if (age + 1 >= OOH_SAMPLES) {
                            voiceAge[v] = -1
                            voicesActive--
                        } else {
                            voiceAge[v] = age + 1
                        }
                    }
                    leftEar.ooh1.run(busL, OOH_F1, 0.35f)
                    leftEar.ooh2.run(busL, OOH_F2, 0.30f)
                    rightEar.ooh1.run(busR, OOH_F1, 0.35f)
                    rightEar.ooh2.run(busR, OOH_F2, 0.30f)
                    l += OOH_GAIN * (leftEar.ooh1.band + 0.6f * leftEar.ooh2.band)
                    r += OOH_GAIN * (rightEar.ooh1.band + 0.6f * rightEar.ooh2.band)
                }

                if (l.isNaN() || r.isNaN() || l > 50f || r > 50f || l < -50f || r < -50f) {
                    leftEar.reset()
                    rightEar.reset()
                    l = 0f
                    r = 0f
                }
                out[2 * i] = (tanh(l * OUT_GAIN) * 30000f).toInt().toShort()
                out[2 * i + 1] = (tanh(r * OUT_GAIN) * 30000f).toInt().toShort()
            }
        }

        private companion object {
            val OOH_F1 = 2f * sin(PI.toFloat() * 500f / SAMPLE_RATE)
            val OOH_F2 = 2f * sin(PI.toFloat() * 1050f / SAMPLE_RATE)
            val WANDER_SLEW = (1.0 - exp(-1.0 / (0.12 * SAMPLE_RATE))).toFloat()
            val SWELL_ATTACK = (1.0 - exp(-1.0 / (0.25 * SAMPLE_RATE))).toFloat()
            val SWELL_RELEASE = (1.0 - exp(-1.0 / (0.9 * SAMPLE_RATE))).toFloat()
            val EXC_SLEW = (1.0 - exp(-BLOCK_FRAMES / (1.5 * SAMPLE_RATE))).toFloat()
            val APPLAUSE_DECAY = (1.0 / (2.2 * SAMPLE_RATE)).toFloat()
            val OOH_SAMPLES = (1.5 * SAMPLE_RATE).toInt()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        /** 20 ms per block. */
        const val BLOCK_FRAMES = 882
        const val CLICKS = 24
        const val VOICES = 6

        // Band levels, set so every band contributes a similar loudness before its weight is applied
        // (measured: unit white noise through each filter comes out at 0.15 to 0.5, hence the spread).
        const val A_RUMBLE = 1.8f
        const val A1 = 0.60f
        const val A2 = 0.26f
        const val A3 = 0.13f
        const val A_AIR = 0.07f
        const val A_ROAR = 0.45f
        const val CLAP_GAIN = 0.35f
        const val OOH_GAIN = 0.50f
        const val OUT_GAIN = 1.5f
    }
}
