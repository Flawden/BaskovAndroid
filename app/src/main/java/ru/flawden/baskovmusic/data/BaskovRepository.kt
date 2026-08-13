package ru.flawden.baskovmusic.data

import ru.flawden.baskovmusic.data.api.BaskovApiClient
import ru.flawden.baskovmusic.data.auth.AuthSessionManager
import ru.flawden.baskovmusic.data.auth.SessionStore
import ru.flawden.baskovmusic.model.AccountInfo
import ru.flawden.baskovmusic.model.GuildSummary
import ru.flawden.baskovmusic.model.FavoriteSnapshot
import ru.flawden.baskovmusic.model.RemotePlayerSnapshot
import ru.flawden.baskovmusic.model.ServerHubItem
import ru.flawden.baskovmusic.model.HomeSnapshot
import ru.flawden.baskovmusic.model.LibrarySnapshot
import ru.flawden.baskovmusic.model.MixDetail
import ru.flawden.baskovmusic.model.MixesSnapshot
import ru.flawden.baskovmusic.model.PlaybackQueueItem
import ru.flawden.baskovmusic.model.PlaybackQueueSpec
import ru.flawden.baskovmusic.model.SharedPlaylistDetail
import ru.flawden.baskovmusic.model.SharedPlaylistSummary
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

    suspend fun favorites(guildId: String): FavoriteSnapshot =
        authenticated { client, token -> client.favorites(token, guildId) }

    suspend fun addFavorite(guildId: String, track: TrackPreview): FavoriteSnapshot =
        authenticated { client, token -> client.addFavorite(token, guildId, track) }

    suspend fun removeFavorite(guildId: String, oneBasedPosition: Int): FavoriteSnapshot =
        authenticated { client, token -> client.removeFavorite(token, guildId, oneBasedPosition) }

    suspend fun clearFavorites(guildId: String): FavoriteSnapshot =
        authenticated { client, token -> client.clearFavorites(token, guildId) }

    suspend fun player(guildId: String): RemotePlayerSnapshot =
        authenticated { client, token -> client.player(token, guildId) }

    suspend fun serverHubs(): List<ServerHubItem> = guilds().map { guild ->
        ServerHubItem(
            guild = guild,
            player = runCatching { player(guild.guildId) }.getOrNull(),
        )
    }

    suspend fun search(guildId: String, query: String): List<TrackPreview> =
        authenticated { client, token -> client.search(token, guildId, query) }

    suspend fun playlists(guildId: String): List<SharedPlaylistSummary> =
        authenticated { client, token -> client.playlists(token, guildId) }

    suspend fun playlist(guildId: String, name: String): SharedPlaylistDetail =
        authenticated { client, token -> client.playlist(token, guildId, name) }

    suspend fun createPlaylist(guildId: String, name: String): SharedPlaylistDetail =
        authenticated { client, token -> client.createPlaylist(token, guildId, name) }

    suspend fun addPlaylistTrack(
        guildId: String,
        playlistName: String,
        track: TrackPreview,
    ): SharedPlaylistDetail = authenticated { client, token ->
        client.addPlaylistTrack(token, guildId, playlistName, track)
    }

    suspend fun removePlaylistTrack(
        guildId: String,
        playlistName: String,
        oneBasedPosition: Int,
    ): SharedPlaylistDetail = authenticated { client, token ->
        client.removePlaylistTrack(token, guildId, playlistName, oneBasedPosition)
    }

    suspend fun movePlaylistTrack(
        guildId: String,
        playlistName: String,
        fromOneBasedPosition: Int,
        toOneBasedPosition: Int,
    ): SharedPlaylistDetail = authenticated { client, token ->
        client.movePlaylistTrack(token, guildId, playlistName, fromOneBasedPosition, toOneBasedPosition)
    }

    suspend fun renamePlaylist(
        guildId: String,
        playlistName: String,
        newName: String,
    ): SharedPlaylistDetail = authenticated { client, token ->
        client.renamePlaylist(token, guildId, playlistName, newName)
    }

    suspend fun deletePlaylist(guildId: String, playlistName: String): SharedPlaylistDetail =
        authenticated { client, token -> client.deletePlaylist(token, guildId, playlistName) }

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
