package com.shagiev.yaz

enum class KeyType {
    CHAR, SHIFT, BACKSPACE, ENTER, SPACE, LANGUAGE, SYMBOLS, ABC, EMPTY
}

data class Key(
    val label: String,
    val output: String = "",
    val type: KeyType = KeyType.CHAR,
    val widthDp: Float? = null,
    val popup: List<String> = emptyList(),
    val hint: String? = null
)
