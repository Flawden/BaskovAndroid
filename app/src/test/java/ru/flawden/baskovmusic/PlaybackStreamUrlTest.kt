package ru.flawden.baskovmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.flawden.baskovmusic.playback.PlaybackStreamUrl

class PlaybackStreamUrlTest {
    private val base =
        "https://music.example.test/api/v1/playback/stream?guildId=1&artist=A&title=Song"

    @Test
    fun addsAndReadsStartMillis() {
        val sought = PlaybackStreamUrl.withStartMillis(base, 137_000L)
        assertEquals("$base&startMillis=137000", sought)
        assertEquals(137_000L, PlaybackStreamUrl.startMillis(sought))
    }

    @Test
    fun replacesOldStartMillisWithoutDuplicatingIt() {
        val old = "$base&startMillis=15000"
        assertEquals(
            "$base&startMillis=42000",
            PlaybackStreamUrl.withStartMillis(old, 42_000L),
        )
    }

    @Test
    fun zeroStartRestoresCanonicalStreamUrl() {
        val sought = "$base&startMillis=15000"
        assertEquals(base, PlaybackStreamUrl.withStartMillis(sought, 0L))
        assertEquals(base, PlaybackStreamUrl.baseKey(sought))
    }

    @Test
    fun distinguishesRemoteStreamsFromLocalContentUris() {
        assertTrue(PlaybackStreamUrl.isRemoteHttp(base))
        assertTrue(PlaybackStreamUrl.isRemoteHttp("http://10.0.2.2:18080/stream"))
        assertFalse(PlaybackStreamUrl.isRemoteHttp("content://media/external/audio/media/42"))
        assertFalse(PlaybackStreamUrl.isRemoteHttp("file:///storage/emulated/0/Music/song.mp3"))
    }
}
