package ru.flawden.baskovmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.flawden.baskovmusic.MainActivity
import ru.flawden.baskovmusic.data.BaskovRepository
import ru.flawden.baskovmusic.data.TasteSignalReporter
import ru.flawden.baskovmusic.data.auth.AuthSessionManager
import ru.flawden.baskovmusic.data.auth.SessionStore
import ru.flawden.baskovmusic.model.TasteSignal
import ru.flawden.baskovmusic.model.TasteSignalSource
import ru.flawden.baskovmusic.model.TasteSignalType
import ru.flawden.baskovmusic.model.TrackPreview

/**
 * System-owned Baskov playback surface.
 *
 * v0.5 adds durable queue/position snapshots plus Media3 playback resumption. The Bearer token is
 * never written into the playback snapshot: a restarted service recovers/rotates the encrypted
 * device session first, then hydrates the process-local PlaybackAuthBridge before Media3 prepares
 * the restored stream URL.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var stateStore: PlaybackStateStore
    private lateinit var authSessionManager: AuthSessionManager
    private lateinit var playbackModeStore: PlaybackModeStore
    private lateinit var repository: BaskovRepository
    private lateinit var tasteSignals: TasteSignalReporter
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var snapshotJob: Job? = null
    private var authMaintenanceJob: Job? = null

    @Volatile
    private var hasPlaybackQueue = false

    @Volatile
    private var hasRemotePlaybackQueue = false

    private var activeTasteTrack: ActiveTasteTrack? = null

    private val persistenceListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            sampleTasteProgress()
            val player = mediaSession?.player
            if (player != null && player.mediaItemCount == 0 && activeTasteTrack != null) {
                finishTasteTrack(classifyEarlyOutcome(activeTasteTrack!!))
            }
            persistCurrentState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            sampleTasteProgress()
            val previous = activeTasteTrack
            val sameTrack = previous != null && mediaItem?.mediaId == previous.stableKey
            when {
                previous == null -> startTasteTrack(mediaItem)
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> {
                    finishTasteTrack(TasteSignalType.REPLAY)
                    startTasteTrack(mediaItem)
                }
                sameTrack -> Unit
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> {
                    finishTasteTrack(TasteSignalType.COMPLETED)
                    startTasteTrack(mediaItem)
                }
                else -> {
                    finishTasteTrack(classifyEarlyOutcome(previous))
                    startTasteTrack(mediaItem)
                }
            }
            normalizeRemoteRepeatOffset(reason)
            persistCurrentState()
            refreshFavoriteState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && activeTasteTrack == null) {
                startTasteTrack(mediaSession?.player?.currentMediaItem)
            }
            sampleTasteProgress()
            persistCurrentState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            sampleTasteProgress()
            persistCurrentState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            sampleTasteProgress()
            if (playbackState == Player.STATE_ENDED && activeTasteTrack != null) {
                val player = mediaSession?.player
                val outcome = if (player?.repeatMode == Player.REPEAT_MODE_OFF) {
                    TasteSignalType.COMPLETED
                } else {
                    TasteSignalType.REPLAY
                }
                finishTasteTrack(outcome)
                recoverRepeatAtEnd()
            }
            persistCurrentState()
        }
    }

    private var repeatRecoveryInProgress = false

    private fun normalizeRemoteRepeatOffset(reason: Int) {
        if (repeatRecoveryInProgress) return
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        ) {
            return
        }
        val player = mediaSession?.player ?: return
        val index = player.currentMediaItemIndex
        if (index !in 0 until player.mediaItemCount) return
        val uri = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        if (!PlaybackStreamUrl.isRemoteHttp(uri) || PlaybackStreamUrl.startMillis(uri) <= 0L) return
        restartQueueFromZero(player, index)
    }

    private fun recoverRepeatAtEnd() {
        if (repeatRecoveryInProgress) return
        val player = mediaSession?.player ?: return
        if (player.mediaItemCount == 0) return
        val targetIndex = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> player.currentMediaItemIndex
            Player.REPEAT_MODE_ALL -> player.currentTimeline.getFirstWindowIndex(player.shuffleModeEnabled)
            else -> return
        }
        if (targetIndex !in 0 until player.mediaItemCount) return
        restartQueueFromZero(player, targetIndex)
    }

    private fun restartQueueFromZero(player: Player, targetIndex: Int) {
        if (repeatRecoveryInProgress) return
        repeatRecoveryInProgress = true
        try {
            val rebuilt = (0 until player.mediaItemCount).map { index ->
                val item = player.getMediaItemAt(index)
                val uri = item.localConfiguration?.uri?.toString()
                if (uri == null || !PlaybackStreamUrl.isRemoteHttp(uri)) {
                    item
                } else {
                    item.buildUpon()
                        .setUri(PlaybackStreamUrl.withStartMillis(uri, 0L))
                        .build()
                }
            }
            player.setMediaItems(rebuilt, targetIndex, 0L)
            player.prepare()
            player.play()
        } finally {
            repeatRecoveryInProgress = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = PlaybackStateStore(this)
        val sessionStore = SessionStore(this)
        authSessionManager = AuthSessionManager(sessionStore)
        repository = BaskovRepository(sessionStore)
        tasteSignals = TasteSignalReporter(this, repository)
        serviceScope.launch(Dispatchers.IO) { tasteSignals.flush() }
        playbackModeStore = PlaybackModeStore(this)

        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            BaskovAuthenticatedDataSourceFactory(),
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { exoPlayer ->
                exoPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                exoPlayer.setHandleAudioBecomingNoisy(true)
                playbackModeStore.applyTo(exoPlayer)
                exoPlayer.addListener(persistenceListener)
            }

        val sessionActivity = PendingIntent.getActivity(
            this,
            1001,
            Intent(this, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(PlaybackSessionCallback())
            .setSessionActivity(sessionActivity)
            .setMediaButtonPreferences(listOf(favoriteButton(false)))
            .build()
        refreshFavoriteState()

        PlaybackArtworkBridge.listener = { streamUrl, artworkUrl ->
            serviceScope.launch(Dispatchers.Main.immediate) {
                applyArtwork(player, streamUrl, artworkUrl)
            }
        }
        snapshotJob = serviceScope.launch {
            while (isActive) {
                delay(SNAPSHOT_INTERVAL_MILLIS)
                persistCurrentState()
            }
        }
        authMaintenanceJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(AUTH_CHECK_INTERVAL_MILLIS)
                if (!hasPlaybackQueue || !hasRemotePlaybackQueue) continue
                runCatching {
                    authSessionManager.requireFreshSession(MIN_PLAYBACK_ACCESS_VALIDITY_MILLIS)
                }.onSuccess { tokens ->
                    PlaybackAuthBridge.update(tokens.accessToken)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistCurrentState()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistCurrentState()
        snapshotJob?.cancel()
        authMaintenanceJob?.cancel()
        activeTasteTrack = null
        mediaSession?.run {
            player.removeListener(persistenceListener)
            player.release()
            release()
        }
        mediaSession = null
        hasPlaybackQueue = false
        hasRemotePlaybackQueue = false
        PlaybackArtworkBridge.listener = null
        PlaybackAuthBridge.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun persistCurrentState() {
        val player = mediaSession?.player ?: return
        sampleTasteProgress()
        hasPlaybackQueue = player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED
        hasRemotePlaybackQueue = hasPlaybackQueue && (0 until player.mediaItemCount).any { index ->
            player.getMediaItemAt(index).localConfiguration?.uri?.toString()
                ?.let(PlaybackStreamUrl::isRemoteHttp) == true
        }
        stateStore.save(player)
    }


    private fun applyArtwork(player: Player, streamUrl: String, artworkUrl: String) {
        val streamKey = PlaybackStreamUrl.baseKey(streamUrl)
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val itemUrl = item.localConfiguration?.uri?.toString() ?: continue
            if (PlaybackStreamUrl.baseKey(itemUrl) != streamKey) continue
            if (item.mediaMetadata.artworkUri?.toString() == artworkUrl) continue

            val metadata = item.mediaMetadata.buildUpon()
                .setArtworkUri(Uri.parse(artworkUrl))
                .build()
            player.replaceMediaItem(
                index,
                item.buildUpon().setMediaMetadata(metadata).build(),
            )
        }
    }

    private fun startTasteTrack(mediaItem: MediaItem?) {
        val next = mediaItem?.toActiveTasteTrack() ?: return
        val existing = activeTasteTrack
        if (existing != null && existing.stableKey == next.stableKey && existing.source == next.source) return
        activeTasteTrack = next
        queueTasteSignal(next.toSignal(TasteSignalType.PLAY, 0.0))
    }

    private fun sampleTasteProgress() {
        val active = activeTasteTrack ?: return
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem ?: return
        if (item.mediaId != active.stableKey) return
        active.maxPositionMillis = maxOf(active.maxPositionMillis, player.currentPosition.coerceAtLeast(0L))
        val uri = item.localConfiguration?.uri?.toString()
        val serverDuration = if (uri != null && PlaybackStreamUrl.isRemoteHttp(uri)) {
            PlaybackStreamMetrics.durationMillis(uri)
        } else {
            0L
        }
        val nativeDuration = player.duration.takeIf { it > 0L } ?: 0L
        active.durationMillis = maxOf(active.durationMillis, serverDuration, nativeDuration)
    }

    private fun finishTasteTrack(type: TasteSignalType) {
        val active = activeTasteTrack ?: return
        activeTasteTrack = null
        val ratio = when (type) {
            TasteSignalType.COMPLETED,
            TasteSignalType.REPLAY -> 1.0
            else -> active.completionRatio()
        }
        queueTasteSignal(active.toSignal(type, ratio))
    }

    private fun classifyEarlyOutcome(active: ActiveTasteTrack): TasteSignalType =
        if (active.maxPositionMillis <= QUICK_SKIP_MAX_MILLIS ||
            active.completionRatio() <= QUICK_SKIP_MAX_RATIO
        ) {
            TasteSignalType.QUICK_SKIP
        } else {
            TasteSignalType.STOP_EARLY
        }

    private fun queueTasteSignal(event: TasteSignal) {
        serviceScope.launch(Dispatchers.IO) {
            val guildId = repository.selectedGuildId() ?: return@launch
            tasteSignals.enqueue(guildId, event)
            tasteSignals.flush()
        }
    }

    private fun refreshFavoriteState() {
        val session = mediaSession ?: return
        val item = session.player.currentMediaItem
        val stableKey = item?.mediaId?.takeIf { it.isNotBlank() && !it.startsWith("local:") }
        if (stableKey == null) {
            FavoriteStateBridge.publish(null, supported = false, favorite = false)
            session.setMediaButtonPreferences(emptyList())
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            val guildId = repository.selectedGuildId()
            val favorite = guildId?.let { runCatching { repository.favoriteStatus(it, stableKey) }.getOrDefault(false) } ?: false
            launch(Dispatchers.Main.immediate) {
                if (mediaSession?.player?.currentMediaItem?.mediaId == stableKey) {
                    FavoriteStateBridge.publish(stableKey, supported = true, favorite = favorite)
                    mediaSession?.setMediaButtonPreferences(listOf(favoriteButton(favorite)))
                }
            }
        }
    }

    private fun favoriteButton(favorite: Boolean): CommandButton = CommandButton.Builder(
        if (favorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
    )
        .setDisplayName(if (favorite) "Убрать из избранного" else "В избранное")
        .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
        .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
        .build()

    private inner class PlaybackSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                    .build(),
            )
            .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != ACTION_TOGGLE_FAVORITE) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            val item = session.player.currentMediaItem
                ?: return com.google.common.util.concurrent.Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_BAD_VALUE),
                )
            val stableKey = item.mediaId.takeIf { it.isNotBlank() && !it.startsWith("local:") }
                ?: return com.google.common.util.concurrent.Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED),
                )
            return serviceScope.future(Dispatchers.IO) {
                val guildId = repository.selectedGuildId()
                    ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)
                val currentlyFavorite = repository.favoriteStatus(guildId, stableKey)
                val tasteTrack = TrackPreview(
                    stableKey = stableKey,
                    title = item.mediaMetadata.title?.toString(),
                    artist = item.mediaMetadata.artist?.toString(),
                )
                if (currentlyFavorite) {
                    repository.removeFavoriteByStableKey(guildId, stableKey)
                } else {
                    repository.addFavorite(guildId, tasteTrack)
                }
                tasteSignals.enqueue(
                    guildId,
                    TasteSignal(
                        type = if (currentlyFavorite) TasteSignalType.FAVORITE_REMOVE else TasteSignalType.FAVORITE_ADD,
                        source = TasteSignalSource.REMOTE,
                        stableKey = stableKey,
                        artist = tasteTrack.artist.orEmpty().ifBlank { "Неизвестно" },
                        title = tasteTrack.title.orEmpty().ifBlank { "Неизвестно" },
                        completionRatio = 1.0,
                    ),
                )
                serviceScope.launch { tasteSignals.flush() }
                serviceScope.launch(Dispatchers.Main.immediate) {
                    FavoriteStateBridge.publish(stableKey, supported = true, favorite = !currentlyFavorite)
                    mediaSession?.setMediaButtonPreferences(listOf(favoriteButton(!currentlyFavorite)))
                }
                SessionResult(SessionResult.RESULT_SUCCESS)
            }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val snapshot = stateStore.load() ?: return super.onPlaybackResumption(mediaSession, controller)
            return serviceScope.future(Dispatchers.IO) {
                if (snapshot.containsRemoteItems) {
                    authSessionManager.authenticated { client, token -> client.me(token) }
                    val tokens = authSessionManager.requireFreshSession(MIN_PLAYBACK_ACCESS_VALIDITY_MILLIS)
                    PlaybackAuthBridge.update(tokens.accessToken)
                } else {
                    PlaybackAuthBridge.clear()
                }
                MediaSession.MediaItemsWithStartPosition(
                    snapshot.toMediaItems(),
                    snapshot.currentIndex,
                    if (snapshot.currentIsRemote) 0L else snapshot.positionMillis,
                )
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_FAVORITE = "ru.flawden.baskovmusic.action.TOGGLE_FAVORITE"
        private const val SNAPSHOT_INTERVAL_MILLIS = 5_000L
        const val AUTH_CHECK_INTERVAL_MILLIS = 60_000L
        const val MIN_PLAYBACK_ACCESS_VALIDITY_MILLIS = 5 * 60_000L
        private const val QUICK_SKIP_MAX_MILLIS = 30_000L
        private const val QUICK_SKIP_MAX_RATIO = 0.25
    }
}

/**
 * Keeps the short-lived access token in process memory only. Durable access/refresh credentials
 * remain encrypted by SessionStore/Android Keystore and can be rehydrated after process death.
 */
private data class ActiveTasteTrack(
    val stableKey: String,
    val source: TasteSignalSource,
    val artist: String,
    val title: String,
    var maxPositionMillis: Long = 0L,
    var durationMillis: Long = 0L,
) {
    fun completionRatio(): Double =
        if (durationMillis <= 0L) 0.0
        else (maxPositionMillis / durationMillis.toDouble()).coerceIn(0.0, 1.0)

    fun toSignal(type: TasteSignalType, ratio: Double): TasteSignal = TasteSignal(
        type = type,
        source = source,
        stableKey = stableKey.takeIf { source == TasteSignalSource.LOCAL || !it.startsWith("baskov-") },
        artist = artist,
        title = title,
        completionRatio = ratio.coerceIn(0.0, 1.0),
    )
}

private fun MediaItem.toActiveTasteTrack(): ActiveTasteTrack? {
    val uri = localConfiguration?.uri?.toString()
    val source = if (uri != null && PlaybackStreamUrl.isRemoteHttp(uri)) {
        TasteSignalSource.REMOTE
    } else {
        TasteSignalSource.LOCAL
    }
    val key = mediaId.takeIf(String::isNotBlank) ?: return null
    if (source == TasteSignalSource.LOCAL && !key.startsWith("local:")) return null
    return ActiveTasteTrack(
        stableKey = key,
        source = source,
        artist = mediaMetadata.artist?.toString().orEmpty().ifBlank { "Неизвестно" },
        title = mediaMetadata.title?.toString().orEmpty().ifBlank { "Неизвестно" },
    )
}

internal data class FavoritePlaybackState(
    val stableKey: String? = null,
    val supported: Boolean = false,
    val favorite: Boolean = false,
)

internal object FavoriteStateBridge {
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(FavoritePlaybackState())
    val state: kotlinx.coroutines.flow.StateFlow<FavoritePlaybackState> = mutableState

    fun publish(stableKey: String?, supported: Boolean, favorite: Boolean) {
        mutableState.value = FavoritePlaybackState(stableKey, supported, favorite)
    }
}

internal object PlaybackArtworkBridge {
    @Volatile
    var listener: ((streamUrl: String, artworkUrl: String) -> Unit)? = null

    fun publish(streamUrl: String, artworkUrl: String) {
        listener?.invoke(streamUrl, artworkUrl)
    }
}

internal object PlaybackAuthBridge {
    @Volatile
    private var bearerToken: String? = null

    fun update(token: String) {
        bearerToken = token
    }

    fun clear() {
        bearerToken = null
    }

    fun authorizationHeader(): String? = bearerToken?.let { "Bearer $it" }
}

@OptIn(UnstableApi::class)
private class BaskovAuthenticatedDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource = BaskovAuthenticatedDataSource()
}

@OptIn(UnstableApi::class)
private class BaskovAuthenticatedDataSource : DataSource {
    private val source = DefaultHttpDataSource.Factory()
        .setUserAgent("BaskovAndroid/0.15.0")
        .createDataSource()
        .also { http ->
            http.setRequestProperty("Accept", "audio/ogg")
            PlaybackAuthBridge.authorizationHeader()?.let { value ->
                http.setRequestProperty("Authorization", value)
            }
        }

    override fun addTransferListener(transferListener: TransferListener) {
        source.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val openedLength = source.open(dataSpec)
        source.responseHeaders["X-Baskov-Playback-Duration-Millis"]
            ?.firstOrNull()
            ?.toLongOrNull()
            ?.let { duration ->
                PlaybackStreamMetrics.recordDuration(dataSpec.uri.toString(), duration)
            }
        source.responseHeaders["X-Baskov-Playback-Artwork-Url"]
            ?.firstOrNull()
            ?.let(PlaybackArtworkUrl::normalize)
            ?.let { artworkUrl ->
                PlaybackArtworkBridge.publish(dataSpec.uri.toString(), artworkUrl)
            }
        return openedLength
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        source.read(buffer, offset, length)

    override fun getUri(): Uri? = source.uri

    override fun getResponseHeaders(): Map<String, List<String>> = source.responseHeaders

    override fun close() {
        source.close()
    }
}
