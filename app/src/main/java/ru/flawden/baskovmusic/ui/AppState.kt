package ru.flawden.baskovmusic.ui

import ru.flawden.baskovmusic.model.AccountInfo
import ru.flawden.baskovmusic.model.GuildSummary
import ru.flawden.baskovmusic.model.FavoriteSnapshot
import ru.flawden.baskovmusic.model.ServerHubItem
import ru.flawden.baskovmusic.model.HomeSnapshot
import ru.flawden.baskovmusic.model.LibrarySnapshot
import ru.flawden.baskovmusic.model.LocalMusicFolder
import ru.flawden.baskovmusic.model.LocalTrack
import ru.flawden.baskovmusic.model.MixDetail
import ru.flawden.baskovmusic.model.MixesSnapshot
import ru.flawden.baskovmusic.model.SharedPlaylistDetail
import ru.flawden.baskovmusic.model.SharedPlaylistSummary
import ru.flawden.baskovmusic.model.TrackPreview

sealed interface AppScreen {
    data object Loading : AppScreen
    data class Pairing(val savedBaseUrl: String = "") : AppScreen
    data class GuildPicker(val account: AccountInfo, val guilds: List<GuildSummary>) : AppScreen
    data class Home(val account: AccountInfo, val guild: GuildSummary, val snapshot: HomeSnapshot) : AppScreen
    data class Library(val account: AccountInfo, val guild: GuildSummary, val snapshot: LibrarySnapshot) : AppScreen
    data class LocalMusic(
        val account: AccountInfo,
        val guild: GuildSummary,
        val permissionGranted: Boolean,
        val tracks: List<LocalTrack>,
        val folders: List<LocalMusicFolder>,
        val folderFilterEnabled: Boolean,
        val selectedFolders: Set<String>,
    ) : AppScreen
    data class Search(
        val account: AccountInfo,
        val guild: GuildSummary,
        val query: String = "",
        val remoteResults: List<TrackPreview> = emptyList(),
        val localResults: List<LocalTrack> = emptyList(),
        val searched: Boolean = false,
    ) : AppScreen
    data class Favorites(
        val account: AccountInfo,
        val guild: GuildSummary,
        val snapshot: FavoriteSnapshot,
        val addQuery: String = "",
        val addResults: List<TrackPreview> = emptyList(),
        val addSearched: Boolean = false,
    ) : AppScreen
    data class Servers(
        val account: AccountInfo,
        val currentGuild: GuildSummary,
        val servers: List<ServerHubItem>,
    ) : AppScreen
    data class Playlists(
        val account: AccountInfo,
        val guild: GuildSummary,
        val playlists: List<SharedPlaylistSummary>,
    ) : AppScreen
    data class Playlist(
        val account: AccountInfo,
        val guild: GuildSummary,
        val detail: SharedPlaylistDetail,
        val addQuery: String = "",
        val addResults: List<TrackPreview> = emptyList(),
        val addSearched: Boolean = false,
    ) : AppScreen
    data class Mixes(val account: AccountInfo, val guild: GuildSummary, val snapshot: MixesSnapshot) : AppScreen
    data class Mix(val account: AccountInfo, val guild: GuildSummary, val detail: MixDetail) : AppScreen
    data class Track(val account: AccountInfo, val guild: GuildSummary, val track: TrackPreview) : AppScreen
}

data class AppUiState(
    val screen: AppScreen = AppScreen.Loading,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)
