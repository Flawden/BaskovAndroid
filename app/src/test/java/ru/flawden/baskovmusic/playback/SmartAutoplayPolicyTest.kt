package ru.flawden.baskovmusic.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.flawden.baskovmusic.model.TrackPreview

class SmartAutoplayPolicyTest {
    @Test
    fun continuesOnlyForRemoteQueueExhaustionWithRepeatOff() {
        assertTrue(SmartAutoplayPolicy.shouldContinue(remote = true, repeatModeOff = true, hasSeed = true))
        assertFalse(SmartAutoplayPolicy.shouldContinue(remote = false, repeatModeOff = true, hasSeed = true))
        assertFalse(SmartAutoplayPolicy.shouldContinue(remote = true, repeatModeOff = false, hasSeed = true))
        assertFalse(SmartAutoplayPolicy.shouldContinue(remote = true, repeatModeOff = true, hasSeed = false))
    }

    @Test
    fun rejectsStableKeyOrLogicalSelfLoop() {
        val sameKey = TrackPreview("track:green-day:holiday", "Different", "Different")
        assertTrue(
            SmartAutoplayPolicy.sameLogicalTrack(
                "track:green-day:holiday",
                "Green Day",
                "Holiday",
                sameKey,
            ),
        )

        val sameLogical = TrackPreview(null, "  HOLIDAY ", "green   day")
        assertTrue(
            SmartAutoplayPolicy.sameLogicalTrack(
                null,
                "Green Day",
                "Holiday",
                sameLogical,
            ),
        )

        val different = TrackPreview(null, "The Kids Aren't Alright", "The Offspring")
        assertFalse(
            SmartAutoplayPolicy.sameLogicalTrack(
                null,
                "Green Day",
                "Holiday",
                different,
            ),
        )
    }
}
