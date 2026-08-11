package ru.flawden.baskovmusic.ui

import ru.flawden.baskovmusic.model.AccountInfo
import ru.flawden.baskovmusic.model.GuildSummary
import ru.flawden.baskovmusic.model.HomeSnapshot
import ru.flawden.baskovmusic.model.LibrarySnapshot
import ru.flawden.baskovmusic.model.MixDetail
import ru.flawden.baskovmusic.model.MixesSnapshot
import ru.flawden.baskovmusic.model.TrackPreview

sealed interface AppScreen {
    data object Loading : AppScreen
    data class Pairing(val savedBaseUrl: String = "") : AppScreen
    data class GuildPicker(val account: AccountInfo, val guilds: List<GuildSummary>) : AppScreen
    data class Home(val account: AccountInfo, val guild: GuildSummary, val snapshot: HomeSnapshot) : AppScreen
    data class Library(val account: AccountInfo, val guild: GuildSummary, val snapshot: LibrarySnapshot) : AppScreen
    data class Mixes(val account: AccountInfo, val guild: GuildSummary, val snapshot: MixesSnapshot) : AppScreen
    data class Mix(val account: AccountInfo, val guild: GuildSummary, val detail: MixDetail) : AppScreen
    data class Track(val account: AccountInfo, val guild: GuildSummary, val track: TrackPreview) : AppScreen
}

data class AppUiState(
    val screen: AppScreen = AppScreen.Loading,
    val busy: Boolean = false,
    val error: String? = null,
)
