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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.flawden.baskovmusic.model.PlaybackQueueSpec
import ru.flawden.baskovmusic.model.TrackPreview

/**
 * UI-side controller for the system MediaSessionService.
 *
 * v0.5 can also surface a persisted playback snapshot when the process was recreated. The snapshot
 * is display-only until the user presses Play; that command triggers Media3 onPlaybackResumption,
 * where PlaybackService recovers a fresh Bearer token before preparing the restored queue.
 * v0.6 exposes safe queue navigation/removal commands for the full Now Playing surface while the
 * MediaSession remains the single source of truth for both UI and system playback controls.
 */
class LocalPlaybackController(context: Context) : Player.Listener, AutoCloseable {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(LocalPlaybackUiState(connecting = true))
    private val playbackStateStore = PlaybackStateStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, PlaybackService::class.java),
    )
    private val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()

    private var controller: MediaController? = null
    private var pendingPlay: PendingPlay? = null
    private var progressJob: Job? = null
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
                        progressJob?.cancel()
                        progressJob = scope.launch {
                            while (isActive && !closed && controller === connected) {
                                delay(PROGRESS_INTERVAL_MILLIS)
                                if (connected.mediaItemCount > 0) publish()
                            }
                        }
                        publish(error = null)
                        pendingPlay?.let { pending ->
                            pendingPlay = null
                            playNow(connected, pending.spec, pending.startIndex)
                        } ?: loadResumableSnapshot(connected)
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
        mutableState.value = mutableState.value.copy(resumable = false, error = null)
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
        if (connected.mediaItemCount == 0) {
            if (mutableState.value.resumable) {
                mutableState.value = mutableState.value.copy(connecting = true, error = null)
                connected.play()
            }
            return
        }
        if (connected.isPlaying) connected.pause() else connected.play()
        publish()
    }    fun next() {
        val connected = controller ?: return
        val targetIndex = connected.currentMediaItemIndex + 1
        if (targetIndex in 0 until connected.mediaItemCount) {
            restartQueueAt(connected, targetIndex, 0L, playWhenReady = true)
        }
        publish()
    }

    fun previous() {
        val connected = controller ?: return
        if (mutableState.value.positionMillis > 3_000L) {
            seekTo(0L)
            return
        }
        val targetIndex = connected.currentMediaItemIndex - 1
        if (targetIndex in 0 until connected.mediaItemCount) {
            restartQueueAt(connected, targetIndex, 0L, playWhenReady = true)
        }
        publish()
    }

    fun playQueueItem(index: Int) {
        val connected = controller ?: return
        if (mutableState.value.resumable || index !in 0 until connected.mediaItemCount) return
        restartQueueAt(connected, index, 0L, playWhenReady = true)
        publish(error = null)
    }

    fun seekTo(positionMillis: Long) {
        val connected = controller ?: return
        val state = mutableState.value
        if (state.resumable || connected.mediaItemCount == 0) return

        val index = connected.currentMediaItemIndex
        if (index !in 0 until connected.mediaItemCount) return

        val target = if (state.durationMillis > 0L) {
            positionMillis.coerceIn(0L, (state.durationMillis - 1L).coerceAtLeast(0L))
        } else {
            positionMillis.coerceAtLeast(0L)
        }
        restartQueueAt(
            connected = connected,
            index = index,
            startMillis = target,
            playWhenReady = connected.playWhenReady,
        )
        publish(error = null)
    }

    fun seekBy(deltaMillis: Long) = seekTo(mutableState.value.positionMillis + deltaMillis)

    fun removeQueueItem(index: Int) {
        val connected = controller ?: return
        if (mutableState.value.resumable || index !in 0 until connected.mediaItemCount) return
        if (connected.mediaItemCount == 1) {
            stop()
            return
        }
        connected.removeMediaItem(index)
        publish(error = null)
    }

    fun stop() {
        pendingPlay = null
        controller?.run {
            stop()
            clearMediaItems()
        }
        playbackStateStore.clear()
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

    private fun restartQueueAt(
        connected: MediaController,
        index: Int,
        startMillis: Long,
        playWhenReady: Boolean,
    ) {
        val rebuilt = (0 until connected.mediaItemCount).map { itemIndex ->
            val item = connected.getMediaItemAt(itemIndex)
            val uri = item.localConfiguration?.uri?.toString()
            if (uri == null) {
                item
            } else {
                val itemStartMillis = if (itemIndex == index) startMillis else 0L
                item.buildUpon()
                    .setUri(PlaybackStreamUrl.withStartMillis(uri, itemStartMillis))
                    .build()
            }
        }
        connected.setMediaItems(rebuilt, index, 0L)
        connected.prepare()
        if (playWhenReady) connected.play() else connected.pause()
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

    private fun loadResumableSnapshot(connected: MediaController) {
        scope.launch(Dispatchers.IO) snapshotLoad@ {
            val snapshot = playbackStateStore.load() ?: return@snapshotLoad
            launch(Dispatchers.Main.immediate) publishSnapshot@ {
                if (closed || controller !== connected || connected.mediaItemCount != 0) {
                    return@publishSnapshot
                }
                val queue = snapshot.items.map { item ->
                    TrackPreview(
                        stableKey = item.mediaId.takeIf(String::isNotBlank),
                        title = item.title,
                        artist = item.artist,
                    )
                }
                mutableState.value = LocalPlaybackUiState(
                    queue = queue,
                    currentIndex = snapshot.currentIndex.coerceIn(queue.indices),
                    positionMillis = snapshot.positionMillis,
                    durationMillis = 0L,
                    isPlaying = false,
                    buffering = false,
                    connecting = false,
                    resumable = true,
                    error = null,
                )
            }
        }
    }

    private fun publish(error: String? = mutableState.value.error) {
        val connected = controller
        if (connected == null) {
            mutableState.value = mutableState.value.copy(connecting = true, error = error)
            return
        }

        if (connected.mediaItemCount == 0 && mutableState.value.resumable) {
            mutableState.value = mutableState.value.copy(
                isPlaying = false,
                buffering = connected.playbackState == Player.STATE_BUFFERING,
                connecting = false,
                error = error,
            )
            return
        }

        val queue = (0 until connected.mediaItemCount).map { index ->
            connected.getMediaItemAt(index).toTrackPreview()
        }

        val currentIndex = connected.currentMediaItemIndex.takeIf { it in queue.indices } ?: -1
        val currentUri = connected.currentMediaItem?.localConfiguration?.uri?.toString()
        val streamOffsetMillis = PlaybackStreamUrl.startMillis(currentUri)
        val absolutePositionMillis = (
            streamOffsetMillis + connected.currentPosition.coerceAtLeast(0L)
            ).coerceAtLeast(0L)
        val serverDurationMillis = PlaybackStreamMetrics.durationMillis(currentUri)
        val fallbackDurationMillis = connected.duration
            .takeIf { it > 0L }
            ?.let { streamOffsetMillis + it }
            ?: 0L
        val durationMillis = maxOf(serverDurationMillis, fallbackDurationMillis)
        mutableState.value = LocalPlaybackUiState(
            queue = queue,
            currentIndex = currentIndex,
            positionMillis = if (durationMillis > 0L) {
                absolutePositionMillis.coerceAtMost(durationMillis)
            } else {
                absolutePositionMillis
            },
            durationMillis = durationMillis,
            artworkUrl = connected.currentMediaItem?.mediaMetadata?.artworkUri?.toString(),
            isPlaying = connected.isPlaying,
            buffering = connected.playbackState == Player.STATE_BUFFERING,
            connecting = false,
            resumable = false,
            error = error,
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        pendingPlay = null
        controller?.removeListener(this)
        controller = null
        scope.cancel()
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
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val connecting: Boolean = false,
    val resumable: Boolean = false,
    val error: String? = null,
) {
    val current: TrackPreview?
        get() = queue.getOrNull(currentIndex)

    val hasPrevious: Boolean
        get() = !resumable && currentIndex > 0

    val hasNext: Boolean
        get() = !resumable && currentIndex >= 0 && currentIndex < queue.lastIndex

    val canSeek: Boolean
        get() = !resumable && !connecting && currentIndex >= 0 && durationMillis > 0L
}

private const val PROGRESS_INTERVAL_MILLIS = 500L
