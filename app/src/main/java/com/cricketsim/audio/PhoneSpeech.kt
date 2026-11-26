package com.cricketsim.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * The phone's own text-to-speech, ALWAYS on, with or without a screen reader (SpokenCommentary stays silent when TalkBack is running, so a call that must
 * be heard, such as the manual fielding call, cannot use it). One line at a time: a new one cuts off the old one, and [speak] says when it has finished
 * so the buzzes can start only after the voice is done. The engine is made on first use, or earlier by [warmUp].
 *
 * Patch 150 (the manual running, stopping and catching screens froze for several seconds): a caller waiting for "finished" is never left waiting for nothing.
 *  - A voice that failed to start (or never could) says "finished" at once, instead of leaving the caller to its long time-out.
 *  - A line still waiting for the engine to start is told it is over when a newer line replaces it.
 *  - Each line has its own id, so the end of an old, cut-off line cannot be taken for the end of the new one.
 *  - [warmUp] starts the engine early, so the first spoken call of a match does not wait for it to start.
 */
class PhoneSpeech(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var pendingText: String? = null
    private var pendingDone: (() -> Unit)? = null
    private var onDone: (() -> Unit)? = null
    private var counter = 0
    private var currentUtterance: String? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) { if (utteranceId == currentUtterance) finished() }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) { if (utteranceId == currentUtterance) finished() }
    }

    /** Starts the engine without saying anything, so the first call does not have to wait for it. */
    fun warmUp() {
        if (tts == null && !failed) tts = TextToSpeech(appContext, this)
    }

    fun speak(text: String, whenDone: (() -> Unit)? = null) {
        if (failed) {
            // No voice on this phone: carry on without it.
            if (whenDone != null) handler.post(whenDone)
            return
        }
        val engine = tts
        if (engine == null) {
            dropPending()
            pendingText = text
            pendingDone = whenDone
            tts = TextToSpeech(appContext, this)
            return
        }
        if (!ready) {
            dropPending()
            pendingText = text
            pendingDone = whenDone
            return
        }
        say(engine, text, whenDone)
    }

    /** A line still waiting for the engine is replaced: its caller is told it is over, so it does not wait for a voice that will never say it. */
    private fun dropPending() {
        val old = pendingDone
        pendingText = null
        pendingDone = null
        if (old != null) handler.post(old)
    }

    private fun say(engine: TextToSpeech, text: String, whenDone: (() -> Unit)?) {
        onDone = whenDone
        val id = "cricket-fielding-" + (++counter)
        currentUtterance = id
        engine.setOnUtteranceProgressListener(listener)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        // The engine refused the line (it is not bound, or was shut down): nothing will be said and no callback will come.
        if (result == TextToSpeech.ERROR) finished()
    }

    private fun finished() {
        val callback = onDone
        onDone = null
        if (callback != null) handler.post(callback)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        val text = pendingText
        val done = pendingDone
        pendingText = null
        pendingDone = null
        if (ready) {
            tts?.setLanguage(Locale.getDefault())
            if (text != null) say(tts ?: return, text, done)
        } else {
            failed = true
            // No voice on this phone: carry on without it.
            if (done != null) handler.post(done)
        }
    }

    fun stop() {
        onDone = null
        currentUtterance = null
        tts?.stop()
    }

    fun shutdown() {
        onDone = null
        currentUtterance = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
