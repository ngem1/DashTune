package com.chamika.dashtune.media

import android.content.Context
import androidx.preference.PreferenceManager
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID

class StreamingUrlBuilder(
    private val context: Context,
    private val jellyfinApi: ApiClient,
    private val audioCapabilities: AudioCapabilities,
) {
    companion object {
        const val PREF_BITRATE = "bitrate"
        const val PREF_DOLBY_ATMOS = "dolby_atmos"
        const val DIRECT_STREAM = "Direct stream"

        private val BASE_CONTAINERS = listOf(
            "flac",
            "mp3",
            "m4a",
            "aac",
            "ogg",
            "mp4",
            "mkv",
            "mka",
        )

        private const val ATMOS_MAX_AUDIO_CHANNELS = 8
    }

    fun buildStreamingUrl(mediaId: String): String = buildStreamingUrl(mediaId.toUUID())

    fun buildStreamingUrl(mediaId: UUID): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val preferenceBitrate = prefs.getString(PREF_BITRATE, DIRECT_STREAM)!!
        val isDirectStream = preferenceBitrate == DIRECT_STREAM
        val bitrate = if (isDirectStream) null else preferenceBitrate.toIntOrNull()

        val atmosEnabled = prefs.getBoolean(PREF_DOLBY_ATMOS, audioCapabilities.supportsAtmosCodecs()) &&
            audioCapabilities.supportsAtmosCodecs()

        val containers = buildList {
            addAll(BASE_CONTAINERS)
            if (atmosEnabled && audioCapabilities.supportsEac3) {
                addAll(listOf("mp4|eac3", "m4a|eac3", "mkv|eac3"))
            }
            if (atmosEnabled && audioCapabilities.supportsAc4) {
                addAll(listOf("mp4|ac4", "m4a|ac4"))
            }
        }

        return jellyfinApi.universalAudioApi.getUniversalAudioStreamUrl(
            mediaId,
            container = containers,
            maxAudioChannels = if (atmosEnabled) ATMOS_MAX_AUDIO_CHANNELS else null,
            audioBitRate = bitrate,
            maxStreamingBitrate = bitrate,
            transcodingContainer = if (isDirectStream) null else "mp3",
            audioCodec = if (isDirectStream) null else "mp3",
        )
    }
}
