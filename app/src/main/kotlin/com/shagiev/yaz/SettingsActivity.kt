package com.shagiev.yaz

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val themeMode = readTheme()
        setTheme(
            if (isDarkTheme(themeMode)) {
                android.R.style.Theme_Material_NoActionBar
            } else {
                android.R.style.Theme_Material_Light_NoActionBar
            }
        )
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }
        scroll.addView(root)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                top = bars.top
                bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(0, top, 0, bottom)
            insets
        }
        setContentView(scroll)

        addHeader(root)

        addSection(root, "Клавиатура")
        root.addView(makeButton("Выбрать клавиатуру") {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        })
        root.addView(makeButton("Настройки клавиатуры Android") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        addText(
            root,
            "Если Yaz ещё не включена: настройки Android → Языки и ввод → " +
                "Виртуальная клавиатура → Управление клавиатурами.",
            14f, 2, 2
        )

        addSection(root, "Оформление")
        val themeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        val themeModes = listOf(
            ThemeMode.AUTO to "Авто (как в системе)",
            ThemeMode.LIGHT to "Светлая",
            ThemeMode.DARK to "Тёмная"
        )
        themeModes.forEach { (mode, label) ->
            val button = RadioButton(this).apply {
                text = label
                id = View.generateViewId()
                isChecked = mode == themeMode
                setOnCheckedChangeListener { _, checked ->
                    if (checked && mode != readTheme()) {
                        prefs.edit().putString(Prefs.THEME, mode.name).apply()
                        recreate()
                    }
                }
            }
            themeGroup.addView(button)
        }
        root.addView(themeGroup)

        addText(root, "Высота клавиш", 16f, 12, 4)
        val heightLabel = TextView(this).apply {
            text = "${prefs.getInt(Prefs.KEY_HEIGHT, 50)} dp"
            textSize = 15f
        }
        root.addView(heightLabel)

        val heightBar = SeekBar(this).apply {
            max = 30
            progress = prefs.getInt(Prefs.KEY_HEIGHT, 50).coerceIn(40, 70) - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = 40 + progress
                    heightLabel.text = "$value dp"
                    prefs.edit().putInt(Prefs.KEY_HEIGHT, value).apply()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        root.addView(heightBar)

        addSection(root, "Обратная связь")
        root.addView(switchRow("Вибрация при нажатии", Prefs.VIBRATION, true))
        root.addView(switchRow("Звук при нажатии", Prefs.SOUND, true))

        addSection(root, "Проверка ввода")
        val testField = EditText(this).apply {
            hint = "Проверьте клавиатуру здесь"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        root.addView(testField)
        root.addView(makeButton("Фокус на поле проверки") {
            testField.requestFocus()
            testField.setSelection(testField.text.length)
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(testField, InputMethodManager.SHOW_IMPLICIT)
        })

        addAboutSection(root)
    }

    private fun addHeader(root: LinearLayout) {
        root.addView(TextView(this).apply {
            text = "Yaz"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Простая офлайн-клавиатура: English, Татарча"
            textSize = 14f
            setPadding(0, dp(2), 0, dp(4))
        })
    }

    private fun addAboutSection(root: LinearLayout) {
        addSection(root, "О приложении")
        val info = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        addText(
            root,
            "Версия ${info.versionName} ($versionCode). Свободная клавиатура для Android.",
            14f, 2, 8
        )
        root.addView(makeButton("GitHub") {
            openUrl("https://github.com/ElmirShagiev/Yaz")
        })
        root.addView(makeButton("Релизы") {
            openUrl("https://github.com/ElmirShagiev/Yaz/releases")
        })
        root.addView(makeButton("Лицензия MIT") {
            openUrl("https://github.com/ElmirShagiev/Yaz/blob/main/LICENSE")
        })
    }

    private fun addSection(parent: LinearLayout, title: String) {
        val accent = TypedValue()
        theme.resolveAttribute(android.R.attr.colorAccent, accent, true)
        parent.addView(TextView(this).apply {
            text = title
            textSize = 13f
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accent.data)
            setPadding(0, dp(24), 0, dp(6))
        })
    }

    private fun addText(
        parent: LinearLayout,
        text: String,
        size: Float,
        top: Int,
        bottom: Int
    ) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = size
            setPadding(0, dp(top), 0, dp(bottom))
        })
    }

    private fun switchRow(text: String, key: String, default: Boolean): Switch =
        Switch(this).apply {
            this.text = text
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
            }
        }

    private fun makeButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun readTheme(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString(Prefs.THEME, ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name)
    }.getOrDefault(ThemeMode.AUTO)

    private fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO ->
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
