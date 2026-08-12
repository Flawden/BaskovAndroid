package ru.flawden.baskovmusic.data

import android.content.Context

class LocalMusicSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "baskov_local_music",
        Context.MODE_PRIVATE,
    )

    fun selection(): LocalFolderSelection = LocalFolderSelection(
        enabled = preferences.getBoolean(FOLDER_FILTER_ENABLED, false),
        selectedFolders = preferences.getStringSet(SELECTED_FOLDERS, emptySet()).orEmpty().toSet(),
    )

    fun saveSelection(enabled: Boolean, selectedFolders: Set<String>) {
        preferences.edit()
            .putBoolean(FOLDER_FILTER_ENABLED, enabled)
            .putStringSet(SELECTED_FOLDERS, selectedFolders.toSet())
            .apply()
    }

    fun useAllFolders() = saveSelection(enabled = false, selectedFolders = emptySet())

    private companion object {
        const val FOLDER_FILTER_ENABLED = "folder_filter_enabled"
        const val SELECTED_FOLDERS = "selected_folders"
    }
}

data class LocalFolderSelection(
    val enabled: Boolean,
    val selectedFolders: Set<String>,
)
