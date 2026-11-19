package com.cricketsim.ui.match

import com.cricketsim.audio.SoundEngine
import com.cricketsim.logic.CoinSide
import com.cricketsim.logic.MatchReferees
import com.cricketsim.logic.TossDecision
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The toss presentation (patch 132), only in the career matches of international series and tournaments and of franchise leagues. A presenter (an excited,
 * authoritative commentator's voice) welcomes everyone, names the match referee and invites the captain to call heads or tails (or the user to flip the coin);
 * the caller says "Heads!" or "Tails!"; the presenter says what the coin showed and asks the captain who won the toss for his decision; he answers (bat or bowl);
 * the presenter asks the other captain how he sees it; he answers; the presenter closes. The user's captain speaks in the player's voice of the press conference,
 * the opposition captain in another voice. All recorded (web app endpoint toss_tts_generate, fetched by tools/fetch_audio.sh, ids from tools/toss_clip_ids.py),
 * through the press voice channel of [SoundEngine]. [create] gives null when the clips are not in the audio pack: the toss then goes on exactly as before.
 */
class TossShow private constructor(private val sound: SoundEngine, private val refereeNumber: Int) {

    private fun pick(prefix: String, n: Int): String = "${prefix}_${Random.nextInt(n) + 1}"

    /** The welcome, the match referee, and the invitation to call the coin (or, when the user flips it, to flip it). */
    fun opening(userFlips: Boolean): List<String> =
        listOf(pick("toss_w", 4), "toss_ref_$refereeNumber", pick(if (userFlips) "toss_inv_flip" else "toss_inv_call", 3))

    /** The call (in the voice of whoever called), what the coin showed, and the question to the captain who won. */
    fun afterCoin(callerIsUser: Boolean, call: CoinSide, coin: CoinSide, userWon: Boolean): List<String> {
        val who = if (callerIsUser) "user" else "opp"
        val callWord = if (call == CoinSide.HEADS) "heads" else "tails"
        val result = if (coin == CoinSide.HEADS) pick("toss_res_h", 2) else pick("toss_res_t", 2)
        return listOf("toss_${who}_$callWord", result, pick(if (userWon) "toss_qw_user" else "toss_qw_opp", 3))
    }

    /** The winner's answer (his decision), the presenter's question to the other captain, his answer, and the closing. */
    fun afterDecision(userWon: Boolean, decision: TossDecision): List<String> {
        val winner = if (userWon) "user" else "opp"
        val loser = if (userWon) "opp" else "user"
        val word = if (decision == TossDecision.BAT) "bat" else "bowl"
        return listOf(pick("toss_${winner}_$word", 2), pick("toss_qs_$loser", 3), pick("toss_${loser}_lose", 3), pick("toss_end", 2))
    }

    /** Plays these clips one after the other now, cutting off what was playing. */
    fun play(ids: List<String>) {
        sound.playToss(ids)
    }

    /** Plays these clips after what is already playing. */
    fun queue(ids: List<String>) {
        sound.queueToss(ids)
    }

    fun stop() {
        sound.stopPressVoice()
    }

    /** Waits until the voices have finished (or [maxMillis] has passed). */
    suspend fun waitUntilDone(maxMillis: Long = 90_000L) {
        delay(250L)
        var waited = 0L
        while (sound.isPressSpeaking() && waited < maxMillis) {
            delay(100L)
            waited += 100L
        }
    }

    companion object {
        /** The show for this match referee, or null (no referee given, sound effects off, or the clips are not in the audio pack): then nothing is added to the toss. */
        fun create(sound: SoundEngine?, referee: String?): TossShow? {
            if (sound == null || referee == null) return null
            val n = MatchReferees.indexOf(referee)
            if (n <= 0 || !sound.hasPressClip("toss_w_1") || !sound.hasPressClip("toss_ref_$n")) return null
            return TossShow(sound, n)
        }
    }
}
