package ru.flawden.baskovmusic.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.media3.common.Player
import ru.flawden.baskovmusic.R
import ru.flawden.baskovmusic.model.HomeSnapshot
import ru.flawden.baskovmusic.model.LocalTrack
import ru.flawden.baskovmusic.model.MixCard
import ru.flawden.baskovmusic.model.SharedPlaylistSummary
import ru.flawden.baskovmusic.model.TrackPreview
import ru.flawden.baskovmusic.playback.LocalPlaybackUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaskovApp(
    viewModel: BaskovViewModel,
    openNowPlayingRequest: Boolean = false,
    onNowPlayingRequestConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
    LaunchedEffect(playback.error) {
        playback.error?.let {
            snackbar.showSnackbar("Playback: $it")
            viewModel.clearPlaybackError()
        }
    }
    LaunchedEffect(playback.current) {
        if (playback.current == null) showNowPlaying = false
    }
    LaunchedEffect(openNowPlayingRequest, playback.current) {
        if (openNowPlayingRequest && playback.current != null) {
            showNowPlaying = true
            onNowPlayingRequestConsumed()
        }
    }

    val supportsBack = when (state.screen) {
        is AppScreen.Library,
        is AppScreen.LocalMusic,
        is AppScreen.Search,
        is AppScreen.Playlists,
        is AppScreen.Playlist,
        is AppScreen.Mixes,
        is AppScreen.Mix,
        is AppScreen.Track,
        -> true

        AppScreen.Loading,
        is AppScreen.Pairing,
        is AppScreen.GuildPicker,
        is AppScreen.Home,
        -> false
    }
    BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
    BackHandler(enabled = !showNowPlaying && supportsBack && !state.busy) { viewModel.goBack() }

    BaskovTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(title = { Text(if (showNowPlaying) "Сейчас играет" else "Baskov Music") })
            },
            bottomBar = {
                if (playback.current != null && !showNowPlaying) {
                    MiniPlayer(
                        playback = playback,
                        onOpen = { showNowPlaying = true },
                        onToggle = viewModel::togglePlayback,
                        onPrevious = viewModel::previousTrack,
                        onNext = viewModel::nextTrack,
                        onStop = viewModel::stopPlayback,
                    )
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (showNowPlaying && playback.current != null) {
                    NowPlayingScreen(
                        playback = playback,
                        onClose = { showNowPlaying = false },
                        onToggle = viewModel::togglePlayback,
                        onPrevious = viewModel::previousTrack,
                        onNext = viewModel::nextTrack,
                        onPlayQueueItem = viewModel::playQueueItem,
                        onRemoveQueueItem = viewModel::removeQueueItem,
                        onSeek = viewModel::seekPlayback,
                        onSeekBy = viewModel::seekPlaybackBy,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeat = viewModel::cycleRepeatMode,
                        onStop = viewModel::stopPlayback,
                    )
                } else when (val screen = state.screen) {
                    AppScreen.Loading -> CircularProgressIndicator()
                    is AppScreen.Pairing -> PairingScreen(state.busy, viewModel::pair)
                    is AppScreen.GuildPicker -> GuildPickerScreen(
                        displayName = screen.account.displayName,
                        guilds = screen.guilds,
                        busy = state.busy,
                        onChoose = viewModel::chooseGuild,
                        onLogout = viewModel::logout,
                    )
                    is AppScreen.Home -> HomeScreen(
                        screen = screen,
                        busy = state.busy,
                        onRefresh = viewModel::refreshHome,
                        onOpenLibrary = viewModel::openLibrary,
                        onOpenLocalMusic = viewModel::openLocalMusic,
                        onOpenSearch = viewModel::openSearch,
                        onOpenPlaylists = viewModel::openPlaylists,
                        onOpenMixes = viewModel::openMixes,
                        onOpenMix = viewModel::openMix,
                        onOpenTrack = viewModel::openTrack,
                        onPlayTrack = viewModel::playTrack,
                        onChangeGuild = viewModel::changeGuild,
                        onLogout = viewModel::logout,
                    )
                    is AppScreen.Library -> LibraryScreen(
                        screen = screen,
                        busy = state.busy,
                        onRefresh = viewModel::refreshLibrary,
                        onOpenTrack = viewModel::openTrack,
                        onPlayTrack = viewModel::playTrack,
                        onBack = viewModel::goBack,
                    )
                    is AppScreen.LocalMusic -> LocalMusicScreen(
                        screen = screen,
                        busy = state.busy,
                        permission = viewModel.localAudioPermission(),
                        onPermissionResult = { viewModel.refreshLocalMusic() },
                        onRefresh = viewModel::refreshLocalMusic,
                        onToggleFolder = viewModel::toggleLocalFolder,
                        onUseAllFolders = viewModel::useAllLocalFolders,
                        onClearFolders = viewModel::clearLocalFolders,
                        onPlay = viewModel::playLocalTrack,
                        onBack = viewModel::goBack,
                    )
                    is AppScreen.Search -> SearchScreen(
                        screen = screen,
                        busy = state.busy,
                        onSearch = viewModel::search,
                        onPlayRemote = viewModel::playSearchRemote,
                        onPlayLocal = viewModel::playSearchLocal,
                        onBack = viewModel::goBack,
                    )
                    is AppScreen.Playlists -> PlaylistsScreen(
                        screen = screen,
                        busy = state.busy,
                        onRefresh = viewModel::refreshPlaylists,
                        onCreate = viewModel::createPlaylist,
                        onOpen = viewModel::openPlaylist,
                        onBack = viewModel::goBack,
                    )
                    is AppScreen.Playlist -> PlaylistScreen(
                        screen = screen,
                        busy = state.busy,
                        onRefresh = viewModel::refreshPlaylist,
                        onPlayAll = viewModel::playPlaylist,
                        onSearchAdd = viewModel::searchPlaylistAdd,
                        onAdd = viewModel::addTrackToPlaylist,
                        onRemove = viewModel::removePlaylistTrack,
                        onMove = viewModel::movePlaylistTrack,
                        onRename = viewModel::renamePlaylist,
                        onDelete = viewModel::deletePlaylist,
                        onBack = viewModel::goBack,
                    )
                    is AppScreen.Mixes -> MixesScreen(
                        screen = screen,
                        busy = state.busy,
                        onRefresh = viewModel::refreshMixes,
                        onOpenMix = viewModel::openMix,
                        onBack = viewModel::goBack,
                    )
                    is AppScreen.Mix -> MixDetailScreen(
                        screen = screen,
                        busy = state.busy,
                        onRefresh = viewModel::refreshMix,
                        onOpenTrack = viewModel::openTrack,
                        onPlayTrack = viewModel::playTrack,
                        onBack = viewModel::goBack,
                    )
                    is AppScreen.Track -> TrackDetailScreen(
                        screen = screen,
                        busy = state.busy,
                        onPlay = viewModel::playTrack,
                        onBack = viewModel::goBack,
                    )
                }

                if (state.busy && state.screen !is AppScreen.Loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PairingScreen(busy: Boolean, onPair: (String) -> Unit) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Подключить устройство",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text("В Discord выполни /device pair и введи одноразовый код. Сервер Baskov уже настроен.")
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(16) },
            label = { Text("Pairing code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = { onPair(code) },
            enabled = !busy && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Подключить") }
        Text(
            "Baskov Production • защищённое HTTPS-подключение",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun GuildPickerScreen(
    displayName: String,
    guilds: List<ru.flawden.baskovmusic.model.GuildSummary>,
    busy: Boolean,
    onChoose: (ru.flawden.baskovmusic.model.GuildSummary) -> Unit,
    onLogout: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Привет, $displayName", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Выбери Discord-сервер для персонального Home.")
        Spacer(Modifier.height(16.dp))
        if (guilds.isEmpty()) {
            Text("Доступных серверов нет. Проверь, что связанный Discord-аккаунт состоит на сервере с Басковым.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(guilds, key = { it.guildId }) { guild ->
                    Card(onClick = { onChoose(guild) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text(guild.name, fontWeight = FontWeight.SemiBold)
                            Text(guild.guildId, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onLogout, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Отключить устройство")
        }
    }
}

@Composable
private fun HomeScreen(
    screen: AppScreen.Home,
    busy: Boolean,
    onRefresh: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLocalMusic: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenMixes: () -> Unit,
    onOpenMix: (MixCard) -> Unit,
    onOpenTrack: (TrackPreview) -> Unit,
    onPlayTrack: (TrackPreview) -> Unit,
    onChangeGuild: () -> Unit,
    onLogout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(screen.guild.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${screen.account.displayName} • ${screen.snapshot.date}")
        }
        item { HomeSummary(screen.snapshot) }
        item {
            Button(
                onClick = onOpenSearch,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🔍 Найти и включить")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenLibrary, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Библиотека")
                }
                Button(onClick = onOpenMixes, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Миксы")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenLocalMusic,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("📱 Музыка на телефоне")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenPlaylists,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🎵 Общие плейлисты")
            }
        }
        item { SectionTitle("Сегодня") }
        if (screen.snapshot.today.isEmpty()) item { EmptyCard("На сегодня миксов пока нет.") }
        items(screen.snapshot.today) { MixCardView(it, busy, onOpenMix) }
        item { SectionTitle("Для тебя") }
        if (screen.snapshot.forYou.isEmpty()) item { EmptyCard("Персональные миксы ещё формируются.") }
        items(screen.snapshot.forYou) { MixCardView(it, busy, onOpenMix) }
        item { SectionTitle("Темы") }
        if (screen.snapshot.themes.isEmpty()) item { EmptyCard("Темы появятся после накопления сигналов вкуса.") }
        items(screen.snapshot.themes) { theme ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(theme.name ?: "Theme")
                    Text("${(theme.affinity * 100).toInt()}%")
                }
            }
        }
        item { SectionTitle("Недавнее") }
        if (screen.snapshot.recent.isEmpty()) item { EmptyCard("История прослушиваний пока пуста.") }
        items(screen.snapshot.recent) { track -> TrackCardView(track, busy, onOpenTrack, onPlayTrack) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Обновить") }
                OutlinedButton(onClick = onChangeGuild, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Сервер") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onLogout, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Выйти") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LibraryScreen(
    screen: AppScreen.Library,
    busy: Boolean,
    onRefresh: () -> Unit,
    onOpenTrack: (TrackPreview) -> Unit,
    onPlayTrack: (TrackPreview) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Библиотека", screen.guild.name, busy, onBack) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Избранное: ${screen.snapshot.favorites}", fontWeight = FontWeight.SemiBold)
                    Text("История: ${screen.snapshot.personalHistory}")
                }
            }
        }
        item { SectionTitle("Избранное") }
        if (screen.snapshot.favoriteTracks.isEmpty()) item { EmptyCard("В избранном пока ничего нет.") }
        items(screen.snapshot.favoriteTracks) { track -> TrackCardView(track, busy, onOpenTrack, onPlayTrack) }
        item { SectionTitle("История") }
        if (screen.snapshot.historyTracks.isEmpty()) item { EmptyCard("История пока пуста.") }
        items(screen.snapshot.historyTracks) { track -> TrackCardView(track, busy, onOpenTrack, onPlayTrack) }
        item { SectionTitle("Недавнее") }
        if (screen.snapshot.recent.isEmpty()) item { EmptyCard("Недавних треков пока нет.") }
        items(screen.snapshot.recent) { track -> TrackCardView(track, busy, onOpenTrack, onPlayTrack) }
        item { BottomActions(busy, onRefresh, onBack) }
    }
}

@Composable
private fun LocalMusicScreen(
    screen: AppScreen.LocalMusic,
    busy: Boolean,
    permission: String,
    onPermissionResult: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onToggleFolder: (String) -> Unit,
    onUseAllFolders: () -> Unit,
    onClearFolders: () -> Unit,
    onPlay: (LocalTrack) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var foldersExpanded by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onPermissionResult(granted) }
    val folderTracks = remember(
        screen.tracks,
        screen.folderFilterEnabled,
        screen.selectedFolders,
    ) {
        if (!screen.folderFilterEnabled) {
            screen.tracks
        } else {
            screen.tracks.filter { it.folderPath in screen.selectedFolders }
        }
    }
    val visibleTracks = remember(folderTracks, query) {
        val needle = query.trim()
        if (needle.isBlank()) {
            folderTracks
        } else {
            folderTracks.filter { track ->
                track.title.contains(needle, ignoreCase = true) ||
                    track.artist.contains(needle, ignoreCase = true) ||
                    track.album?.contains(needle, ignoreCase = true) == true
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Музыка на телефоне", screen.guild.name, busy, onBack) }
        if (!screen.permissionGranted) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Разреши Баскову читать аудиофайлы на устройстве.", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Файлы никуда не загружаются: Baskov Music читает библиотеку через Android MediaStore и играет content:// напрямую.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { permissionLauncher.launch(permission) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Разрешить доступ к музыке") }
                    }
                }
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Папки библиотеки", fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (screen.folderFilterEnabled) {
                                        "Выбрано: ${screen.selectedFolders.count { selected -> screen.folders.any { it.path == selected } }} из ${screen.folders.size}"
                                    } else {
                                        "Используются все найденные папки"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { foldersExpanded = !foldersExpanded },
                                enabled = screen.folders.isNotEmpty(),
                            ) { Text(if (foldersExpanded) "Свернуть" else "Показать") }
                        }
                        if (foldersExpanded) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(
                                    onClick = onUseAllFolders,
                                    enabled = !busy && screen.folders.isNotEmpty() && screen.folderFilterEnabled,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Выбрать все") }
                                TextButton(
                                    onClick = onClearFolders,
                                    enabled = !busy && screen.folders.isNotEmpty() && (!screen.folderFilterEnabled || screen.selectedFolders.isNotEmpty()),
                                    modifier = Modifier.weight(1f),
                                ) { Text("Снять все") }
                            }
                            if (screen.folders.isEmpty()) {
                                Text("MediaStore пока не сообщил ни одной папки.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                screen.folders.forEach { folder ->
                                    val checked = !screen.folderFilterEnabled || folder.path in screen.selectedFolders
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { onToggleFolder(folder.path) },
                                            enabled = !busy,
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(folder.label, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "${folder.trackCount} треков",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (screen.folders.isEmpty()) {
                            Text("MediaStore пока не сообщил ни одной папки.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск по локальной музыке") },
                    placeholder = { Text("Трек, исполнитель или альбом") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    when {
                        query.isNotBlank() -> "Совпадений: ${visibleTracks.size}"
                        screen.folderFilterEnabled -> "Треков в выбранных папках: ${folderTracks.size}"
                        else -> "Найдено треков: ${screen.tracks.size}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (visibleTracks.isEmpty()) {
                item {
                    EmptyCard(
                        when {
                            screen.tracks.isEmpty() -> "MediaStore не нашёл музыкальных файлов."
                            screen.folderFilterEnabled && folderTracks.isEmpty() -> "В выбранных папках нет треков. Отметь другую папку или нажми «Все»."
                            else -> "По этому запросу ничего не найдено."
                        },
                    )
                }
            }
            items(visibleTracks, key = { it.id }) { track ->
                LocalTrackCard(track = track, busy = busy, onPlay = onPlay)
            }
            item { BottomActions(busy, onRefresh, onBack) }
        }
    }
}

@Composable
private fun LocalTrackCard(
    track: LocalTrack,
    busy: Boolean,
    onPlay: (LocalTrack) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                fallback = painterResource(R.drawable.ic_launcher_foreground),
                error = painterResource(R.drawable.ic_launcher_foreground),
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = if (track.artworkUri == null) ContentScale.Fit else ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(track.title, fontWeight = FontWeight.SemiBold)
                Text(track.artist, style = MaterialTheme.typography.bodySmall)
                Text(
                    listOfNotNull(track.album, formatPlaybackTime(track.durationMillis).takeIf { track.durationMillis > 0L })
                        .joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onPlay(track) }, enabled = !busy) { Text("▶") }
        }
    }
}

@Composable
private fun SearchScreen(
    screen: AppScreen.Search,
    busy: Boolean,
    onSearch: (String) -> Unit,
    onPlayRemote: (TrackPreview) -> Unit,
    onPlayLocal: (LocalTrack) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable(screen.query) { mutableStateOf(screen.query) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Поиск", screen.guild.name, busy, onBack) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Что включить?") },
                placeholder = { Text("Green Day Holiday") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = { onSearch(query) },
                enabled = !busy && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🔍 Найти")
            }
        }
        if (!screen.searched) {
            item {
                EmptyCard("Поиск одновременно смотрит Baskov backend и выбранные папки на телефоне.")
            }
        } else {
            item { SectionTitle("Baskov / интернет") }
            if (screen.remoteResults.isEmpty()) {
                item { EmptyCard("В интернете ничего не найдено.") }
            } else {
                items(screen.remoteResults) { track ->
                    TrackCardView(
                        track = track,
                        busy = busy,
                        onOpen = { onPlayRemote(it) },
                        onPlay = onPlayRemote,
                    )
                }
            }

            item { SectionTitle("На телефоне") }
            if (screen.localResults.isEmpty()) {
                item { EmptyCard("В выбранных локальных папках совпадений нет.") }
            } else {
                items(screen.localResults, key = { it.id }) { track ->
                    LocalTrackCard(track = track, busy = busy, onPlay = onPlayLocal)
                }
            }
        }
        item {
            OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Назад")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlaylistsScreen(
    screen: AppScreen.Playlists,
    busy: Boolean,
    onRefresh: () -> Unit,
    onCreate: (String) -> Unit,
    onOpen: (SharedPlaylistSummary) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Общие плейлисты", screen.guild.name, busy, onBack) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Новый плейлист", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onCreate(name) },
                        enabled = !busy && name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("＋ Создать") }
                }
            }
        }
        item {
            Text(
                "Это те же плейлисты, которые видит Discord-бот на этом сервере.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        if (screen.playlists.isEmpty()) {
            item { EmptyCard("На этом сервере пока нет плейлистов.") }
        } else {
            items(screen.playlists, key = { it.name }) { playlist ->
                Card(onClick = { onOpen(playlist) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(playlist.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${playlist.trackCount} треков • ${if (playlist.ownedByMe) "мой" else "чужой / read-only"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("→")
                    }
                }
            }
        }
        item { BottomActions(busy, onRefresh, onBack) }
    }
}

@Composable
private fun PlaylistScreen(
    screen: AppScreen.Playlist,
    busy: Boolean,
    onRefresh: () -> Unit,
    onPlayAll: () -> Unit,
    onSearchAdd: (String) -> Unit,
    onAdd: (TrackPreview) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var addQuery by rememberSaveable(screen.detail.name) { mutableStateOf(screen.addQuery) }
    var rename by rememberSaveable(screen.detail.name) { mutableStateOf(screen.detail.name) }
    var deleteArmed by rememberSaveable(screen.detail.name) { mutableStateOf(false) }
    val editable = screen.detail.ownedByMe

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(screen.detail.name, screen.guild.name, busy, onBack) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (editable) "Твой общий плейлист" else "Плейлист другого пользователя — только чтение",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Треков: ${screen.detail.tracks.size}")
                    Button(
                        onClick = onPlayAll,
                        enabled = !busy && screen.detail.tracks.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("▶ Играть плейлист на телефоне") }
                }
            }
        }
        if (editable) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Управление", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = rename,
                            onValueChange = { rename = it },
                            label = { Text("Название") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onRename(rename) },
                                enabled = !busy && rename.isNotBlank() && rename.trim() != screen.detail.name,
                                modifier = Modifier.weight(1f),
                            ) { Text("Переименовать") }
                            OutlinedButton(
                                onClick = {
                                    if (deleteArmed) onDelete() else deleteArmed = true
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(if (deleteArmed) "Точно удалить?" else "Удалить") }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Треки") }
        if (screen.detail.tracks.isEmpty()) {
            item { EmptyCard("Плейлист пока пуст.") }
        } else {
            itemsIndexed(screen.detail.tracks) { index, track ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${index + 1}. ${track.title ?: "Unknown track"}", fontWeight = FontWeight.SemiBold)
                        Text(track.artist ?: "Unknown artist", style = MaterialTheme.typography.bodySmall)
                        if (editable) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onMove(index, -1) },
                                    enabled = !busy && index > 0,
                                    modifier = Modifier.weight(1f),
                                ) { Text("↑") }
                                OutlinedButton(
                                    onClick = { onMove(index, 1) },
                                    enabled = !busy && index < screen.detail.tracks.lastIndex,
                                    modifier = Modifier.weight(1f),
                                ) { Text("↓") }
                                OutlinedButton(
                                    onClick = { onRemove(index) },
                                    enabled = !busy,
                                    modifier = Modifier.weight(1f),
                                ) { Text("🗑") }
                            }
                        }
                    }
                }
            }
        }

        if (editable) {
            item { SectionTitle("Добавить из Baskov") }
            item {
                Text(
                    "Пока в общий плейлист можно сохранять серверные треки. Локальные content:// файлы станут общими после будущего Phone → Discord transport.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                OutlinedTextField(
                    value = addQuery,
                    onValueChange = { addQuery = it },
                    label = { Text("Найти трек") },
                    placeholder = { Text("Green Day Holiday") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = { onSearchAdd(addQuery) },
                    enabled = !busy && addQuery.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🔍 Найти для плейлиста") }
            }
            if (screen.addSearched) {
                if (screen.addResults.isEmpty()) {
                    item { EmptyCard("Ничего не найдено.") }
                } else {
                    items(screen.addResults) { track ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(track.title ?: "Unknown track", fontWeight = FontWeight.SemiBold)
                                    Text(track.artist ?: "Unknown artist", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                    onClick = { onAdd(track) },
                                    enabled = !busy && !track.title.isNullOrBlank(),
                                ) { Text("＋") }
                            }
                        }
                    }
                }
            }
        }
        item { BottomActions(busy, onRefresh, onBack) }
    }
}

@Composable
private fun MixesScreen(
    screen: AppScreen.Mixes,
    busy: Boolean,
    onRefresh: () -> Unit,
    onOpenMix: (MixCard) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Миксы", "${screen.guild.name} • ${screen.snapshot.date}", busy, onBack) }
        item { SectionTitle("Сегодня") }
        if (screen.snapshot.today.isEmpty()) item { EmptyCard("На сегодня миксов пока нет.") }
        items(screen.snapshot.today) { mix -> MixCardView(mix, busy, onOpenMix) }
        item { SectionTitle("Для тебя") }
        if (screen.snapshot.forYou.isEmpty()) item { EmptyCard("Персональные миксы ещё формируются.") }
        items(screen.snapshot.forYou) { mix -> MixCardView(mix, busy, onOpenMix) }
        item { SectionTitle("Темы") }
        if (screen.snapshot.themes.isEmpty()) item { EmptyCard("Темы появятся после накопления сигналов вкуса.") }
        items(screen.snapshot.themes) { theme ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(theme.name ?: "Theme")
                    Text("${(theme.affinity * 100).toInt()}%")
                }
            }
        }
        item { BottomActions(busy, onRefresh, onBack) }
    }
}

@Composable
private fun MixDetailScreen(
    screen: AppScreen.Mix,
    busy: Boolean,
    onRefresh: () -> Unit,
    onOpenTrack: (TrackPreview) -> Unit,
    onPlayTrack: (TrackPreview) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader(screen.detail.label, screen.guild.name, busy, onBack) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    screen.detail.description?.let { Text(it) }
                    Text("station: ${screen.detail.stationSlug}", style = MaterialTheme.typography.bodySmall)
                    Text(if (screen.detail.available) "Доступен" else "Пока недоступен")
                    if (screen.detail.daily) Text("Ежедневный микс", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Text(
                "Предпросмотр основы микса. Это не обещанная очередь воспроизведения.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { SectionTitle("Треки в основе") }
        if (screen.detail.seedPreview.isEmpty()) item { EmptyCard("Для этого микса пока нет seed preview.") }
        items(screen.detail.seedPreview) { track -> TrackCardView(track, busy, onOpenTrack, onPlayTrack) }
        item { BottomActions(busy, onRefresh, onBack) }
    }
}

@Composable
private fun TrackDetailScreen(
    screen: AppScreen.Track,
    busy: Boolean,
    onPlay: (TrackPreview) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader("Трек", screen.guild.name, busy, onBack)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(screen.track.title ?: "Unknown track", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(screen.track.artist ?: "Unknown artist")
                screen.track.stableKey?.let {
                    Text("Track identity: $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(
            onClick = { onPlay(screen.track) },
            enabled = !busy && !screen.track.title.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("▶ Играть на телефоне") }
        Text(
            "Системная MediaSession управляет playback, а provider/source по-прежнему выбирает Baskov backend.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, busy: Boolean, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = onBack, enabled = !busy) { Text("← Назад") }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BottomActions(busy: Boolean, onRefresh: () -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Обновить") }
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Назад") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeSummary(home: HomeSnapshot) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Твой Baskov", fontWeight = FontWeight.Bold)
            Text("Избранное: ${home.library.favorites}")
            Text("История: ${home.library.personalHistory}")
            Text("Сигналов вкуса: ${home.taste.evidenceSignals}")
            Text("Уверенность: ${(home.taste.confidence * 100).toInt()}%")
        }
    }
}

@Composable
private fun SectionTitle(value: String) =
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun MixCardView(mix: MixCard, busy: Boolean, onOpen: (MixCard) -> Unit) {
    val canOpen = !busy && !mix.stationSlug.isNullOrBlank()
    Card(
        onClick = { onOpen(mix) },
        enabled = canOpen,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mix.label ?: mix.stationSlug ?: "Mix", fontWeight = FontWeight.SemiBold)
            mix.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (!mix.available) Text("Пока недоступен", style = MaterialTheme.typography.labelSmall)
            if (mix.daily) Text("Ежедневный", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TrackCardView(
    track: TrackPreview,
    busy: Boolean,
    onOpen: (TrackPreview) -> Unit,
    onPlay: (TrackPreview) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(track.title ?: "Unknown track", fontWeight = FontWeight.SemiBold)
            Text(track.artist ?: "Unknown artist", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onOpen(track) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Открыть") }
                Button(
                    onClick = { onPlay(track) },
                    enabled = !busy && !track.title.isNullOrBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("▶ Играть") }
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    playback: LocalPlaybackUiState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
) {
    val track = playback.current ?: return
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = playback.artworkUrl,
                    contentDescription = null,
                    fallback = painterResource(R.drawable.ic_launcher_foreground),
                    error = painterResource(R.drawable.ic_launcher_foreground),
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f)) {
                    Text(track.title ?: "Unknown track", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${track.artist ?: "Unknown artist"} • ${formatPlaybackTime(playback.positionMillis)} • ${playbackStatus(playback)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onPrevious, enabled = playback.hasPrevious) { Text("⏮") }
                TextButton(onClick = onToggle) { Text(if (playback.isPlaying) "⏸" else "▶") }
                TextButton(onClick = onNext, enabled = playback.hasNext) { Text("⏭") }
                TextButton(onClick = onStop) { Text("⏹") }
            }
            Text(
                "Нажми на плеер, чтобы открыть Now Playing и очередь.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun NowPlayingScreen(
    playback: LocalPlaybackUiState,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onStop: () -> Unit,
) {
    val track = playback.current ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text("‹ Назад") }
                Text(
                    "СЕЙЧАС ИГРАЕТ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onStop) { Text("■") }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2B0A4D), Color(0xFF0B1430), Color(0xFF070712)),
                        ),
                    )
                    .border(1.dp, BaskovCyan.copy(alpha = 0.45f), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = playback.artworkUrl,
                    contentDescription = "${track.title ?: "Track"} artwork",
                    fallback = painterResource(R.drawable.ic_launcher_foreground),
                    error = painterResource(R.drawable.ic_launcher_foreground),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (playback.artworkUrl == null) ContentScale.Fit else ContentScale.Crop,
                )
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    track.title ?: "Unknown track",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    track.artist ?: "Unknown artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Трек ${playback.currentIndex + 1} из ${playback.queue.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        item {
            if (playback.canSeek) {
                var dragging by remember(track.stableKey, playback.currentIndex) { mutableStateOf(false) }
                var sliderSeconds by remember(track.stableKey, playback.currentIndex) {
                    mutableStateOf(playback.positionMillis / 1_000f)
                }
                val durationSeconds = (playback.durationMillis / 1_000f).coerceAtLeast(0.001f)
                LaunchedEffect(playback.positionMillis, playback.durationMillis, dragging) {
                    if (!dragging) {
                        sliderSeconds = (playback.positionMillis / 1_000f).coerceIn(0f, durationSeconds)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = sliderSeconds.coerceIn(0f, durationSeconds),
                        onValueChange = {
                            dragging = true
                            sliderSeconds = it
                        },
                        onValueChangeFinished = {
                            dragging = false
                            onSeek((sliderSeconds * 1_000f).toLong())
                        },
                        valueRange = 0f..durationSeconds,
                        enabled = !playback.buffering,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatPlaybackTime(playback.positionMillis), style = MaterialTheme.typography.labelMedium)
                        Text(
                            formatPlaybackTime(playback.durationMillis),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (playback.resumable) {
                Text(
                    "Сохранено на ${formatPlaybackTime(playback.positionMillis)} • Play восстановит эту позицию",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                Text(
                    "Получаю длительность потока…",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onSeekBy(-15_000L) },
                    enabled = playback.canSeek,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("−15", maxLines = 1)
                }
                TextButton(onClick = onPrevious, enabled = playback.hasPrevious) { Text("⏮") }
                Button(
                    onClick = onToggle,
                    enabled = !playback.connecting,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                ) { Text(if (playback.isPlaying) "⏸" else "▶") }
                TextButton(onClick = onNext, enabled = playback.hasNext) { Text("⏭") }
                OutlinedButton(
                    onClick = { onSeekBy(15_000L) },
                    enabled = playback.canSeek,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("+15", maxLines = 1)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onToggleShuffle,
                    enabled = !playback.connecting && playback.queue.size > 1,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (playback.shuffleEnabled) "🔀 Shuffle: ON" else "🔀 Shuffle: OFF")
                }
                OutlinedButton(
                    onClick = onCycleRepeat,
                    enabled = !playback.connecting && playback.queue.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (playback.repeatMode) {
                            Player.REPEAT_MODE_ALL -> "🔁 Repeat: ALL"
                            Player.REPEAT_MODE_ONE -> "🔂 Repeat: ONE"
                            else -> "↪ Repeat: OFF"
                        },
                    )
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "●  ${if (playback.isLocal) "PHONE" else "BASKOV SERVER"}  •  ${playbackStatus(playback).uppercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = BaskovCyan,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item { SectionTitle("Очередь") }
        itemsIndexed(
            playback.queue,
            key = { index, item -> item.stableKey ?: "${item.artist.orEmpty()}::${item.title.orEmpty()}::$index" },
        ) { index, item ->
            QueueTrackCard(
                track = item,
                index = index,
                isCurrent = index == playback.currentIndex,
                queueSize = playback.queue.size,
                queueEditable = !playback.resumable && !playback.connecting,
                onPlay = onPlayQueueItem,
                onRemove = onRemoveQueueItem,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun QueueTrackCard(
    track: TrackPreview,
    index: Int,
    isCurrent: Boolean,
    queueSize: Int,
    queueEditable: Boolean,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${index + 1}. ${track.title ?: "Unknown track"}",
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
            )
            Text(track.artist ?: "Unknown artist", style = MaterialTheme.typography.bodySmall)
            if (isCurrent) {
                Text("Сейчас играет", style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onPlay(index) },
                    enabled = queueEditable && !isCurrent,
                    modifier = Modifier.weight(1f),
                ) { Text(if (isCurrent) "Текущий" else "Играть") }
                TextButton(
                    onClick = { onRemove(index) },
                    enabled = queueEditable && queueSize > 1,
                    modifier = Modifier.weight(1f),
                ) { Text("Убрать") }
            }
        }
    }
}

private fun formatPlaybackTime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private fun playbackStatus(playback: LocalPlaybackUiState): String = when {
    playback.connecting -> "подключение…"
    playback.buffering -> "загрузка…"
    playback.isPlaying -> "играет"
    playback.resumable -> "можно восстановить"
    else -> "пауза"
}

@Composable
private fun EmptyCard(message: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
    }
}
