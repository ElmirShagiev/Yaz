package com.shagiev.yaz

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

enum class ThemeMode {
    AUTO, LIGHT, DARK
}

data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val keyPressed: Int,
    val special: Int,
    val specialPressed: Int,
    val text: Int,
    val hint: Int,
    val accent: Int,
    val popup: Int,
    val popupText: Int
)

object Themes {

    fun resolve(context: Context, mode: ThemeMode): KeyboardTheme {
        val night = when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO ->
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        return if (night) DARK else LIGHT
    }

    private val DARK = KeyboardTheme(
        background = Color.parseColor("#1C1C1E"),
        key = Color.parseColor("#3A3A3C"),
        keyPressed = Color.parseColor("#5A5A5E"),
        special = Color.parseColor("#2C2C2E"),
        specialPressed = Color.parseColor("#48484A"),
        text = Color.WHITE,
        hint = Color.parseColor("#8E8E93"),
        accent = Color.parseColor("#0A84FF"),
        popup = Color.parseColor("#2C2C2E"),
        popupText = Color.WHITE
    )

    private val LIGHT = KeyboardTheme(
        background = Color.parseColor("#D6D9DE"),
        key = Color.WHITE,
        keyPressed = Color.parseColor("#C7CBD1"),
        special = Color.parseColor("#E3E5E8"),
        specialPressed = Color.parseColor("#CDD1D6"),
        text = Color.parseColor("#111111"),
        hint = Color.parseColor("#6B7280"),
        accent = Color.parseColor("#1A73E8"),
        popup = Color.WHITE,
        popupText = Color.parseColor("#111111")
    )
}
