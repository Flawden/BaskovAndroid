package ru.flawden.baskovmusic.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
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
import ru.flawden.baskovmusic.model.LocalTrack
import ru.flawden.baskovmusic.model.PlaybackQueueSpec
import ru.flawden.baskovmusic.model.TrackPreview

/** UI-side controller for the single system MediaSessionService. */
class LocalPlaybackController(context: Context) : Player.Listener, AutoCloseable {
    private val appContext = context.applicationContext
    private val playbackModeStore = PlaybackModeStore(appContext)
    private val initialModes = playbackModeStore.load()
    private val mutableState = MutableStateFlow(
        LocalPlaybackUiState(
            connecting = true,
            shuffleEnabled = initialModes.shuffleEnabled,
            repeatMode = initialModes.repeatMode,
        ),
    )
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
        scope.launch {
            FavoriteStateBridge.state.collect { favorite ->
                val currentKey = mutableState.value.current?.stableKey
                mutableState.value = mutableState.value.copy(
                    favoriteSupported = favorite.supported && favorite.stableKey == currentKey,
                    isFavorite = favorite.supported && favorite.stableKey == currentKey && favorite.favorite,
                )
            }
        }
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
                            pending.bearerToken?.let(PlaybackAuthBridge::update) ?: PlaybackAuthBridge.clear()
                            playNow(connected, pending.mediaItems, pending.startIndex)
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
        enqueueOrPlay(mediaItems, startIndex, spec.bearerToken)
    }

    fun playLocal(tracks: List<LocalTrack>, startIndex: Int) {
        require(tracks.isNotEmpty()) { "Local playback queue is empty" }
        val mediaItems = tracks.map { track ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .apply {
                    track.album?.let(::setAlbumTitle)
                    track.artworkUri?.let { setArtworkUri(Uri.parse(it)) }
                }
                .build()
            MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaId("local:${track.id}")
                .setMediaMetadata(metadata)
                .build()
        }
        enqueueOrPlay(mediaItems, startIndex, bearerToken = null)
    }

    private fun enqueueOrPlay(mediaItems: List<MediaItem>, startIndex: Int, bearerToken: String?) {
        if (bearerToken == null) PlaybackAuthBridge.clear() else PlaybackAuthBridge.update(bearerToken)
        mutableState.value = mutableState.value.copy(resumable = false, error = null)
        val connected = controller
        if (connected == null) {
            pendingPlay = PendingPlay(mediaItems, startIndex, bearerToken)
            mutableState.value = mutableState.value.copy(connecting = true, error = null)
            return
        }
        playNow(connected, mediaItems, startIndex)
    }

    fun toggleFavorite() {
        val connected = controller ?: return
        if (!mutableState.value.favoriteSupported) return
        connected.sendCustomCommand(
            SessionCommand(PlaybackService.ACTION_TOGGLE_FAVORITE, Bundle.EMPTY),
            Bundle.EMPTY,
        )
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
    }

    fun next() {
        val connected = controller ?: return
        val nativeTarget = connected.nextMediaItemIndex
        val targetIndex = if (nativeTarget in 0 until connected.mediaItemCount) {
            nativeTarget
        } else if (connected.repeatMode == Player.REPEAT_MODE_ALL) {
            connected.currentTimeline.getFirstWindowIndex(connected.shuffleModeEnabled)
        } else {
            -1
        }
        if (targetIndex in 0 until connected.mediaItemCount) {
            playFromStart(connected, targetIndex)
        }
        publish()
    }

    fun previous() {
        val connected = controller ?: return
        if (mutableState.value.positionMillis > 3_000L) {
            seekTo(0L)
            return
        }
        val nativeTarget = connected.previousMediaItemIndex
        val targetIndex = if (nativeTarget in 0 until connected.mediaItemCount) {
            nativeTarget
        } else if (connected.repeatMode == Player.REPEAT_MODE_ALL) {
            connected.currentTimeline.getLastWindowIndex(connected.shuffleModeEnabled)
        } else {
            -1
        }
        if (targetIndex in 0 until connected.mediaItemCount) {
            playFromStart(connected, targetIndex)
        }
        publish()
    }

    fun playQueueItem(index: Int) {
        val connected = controller ?: return
        if (mutableState.value.resumable || index !in 0 until connected.mediaItemCount) return
        playFromStart(connected, index)
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
        val currentUri = connected.currentMediaItem?.localConfiguration?.uri?.toString()
        if (PlaybackStreamUrl.isRemoteHttp(currentUri)) {
            restartQueueAt(
                connected = connected,
                index = index,
                startMillis = target,
                playWhenReady = connected.playWhenReady,
            )
        } else {
            connected.seekTo(index, target)
        }
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

    fun toggleShuffle() {
        val connected = controller ?: return
        val enabled = !connected.shuffleModeEnabled
        connected.shuffleModeEnabled = enabled
        playbackModeStore.saveShuffle(enabled)
        publish(error = null)
    }

    fun cycleRepeatMode() {
        val connected = controller ?: return
        val next = when (connected.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        connected.repeatMode = next
        playbackModeStore.saveRepeatMode(next)
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
        val modes = playbackModeStore.load()
        mutableState.value = LocalPlaybackUiState(
            connecting = controller == null,
            shuffleEnabled = modes.shuffleEnabled,
            repeatMode = modes.repeatMode,
        )
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
    override fun onPlaybackStateChanged(playbackState: Int) = publish()
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish(error = null)
    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = publish()
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = publish()
    override fun onRepeatModeChanged(repeatMode: Int) = publish()
    override fun onPlayerError(error: PlaybackException) {
        publish(error = error.message ?: error.errorCodeName)
    }

    private fun playFromStart(connected: MediaController, index: Int) {
        val targetUri = connected.getMediaItemAt(index).localConfiguration?.uri?.toString()
        if (PlaybackStreamUrl.isRemoteHttp(targetUri)) {
            restartQueueAt(connected, index, 0L, playWhenReady = true)
        } else {
            connected.seekToDefaultPosition(index)
            connected.play()
        }
    }

    private fun restartQueueAt(
        connected: MediaController,
        index: Int,
        startMillis: Long,
        playWhenReady: Boolean,
    ) {
        val targetUri = connected.getMediaItemAt(index).localConfiguration?.uri?.toString()
        val targetIsRemote = PlaybackStreamUrl.isRemoteHttp(targetUri)
        val rebuilt = (0 until connected.mediaItemCount).map { itemIndex ->
            val item = connected.getMediaItemAt(itemIndex)
            val uri = item.localConfiguration?.uri?.toString()
            if (uri == null || !PlaybackStreamUrl.isRemoteHttp(uri)) {
                item
            } else {
                val itemStartMillis = if (targetIsRemote && itemIndex == index) startMillis else 0L
                item.buildUpon()
                    .setUri(PlaybackStreamUrl.withStartMillis(uri, itemStartMillis))
                    .build()
            }
        }
        val playerStartMillis = if (targetIsRemote) 0L else startMillis
        connected.setMediaItems(rebuilt, index, playerStartMillis)
        connected.prepare()
        if (playWhenReady) connected.play() else connected.pause()
    }

    private fun playNow(connected: MediaController, mediaItems: List<MediaItem>, startIndex: Int) {
        val safeIndex = startIndex.coerceIn(mediaItems.indices)
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
                    artworkUrl = snapshot.items.getOrNull(snapshot.currentIndex)?.artworkUri,
                    isLocal = !snapshot.currentIsRemote,
                    isPlaying = false,
                    buffering = false,
                    connecting = false,
                    resumable = true,
                    shuffleEnabled = playbackModeStore.load().shuffleEnabled,
                    repeatMode = playbackModeStore.load().repeatMode,
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
        val isRemote = PlaybackStreamUrl.isRemoteHttp(currentUri)
        val streamOffsetMillis = if (isRemote) PlaybackStreamUrl.startMillis(currentUri) else 0L
        val absolutePositionMillis = (
            streamOffsetMillis + connected.currentPosition.coerceAtLeast(0L)
        ).coerceAtLeast(0L)
        val serverDurationMillis = if (isRemote) PlaybackStreamMetrics.durationMillis(currentUri) else 0L
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
            isLocal = currentUri != null && !isRemote,
            isPlaying = connected.isPlaying,
            buffering = connected.playbackState == Player.STATE_BUFFERING,
            connecting = false,
            resumable = false,
            shuffleEnabled = connected.shuffleModeEnabled,
            repeatMode = connected.repeatMode,
            favoriteSupported = FavoriteStateBridge.state.value.let { favorite ->
                favorite.supported && favorite.stableKey == queue.getOrNull(currentIndex)?.stableKey
            },
            isFavorite = FavoriteStateBridge.state.value.let { favorite ->
                favorite.supported && favorite.stableKey == queue.getOrNull(currentIndex)?.stableKey && favorite.favorite
            },
            canGoPrevious = connected.hasPreviousMediaItem() ||
                (connected.repeatMode == Player.REPEAT_MODE_ALL && connected.mediaItemCount > 0),
            canGoNext = connected.hasNextMediaItem() ||
                (connected.repeatMode == Player.REPEAT_MODE_ALL && connected.mediaItemCount > 0),
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
        val mediaItems: List<MediaItem>,
        val startIndex: Int,
        val bearerToken: String?,
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
    val isLocal: Boolean = false,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val connecting: Boolean = false,
    val resumable: Boolean = false,
    val shuffleEnabled: Boolean = false,
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val favoriteSupported: Boolean = false,
    val isFavorite: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
    val error: String? = null,
) {
    val current: TrackPreview?
        get() = queue.getOrNull(currentIndex)

    val hasPrevious: Boolean
        get() = !resumable && canGoPrevious

    val hasNext: Boolean
        get() = !resumable && canGoNext

    val canSeek: Boolean
        get() = !resumable && !connecting && currentIndex >= 0 && durationMillis > 0L
}

private const val PROGRESS_INTERVAL_MILLIS = 500L
