package com.shagiev.yaz

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class KeyboardService : InputMethodService() {

    private var keyboardView: KeyboardView? = null
    private lateinit var prefs: SharedPreferences

    private var language = Language.ENGLISH
    private var themeMode = ThemeMode.AUTO
    private var keyHeight = 50
    private var vibration = true
    private var sound = true

    private val noAutoCaps = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_URI
    )

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        loadPrefs()
        applyToView()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        loadPrefs()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        loadPrefs()
        val view = KeyboardView(this, this)
        view.applySettings(themeMode, keyHeight, vibration, sound)
        view.setLanguage(language)
        keyboardView = view
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView?.setLanguage(language)
        keyboardView?.resetForInput()
        keyboardView?.setShift(shouldAutoShift(info))
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    fun onBackspace() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        if (!ic.deleteSurroundingTextInCodePoints(1, 0)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    fun onEnter() {
        val info = currentInputEditorInfo
        val noEnterAction = info != null &&
            (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val action = info?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_NONE

        if (!noEnterAction &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    fun onLanguageChanged(newLanguage: Language) {
        language = newLanguage
        prefs.edit().putString(Prefs.LANGUAGE, newLanguage.name).apply()
    }

    private fun shouldAutoShift(info: EditorInfo?): Boolean {
        val editorInfo = info ?: return false
        val inputType = editorInfo.inputType
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        if ((inputType and InputType.TYPE_MASK_VARIATION) in noAutoCaps) return false

        val capsMask = TextUtils.CAP_MODE_CHARACTERS or
            TextUtils.CAP_MODE_WORDS or
            TextUtils.CAP_MODE_SENTENCES
        if ((editorInfo.initialCapsMode and capsMask) != 0) return true

        val capsFlags = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        return (inputType and capsFlags) != 0
    }

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        runCatching {
            enumValueOf<T>(prefs.getString(key, default.name) ?: default.name)
        }.getOrDefault(default)

    private fun loadPrefs() {
        language = enumPref(Prefs.LANGUAGE, Language.ENGLISH)
        if (language == Language.RUSSIAN) {
            language = Language.TATAR
            prefs.edit().putString(Prefs.LANGUAGE, Language.TATAR.name).apply()
        }
        themeMode = enumPref(Prefs.THEME, ThemeMode.AUTO)
        keyHeight = prefs.getInt(Prefs.KEY_HEIGHT, 50).coerceIn(40, 70)
        vibration = prefs.getBoolean(Prefs.VIBRATION, true)
        sound = prefs.getBoolean(Prefs.SOUND, true)
    }

    private fun applyToView() {
        keyboardView?.applySettings(themeMode, keyHeight, vibration, sound)
        keyboardView?.setLanguage(language)
    }
}
