package ru.flawden.baskovmusic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalTasteIdentityTest {

    @Test
    fun fingerprintIgnoresMediaStoreIdentityButKeepsMetadataIdentity() {
        val first = LocalTrack(
            id = 10L,
            title = " Monster ",
            artist = "SKILLET",
            album = "Awake",
            durationMillis = 178_000L,
            contentUri = "content://media/10",
            artworkUri = null,
            folderPath = "/Music/A",
        )
        val sameMetadataDifferentRow = first.copy(
            id = 999L,
            title = "monster",
            artist = "skillet",
            contentUri = "content://media/999",
            folderPath = "/Moved",
        )
        val differentDuration = first.copy(durationMillis = 179_000L)

        assertEquals(first.tasteStableKey, sameMetadataDifferentRow.tasteStableKey)
        assertNotEquals(first.tasteStableKey, differentDuration.tasteStableKey)
        assertEquals(first.tasteStableKey, first.preview.stableKey)
    }
}
