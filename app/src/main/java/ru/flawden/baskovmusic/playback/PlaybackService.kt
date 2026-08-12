package ru.flawden.baskovmusic.playback

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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var snapshotJob: Job? = null
    private var authMaintenanceJob: Job? = null

    @Volatile
    private var hasPlaybackQueue = false

    private val persistenceListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            persistCurrentState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            persistCurrentState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            persistCurrentState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            persistCurrentState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            persistCurrentState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = PlaybackStateStore(this)
        authSessionManager = AuthSessionManager(SessionStore(this))

        val dataSourceFactory = BaskovAuthenticatedDataSourceFactory()
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
                exoPlayer.addListener(persistenceListener)
            }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(PlaybackSessionCallback())
            .build()

        snapshotJob = serviceScope.launch {
            while (isActive) {
                delay(SNAPSHOT_INTERVAL_MILLIS)
                persistCurrentState()
            }
        }
        authMaintenanceJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(AUTH_CHECK_INTERVAL_MILLIS)
                if (!hasPlaybackQueue) continue
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
        PlaybackAuthBridge.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun persistCurrentState() {
        val player = mediaSession?.player ?: return
        hasPlaybackQueue = player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED
        stateStore.save(player)
    }

    private inner class PlaybackSessionCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val snapshot = stateStore.load() ?: return super.onPlaybackResumption(mediaSession, controller)
            return serviceScope.future(Dispatchers.IO) {
                authSessionManager.authenticated { client, token -> client.me(token) }
                val tokens = authSessionManager.requireFreshSession(MIN_PLAYBACK_ACCESS_VALIDITY_MILLIS)
                PlaybackAuthBridge.update(tokens.accessToken)
                MediaSession.MediaItemsWithStartPosition(
                    snapshot.toMediaItems(),
                    snapshot.currentIndex,
                    0L, // Current URL already carries Product API v1.33 startMillis.
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
        .setUserAgent("BaskovAndroid/0.7.0")
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
