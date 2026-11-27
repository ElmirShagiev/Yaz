package com.shagiev.yaz

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.PopupWindow
import kotlin.math.min

class KeyboardView(
    context: Context,
    private val service: KeyboardService
) : View(context) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val handler = Handler(Looper.getMainLooper())

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = sp(23f)
    }
    private val specialTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = sp(18f)
    }
    private val spaceTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = sp(15f)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textSize = sp(11f)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private data class KeyRect(val key: Key, val rect: RectF)

    private data class TouchState(
        var keyRect: KeyRect,
        val downX: Float,
        val downY: Float,
        var moved: Boolean = false
    )

    private var rows: List<List<KeyRect>> = emptyList()
    private val activeTouches = mutableMapOf<Int, TouchState>()
    private var ownerPointerId = -1
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var popupWindow: PopupWindow? = null
    private var popupView: PopupView? = null
    private var popupLabels: List<String> = emptyList()
    private var popupLeft = 0f
    private var popupBoxWidth = 0f
    private var popupItemGap = 0f
    private var popupSidePadding = 0f
    private var popupSelected = -1
    private var popupVisible = false
    private var previewVisible = false
    private var backspaceRepeating = false

    private var language = Language.ENGLISH
    private var page = Page.LETTERS
    private var shift = false
    private var caps = false
    private var themeMode = ThemeMode.AUTO
    private var theme = Themes.resolve(context, themeMode)
    private var keyHeightDp = 50
    private var vibration = true
    private var sound = true
    private var bottomInset = 0

    private val popupRunnable = Runnable { showFloating(false) }

    private val backspaceRunnable = object : Runnable {
        override fun run() {
            if (activeTouches[ownerPointerId]?.keyRect?.key?.type != KeyType.BACKSPACE) {
                backspaceRepeating = false
                return
            }
            backspaceRepeating = true
            service.onBackspace()
            handler.postDelayed(this, 45)
        }
    }

    init {
        isClickable = true
        isFocusable = false
        setWillNotDraw(false)
        setOnApplyWindowInsetsListener { _, insets ->
            updateBottomInset(insets)
            insets
        }
        rebuildLayout()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post {
            rootWindowInsets?.let { updateBottomInset(it) }
        }
    }

    private fun updateBottomInset(insets: WindowInsets) {
        val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            maxOf(
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom
            )
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (bottom != bottomInset) {
            bottomInset = bottom
            requestLayout()
            invalidate()
        }
    }

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * scaledDensity

    fun applySettings(
        mode: ThemeMode,
        heightDp: Int,
        vibrate: Boolean,
        soundEnabled: Boolean
    ) {
        themeMode = mode
        theme = Themes.resolve(context, mode)
        keyHeightDp = heightDp
        vibration = vibrate
        sound = soundEnabled
        rebuildLayout()
    }

    fun setLanguage(newLanguage: Language) {
        if (language == newLanguage && rows.isNotEmpty()) return
        language = newLanguage
        page = Page.LETTERS
        shift = false
        caps = false
        rebuildLayout()
    }

    fun setShift(value: Boolean) {
        if (shift == value) return
        shift = value
        rebuildLayout()
    }

    fun resetForInput(startWithSymbols: Boolean = false) {
        val startPage = if (startWithSymbols) Page.SYMBOLS else Page.LETTERS
        val changed = page != startPage || shift || caps
        page = startPage
        shift = false
        caps = false
        if (changed) rebuildLayout()
    }

    private fun rebuildLayout() {
        if (popupVisible || previewVisible) dismissFloating()
        if (width > 0) {
            updateGeometry(width)
        } else {
            rows = Layouts.rows(language, page, shift, caps).map { row ->
                row.map { KeyRect(it, RectF()) }
            }
        }
        handler.post {
            requestLayout()
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rowCount = Layouts.rows(language, page, shift, caps).size
        val height = (
            dp(6f) * 2 +
                keyHeightDp * density * rowCount +
                dp(10.33f) * (rowCount - 1) +
                bottomInset +
                dp(3f)
            ).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (popupVisible || previewVisible) dismissFloating()
        updateGeometry(w)
    }

    private fun updateGeometry(viewWidth: Int) {
        val horizontalPadding = dp(4f)
        val keyGap = dp(4.77f)
        val rowGap = dp(10.33f)
        val top = dp(6f)
        val keyHeight = keyHeightDp * density
        val layout = Layouts.rows(language, page, shift, caps)
        val contentWidth = viewWidth - 2 * horizontalPadding

        rows = layout.mapIndexed { rowIndex, keys ->
            val fixedWidth = keys.fold(0f) { sum, key ->
                sum + (key.widthDp?.times(density) ?: 0f)
            }
            val gaps = keyGap * (keys.size - 1)
            val flexibleCount = keys.count { it.widthDp == null }
            val flexibleWidth = if (flexibleCount == 0) {
                0f
            } else {
                (contentWidth - fixedWidth - gaps) / flexibleCount
            }
            val rowWidth = fixedWidth + flexibleWidth * flexibleCount + gaps
            var x = horizontalPadding + maxOf(0f, (contentWidth - rowWidth) / 2f)
            val y = top + rowIndex * (keyHeight + rowGap)
            keys.map { key ->
                val keyWidth = key.widthDp?.times(density) ?: flexibleWidth
                val rect = RectF(x, y, x + keyWidth, y + keyHeight)
                x += keyWidth + keyGap
                KeyRect(key, rect)
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        for (row in rows) {
            for (keyRect in row) drawKey(canvas, keyRect)
        }
        if (popupVisible) {
            fillPaint.color = Color.argb(55, 0, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
            for (touch in activeTouches.values) {
                drawKey(canvas, touch.keyRect)
            }
        }
    }

    private fun drawKey(canvas: Canvas, keyRect: KeyRect) {
        val key = keyRect.key
        val isSpecial = key.type != KeyType.CHAR
        val activeShift = key.type == KeyType.SHIFT && (shift || caps)
        val isPressed = activeTouches.values.any {
            it.keyRect.rect.centerX() == keyRect.rect.centerX() &&
                it.keyRect.rect.centerY() == keyRect.rect.centerY()
        }

        fillPaint.color = when {
            activeShift -> theme.accent
            isPressed -> if (isSpecial) theme.specialPressed else theme.keyPressed
            else -> if (isSpecial) theme.special else theme.key
        }
        val radius = dp(10f)
        canvas.drawRoundRect(keyRect.rect, radius, radius, fillPaint)

        if (key.type == KeyType.CHAR && key.hint != null) {
            hintPaint.color = theme.hint
            val baseline = keyRect.rect.top + dp(17f)
            canvas.drawText(key.hint, keyRect.rect.left + dp(7f), baseline, hintPaint)
        }

        when (key.type) {
            KeyType.LANGUAGE -> drawGlobe(canvas, keyRect.rect)
            KeyType.SHIFT -> drawShiftIcon(canvas, keyRect.rect, if (activeShift) Color.WHITE else theme.text)
            KeyType.BACKSPACE -> drawBackspaceIcon(canvas, keyRect.rect, theme.text)
            KeyType.ENTER -> drawEnterIcon(canvas, keyRect.rect, theme.text)
            else -> {
                val paint = when {
                    key.type == KeyType.SPACE -> spaceTextPaint
                    isSpecial -> specialTextPaint
                    else -> textPaint
                }
                paint.color = if (activeShift) Color.WHITE else theme.text
                val label = if (key.type == KeyType.SPACE) language.displayName else key.label
                val centerY = keyRect.rect.centerY() - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText(label, keyRect.rect.centerX(), centerY, paint)
            }
        }
    }

    private fun drawGlobe(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = min(rect.width(), rect.height()) * 0.26f
        if (radius <= 0f) return

        iconPaint.color = theme.text
        iconPaint.strokeWidth = dp(1.6f)
        canvas.drawCircle(cx, cy, radius, iconPaint)
        canvas.drawOval(
            RectF(cx - radius * 0.45f, cy - radius, cx + radius * 0.45f, cy + radius),
            iconPaint
        )
        canvas.drawLine(cx - radius, cy, cx + radius, cy, iconPaint)
    }

    private fun drawShiftIcon(canvas: Canvas, rect: RectF, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(2.4f)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val size = rect.height()
        val w = size * 0.22f
        val h = size * 0.24f
        val path = Path().apply {
            moveTo(cx, cy - h)
            lineTo(cx - w, cy + h * 0.1f)
            lineTo(cx - w * 0.45f, cy + h * 0.1f)
            lineTo(cx - w * 0.45f, cy + h)
            lineTo(cx + w * 0.45f, cy + h)
            lineTo(cx + w * 0.45f, cy + h * 0.1f)
            lineTo(cx + w, cy + h * 0.1f)
            close()
        }
        canvas.drawPath(path, iconPaint)
        if (caps) {
            iconFillPaint.color = color
            val bar = RectF(
                cx - w * 0.55f,
                cy + h * 1.15f,
                cx + w * 0.55f,
                cy + h * 1.3f
            )
            canvas.drawRoundRect(bar, dp(1.2f), dp(1.2f), iconFillPaint)
        }
    }

    private fun drawBackspaceIcon(canvas: Canvas, rect: RectF, color: Int) {
        iconPaint.color = color
        iconPaint.strokeWidth = dp(2.4f)
        val cx = rect.centerX()
        val cy = rect.centerY()
        val size = rect.height()
        val w = size * 0.22f
        val h = size * 0.20f
        val path = Path().apply {
            moveTo(cx + w, cy - h)
            lineTo(cx - w * 0.35f, cy - h)
            lineTo(cx - w, cy)
            lineTo(cx - w * 0.35f, cy + h)
            lineTo(cx + w, cy + h)
            close()
        }
        canvas.drawPath(path, iconPaint)

        val xc = cx + w * 0.15f
        val x = w * 0.36f
        val y = h * 0.40f
        canvas.drawLine(xc - x, cy - y, xc + x, cy + y, iconPaint)
        canvas.drawLine(xc + x, cy - y, xc - x, cy + y, iconPaint)
    }

    private fun drawEnterIcon(canvas: Canvas, rect: RectF, color: Int) {
        val size = min(rect.width(), rect.height()) * 0.52f
        val left = rect.centerX() - size / 2f
        val top = rect.centerY() - size / 2f
        val path = Path().apply {
            moveTo(19f, 7f)
            lineTo(19f, 11f)
            lineTo(5.83f, 11f)
            lineTo(9.41f, 7.41f)
            lineTo(8f, 6f)
            lineTo(2f, 12f)
            lineTo(8f, 18f)
            lineTo(9.41f, 16.59f)
            lineTo(5.83f, 13f)
            lineTo(21f, 13f)
            lineTo(21f, 7f)
            close()
        }
        iconFillPaint.color = color
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(size / 24f, size / 24f)
        canvas.drawPath(path, iconFillPaint)
        canvas.restore()
    }

    private fun drawPreview(canvas: Canvas, keyRect: KeyRect) {
        val rect = keyRect.rect
        val previewWidth = rect.width() * 1.08f
        val previewHeight = rect.height() * 1.12f
        val left = (rect.centerX() - previewWidth / 2f)
            .coerceIn(dp(2f), width - previewWidth - dp(2f))
        var top = rect.top - previewHeight - dp(6f)
        if (top < dp(2f)) top = rect.bottom + dp(6f)
        val preview = RectF(left, top, left + previewWidth, top + previewHeight)

        fillPaint.color = theme.popup
        canvas.drawRoundRect(preview, dp(9f), dp(9f), fillPaint)
        textPaint.color = theme.popupText
        val label = if (keyRect.key.type == KeyType.SPACE) {
            language.displayName
        } else {
            keyRect.key.label
        }
        val centerY = preview.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, preview.centerX(), centerY, textPaint)
    }

    private fun showFloating(preview: Boolean) {
        val current = activeTouches[ownerPointerId]?.keyRect ?: return
        if (!isAttachedToWindow) return
        if (preview && current.key.type != KeyType.CHAR) return
        if (!preview && current.key.popup.isEmpty()) return

        val labels = if (preview) {
            listOf(current.key.label)
        } else {
            current.key.popup.map { if (shift || caps) it.uppercase() else it }
        }
        val boxWidth = if (preview) {
            current.rect.width() * 1.08f
        } else {
            maxOf(dp(46f), current.rect.width() * 0.92f)
        }
        val boxHeight = current.rect.height() * (if (preview) 1.12f else 1.08f)
        val itemGap = if (preview) 0f else dp(5f)
        val sidePadding = if (preview) 0f else dp(6f)
        val verticalPadding = if (preview) 0f else dp(5f)
        val totalWidth = sidePadding * 2 +
            boxWidth * labels.size +
            itemGap * (labels.size - 1).coerceAtLeast(0)
        val height = boxHeight + verticalPadding * 2

        val location = IntArray(2)
        getLocationOnScreen(location)
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val keyCenterX = location[0] + current.rect.centerX()
        val edge = if (preview) dp(2f) else dp(4f)
        var left = keyCenterX - totalWidth / 2f
        left = left.coerceIn(edge, screenWidth - totalWidth - edge)
        val top = (location[1] + current.rect.top - height -
            (if (preview) dp(6f) else dp(14f))).coerceAtLeast(edge)
        val selected = if (preview) 0 else
            ((keyCenterX - left - sidePadding) / (boxWidth + itemGap))
                .toInt().coerceIn(0, labels.lastIndex)

        val view = popupView ?: PopupView(context).also { popupView = it }
        view.isPreview = preview
        view.labels = labels
        view.selected = selected
        view.boxWidth = boxWidth
        view.boxHeight = boxHeight
        view.itemGap = itemGap
        view.sidePadding = sidePadding
        view.verticalPadding = verticalPadding
        view.invalidate()

        val popup = popupWindow ?: PopupWindow(
            view,
            totalWidth.toInt(),
            height.toInt(),
            false
        ).also {
            it.isOutsideTouchable = false
            it.isFocusable = false
            it.setTouchable(false)
            it.setAttachedInDecor(false)
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.setClippingEnabled(false)
            popupWindow = it
        }
        popup.width = totalWidth.toInt()
        popup.height = height.toInt()
        val x = (left - location[0]).toInt()
        val y = (top - location[1]).toInt()
        if (popup.isShowing) {
            popup.update(x, y, totalWidth.toInt(), height.toInt())
            view.requestLayout()
            view.invalidate()
        } else {
            popup.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        }

        popupLabels = if (preview) emptyList() else labels
        popupLeft = left
        popupBoxWidth = boxWidth
        popupItemGap = itemGap
        popupSidePadding = sidePadding
        popupSelected = if (preview) -1 else selected
        previewVisible = preview
        popupVisible = !preview
        invalidate()
    }

    private fun dismissFloating() {
        if (!popupVisible && !previewVisible) return
        popupWindow?.dismiss()
        popupVisible = false
        previewVisible = false
        popupSelected = -1
        popupLabels = emptyList()
        invalidate()
    }

    private inner class PopupView(context: Context) : View(context) {
        var labels: List<String> = emptyList()
        var selected: Int = 0
        var boxWidth: Float = 0f
        var boxHeight: Float = 0f
        var itemGap: Float = 0f
        var sidePadding: Float = 0f
        var verticalPadding: Float = 0f
        var isPreview: Boolean = false

        init {
            setWillNotDraw(false)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (isPreview) {
                setMeasuredDimension(boxWidth.toInt(), boxHeight.toInt())
                return
            }
            val totalWidth = sidePadding * 2 +
                boxWidth * labels.size +
                itemGap * (labels.size - 1).coerceAtLeast(0)
            setMeasuredDimension(
                totalWidth.toInt(),
                (boxHeight + verticalPadding * 2).toInt()
            )
        }

        override fun onDraw(canvas: Canvas) {
            if (isPreview) {
                val radius = dp(9f)
                fillPaint.color = theme.popup
                canvas.drawRoundRect(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat(),
                    radius,
                    radius,
                    fillPaint
                )
                textPaint.color = theme.popupText
                val label = labels.firstOrNull().orEmpty()
                val centerY = height / 2f -
                    (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(label, width / 2f, centerY, textPaint)
                return
            }
            val radius = dp(12f)
            fillPaint.color = theme.popup
            canvas.drawRoundRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                radius,
                radius,
                fillPaint
            )
            for ((index, label) in labels.withIndex()) {
                val left = sidePadding + index * (boxWidth + itemGap)
                val rect = RectF(
                    left,
                    verticalPadding,
                    left + boxWidth,
                    verticalPadding + boxHeight
                )
                if (index == selected) {
                    fillPaint.color = theme.accent
                    canvas.drawRoundRect(rect, dp(9f), dp(9f), fillPaint)
                }
                textPaint.color = if (index == selected) Color.WHITE else theme.popupText
                val centerY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(label, rect.centerX(), centerY, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                startTouch(
                    event.getPointerId(index),
                    event.getX(index),
                    event.getY(index)
                )
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (pointerId in activeTouches.keys.toList()) {
                    val pointerIndex = event.findPointerIndex(pointerId)
                    if (pointerIndex >= 0) {
                        val x = event.getX(pointerIndex)
                        moveTouch(
                            pointerId,
                            x,
                            event.getY(pointerIndex),
                            event.rawX - event.x + x
                        )
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                finishTouch(
                    event.getPointerId(index),
                    event.getX(index),
                    event.getY(index),
                    commit = true
                )
                return true
            }

            MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                finishTouch(
                    event.getPointerId(index),
                    event.getX(index),
                    event.getY(index),
                    commit = true
                )
                for (pointerId in activeTouches.keys.toList()) {
                    cancelTouch(pointerId)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                for (pointerId in activeTouches.keys.toList()) {
                    cancelTouch(pointerId)
                }
                return true
            }
        }
        return true
    }

    private fun startTouch(pointerId: Int, x: Float, y: Float) {
        if (activeTouches.containsKey(pointerId) || popupVisible) return
        if (previewVisible) dismissFloating()
        val keyRect = findKey(x, y) ?: return
        activeTouches[pointerId] = TouchState(keyRect, x, y)
        if (ownerPointerId == -1) ownerPointerId = pointerId
        if (keyRect.key.type != KeyType.EMPTY) playFeedback()

        if (pointerId == ownerPointerId) {
            if (keyRect.key.type == KeyType.CHAR) {
                showFloating(true)
            }
            if (keyRect.key.popup.isNotEmpty()) {
                handler.removeCallbacks(popupRunnable)
                handler.postDelayed(popupRunnable, 350)
            }
            if (keyRect.key.type == KeyType.BACKSPACE) {
                backspaceRepeating = false
                handler.removeCallbacks(backspaceRunnable)
                handler.postDelayed(backspaceRunnable, 400)
            }
        }
        invalidate()
    }

    private fun moveTouch(pointerId: Int, x: Float, y: Float, rawX: Float) {
        val touch = activeTouches[pointerId] ?: return
        if (popupVisible && pointerId == ownerPointerId) {
            if (popupBoxWidth > 0f && popupLabels.isNotEmpty()) {
                val stride = popupBoxWidth + popupItemGap
                val index = ((rawX - popupLeft - popupSidePadding) / stride)
                    .toInt()
                    .coerceIn(0, popupLabels.lastIndex)
                if (index != popupSelected) {
                    popupSelected = index
                    popupView?.selected = index
                    popupView?.invalidate()
                }
            }
            return
        }

        val current = touch.keyRect
        val dx = x - touch.downX
        val dy = y - touch.downY
        if (!touch.moved && dx * dx + dy * dy > touchSlop * touchSlop) {
            touch.moved = true
        }
        if (!touch.moved) {
            // Палец почти не двигался: держим клавишу, выбранную
            // при нажатии, даже если точка пришлась на зазор.
            return
        }
        if (y < 0f) {
            cancelTouch(pointerId)
            return
        }
        val next = findKey(x, y)
        if (next == null) {
            cancelTouch(pointerId)
        } else if (next != current) {
            switchPressedKey(pointerId, next)
        }
    }

    private fun finishTouch(pointerId: Int, x: Float, y: Float, commit: Boolean) {
        val touch = activeTouches[pointerId] ?: return
        val isOwner = pointerId == ownerPointerId
        if (isOwner) {
            handler.removeCallbacks(popupRunnable)
            handler.removeCallbacks(backspaceRunnable)
        }

        var keyRect: KeyRect? = touch.keyRect
        if (commit && !(popupVisible && isOwner)) {
            keyRect = findKey(x, y)
        }

        val wasPopup = popupVisible && isOwner
        val selectedPopup = if (wasPopup) popupLabels.getOrNull(popupSelected) else null
        val repeating = backspaceRepeating && isOwner

        if (isOwner) {
            dismissFloating()
            backspaceRepeating = false
        }
        activeTouches.remove(pointerId)
        if (isOwner) {
            ownerPointerId = activeTouches.keys.firstOrNull() ?: -1
        }

        if (commit && keyRect != null) {
            if (wasPopup && selectedPopup != null) {
                commitPopup(selectedPopup)
            } else if (!repeating) {
                handleKey(keyRect.key)
            }
        }
        invalidate()
    }

    private fun switchPressedKey(pointerId: Int, keyRect: KeyRect) {
        val touch = activeTouches[pointerId] ?: return
        touch.keyRect = keyRect
        if (pointerId != ownerPointerId) {
            invalidate()
            return
        }
        handler.removeCallbacks(popupRunnable)
        if (previewVisible) dismissFloating()
        if (backspaceRepeating) {
            handler.removeCallbacks(backspaceRunnable)
            backspaceRepeating = false
        }
        if (keyRect.key.type == KeyType.CHAR) {
            showFloating(true)
        }
        if (keyRect.key.popup.isNotEmpty()) {
            handler.postDelayed(popupRunnable, 350)
        }
        invalidate()
    }

    private fun cancelTouch(pointerId: Int) {
        activeTouches.remove(pointerId) ?: return
        if (pointerId == ownerPointerId) {
            handler.removeCallbacks(popupRunnable)
            handler.removeCallbacks(backspaceRunnable)
            backspaceRepeating = false
            if (previewVisible || popupVisible) dismissFloating()
            ownerPointerId = activeTouches.keys.firstOrNull() ?: -1
        }
        invalidate()
    }

    private fun resetTouchState() {
        handler.removeCallbacksAndMessages(null)
        activeTouches.clear()
        ownerPointerId = -1
        backspaceRepeating = false
        dismissFloating()
    }

    override fun onDetachedFromWindow() {
        resetTouchState()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) resetTouchState()
    }

    private fun findKey(x: Float, y: Float): KeyRect? {
        val keysBottom = rows.lastOrNull()?.lastOrNull()?.rect?.bottom ?: return null
        if (y > keysBottom) return null
        return rows.flatten().minByOrNull { keyRect ->
            val rect = keyRect.rect
            val dx = maxOf(rect.left - x, 0f, x - rect.right)
            val dy = maxOf(rect.top - y, 0f, y - rect.bottom)
            dx * dx + dy * dy
        }
    }

    private fun handleKey(key: Key) {
        when (key.type) {
            KeyType.SHIFT -> {
                if (caps) {
                    caps = false
                    shift = false
                } else if (shift) {
                    caps = true
                } else {
                    shift = true
                }
                rebuildLayout()
            }

            KeyType.BACKSPACE -> {
                service.onBackspace()
                consumeShift()
            }

            KeyType.ENTER -> {
                service.onEnter()
                consumeShift()
            }

            KeyType.SPACE -> {
                service.onText(" ")
                consumeShift()
            }

            KeyType.LANGUAGE -> {
                val newLanguage = language.next()
                language = newLanguage
                page = Page.LETTERS
                shift = false
                caps = false
                service.onLanguageChanged(newLanguage)
                rebuildLayout()
            }

            KeyType.SYMBOLS -> {
                page = Page.SYMBOLS
                rebuildLayout()
            }

            KeyType.ABC -> {
                page = Page.LETTERS
                rebuildLayout()
            }

            KeyType.EMPTY -> Unit

            KeyType.CHAR -> {
                val text = if (shift || caps) key.label else key.output
                service.onText(text)
                consumeShift()
            }
        }
    }

    private fun commitPopup(text: String) {
        service.onText(text)
        consumeShift()
    }

    private fun consumeShift() {
        if (shift && !caps) {
            shift = false
            rebuildLayout()
        }
    }

    private fun playFeedback() {
        if (vibration) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (sound) {
            playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        theme = Themes.resolve(context, themeMode)
        rebuildLayout()
    }
}
