package com.chamika.dashtune.media

import android.media.MediaCodecList
import androidx.media3.common.MimeTypes

/**
 * Probes the device for audio decoder support used when negotiating direct play with Jellyfin.
 */
class AudioCapabilities(
    private val mimeTypeChecker: (String) -> Boolean = ::deviceSupportsMimeType,
) {
    val supportsEac3: Boolean by lazy { mimeTypeChecker(MimeTypes.AUDIO_E_AC3) }
    val supportsAc4: Boolean by lazy { mimeTypeChecker(MimeTypes.AUDIO_AC4) }

    fun supportsAtmosCodecs(): Boolean = supportsEac3 || supportsAc4

    companion object {
        fun deviceSupportsMimeType(mimeType: String): Boolean {
            val codecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            return codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }
    }
}
