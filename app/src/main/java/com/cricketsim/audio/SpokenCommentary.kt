package com.cricketsim.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import java.util.Locale

/**
 * The web's `commentary` setting: reading each announcement aloud with the
 * platform's text-to-speech, for someone NOT using a screen reader.
 *
 * It deliberately does nothing while TalkBack (touch exploration) is on,
 * because TalkBack already speaks the live regions — with both, every ball
 * would be read twice, in two voices, on top of each other. The engine is
 * created on first use, so a TalkBack user never pays for it at all.
 */
class SpokenCommentary(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    private fun screenReaderRunning(): Boolean {
        val manager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return manager?.isTouchExplorationEnabled == true
    }

    fun speak(text: String) {
        if (screenReaderRunning()) return
        val engine = tts
        if (engine == null) {
            pending = text
            tts = TextToSpeech(appContext, this)
            return
        }
        if (ready) {
            // Flush, like the web: a new ball's text replaces the last one
            // rather than queueing behind it.
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        } else {
            pending = text
        }
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.setLanguage(Locale.getDefault())
            pending?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) }
        }
        pending = null
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        const val UTTERANCE_ID = "cricket-commentary"
    }
}
