package ru.flawden.baskovmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.flawden.baskovmusic.model.HomeSnapshot
import ru.flawden.baskovmusic.model.MixCard
import ru.flawden.baskovmusic.model.TrackPreview
import ru.flawden.baskovmusic.playback.LocalPlaybackUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaskovApp(viewModel: BaskovViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

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

    val supportsBack = when (state.screen) {
        is AppScreen.Library,
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
    BackHandler(enabled = supportsBack && !state.busy) { viewModel.goBack() }

    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { TopAppBar(title = { Text("Baskov Music") }) },
            bottomBar = {
                if (playback.current != null) {
                    MiniPlayer(
                        playback = playback,
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
                when (val screen = state.screen) {
                    AppScreen.Loading -> CircularProgressIndicator()
                    is AppScreen.Pairing -> PairingScreen(screen.savedBaseUrl, state.busy, viewModel::pair)
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
private fun PairingScreen(savedBaseUrl: String, busy: Boolean, onPair: (String, String) -> Unit) {
    var baseUrl by remember(savedBaseUrl) { mutableStateOf(savedBaseUrl) }
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Подключить устройство", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("В Discord выполни /device pair, затем введи одноразовый код здесь.")
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Baskov API URL") },
            placeholder = { Text("https://music.example.com/") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(16) },
            label = { Text("Pairing code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = { onPair(baseUrl, code) },
            enabled = !busy && baseUrl.isNotBlank() && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Подключить") }
        Text(
            "Release-сборка принимает только HTTPS. Debug разрешает HTTP для локальной разработки.",
            style = MaterialTheme.typography.bodySmall,
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenLibrary, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Библиотека")
                }
                Button(onClick = onOpenMixes, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Миксы")
                }
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
            "v0.4 играет через системную MediaSession, а provider/source по-прежнему выбирает Baskov backend.",
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
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
) {
    val track = playback.current ?: return
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(track.title ?: "Unknown track", fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    append(track.artist ?: "Unknown artist")
                    if (playback.connecting) append(" • подключение…")
                    else if (playback.buffering) append(" • загрузка…")
                    else if (playback.isPlaying) append(" • играет")
                    else if (playback.resumable) append(" • можно восстановить")
                    else append(" • пауза")
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onPrevious, enabled = playback.hasPrevious) { Text("⏮") }
                TextButton(onClick = onToggle) { Text(if (playback.isPlaying) "⏸" else "▶") }
                TextButton(onClick = onNext, enabled = playback.hasNext) { Text("⏭") }
                TextButton(onClick = onStop) { Text("⏹") }
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
    }
}
