package com.uhufor.inspector.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.toColorInt
import com.uhufor.inspector.Config
import com.uhufor.inspector.configProvider
import com.uhufor.inspector.engine.InspectorEngine
import com.uhufor.inspector.util.UnitConverter
import com.uhufor.inspector.util.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@SuppressLint("ClickableViewAccessibility")
internal class OverlayCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : View(context, attrs, defStyleAttr, defStyleRes) {

    interface BackKeyListener {
        fun onBackPressed()
    }

    var backKeyListener: BackKeyListener? = null

    private val configProvider = context.configProvider()
    private val cfg: Config
        get() = configProvider.getConfig()

    private val engine = InspectorEngine(context) { postInvalidate() }

    private val normalBorderWidth = 1.dp(context).toFloat()
    private val clickableBorderWidth = 2.dp(context).toFloat()

    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = normalBorderWidth
        style = Paint.Style.STROKE
        color = Color.RED
    }

    private val paintClickableBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = clickableBorderWidth
        style = Paint.Style.STROKE
        color = Color.RED
    }

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TEXT_SIZE
        color = Color.RED
    }

    private val paintDistance = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = DISTANCE_TEXT_SIZE
        color = Color.WHITE
    }

    private val paintDashedLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = DASHED_LINE_WIDTH
        style = Paint.Style.STROKE
        color = Color.WHITE
        pathEffect = android.graphics.DashPathEffect(
            floatArrayOf(DASH_PATTERN_ON, DASH_PATTERN_OFF),
            DASH_PHASE
        )
    }


    private val elementColorMap = mutableMapOf<Int, Int>()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isFocusable = true
        isFocusableInTouchMode = true
        setOnTouchListener(::handleTouch)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            engine.handleTap(event.rawX, event.rawY)
            requestFocus()
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backKeyListener?.onBackPressed()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawAllElements(canvas)
        drawSelectedElement(canvas)
    }

    private fun drawAllElements(canvas: Canvas) {
        engine.allElements.forEach { element ->
            val color = applyAlpha(getColorForElement(element), 0.5f)
            val paint = if (element.isClickable) paintClickableBorder else paintBorder
            paint.color = color
            canvas.drawRect(element.bounds, paint)
            paint.color = Color.RED
        }
    }

    private fun drawSelectedElement(canvas: Canvas) {
        val selected = engine.selection ?: return
        val color = getComplementaryColor(getColorForElement(selected))
        val paint = if (selected.isClickable) paintClickableBorder else paintBorder

        selected.parentBounds?.let { parentBounds ->
            drawDarkBackground(canvas, selected.bounds, parentBounds)
        }

        paint.color = color
        canvas.drawRect(selected.bounds, paint)

        val dm = resources.displayMetrics
        val width = UnitConverter.format(selected.bounds.width(), dm, cfg.unitMode)
        val height = UnitConverter.format(selected.bounds.height(), dm, cfg.unitMode)

        paintText.color = color
        canvas.drawText(
            "$width × $height",
            selected.bounds.left,
            selected.bounds.top - DIMENSION_TEXT_OFFSET,
            paintText
        )

        selected.parentBounds?.let { parentBounds ->
            drawDistanceToBounds(canvas, selected.bounds, parentBounds)
        }

        paintText.color = Color.RED
    }

    private fun drawDarkBackground(
        canvas: Canvas,
        childBounds: android.graphics.RectF,
        parentBounds: android.graphics.RectF,
    ) {
        val bgPaint = Paint().apply {
            color = DARK_BG_COLOR.toColorInt()
            style = Paint.Style.FILL
        }

        val path = android.graphics.Path()
        path.addRect(parentBounds, android.graphics.Path.Direction.CW)
        path.addRect(childBounds, android.graphics.Path.Direction.CCW)
        canvas.drawPath(path, bgPaint)
    }

    private fun drawDistanceToBounds(
        canvas: Canvas,
        childBounds: android.graphics.RectF,
        parentBounds: android.graphics.RectF,
    ) {
        val dm = resources.displayMetrics

        val leftDistance = childBounds.left - parentBounds.left
        if (leftDistance > 0) {
            val distanceText = UnitConverter.format(leftDistance, dm, cfg.unitMode)
            drawDistanceLine(
                canvas,
                parentBounds.left, childBounds.top + childBounds.height() / 2,
                childBounds.left, childBounds.top + childBounds.height() / 2,
                distanceText
            )
        }

        val rightDistance = parentBounds.right - childBounds.right
        if (rightDistance > 0) {
            val distanceText = UnitConverter.format(rightDistance, dm, cfg.unitMode)
            drawDistanceLine(
                canvas,
                childBounds.right, childBounds.top + childBounds.height() / 2,
                parentBounds.right, childBounds.top + childBounds.height() / 2,
                distanceText
            )
        }

        val topDistance = childBounds.top - parentBounds.top
        if (topDistance > 0) {
            val distanceText = UnitConverter.format(topDistance, dm, cfg.unitMode)
            drawDistanceLine(
                canvas,
                childBounds.left + childBounds.width() / 2, parentBounds.top,
                childBounds.left + childBounds.width() / 2, childBounds.top,
                distanceText
            )
        }

        val bottomDistance = parentBounds.bottom - childBounds.bottom
        if (bottomDistance > 0) {
            val distanceText = UnitConverter.format(bottomDistance, dm, cfg.unitMode)
            drawDistanceLine(
                canvas,
                childBounds.left + childBounds.width() / 2, childBounds.bottom,
                childBounds.left + childBounds.width() / 2, parentBounds.bottom,
                distanceText
            )
        }
    }

    private fun drawDistanceLine(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        distanceText: String,
    ) {
        canvas.drawLine(startX, startY, endX, endY, paintDashedLine)
        drawArrow(canvas, startX, startY, endX, endY, paintDashedLine)
        drawArrow(canvas, endX, endY, startX, startY, paintDashedLine)

        val isHorizontal = abs(startY - endY) < abs(startX - endX)

        val textWidth = paintDistance.measureText(distanceText)
        val textX = (startX + endX) / 2 - textWidth / 2

        val textY = if (isHorizontal) {
            (startY + endY) / 2 - TEXT_VERTICAL_OFFSET_HORIZONTAL_LINE
        } else {
            (startY + endY) / 2 + TEXT_VERTICAL_OFFSET_VERTICAL_LINE
        }

        val textBgRect = android.graphics.RectF(
            textX - TEXT_PADDING_HORIZONTAL,
            textY - TEXT_PADDING_TOP,
            textX + textWidth + TEXT_PADDING_HORIZONTAL,
            textY + TEXT_PADDING_BOTTOM
        )

        val bgPaint = Paint().apply {
            color = Color.argb(TEXT_BG_ALPHA, 0, 0, 0)
        }

        canvas.drawRect(textBgRect, bgPaint)
        canvas.drawText(distanceText, textX, textY, paintDistance)
    }

    private fun drawArrow(
        canvas: Canvas,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        paint: Paint,
    ) {
        val angle = atan2((toY - fromY).toDouble(), (toX - fromX).toDouble())

        val arrowX1 = fromX + ARROW_SIZE * cos(angle - ARROW_ANGLE).toFloat()
        val arrowY1 = fromY + ARROW_SIZE * sin(angle - ARROW_ANGLE).toFloat()
        val arrowX2 = fromX + ARROW_SIZE * cos(angle + ARROW_ANGLE).toFloat()
        val arrowY2 = fromY + ARROW_SIZE * sin(angle + ARROW_ANGLE).toFloat()

        canvas.drawLine(fromX, fromY, arrowX1, arrowY1, paint)
        canvas.drawLine(fromX, fromY, arrowX2, arrowY2, paint)
    }

    private fun getColorForElement(element: Any) =
        elementColorMap.getOrPut(element.hashCode()) {
            ELEMENT_COLORS[Random.nextInt(ELEMENT_COLORS.size)]
        }

    private fun getComplementaryColor(color: Int) = Color.rgb(
        255 - Color.red(color),
        255 - Color.green(color),
        255 - Color.blue(color)
    )

    private fun applyAlpha(color: Int, alpha: Float) = Color.argb(
        (Color.alpha(color) * alpha).toInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.START or Gravity.TOP }

    fun scanAllElements() {
        engine.scanAllElements()
    }

    fun clearScan() {
        engine.clearScan()
    }

//    fun handleTap(x: Float, y: Float) {
//        engine.handleTap(x, y)
//    }
//    fun getSelection() = engine.selection
//    fun getAllElements() = engine.allElements

    companion object {
        private const val TEXT_SIZE = 24f
        private const val DISTANCE_TEXT_SIZE = 18f
        private const val DASHED_LINE_WIDTH = 2f
        private const val ARROW_SIZE = 10f
        private const val TEXT_PADDING_HORIZONTAL = 5f
        private const val TEXT_PADDING_TOP = 20f
        private const val TEXT_PADDING_BOTTOM = 5f
        private const val TEXT_VERTICAL_OFFSET_HORIZONTAL_LINE = 15f
        private const val TEXT_VERTICAL_OFFSET_VERTICAL_LINE = 5f
        private const val DIMENSION_TEXT_OFFSET = 8f
        private const val TEXT_BG_ALPHA = 220
        private const val DARK_BG_COLOR = "#30000000"
        private const val DASH_PATTERN_ON = 10f
        private const val DASH_PATTERN_OFF = 5f
        private const val DASH_PHASE = 0f
        private const val ARROW_ANGLE = Math.PI / 6

        private val ELEMENT_COLORS = listOf(
            "#F44336".toColorInt(),
            "#E91E63".toColorInt(),
            "#9C27B0".toColorInt(),
            "#673AB7".toColorInt(),
            "#3F51B5".toColorInt(),
            "#2196F3".toColorInt(),
            "#03A9F4".toColorInt(),
            "#00BCD4".toColorInt(),
            "#009688".toColorInt(),
            "#4CAF50".toColorInt(),
            "#8BC34A".toColorInt(),
            "#CDDC39".toColorInt(),
            "#FFEB3B".toColorInt(),
            "#FFC107".toColorInt(),
            "#FF9800".toColorInt(),
            "#FF5722".toColorInt(),
            "#795548".toColorInt(),
            "#9E9E9E".toColorInt(),
            "#607D8B".toColorInt(),
            "#000000".toColorInt()
        )
    }
}
