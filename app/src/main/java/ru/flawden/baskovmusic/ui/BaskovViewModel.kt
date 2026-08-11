package ru.flawden.baskovmusic.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.flawden.baskovmusic.BuildConfig
import ru.flawden.baskovmusic.data.BaskovRepository
import ru.flawden.baskovmusic.data.api.PairingCodePolicy
import ru.flawden.baskovmusic.data.api.ServerUrlPolicy
import ru.flawden.baskovmusic.data.auth.SessionStore
import ru.flawden.baskovmusic.model.GuildSummary

class BaskovViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BaskovRepository(SessionStore(application))
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    init {
        bootstrap()
    }

    fun pair(baseUrlRaw: String, code: String) = launchBusy {
        val normalizedCode = PairingCodePolicy.normalize(code)
        val baseUrl = ServerUrlPolicy.normalize(baseUrlRaw, allowCleartext = BuildConfig.DEBUG)
        repository.pair(baseUrl, normalizedCode, defaultDeviceName())
        loadAuthenticatedState()
    }

    fun chooseGuild(guild: GuildSummary) = launchBusy {
        repository.selectGuild(guild.guildId)
        val account = repository.me()
        val snapshot = repository.home(guild.guildId)
        mutableState.value = AppUiState(screen = AppScreen.Home(account, guild, snapshot))
    }

    fun refreshHome() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Home ?: return@launchBusy
        val snapshot = repository.home(current.guild.guildId)
        mutableState.value = AppUiState(screen = current.copy(snapshot = snapshot))
    }

    fun changeGuild() = launchBusy {
        val account = repository.me()
        val guilds = repository.guilds()
        mutableState.value = AppUiState(screen = AppScreen.GuildPicker(account, guilds))
    }

    fun logout() = launchBusy {
        repository.logout()
        mutableState.value = AppUiState(screen = AppScreen.Pairing(repository.savedBaseUrl().orEmpty()))
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    private fun bootstrap() = viewModelScope.launch {
        runCatching {
            if (repository.hasSession()) loadAuthenticatedState()
            else mutableState.value = AppUiState(screen = AppScreen.Pairing(repository.savedBaseUrl().orEmpty()))
        }.onFailure {
            repository.logout()
            mutableState.value = AppUiState(
                screen = AppScreen.Pairing(repository.savedBaseUrl().orEmpty()),
                error = friendlyMessage(it),
            )
        }
    }

    private suspend fun loadAuthenticatedState() {
        val account = repository.me()
        val guilds = repository.guilds()
        if (guilds.isEmpty()) {
            mutableState.value = AppUiState(
                screen = AppScreen.GuildPicker(account, emptyList()),
                error = "Linked Discord account is not present in any Baskov guild.",
            )
            return
        }
        val selectedId = repository.selectedGuildId()
        val selected = guilds.firstOrNull { it.guildId == selectedId }
        if (selected == null) {
            mutableState.value = AppUiState(screen = AppScreen.GuildPicker(account, guilds))
            return
        }
        mutableState.value = AppUiState(
            screen = AppScreen.Home(account, selected, repository.home(selected.guildId)),
        )
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        runCatching { block() }
            .onFailure { mutableState.value = mutableState.value.copy(error = friendlyMessage(it)) }
        mutableState.value = mutableState.value.copy(busy = false)
    }

    private fun defaultDeviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Android device" }
        .take(80)

    private fun friendlyMessage(error: Throwable): String = error.message ?: error::class.java.simpleName
}
