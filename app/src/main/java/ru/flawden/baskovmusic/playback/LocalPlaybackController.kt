package ru.flawden.baskovmusic.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.flawden.baskovmusic.model.PlaybackQueueSpec
import ru.flawden.baskovmusic.model.TrackPreview

/**
 * Foreground-only local playback for v0.3. The controller consumes only Baskov's authenticated
 * Ogg/Opus stream URLs; it never performs YouTube/SoundCloud search or provider extraction.
 */
class LocalPlaybackController(context: Context) : Player.Listener, AutoCloseable {
    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("BaskovAndroid/0.3.0")
    private val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(httpFactory)
    private val player = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    private val mutableState = MutableStateFlow(LocalPlaybackUiState())
    private var queue: List<TrackPreview> = emptyList()

    val state: StateFlow<LocalPlaybackUiState> = mutableState.asStateFlow()

    init {
        player.addListener(this)
    }

    @OptIn(UnstableApi::class)
    fun play(spec: PlaybackQueueSpec, startIndex: Int) {
        require(spec.items.isNotEmpty()) { "Playback queue is empty" }
        val safeIndex = startIndex.coerceIn(spec.items.indices)
        queue = spec.items.map { it.track }
        httpFactory.setDefaultRequestProperties(
            mapOf(
                "Authorization" to "Bearer ${spec.bearerToken}",
                "Accept" to "audio/ogg",
            ),
        )

        val mediaItems = spec.items.mapIndexed { index, item ->
            MediaItem.Builder()
                .setUri(item.streamUrl)
                .setMediaId(item.track.stableKey ?: "baskov-$index")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.track.title ?: "Unknown track")
                        .setArtist(item.track.artist ?: "Unknown artist")
                        .build(),
                )
                .build()
        }

        player.setMediaItems(mediaItems, safeIndex, 0L)
        player.prepare()
        player.play()
        publish(error = null)
    }

    fun togglePlayPause() {
        if (player.mediaItemCount == 0) return
        if (player.isPlaying) player.pause() else player.play()
        publish()
    }

    fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
        }
        publish()
    }

    fun previous() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
        } else if (player.currentPosition > 3_000L) {
            player.seekTo(0L)
        }
        publish()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        mutableState.value = LocalPlaybackUiState()
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        publish()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        publish()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        publish(error = null)
    }

    override fun onPlayerError(error: PlaybackException) {
        publish(error = error.message ?: error.errorCodeName)
    }

    private fun publish(error: String? = mutableState.value.error) {
        val index = player.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        mutableState.value = LocalPlaybackUiState(
            queue = queue,
            currentIndex = index,
            isPlaying = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            error = error,
        )
    }

    override fun close() {
        player.removeListener(this)
        player.release()
        queue = emptyList()
    }
}

data class LocalPlaybackUiState(
    val queue: List<TrackPreview> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
) {
    val current: TrackPreview?
        get() = queue.getOrNull(currentIndex)

    val hasPrevious: Boolean
        get() = currentIndex > 0

    val hasNext: Boolean
        get() = currentIndex >= 0 && currentIndex < queue.lastIndex
}
