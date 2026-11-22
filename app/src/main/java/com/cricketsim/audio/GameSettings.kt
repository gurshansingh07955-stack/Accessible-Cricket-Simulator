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

/** Who does the fielding (Settings > Manual fielding). */
enum class FieldingMode(val label: String, val description: String) {
    AUTOMATIC("Automatic fielding", "The computer fields for you, as it always has. Its fielders now and then drop a catch, the better the fielder the less often."),
    HALF_MANUAL("Half manual fielding", "You take the catches. Your phone's own voice says who is catching, his fielding rating and where the catch is coming from, then you swipe up, left or right with two fingers on the last buzz."),
    FULL_MANUAL("Full manual fielding", "You take the catches, stop singles and doubles, and do the stumpings and the run outs. Your phone's voice tells you who, where and which swipe: up, left or right for a catch or a stop, down for a stumping, down twice quickly for a run out.")
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
    val crowdVolume: Float = 0.7f,
    // The main-menu music (your mainmenu sound), 0 to 1, independent of the crowd.
    val musicVolume: Float = 0.6f,
    // Batting without gestures: footwork, shot and intent chosen from lists (see BatMenuStep).
    val menuBatting: Boolean = false,
    // "Face next ball" button (on = tap / long-press as before; off = the ball comes by itself after
    // autoBowlSeconds and footwork is chosen by tilting the phone). Ignored while menuBatting is on.
    val faceNextBall: Boolean = true,
    val autoBowlSeconds: Int = 10,
    // Manual fielding: who takes the catches (and, later, stops the ball and stumps).
    val fieldingMode: FieldingMode = FieldingMode.AUTOMATIC,
    // Running between the wickets: on = the user decides each run when he bats (swipe up / down, five seconds); off = the computer decides, as before.
    val manualRunning: Boolean = false,
    // How the user answers a running decision: false = two-finger swipes, true = Run / Stay buttons (only the chosen way is on the screen).
    val runByButtons: Boolean = false,
    // Milestone pop-ups (patch 139): a pop-up with the figures when a batter reaches a 50 or a 100, or a bowler three wickets, a hat-trick... On by default.
    val milestonePopups: Boolean = true
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
            crowdVolume = prefs.getFloat(KEY_CROWD_VOLUME, defaults.crowdVolume).coerceIn(0f, 1f),
            musicVolume = prefs.getFloat(KEY_MUSIC_VOLUME, defaults.musicVolume).coerceIn(0f, 1f),
            menuBatting = prefs.getBoolean(KEY_MENU_BATTING, defaults.menuBatting),
            faceNextBall = prefs.getBoolean(KEY_FACE_NEXT_BALL, defaults.faceNextBall),
            autoBowlSeconds = prefs.getInt(KEY_AUTO_BOWL_SECONDS, defaults.autoBowlSeconds).coerceIn(5, 30),
            fieldingMode = readEnum(KEY_FIELDING_MODE, defaults.fieldingMode),
            manualRunning = prefs.getBoolean(KEY_MANUAL_RUNNING, defaults.manualRunning),
            runByButtons = prefs.getBoolean(KEY_RUN_BY_BUTTONS, defaults.runByButtons),
            milestonePopups = prefs.getBoolean(KEY_MILESTONE_POPUPS, defaults.milestonePopups)
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
            .putFloat(KEY_MUSIC_VOLUME, settings.musicVolume)
            .putBoolean(KEY_MENU_BATTING, settings.menuBatting)
            .putBoolean(KEY_FACE_NEXT_BALL, settings.faceNextBall)
            .putInt(KEY_AUTO_BOWL_SECONDS, settings.autoBowlSeconds)
            .putString(KEY_FIELDING_MODE, settings.fieldingMode.name)
            .putBoolean(KEY_MANUAL_RUNNING, settings.manualRunning)
            .putBoolean(KEY_RUN_BY_BUTTONS, settings.runByButtons)
            .putBoolean(KEY_MILESTONE_POPUPS, settings.milestonePopups)
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
        const val KEY_MUSIC_VOLUME = "music_volume"
        const val KEY_MENU_BATTING = "menu_batting"
        const val KEY_FACE_NEXT_BALL = "face_next_ball"
        const val KEY_AUTO_BOWL_SECONDS = "auto_bowl_seconds"
        const val KEY_FIELDING_MODE = "fielding_mode"
        const val KEY_MANUAL_RUNNING = "manual_running"
        const val KEY_RUN_BY_BUTTONS = "run_by_buttons"
        const val KEY_MILESTONE_POPUPS = "milestone_popups"
    }
}
