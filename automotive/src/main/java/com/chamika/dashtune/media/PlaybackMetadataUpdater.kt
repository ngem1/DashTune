package com.chamika.dashtune.media

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.preference.PreferenceManager

object PlaybackMetadataUpdater {

    const val PREF_SHOW_STREAM_FORMAT = "show_stream_format"

    private const val KEY_IMMERSIVE_AUDIO =
        "androidx.car.app.mediaextensions.KEY_IMMERSIVE_AUDIO"
    private const val KEY_CONTENT_FORMAT_LARGE_ICON_URI =
        "androidx.car.app.mediaextensions.KEY_CONTENT_FORMAT_TINTABLE_LARGE_ICON_URI"
    private const val KEY_CONTENT_FORMAT_SMALL_ICON_URI =
        "androidx.car.app.mediaextensions.KEY_CONTENT_FORMAT_TINTABLE_SMALL_ICON_URI"

    private const val ATMOS_ICON_URI = "android.resource://com.chamika.dashtune/drawable/ic_dolby_atmos"

    fun updateNowPlayingFormat(
        context: Context,
        player: Player,
        format: Format?,
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(PREF_SHOW_STREAM_FORMAT, true)) return

        val currentItem = player.currentMediaItem ?: return
        val formatLabel = PlaybackFormatFormatter.formatLabel(format) ?: return

        val artist = currentItem.mediaMetadata.albumArtist?.toString()
            ?: currentItem.mediaMetadata.artist?.toString()
        val subtitle = if (artist != null) "$artist · $formatLabel" else formatLabel

        val extras = Bundle(currentItem.mediaMetadata.extras ?: Bundle())
        if (PlaybackFormatFormatter.isAtmos(format)) {
            extras.putBoolean(KEY_IMMERSIVE_AUDIO, true)
            extras.putString(KEY_CONTENT_FORMAT_LARGE_ICON_URI, ATMOS_ICON_URI)
            extras.putString(KEY_CONTENT_FORMAT_SMALL_ICON_URI, ATMOS_ICON_URI)
        } else {
            extras.remove(KEY_IMMERSIVE_AUDIO)
            extras.remove(KEY_CONTENT_FORMAT_LARGE_ICON_URI)
            extras.remove(KEY_CONTENT_FORMAT_SMALL_ICON_URI)
        }

        val updatedMetadata = currentItem.mediaMetadata.buildUpon()
            .setSubtitle(subtitle)
            .apply {
                if (artist != null) {
                    setArtist(artist)
                }
            }
            .setExtras(extras)
            .build()

        val updatedItem = currentItem.buildUpon()
            .setMediaMetadata(updatedMetadata)
            .build()

        if (metadataEquivalent(currentItem, updatedItem)) return

        player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
    }

    private fun metadataEquivalent(before: MediaItem, after: MediaItem): Boolean {
        val beforeMetadata = before.mediaMetadata
        val afterMetadata = after.mediaMetadata
        return beforeMetadata.subtitle == afterMetadata.subtitle &&
            beforeMetadata.artist == afterMetadata.artist &&
            extrasEquivalent(beforeMetadata.extras, afterMetadata.extras)
    }

    private fun extrasEquivalent(before: Bundle?, after: Bundle?): Boolean {
        if (before == null && after == null) return true
        if (before == null || after == null) return false
        return before.getBoolean(KEY_IMMERSIVE_AUDIO, false) ==
            after.getBoolean(KEY_IMMERSIVE_AUDIO, false) &&
            before.getString(KEY_CONTENT_FORMAT_LARGE_ICON_URI) ==
            after.getString(KEY_CONTENT_FORMAT_LARGE_ICON_URI)
    }
}
