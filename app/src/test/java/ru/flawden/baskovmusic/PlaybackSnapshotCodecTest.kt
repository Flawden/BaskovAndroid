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
    fun rejectsMalformedSnapshot() {
        assertNull(PlaybackSnapshotCodec.decode("not-a-valid-snapshot"))
    }
}
