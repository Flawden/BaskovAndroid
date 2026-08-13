package ru.flawden.baskovmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
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
import androidx.media3.session.MediaSession
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
import ru.flawden.baskovmusic.data.auth.AuthSessionManager
import ru.flawden.baskovmusic.data.auth.SessionStore

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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var snapshotJob: Job? = null
    private var authMaintenanceJob: Job? = null

    @Volatile
    private var hasPlaybackQueue = false

    @Volatile
    private var hasRemotePlaybackQueue = false

    private val persistenceListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            persistCurrentState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            normalizeRemoteRepeatOffset(reason)
            persistCurrentState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            persistCurrentState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            persistCurrentState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
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
        authSessionManager = AuthSessionManager(SessionStore(this))
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
            .build()

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

    private inner class PlaybackSessionCallback : MediaSession.Callback {
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

    private companion object {
        const val SNAPSHOT_INTERVAL_MILLIS = 5_000L
        const val AUTH_CHECK_INTERVAL_MILLIS = 60_000L
        const val MIN_PLAYBACK_ACCESS_VALIDITY_MILLIS = 5 * 60_000L
    }
}

/**
 * Keeps the short-lived access token in process memory only. Durable access/refresh credentials
 * remain encrypted by SessionStore/Android Keystore and can be rehydrated after process death.
 */
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
        .setUserAgent("BaskovAndroid/0.13.0")
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
