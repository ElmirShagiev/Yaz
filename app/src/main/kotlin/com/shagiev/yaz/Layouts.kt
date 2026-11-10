package com.shagiev.yaz

enum class Page {
    LETTERS, SYMBOLS
}

object Layouts {

    private const val WIDE_KEY_WIDTH_DP = 30.9f
    private const val TATAR_KEY_WIDTH_DP = 27.667f
    private const val TATAR_BOTTOM_WIDTH_DP = 26f
    private const val NARROW_SPECIAL_WIDTH_DP = 35f
    private const val WIDE_SPECIAL_WIDTH_DP = 48.667f

    private val russianPopups = mapOf(
        "е" to listOf("ё"),
        "ь" to listOf("ъ")
    )

    private val tatarPopups = mapOf(
        "а" to listOf("ә"),
        "о" to listOf("ө"),
        "у" to listOf("ү"),
        "ж" to listOf("җ"),
        "н" to listOf("ң"),
        "х" to listOf("һ"),
        "е" to listOf("ё"),
        "ь" to listOf("ъ")
    )

    private val symbolPopups = mapOf(
        "-" to listOf("—", "_", "–", "·"),
        "\"" to listOf("“", "”", "„", "«", "»"),
        "'" to listOf("‘", "’", "‚", "‛"),
        "(" to listOf("[", "{", "<"),
        ")" to listOf("]", "}", ">"),
        "₽" to listOf("\$", "€", "£", "¥"),
        "#" to listOf("№", "%"),
        "%" to listOf("‰", "‱"),
        "+" to listOf("±", "×", "÷"),
        "=" to listOf("≠", "≈", "≡", "≤", "≥"),
        "*" to listOf("•", "·", "×"),
        "!" to listOf("¡"),
        "?" to listOf("¿"),
        "/" to listOf("\\", "⁄"),
        "\\" to listOf("|", "¦"),
        "&" to listOf("§", "¶")
    )

    fun rows(
        language: Language,
        page: Page,
        shift: Boolean,
        caps: Boolean
    ): List<List<Key>> {
        return when (page) {
            Page.LETTERS -> letterRows(language, shift, caps)
            Page.SYMBOLS -> symbolRows(language)
        }
    }

    private fun letterRows(
        language: Language,
        shift: Boolean,
        caps: Boolean
    ): List<List<Key>> {
        val upper = shift || caps
        val popups = when (language) {
            Language.ENGLISH -> emptyMap()
            Language.RUSSIAN -> russianPopups
            Language.TATAR -> tatarPopups
        }

        fun letter(c: String, extra: List<String> = emptyList(), widthDp: Float): Key {
            val label = if (upper) c.uppercase() else c
            return Key(
                label = label,
                output = c,
                widthDp = widthDp,
                popup = extra.filter { it != c }.distinct()
            )
        }

        // Цифры 1–0 на верхнем ряду пока отключены — код оставлен для будущего включения.
        // fun digitHint(index: Int): String? = when (index) {
        //     in 0..8 -> (index + 1).toString()
        //     9 -> "0"
        //     else -> null
        // }

        val rows = when (language) {
            Language.ENGLISH -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            Language.RUSSIAN, Language.TATAR ->
                listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю")
        }.mapIndexed { index, row ->
            val letterWidth = when {
                language == Language.ENGLISH -> WIDE_KEY_WIDTH_DP
                index == 2 -> TATAR_BOTTOM_WIDTH_DP
                else -> TATAR_KEY_WIDTH_DP
            }
            val keys = row.map { c ->
                letter(c.toString(), popups[c.toString()].orEmpty(), letterWidth)
            }
            if (index == 2) {
                val specialWidth =
                    if (language == Language.ENGLISH) WIDE_SPECIAL_WIDTH_DP
                    else NARROW_SPECIAL_WIDTH_DP
                listOf(shiftKey(caps, specialWidth)) + keys +
                    listOf(backspaceKey(specialWidth))
            } else {
                keys
            }
        }

        return rows + listOf(bottomRow(language, symbols = true))
    }

    private fun symbolRows(language: Language): List<List<Key>> {
        fun symbol(c: String): Key = Key(
            label = c,
            output = c,
            widthDp = WIDE_KEY_WIDTH_DP,
            popup = symbolPopups[c].orEmpty().filter { it != c }.distinct()
        )

        return listOf(
            "1234567890".map { symbol(it.toString()) },
            listOf("@", "#", "₽", "_", "&", "-", "+", "(", ")", "/").map { symbol(it) },
            listOf(
                Key(
                    label = "",
                    type = KeyType.EMPTY,
                    widthDp = WIDE_SPECIAL_WIDTH_DP
                )
            ) +
                listOf("*", "\"", "'", ":", ";", "!", "?").map { symbol(it) } +
                listOf(backspaceKey(WIDE_SPECIAL_WIDTH_DP)),
            bottomRow(language, symbols = false)
        )
    }

    private fun bottomRow(language: Language, symbols: Boolean): List<Key> {
        val pageKey = if (symbols) symbolsKey() else abcKey(language)
        return listOf(
            pageKey,
            Key(label = ",", output = ",", widthDp = WIDE_KEY_WIDTH_DP),
            languageKey(language),
            Key(label = language.displayName, type = KeyType.SPACE),
            Key(label = ".", output = ".", widthDp = WIDE_KEY_WIDTH_DP),
            enterKey()
        )
    }

    private fun shiftKey(caps: Boolean, widthDp: Float): Key =
        Key(
            label = if (caps) "⇪" else "⇧",
            type = KeyType.SHIFT,
            widthDp = widthDp
        )

    private fun backspaceKey(widthDp: Float): Key =
        Key(label = "⌫", type = KeyType.BACKSPACE, widthDp = widthDp)

    private fun enterKey(): Key =
        Key(label = "⏎", type = KeyType.ENTER, widthDp = WIDE_SPECIAL_WIDTH_DP)

    private fun languageKey(language: Language): Key =
        Key(label = language.code, type = KeyType.LANGUAGE, widthDp = WIDE_KEY_WIDTH_DP)

    private fun symbolsKey(): Key =
        Key(label = "?123", type = KeyType.SYMBOLS, widthDp = WIDE_SPECIAL_WIDTH_DP)

    private fun abcKey(language: Language): Key =
        Key(
            label = if (language == Language.ENGLISH) "ABC" else "АБВ",
            type = KeyType.ABC,
            widthDp = WIDE_SPECIAL_WIDTH_DP
        )
}
