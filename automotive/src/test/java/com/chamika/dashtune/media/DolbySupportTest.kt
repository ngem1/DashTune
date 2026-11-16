package com.chamika.dashtune.media

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.test.core.app.ApplicationProvider
import com.chamika.dashtune.media.DolbySupport.Capabilities
import com.chamika.dashtune.media.DolbySupport.DOLBY_ATMOS_LARGE_ICON_URI
import com.chamika.dashtune.media.DolbySupport.DOLBY_AUDIO_LARGE_ICON_URI
import com.chamika.dashtune.media.DolbySupport.DOLBY_SMALL_ICON_URI
import com.chamika.dashtune.media.DolbySupport.DolbyFormat
import com.chamika.dashtune.media.DolbySupport.KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI
import com.chamika.dashtune.media.DolbySupport.KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI
import com.chamika.dashtune.media.DolbySupport.KEY_IMMERSIVE_AUDIO
import com.chamika.dashtune.media.DolbySupport.METADATA_VALUE_ATTRIBUTE_PRESENT
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.sdk.model.api.AudioSpatialFormat
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class DolbySupportTest {

    private fun audioStream(
        codec: String? = "eac3",
        spatialFormat: AudioSpatialFormat = AudioSpatialFormat.NONE,
        profile: String? = null,
        displayTitle: String? = null,
        title: String? = null,
    ): MediaStream = mockk(relaxed = true) {
        every { type } returns MediaStreamType.AUDIO
        every { this@mockk.codec } returns codec
        every { audioSpatialFormat } returns spatialFormat
        every { this@mockk.profile } returns profile
        every { this@mockk.displayTitle } returns displayTitle
        every { this@mockk.title } returns title
    }

    // --- allowedContainers tests ---

    @Test
    fun `allowedContainers without Dolby capabilities includes base and audiobook containers`() {
        val containers = DolbySupport.allowedContainers(Capabilities())

        assertEquals(
            listOf("flac", "mp3", "m4a", "aac", "ogg", "m4b", "mp4", "mka", "mkv"),
            containers
        )
    }

    @Test
    fun `allowedContainers with E-AC-3 JOC capability adds eac3 and ec3`() {
        val containers = DolbySupport.allowedContainers(
            Capabilities(supportsEac3Joc = true, supportsEac3 = true)
        )

        assertEquals(
            listOf("flac", "mp3", "m4a", "aac", "ogg", "m4b", "mp4", "eac3", "ec3", "mka", "mkv"),
            containers
        )
    }

    @Test
    fun `allowedContainers with full Dolby capabilities returns ALL_CONTAINERS`() {
        val caps = Capabilities(
            supportsEac3Joc = true,
            supportsEac3 = true,
            supportsAc3 = true,
            supportsAc4 = true,
            supportsAc4Level4 = true,
        )

        assertEquals(DolbySupport.ALL_CONTAINERS, DolbySupport.allowedContainers(caps))
    }

    // --- Capabilities helper tests ---

    @Test
    fun `supportsDolbyAtmos is true when E-AC-3 JOC or AC-4 is supported`() {
        assertFalse(Capabilities().supportsDolbyAtmos)
        assertFalse(Capabilities(supportsEac3 = true, supportsAc3 = true).supportsDolbyAtmos)
        assertTrue(Capabilities(supportsEac3Joc = true).supportsDolbyAtmos)
        assertTrue(Capabilities(supportsAc4 = true).supportsDolbyAtmos)
    }

    @Test
    fun `supportsDolbyAudio is true when any Dolby codec is supported`() {
        assertFalse(Capabilities().supportsDolbyAudio)
        assertTrue(Capabilities(supportsAc3 = true).supportsDolbyAudio)
        assertTrue(Capabilities(supportsEac3 = true).supportsDolbyAudio)
        assertTrue(Capabilities(supportsEac3Joc = true).supportsDolbyAudio)
        assertTrue(Capabilities(supportsAc4 = true).supportsDolbyAudio)
    }

    @Test
    fun `supportsCodec checks Dolby codecs against capabilities`() {
        val noDolby = Capabilities()
        assertFalse(noDolby.supportsCodec("eac3"))
        assertFalse(noDolby.supportsCodec("ec3"))
        assertFalse(noDolby.supportsCodec("ac3"))
        assertFalse(noDolby.supportsCodec("ac4"))
        assertTrue(noDolby.supportsCodec("aac"))
        assertTrue(noDolby.supportsCodec("flac"))

        val eac3Caps = Capabilities(supportsEac3Joc = true, supportsEac3 = true)
        assertTrue(eac3Caps.supportsCodec("eac3"))
        assertTrue(eac3Caps.supportsCodec("ec3"))
        assertTrue(eac3Caps.supportsCodec("ac3"))
        assertFalse(eac3Caps.supportsCodec("ac4"))
    }

    // --- requiresTranscoding tests ---

    @Test
    fun `requiresTranscoding is true for E-AC-3 audiobook when device lacks E-AC-3 support`() {
        val dto = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO_BOOK,
            name = "Atmos Book",
            container = "m4b",
            mediaStreams = listOf(
                audioStream(codec = "eac3", spatialFormat = AudioSpatialFormat.DOLBY_ATMOS)
            )
        )

        assertTrue(DolbySupport.requiresTranscoding(dto, Capabilities()))
        assertFalse(
            DolbySupport.requiresTranscoding(
                dto,
                Capabilities(supportsEac3Joc = true, supportsEac3 = true)
            )
        )
    }

    // --- fromJellyfinItem & fromJellyfinStream tests ---

    @Test
    fun `fromJellyfinItem detects DOLBY_ATMOS from AudioSpatialFormat DOLBY_ATMOS`() {
        val dto = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO_BOOK,
            name = "Atmos Audiobook",
            mediaStreams = listOf(
                audioStream(codec = "eac3", spatialFormat = AudioSpatialFormat.DOLBY_ATMOS)
            )
        )
        val caps = Capabilities(supportsEac3Joc = true, supportsEac3 = true)

        assertEquals(DolbyFormat.DOLBY_ATMOS, DolbySupport.fromJellyfinItem(dto, caps))
    }

    @Test
    fun `fromJellyfinItem detects DOLBY_ATMOS from JOC profile when spatialFormat is NONE`() {
        val dto = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO,
            name = "Atmos Chapter",
            mediaStreams = listOf(
                audioStream(
                    codec = "eac3",
                    spatialFormat = AudioSpatialFormat.NONE,
                    profile = "Dolby Digital Plus + Dolby Atmos",
                )
            )
        )
        val caps = Capabilities(supportsEac3Joc = true, supportsEac3 = true)

        assertEquals(DolbyFormat.DOLBY_ATMOS, DolbySupport.fromJellyfinItem(dto, caps))
    }

    @Test
    fun `fromJellyfinItem detects DOLBY_AUDIO for standard 5_1 E-AC-3 without Atmos`() {
        val dto = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO,
            name = "DD+ Track",
            mediaStreams = listOf(
                audioStream(codec = "eac3", spatialFormat = AudioSpatialFormat.NONE)
            )
        )
        val caps = Capabilities(supportsEac3 = true)

        assertEquals(DolbyFormat.DOLBY_AUDIO, DolbySupport.fromJellyfinItem(dto, caps))
    }

    @Test
    fun `fromJellyfinItem returns NONE when transcoding is active`() {
        val dto = BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO_BOOK,
            name = "Atmos Audiobook",
            mediaStreams = listOf(
                audioStream(codec = "eac3", spatialFormat = AudioSpatialFormat.DOLBY_ATMOS)
            )
        )
        val caps = Capabilities(supportsEac3Joc = true, supportsEac3 = true)

        assertEquals(
            DolbyFormat.NONE,
            DolbySupport.fromJellyfinItem(dto, caps, isTranscoding = true)
        )
    }

    // --- fromExoPlayerFormat tests ---

    @Test
    fun `fromExoPlayerFormat detects DOLBY_ATMOS for AUDIO_E_AC3_JOC and AUDIO_AC4`() {
        val jocFormat = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC).build()
        val ac4Format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC4).build()

        assertEquals(DolbyFormat.DOLBY_ATMOS, DolbySupport.fromExoPlayerFormat(jocFormat))
        assertEquals(DolbyFormat.DOLBY_ATMOS, DolbySupport.fromExoPlayerFormat(ac4Format))
    }

    @Test
    fun `fromExoPlayerFormat detects DOLBY_AUDIO for AUDIO_E_AC3 and AUDIO_AC3`() {
        val eac3Format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).build()
        val ac3Format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build()
        val mp3Format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_MPEG).build()

        assertEquals(DolbyFormat.DOLBY_AUDIO, DolbySupport.fromExoPlayerFormat(eac3Format))
        assertEquals(DolbyFormat.DOLBY_AUDIO, DolbySupport.fromExoPlayerFormat(ac3Format))
        assertEquals(DolbyFormat.NONE, DolbySupport.fromExoPlayerFormat(mp3Format))
        assertEquals(DolbyFormat.NONE, DolbySupport.fromExoPlayerFormat(null))
    }

    // --- applyFormatAttribution & withFormatAttribution tests ---

    @Test
    fun `applyFormatAttribution sets immersive audio and Atmos icons for DOLBY_ATMOS`() {
        val extras = Bundle()

        DolbySupport.applyFormatAttribution(extras, DolbyFormat.DOLBY_ATMOS)

        assertEquals(METADATA_VALUE_ATTRIBUTE_PRESENT, extras.getLong(KEY_IMMERSIVE_AUDIO))
        assertEquals(
            DOLBY_ATMOS_LARGE_ICON_URI,
            extras.getString(KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI)
        )
        assertEquals(
            DOLBY_SMALL_ICON_URI,
            extras.getString(KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI)
        )
        assertTrue(DolbySupport.hasFormatAttribution(extras, DolbyFormat.DOLBY_ATMOS))
    }

    @Test
    fun `applyFormatAttribution sets Dolby Audio icons without immersive audio flag for DOLBY_AUDIO`() {
        val extras = Bundle().apply {
            putLong(KEY_IMMERSIVE_AUDIO, METADATA_VALUE_ATTRIBUTE_PRESENT)
        }

        DolbySupport.applyFormatAttribution(extras, DolbyFormat.DOLBY_AUDIO)

        assertFalse(extras.containsKey(KEY_IMMERSIVE_AUDIO))
        assertEquals(
            DOLBY_AUDIO_LARGE_ICON_URI,
            extras.getString(KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI)
        )
        assertEquals(
            DOLBY_SMALL_ICON_URI,
            extras.getString(KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI)
        )
        assertTrue(DolbySupport.hasFormatAttribution(extras, DolbyFormat.DOLBY_AUDIO))
    }

    @Test
    fun `withFormatAttribution returns identical instance when attribution already matches`() {
        val extras = Bundle()
        DolbySupport.applyFormatAttribution(extras, DolbyFormat.DOLBY_ATMOS)
        val item = MediaItem.Builder()
            .setMediaId("track-1")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Track").setExtras(extras).build())
            .build()

        val result = DolbySupport.withFormatAttribution(item, DolbyFormat.DOLBY_ATMOS)

        assertSame(item, result)
    }

    // --- Transcoding fallback URI tests ---

    @Test
    fun `toTranscodingUri strips container params and returns null when already transcoded`() {
        val directUri = Uri.parse(
            "http://server:8096/Audio/123/universal?container=flac&container=m4b&container=eac3&transcodingContainer=mp3&audioCodec=mp3"
        )

        val transcodedUri = DolbySupport.toTranscodingUri(directUri)

        assertNotNull(transcodedUri)
        assertTrue(transcodedUri!!.getQueryParameters("container").isEmpty())
        assertEquals("mp3", transcodedUri.getQueryParameter("transcodingContainer"))
        assertEquals("mp3", transcodedUri.getQueryParameter("audioCodec"))
        assertNull(DolbySupport.toTranscodingUri(transcodedUri))
    }

    @Test
    fun `isUnsupportedFormatError matches decoding format errors`() {
        assertTrue(
            DolbySupport.isUnsupportedFormatError(
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
            )
        )
        assertTrue(
            DolbySupport.isUnsupportedFormatError(
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
            )
        )
        assertTrue(
            DolbySupport.isUnsupportedFormatError(
                PlaybackException.ERROR_CODE_DECODING_INIT_FAILED
            )
        )
        assertFalse(
            DolbySupport.isUnsupportedFormatError(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
            )
        )
    }

    @Test
    fun `detectCapabilities runs safely under Robolectric`() {
        val caps = DolbySupport.detectCapabilities(ApplicationProvider.getApplicationContext())
        assertNotNull(caps)
    }
}
