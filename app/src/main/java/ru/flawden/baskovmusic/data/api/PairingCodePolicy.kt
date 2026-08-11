package ru.flawden.baskovmusic.data.api

object PairingCodePolicy {
    private val pattern = Regex("^[A-Z0-9]{8}$")

    fun normalize(raw: String): String {
        val code = raw.trim().uppercase()
        require(pattern.matches(code)) { "Pairing code должен состоять из 8 букв/цифр." }
        return code
    }
}
