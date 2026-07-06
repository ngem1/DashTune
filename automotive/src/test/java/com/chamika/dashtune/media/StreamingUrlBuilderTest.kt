package com.chamika.dashtune.media

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.operations.UniversalAudioApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class StreamingUrlBuilderTest {

    private lateinit var context: Context
    private lateinit var jellyfinApi: ApiClient
    private lateinit var mockUniversalAudioApi: UniversalAudioApi
    private lateinit var audioCapabilities: AudioCapabilities

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        jellyfinApi = mockk(relaxed = true)
        mockUniversalAudioApi = mockk(relaxed = true)
        every { jellyfinApi.universalAudioApi } returns mockUniversalAudioApi
        every {
            mockUniversalAudioApi.getUniversalAudioStreamUrl(any(), any(), any(), any(), any(), any(), any(), any())
        } returns "http://localhost:8096/Audio/test/universal"

        audioCapabilities = mockk {
            every { supportsAtmosCodecs() } returns true
            every { supportsEac3 } returns true
            every { supportsAc4 } returns false
        }
    }

    @Test
    fun `direct stream passes null bitrates and atmos containers`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(StreamingUrlBuilder.PREF_BITRATE, StreamingUrlBuilder.DIRECT_STREAM)
            .putBoolean(StreamingUrlBuilder.PREF_DOLBY_ATMOS, true)
            .commit()

        val builder = StreamingUrlBuilder(context, jellyfinApi, audioCapabilities)
        builder.buildStreamingUrl(UUID.randomUUID())

        verify {
            mockUniversalAudioApi.getUniversalAudioStreamUrl(
                any(),
                container = match { it.contains("mp4|eac3") && it.contains("flac") },
                maxAudioChannels = 8,
                audioBitRate = null,
                maxStreamingBitrate = null,
                transcodingContainer = null,
                audioCodec = null,
            )
        }
    }

    @Test
    fun `bitrate cap passes mp3 transcode params`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(StreamingUrlBuilder.PREF_BITRATE, "256000")
            .putBoolean(StreamingUrlBuilder.PREF_DOLBY_ATMOS, true)
            .commit()

        val builder = StreamingUrlBuilder(context, jellyfinApi, audioCapabilities)
        builder.buildStreamingUrl(UUID.randomUUID())

        verify {
            mockUniversalAudioApi.getUniversalAudioStreamUrl(
                any(),
                container = any(),
                maxAudioChannels = 8,
                audioBitRate = 256000,
                maxStreamingBitrate = 256000,
                transcodingContainer = "mp3",
                audioCodec = "mp3",
            )
        }
    }

    @Test
    fun `atmos disabled omits eac3 containers`() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(StreamingUrlBuilder.PREF_BITRATE, StreamingUrlBuilder.DIRECT_STREAM)
            .putBoolean(StreamingUrlBuilder.PREF_DOLBY_ATMOS, false)
            .commit()

        val builder = StreamingUrlBuilder(context, jellyfinApi, audioCapabilities)
        builder.buildStreamingUrl(UUID.randomUUID())

        verify {
            mockUniversalAudioApi.getUniversalAudioStreamUrl(
                any(),
                container = match { containers ->
                    containers.none { it.contains("eac3") || it.contains("ac4") }
                },
                maxAudioChannels = null,
                audioBitRate = null,
                maxStreamingBitrate = null,
                transcodingContainer = null,
                audioCodec = null,
            )
        }
    }
}
