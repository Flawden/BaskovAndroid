package ru.flawden.baskovmusic.model

import java.security.MessageDigest
import java.util.Locale

data class SessionTokens(
    val userId: String,
    val sessionId: String,
    val deviceName: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochMillis: Long,
    val refreshExpiresAtEpochMillis: Long,
)

data class AccountInfo(
    val userId: String,
    val displayName: String,
    val sessionId: String,
    val deviceName: String,
)

data class GuildSummary(
    val guildId: String,
    val name: String,
)

data class TrackPreview(
    val stableKey: String?,
    val title: String?,
    val artist: String?,
)

data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMillis: Long,
    val contentUri: String,
    val artworkUri: String?,
    val folderPath: String,
) {
    val tasteStableKey: String
        get() {
            val canonical = listOf(
                artist.tasteIdentityPart(),
                title.tasteIdentityPart(),
                album.tasteIdentityPart(),
                durationMillis.coerceAtLeast(0L).toString(),
            ).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
            return "local:v1:$digest"
        }

    val preview: TrackPreview
        get() = TrackPreview(
            stableKey = tasteStableKey,
            title = title,
            artist = artist,
        )
}

enum class TasteSignalType {
    PLAY,
    COMPLETED,
    REPLAY,
    QUICK_SKIP,
    STOP_EARLY,
    FAVORITE_ADD,
    FAVORITE_REMOVE,
}

enum class TasteSignalSource {
    LOCAL,
    REMOTE,
}

data class TasteSignal(
    val type: TasteSignalType,
    val source: TasteSignalSource,
    val stableKey: String?,
    val artist: String,
    val title: String,
    val completionRatio: Double,
)

private fun String?.tasteIdentityPart(): String = this
    .orEmpty()
    .trim()
    .lowercase(Locale.ROOT)
    .replace(Regex("\\s+"), " ")

data class LocalMusicFolder(
    val path: String,
    val trackCount: Int,
) {
    val label: String
        get() = path.trim('/').ifBlank { "Корень хранилища" }
}

data class MixCard(
    val stationSlug: String?,
    val label: String?,
    val description: String?,
    val available: Boolean,
    val daily: Boolean,
)

data class ThemeAffinity(
    val name: String?,
    val affinity: Double,
)

data class LibrarySummary(
    val favorites: Int,
    val personalHistory: Int,
    val recent: List<TrackPreview>,
)

data class LibrarySnapshot(
    val guildId: String,
    val userId: String,
    val favorites: Int,
    val personalHistory: Int,
    val recent: List<TrackPreview>,
    val favoriteTracks: List<TrackPreview>,
    val historyTracks: List<TrackPreview>,
)

data class MixesSnapshot(
    val guildId: String,
    val userId: String,
    val date: String,
    val today: List<MixCard>,
    val forYou: List<MixCard>,
    val themes: List<ThemeAffinity>,
)

data class MixDetail(
    val guildId: String,
    val userId: String,
    val stationSlug: String,
    val label: String,
    val description: String?,
    val available: Boolean,
    val daily: Boolean,
    val seedPreview: List<TrackPreview>,
)

data class TasteSummary(
    val evidenceSignals: Int,
    val confidence: Double,
    val recommendations: Int,
)

data class HomeSnapshot(
    val guildId: String,
    val userId: String,
    val date: String,
    val today: List<MixCard>,
    val forYou: List<MixCard>,
    val themes: List<ThemeAffinity>,
    val library: LibrarySummary,
    val recent: List<TrackPreview>,
    val taste: TasteSummary,
)

data class FavoriteSnapshot(
    val guildId: String,
    val userId: String,
    val total: Int,
    val offset: Int,
    val limit: Int,
    val hasMore: Boolean,
    val tracks: List<TrackPreview>,
)

data class RemotePlayerSnapshot(
    val guildId: String,
    val sessionActive: Boolean,
    val playing: Boolean,
    val paused: Boolean,
    val queueSize: Int,
    val current: TrackPreview?,
)

data class ServerHubItem(
    val guild: GuildSummary,
    val player: RemotePlayerSnapshot?,
)

data class SharedPlaylistSummary(
    val name: String,
    val ownerUserId: String,
    val ownedByMe: Boolean,
    val trackCount: Int,
    val createdAtEpochMillis: Long,
)

data class SharedPlaylistDetail(
    val guildId: String,
    val userId: String,
    val name: String,
    val ownerUserId: String,
    val ownedByMe: Boolean,
    val createdAtEpochMillis: Long,
    val tracks: List<TrackPreview>,
)

data class PlaybackQueueItem(
    val track: TrackPreview,
    val streamUrl: String,
)

data class PlaybackQueueSpec(
    val bearerToken: String,
    val items: List<PlaybackQueueItem>,
)
