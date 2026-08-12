package ru.flawden.baskovmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.flawden.baskovmusic.playback.PlaybackArtworkUrl

class PlaybackArtworkUrlTest {
    @Test
    fun acceptsHttpAndHttpsArtwork() {
        assertEquals(
            "https://i.ytimg.com/vi/example/hqdefault.jpg",
            PlaybackArtworkUrl.normalize("https://i.ytimg.com/vi/example/hqdefault.jpg"),
        )
        assertEquals(
            "http://example.test/cover.jpg",
            PlaybackArtworkUrl.normalize("http://example.test/cover.jpg"),
        )
    }

    @Test
    fun rejectsUnsafeOrMalformedArtwork() {
        assertNull(PlaybackArtworkUrl.normalize("file:///tmp/cover.jpg"))
        assertNull(PlaybackArtworkUrl.normalize("/relative.jpg"))
        assertNull(PlaybackArtworkUrl.normalize("not a url"))
        assertNull(PlaybackArtworkUrl.normalize("   "))
        assertNull(PlaybackArtworkUrl.normalize(null))
    }
}
