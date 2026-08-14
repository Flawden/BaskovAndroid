package ru.flawden.baskovmusic.playback

import java.util.Locale
import ru.flawden.baskovmusic.model.TrackPreview

internal object SmartAutoplayPolicy {
    fun shouldContinue(
        remote: Boolean,
        repeatModeOff: Boolean,
        hasSeed: Boolean,
    ): Boolean = remote && repeatModeOff && hasSeed

    fun sameLogicalTrack(
        seedStableKey: String?,
        seedArtist: String,
        seedTitle: String,
        next: TrackPreview,
    ): Boolean {
        val nextKey = next.stableKey?.takeIf(String::isNotBlank)
        if (!seedStableKey.isNullOrBlank() && nextKey != null && seedStableKey == nextKey) {
            return true
        }
        return normalize(seedArtist) == normalize(next.artist.orEmpty()) &&
            normalize(seedTitle) == normalize(next.title.orEmpty())
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}
