package com.cricketsim.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * The IPL auctioneer's recorded voice. The calls are NOT one clip per sentence: there would be
 * far too many (every player x every price). Instead the app plays a short run of clips that
 * together make the call, for example
 *
 *     "Sold!"  +  "Virat Kohli"  +  "to"  +  "Chennai Super Kings"  +  "for"  +  "12 crore 50 lakh"
 *
 * where each part is a separate recording (a player's name, a team, a price, a fixed phrase), all in
 * the same British voice and played back to back with a very small gap. Clip ids are built by the
 * IPL screen; the clips live in the audio pack as "<id>.mp3" (see tools/auction_clip_ids.py for how
 * the full list is worked out, and tools/fetch_audio.sh for how they are fetched).
 *
 * If ANY clip of a call is missing from the pack, [play] returns false and plays nothing at all, so
 * the caller can speak the whole call with the phone's own voice instead; the two voices are never
 * mixed inside one call.
 */
internal class AuctionVoice(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<ByteArray>()
    private var current: MediaPlayer? = null
    private var busy = false

    /**
     * How fast the voice speaks: 1 = as recorded, more = quicker at the same pitch (the fast auction).
     * The caller sets it before each call; the gap between the parts shrinks with it too, so a fast call
     * still sounds like one sentence.
     */
    @Volatile
    var speed: Float = 1f

    /** True while a call is playing or waiting to play; the auction hall waits for it to end before timing the next step. */
    val isSpeaking: Boolean get() = busy || queue.isNotEmpty()

    /** Queues the clips for playback, after anything already playing. False if any clip is missing. */
    fun play(ids: List<String>, interrupt: Boolean = false): Boolean {
        val clips = ArrayList<ByteArray>()
        for (id in ids) {
            clips.add(AudioPack.bytesFor(context.applicationContext, "$id.mp3") ?: return false)
        }
        // A new bid must be heard NOW, not after the previous call has finished: stale calls are dropped
        // so the voice never trails behind the price on screen.
        if (interrupt) stop()
        clips.forEach { queue.addLast(it) }
        if (!busy) next()
        return true
    }

    private fun next() {
        val bytes = queue.removeFirstOrNull()
        if (bytes == null) {
            busy = false
            return
        }
        busy = true
        val player = MediaPlayer()
        current = player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(BytesSource(bytes))
            player.setOnPreparedListener { prepared ->
                if (speed != 1f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching { prepared.playbackParams = PlaybackParams().setSpeed(speed) }
                }
                prepared.start()
            }
            player.setOnCompletionListener {
                it.release()
                if (current === it) current = null
                handler.postDelayed({ next() }, (GAP_MS / speed).toLong())
            }
            player.setOnErrorListener { p, _, _ ->
                runCatching { p.release() }
                if (current === p) current = null
                handler.post { next() }
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            runCatching { player.release() }
            if (current === player) current = null
            handler.post { next() }
        }
    }

    /** Stops whatever is playing and forgets everything queued. */
    fun stop() {
        queue.clear()
        handler.removeCallbacksAndMessages(null)
        runCatching { current?.stop() }
        runCatching { current?.release() }
        current = null
        busy = false
    }

    private class BytesSource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val count = minOf(size, data.size - position.toInt())
            System.arraycopy(data, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize(): Long = data.size.toLong()

        override fun close() {}
    }

    private companion object {
        /** A breath between the parts of one call, short so the call still sounds like one sentence. */
        const val GAP_MS = 40L
    }
}
