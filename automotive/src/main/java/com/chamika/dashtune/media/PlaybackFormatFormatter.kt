package com.chamika.dashtune.media

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes

object PlaybackFormatFormatter {

    fun formatLabel(format: Format?): String? {
        if (format == null) return null

        val codec = codecLabel(format)
        val bitrate = bitrateLabel(format)
        val channels = channelLabel(format.channelCount)

        return listOfNotNull(codec, bitrate, channels).joinToString(" · ").ifBlank { null }
    }

    fun isAtmos(format: Format?): Boolean {
        if (format == null) return false
        val mime = format.sampleMimeType ?: return false
        if (mime == MimeTypes.AUDIO_AC4) return true
        if (mime != MimeTypes.AUDIO_E_AC3) return false
        return format.channelCount >= 6 || format.codecs?.contains("joc", ignoreCase = true) == true
    }

    private fun codecLabel(format: Format): String? {
        val mime = format.sampleMimeType ?: return null
        return when (mime) {
            MimeTypes.AUDIO_E_AC3 -> if (isAtmos(format)) "Dolby Atmos" else "E-AC-3"
            MimeTypes.AUDIO_AC3 -> "AC-3"
            MimeTypes.AUDIO_AC4 -> "Dolby Atmos"
            MimeTypes.AUDIO_FLAC -> "FLAC"
            MimeTypes.AUDIO_MPEG -> "MP3"
            MimeTypes.AUDIO_MPEG_L2 -> "MP2"
            MimeTypes.AUDIO_AAC -> "AAC"
            MimeTypes.AUDIO_OPUS -> "Opus"
            MimeTypes.AUDIO_VORBIS -> "Vorbis"
            MimeTypes.AUDIO_ALAC -> "ALAC"
            else -> mime.substringAfter("audio/").uppercase()
        }
    }

    private fun bitrateLabel(format: Format): String? {
        val bitrate = when {
            format.averageBitrate > 0 -> format.averageBitrate
            format.bitrate > 0 -> format.bitrate
            else -> return null
        }
        return when {
            bitrate >= 10_000_000 -> "${bitrate / 1_000_000} Mbps"
            bitrate >= 1_000 -> "${bitrate / 1_000} kbps"
            else -> "$bitrate bps"
        }
    }

    private fun channelLabel(channelCount: Int): String? = when {
        channelCount <= 0 -> null
        channelCount == 1 -> "mono"
        channelCount == 2 -> "stereo"
        else -> "${channelCount}ch"
    }
}
