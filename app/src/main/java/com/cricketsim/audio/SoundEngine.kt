package com.cricketsim.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.cricketsim.logic.CommentaryCategory
import com.cricketsim.logic.CommentaryLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * The Android counterpart of helpers/audioManager.tsx and
 * helpers/commentaryVoice.tsx. It is a rewrite around Android's audio
 * APIs, not a translation of Web Audio, but it keeps the web's behaviour
 * and constants:
 *
 * - ONE-SHOT EFFECTS. Recordings (bat hit, cheers, coin flip, thunder)
 *   play through SoundPool; everything else, and every recording's
 *   stand-in, is synthesized PCM (Synth) played through AudioTrack.
 *   Outcome sounds follow the web's mapping: wicket = the wicket sweep
 *   then a big roar 800ms later; a six = boundary arpeggio then a big roar
 *   500ms later; a four/five = boundary then a small cheer; anything else
 *   = bat hit. Each also ducks then swells the crowd exactly as the web
 *   does.
 * - TIMING TICK. A pre-built low-latency AudioTrack per tick, replayed by
 *   rewinding it, so the pulse fires as close to instantly as Android
 *   allows. The accented final pulse is what lets you find the beat by
 *   ear. See INPUT LATENCY below.
 * - AIM TONE. A continuous, non-speech tone used by the pitching/batting
 *   two-finger drag gestures (see PitchingScreen's AimStep) — a short
 *   looping sine (Synth.aimTone) whose PLAYBACK RATE is set live as the
 *   drag moves, exactly the same AudioTrack.setPlaybackRate mechanism the
 *   crowd bed's tension creep already uses, just swept across a much
 *   wider range so the pitch change is obvious rather than subtle. It
 *   never speaks during the drag itself — see updateAimTone's doc
 *   comment for why.
 * - CROWD AMBIENCE. A looping bed whose volume is the product of four
 *   things, recomputed together so they never fight: tension (the crowd's
 *   mood, from match closeness), ducking (a dip under commentary and
 *   effects), swelling (a boost after a boundary or wicket) and the
 *   user's crowd-volume setting; its playback rate also creeps up with
 *   tension. All the web's constants are kept. One improvement: ducking
 *   is a COUNT of things asking for quiet, not a single flag, because on
 *   the web a short effect-duck ending could un-duck the crowd in the
 *   middle of a commentary sequence.
 * - RAIN. A looping rain bed plus a thunder crack, ducking the crowd for
 *   as long as it runs.
 * - COMMENTARY. The AI voice duo clips, queued so a wicket's comment, an
 *   over-complete comment and an innings-break comment play as one
 *   conversation rather than over each other; the crowd ducks for the
 *   whole sequence. Each clip plays from the bundled, encrypted audio pack
 *   (AudioPack.kt) when it contains that clip — instant, offline, straight
 *   from memory via a custom MediaDataSource, no temp file — and otherwise
 *   STREAMS from the web app's host. A clip that can't be played either
 *   way is skipped. There is no text-to-speech fallback for these on
 *   purpose: the same line is already on screen and spoken by TalkBack,
 *   and a second synthetic voice would just talk over it.
 *
 * INPUT LATENCY. Android audio output has latency the web doesn't (tens
 * of milliseconds, device-dependent). The timing score is measured against
 * the SCHEDULED pulse time, so a tick that sounds late makes a player who
 * taps on the sound systematically late. The haptic buzz is unaffected.
 * If that turns out to matter on real devices, compensate with the
 * *_INPUT_LATENCY_COMPENSATION_MS constants in the two timing screens.
 *
 * Everything is on the main thread (MediaPlayer, SoundPool and
 * AudioTrack calls are cheap; asset downloads use withContext).
 */
class SoundEngine(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var settings: GameSettings = GameSettings()
        private set

    private var paused = false

    // --- Synthesized buffers ---

    private enum class Fx { TICK, TICK_ACCENT, BAT_HIT, BOUNDARY, WICKET, WHOOSH, CHEER_BIG, CHEER_SMALL, COIN, THUNDER, RAIN, CROWD, AIM_TONE }

    private val synthCache = HashMap<Fx, ShortArray>()

    private fun pcmFor(fx: Fx): ShortArray = synchronized(synthCache) {
        synthCache.getOrPut(fx) {
            when (fx) {
                Fx.TICK -> Synth.tick(false)
                Fx.TICK_ACCENT -> Synth.tick(true)
                Fx.BAT_HIT -> Synth.batHit()
                Fx.BOUNDARY -> Synth.boundary()
                Fx.WICKET -> Synth.wicket()
                Fx.WHOOSH -> Synth.whoosh()
                Fx.CHEER_BIG -> Synth.cheer(true)
                Fx.CHEER_SMALL -> Synth.cheer(false)
                Fx.COIN -> Synth.coin()
                Fx.THUNDER -> Synth.thunder()
                Fx.RAIN -> Synth.rainLoop()
                Fx.CROWD -> Synth.crowdBed()
                Fx.AIM_TONE -> Synth.aimTone()
            }
        }
    }

    private val effectAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val speechAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private fun buildTrack(pcm: ShortArray, looping: Boolean, lowLatency: Boolean): AudioTrack? = try {
        val builder = AudioTrack.Builder()
            .setAudioAttributes(effectAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Synth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
        if (lowLatency && Build.VERSION.SDK_INT >= 26) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        val track = builder.build()
        track.write(pcm, 0, pcm.size)
        if (looping) track.setLoopPoints(0, pcm.size, -1)
        track
    } catch (e: Exception) {
        null
    }

    // --- Settings, lifecycle ---

    fun applySettings(new: GameSettings) {
        val old = settings
        settings = new
        if (old.soundEffects && !new.soundEffects) {
            stopCrowdAmbience()
            stopRainAmbience()
            stopAimTone()
        }
        if (old.aiCommentaryMode != AiCommentaryMode.OFF && new.aiCommentaryMode == AiCommentaryMode.OFF) {
            stopCommentary()
        }
        if (old.crowdVolume != new.crowdVolume) applyCrowdGain(0.3f)
    }

    /**
     * Builds every synthesized buffer off the main thread, the timing ticks,
     * and starts fetching the recordings. Cheap to call more than once.
     */
    fun warmUp() {
        scope.launch {
            withContext(Dispatchers.Default) { Fx.values().forEach { pcmFor(it) } }
            if (tickTrack == null) tickTrack = buildTrack(pcmFor(Fx.TICK), looping = false, lowLatency = true)
            if (accentTickTrack == null) accentTickTrack = buildTrack(pcmFor(Fx.TICK_ACCENT), looping = false, lowLatency = true)
            for (asset in ONE_SHOT_ASSETS) launch { loadOneShot(asset) }
            launch { AudioAssets.ensure(appContext, RecordedAsset.CROWD_BED) }
            launch { AudioAssets.ensure(appContext, RecordedAsset.RAIN) }
        }
    }

    fun onAppBackgrounded() {
        paused = true
        crowdChannel?.pause()
        rainChannel?.pause()
        stopCommentary()
    }

    fun onAppForegrounded() {
        paused = false
        crowdChannel?.resume()
        rainChannel?.resume()
    }

    fun release() {
        stopCommentary()
        crowdChannel?.stop()
        crowdChannel = null
        rainChannel?.stop()
        rainChannel = null
        aimToneChannel?.stop()
        aimToneChannel = null
        runCatching { tickTrack?.release() }
        runCatching { accentTickTrack?.release() }
        runCatching { soundPool.release() }
        scope.cancel()
    }

    /** Runs `block` after `delayMs`, on the main thread, cancelled with the engine. */
    fun schedule(delayMs: Long, block: () -> Unit) {
        scope.launch {
            delay(delayMs)
            block()
        }
    }

    // --- Recorded one-shots (SoundPool) ---

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(effectAttributes)
        .build()
    private val soundIds = HashMap<RecordedAsset, Int>()
    private val loadedSoundIds = HashSet<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(loadedSoundIds) { loadedSoundIds.add(sampleId) }
        }
    }

    private suspend fun loadOneShot(asset: RecordedAsset) {
        val source = AudioAssets.ensure(appContext, asset) ?: return
        val id = when (source) {
            is AssetSource.Raw -> soundPool.load(appContext, source.resId, 1)
            is AssetSource.Local -> soundPool.load(source.file.absolutePath, 1)
            is AssetSource.Bytes -> loadBytesIntoSoundPool(source.bytes)
        }
        soundIds[asset] = id
    }

    /**
     * SoundPool only loads from a path or file descriptor, never from a
     * plain byte array, so the decrypted bytes are written to a short-lived
     * temp file, loaded, then removed — SoundPool copies the audio into its
     * own buffers once loaded, so the temp file is only needed for that
     * brief window, and this is the only place a plaintext copy of any
     * bundled sound ever touches disk.
     */
    private suspend fun loadBytesIntoSoundPool(bytes: ByteArray): Int = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("sfx_", ".mp3", appContext.cacheDir)
        temp.writeBytes(bytes)
        val id = soundPool.load(temp.absolutePath, 1)
        scope.launch {
            delay(5_000)
            runCatching { temp.delete() }
        }
        id
    }

    /** Plays a recording if it has loaded. Returns false if it isn't ready (so the caller can use a stand-in). */
    private fun playRecorded(asset: RecordedAsset, gain: Float): Boolean {
        val id = soundIds[asset] ?: return false
        val ready = synchronized(loadedSoundIds) { id in loadedSoundIds }
        if (!ready) return false
        soundPool.play(id, gain, gain, 1, 0, 1f)
        return true
    }

    // --- Synthesized one-shots ---

    private fun playPcm(fx: Fx, gain: Float) {
        val pcm = pcmFor(fx)
        val track = buildTrack(pcm, looping = false, lowLatency = false) ?: return
        runCatching {
            track.setVolume(gain)
            track.play()
        }
        val lifetimeMs = pcm.size * 1000L / Synth.SAMPLE_RATE + 300L
        scope.launch {
            delay(lifetimeMs)
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    // --- The timing tick ---

    private var tickTrack: AudioTrack? = null
    private var accentTickTrack: AudioTrack? = null

    /** One pulse of the batting/pitching rhythm. The accent is the final pulse. */
    fun playTimingTick(accent: Boolean) {
        if (!settings.soundEffects) return
        val track = (if (accent) accentTickTrack else tickTrack) ?: return
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.setVolume(1f)
            track.play()
        }
    }

    // --- Match effects ---

    fun playBallDelivery() {
        if (!settings.soundEffects) return
        playPcm(Fx.WHOOSH, MASTER)
    }

    /** The sound of what just happened on the ball, with the crowd's reaction. Web mapping, see the class comment. */
    fun playOutcomeSounds(isWicket: Boolean, runs: Int) {
        if (!settings.soundEffects) return
        when {
            isWicket -> {
                playPcm(Fx.WICKET, MASTER)
                duckFor(300)
                swellCrowd(2200)
                schedule(800) { playCheer(big = true) }
            }
            runs == 6 -> {
                playPcm(Fx.BOUNDARY, MASTER)
                duckFor(180)
                swellCrowd(2400)
                schedule(500) { playCheer(big = true) }
            }
            runs >= 4 -> {
                playPcm(Fx.BOUNDARY, MASTER)
                duckFor(180)
                swellCrowd(2400)
                schedule(500) { playCheer(big = false) }
            }
            else -> {
                if (!playRecorded(RecordedAsset.BAT_HIT, 0.9f * MASTER)) playPcm(Fx.BAT_HIT, MASTER)
                duckFor(250)
            }
        }
    }

    private fun playCheer(big: Boolean) {
        if (!settings.soundEffects) return
        val recorded = if (big) RecordedAsset.BIG_ROAR else RecordedAsset.SMALL_CHEER
        val gain = (if (big) 0.85f else 0.8f) * MASTER
        if (!playRecorded(recorded, gain)) playPcm(if (big) Fx.CHEER_BIG else Fx.CHEER_SMALL, MASTER)
    }

    fun playCoinFlip() {
        if (!settings.soundEffects) return
        if (!playRecorded(RecordedAsset.COIN_FLIP, 0.8f * MASTER)) playPcm(Fx.COIN, MASTER)
    }

    private fun playThunder() {
        if (!playRecorded(RecordedAsset.THUNDER, 0.75f * MASTER)) playPcm(Fx.THUNDER, 0.75f * MASTER)
    }

    // --- Looping channels (crowd, rain, aim tone) ---

    private interface LoopChannel {
        fun start()
        fun pause()
        fun resume()
        fun stop()
        fun setVolume(volume: Float)
        fun setRate(rate: Float)
    }

    private class SynthLoop(private val track: AudioTrack) : LoopChannel {
        private val baseRate = track.playbackRate
        override fun start() { runCatching { track.play() } }
        override fun pause() { runCatching { track.pause() } }
        override fun resume() { runCatching { track.play() } }
        override fun stop() {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        override fun setVolume(volume: Float) { runCatching { track.setVolume(volume) } }
        override fun setRate(rate: Float) { runCatching { track.setPlaybackRate((baseRate * rate).toInt()) } }
    }

    private class MediaLoop(private val player: MediaPlayer) : LoopChannel {
        private var prepared = false
        private var wantPlaying = false

        fun onPrepared() {
            prepared = true
            if (wantPlaying) runCatching { player.start() }
        }

        override fun start() {
            wantPlaying = true
            if (prepared) runCatching { player.start() }
        }
        override fun pause() {
            wantPlaying = false
            if (prepared) runCatching { if (player.isPlaying) player.pause() }
        }
        override fun resume() = start()
        override fun stop() {
            wantPlaying = false
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        override fun setVolume(volume: Float) { runCatching { player.setVolume(volume, volume) } }
        override fun setRate(rate: Float) {
            // Setting a non-zero speed on a MediaPlayer also STARTS it, so
            // only touch it while it is genuinely playing.
            if (prepared) runCatching {
                if (player.isPlaying) player.playbackParams = player.playbackParams.setSpeed(rate)
            }
        }
    }

    private fun synthLoop(fx: Fx): LoopChannel? =
        buildTrack(pcmFor(fx), looping = true, lowLatency = false)?.let { SynthLoop(it) }

    private fun mediaLoop(source: AssetSource, onError: () -> Unit): LoopChannel? {
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(effectAttributes)
            when (source) {
                is AssetSource.Raw -> appContext.resources.openRawResourceFd(source.resId).use { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                is AssetSource.Local -> player.setDataSource(source.file.absolutePath)
                is AssetSource.Bytes -> player.setDataSource(ByteArrayMediaDataSource(source.bytes))
            }
            player.isLooping = true
            player.setVolume(0f, 0f)
            val loop = MediaLoop(player)
            player.setOnPreparedListener { loop.onPrepared() }
            player.setOnErrorListener { _, _, _ ->
                onError()
                true
            }
            player.prepareAsync()
            loop
        } catch (e: Exception) {
            runCatching { player.release() }
            null
        }
    }

    /** Ramps a value to a target over a duration on the main thread; a new ramp replaces the old one. */
    private class Ramper(private val scope: CoroutineScope, private val apply: (Float) -> Unit) {
        var current = 0f
            private set
        private var job: Job? = null

        fun set(value: Float) {
            job?.cancel()
            current = value
            apply(value)
        }

        fun rampTo(target: Float, seconds: Float) {
            job?.cancel()
            val from = current
            val steps = max(1, (seconds * 1000f / STEP_MS).toInt())
            job = scope.launch {
                for (step in 1..steps) {
                    delay(STEP_MS)
                    current = from + (target - from) * step / steps
                    apply(current)
                }
            }
        }

        fun cancel() {
            job?.cancel()
        }

        private companion object {
            const val STEP_MS = 30L
        }
    }

    // --- Crowd ambience ---

    private var crowdChannel: LoopChannel? = null
    private var crowdRamp: Ramper? = null
    private var tension = 0f
    private var duckHolds = 0
    private var swellUntilMs = 0L
    private var swellJob: Job? = null

    private fun crowdTarget(): Float {
        val inSwell = SystemClock.elapsedRealtime() < swellUntilMs
        val baseline = CROWD_BASE_GAIN_CALM + (CROWD_BASE_GAIN_TENSE - CROWD_BASE_GAIN_CALM) * tension
        val gain = if (inSwell) CROWD_SWELL_GAIN else baseline * (if (duckHolds > 0) CROWD_DUCK_FACTOR else 1f)
        return gain * settings.crowdVolume * MASTER
    }

    private fun applyCrowdGain(rampSeconds: Float) {
        crowdRamp?.rampTo(crowdTarget(), max(0.01f, rampSeconds))
    }

    fun startCrowdAmbience() {
        if (!settings.soundEffects || crowdChannel != null) return
        launchCrowd(useSynth = false)
    }

    private fun launchCrowd(useSynth: Boolean) {
        swellJob?.cancel()
        swellUntilMs = 0L

        val source = if (useSynth) null else AudioAssets.find(appContext, RecordedAsset.CROWD_BED)
        var channel: LoopChannel? = null
        if (source != null) {
            channel = mediaLoop(source) {
                // The recording wouldn't play: fall back to the synthesized bed.
                scope.launch {
                    if (crowdChannel === channel) {
                        stopCrowdNow()
                        launchCrowd(useSynth = true)
                    }
                }
            }
        }
        val active = channel ?: synthLoop(Fx.CROWD) ?: return
        val ramp = Ramper(scope) { active.setVolume(it) }
        crowdChannel = active
        crowdRamp = ramp
        ramp.set(0f)
        if (!paused) active.start()
        applyCrowdGain(2.5f) // fade in to the current baseline/duck/swell state
        active.setRate(0.97f + 0.08f * tension)
    }

    private fun stopCrowdNow() {
        crowdRamp?.cancel()
        crowdRamp = null
        crowdChannel?.stop()
        crowdChannel = null
    }

    fun stopCrowdAmbience() {
        val channel = crowdChannel ?: return
        val ramp = crowdRamp
        crowdChannel = null
        crowdRamp = null
        swellJob?.cancel()
        swellUntilMs = 0L
        ramp?.rampTo(0f, 1f)
        scope.launch {
            delay(1100)
            channel.stop()
        }
    }

    /** 0 = a decided, one-sided match; 1 = a nail-biter. Ramped slowly, like a crowd's mood. */
    fun setCrowdTension(fraction: Float) {
        tension = fraction.coerceIn(0f, 1f)
        if (crowdChannel == null) return
        applyCrowdGain(2.5f)
        crowdChannel?.setRate(0.97f + 0.08f * tension)
    }

    /** Dip the crowd so commentary or an effect is heard clearly. Every start needs one matching duckEnd(). */
    fun duckStart() {
        duckHolds++
        applyCrowdGain(0.12f)
    }

    fun duckEnd() {
        duckHolds = max(0, duckHolds - 1)
        applyCrowdGain(0.6f)
    }

    private fun duckFor(durationMs: Long) {
        duckStart()
        scope.launch {
            delay(durationMs)
            duckEnd()
        }
    }

    /** Lift the crowd above baseline for a moment, then let it settle: the crowd reacting. */
    fun swellCrowd(durationMs: Long = 1800) {
        swellUntilMs = SystemClock.elapsedRealtime() + durationMs
        applyCrowdGain(0.15f)
        swellJob?.cancel()
        swellJob = scope.launch {
            delay(durationMs)
            applyCrowdGain(1.4f)
        }
    }

    // --- Rain ---

    private var rainChannel: LoopChannel? = null
    private var rainRamp: Ramper? = null
    private var rainActive = false

    /** A thunder crack and a looping rain bed; the crowd ducks for as long as it runs. */
    fun startRainAmbience() {
        if (!settings.soundEffects || rainActive) return
        rainActive = true
        duckStart()
        playThunder()

        val source = AudioAssets.find(appContext, RecordedAsset.RAIN)
        var channel: LoopChannel? = null
        if (source != null) channel = mediaLoop(source) { /* falls silent; the rain screen still says it is raining */ }
        val active = channel ?: synthLoop(Fx.RAIN) ?: return
        val ramp = Ramper(scope) { active.setVolume(it) }
        rainChannel = active
        rainRamp = ramp
        ramp.set(0f)
        if (!paused) active.start()
        ramp.rampTo(RAIN_GAIN * MASTER, 1.5f)
    }

    fun stopRainAmbience() {
        if (!rainActive) return
        rainActive = false
        duckEnd()
        val channel = rainChannel ?: return
        val ramp = rainRamp
        rainChannel = null
        rainRamp = null
        ramp?.rampTo(0f, 1f)
        scope.launch {
            delay(1100)
            channel.stop()
        }
    }

    // --- Aim tone (pitching/batting two-finger gesture feedback) ---

    private var aimToneChannel: LoopChannel? = null

    /**
     * Starts the continuous aim-drag tone, silent until the first
     * updateAimTone call. Ducks the crowd for as long as it plays, same as
     * any other effect — the point of a drag gesture is to hear the tone
     * clearly, not the ambience under it. Safe to call again while already
     * running (a no-op).
     */
    fun startAimTone() {
        if (!settings.soundEffects || aimToneChannel != null) return
        val channel = synthLoop(Fx.AIM_TONE) ?: return
        aimToneChannel = channel
        duckStart()
        channel.setVolume(0f)
        if (!paused) channel.start()
    }

    /**
     * Sets the tone's pitch live from a 0..1 drag position, linearly across
     * AIM_TONE_MIN_RATE..AIM_TONE_MAX_RATE. Called continuously as a
     * two-finger drag moves — deliberately the ONLY feedback during the
     * drag itself (see the gesture redesign plan's core principle: a
     * continuous tone during the drag, a spoken announcement only when the
     * screen's own zone-crossing logic decides one is warranted — never
     * speech driven directly off every touch-move event, which would
     * overrun TalkBack's speech queue and stutter). A no-op if
     * startAimTone was never called or has already been stopped.
     */
    fun updateAimTone(fraction: Float) {
        val channel = aimToneChannel ?: return
        val clamped = fraction.coerceIn(0f, 1f)
        channel.setRate(AIM_TONE_MIN_RATE + (AIM_TONE_MAX_RATE - AIM_TONE_MIN_RATE) * clamped)
        channel.setVolume(AIM_TONE_GAIN * MASTER)
    }

    fun stopAimTone() {
        val channel = aimToneChannel ?: return
        aimToneChannel = null
        channel.stop()
        duckEnd()
    }

    // --- AI voice commentary ---

    // Root-relative paths as stored in the commentary library, e.g.
    // "/_cdn/commentary/dot_1_excited.mp3". Resolved to a bundled asset or a
    // URL only when a clip is about to play.
    private val commentaryQueue = ArrayDeque<String>()
    private var commentaryPlayer: MediaPlayer? = null
    private var commentaryPlaying = false
    private var commentaryToken = 0

    /**
     * Queues the duo banter for an event, respecting the commentary mode,
     * and starts playing if nothing is. Both voices play in order (excited
     * lead, then calm co-commentator) unless a single-voice mode is set.
     */
    fun enqueueCommentary(category: CommentaryCategory) {
        val mode = settings.aiCommentaryMode
        if (mode == AiCommentaryMode.OFF) return
        val pair = CommentaryLibrary.getRandomCommentaryPair(category)
        val paths = mutableListOf<String>()
        if (mode == AiCommentaryMode.DUO || mode == AiCommentaryMode.EXCITED) {
            pair.excited.audioUrl?.let { paths.add(it) }
        }
        if (mode == AiCommentaryMode.DUO || mode == AiCommentaryMode.CALM) {
            pair.calm.audioUrl?.let { paths.add(it) }
        }
        if (paths.isEmpty()) return
        commentaryQueue.addAll(paths)
        if (!commentaryPlaying) playNextCommentary()
    }

    private fun releaseCommentaryPlayer() {
        commentaryPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        commentaryPlayer = null
    }

    private fun playNextCommentary() {
        releaseCommentaryPlayer()
        val path = commentaryQueue.removeFirstOrNull()
        if (path == null) {
            // The whole sequence has finished: let the crowd come back up.
            if (commentaryPlaying) duckEnd()
            commentaryPlaying = false
            return
        }
        if (!commentaryPlaying) {
            // First clip of a fresh sequence: duck for the whole sequence, not one clip.
            commentaryPlaying = true
            duckStart()
        }

        val token = ++commentaryToken
        var started = false
        val fileName = path.substringAfterLast('/')
        val player = MediaPlayer()
        commentaryPlayer = player
        try {
            player.setAudioAttributes(speechAttributes)
            val bundledBytes = AudioPack.bytesFor(appContext, fileName)
            if (bundledBytes != null) {
                // The copy inside the encrypted pack: instant and offline.
                player.setDataSource(ByteArrayMediaDataSource(bundledBytes))
            } else {
                // Not bundled (one of the 30 clips never generated on the web app): stream it.
                player.setDataSource(AudioAssets.absoluteUrl(path))
            }
            player.setVolume(COMMENTARY_VOLUME, COMMENTARY_VOLUME)
            player.setOnPreparedListener {
                started = true
                it.start()
            }
            player.setOnCompletionListener { if (token == commentaryToken) playNextCommentary() }
            player.setOnErrorListener { _, _, _ ->
                if (token == commentaryToken) playNextCommentary()
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            playNextCommentary()
            return
        }
        // A clip that never starts (offline, a hung connection) must not
        // leave the crowd ducked and the queue stuck forever.
        scope.launch {
            delay(COMMENTARY_START_TIMEOUT_MS)
            if (token == commentaryToken && !started) playNextCommentary()
        }
    }

    /** Clears queued and playing commentary, so it never lags behind the game. */
    fun stopCommentary() {
        commentaryToken++
        commentaryQueue.clear()
        releaseCommentaryPlayer()
        if (commentaryPlaying) duckEnd()
        commentaryPlaying = false
    }

    // --- Vibration ---

    private val vibrator: Vibrator? = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    /** A pattern in milliseconds, on-off-on-... as the web's navigator.vibrate takes it. */
    @Suppress("DEPRECATION")
    fun vibrate(vararg pattern: Long) {
        if (!settings.vibration) return
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        val timings = longArrayOf(0L) + pattern
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                device.vibrate(VibrationEffect.createWaveform(timings, -1))
            } else {
                device.vibrate(timings, -1)
            }
        }
    }

    private companion object {
        // The web's master gain. Applied to effects and the crowd, not to the
        // timing tick (a functional cue) or to commentary (which has its own).
        const val MASTER = 0.5f

        const val CROWD_BASE_GAIN_CALM = 0.09f
        const val CROWD_BASE_GAIN_TENSE = 0.2f
        const val CROWD_SWELL_GAIN = 0.4f
        const val CROWD_DUCK_FACTOR = 0.32f
        const val RAIN_GAIN = 0.45f
        const val COMMENTARY_VOLUME = 0.85f
        const val COMMENTARY_START_TIMEOUT_MS = 8_000L

        // The aim tone's playback-rate sweep — a wide range so the pitch
        // change across a drag is obvious, not subtle. Unmeasured on a real
        // device yet; if a drag feels like it needs finer resolution at one
        // end, narrow the range or make the mapping non-linear rather than
        // widening it further.
        const val AIM_TONE_MIN_RATE = 0.6f
        const val AIM_TONE_MAX_RATE = 2.4f
        const val AIM_TONE_GAIN = 0.55f

        val ONE_SHOT_ASSETS = listOf(
            RecordedAsset.BAT_HIT,
            RecordedAsset.SMALL_CHEER,
            RecordedAsset.BIG_ROAR,
            RecordedAsset.COIN_FLIP,
            RecordedAsset.THUNDER
        )
    }
}
