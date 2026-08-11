package ru.flawden.baskovmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.flawden.baskovmusic.data.api.ServerUrlPolicy

class ServerUrlPolicyTest {
    @Test
    fun releaseRequiresHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerUrlPolicy.normalize("http://example.test", allowCleartext = false)
        }
    }

    @Test
    fun debugAllowsLocalHttp() {
        assertEquals(
            "http://10.0.2.2:18080/",
            ServerUrlPolicy.normalize("http://10.0.2.2:18080/", allowCleartext = true),
        )
    }

    @Test
    fun normalizesTrailingSlash() {
        assertEquals(
            "https://music.example.test/",
            ServerUrlPolicy.normalize(" https://music.example.test/// ", allowCleartext = false),
        )
    }
}
