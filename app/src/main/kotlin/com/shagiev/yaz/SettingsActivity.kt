package com.shagiev.yaz

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(32))
        }
        scroll.addView(root)
        setContentView(scroll)

        addText(root, "Yaz", 24f, 0, 4)
        addText(root, "Простая офлайн-клавиатура: English, Татарча", 14f, 2, 12)

        addText(root, "Проверка ввода", 16f, 16, 6)
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

        addText(root, "Тема", 16f, 16, 6)
        val themeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        val themeModes = listOf(
            ThemeMode.AUTO to "Авто (как в системе)",
            ThemeMode.LIGHT to "Светлая",
            ThemeMode.DARK to "Тёмная"
        )
        val currentTheme = runCatching {
            ThemeMode.valueOf(
                prefs.getString(Prefs.THEME, ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name
            )
        }.getOrDefault(ThemeMode.AUTO)

        themeModes.forEach { (mode, label) ->
            val button = RadioButton(this).apply {
                text = label
                id = View.generateViewId()
                isChecked = mode == currentTheme
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        prefs.edit().putString(Prefs.THEME, mode.name).apply()
                    }
                }
            }
            themeGroup.addView(button)
        }
        root.addView(themeGroup)

        addText(root, "Высота клавиш", 16f, 16, 6)
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

        addText(root, "Обратная связь", 16f, 16, 6)
        root.addView(checkBox("Вибрация при нажатии", Prefs.VIBRATION, true))
        root.addView(checkBox("Звук при нажатии", Prefs.SOUND, true))

        addText(root, "Клавиатура", 16f, 16, 6)
        root.addView(makeButton("Выбрать клавиатуру") {
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.showInputMethodPicker()
        })
        root.addView(makeButton("Настройки клавиатуры Android") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        addText(
            root,
            "Если клавиатура ещё не включена: откройте настройки Android → " +
                "Система → Языки и ввод → Виртуальная клавиатура → " +
                "Управление клавиатурами и включите Yaz.",
            14f, 2, 12
        )
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

    private fun checkBox(text: String, key: String, default: Boolean): CheckBox =
        CheckBox(this).apply {
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
