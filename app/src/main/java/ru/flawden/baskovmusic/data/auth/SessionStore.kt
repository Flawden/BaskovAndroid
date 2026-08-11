package ru.flawden.baskovmusic.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ru.flawden.baskovmusic.model.SessionTokens

private val Context.sessionDataStore by preferencesDataStore("baskov_session")

class SessionStore(
    private val context: Context,
    private val cipher: KeystoreCipher = KeystoreCipher(),
) {
    suspend fun saveBaseUrl(value: String) {
        context.sessionDataStore.edit { it[BASE_URL] = value }
    }

    suspend fun baseUrl(): String? = context.sessionDataStore.data.map { it[BASE_URL] }.first()

    suspend fun saveSelectedGuildId(value: String?) {
        context.sessionDataStore.edit {
            if (value == null) it.remove(SELECTED_GUILD_ID) else it[SELECTED_GUILD_ID] = value
        }
    }

    suspend fun selectedGuildId(): String? = context.sessionDataStore.data.map { it[SELECTED_GUILD_ID] }.first()

    suspend fun saveTokens(tokens: SessionTokens) {
        context.sessionDataStore.edit {
            it[USER_ID] = tokens.userId
            it[SESSION_ID] = tokens.sessionId
            it[DEVICE_NAME] = tokens.deviceName
            it[ACCESS_TOKEN] = cipher.encrypt(tokens.accessToken)
            it[REFRESH_TOKEN] = cipher.encrypt(tokens.refreshToken)
            it[ACCESS_EXPIRES] = tokens.accessExpiresAtEpochMillis.toString()
            it[REFRESH_EXPIRES] = tokens.refreshExpiresAtEpochMillis.toString()
        }
    }

    suspend fun tokens(): SessionTokens? {
        val prefs = context.sessionDataStore.data.first()
        val access = prefs[ACCESS_TOKEN] ?: return null
        val refresh = prefs[REFRESH_TOKEN] ?: return null
        return runCatching {
            SessionTokens(
                userId = prefs[USER_ID].orEmpty(),
                sessionId = prefs[SESSION_ID].orEmpty(),
                deviceName = prefs[DEVICE_NAME].orEmpty(),
                accessToken = cipher.decrypt(access),
                refreshToken = cipher.decrypt(refresh),
                accessExpiresAtEpochMillis = prefs[ACCESS_EXPIRES]?.toLongOrNull() ?: 0L,
                refreshExpiresAtEpochMillis = prefs[REFRESH_EXPIRES]?.toLongOrNull() ?: 0L,
            )
        }.getOrNull()
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit {
            it.remove(USER_ID)
            it.remove(SESSION_ID)
            it.remove(DEVICE_NAME)
            it.remove(ACCESS_TOKEN)
            it.remove(REFRESH_TOKEN)
            it.remove(ACCESS_EXPIRES)
            it.remove(REFRESH_EXPIRES)
            it.remove(SELECTED_GUILD_ID)
        }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val SELECTED_GUILD_ID = stringPreferencesKey("selected_guild_id")
        val USER_ID = stringPreferencesKey("user_id")
        val SESSION_ID = stringPreferencesKey("session_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val ACCESS_TOKEN = stringPreferencesKey("access_token_enc")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token_enc")
        val ACCESS_EXPIRES = stringPreferencesKey("access_expires")
        val REFRESH_EXPIRES = stringPreferencesKey("refresh_expires")
    }
}
