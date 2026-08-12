package ru.flawden.baskovmusic.data.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.flawden.baskovmusic.data.api.ApiException
import ru.flawden.baskovmusic.data.api.BaskovApiClient
import ru.flawden.baskovmusic.model.SessionTokens

/**
 * Process-wide coordinator for access-token validation and refresh rotation.
 *
 * Both the UI repository and PlaybackService use this class. The shared mutex is important because
 * refresh tokens rotate: two independent refresh attempts with the same refresh token would race
 * and one side could invalidate the other side's freshly issued credentials.
 */
class AuthSessionManager(
    private val sessionStore: SessionStore,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun hasSession(): Boolean = sessionStore.tokens() != null && sessionStore.baseUrl() != null

    suspend fun requireFreshSession(minAccessValidityMillis: Long = 0L): SessionTokens {
        require(minAccessValidityMillis >= 0L) { "Minimum access-token validity must be non-negative" }
        val initial = requireTokens()
        if (hasEnoughAccessValidity(initial, minAccessValidityMillis)) return initial

        return refreshMutex.withLock {
            val current = requireTokens()
            if (hasEnoughAccessValidity(current, minAccessValidityMillis)) {
                current
            } else {
                refresh(current)
            }
        }
    }

    suspend fun <T> authenticated(block: suspend (BaskovApiClient, String) -> T): T {
        val baseUrl = requireBaseUrl()
        val client = BaskovApiClient(baseUrl)
        val initial = requireFreshSession(MIN_API_ACCESS_VALIDITY_MILLIS)
        return try {
            block(client, initial.accessToken)
        } catch (error: ApiException) {
            if (error.statusCode != 401) throw error
            refreshMutex.withLock {
                val current = requireTokens()
                if (current.accessToken != initial.accessToken) {
                    return@withLock block(client, current.accessToken)
                }
                val rotated = refresh(current, client)
                block(client, rotated.accessToken)
            }
        }
    }

    private suspend fun refresh(current: SessionTokens): SessionTokens =
        refresh(current, BaskovApiClient(requireBaseUrl()))

    private suspend fun refresh(
        current: SessionTokens,
        client: BaskovApiClient,
    ): SessionTokens {
        check(current.refreshExpiresAtEpochMillis > nowEpochMillis()) { "Device session has expired" }
        val rotated = client.refresh(current.refreshToken)
        sessionStore.saveTokens(rotated)
        return rotated
    }

    private fun hasEnoughAccessValidity(tokens: SessionTokens, minimumMillis: Long): Boolean =
        tokens.accessExpiresAtEpochMillis - nowEpochMillis() > minimumMillis

    private suspend fun requireBaseUrl(): String =
        sessionStore.baseUrl() ?: error("Baskov API address is not configured")

    private suspend fun requireTokens(): SessionTokens =
        sessionStore.tokens() ?: error("Device is not paired")

    private companion object {
        const val MIN_API_ACCESS_VALIDITY_MILLIS = 30_000L
        val refreshMutex = Mutex()
    }
}
