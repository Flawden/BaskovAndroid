package ru.flawden.baskovmusic.data.api

import java.net.URI

object ServerUrlPolicy {
    fun normalize(raw: String, allowCleartext: Boolean): String {
        val trimmed = raw.trim().trimEnd('/')
        require(trimmed.isNotBlank()) { "Укажи адрес Baskov API." }
        require(!trimmed.contains(' ')) { "Адрес не должен содержать пробелы." }

        val uri = runCatching { URI("$trimmed/") }.getOrElse {
            throw IllegalArgumentException("Некорректный адрес Baskov API.")
        }
        val secure = uri.scheme.equals("https", ignoreCase = true)
        val clear = uri.scheme.equals("http", ignoreCase = true)
        require(secure || (allowCleartext && clear)) {
            if (allowCleartext) "Нужен http:// или https:// адрес." else "Release-сборка принимает только HTTPS."
        }
        require(!uri.host.isNullOrBlank()) { "У адреса Baskov API должен быть host." }
        require(uri.userInfo == null) { "Не помещай логин/пароль в URL." }
        require(uri.query == null && uri.fragment == null) { "Baskov API URL не должен содержать query или fragment." }

        return "$trimmed/"
    }
}
