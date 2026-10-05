package com.cricketsim.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The real recordings the web app uses. On Android each is looked for in
 * this order:
 *
 *   1. Decrypted from the bundled R.raw.audio_pack (see AudioPack.kt) —
 *      instant, offline, and the only copy of it that ships in the APK
 *      (no individually-named, directly playable file sitting in the app;
 *      see AudioPack.kt's doc comment for exactly what that does and does
 *      not protect against).
 *   2. A copy previously downloaded into the app's private storage.
 *   3. Downloaded now from the web app's host (BASE_URL + path), then kept
 *      for next time. Needs the INTERNET permission.
 *
 * If none of that works, SoundEngine uses a synthesized stand-in
 * (Synth.kt), so the game is never silent.
 *
 * VERIFIED: every path below answers HTTP 200 (audio/mpeg) on the web
 * app's public host, and the paths are copied verbatim from
 * helpers/audioManager.tsx — except the six DRS_* entries, which have no
 * web-app equivalent (Decision Review System is Android-only; see
 * DrsSystem.kt) and were generated straight onto that same host by
 * tools/fetch_audio.sh's ElevenLabs generation pass instead.
 *
 * LICENCE: the crowd recording's file name suggests a third-party stock
 * source, and it is bundled (inside the encrypted pack) in every APK.
 * Check its licence before distributing.
 *
 * The AI commentary clips (`/_cdn/commentary/<id>.mp3`) follow the same
 * order, handled by SoundEngine rather than here: the bundled copy from
 * AudioPack when there is one, else streamed from BASE_URL.
 */
enum class RecordedAsset(val fileName: String, val path: String, val rawName: String) {
    CROWD_BED(
        "crowd_ambience.mp3",
        "/_cdn/static/1dd47f8a-7a8c-4ede-acef-ca28b4493556-arunangshubanerjee-live-football-match-stadium-crowd-cheering-5634.mp3",
        "crowd_ambience"
    ),
    SMALL_CHEER("small_crowd_cheer.mp3", "/_cdn/sfx/small_crowd_cheer.mp3", "small_crowd_cheer"),
    BIG_ROAR("big_crowd_roar.mp3", "/_cdn/sfx/big_crowd_roar.mp3", "big_crowd_roar"),
    COIN_FLIP("coin_flip.mp3", "/_cdn/sfx/coin_flip.mp3", "coin_flip"),
    BAT_HIT(
        "bat_hit.mp3",
        "/_cdn/static/90a609ea-4f7c-42a2-aa68-e04a150e343c-NoiseFree_1788362501831.mp3",
        "bat_hit"
    ),
    RAIN("rain_ambience.mp3", "/_cdn/sfx/rain_ambience.mp3", "rain_ambience"),
    THUNDER("thunder_crack.mp3", "/_cdn/sfx/thunder_crack.mp3", "thunder_crack"),

    // --- Decision Review System (see DrsSystem.kt / DrsScreens.kt) ---
    // Generated via ElevenLabs' sound-generation endpoint, not recorded —
    // "Recorded" here just means "a real audio file", the same as THUNDER
    // and the crowd/rain beds above, all of which SoundEngine treats
    // identically regardless of how they were produced.
    DRS_THIRD_UMPIRE_CHECK("drs_third_umpire_check.mp3", "/_cdn/sfx/drs_third_umpire_check.mp3", "drs_third_umpire_check"),
    DRS_HEARTBEAT("drs_heartbeat.mp3", "/_cdn/sfx/drs_heartbeat.mp3", "drs_heartbeat"),
    DRS_BGM("drs_bgm.mp3", "/_cdn/sfx/drs_bgm.mp3", "drs_bgm"),
    DRS_FIRECRACKER("drs_firecracker.mp3", "/_cdn/sfx/drs_firecracker.mp3", "drs_firecracker"),
    DRS_LOSE("drs_lose.mp3", "/_cdn/sfx/drs_lose.mp3", "drs_lose"),
    DRS_UMPIRES_CALL_AGAINST("drs_umpires_call_against.mp3", "/_cdn/sfx/drs_umpires_call_against.mp3", "drs_umpires_call_against"),

    // --- Drinks break ---
    DRINKS_BREAK("drinks_break_drinking.mp3", "/_cdn/sfx/drinks_break_drinking.mp3", "drinks_break_drinking")
}

sealed interface AssetSource {
    data class Raw(val resId: Int) : AssetSource
    data class Local(val file: File) : AssetSource

    /** Decrypted straight from the bundled pack, held in memory only — see AudioPack.kt. */
    data class Bytes(val bytes: ByteArray) : AssetSource
}

object AudioAssets {
    const val BASE_URL = "https://5764.floot.app"

    /** The commentary library stores root-relative paths; make them fetchable. */
    fun absoluteUrl(path: String): String = if (path.startsWith("http")) path else BASE_URL + path

    /** A copy that is available right now (bundled or already downloaded), or null. Never touches the network. */
    fun find(context: Context, asset: RecordedAsset): AssetSource? {
        AudioPack.bytesFor(context, asset.fileName)?.let { return AssetSource.Bytes(it) }
        // Legacy fallback: an older build's loose named resource, if one
        // somehow still exists. Harmless dead code once the pack is in use
        // (nothing is ever shipped there any more — see tools/fetch_audio.sh).
        val resId = context.resources.getIdentifier(asset.rawName, "raw", context.packageName)
        if (resId != 0) return AssetSource.Raw(resId)
        val file = cacheFile(context, asset)
        return if (file.exists() && file.length() > 0L) AssetSource.Local(file) else null
    }

    /** Like `find`, but downloads the recording first if it isn't available. Null if it can't be had. */
    suspend fun ensure(context: Context, asset: RecordedAsset): AssetSource? {
        find(context, asset)?.let { return it }
        val destination = cacheFile(context, asset)
        return if (download(absoluteUrl(asset.path), destination)) AssetSource.Local(destination) else null
    }

    private fun cacheFile(context: Context, asset: RecordedAsset): File {
        val directory = File(context.filesDir, "audio")
        directory.mkdirs()
        return File(directory, asset.fileName)
    }

    private suspend fun download(url: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 20_000
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext false
                // Written to a temp file first so an interrupted download
                // is never mistaken for a complete recording.
                val temp = File(destination.parentFile, destination.name + ".part")
                connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                if (temp.length() == 0L) {
                    temp.delete()
                    return@withContext false
                }
                if (destination.exists()) destination.delete()
                temp.renameTo(destination)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            false
        }
    }
}
