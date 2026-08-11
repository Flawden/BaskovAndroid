package ru.flawden.baskovmusic.data.api

class ApiException(
    val statusCode: Int,
    val apiCode: String?,
    override val message: String,
) : Exception(message)
