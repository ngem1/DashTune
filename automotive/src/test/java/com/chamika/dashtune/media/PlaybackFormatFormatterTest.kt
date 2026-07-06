package com.chamika.dashtune.media

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFormatFormatterTest {

    @Test
    fun `formatLabel combines codec bitrate and channels`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setAverageBitrate(1_411_000)
            .setChannelCount(2)
            .build()

        assertEquals("FLAC · 1411 kbps · stereo", PlaybackFormatFormatter.formatLabel(format))
    }

    @Test
    fun `isAtmos detects eac3 with joc`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_E_AC3)
            .setCodecs("ec+3.joc")
            .setChannelCount(6)
            .build()

        assertTrue(PlaybackFormatFormatter.isAtmos(format))
        assertEquals("Dolby Atmos", PlaybackFormatFormatter.formatLabel(format)?.substringBefore(" ·"))
    }

    @Test
    fun `isAtmos is false for stereo mp3`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_MPEG)
            .setChannelCount(2)
            .build()

        assertFalse(PlaybackFormatFormatter.isAtmos(format))
        assertEquals("MP3 · stereo", PlaybackFormatFormatter.formatLabel(format))
    }
}
