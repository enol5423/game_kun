package com.gamekun.mergeblocks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    val game = Game2048()

    /** Called after every successful move (score may have changed). */
    var onBoardChanged: (() -> Unit)? = null

    /** Called once when the game transitions into game-over. */
    var onGameOver: (() -> Unit)? = null

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5D4A66")
    }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val tileColors = mapOf(
        0 to Color.parseColor("#6E5A78"),
        2 to Color.parseColor("#EFE6F7"),
        4 to Color.parseColor("#E3CCF4"),
        8 to Color.parseColor("#C79BEB"),
        16 to Color.parseColor("#A96FDD"),
        32 to Color.parseColor("#8A4BC8"),
        64 to Color.parseColor("#F2A65A"),
        128 to Color.parseColor("#F28444"),
        256 to Color.parseColor("#F26430"),
        512 to Color.parseColor("#E84C3D"),
        1024 to Color.parseColor("#D93750"),
        2048 to Color.parseColor("#FFC300")
    )
    private val fallbackColor = Color.parseColor("#2E1A47")

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val dir = if (abs(dx) > abs(dy)) {
                    if (dx > 0) Game2048.Direction.RIGHT else Game2048.Direction.LEFT
                } else {
                    if (dy > 0) Game2048.Direction.DOWN else Game2048.Direction.UP
                }
                if (game.move(dir)) {
                    invalidate()
                    onBoardChanged?.invoke()
                    if (game.isGameOver) onGameOver?.invoke()
                }
                return true
            }
        })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) performClick()
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = Game2048.SIZE
        val board = min(width, height).toFloat()
        val left = (width - board) / 2f
        val top = (height - board) / 2f
        val gap = board * 0.02f
        val cell = (board - gap * (n + 1)) / n

        canvas.drawRoundRect(
            RectF(left, top, left + board, top + board), gap, gap, boardPaint
        )

        for (r in 0 until n) {
            for (c in 0 until n) {
                val x = left + gap + c * (cell + gap)
                val y = top + gap + r * (cell + gap)
                val value = game.grid[r][c]
                cellPaint.color = tileColors[value] ?: fallbackColor
                canvas.drawRoundRect(
                    RectF(x, y, x + cell, y + cell), gap * 0.8f, gap * 0.8f, cellPaint
                )
                if (value != 0) {
                    textPaint.color =
                        if (value <= 4) Color.parseColor("#3B2A4A") else Color.WHITE
                    textPaint.textSize = when {
                        value < 100 -> cell * 0.5f
                        value < 1000 -> cell * 0.4f
                        else -> cell * 0.32f
                    }
                    val textY = y + cell / 2f -
                        (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(value.toString(), x + cell / 2f, textY, textPaint)
                }
            }
        }
    }
}
