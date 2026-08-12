package ru.flawden.baskovmusic.playback

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Product API v1.33 performs time-based seek by opening a fresh Ogg/Opus stream with startMillis.
 * The HTTP body itself remains intentionally non-byte-seekable.
 */
internal object PlaybackStreamUrl {
    private const val START_MILLIS = "startMillis"

    fun startMillis(url: String?): Long {
        if (url.isNullOrBlank()) return 0L
        val query = url.substringBefore('#').substringAfter('?', "")
        return query
            .split('&')
            .firstOrNull { it.substringBefore('=') == START_MILLIS }
            ?.substringAfter('=', "")
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L
    }

    fun withStartMillis(url: String, startMillis: Long): String {
        val normalized = withoutStartMillis(url)
        val safeStart = startMillis.coerceAtLeast(0L)
        if (safeStart == 0L) return normalized

        val hashIndex = normalized.indexOf('#')
        val main = if (hashIndex >= 0) normalized.substring(0, hashIndex) else normalized
        val fragment = if (hashIndex >= 0) normalized.substring(hashIndex) else ""
        val separator = if ('?' in main) '&' else '?'
        return "$main$separator$START_MILLIS=$safeStart$fragment"
    }

    fun baseKey(url: String): String = withoutStartMillis(url)

    private fun withoutStartMillis(url: String): String {
        val hashIndex = url.indexOf('#')
        val main = if (hashIndex >= 0) url.substring(0, hashIndex) else url
        val fragment = if (hashIndex >= 0) url.substring(hashIndex) else ""

        val queryIndex = main.indexOf('?')
        if (queryIndex < 0) return main + fragment

        val base = main.substring(0, queryIndex)
        val filtered = main.substring(queryIndex + 1)
            .split('&')
            .filter { it.isNotBlank() && it.substringBefore('=') != START_MILLIS }

        return buildString {
            append(base)
            if (filtered.isNotEmpty()) {
                append('?')
                append(filtered.joinToString("&"))
            }
            append(fragment)
        }
    }
}

internal object PlaybackArtworkUrl {
    fun normalize(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val uri = URI.create(value.trim())
            val scheme = uri.scheme
            require(
                scheme.equals("http", ignoreCase = true) ||
                    scheme.equals("https", ignoreCase = true),
            )
            require(!uri.host.isNullOrBlank())
            uri.toASCIIString()
        }.getOrNull()
    }
}

internal object PlaybackStreamMetrics {
    private val durationByStream = ConcurrentHashMap<String, Long>()

    fun recordDuration(url: String, durationMillis: Long) {
        if (durationMillis > 0L) {
            durationByStream[PlaybackStreamUrl.baseKey(url)] = durationMillis
        }
    }

    fun durationMillis(url: String?): Long {
        if (url.isNullOrBlank()) return 0L
        return durationByStream[PlaybackStreamUrl.baseKey(url)] ?: 0L
    }
}
