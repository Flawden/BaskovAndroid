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
import ru.flawden.baskovmusic.data.LocalMusicRepository
import ru.flawden.baskovmusic.data.LocalMusicSettingsStore
import ru.flawden.baskovmusic.data.TasteSignalReporter
import ru.flawden.baskovmusic.data.api.PairingCodePolicy
import ru.flawden.baskovmusic.data.api.ServerUrlPolicy
import ru.flawden.baskovmusic.data.auth.SessionStore
import ru.flawden.baskovmusic.model.AccountInfo
import ru.flawden.baskovmusic.model.GuildSummary
import ru.flawden.baskovmusic.model.LocalTrack
import ru.flawden.baskovmusic.model.MixCard
import ru.flawden.baskovmusic.model.SharedPlaylistSummary
import ru.flawden.baskovmusic.model.TasteSignal
import ru.flawden.baskovmusic.model.TasteSignalSource
import ru.flawden.baskovmusic.model.TasteSignalType
import ru.flawden.baskovmusic.model.TrackPreview
import ru.flawden.baskovmusic.playback.LocalPlaybackController
import ru.flawden.baskovmusic.playback.FavoriteStateBridge
import ru.flawden.baskovmusic.playback.LocalPlaybackUiState

class BaskovViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BaskovRepository(SessionStore(application))
    private val localMusicRepository = LocalMusicRepository(application)
    private val localMusicSettings = LocalMusicSettingsStore(application)
    private val tasteSignals = TasteSignalReporter(application, repository)
    private val mutableState = MutableStateFlow(AppUiState())
    private val backStack = ArrayDeque<AppScreen>()
    private val playback = LocalPlaybackController(application)

    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    val playbackState: StateFlow<LocalPlaybackUiState> = playback.state

    init {
        viewModelScope.launch { tasteSignals.flush() }
        viewModelScope.launch {
            FavoriteStateBridge.state.collect { favorite ->
                val stableKey = favorite.stableKey ?: return@collect
                val currentKeys = mutableState.value.favoriteKeys
                val updatedKeys = if (favorite.supported && favorite.favorite) {
                    currentKeys + stableKey
                } else {
                    currentKeys - stableKey
                }
                if (updatedKeys != currentKeys) {
                    mutableState.value = mutableState.value.copy(favoriteKeys = updatedKeys)
                }
            }
        }
        bootstrap()
    }

    fun pair(code: String) = launchBusy {
        val normalizedCode = PairingCodePolicy.normalize(code)
        val baseUrl = ServerUrlPolicy.normalize(
            BuildConfig.BASKOV_API_BASE_URL,
            allowCleartext = false,
        )
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
        syncFavoriteKeys(guild.guildId)
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

    fun openFavorites() = launchBusy {
        val context = currentContext() ?: return@launchBusy
        val snapshot = repository.favorites(context.guild.guildId)
        navigate(AppScreen.Favorites(context.account, context.guild, snapshot))
        syncFavoriteKeys(context.guild.guildId)
    }

    fun refreshFavorites() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Favorites ?: return@launchBusy
        setScreen(current.copy(snapshot = repository.favorites(current.guild.guildId)))
        syncFavoriteKeys(current.guild.guildId)
    }

    fun loadMoreFavorites() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Favorites ?: return@launchBusy
        if (!current.snapshot.hasMore) return@launchBusy
        val next = repository.favorites(
            current.guild.guildId,
            offset = current.snapshot.offset + current.snapshot.tracks.size,
            limit = current.snapshot.limit.coerceAtLeast(50),
        )
        val merged = current.snapshot.tracks + next.tracks
        setScreen(current.copy(snapshot = next.copy(offset = 0, tracks = merged)))
    }

    fun searchFavoriteAdd(query: String) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Favorites ?: return@launchBusy
        val normalized = query.trim()
        require(normalized.isNotBlank()) { "Введите трек для добавления" }
        val results = repository.search(current.guild.guildId, normalized)
        setScreen(current.copy(addQuery = normalized, addResults = results, addSearched = true))
    }

    fun addFavorite(track: TrackPreview) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Favorites ?: return@launchBusy
        repository.addFavorite(current.guild.guildId, track)
        recordFavoriteTaste(current.guild.guildId, track, favorite = true)
        setScreen(current.copy(snapshot = repository.favorites(current.guild.guildId)))
        syncFavoriteKeys(current.guild.guildId)
        setNotice("Добавлено в избранное")
    }

    fun removeFavorite(track: TrackPreview) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Favorites ?: return@launchBusy
        val stableKey = requireNotNull(track.stableKey) { "Favorite stableKey is required" }
        repository.removeFavoriteByStableKey(current.guild.guildId, stableKey)
        recordFavoriteTaste(current.guild.guildId, track, favorite = false)
        setScreen(current.copy(snapshot = repository.favorites(current.guild.guildId)))
        syncFavoriteKeys(current.guild.guildId)
        setNotice("Удалено из избранного")
    }

    fun clearFavorites() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Favorites ?: return@launchBusy
        val updated = repository.clearFavorites(current.guild.guildId)
        setScreen(current.copy(snapshot = updated, addResults = emptyList(), addSearched = false))
        applyFavoriteKeys(emptySet())
        setNotice("Избранное очищено")
    }

    fun playFavorites() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Favorites ?: return@launchBusy
        val all = repository.allFavorites(current.guild.guildId)
        require(all.tracks.isNotEmpty()) { "Избранное пусто" }
        playback.play(repository.playbackQueue(current.guild.guildId, all.tracks), 0)
    }

    fun openServers() = launchBusy {
        val context = currentContext() ?: return@launchBusy
        navigate(AppScreen.Servers(context.account, context.guild, repository.serverHubs()))
    }

    fun refreshServers() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Servers ?: return@launchBusy
        setScreen(current.copy(servers = repository.serverHubs()))
    }

    fun openLocalMusic() = launchBusy {
        val context = currentContext() ?: return@launchBusy
        navigate(loadLocalMusicScreen(context.account, context.guild))
    }

    fun openSearch() = launchBusy {
        val context = currentContext() ?: return@launchBusy
        syncFavoriteKeys(context.guild.guildId)
        navigate(AppScreen.Search(context.account, context.guild))
    }

    fun search(query: String) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Search ?: return@launchBusy
        val normalized = query.trim()
        require(normalized.isNotBlank()) { "Введите название трека или исполнителя" }

        val remote = repository.search(current.guild.guildId, normalized)
        val local = if (localMusicRepository.hasPermission()) {
            val selection = localMusicSettings.selection()
            localMusicRepository.tracks()
                .asSequence()
                .filter { track -> !selection.enabled || track.folderPath in selection.selectedFolders }
                .filter { track ->
                    track.title.contains(normalized, ignoreCase = true) ||
                        track.artist.contains(normalized, ignoreCase = true) ||
                        track.album?.contains(normalized, ignoreCase = true) == true
                }
                .take(50)
                .toList()
        } else {
            emptyList()
        }
        setScreen(current.copy(
            query = normalized,
            remoteResults = remote,
            localResults = local,
            searched = true,
        ))
    }

    fun playSearchRemote(track: TrackPreview) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Search ?: return@launchBusy
        val queue = current.remoteResults.ifEmpty { listOf(track) }
        val startIndex = queue.indexOfFirst { sameTrack(it, track) }.coerceAtLeast(0)
        playback.play(repository.playbackQueue(current.guild.guildId, queue), startIndex)
    }

    fun playSearchLocal(track: LocalTrack) {
        val current = mutableState.value.screen as? AppScreen.Search ?: return
        val queue = current.localResults.ifEmpty { listOf(track) }
        val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playback.playLocal(queue, startIndex)
    }

    fun openPlaylists() = launchBusy {
        val context = currentContext() ?: return@launchBusy
        val playlists = repository.playlists(context.guild.guildId)
        navigate(AppScreen.Playlists(context.account, context.guild, playlists))
    }

    fun refreshPlaylists() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlists ?: return@launchBusy
        setScreen(current.copy(playlists = repository.playlists(current.guild.guildId)))
    }

    fun createPlaylist(name: String) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlists ?: return@launchBusy
        val normalized = name.trim()
        require(normalized.isNotBlank()) { "Введите название плейлиста" }
        val detail = repository.createPlaylist(current.guild.guildId, normalized)
        navigate(AppScreen.Playlist(current.account, current.guild, detail))
    }

    fun openPlaylist(playlist: SharedPlaylistSummary) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlists ?: return@launchBusy
        val detail = repository.playlist(current.guild.guildId, playlist.name)
        navigate(AppScreen.Playlist(current.account, current.guild, detail))
    }

    fun refreshPlaylist() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlist ?: return@launchBusy
        val detail = repository.playlist(current.guild.guildId, current.detail.name)
        setScreen(current.copy(detail = detail))
    }

    fun searchPlaylistAdd(query: String) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlist ?: return@launchBusy
        val normalized = query.trim()
        require(normalized.isNotBlank()) { "Введите трек для добавления" }
        val results = repository.search(current.guild.guildId, normalized)
        setScreen(current.copy(addQuery = normalized, addResults = results, addSearched = true))
    }

    fun addTrackToPlaylist(track: TrackPreview) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlist ?: return@launchBusy
        require(current.detail.ownedByMe) { "Этот плейлист принадлежит другому пользователю" }
        val detail = repository.addPlaylistTrack(current.guild.guildId, current.detail.name, track)
        setScreen(current.copy(detail = detail))
    }

    fun removePlaylistTrack(index: Int) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlist ?: return@launchBusy
        require(current.detail.ownedByMe) { "Этот плейлист доступен только для чтения" }
        val detail = repository.removePlaylistTrack(
            current.guild.guildId,
            current.detail.name,
            index + 1,
        )
        setScreen(current.copy(detail = detail))
    }

    fun movePlaylistTrack(index: Int, delta: Int) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlist ?: return@launchBusy
        require(current.detail.ownedByMe) { "Этот плейлист доступен только для чтения" }
        val target = index + delta
        if (target !in current.detail.tracks.indices) return@launchBusy
        val detail = repository.movePlaylistTrack(
            current.guild.guildId,
            current.detail.name,
            index + 1,
            target + 1,
        )
        setScreen(current.copy(detail = detail))
    }

    fun renamePlaylist(newName: String) = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlist ?: return@launchBusy
        require(current.detail.ownedByMe) { "Этот плейлист доступен только для чтения" }
        val normalized = newName.trim()
        require(normalized.isNotBlank()) { "Введите новое название" }
        val detail = repository.renamePlaylist(current.guild.guildId, current.detail.name, normalized)
        setScreen(current.copy(detail = detail))
    }

    fun deletePlaylist() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlist ?: return@launchBusy
        require(current.detail.ownedByMe) { "Этот плейлист доступен только для чтения" }
        repository.deletePlaylist(current.guild.guildId, current.detail.name)
        while (backStack.peekLast() is AppScreen.Playlist) backStack.removeLast()
        setScreen(AppScreen.Playlists(
            current.account,
            current.guild,
            repository.playlists(current.guild.guildId),
        ))
    }

    fun playPlaylist() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.Playlist ?: return@launchBusy
        require(current.detail.tracks.isNotEmpty()) { "Плейлист пуст" }
        playback.play(repository.playbackQueue(current.guild.guildId, current.detail.tracks), 0)
    }

    fun refreshLocalMusic() = launchBusy {
        val current = mutableState.value.screen as? AppScreen.LocalMusic ?: return@launchBusy
        setScreen(loadLocalMusicScreen(current.account, current.guild))
    }

    fun toggleLocalFolder(path: String) {
        val current = mutableState.value.screen as? AppScreen.LocalMusic ?: return
        val allFolders = current.folders.mapTo(linkedSetOf()) { it.path }
        val selected = if (current.folderFilterEnabled) {
            current.selectedFolders.toMutableSet()
        } else {
            allFolders.toMutableSet()
        }
        if (!selected.add(path)) selected.remove(path)
        val useAll = selected == allFolders
        localMusicSettings.saveSelection(
            enabled = !useAll,
            selectedFolders = if (useAll) emptySet() else selected,
        )
        setScreen(
            current.copy(
                folderFilterEnabled = !useAll,
                selectedFolders = if (useAll) emptySet() else selected,
            ),
        )
    }

    fun useAllLocalFolders() {
        val current = mutableState.value.screen as? AppScreen.LocalMusic ?: return
        localMusicSettings.useAllFolders()
        setScreen(current.copy(folderFilterEnabled = false, selectedFolders = emptySet()))
    }

    fun clearLocalFolders() {
        val current = mutableState.value.screen as? AppScreen.LocalMusic ?: return
        localMusicSettings.saveSelection(enabled = true, selectedFolders = emptySet())
        setScreen(current.copy(folderFilterEnabled = true, selectedFolders = emptySet()))
    }

    fun playLocalTrack(track: LocalTrack) {
        val current = mutableState.value.screen as? AppScreen.LocalMusic ?: return
        val queue = filteredLocalTracks(current)
        val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playback.playLocal(queue, startIndex)
    }

    fun localAudioPermission(): String = localMusicRepository.requiredPermission()

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

    fun toggleCurrentFavorite() {
        playback.toggleFavorite()
    }

    fun toggleFavorite(track: TrackPreview) = launchBusy {
        val context = currentContext() ?: return@launchBusy
        val stableKey = requireNotNull(track.stableKey) { "Track stableKey is required" }
        val favorite = stableKey !in mutableState.value.favoriteKeys
        if (favorite) {
            repository.addFavorite(context.guild.guildId, track)
        } else {
            repository.removeFavoriteByStableKey(context.guild.guildId, stableKey)
        }
        recordFavoriteTaste(context.guild.guildId, track, favorite)
        applyFavoriteKeys(
            if (favorite) mutableState.value.favoriteKeys + stableKey
            else mutableState.value.favoriteKeys - stableKey,
        )
    }

    fun togglePlayback() = playback.togglePlayPause()
    fun nextTrack() = playback.next()
    fun previousTrack() = playback.previous()
    fun playQueueItem(index: Int) = playback.playQueueItem(index)
    fun removeQueueItem(index: Int) = playback.removeQueueItem(index)
    fun seekPlayback(positionMillis: Long) = playback.seekTo(positionMillis)
    fun seekPlaybackBy(deltaMillis: Long) = playback.seekBy(deltaMillis)
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeatMode() = playback.cycleRepeatMode()
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

    fun clearNotice() {
        mutableState.value = mutableState.value.copy(notice = null)
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
        syncFavoriteKeys(selected.guildId)
    }

    private fun recordFavoriteTaste(guildId: String, track: TrackPreview, favorite: Boolean) {
        val title = track.title?.takeIf(String::isNotBlank) ?: return
        tasteSignals.enqueue(
            guildId,
            TasteSignal(
                type = if (favorite) TasteSignalType.FAVORITE_ADD else TasteSignalType.FAVORITE_REMOVE,
                source = TasteSignalSource.REMOTE,
                stableKey = track.stableKey,
                artist = track.artist.orEmpty().ifBlank { "Неизвестно" },
                title = title,
                completionRatio = 1.0,
            ),
        )
        viewModelScope.launch { tasteSignals.flush() }
    }

    private suspend fun syncFavoriteKeys(guildId: String) {
        applyFavoriteKeys(repository.favoriteKeys(guildId))
    }

    private fun applyFavoriteKeys(keys: Set<String>) {
        mutableState.value = mutableState.value.copy(favoriteKeys = keys)
        val playbackSnapshot = playback.state.value
        val currentKey = playbackSnapshot.current?.stableKey
        if (currentKey != null && playbackSnapshot.favoriteSupported) {
            FavoriteStateBridge.publish(
                stableKey = currentKey,
                supported = true,
                favorite = currentKey in keys,
            )
        }
    }

    private suspend fun loadLocalMusicScreen(
        account: AccountInfo,
        guild: GuildSummary,
    ): AppScreen.LocalMusic {
        val granted = localMusicRepository.hasPermission()
        val tracks = if (granted) localMusicRepository.tracks() else emptyList()
        val selection = localMusicSettings.selection()
        return AppScreen.LocalMusic(
            account = account,
            guild = guild,
            permissionGranted = granted,
            tracks = tracks,
            folders = localMusicRepository.folders(tracks),
            folderFilterEnabled = selection.enabled,
            selectedFolders = selection.selectedFolders,
        )
    }

    private fun filteredLocalTracks(screen: AppScreen.LocalMusic): List<LocalTrack> =
        if (!screen.folderFilterEnabled) {
            screen.tracks
        } else {
            screen.tracks.filter { it.folderPath in screen.selectedFolders }
        }

    private fun currentContext(): ScreenContext? = when (val screen = mutableState.value.screen) {
        is AppScreen.Home -> ScreenContext(screen.account, screen.guild)
        is AppScreen.Library -> ScreenContext(screen.account, screen.guild)
        is AppScreen.LocalMusic -> ScreenContext(screen.account, screen.guild)
        is AppScreen.Search -> ScreenContext(screen.account, screen.guild)
        is AppScreen.Favorites -> ScreenContext(screen.account, screen.guild)
        is AppScreen.Servers -> ScreenContext(screen.account, screen.currentGuild)
        is AppScreen.Playlists -> ScreenContext(screen.account, screen.guild)
        is AppScreen.Playlist -> ScreenContext(screen.account, screen.guild)
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
            is AppScreen.LocalMusic -> emptyList()
            is AppScreen.Search -> screen.remoteResults
            is AppScreen.Favorites -> screen.snapshot.tracks
            is AppScreen.Playlist -> screen.detail.tracks
            is AppScreen.Mix -> screen.detail.seedPreview
            is AppScreen.Track -> backStack.peekLast()?.let { queueFromScreen(it, track) }.orEmpty()
            AppScreen.Loading,
            is AppScreen.Pairing,
            is AppScreen.GuildPicker,
            is AppScreen.Servers,
            is AppScreen.Playlists,
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
        is AppScreen.LocalMusic -> emptyList()
        is AppScreen.Search -> screen.remoteResults
        is AppScreen.Favorites -> screen.snapshot.tracks
        is AppScreen.Playlist -> screen.detail.tracks
        is AppScreen.Mix -> screen.detail.seedPreview
        is AppScreen.Track -> listOf(screen.track)
        AppScreen.Loading,
        is AppScreen.Pairing,
        is AppScreen.GuildPicker,
        is AppScreen.Servers,
        is AppScreen.Playlists,
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

    private fun setNotice(message: String) {
        mutableState.value = mutableState.value.copy(notice = message)
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
