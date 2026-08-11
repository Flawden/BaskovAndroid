package ru.flawden.baskovmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.flawden.baskovmusic.data.api.PairingCodePolicy

class PairingCodePolicyTest {
    @Test
    fun normalizesLowercaseCode() {
        assertEquals("7KQ9WM2P", PairingCodePolicy.normalize(" 7kq9wm2p "))
    }

    @Test
    fun rejectsWrongLength() {
        assertThrows(IllegalArgumentException::class.java) { PairingCodePolicy.normalize("ABC123") }
    }

    @Test
    fun rejectsSymbols() {
        assertThrows(IllegalArgumentException::class.java) { PairingCodePolicy.normalize("ABC-1234") }
    }
}
