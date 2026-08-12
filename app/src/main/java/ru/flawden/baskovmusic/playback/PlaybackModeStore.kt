package ru.flawden.baskovmusic.playback

import android.content.Context
import androidx.media3.common.Player

internal class PlaybackModeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "baskov_playback_modes",
        Context.MODE_PRIVATE,
    )

    fun load(): PlaybackModes = PlaybackModes(
        shuffleEnabled = preferences.getBoolean(SHUFFLE_ENABLED, false),
        repeatMode = preferences.getInt(REPEAT_MODE, Player.REPEAT_MODE_OFF).takeIf {
            it == Player.REPEAT_MODE_OFF || it == Player.REPEAT_MODE_ONE || it == Player.REPEAT_MODE_ALL
        } ?: Player.REPEAT_MODE_OFF,
    )

    fun saveShuffle(enabled: Boolean) {
        preferences.edit().putBoolean(SHUFFLE_ENABLED, enabled).apply()
    }

    fun saveRepeatMode(@Player.RepeatMode repeatMode: Int) {
        preferences.edit().putInt(REPEAT_MODE, repeatMode).apply()
    }

    fun applyTo(player: Player) {
        val modes = load()
        player.shuffleModeEnabled = modes.shuffleEnabled
        player.repeatMode = modes.repeatMode
    }

    private companion object {
        const val SHUFFLE_ENABLED = "shuffle_enabled"
        const val REPEAT_MODE = "repeat_mode"
    }
}

internal data class PlaybackModes(
    val shuffleEnabled: Boolean,
    @Player.RepeatMode val repeatMode: Int,
)
