package ru.flawden.baskovmusic.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.flawden.baskovmusic.BuildConfig
import ru.flawden.baskovmusic.data.BaskovRepository
import ru.flawden.baskovmusic.data.api.PairingCodePolicy
import ru.flawden.baskovmusic.data.api.ServerUrlPolicy
import ru.flawden.baskovmusic.data.auth.SessionStore
import ru.flawden.baskovmusic.model.AccountInfo
import ru.flawden.baskovmusic.model.GuildSummary
import ru.flawden.baskovmusic.model.MixCard
import ru.flawden.baskovmusic.model.TrackPreview
import ru.flawden.baskovmusic.playback.LocalPlaybackController
import ru.flawden.baskovmusic.playback.LocalPlaybackUiState

class BaskovViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BaskovRepository(SessionStore(application))
    private val mutableState = MutableStateFlow(AppUiState())
    private val backStack = ArrayDeque<AppScreen>()
    private val playback = LocalPlaybackController(application)

    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    val playbackState: StateFlow<LocalPlaybackUiState> = playback.state

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
        playback.stop()
        repository.selectGuild(guild.guildId)
        val account = repository.me()
        val snapshot = repository.home(guild.guildId)
        backStack.clear()
        setScreen(AppScreen.Home(account, guild, snapshot))
    }

    fun refreshHome() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Home ?: return@launchBusy
        val snapshot = repository.home(current.guild.guildId)
        setScreen(current.copy(snapshot = snapshot))
    }

    fun openLibrary() = launchBusy {
        val context = currentContext() ?: return@launchBusy
        val snapshot = repository.library(context.guild.guildId)
        navigate(AppScreen.Library(context.account, context.guild, snapshot))
    }

    fun refreshLibrary() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Library ?: return@launchBusy
        val snapshot = repository.library(current.guild.guildId)
        setScreen(current.copy(snapshot = snapshot))
    }

    fun openMixes() = launchBusy {
        val context = currentContext() ?: return@launchBusy
        val snapshot = repository.mixes(context.guild.guildId)
        navigate(AppScreen.Mixes(context.account, context.guild, snapshot))
    }

    fun refreshMixes() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Mixes ?: return@launchBusy
        val snapshot = repository.mixes(current.guild.guildId)
        setScreen(current.copy(snapshot = snapshot))
    }

    fun openMix(mix: MixCard) {
        val stationSlug = mix.stationSlug
        if (stationSlug.isNullOrBlank()) {
            setError("У этого микса нет stationSlug, открыть его нельзя.")
            return
        }
        openMix(stationSlug)
    }

    private fun openMix(stationSlug: String) = launchBusy {
        val context = currentContext() ?: return@launchBusy
        val detail = repository.mixDetail(context.guild.guildId, stationSlug)
        navigate(AppScreen.Mix(context.account, context.guild, detail))
    }

    fun refreshMix() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Mix ?: return@launchBusy
        val detail = repository.mixDetail(current.guild.guildId, current.detail.stationSlug)
        setScreen(current.copy(detail = detail))
    }

    fun openTrack(track: TrackPreview) {
        if (mutableState.value.busy) return
        val context = currentContext() ?: return
        navigate(AppScreen.Track(context.account, context.guild, track))
    }

    fun playTrack(track: TrackPreview) = launchBusy {
        val context = currentContext() ?: return@launchBusy
        val queue = playbackQueueFor(track)
        val startIndex = queue.indexOfFirst { sameTrack(it, track) }.coerceAtLeast(0)
        playback.play(repository.playbackQueue(context.guild.guildId, queue), startIndex)
    }

    fun togglePlayback() = playback.togglePlayPause()
    fun nextTrack() = playback.next()
    fun previousTrack() = playback.previous()
    fun playQueueItem(index: Int) = playback.playQueueItem(index)
    fun removeQueueItem(index: Int) = playback.removeQueueItem(index)
    fun stopPlayback() = playback.stop()
    fun clearPlaybackError() = playback.clearError()

    fun goBack() {
        if (mutableState.value.busy || backStack.isEmpty()) return
        setScreen(backStack.removeLast())
    }

    fun changeGuild() = launchBusy {
        playback.stop()
        val account = repository.me()
        val guilds = repository.guilds()
        backStack.clear()
        setScreen(AppScreen.GuildPicker(account, guilds))
    }

    fun logout() = launchBusy {
        playback.stop()
        repository.logout()
        backStack.clear()
        setScreen(AppScreen.Pairing(repository.savedBaseUrl().orEmpty()))
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    private fun bootstrap() = viewModelScope.launch {
        runCatching {
            if (repository.hasSession()) {
                loadAuthenticatedState()
            } else {
                playback.stop()
                setScreen(AppScreen.Pairing(repository.savedBaseUrl().orEmpty()))
            }
        }.onFailure {
            playback.stop()
            repository.logout()
            backStack.clear()
            mutableState.value = AppUiState(
                screen = AppScreen.Pairing(repository.savedBaseUrl().orEmpty()),
                error = friendlyMessage(it),
            )
        }
    }

    private suspend fun loadAuthenticatedState() {
        val account = repository.me()
        val guilds = repository.guilds()
        backStack.clear()
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
            setScreen(AppScreen.GuildPicker(account, guilds))
            return
        }
        setScreen(AppScreen.Home(account, selected, repository.home(selected.guildId)))
    }

    private fun currentContext(): ScreenContext? = when (val screen = mutableState.value.screen) {
        is AppScreen.Home -> ScreenContext(screen.account, screen.guild)
        is AppScreen.Library -> ScreenContext(screen.account, screen.guild)
        is AppScreen.Mixes -> ScreenContext(screen.account, screen.guild)
        is AppScreen.Mix -> ScreenContext(screen.account, screen.guild)
        is AppScreen.Track -> ScreenContext(screen.account, screen.guild)
        AppScreen.Loading,
        is AppScreen.Pairing,
        is AppScreen.GuildPicker,
        -> null
    }

    private fun playbackQueueFor(track: TrackPreview): List<TrackPreview> {
        val source = when (val screen = mutableState.value.screen) {
            is AppScreen.Home -> screen.snapshot.recent
            is AppScreen.Library -> listOf(
                screen.snapshot.favoriteTracks,
                screen.snapshot.historyTracks,
                screen.snapshot.recent,
            ).firstOrNull { list -> list.any { sameTrack(it, track) } }.orEmpty()
            is AppScreen.Mix -> screen.detail.seedPreview
            is AppScreen.Track -> backStack.peekLast()?.let { queueFromScreen(it, track) }.orEmpty()
            AppScreen.Loading,
            is AppScreen.Pairing,
            is AppScreen.GuildPicker,
            is AppScreen.Mixes,
            -> emptyList()
        }
        return (source.ifEmpty { listOf(track) } + track)
            .filter { !it.title.isNullOrBlank() }
            .distinctBy { it.stableKey ?: "${it.artist.orEmpty()}::${it.title.orEmpty()}" }
    }

    private fun queueFromScreen(screen: AppScreen, track: TrackPreview): List<TrackPreview> = when (screen) {
        is AppScreen.Home -> screen.snapshot.recent
        is AppScreen.Library -> listOf(
            screen.snapshot.favoriteTracks,
            screen.snapshot.historyTracks,
            screen.snapshot.recent,
        ).firstOrNull { list -> list.any { sameTrack(it, track) } }.orEmpty()
        is AppScreen.Mix -> screen.detail.seedPreview
        is AppScreen.Track -> listOf(screen.track)
        AppScreen.Loading,
        is AppScreen.Pairing,
        is AppScreen.GuildPicker,
        is AppScreen.Mixes,
        -> emptyList()
    }

    private fun sameTrack(left: TrackPreview, right: TrackPreview): Boolean {
        val leftKey = left.stableKey
        val rightKey = right.stableKey
        if (!leftKey.isNullOrBlank() && !rightKey.isNullOrBlank()) return leftKey == rightKey
        return left.title == right.title && left.artist == right.artist
    }

    private fun navigate(screen: AppScreen) {
        val current = mutableState.value.screen
        if (current != screen) backStack.addLast(current)
        setScreen(screen)
    }

    private fun setScreen(screen: AppScreen) {
        mutableState.value = mutableState.value.copy(screen = screen, error = null)
    }

    private fun setError(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        runCatching { block() }
            .onFailure { mutableState.value = mutableState.value.copy(error = friendlyMessage(it)) }
        mutableState.value = mutableState.value.copy(busy = false)
    }

    override fun onCleared() {
        playback.close()
        super.onCleared()
    }

    private fun defaultDeviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Android device" }
        .take(80)

    private fun friendlyMessage(error: Throwable): String = error.message ?: error::class.java.simpleName

    private data class ScreenContext(
        val account: AccountInfo,
        val guild: GuildSummary,
    )
}
