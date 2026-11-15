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
 * so the buzzes can start only after the voice is done. The engine is made on first use.
 */
class PhoneSpeech(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var pendingDone: (() -> Unit)? = null
    private var onDone: (() -> Unit)? = null

    fun speak(text: String, whenDone: (() -> Unit)? = null) {
        val engine = tts
        if (engine == null) {
            pendingText = text
            pendingDone = whenDone
            tts = TextToSpeech(appContext, this)
            return
        }
        if (!ready) {
            pendingText = text
            pendingDone = whenDone
            return
        }
        say(engine, text, whenDone)
    }

    private fun say(engine: TextToSpeech, text: String, whenDone: (() -> Unit)?) {
        onDone = whenDone
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { finished() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { finished() }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
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
        } else if (done != null) {
            // No voice on this phone: carry on without it.
            handler.post(done)
        }
    }

    fun stop() {
        onDone = null
        tts?.stop()
    }

    fun shutdown() {
        onDone = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        const val UTTERANCE_ID = "cricket-fielding"
    }
}
