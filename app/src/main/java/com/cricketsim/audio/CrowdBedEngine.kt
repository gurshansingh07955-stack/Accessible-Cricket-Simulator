package com.cricketsim.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Process
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * The stadium crowd built from the REAL recording (crowd_ambience.mp3), played as a live
 * stereo stream so it can change while the match goes on.
 *
 * WHY THIS EXISTS: a crowd made only of filtered noise (CrowdEngine) sounds like radio
 * static, however it is shaped; a real crowd is what makes a crowd sound like a crowd. The
 * plain MediaPlayer loop, on the other hand, can only be louder or quieter, repeats
 * audibly, sits in the middle of the head, and was sped up for "tension" (which raises its
 * pitch like a tape machine). So this keeps the real recording and fixes those things:
 *
 *  - The recording is decoded once, and its end is blended into its start so the loop has
 *    no seam.
 *  - It is read by TWO playback heads half a loop apart, with left/right swapped on the
 *    second, so the crowd is wide and surrounds you, and the repeat is far less obvious.
 *    (A mono recording is first turned into two different-sounding ears.)
 *  - The two heads drift slowly in loudness (an 11 s and an 18 s cycle) so the crowd
 *    breathes, and now and then it surges and settles by itself.
 *  - Tension changes how OPEN the crowd sounds (a low-pass filter opens up as excitement
 *    rises) instead of how fast it plays, so the pitch never changes.
 *  - A six, a four or a wicket makes it surge and brighten for a couple of seconds.
 *
 * The caller sets the overall loudness (setVolume). If the recording can't be decoded,
 * onFailed() is called and the caller falls back to the old looping player.
 */
internal class CrowdBedEngine(
    private val context: Context,
    private val source: AssetSource,
    private val attributes: AudioAttributes,
    private val onFailed: () -> Unit
) {

    @Volatile private var excitement = 0f
    @Volatile private var volume = 0f
    @Volatile private var running = false
    @Volatile private var wantPlay = false
    @Volatile private var track: AudioTrack? = null
    private val reactions = ConcurrentLinkedQueue<CrowdEngine.Reaction>()

    fun setExcitement(value: Float) {
        excitement = value.coerceIn(0f, 1f)
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        runCatching { track?.setVolume(volume) }
    }

    fun react(reaction: CrowdEngine.Reaction) {
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

    /** Starts decoding and streaming on a background thread; silent until the recording is ready. */
    fun start() {
        if (running) return
        running = true
        val worker = Thread({ work() }, "crowd-bed")
        worker.isDaemon = true
        worker.start()
    }

    /** Ends the crowd; the worker thread notices and releases the stream itself. */
    fun stop() {
        if (!running) return
        running = false
        wantPlay = false
        reactions.clear()
        runCatching { track?.pause() }
        runCatching { track?.flush() }
    }

    private fun work() {
        val bed = try {
            decodeBed()
        } catch (e: Exception) {
            null
        }
        if (!running) return
        if (bed == null) {
            running = false
            onFailed()
            return
        }
        val audio = createTrack(bed.sampleRate)
        if (audio == null) {
            running = false
            onFailed()
            return
        }
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
        track = audio
        audio.setVolume(volume)
        val frames = bed.sampleRate / 50
        val player = BedPlayer(bed, frames)
        val block = ShortArray(frames * 2)
        try {
            var primed = 0
            while (running) {
                player.fill(block, excitement, reactions)
                var offset = 0
                while (offset < block.size && running) {
                    val written = audio.write(block, offset, block.size - offset)
                    if (written < 0) return
                    if (written == 0) Thread.sleep(2) else offset += written
                }
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

    private fun createTrack(sampleRate: Int): AudioTrack? {
        val frames = sampleRate / 50
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) return null
        val created = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBytes, frames * 4 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            return null
        }
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { created.release() }
            return null
        }
        return created
    }

    /** A decoded, seamlessly looping stereo recording. */
    private class Bed(val left: ShortArray, val right: ShortArray, val sampleRate: Int)

    /** Decodes the recording to PCM, makes it stereo and blends its end into its start. Null if that can't be done. */
    private fun decodeBed(): Bed? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            when (source) {
                is AssetSource.Bytes -> extractor.setDataSource(ByteArrayMediaDataSource(source.bytes))
                is AssetSource.Local -> extractor.setDataSource(source.file.absolutePath)
                is AssetSource.Raw -> context.resources.openRawResourceFd(source.resId).use { afd ->
                    extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            }
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if ((candidate.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var pcm = ShortArray(sampleRate * channels * 20)
            var count = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var guard = 0
            while (!outputDone && running) {
                if (++guard > 200_000) return null
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIndex) ?: return null
                        val size = extractor.readSampleData(inBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    if (info.size > 0) {
                        val outBuffer = decoder.getOutputBuffer(outIndex) ?: return null
                        outBuffer.position(info.offset)
                        outBuffer.limit(info.offset + info.size)
                        val shorts = outBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val piece = ShortArray(shorts.remaining())
                        shorts.get(piece)
                        if (count + piece.size > pcm.size) pcm = pcm.copyOf(max(pcm.size * 2, count + piece.size))
                        System.arraycopy(piece, 0, pcm, count, piece.size)
                        count += piece.size
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (count > sampleRate * channels * MAX_SECONDS) outputDone = true
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val changed = decoder.outputFormat
                    sampleRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
            if (!running) return null

            val frames = count / max(1, channels)
            if (frames < sampleRate * 4) return null
            val left = ShortArray(frames)
            var right = ShortArray(frames)
            for (i in 0 until frames) {
                left[i] = pcm[i * channels]
                right[i] = if (channels >= 2) pcm[i * channels + 1] else pcm[i * channels]
            }
            if (channels < 2) {
                // One mono recording would sit dead centre: give the right ear the same crowd a third of a loop later.
                val shift = frames / 3
                val moved = ShortArray(frames)
                for (i in 0 until frames) moved[i] = left[(i + shift) % frames]
                right = moved
            }
            return loopSeamless(left, right, sampleRate)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Blends the last few seconds into the first few, so going from the end back to the start has no audible seam. */
    private fun loopSeamless(left: ShortArray, right: ShortArray, sampleRate: Int): Bed {
        val frames = left.size
        val fade = min(frames / 4, sampleRate * 3)
        val newLength = frames - fade
        val outLeft = left.copyOf(newLength)
        val outRight = right.copyOf(newLength)
        for (i in 0 until fade) {
            val w = i.toFloat() / fade
            val fadeIn = sin(w * (PI.toFloat() / 2f))
            val fadeOut = cos(w * (PI.toFloat() / 2f))
            outLeft[i] = (left[i] * fadeIn + left[newLength + i] * fadeOut).toInt().coerceIn(-32768, 32767).toShort()
            outRight[i] = (right[i] * fadeIn + right[newLength + i] * fadeOut).toInt().coerceIn(-32768, 32767).toShort()
        }
        return Bed(outLeft, outRight, sampleRate)
    }

    /** Reads the loop with two heads and shapes it with excitement, breathing and surges. */
    private class BedPlayer(private val bed: Bed, private val blockFrames: Int) {
        private val size = bed.left.size
        private val half = size / 2
        private var head1 = Random.nextInt(size)
        private var head2 = (head1 + half) % size
        private var lowL = 0f
        private var lowR = 0f
        private var exc = 0f
        private var swell = 0f
        private var swellTarget = 0f
        private var swellHoldBlocks = 0
        private var phase1 = Random.nextFloat() * 6.2831855f
        private var phase2 = Random.nextFloat() * 6.2831855f
        private var surgeCountdown = nextSurge(0f)
        private val blocksPerSecond = bed.sampleRate.toFloat() / blockFrames
        private val phaseStep1 = 6.2831855f * blockFrames / (11.3f * bed.sampleRate)
        private val phaseStep2 = 6.2831855f * blockFrames / (17.9f * bed.sampleRate)
        private val excSlew = 1f - exp(-1f / (1.5f * blocksPerSecond))

        private fun nextSurge(excitement: Float): Int =
            ((10f + 15f * Random.nextFloat()) * (1f - 0.5f * excitement) * (bed.sampleRate.toFloat() / blockFrames)).toInt()

        private fun trigger(reaction: CrowdEngine.Reaction) {
            val (level, seconds) = when (reaction) {
                CrowdEngine.Reaction.SIX -> 1.0f to 2.4f
                CrowdEngine.Reaction.FOUR -> 0.65f to 1.6f
                CrowdEngine.Reaction.WICKET -> 0.9f to 2.0f
                CrowdEngine.Reaction.GASP -> 0.4f to 1.0f
            }
            swellTarget = max(swellTarget, level)
            swellHoldBlocks = max(swellHoldBlocks, (seconds * blocksPerSecond).toInt())
        }

        fun fill(out: ShortArray, excitementTarget: Float, queue: ConcurrentLinkedQueue<CrowdEngine.Reaction>) {
            while (true) {
                val next = queue.poll() ?: break
                trigger(next)
            }
            exc += (excitementTarget - exc) * excSlew

            // Now and then the crowd surges a little and settles by itself.
            if (--surgeCountdown <= 0) {
                swellTarget = max(swellTarget, 0.35f + 0.45f * Random.nextFloat())
                swellHoldBlocks = max(swellHoldBlocks, (2.0f * blocksPerSecond).toInt())
                surgeCountdown = nextSurge(exc)
            }
            if (swellHoldBlocks > 0) {
                swellHoldBlocks--
                swell += (swellTarget - swell) * 0.2f
            } else {
                swell *= 0.985f
                swellTarget = 0f
            }

            phase1 += phaseStep1
            phase2 += phaseStep2
            val gain1 = BASE_GAIN * (1f + 0.22f * sin(phase1)) * (1f + SURGE_LIFT * swell)
            val gain2 = BASE_GAIN * (1f + 0.22f * sin(phase2)) * (1f + SURGE_LIFT * swell)

            // Calm crowds sound a little distant; excitement opens the top end.
            val cutoff = 11000f + 9000f * min(1f, 0.6f * exc + 0.8f * swell)
            val alpha = 1f - exp(-6.2831855f * cutoff / bed.sampleRate)

            val left = bed.left
            val right = bed.right
            for (i in 0 until blockFrames) {
                val l = (left[head1] * gain1 + right[head2] * gain2) * INV
                val r = (right[head1] * gain1 + left[head2] * gain2) * INV
                lowL += alpha * (l - lowL)
                lowR += alpha * (r - lowR)
                out[2 * i] = (tanh(lowL) * 32000f).toInt().toShort()
                out[2 * i + 1] = (tanh(lowR) * 32000f).toInt().toShort()
                if (++head1 >= size) head1 = 0
                if (++head2 >= size) head2 = 0
            }
        }
    }

    private companion object {
        const val MAX_SECONDS = 75
        const val BASE_GAIN = 0.62f

        /** How much louder the crowd gets at a full surge (0.45 = 45% louder). */
        const val SURGE_LIFT = 0.45f
        const val INV = 1f / 32768f
    }
}
