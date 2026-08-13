package ru.flawden.baskovmusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.flawden.baskovmusic.model.LocalTrack

class LocalSearchRankerTest {

    @Test
    fun typoInCyrillicTitleStillFindsTrack() {
        val tracks = listOf(
            track(1, "Привет", "Секрет"),
            track(2, "Прощай", "Другой"),
        )

        val result = LocalSearchRanker.search(tracks, "Прывет")

        assertEquals(1L, result.first().id)
    }

    @Test
    fun adjacentTranspositionInArtistQueryIsTolerated() {
        val tracks = listOf(
            track(1, "Holiday", "Green Day"),
            track(2, "Holiday", "Scorpions"),
        )

        val result = LocalSearchRanker.search(tracks, "Green Dya")

        assertEquals(1L, result.first().id)
    }

    @Test
    fun exactTitleOutranksAlbumOnlyMatch() {
        val tracks = listOf(
            track(1, "Monster", "Skillet", album = "Awake"),
            track(2, "Other Song", "Another Artist", album = "Monster"),
        )

        val result = LocalSearchRanker.search(tracks, "Monster")

        assertEquals(1L, result.first().id)
    }

    @Test
    fun artistAndTitleQueryRanksCombinedMatchFirst() {
        val tracks = listOf(
            track(1, "Monster", "Skillet"),
            track(2, "Monster", "Imagine Dragons"),
            track(3, "Hero", "Skillet"),
        )

        val result = LocalSearchRanker.search(tracks, "Skillet Monster")

        assertEquals(1L, result.first().id)
    }

    @Test
    fun unrelatedNoiseIsExcluded() {
        val tracks = listOf(
            track(1, "Holiday", "Green Day"),
            track(2, "Monster", "Skillet"),
        )

        val result = LocalSearchRanker.search(tracks, "qzxwplmn")

        assertTrue(result.isEmpty())
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String? = null,
    ) = LocalTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMillis = 180_000L,
        contentUri = "content://media/$id",
        artworkUri = null,
        folderPath = "/Music",
    )
}
