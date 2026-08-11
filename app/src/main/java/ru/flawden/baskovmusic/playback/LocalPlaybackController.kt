package ru.flawden.baskovmusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.flawden.baskovmusic.model.PlaybackQueueSpec
import ru.flawden.baskovmusic.model.TrackPreview

/**
 * UI-side controller for the v0.4 MediaSessionService.
 *
 * The Activity/ViewModel no longer owns ExoPlayer. It owns only a MediaController connection,
 * which means leaving the Activity does not tear playback down. The system session is the single
 * source of truth for notification, lock-screen, headset/Bluetooth and in-app controls.
 */
class LocalPlaybackController(context: Context) : Player.Listener, AutoCloseable {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(LocalPlaybackUiState(connecting = true))
    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, PlaybackService::class.java),
    )
    private val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()

    private var controller: MediaController? = null
    private var pendingPlay: PendingPlay? = null
    private var closed = false

    val state: StateFlow<LocalPlaybackUiState> = mutableState.asStateFlow()

    init {
        controllerFuture.addListener(
            {
                if (closed) return@addListener
                runCatching { controllerFuture.get() }
                    .onSuccess { connected ->
                        controller = connected
                        connected.addListener(this)
                        publish(error = null)
                        pendingPlay?.let { pending ->
                            pendingPlay = null
                            playNow(connected, pending.spec, pending.startIndex)
                        }
                    }
                    .onFailure { error ->
                        mutableState.value = mutableState.value.copy(
                            connecting = false,
                            error = error.message ?: error::class.java.simpleName,
                        )
                    }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun play(spec: PlaybackQueueSpec, startIndex: Int) {
        require(spec.items.isNotEmpty()) { "Playback queue is empty" }
        PlaybackAuthBridge.update(spec.bearerToken)
        val connected = controller
        if (connected == null) {
            pendingPlay = PendingPlay(spec, startIndex)
            mutableState.value = mutableState.value.copy(connecting = true, error = null)
            return
        }
        playNow(connected, spec, startIndex)
    }

    fun togglePlayPause() {
        val connected = controller ?: return
        if (connected.mediaItemCount == 0) return
        if (connected.isPlaying) connected.pause() else connected.play()
        publish()
    }

    fun next() {
        controller?.let { connected ->
            if (connected.hasNextMediaItem()) {
                connected.seekToNextMediaItem()
                connected.play()
            }
        }
        publish()
    }

    fun previous() {
        controller?.let { connected ->
            if (connected.hasPreviousMediaItem()) {
                connected.seekToPreviousMediaItem()
                connected.play()
            } else if (connected.currentPosition > 3_000L) {
                connected.seekTo(0L)
            }
        }
        publish()
    }

    fun stop() {
        pendingPlay = null
        controller?.run {
            stop()
            clearMediaItems()
        }
        PlaybackAuthBridge.clear()
        mutableState.value = LocalPlaybackUiState(connecting = controller == null)
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

    override fun onPlaybackStateChanged(playbackState: Int) = publish()

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish(error = null)

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = publish()

    override fun onPlayerError(error: PlaybackException) {
        publish(error = error.message ?: error.errorCodeName)
    }

    private fun playNow(connected: MediaController, spec: PlaybackQueueSpec, startIndex: Int) {
        val safeIndex = startIndex.coerceIn(spec.items.indices)
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

        connected.setMediaItems(mediaItems, safeIndex, 0L)
        connected.prepare()
        connected.play()
        publish(error = null)
    }

    private fun publish(error: String? = mutableState.value.error) {
        val connected = controller
        if (connected == null) {
            mutableState.value = mutableState.value.copy(connecting = true, error = error)
            return
        }

        val queue = (0 until connected.mediaItemCount).map { index ->
            connected.getMediaItemAt(index).toTrackPreview()
        }
        val currentIndex = connected.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1

        mutableState.value = LocalPlaybackUiState(
            queue = queue,
            currentIndex = currentIndex,
            isPlaying = connected.isPlaying,
            buffering = connected.playbackState == Player.STATE_BUFFERING,
            connecting = false,
            error = error,
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        pendingPlay = null
        controller?.removeListener(this)
        controller = null
        MediaController.releaseFuture(controllerFuture)
    }

    private data class PendingPlay(
        val spec: PlaybackQueueSpec,
        val startIndex: Int,
    )
}

private fun MediaItem.toTrackPreview(): TrackPreview = TrackPreview(
    stableKey = mediaId.takeIf { it.isNotBlank() },
    title = mediaMetadata.title?.toString(),
    artist = mediaMetadata.artist?.toString(),
)

data class LocalPlaybackUiState(
    val queue: List<TrackPreview> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val connecting: Boolean = false,
    val error: String? = null,
) {
    val current: TrackPreview?
        get() = queue.getOrNull(currentIndex)

    val hasPrevious: Boolean
        get() = currentIndex > 0

    val hasNext: Boolean
        get() = currentIndex >= 0 && currentIndex < queue.lastIndex
}
