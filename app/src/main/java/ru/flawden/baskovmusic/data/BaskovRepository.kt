package ru.flawden.baskovmusic.data

import ru.flawden.baskovmusic.data.api.BaskovApiClient
import ru.flawden.baskovmusic.data.auth.AuthSessionManager
import ru.flawden.baskovmusic.data.auth.SessionStore
import ru.flawden.baskovmusic.model.AccountInfo
import ru.flawden.baskovmusic.model.GuildSummary
import ru.flawden.baskovmusic.model.HomeSnapshot
import ru.flawden.baskovmusic.model.LibrarySnapshot
import ru.flawden.baskovmusic.model.MixDetail
import ru.flawden.baskovmusic.model.MixesSnapshot
import ru.flawden.baskovmusic.model.PlaybackQueueItem
import ru.flawden.baskovmusic.model.PlaybackQueueSpec
import ru.flawden.baskovmusic.model.TrackPreview

class BaskovRepository(
    private val sessionStore: SessionStore,
) {
    private val authSessionManager = AuthSessionManager(sessionStore)

    suspend fun hasSession(): Boolean = authSessionManager.hasSession()
    suspend fun savedBaseUrl(): String? = sessionStore.baseUrl()
    suspend fun selectedGuildId(): String? = sessionStore.selectedGuildId()

    suspend fun pair(baseUrl: String, code: String, deviceName: String) {
        val tokens = BaskovApiClient(baseUrl).pair(code, deviceName)
        sessionStore.saveBaseUrl(baseUrl)
        sessionStore.saveTokens(tokens)
        sessionStore.saveSelectedGuildId(null)
    }

    suspend fun me(): AccountInfo = authenticated { client, token -> client.me(token) }
    suspend fun guilds(): List<GuildSummary> = authenticated { client, token -> client.guilds(token) }
    suspend fun home(guildId: String): HomeSnapshot = authenticated { client, token -> client.home(token, guildId) }
    suspend fun library(guildId: String): LibrarySnapshot =
        authenticated { client, token -> client.library(token, guildId) }

    suspend fun mixes(guildId: String): MixesSnapshot =
        authenticated { client, token -> client.mixes(token, guildId) }

    suspend fun mixDetail(guildId: String, stationSlug: String): MixDetail =
        authenticated { client, token -> client.mixDetail(token, guildId, stationSlug) }

    suspend fun playbackQueue(guildId: String, tracks: List<TrackPreview>): PlaybackQueueSpec {
        val playable = tracks.filter { !it.title.isNullOrBlank() }
        require(playable.isNotEmpty()) { "Playback queue is empty" }

        // Validate the current device session first, then proactively rotate an access token that
        // is too close to expiry for a background playback queue. PlaybackService keeps doing the
        // same check while a queue is active.
        authenticated { client, token -> client.me(token) }
        val session = authSessionManager.requireFreshSession(MIN_PLAYBACK_ACCESS_VALIDITY_MILLIS)
        val client = BaskovApiClient(requireNotNull(sessionStore.baseUrl()))
        return PlaybackQueueSpec(
            bearerToken = session.accessToken,
            items = playable.map { track ->
                PlaybackQueueItem(
                    track = track,
                    streamUrl = client.playbackStreamUrl(
                        guildId = guildId,
                        artist = track.artist.orEmpty().ifBlank { "Неизвестно" },
                        title = requireNotNull(track.title),
                    ),
                )
            },
        )
    }

    suspend fun selectGuild(guildId: String) = sessionStore.saveSelectedGuildId(guildId)

    suspend fun logout() {
        runCatching { authenticated<Unit> { client, token -> client.logout(token) } }
        sessionStore.clearSession()
    }

    private suspend fun <T> authenticated(block: suspend (BaskovApiClient, String) -> T): T =
        authSessionManager.authenticated(block)

    private companion object {
        const val MIN_PLAYBACK_ACCESS_VALIDITY_MILLIS = 5 * 60_000L
    }
}
