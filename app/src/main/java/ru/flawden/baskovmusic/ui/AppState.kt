package ru.flawden.baskovmusic.ui

import ru.flawden.baskovmusic.model.AccountInfo
import ru.flawden.baskovmusic.model.GuildSummary
import ru.flawden.baskovmusic.model.HomeSnapshot

sealed interface AppScreen {
    data object Loading : AppScreen
    data class Pairing(val savedBaseUrl: String = "") : AppScreen
    data class GuildPicker(val account: AccountInfo, val guilds: List<GuildSummary>) : AppScreen
    data class Home(val account: AccountInfo, val guild: GuildSummary, val snapshot: HomeSnapshot) : AppScreen
}

data class AppUiState(
    val screen: AppScreen = AppScreen.Loading,
    val busy: Boolean = false,
    val error: String? = null,
)
