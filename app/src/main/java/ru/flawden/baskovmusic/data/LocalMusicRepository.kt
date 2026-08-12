package ru.flawden.baskovmusic.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.flawden.baskovmusic.model.LocalTrack

/** Reads the device audio library through MediaStore without copying audio into the app. */
class LocalMusicRepository(context: Context) {
    private val appContext = context.applicationContext

    fun requiredPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        requiredPermission(),
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun tracks(): List<LocalTrack> = withContext(Dispatchers.IO) {
        check(hasPermission()) { "Нужен доступ к музыке на устройстве." }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val result = ArrayList<LocalTrack>()

        appContext.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val durationMillis = cursor.getLong(durationColumn).coerceAtLeast(0L)
                val displayName = cursor.getString(displayNameColumn)?.trim().orEmpty()
                val title = cleanMetadata(cursor.getString(titleColumn))
                    ?: displayName.substringBeforeLast('.', displayName).ifBlank { "Unknown track" }
                val artist = cleanMetadata(cursor.getString(artistColumn)) ?: "Unknown artist"
                val album = cleanMetadata(cursor.getString(albumColumn))
                val albumId = cursor.getLong(albumIdColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                ).toString()
                val artworkUri = albumId.takeIf { it > 0L }
                    ?.let { ContentUris.withAppendedId(ALBUM_ART_URI, it).toString() }

                result += LocalTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMillis = durationMillis,
                    contentUri = contentUri,
                    artworkUri = artworkUri,
                )
            }
        }
        result
    }

    private fun cleanMetadata(value: String?): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }

    private companion object {
        val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
