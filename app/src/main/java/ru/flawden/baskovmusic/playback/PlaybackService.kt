package ru.flawden.baskovmusic.playback

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * System-owned Baskov playback surface for v0.4.
 *
 * The ExoPlayer and MediaSession live in this service so playback can continue while the UI is
 * backgrounded or removed from recents. Media3 owns the foreground notification lifecycle and
 * exposes transport controls to the lock screen, headset/Bluetooth buttons and system media UI.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

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
            }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        PlaybackAuthBridge.clear()
        super.onDestroy()
    }
}

/**
 * Keeps the short-lived access token in process memory only. The durable device session remains
 * encrypted by SessionStore/Android Keystore; this bridge is populated only after the repository
 * has validated/rotated the access token for a new playback queue.
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
    override fun createDataSource(): DataSource {
        val source = DefaultHttpDataSource.Factory()
            .setUserAgent("BaskovAndroid/0.4.0")
            .createDataSource()

        source.setRequestProperty("Accept", "audio/ogg")
        PlaybackAuthBridge.authorizationHeader()?.let { value ->
            source.setRequestProperty("Authorization", value)
        }
        return source
    }
}
