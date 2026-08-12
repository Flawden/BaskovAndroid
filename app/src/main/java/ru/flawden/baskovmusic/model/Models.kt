package ru.flawden.baskovmusic.model

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
) {
    val preview: TrackPreview
        get() = TrackPreview(
            stableKey = "local:$id",
            title = title,
            artist = artist,
        )
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

data class PlaybackQueueItem(
    val track: TrackPreview,
    val streamUrl: String,
)

data class PlaybackQueueSpec(
    val bearerToken: String,
    val items: List<PlaybackQueueItem>,
)
