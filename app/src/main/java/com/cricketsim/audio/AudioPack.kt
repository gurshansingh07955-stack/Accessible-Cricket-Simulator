package com.cricketsim.audio

import android.content.Context
import android.media.MediaDataSource
import com.cricketsim.BuildConfig
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reads the app's bundled audio out of ONE opaque, encrypted resource
 * (R.raw.audio_pack) instead of shipping separately named, directly
 * playable files. tools/fetch_audio.sh builds that resource: it downloads
 * every recording and commentary clip, concatenates them behind a small
 * JSON index (name -> offset/length, prefixed with its own 4-byte
 * big-endian length), and encrypts the whole thing with AES-256-CBC using
 * the key/IV below (no salt, no key-derivation function — deliberately
 * the simplest possible scheme, with no room for the two sides to derive
 * different bytes, matched exactly by that script's own
 * `openssl enc -aes-256-cbc -K ... -iv ... -nosalt`).
 *
 * ⚠️ HONEST LIMITS — read this before assuming this "secures" the audio:
 * - This stops a CASUAL `unzip app.apk`, `adb pull`, or a generic
 *   APK-asset-scraping tool from handing someone a folder of playable MP3
 *   files, which is what shipping them individually named (as this
 *   project briefly did — see PORTING_NOTES.md) would otherwise do.
 * - It does NOT stop a MOTIVATED person. The key must ship inside the app
 *   to decrypt on-device, so anyone who decompiles the APK (a routine,
 *   well-tooled process) can recover it. This is a real limit of ANY
 *   on-device asset protection, on any platform — not a flaw specific to
 *   this implementation.
 * - Because this project's repository is PUBLIC on GitHub, the default key
 *   below is also sitting in plain text in tools/fetch_audio.sh's own
 *   history: anyone can read it there without ever touching the APK.
 *   Packing still removes the "just unzip it" case, but does not provide
 *   real secrecy while the key ships this way.
 * - The genuinely stronger option: move KEY_HEX/IV_HEX out of the
 *   repository into a GitHub Actions secret used only at CI time (never
 *   committed), and pass the matching values into the Android build as
 *   `-PaudioPackKeyHex=... -PaudioPackIvHex=...` — already wired up in
 *   app/build.gradle.kts, which reads exactly those two Gradle properties
 *   into BuildConfig.AUDIO_PACK_KEY_HEX / AUDIO_PACK_IV_HEX and only falls
 *   back to the committed constants when they are not supplied. Doing
 *   that closes the "read it straight off GitHub" hole; APK decompilation
 *   remains the residual, unavoidable risk either way.
 */
object AudioPack {

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
    }

    private val secretKey by lazy { SecretKeySpec(hexToBytes(BuildConfig.AUDIO_PACK_KEY_HEX), "AES") }
    private val ivSpec by lazy { IvParameterSpec(hexToBytes(BuildConfig.AUDIO_PACK_IV_HEX)) }

    // Populated once, from R.raw.audio_pack. `payload` excludes the index;
    // `index` maps a name (e.g. "crowd_ambience.mp3", "dot_1_excited.mp3",
    // matching RecordedAsset.fileName / a commentary clip's own file name)
    // to its [offset, length] within `payload`. Both stay empty, rather
    // than throwing, if the resource is missing, the key doesn't match, or
    // the pack is corrupt — every caller already treats "this asset isn't
    // bundled" as a normal case and falls back to a synthesized sound or a
    // network download, so a decrypt failure here must never crash the app.
    @Volatile private var payload: ByteArray? = null
    @Volatile private var index: Map<String, IntArray> = emptyMap()
    @Volatile private var attempted = false

    private fun ensureLoaded(context: Context) {
        if (attempted) return
        synchronized(this) {
            if (attempted) return
            attempted = true
            try {
                val resId = context.resources.getIdentifier("audio_pack", "raw", context.packageName)
                if (resId == 0) return
                val encrypted = context.resources.openRawResource(resId).use { it.readBytes() }

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
                val plaintext = cipher.doFinal(encrypted)

                val indexLength = ((plaintext[0].toInt() and 0xFF) shl 24) or
                    ((plaintext[1].toInt() and 0xFF) shl 16) or
                    ((plaintext[2].toInt() and 0xFF) shl 8) or
                    (plaintext[3].toInt() and 0xFF)
                val indexJson = String(plaintext, 4, indexLength, Charsets.UTF_8)
                val obj = JSONObject(indexJson)
                val map = HashMap<String, IntArray>()
                obj.keys().forEach { key ->
                    val entry = obj.getJSONObject(key)
                    map[key] = intArrayOf(entry.getInt("offset"), entry.getInt("length"))
                }
                index = map
                payload = plaintext.copyOfRange(4 + indexLength, plaintext.size)
            } catch (e: Exception) {
                // Missing resource, wrong key, or a corrupt pack: leave
                // payload/index empty, so every lookup below simply misses.
            }
        }
    }

    /**
     * The decrypted bytes for one bundled file, by its exact name (a
     * recording's own file name, e.g. "crowd_ambience.mp3"; a commentary
     * clip's file name, e.g. "dot_1_excited.mp3"). Null if the pack isn't
     * usable, or simply doesn't contain that name (the 30 clips never
     * generated on the web app, in particular).
     */
    fun bytesFor(context: Context, name: String): ByteArray? {
        ensureLoaded(context)
        val data = payload ?: return null
        val (offset, length) = index[name] ?: return null
        if (offset < 0 || length < 0 || offset + length > data.size) return null
        return data.copyOfRange(offset, offset + length)
    }
}

/**
 * Lets MediaPlayer stream straight from a decrypted in-memory buffer, so a
 * looping bed or a commentary clip never needs a plaintext temp file on
 * disk at all — only SoundPool's one-shots do (see SoundEngine.kt), because
 * SoundPool has no in-memory-buffer API of its own.
 */
class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val length = minOf(size, (data.size - position).toInt())
        System.arraycopy(data, position.toInt(), buffer, offset, length)
        return length
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() {}
}
