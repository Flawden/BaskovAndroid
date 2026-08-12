package ru.flawden.baskovmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.flawden.baskovmusic.playback.PlaybackSnapshot
import ru.flawden.baskovmusic.playback.PlaybackSnapshotCodec
import ru.flawden.baskovmusic.playback.PlaybackSnapshotItem

class PlaybackSnapshotCodecTest {
    @Test
    fun roundTripsQueueAndPosition() {
        val snapshot = PlaybackSnapshot(
            items = listOf(
                PlaybackSnapshotItem(
                    mediaId = "artist::track-1",
                    uri = "https://music.example.test/api/v1/playback/stream?guildId=1&title=One",
                    title = "One | Один",
                    artist = "Artist",
                ),
                PlaybackSnapshotItem(
                    mediaId = "artist::track-2",
                    uri = "https://music.example.test/api/v1/playback/stream?guildId=1&title=Two",
                    title = "Two",
                    artist = null,
                    artworkUri = "content://media/external/audio/albumart/7",
                ),
            ),
            currentIndex = 1,
            positionMillis = 42_500L,
            playWhenReady = true,
            savedAtEpochMillis = 1_786_479_000_000L,
        )

        assertEquals(snapshot, PlaybackSnapshotCodec.decode(PlaybackSnapshotCodec.encode(snapshot)))
    }

    @Test
    fun decodesLegacyV1SnapshotWithoutArtwork() {
        val legacy = "1|0|12345|0|1786479000000\n" +
            "bG9jYWw6NDI|Y29udGVudDovL21lZGlhL2V4dGVybmFsL2F1ZGlvL21lZGlhLzQy|U29uZw|QXJ0aXN0"

        val decoded = PlaybackSnapshotCodec.decode(legacy)

        assertEquals(12_345L, decoded?.positionMillis)
        assertEquals("content://media/external/audio/media/42", decoded?.items?.single()?.uri)
        assertNull(decoded?.items?.single()?.artworkUri)
    }

    @Test
    fun rejectsMalformedSnapshot() {
        assertNull(PlaybackSnapshotCodec.decode("not-a-valid-snapshot"))
    }
}
