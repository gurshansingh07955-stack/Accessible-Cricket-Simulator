package com.cricketsim.audio

import android.content.Context
import com.cricketsim.logic.Difficulty

/**
 * The web app's helpers/gameSettings.tsx, minus what doesn't apply on
 * Android. Persisted in SharedPreferences (the equivalent of the web's
 * localStorage key) — this is just settings, not the match itself, so it
 * does not wait on the separate save/resume persistence work.
 *
 * `spokenCommentary` is the web's `commentary` toggle (browser
 * text-to-speech reading each announcement). On Android TalkBack already
 * reads the live regions, so it only speaks when a screen reader is NOT
 * running (see SpokenCommentary) — otherwise every ball would be read
 * twice.
 */
enum class AiCommentaryMode(val label: String, val description: String) {
    OFF("Off", "No voice commentary."),
    DUO("Both commentators", "The excited lead commentator, then the calm co-commentator, like two people talking."),
    EXCITED("Excited commentator only", "Only the energetic lead commentator."),
    CALM("Calm commentator only", "Only the composed co-commentator.")
}

data class GameSettings(
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val vibration: Boolean = true,
    val soundEffects: Boolean = true,
    val spokenCommentary: Boolean = true,
    val aiCommentaryMode: AiCommentaryMode = AiCommentaryMode.DUO,
    // 0 (silent) to 1 (full): an independent multiplier on top of the
    // crowd ambience's own tension/duck/swell gain, so the crowd can be
    // turned down without touching the sound effects.
    val crowdVolume: Float = 0.7f
)

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): GameSettings {
        val defaults = GameSettings()
        return GameSettings(
            difficulty = readEnum(KEY_DIFFICULTY, defaults.difficulty),
            vibration = prefs.getBoolean(KEY_VIBRATION, defaults.vibration),
            soundEffects = prefs.getBoolean(KEY_SOUND_EFFECTS, defaults.soundEffects),
            spokenCommentary = prefs.getBoolean(KEY_SPOKEN_COMMENTARY, defaults.spokenCommentary),
            aiCommentaryMode = readEnum(KEY_AI_COMMENTARY, defaults.aiCommentaryMode),
            crowdVolume = prefs.getFloat(KEY_CROWD_VOLUME, defaults.crowdVolume).coerceIn(0f, 1f)
        )
    }

    fun save(settings: GameSettings) {
        prefs.edit()
            .putString(KEY_DIFFICULTY, settings.difficulty.name)
            .putBoolean(KEY_VIBRATION, settings.vibration)
            .putBoolean(KEY_SOUND_EFFECTS, settings.soundEffects)
            .putBoolean(KEY_SPOKEN_COMMENTARY, settings.spokenCommentary)
            .putString(KEY_AI_COMMENTARY, settings.aiCommentaryMode.name)
            .putFloat(KEY_CROWD_VOLUME, settings.crowdVolume)
            .apply()
    }

    // An unknown or missing stored name (e.g. after an enum is renamed)
    // falls back to the default instead of crashing.
    private inline fun <reified E : Enum<E>> readEnum(key: String, default: E): E {
        val name = prefs.getString(key, null) ?: return default
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }

    private companion object {
        const val PREFS_NAME = "cricket_sim_settings"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_VIBRATION = "vibration"
        const val KEY_SOUND_EFFECTS = "sound_effects"
        const val KEY_SPOKEN_COMMENTARY = "spoken_commentary"
        const val KEY_AI_COMMENTARY = "ai_commentary_mode"
        const val KEY_CROWD_VOLUME = "crowd_volume"
    }
}
