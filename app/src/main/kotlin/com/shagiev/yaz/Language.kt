package com.shagiev.yaz

enum class Language(val code: String, val displayName: String) {
    ENGLISH("EN", "English"),
    RUSSIAN("RU", "Русский"),
    TATAR("TT", "Татарча");

    fun next(): Language = when (this) {
        ENGLISH -> TATAR
        RUSSIAN -> TATAR
        TATAR -> ENGLISH
    }
}
