package ru.flawden.baskovmusic.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.flawden.baskovmusic.model.AccountInfo
import ru.flawden.baskovmusic.model.GuildSummary
import ru.flawden.baskovmusic.model.FavoriteSnapshot
import ru.flawden.baskovmusic.model.RemotePlayerSnapshot
import ru.flawden.baskovmusic.model.HomeSnapshot
import ru.flawden.baskovmusic.model.LibrarySnapshot
import ru.flawden.baskovmusic.model.LibrarySummary
import ru.flawden.baskovmusic.model.MixCard
import ru.flawden.baskovmusic.model.MixDetail
import ru.flawden.baskovmusic.model.MixesSnapshot
import ru.flawden.baskovmusic.model.SessionTokens
import ru.flawden.baskovmusic.model.SharedPlaylistDetail
import ru.flawden.baskovmusic.model.SharedPlaylistSummary
import ru.flawden.baskovmusic.model.TasteSignal
import ru.flawden.baskovmusic.model.TasteSummary
import ru.flawden.baskovmusic.model.ThemeAffinity
import ru.flawden.baskovmusic.model.TrackPreview
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class BaskovApiClient(
    private val baseUrl: String,
) {
    suspend fun pair(code: String, deviceName: String): SessionTokens =
        requestJson(
            method = "POST",
            path = "api/v1/auth/device/pair",
            body = JSONObject()
                .put("code", PairingCodePolicy.normalize(code))
                .put("deviceName", deviceName.trim()),
        ).toTokens()

    suspend fun refresh(refreshToken: String): SessionTokens =
        requestJson(
            method = "POST",
            path = "api/v1/auth/refresh",
            body = JSONObject().put("refreshToken", refreshToken),
        ).toTokens()

    suspend fun me(accessToken: String): AccountInfo =
        requestJson("GET", "api/v1/auth/me", accessToken = accessToken).let {
            AccountInfo(
                userId = it.getString("userId"),
                displayName = it.getString("displayName"),
                sessionId = it.getString("sessionId"),
                deviceName = it.getString("deviceName"),
            )
        }

    suspend fun guilds(accessToken: String): List<GuildSummary> {
        val root = requestJson("GET", "api/v1/guilds", accessToken = accessToken)
        val rows = root.getJSONArray("guilds")
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.getJSONObject(index)
                add(GuildSummary(item.getString("guildId"), item.getString("name")))
            }
        }
    }

    suspend fun home(accessToken: String, guildId: String): HomeSnapshot =
        requestJson(
            "GET",
            "api/v1/home?guildId=${encodeQuery(guildId)}",
            accessToken = accessToken,
        ).toHome()

    suspend fun library(accessToken: String, guildId: String): LibrarySnapshot =
        requestJson(
            "GET",
            "api/v1/library?guildId=${encodeQuery(guildId)}",
            accessToken = accessToken,
        ).toLibrary()

    suspend fun favorites(
        accessToken: String,
        guildId: String,
        offset: Int? = null,
        limit: Int? = null,
    ): FavoriteSnapshot {
        val paging = buildString {
            offset?.let { append("&offset=${it.coerceAtLeast(0)}") }
            limit?.let { append("&limit=${it.coerceIn(1, 100)}") }
        }
        return requestJson(
            "GET",
            "api/v1/favorites?guildId=${encodeQuery(guildId)}$paging",
            accessToken = accessToken,
        ).toFavorites()
    }

    suspend fun favoriteKeys(accessToken: String, guildId: String): Set<String> =
        requestJson(
            "GET",
            "api/v1/favorites/keys?guildId=${encodeQuery(guildId)}",
            accessToken = accessToken,
        ).getJSONArray("stableKeys").let { rows ->
            buildSet {
                for (index in 0 until rows.length()) add(rows.getString(index))
            }
        }

    suspend fun favoriteStatus(accessToken: String, guildId: String, stableKey: String): Boolean =
        requestJson(
            "GET",
            "api/v1/favorites/status?guildId=${encodeQuery(guildId)}&stableKey=${encodeQuery(stableKey)}",
            accessToken = accessToken,
        ).optBoolean("favorite", false)

    suspend fun addFavorite(
        accessToken: String,
        guildId: String,
        track: TrackPreview,
    ): FavoriteSnapshot = requestJson(
        "POST",
        "api/v1/favorites?guildId=${encodeQuery(guildId)}",
        body = JSONObject()
            .put("artist", track.artist.orEmpty().ifBlank { "Неизвестно" })
            .put("title", requireNotNull(track.title) { "Track title is required" }),
        accessToken = accessToken,
    ).toFavorites()

    suspend fun removeFavorite(
        accessToken: String,
        guildId: String,
        oneBasedPosition: Int,
    ): FavoriteSnapshot = requestJson(
        "DELETE",
        "api/v1/favorites/$oneBasedPosition?guildId=${encodeQuery(guildId)}",
        accessToken = accessToken,
    ).toFavorites()

    suspend fun removeFavoriteByStableKey(
        accessToken: String,
        guildId: String,
        stableKey: String,
    ): Boolean = !requestJson(
        "DELETE",
        "api/v1/favorites/by-key?guildId=${encodeQuery(guildId)}&stableKey=${encodeQuery(stableKey)}",
        accessToken = accessToken,
    ).optBoolean("favorite", true)

    suspend fun clearFavorites(accessToken: String, guildId: String): FavoriteSnapshot =
        requestJson(
            "DELETE",
            "api/v1/favorites?guildId=${encodeQuery(guildId)}",
            accessToken = accessToken,
        ).toFavorites()

    suspend fun recordTasteSignals(
        accessToken: String,
        guildId: String,
        events: List<TasteSignal>,
    ) {
        if (events.isEmpty()) return
        require(events.size <= 50) { "Taste signal batch cannot exceed 50 events" }
        val rows = JSONArray()
        events.forEach { event ->
            rows.put(
                JSONObject()
                    .put("type", event.type.name)
                    .put("source", event.source.name)
                    .apply {
                        event.stableKey?.takeIf(String::isNotBlank)?.let { put("stableKey", it) }
                    }
                    .put("artist", event.artist)
                    .put("title", event.title)
                    .put("completionRatio", event.completionRatio.coerceIn(0.0, 1.0)),
            )
        }
        requestJson(
            "POST",
            "api/v1/taste/events?guildId=${encodeQuery(guildId)}",
            body = JSONObject().put("events", rows),
            accessToken = accessToken,
        )
    }

    suspend fun player(accessToken: String, guildId: String): RemotePlayerSnapshot =
        requestJson(
            "GET",
            "api/v1/player?guildId=${encodeQuery(guildId)}",
            accessToken = accessToken,
        ).toPlayer()

    suspend fun search(accessToken: String, guildId: String, query: String): List<TrackPreview> =
        requestJson(
            "GET",
            "api/v1/search?guildId=${encodeQuery(guildId)}&query=${encodeQuery(query)}&limit=5",
            accessToken = accessToken,
        ).getJSONArray("tracks").mapTracks()

    suspend fun playlists(accessToken: String, guildId: String): List<SharedPlaylistSummary> =
        requestJson(
            "GET",
            "api/v1/playlists?guildId=${encodeQuery(guildId)}",
            accessToken = accessToken,
        ).getJSONArray("playlists").mapPlaylists()

    suspend fun playlist(accessToken: String, guildId: String, name: String): SharedPlaylistDetail =
        requestJson(
            "GET",
            "api/v1/playlists/${encodePathSegment(name)}?guildId=${encodeQuery(guildId)}",
            accessToken = accessToken,
        ).toPlaylistDetail()

    suspend fun createPlaylist(accessToken: String, guildId: String, name: String): SharedPlaylistDetail =
        requestJson(
            "POST",
            "api/v1/playlists?guildId=${encodeQuery(guildId)}",
            body = JSONObject().put("name", name.trim()),
            accessToken = accessToken,
        ).toPlaylistDetail()

    suspend fun addPlaylistTrack(
        accessToken: String,
        guildId: String,
        playlistName: String,
        track: TrackPreview,
    ): SharedPlaylistDetail = requestJson(
        "POST",
        "api/v1/playlists/${encodePathSegment(playlistName)}/tracks?guildId=${encodeQuery(guildId)}",
        body = JSONObject()
            .put("artist", track.artist.orEmpty().ifBlank { "Неизвестно" })
            .put("title", requireNotNull(track.title) { "Track title is required" }),
        accessToken = accessToken,
    ).toPlaylistDetail()

    suspend fun removePlaylistTrack(
        accessToken: String,
        guildId: String,
        playlistName: String,
        oneBasedPosition: Int,
    ): SharedPlaylistDetail = requestJson(
        "DELETE",
        "api/v1/playlists/${encodePathSegment(playlistName)}/tracks/$oneBasedPosition?guildId=${encodeQuery(guildId)}",
        accessToken = accessToken,
    ).toPlaylistDetail()

    suspend fun movePlaylistTrack(
        accessToken: String,
        guildId: String,
        playlistName: String,
        fromOneBasedPosition: Int,
        toOneBasedPosition: Int,
    ): SharedPlaylistDetail = requestJson(
        "POST",
        "api/v1/playlists/${encodePathSegment(playlistName)}/move?guildId=${encodeQuery(guildId)}",
        body = JSONObject()
            .put("from", fromOneBasedPosition)
            .put("to", toOneBasedPosition),
        accessToken = accessToken,
    ).toPlaylistDetail()

    suspend fun renamePlaylist(
        accessToken: String,
        guildId: String,
        playlistName: String,
        newName: String,
    ): SharedPlaylistDetail = requestJson(
        "POST",
        "api/v1/playlists/${encodePathSegment(playlistName)}/rename?guildId=${encodeQuery(guildId)}",
        body = JSONObject().put("newName", newName.trim()),
        accessToken = accessToken,
    ).toPlaylistDetail()

    suspend fun deletePlaylist(
        accessToken: String,
        guildId: String,
        playlistName: String,
    ): SharedPlaylistDetail = requestJson(
        "DELETE",
        "api/v1/playlists/${encodePathSegment(playlistName)}?guildId=${encodeQuery(guildId)}",
        accessToken = accessToken,
    ).toPlaylistDetail()

    suspend fun mixes(accessToken: String, guildId: String): MixesSnapshot =
        requestJson(
            "GET",
            "api/v1/mixes?guildId=${encodeQuery(guildId)}",
            accessToken = accessToken,
        ).toMixes()

    suspend fun mixDetail(accessToken: String, guildId: String, stationSlug: String): MixDetail =
        requestJson(
            "GET",
            "api/v1/mixes/${encodePathSegment(stationSlug)}?guildId=${encodeQuery(guildId)}",
            accessToken = accessToken,
        ).toMixDetail()

    fun playbackStreamUrl(guildId: String, artist: String, title: String): String =
        baseUrl + "api/v1/playback/stream" +
            "?guildId=${encodeQuery(guildId)}" +
            "&artist=${encodeQuery(artist)}" +
            "&title=${encodeQuery(title)}"

    suspend fun logout(accessToken: String) {
        requestJson("POST", "api/v1/auth/logout", accessToken = accessToken)
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        accessToken: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) {
                throw ApiException(
                    statusCode = status,
                    apiCode = json.optString("code").takeIf(String::isNotBlank),
                    message = json.optString("message").takeIf(String::isNotBlank)
                        ?: "Baskov API вернул HTTP $status",
                )
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toTokens() = SessionTokens(
        userId = getString("userId"),
        sessionId = getString("sessionId"),
        deviceName = getString("deviceName"),
        accessToken = getString("accessToken"),
        refreshToken = getString("refreshToken"),
        accessExpiresAtEpochMillis = getLong("accessExpiresAtEpochMillis"),
        refreshExpiresAtEpochMillis = getLong("refreshExpiresAtEpochMillis"),
    )

    private fun JSONObject.toHome() = HomeSnapshot(
        guildId = getString("guildId"),
        userId = getString("userId"),
        date = getString("date"),
        today = getJSONArray("today").mapMixes(),
        forYou = getJSONArray("forYou").mapMixes(),
        themes = getJSONArray("themes").mapThemes(),
        library = getJSONObject("library").let { library ->
            LibrarySummary(
                favorites = library.getInt("favorites"),
                personalHistory = library.getInt("personalHistory"),
                recent = library.getJSONArray("recent").mapTracks(),
            )
        },
        recent = getJSONArray("recent").mapTracks(),
        taste = getJSONObject("taste").let { taste ->
            TasteSummary(
                evidenceSignals = taste.optInt("evidenceSignals", 0),
                confidence = taste.optDouble("confidence", 0.0),
                recommendations = taste.optInt("recommendations", 0),
            )
        },
    )

    private fun JSONObject.toLibrary() = LibrarySnapshot(
        guildId = getString("guildId"),
        userId = getString("userId"),
        favorites = getInt("favorites"),
        personalHistory = getInt("personalHistory"),
        recent = getJSONArray("recent").mapTracks(),
        favoriteTracks = getJSONArray("favoriteTracks").mapTracks(),
        historyTracks = getJSONArray("historyTracks").mapTracks(),
    )

    private fun JSONObject.toFavorites(): FavoriteSnapshot {
        val tracks = getJSONArray("tracks").mapTracks()
        return FavoriteSnapshot(
            guildId = getString("guildId"),
            userId = getString("userId"),
            total = optInt("total", tracks.size),
            offset = optInt("offset", 0),
            limit = optInt("limit", tracks.size),
            hasMore = optBoolean("hasMore", false),
            tracks = tracks,
        )
    }

    private fun JSONObject.toPlayer() = RemotePlayerSnapshot(
        guildId = getString("guildId"),
        sessionActive = optBoolean("sessionActive", false),
        playing = optBoolean("playing", false),
        paused = optBoolean("paused", false),
        queueSize = optInt("queueSize", 0),
        current = optJSONObject("current")?.toTrack(),
    )

    private fun JSONObject.toPlaylistSummary() = SharedPlaylistSummary(
        name = getString("name"),
        ownerUserId = getString("ownerUserId"),
        ownedByMe = optBoolean("ownedByMe", false),
        trackCount = optInt("trackCount", 0),
        createdAtEpochMillis = optLong("createdAtEpochMillis", 0L),
    )

    private fun JSONObject.toPlaylistDetail() = SharedPlaylistDetail(
        guildId = getString("guildId"),
        userId = getString("userId"),
        name = getString("name"),
        ownerUserId = getString("ownerUserId"),
        ownedByMe = optBoolean("ownedByMe", false),
        createdAtEpochMillis = optLong("createdAtEpochMillis", 0L),
        tracks = getJSONArray("tracks").mapTracks(),
    )

    private fun JSONObject.toMixes() = MixesSnapshot(
        guildId = getString("guildId"),
        userId = getString("userId"),
        date = getString("date"),
        today = getJSONArray("today").mapMixes(),
        forYou = getJSONArray("forYou").mapMixes(),
        themes = getJSONArray("themes").mapThemes(),
    )

    private fun JSONObject.toMixDetail() = MixDetail(
        guildId = getString("guildId"),
        userId = getString("userId"),
        stationSlug = getString("stationSlug"),
        label = optString("label").nullIfBlank() ?: getString("stationSlug"),
        description = optString("description").nullIfBlank(),
        available = optBoolean("available", false),
        daily = optBoolean("daily", false),
        seedPreview = getJSONArray("seedPreview").mapTracks(),
    )

    private fun JSONObject.toMix() = MixCard(
        stationSlug = optString("stationSlug").nullIfBlank(),
        label = optString("label").nullIfBlank(),
        description = optString("description").nullIfBlank(),
        available = optBoolean("available", false),
        daily = optBoolean("daily", false),
    )

    private fun JSONObject.toTrack() = TrackPreview(
        stableKey = optString("stableKey").nullIfBlank(),
        title = optString("title").nullIfBlank(),
        artist = optString("artist").nullIfBlank(),
    )

    private fun JSONArray.mapMixes(): List<MixCard> = buildList {
        for (index in 0 until length()) add(getJSONObject(index).toMix())
    }

    private fun JSONArray.mapThemes(): List<ThemeAffinity> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(ThemeAffinity(item.optString("name").nullIfBlank(), item.optDouble("affinity", 0.0)))
        }
    }

    private fun JSONArray.mapTracks(): List<TrackPreview> = buildList {
        for (index in 0 until length()) add(getJSONObject(index).toTrack())
    }

    private fun JSONArray.mapPlaylists(): List<SharedPlaylistSummary> = buildList {
        for (index in 0 until length()) add(getJSONObject(index).toPlaylistSummary())
    }

    private fun encodeQuery(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }
}
