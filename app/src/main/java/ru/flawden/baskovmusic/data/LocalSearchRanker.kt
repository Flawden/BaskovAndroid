package ru.flawden.baskovmusic.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import ru.flawden.baskovmusic.model.LocalTrack

object LocalSearchRanker {

    fun search(
        tracks: List<LocalTrack>,
        rawQuery: String,
        limit: Int = 50,
    ): List<LocalTrack> {
        val query = normalize(rawQuery)
        if (query.isBlank() || limit <= 0) return emptyList()
        val queryTokens = tokens(query)

        return tracks.asSequence()
            .mapNotNull { track ->
                val title = normalize(track.title)
                val artist = normalize(track.artist)
                val album = normalize(track.album.orEmpty())

                val titleScore = fieldScore(query, queryTokens, title)
                val artistScore = fieldScore(query, queryTokens, artist) * ARTIST_WEIGHT
                val albumScore = fieldScore(query, queryTokens, album) * ALBUM_WEIGHT
                val combinedScore = fieldScore(
                    query,
                    queryTokens,
                    listOf(artist, title).filter(String::isNotBlank).joinToString(" "),
                ) * COMBINED_WEIGHT

                val score = max(max(titleScore, artistScore), max(albumScore, combinedScore))
                score.takeIf { it >= MIN_RESULT_SCORE }?.let {
                    RankedTrack(track, it, title, artist)
                }
            }
            .sortedWith(
                compareByDescending<RankedTrack> { it.score }
                    .thenBy { it.title }
                    .thenBy { it.artist }
                    .thenBy { it.track.id },
            )
            .take(limit.coerceAtMost(MAX_RESULTS))
            .map(RankedTrack::track)
            .toList()
    }

    private fun fieldScore(
        query: String,
        queryTokens: List<String>,
        field: String,
    ): Double {
        if (field.isBlank()) return 0.0
        if (field == query) return 1.0
        if (field.startsWith(query)) return 0.96
        if (field.contains(query)) return 0.90

        val fieldTokens = tokens(field)
        if (fieldTokens.isEmpty()) return 0.0

        val tokenCoverage = queryTokens
            .map { queryToken ->
                fieldTokens.maxOfOrNull { fieldToken -> tokenSimilarity(queryToken, fieldToken) } ?: 0.0
            }
            .averageOrZero()

        val phraseSimilarity = if (query.length >= 4 && field.length >= 4) {
            similarity(query, field)
        } else {
            0.0
        }

        return max(tokenCoverage * TOKEN_WEIGHT, phraseSimilarity * PHRASE_WEIGHT)
    }

    private fun tokenSimilarity(left: String, right: String): Double {
        if (left == right) return 1.0
        if (right.startsWith(left) && left.length >= 2) return 0.92
        if (right.contains(left) && left.length >= 3) return 0.86
        if (left.length < MIN_FUZZY_TOKEN || right.length < MIN_FUZZY_TOKEN) return 0.0
        return similarity(left, right)
    }

    private fun similarity(left: String, right: String): Double {
        if (left == right) return 1.0
        val maxLength = max(left.length, right.length)
        if (maxLength == 0) return 1.0
        val distance = damerauLevenshtein(left, right)
        return (1.0 - distance / maxLength.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun damerauLevenshtein(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        val previousPrevious = IntArray(right.length + 1)
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in 1..left.length) {
            current[0] = i
            for (j in 1..right.length) {
                val substitutionCost = if (left[i - 1] == right[j - 1]) 0 else 1
                var value = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + substitutionCost,
                )
                if (
                    i > 1 &&
                    j > 1 &&
                    left[i - 1] == right[j - 2] &&
                    left[i - 2] == right[j - 1]
                ) {
                    value = minOf(value, previousPrevious[j - 2] + 1)
                }
                current[j] = value
            }
            previous.copyInto(previousPrevious)
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(
            value.lowercase(Locale.ROOT).replace('ё', 'е'),
            Normalizer.Form.NFD,
        )
        return decomposed
            .replace(COMBINING_MARKS, "")
            .replace(NON_LETTER_OR_DIGIT, " ")
            .trim()
            .replace(WHITESPACE, " ")
    }

    private fun tokens(value: String): List<String> =
        value.split(' ').filter(String::isNotBlank)

    private fun List<Double>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else average()

    private data class RankedTrack(
        val track: LocalTrack,
        val score: Double,
        val title: String,
        val artist: String,
    )

    private const val ARTIST_WEIGHT = 0.84
    private const val ALBUM_WEIGHT = 0.58
    private const val COMBINED_WEIGHT = 0.95
    private const val TOKEN_WEIGHT = 0.88
    private const val PHRASE_WEIGHT = 0.72
    private const val MIN_RESULT_SCORE = 0.52
    private const val MIN_FUZZY_TOKEN = 3
    private const val MAX_RESULTS = 200

    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val NON_LETTER_OR_DIGIT = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE = Regex("\\s+")
}
