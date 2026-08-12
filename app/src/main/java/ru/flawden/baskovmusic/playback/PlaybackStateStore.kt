package ru.flawden.baskovmusic.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.nio.charset.StandardCharsets
import java.util.Base64

internal class PlaybackStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "baskov_playback_resume",
        Context.MODE_PRIVATE,
    )

    fun save(player: Player) {
        if (player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            clear()
            return
        }
        val items = (0 until player.mediaItemCount).mapNotNull { index ->
            val mediaItem = player.getMediaItemAt(index)
            val uri = mediaItem.localConfiguration?.uri?.toString() ?: return@mapNotNull null
            PlaybackSnapshotItem(
                mediaId = mediaItem.mediaId,
                uri = uri,
                title = mediaItem.mediaMetadata.title?.toString(),
                artist = mediaItem.mediaMetadata.artist?.toString(),
            )
        }
        if (items.isEmpty()) {
            clear()
            return
        }
        val safeIndex = player.currentMediaItemIndex.coerceIn(items.indices)
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        val absolutePositionMillis = (
            PlaybackStreamUrl.startMillis(currentUri) +
                player.currentPosition.coerceAtLeast(0L)
            ).coerceAtLeast(0L)
        save(
            PlaybackSnapshot(
                items = items,
                currentIndex = safeIndex,
                positionMillis = absolutePositionMillis,
                playWhenReady = player.playWhenReady,
                savedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    fun save(snapshot: PlaybackSnapshot) {
        preferences.edit()
            .putString(SNAPSHOT, PlaybackSnapshotCodec.encode(snapshot))
            .apply()
    }

    fun load(): PlaybackSnapshot? = preferences.getString(SNAPSHOT, null)
        ?.let(PlaybackSnapshotCodec::decode)

    fun clear() {
        preferences.edit().remove(SNAPSHOT).apply()
    }

    private companion object {
        const val SNAPSHOT = "snapshot_v1"
    }
}

internal data class PlaybackSnapshot(
    val items: List<PlaybackSnapshotItem>,
    val currentIndex: Int,
    val positionMillis: Long,
    val playWhenReady: Boolean,
    val savedAtEpochMillis: Long,
) {
    fun toMediaItems(): List<MediaItem> = items.mapIndexed { index, item ->
        val startMillis = if (index == currentIndex) positionMillis else 0L
        MediaItem.Builder()
            .setUri(PlaybackStreamUrl.withStartMillis(item.uri, startMillis))
            .setMediaId(item.mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title ?: "Unknown track")
                    .setArtist(item.artist ?: "Unknown artist")
                    .build(),
            )
            .build()
    }
}

internal data class PlaybackSnapshotItem(
    val mediaId: String,
    val uri: String,
    val title: String?,
    val artist: String?,
)

internal object PlaybackSnapshotCodec {
    private const val VERSION = "1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(snapshot: PlaybackSnapshot): String = buildString {
        append(VERSION)
        append('|').append(snapshot.currentIndex)
        append('|').append(snapshot.positionMillis)
        append('|').append(if (snapshot.playWhenReady) 1 else 0)
        append('|').append(snapshot.savedAtEpochMillis)
        snapshot.items.forEach { item ->
            append('\n')
            append(encodeField(item.mediaId)).append('|')
            append(encodeField(item.uri)).append('|')
            append(encodeField(item.title)).append('|')
            append(encodeField(item.artist))
        }
    }

    fun decode(value: String): PlaybackSnapshot? = runCatching {
        val lines = value.lineSequence().filter(String::isNotBlank).toList()
        require(lines.size >= 2) { "Playback snapshot has no items" }
        val header = lines.first().split('|')
        require(header.size == 5 && header[0] == VERSION) { "Unsupported playback snapshot" }
        val items = lines.drop(1).map { line ->
            val fields = line.split('|')
            require(fields.size == 4) { "Corrupted playback item" }
            PlaybackSnapshotItem(
                mediaId = decodeField(fields[0]).orEmpty(),
                uri = requireNotNull(decodeField(fields[1])).also { require(it.isNotBlank()) },
                title = decodeField(fields[2]),
                artist = decodeField(fields[3]),
            )
        }
        val currentIndex = header[1].toInt()
        require(currentIndex in items.indices) { "Playback index is outside the queue" }
        PlaybackSnapshot(
            items = items,
            currentIndex = currentIndex,
            positionMillis = header[2].toLong().coerceAtLeast(0L),
            playWhenReady = header[3] == "1",
            savedAtEpochMillis = header[4].toLong(),
        )
    }.getOrNull()

    private fun encodeField(value: String?): String = value
        ?.toByteArray(StandardCharsets.UTF_8)
        ?.let(encoder::encodeToString)
        .orEmpty()

    private fun decodeField(value: String): String? = if (value.isEmpty()) {
        null
    } else {
        String(decoder.decode(value), StandardCharsets.UTF_8)
    }
}
