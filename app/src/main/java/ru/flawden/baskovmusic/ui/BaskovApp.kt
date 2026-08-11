package ru.flawden.baskovmusic.ui

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaskovApp(viewModel: BaskovViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { TopAppBar(title = { Text("Baskov Music") }) },
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
                        onChangeGuild = viewModel::changeGuild,
                        onLogout = viewModel::logout,
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
        OutlinedButton(onClick = onLogout, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Отключить устройство") }
    }
}

@Composable
private fun HomeScreen(
    screen: AppScreen.Home,
    busy: Boolean,
    onRefresh: () -> Unit,
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
        item { SectionTitle("Сегодня") }
        items(screen.snapshot.today) { MixCardView(it) }
        item { SectionTitle("Для тебя") }
        items(screen.snapshot.forYou) { MixCardView(it) }
        item { SectionTitle("Темы") }
        items(screen.snapshot.themes) { theme ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(theme.name ?: "Theme")
                    Text("${(theme.affinity * 100).toInt()}%")
                }
            }
        }
        item { SectionTitle("Недавнее") }
        items(screen.snapshot.recent) { track ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(track.title ?: "Unknown track", fontWeight = FontWeight.SemiBold)
                    Text(track.artist ?: "Unknown artist", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
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
private fun SectionTitle(value: String) = Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun MixCardView(mix: MixCard) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(mix.label ?: mix.stationSlug ?: "Mix", fontWeight = FontWeight.SemiBold)
            mix.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (!mix.available) Text("Пока недоступен", style = MaterialTheme.typography.labelSmall)
        }
    }
}
