package com.cricketsim.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The one place the app's settings and sound live, created once by
 * MainActivity and handed down through LocalGameServices so the screens
 * that need it (the timing minigames, the toss, the match, settings) don't
 * each need new parameters threaded through the whole flow.
 *
 * `settings` is Compose state, so anything reading it recomposes when the
 * user changes a setting; every change is also saved and pushed into the
 * SoundEngine immediately.
 */
class GameServices(context: Context) {
    private val store = SettingsStore(context)

    var settings: GameSettings by mutableStateOf(store.load())
        private set

    val sound = SoundEngine(context)
    private val spoken = SpokenCommentary(context)

    init {
        sound.applySettings(settings)
        sound.warmUp()
    }

    fun update(transform: (GameSettings) -> GameSettings) {
        val updated = transform(settings)
        settings = updated
        store.save(updated)
        sound.applySettings(updated)
    }

    fun resetSettings() = update { GameSettings() }

    /** Reads `text` aloud with text-to-speech if that setting is on and no screen reader is running. */
    fun announceSpoken(text: String) {
        if (settings.spokenCommentary) spoken.speak(text)
    }

    fun release() {
        sound.release()
        spoken.shutdown()
    }
}

/** Null when nothing provided one (e.g. a preview); every use treats that as "no audio, default settings". */
val LocalGameServices = staticCompositionLocalOf<GameServices?> { null }
