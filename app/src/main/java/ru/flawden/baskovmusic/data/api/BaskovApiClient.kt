package ru.flawden.baskovmusic.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.flawden.baskovmusic.model.AccountInfo
import ru.flawden.baskovmusic.model.GuildSummary
import ru.flawden.baskovmusic.model.HomeSnapshot
import ru.flawden.baskovmusic.model.LibrarySummary
import ru.flawden.baskovmusic.model.MixCard
import ru.flawden.baskovmusic.model.SessionTokens
import ru.flawden.baskovmusic.model.TasteSummary
import ru.flawden.baskovmusic.model.ThemeAffinity
import ru.flawden.baskovmusic.model.TrackPreview
import java.net.HttpURLConnection
import java.net.URL

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

    suspend fun home(accessToken: String, guildId: String): HomeSnapshot {
        val root = requestJson(
            "GET",
            "api/v1/home?guildId=${encodeQuery(guildId)}",
            accessToken = accessToken,
        )
        return root.toHome()
    }

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

    private fun encodeQuery(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }
}
