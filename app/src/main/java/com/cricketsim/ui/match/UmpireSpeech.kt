package com.cricketsim.ui.match

import com.cricketsim.audio.GameServices
import kotlinx.coroutines.delay

/**
 * Plays the umpire's recorded clip(s) in the umpire's own voice (see audio/UmpireLines.kt). If a clip is not in the
 * pack, or sound effects are off, the phone's own speech says `fallback` instead, so a decision is never silent.
 */
fun GameServices.umpireSay(ids: List<String>, fallback: String) {
    if (!sound.playUmpire(*ids.toTypedArray())) announceSpoken(fallback)
}

/** Waits until the umpire has finished speaking (at most `maxMs`), so the next thing starts in step with him. */
suspend fun GameServices.awaitUmpire(maxMs: Long = 9000L) {
    var waited = 0L
    while (sound.umpireVoice.isSpeaking && waited < maxMs) {
        delay(100L)
        waited += 100L
    }
}
