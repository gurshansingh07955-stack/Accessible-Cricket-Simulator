package com.cricketsim.audio

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.cricketsim.persistence.MatchSaveStore

/**
 * The one place the app's settings, sound and saved match live, created
 * once by MainActivity and handed down through LocalGameServices so the
 * screens that need it (the timing minigames, the toss, the match,
 * settings, the first screen's Resume button) don't each need new
 * parameters threaded through the whole flow.
 *
 * `settings` is Compose state, so anything reading it recomposes when the
 * user changes a setting; every change is also saved and pushed into the
 * SoundEngine immediately.
 *
 * WHY announceSpoken USED TO BE SILENT UNDER TALKBACK. It called
 * SpokenCommentary.speak(), which returns immediately whenever touch
 * exploration is on — by design, since the web's "spoken commentary" is a
 * TTS narration for someone NOT using a screen reader, and running both
 * at once would read everything twice, in two voices, on top of each
 * other (see SpokenCommentary's own doc comment). That's the right call
 * for match commentary, which the on-screen text's own liveRegion already
 * covers reliably enough on its own — a full sentence changing once per
 * ball gives TalkBack plenty to work with. It was the WRONG call for
 * PitchingScreen's per-gesture feedback (a swipe changing angle, line,
 * variation or length), which relied on this same call as its ONLY
 * explicit announcement and got nothing at all under TalkBack, leaving
 * a shared, rapidly-changing liveRegion Text as the sole remaining
 * channel — and Android's accessibility event pipeline coalesces
 * closely-spaced content-changed events on the same node, so a quick
 * swipe (or two swipes close together) could easily have its
 * announcement silently dropped in favor of a later one, or never
 * dispatched at all if the timing lined up wrong. That is what "the
 * gesture worked but it didn't speak" actually was.
 *
 * Fixed by giving announceSpoken a SECOND, direct channel to TalkBack —
 * posting a TYPE_ANNOUNCEMENT AccessibilityEvent straight to the
 * AccessibilityManager, the same primitive View.announceForAccessibility()
 * wraps — which bypasses live-region diffing/coalescing entirely and is
 * the standard, punctual way to say "announce exactly this, right now."
 * This path is deliberately UNCONDITIONAL, not gated behind
 * settings.spokenCommentary: that setting is the separate, optional
 * sighted-user TTS narration described above, and gating a TalkBack
 * user's core gesture feedback behind it would silence it entirely for
 * anyone who ever turned that setting off. The TTS path is unchanged and
 * stays exactly as before for when no screen reader is running.
 */
class GameServices(context: Context) {
    private val appContext = context.applicationContext
    private val store = SettingsStore(appContext)
    private val accessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

    var settings: GameSettings by mutableStateOf(store.load())
        private set

    val sound = SoundEngine(context)
    private val spoken = SpokenCommentary(context)

    /** The match in progress, autosaved by the match screen (see MatchSaveStore). */
    val saves = MatchSaveStore(context)

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

    private fun screenReaderRunning(): Boolean = accessibilityManager?.isTouchExplorationEnabled == true

    /**
     * Announces `text` right now, through whichever channel actually
     * reaches the player — see the class doc comment's "WHY
     * announceSpoken USED TO BE SILENT UNDER TALKBACK" for the bug this
     * fixes. Under TalkBack, a direct AccessibilityEvent always fires,
     * unconditionally; otherwise, falls back to the existing
     * spokenCommentary-gated TTS behavior, unchanged.
     */
    @Suppress("DEPRECATION") // AccessibilityEvent.obtain(int) still the correct call across all supported API levels.
    fun announceSpoken(text: String) {
        if (screenReaderRunning()) {
            val manager = accessibilityManager ?: return
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            event.text.add(text)
            event.className = GameServices::class.java.name
            event.packageName = appContext.packageName
            runCatching { manager.sendAccessibilityEvent(event) }
        } else if (settings.spokenCommentary) {
            spoken.speak(text)
        }
    }

    fun release() {
        sound.release()
        spoken.shutdown()
    }
}

/** Null when nothing provided one (e.g. a preview); every use treats that as "no audio, default settings, no saves". */
val LocalGameServices = staticCompositionLocalOf<GameServices?> { null }
