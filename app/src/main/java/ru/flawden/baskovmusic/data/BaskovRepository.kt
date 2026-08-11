package ru.flawden.baskovmusic.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.flawden.baskovmusic.data.api.ApiException
import ru.flawden.baskovmusic.data.api.BaskovApiClient
import ru.flawden.baskovmusic.data.auth.SessionStore
import ru.flawden.baskovmusic.model.AccountInfo
import ru.flawden.baskovmusic.model.GuildSummary
import ru.flawden.baskovmusic.model.HomeSnapshot

class BaskovRepository(
    private val sessionStore: SessionStore,
) {
    private val refreshMutex = Mutex()

    suspend fun hasSession(): Boolean = sessionStore.tokens() != null && sessionStore.baseUrl() != null
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

    suspend fun selectGuild(guildId: String) = sessionStore.saveSelectedGuildId(guildId)

    suspend fun logout() {
        runCatching { authenticated<Unit> { client, token -> client.logout(token) } }
        sessionStore.clearSession()
    }

    private suspend fun <T> authenticated(block: suspend (BaskovApiClient, String) -> T): T {
        val baseUrl = sessionStore.baseUrl() ?: error("Baskov API address is not configured")
        val client = BaskovApiClient(baseUrl)
        val initial = sessionStore.tokens() ?: error("Device is not paired")
        return try {
            block(client, initial.accessToken)
        } catch (error: ApiException) {
            if (error.statusCode != 401) throw error
            refreshMutex.withLock {
                val current = sessionStore.tokens() ?: throw error
                if (current.accessToken != initial.accessToken) {
                    return@withLock block(client, current.accessToken)
                }
                val rotated = client.refresh(current.refreshToken)
                sessionStore.saveTokens(rotated)
                block(client, rotated.accessToken)
            }
        }
    }
}
