package com.chamika.dashtune.media

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import org.jellyfin.sdk.model.api.AudioSpatialFormat
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.Locale

/**
 * Dolby Atmos and Dolby Audio capability detection, container selection, and AAOS content-format
 * attribution per the Dolby Android Automotive OS developer guide.
 */
@OptIn(UnstableApi::class)
object DolbySupport {

    /**
     * AAOS MediaConstants / MetadataExtras key indicating the track is mixed in an immersive audio
     * format (set to [METADATA_VALUE_ATTRIBUTE_PRESENT] for Dolby Atmos only).
     */
    const val KEY_IMMERSIVE_AUDIO = "androidx.car.app.mediaextensions.KEY_IMMERSIVE_AUDIO"

    /** Value for [KEY_IMMERSIVE_AUDIO] indicating the attribute is present. */
    const val METADATA_VALUE_ATTRIBUTE_PRESENT = 1L

    /**
     * AAOS MetadataExtras key for the tintable large format logo URI (horizontal Dolby Atmos or
     * Dolby Audio logo).
     */
    const val KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI =
        "androidx.car.app.mediaextensions.KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI"

    /**
     * AAOS MetadataExtras key for the tintable small format badge URI (Dolby Double-D symbol).
     */
    const val KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI =
        "androidx.car.app.mediaextensions.KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI"

    const val DOLBY_ATMOS_LARGE_ICON_URI =
        "android.resource://com.chamika.dashtune/drawable/dlb_atms_horz"
    const val DOLBY_AUDIO_LARGE_ICON_URI =
        "android.resource://com.chamika.dashtune/drawable/dlb_audio_horz"
    const val DOLBY_SMALL_ICON_URI =
        "android.resource://com.chamika.dashtune/drawable/dlb_dd_glyph"

    internal const val AC4_LEVEL4_PARAM = "isAc4Level4Supported"
    internal const val AC4_LEVEL4_SUPPORTED_RESPONSE = "isAc4Level4Supported=1"
    internal const val AC4_LEVEL4_CHANNEL_COUNT = 21

    /**
     * Containers direct-streamed regardless of Dolby decoder presence (includes audiobook `.m4b`,
     * `.mp4`, and Matroska `.mka`/`.mkv` containers that commonly package E-AC-3 or AAC chapters).
     */
    val BASE_CONTAINERS: List<String> = listOf(
        "flac",
        "mp3",
        "m4a",
        "aac",
        "ogg",
        "m4b",
        "mp4",
        "mka",
        "mkv",
    )

    /**
     * Full direct-stream container list when all Dolby codecs (`eac3`, `ec3`, `ac3`, `ac4`) are
     * supported by the vehicle.
     */
    val ALL_CONTAINERS: List<String> = listOf(
        "flac",
        "mp3",
        "m4a",
        "aac",
        "ogg",
        "m4b",
        "mp4",
        "eac3",
        "ec3",
        "ac3",
        "ac4",
        "mka",
        "mkv",
    )

    enum class DolbyFormat {
        NONE,
        DOLBY_AUDIO,
        DOLBY_ATMOS,
    }

    data class Capabilities(
        val supportsEac3Joc: Boolean = false,
        val supportsEac3: Boolean = false,
        val supportsAc3: Boolean = false,
        val supportsAc4: Boolean = false,
        val supportsAc4Level4: Boolean = false,
    ) {
        val supportsDolbyAtmos: Boolean
            get() = supportsEac3Joc || supportsAc4

        val supportsDolbyAudio: Boolean
            get() = supportsEac3 || supportsAc3 || supportsDolbyAtmos

        /**
         * Returns true if [codec] (from Jellyfin's `MediaStream.codec` or container name) can be
         * decoded by the vehicle, or if [codec] is a non-Dolby codec handled by standard Android
         * extractors/decoders.
         */
        fun supportsCodec(codec: String?): Boolean =
            when (codec?.lowercase(Locale.ROOT)?.trim()) {
                "eac3", "ec3", "ec-3", "eac3-joc", "ec+3" -> supportsEac3 || supportsEac3Joc
                "ac3", "ac-3" -> supportsAc3 || supportsEac3 || supportsEac3Joc
                "ac4", "ac-4" -> supportsAc4
                "truehd", "mlp" -> false
                else -> true
            }
    }

    /**
     * Explicit media [AudioAttributes] matching the player configuration in
     * [com.chamika.dashtune.DashTuneMusicService] so AAOS queries the media audio zone rather than
     * `USAGE_UNKNOWN`.
     */
    val MEDIA_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    /**
     * Detects Dolby Atmos and Dolby Audio playback support on the vehicle using both ExoPlayer
     * pass-through ([AudioCapabilities]) and platform/OEM codec list ([MediaCodecUtil]),
     * following the Dolby AAOS integration guide.
     */
    fun detectCapabilities(context: Context): Capabilities {
        var passthroughEac3Joc = false
        var passthroughEac3 = false
        var passthroughAc3 = false
        var passthroughAc4 = false
        var passthroughAc4Level4 = false

        try {
            val audioCaps = AudioCapabilities.getCapabilities(context, MEDIA_AUDIO_ATTRIBUTES, null)
            passthroughEac3Joc = audioCaps.supportsEncoding(C.ENCODING_E_AC3_JOC)
            passthroughEac3 = audioCaps.supportsEncoding(C.ENCODING_E_AC3) || passthroughEac3Joc
            passthroughAc3 = audioCaps.supportsEncoding(C.ENCODING_AC3)
            passthroughAc4 = audioCaps.supportsEncoding(C.ENCODING_AC4)
            if (passthroughAc4) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                passthroughAc4Level4 =
                    audioManager?.getParameters(AC4_LEVEL4_PARAM) == AC4_LEVEL4_SUPPORTED_RESPONSE
            }
        } catch (_: Throwable) {
            // Fall through to MediaCodec check if AudioCapabilities query fails.
        }

        val codecEac3Joc = hasMediaCodecDecoder(MimeTypes.AUDIO_E_AC3_JOC)
        val codecEac3 = hasMediaCodecDecoder(MimeTypes.AUDIO_E_AC3) || codecEac3Joc
        val codecAc3 = hasMediaCodecDecoder(MimeTypes.AUDIO_AC3)
        val (codecAc4, codecAc4Level4) = detectAc4MediaCodec()

        return Capabilities(
            supportsEac3Joc = passthroughEac3Joc || codecEac3Joc,
            supportsEac3 = passthroughEac3 || codecEac3,
            supportsAc3 = passthroughAc3 || codecAc3,
            supportsAc4 = passthroughAc4 || codecAc4,
            supportsAc4Level4 = passthroughAc4Level4 || codecAc4Level4,
        )
    }

    private fun hasMediaCodecDecoder(mimeType: String): Boolean =
        try {
            MediaCodecUtil.getDecoderInfo(mimeType, /* secure= */ false, /* tunneling= */ false) != null
        } catch (_: Throwable) {
            false
        }

    private fun detectAc4MediaCodec(): Pair<Boolean, Boolean> =
        try {
            val decoderInfo = MediaCodecUtil.getDecoderInfo(
                MimeTypes.AUDIO_AC4,
                /* secure= */ false,
                /* tunneling= */ false,
            )
            if (decoderInfo == null) {
                false to false
            } else {
                val level4 = try {
                    decoderInfo.isAudioChannelCountSupportedV21(AC4_LEVEL4_CHANNEL_COUNT)
                } catch (_: Throwable) {
                    false
                }
                true to level4
            }
        } catch (_: Throwable) {
            false to false
        }

    /**
     * Returns the direct-play container list to pass to Jellyfin's universal audio endpoint based
     * on the vehicle's detected [capabilities].
     */
    fun allowedContainers(capabilities: Capabilities): List<String> = buildList {
        add("flac")
        add("mp3")
        add("m4a")
        add("aac")
        add("ogg")
        add("m4b")
        add("mp4")
        if (capabilities.supportsEac3 || capabilities.supportsEac3Joc) {
            add("eac3")
            add("ec3")
        }
        if (capabilities.supportsAc3) {
            add("ac3")
        }
        if (capabilities.supportsAc4) {
            add("ac4")
        }
        add("mka")
        add("mkv")
    }

    /**
     * Returns true when [item]'s audio stream metadata is known and uses a Dolby codec that the
     * vehicle's [capabilities] cannot decode natively, meaning Jellyfin should transcode it.
     */
    fun requiresTranscoding(item: BaseItemDto, capabilities: Capabilities): Boolean {
        val audioStream = item.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }
        if (audioStream?.codec != null && !capabilities.supportsCodec(audioStream.codec)) {
            return true
        }
        if (item.container != null && !capabilities.supportsCodec(item.container)) {
            return true
        }
        return false
    }

    /**
     * Infers the [DolbyFormat] of a Jellyfin [BaseItemDto] when its [MediaStream] metadata is
     * populated and playback is not transcoding.
     */
    fun fromJellyfinItem(
        item: BaseItemDto,
        capabilities: Capabilities,
        isTranscoding: Boolean = false,
    ): DolbyFormat {
        if (isTranscoding) return DolbyFormat.NONE
        val audioStream = item.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }
            ?: return DolbyFormat.NONE
        return fromJellyfinStream(audioStream, capabilities)
    }

    /**
     * Infers the [DolbyFormat] of a Jellyfin audio [MediaStream] given the vehicle's
     * [capabilities]. Returns [DolbyFormat.NONE] if the vehicle does not support the stream's
     * codec (since it will be transcoded).
     */
    fun fromJellyfinStream(stream: MediaStream, capabilities: Capabilities): DolbyFormat {
        val codec = stream.codec?.lowercase(Locale.ROOT)?.trim()
        if (!capabilities.supportsCodec(codec)) return DolbyFormat.NONE

        val isAtmosMetadata = stream.audioSpatialFormat == AudioSpatialFormat.DOLBY_ATMOS ||
            containsAtmosMarker(stream.profile) ||
            containsAtmosMarker(stream.displayTitle) ||
            containsAtmosMarker(stream.title)

        return when (codec) {
            "ac4", "ac-4" ->
                if (capabilities.supportsAc4) DolbyFormat.DOLBY_ATMOS else DolbyFormat.NONE
            "eac3", "ec3", "ec-3", "eac3-joc", "ec+3" -> {
                if (codec == "eac3-joc" || codec == "ec+3" || isAtmosMetadata) {
                    if (capabilities.supportsDolbyAtmos || capabilities.supportsEac3) {
                        DolbyFormat.DOLBY_ATMOS
                    } else {
                        DolbyFormat.NONE
                    }
                } else if (capabilities.supportsEac3 || capabilities.supportsEac3Joc) {
                    DolbyFormat.DOLBY_AUDIO
                } else {
                    DolbyFormat.NONE
                }
            }
            "ac3", "ac-3" ->
                if (isAtmosMetadata && capabilities.supportsDolbyAtmos) {
                    DolbyFormat.DOLBY_ATMOS
                } else if (capabilities.supportsAc3 || capabilities.supportsEac3 || capabilities.supportsEac3Joc) {
                    DolbyFormat.DOLBY_AUDIO
                } else {
                    DolbyFormat.NONE
                }
            else ->
                if (isAtmosMetadata && capabilities.supportsDolbyAtmos) {
                    DolbyFormat.DOLBY_ATMOS
                } else {
                    DolbyFormat.NONE
                }
        }
    }

    private fun containsAtmosMarker(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val lower = value.lowercase(Locale.ROOT)
        return lower.contains("atmos") || lower.contains("joc")
    }

    /**
     * Infers the [DolbyFormat] from the active ExoPlayer audio [Format] during playback.
     */
    fun fromExoPlayerFormat(format: Format?): DolbyFormat {
        if (format == null) return DolbyFormat.NONE
        val codecs = format.codecs?.lowercase(Locale.ROOT).orEmpty()
        return when (format.sampleMimeType) {
            MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_AC4 -> DolbyFormat.DOLBY_ATMOS
            MimeTypes.AUDIO_E_AC3 ->
                if (codecs.contains("ec+3") || codecs.contains("joc")) {
                    DolbyFormat.DOLBY_ATMOS
                } else {
                    DolbyFormat.DOLBY_AUDIO
                }
            MimeTypes.AUDIO_AC3 -> DolbyFormat.DOLBY_AUDIO
            else -> DolbyFormat.NONE
        }
    }

    /**
     * Populates or clears the AAOS immersive-audio and Dolby format attribution extras on
     * [extras] for [format].
     */
    fun applyFormatAttribution(extras: Bundle, format: DolbyFormat) {
        when (format) {
            DolbyFormat.DOLBY_ATMOS -> {
                extras.putLong(KEY_IMMERSIVE_AUDIO, METADATA_VALUE_ATTRIBUTE_PRESENT)
                extras.putString(KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI, DOLBY_ATMOS_LARGE_ICON_URI)
                extras.putString(KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI, DOLBY_SMALL_ICON_URI)
            }
            DolbyFormat.DOLBY_AUDIO -> {
                extras.remove(KEY_IMMERSIVE_AUDIO)
                extras.putString(KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI, DOLBY_AUDIO_LARGE_ICON_URI)
                extras.putString(KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI, DOLBY_SMALL_ICON_URI)
            }
            DolbyFormat.NONE -> {
                extras.remove(KEY_IMMERSIVE_AUDIO)
                extras.remove(KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI)
                extras.remove(KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI)
            }
        }
    }

    /**
     * Returns true if [extras] already carries the exact format attribution for [format].
     */
    fun hasFormatAttribution(extras: Bundle?, format: DolbyFormat): Boolean =
        when (format) {
            DolbyFormat.DOLBY_ATMOS ->
                extras != null &&
                    extras.getLong(KEY_IMMERSIVE_AUDIO, 0L) == METADATA_VALUE_ATTRIBUTE_PRESENT &&
                    extras.getString(KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI) == DOLBY_ATMOS_LARGE_ICON_URI &&
                    extras.getString(KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI) == DOLBY_SMALL_ICON_URI
            DolbyFormat.DOLBY_AUDIO ->
                extras != null &&
                    !extras.containsKey(KEY_IMMERSIVE_AUDIO) &&
                    extras.getString(KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI) == DOLBY_AUDIO_LARGE_ICON_URI &&
                    extras.getString(KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI) == DOLBY_SMALL_ICON_URI
            DolbyFormat.NONE ->
                extras == null || (
                    !extras.containsKey(KEY_IMMERSIVE_AUDIO) &&
                        !extras.containsKey(KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI) &&
                        !extras.containsKey(KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI)
                    )
        }

    /**
     * Returns a copy of [item] with its [MediaItem.mediaMetadata] extras updated for [format], or
     * [item] unchanged if its extras already match [format].
     */
    fun withFormatAttribution(item: MediaItem, format: DolbyFormat): MediaItem {
        val currentExtras = item.mediaMetadata.extras
        if (hasFormatAttribution(currentExtras, format)) return item

        val updatedExtras = if (currentExtras != null) Bundle(currentExtras) else Bundle()
        applyFormatAttribution(updatedExtras, format)

        val updatedMetadata = item.mediaMetadata.buildUpon()
            .setExtras(updatedExtras)
            .build()

        return item.buildUpon()
            .setMediaMetadata(updatedMetadata)
            .build()
    }

    /**
     * Returns true when [errorCode] indicates the media stream's audio format could not be
     * decoded locally and should fall back to server-side transcoding.
     */
    fun isUnsupportedFormatError(errorCode: Int): Boolean =
        errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
            errorCode == PlaybackException.ERROR_CODE_DECODING_INIT_FAILED

    /**
     * Converts a direct-play Jellyfin universal audio [uri] (one carrying `container` query
     * parameters) into a server-transcoded URI by removing all `container` parameters while
     * preserving `transcodingContainer`, `audioCodec`, and all other query parameters.
     *
     * Returns `null` if [uri] does not carry any `container` query parameters (i.e. it is already
     * a transcoding URI).
     */
    fun toTranscodingUri(uri: Uri): Uri? {
        if (uri.getQueryParameters("container").isEmpty()) return null

        val builder = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name == "container") continue
            for (value in uri.getQueryParameters(name)) {
                builder.appendQueryParameter(name, value)
            }
        }
        return builder.build()
    }
}
