package com.gamekun.mergeblocks

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Renders the board with slide/merge/spawn animations, particle bursts,
 * floating score text, combo banner and screen shake.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    val game = Game2048()

    var onBoardChanged: (() -> Unit)? = null
    var onGameOver: (() -> Unit)? = null

    /** When true, a tap smashes a tile instead of swiping. */
    var hammerMode = false
    var onHammerHit: ((Boolean) -> Unit)? = null // success -> consumed a charge

    // ------------------------------------------------------------ paints

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A3757")
    }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fxTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val tileColors = mapOf(
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
    private val emptyColor = Color.parseColor("#5D4A66")
    private val fallbackColor = Color.parseColor("#2E1A47")
    private val blockerColor = Color.parseColor("#7D7A85")
    private val bombColor = Color.parseColor("#241B2E")

    // -------------------------------------------------------- animation

    private var moveResult: Game2048.MoveResult? = null
    private var progress = 1f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 170
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    private class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float,
        val color: Int, val size: Float
    )

    private class FloatText(
        val text: String, val x: Float, var y: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float
    )

    private val particles = ArrayList<Particle>()
    private val floatTexts = ArrayList<FloatText>()
    private var lastFrameTime = 0L
    private var comboFlashUntil = 0L
    private var comboShown = 0
    private var shakeUntil = 0L

    // -------------------------------------------------------- geometry

    private var boardSide = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var gap = 0f
    private var cellSize = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        boardSide = min(w, h).toFloat()
        boardLeft = (w - boardSide) / 2f
        boardTop = (h - boardSide) / 2f
        gap = boardSide * 0.022f
        cellSize = (boardSide - gap * (Game2048.SIZE + 1)) / Game2048.SIZE
    }

    private fun cellX(c: Int) = boardLeft + gap + c * (cellSize + gap)
    private fun cellY(r: Int) = boardTop + gap + r * (cellSize + gap)
    private fun cellCenterX(c: Int) = cellX(c) + cellSize / 2f
    private fun cellCenterY(r: Int) = cellY(r) + cellSize / 2f

    // ------------------------------------------------------------ input

    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                val dx = event.x - downX
                val dy = event.y - downY
                val threshold = boardSide * 0.06f
                if (hammerMode && abs(dx) < threshold && abs(dy) < threshold) {
                    handleHammerTap(event.x, event.y)
                } else if (abs(dx) >= threshold || abs(dy) >= threshold) {
                    val dir = if (abs(dx) > abs(dy)) {
                        if (dx > 0) Game2048.Direction.RIGHT else Game2048.Direction.LEFT
                    } else {
                        if (dy > 0) Game2048.Direction.DOWN else Game2048.Direction.UP
                    }
                    doMove(dir)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun doMove(dir: Game2048.Direction) {
        val result = game.move(dir) ?: return
        moveResult = result
        animator.cancel()
        animator.start()
        spawnMoveEffects(result)
        onBoardChanged?.invoke()
        if (game.isGameOver) postDelayed({ onGameOver?.invoke() }, 350)
    }

    private fun handleHammerTap(x: Float, y: Float) {
        val c = ((x - boardLeft - gap) / (cellSize + gap)).toInt()
        val r = ((y - boardTop - gap) / (cellSize + gap)).toInt()
        if (r !in 0 until Game2048.SIZE || c !in 0 until Game2048.SIZE) return
        if (game.smash(r, c)) {
            burst(cellCenterX(c), cellCenterY(r), Color.WHITE, 22, big = true)
            shakeUntil = now() + 250
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            moveResult = null
            onHammerHit?.invoke(true)
            onBoardChanged?.invoke()
            invalidate()
        } else {
            onHammerHit?.invoke(false)
        }
    }

    /** Redraw after an external change (undo/shuffle/revive/new game). */
    fun refresh() {
        moveResult = null
        animator.cancel()
        progress = 1f
        invalidate()
    }

    // -------------------------------------------------------------- fx

    private fun now() = System.currentTimeMillis()

    private fun spawnMoveEffects(result: Game2048.MoveResult) {
        for ((cell, value, points) in result.merges) {
            val cx = cellCenterX(cell.c)
            val cy = cellCenterY(cell.r)
            if (value >= 32) {
                burst(cx, cy, tileColors[value] ?: fallbackColor, if (value >= 256) 26 else 14)
            }
            val label = if (result.combo >= 2) "+$points ×${min(result.combo, Game2048.MAX_MULTIPLIER)}" else "+$points"
            floatTexts.add(
                FloatText(
                    label, cx, cy - cellSize * 0.2f,
                    700f, 700f,
                    if (result.combo >= 2) Color.parseColor("#FFC300") else Color.WHITE,
                    cellSize * if (result.combo >= 2) 0.30f else 0.24f
                )
            )
        }
        if (result.merges.isNotEmpty()) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        for (cell in result.explosions) {
            burst(cellCenterX(cell.c), cellCenterY(cell.r), Color.parseColor("#FF6B35"), 20, big = true)
        }
        if (result.explosions.isNotEmpty()) {
            shakeUntil = now() + 350
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        for (cell in result.crumbled) {
            burst(cellCenterX(cell.c), cellCenterY(cell.r), blockerColor, 12)
        }
        if (result.combo >= 2) {
            comboShown = min(result.combo, Game2048.MAX_MULTIPLIER)
            comboFlashUntil = now() + 1100
        }
        if (particles.isNotEmpty() || floatTexts.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun burst(x: Float, y: Float, color: Int, count: Int, big: Boolean = false) {
        repeat(count) {
            val angle = Random.nextFloat() * (2 * Math.PI).toFloat()
            val speed = cellSize * (if (big) 4f else 2.5f) * (0.4f + Random.nextFloat())
            particles.add(
                Particle(
                    x, y,
                    speed * kotlin.math.cos(angle), speed * kotlin.math.sin(angle),
                    450f, 450f, color,
                    cellSize * (0.04f + Random.nextFloat() * 0.06f)
                )
            )
        }
    }

    // ------------------------------------------------------------- draw

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val time = now()
        val dt = if (lastFrameTime == 0L) 16f else (time - lastFrameTime).coerceAtMost(48).toFloat()
        lastFrameTime = time

        if (time < shakeUntil) {
            canvas.translate(
                (Random.nextFloat() - 0.5f) * cellSize * 0.12f,
                (Random.nextFloat() - 0.5f) * cellSize * 0.12f
            )
        }

        // Board and empty cells.
        canvas.drawRoundRect(
            RectF(boardLeft, boardTop, boardLeft + boardSide, boardTop + boardSide),
            gap * 1.6f, gap * 1.6f, boardPaint
        )
        cellPaint.color = emptyColor
        for (r in 0 until Game2048.SIZE) for (c in 0 until Game2048.SIZE) {
            canvas.drawRoundRect(cellRect(cellX(c), cellY(r), 1f), gap, gap, cellPaint)
        }

        // Tiles.
        val anims = if (progress < 1f) moveResult?.anims else null
        for (r in 0 until Game2048.SIZE) for (c in 0 until Game2048.SIZE) {
            val tile = game.board[r][c] ?: continue
            val anim = anims?.get(tile.id)
            when {
                anim == null -> drawTile(canvas, tile, cellX(c), cellY(r), 1f)
                anim.isMerge -> {
                    val slide = (progress / 0.65f).coerceAtMost(1f)
                    if (progress < 0.65f) {
                        for (from in anim.fromCells) {
                            val x = lerp(cellX(from.c), cellX(c), slide)
                            val y = lerp(cellY(from.r), cellY(r), slide)
                            drawTile(canvas, tile, x, y, 1f, valueOverride = tile.value / 2)
                        }
                    } else {
                        val pop = 1f + 0.28f * sin(Math.PI * (progress - 0.65f) / 0.35f).toFloat()
                        drawTile(canvas, tile, cellX(c), cellY(r), pop)
                    }
                }
                anim.isNew -> {
                    if (progress > 0.55f) {
                        val s = ((progress - 0.55f) / 0.45f).coerceAtMost(1f)
                        drawTile(canvas, tile, cellX(c), cellY(r), s)
                    }
                }
                else -> {
                    val slide = (progress / 0.65f).coerceAtMost(1f)
                    val from = anim.fromCells[0]
                    val x = lerp(cellX(from.c), cellX(c), slide)
                    val y = lerp(cellY(from.r), cellY(r), slide)
                    drawTile(canvas, tile, x, y, 1f)
                }
            }
        }

        drawEffects(canvas, dt)
        drawComboBanner(canvas, time)

        if (particles.isNotEmpty() || floatTexts.isNotEmpty() ||
            time < shakeUntil || time < comboFlashUntil
        ) {
            postInvalidateOnAnimation()
        } else {
            lastFrameTime = 0L
        }
    }

    private fun cellRect(x: Float, y: Float, scale: Float): RectF {
        val half = cellSize / 2f
        val cx = x + half
        val cy = y + half
        return RectF(cx - half * scale, cy - half * scale, cx + half * scale, cy + half * scale)
    }

    private fun drawTile(
        canvas: Canvas, tile: Game2048.Tile,
        x: Float, y: Float, scale: Float,
        valueOverride: Int? = null
    ) {
        if (scale <= 0.02f) return
        val rect = cellRect(x, y, scale)
        when (tile.type) {
            Game2048.TileType.NORMAL -> {
                val value = valueOverride ?: tile.value
                cellPaint.color = tileColors[value] ?: fallbackColor
                if (value >= 128) {
                    cellPaint.setShadowLayer(gap * 1.5f, 0f, 0f, cellPaint.color)
                }
                canvas.drawRoundRect(rect, gap, gap, cellPaint)
                cellPaint.clearShadowLayer()
                textPaint.color = if (value <= 4) Color.parseColor("#3B2A4A") else Color.WHITE
                textPaint.textSize = scale * cellSize * when {
                    value < 100 -> 0.48f
                    value < 1000 -> 0.38f
                    else -> 0.3f
                }
                canvas.drawText(value.toString(), rect.centerX(), textBaseline(rect.centerY()), textPaint)
            }
            Game2048.TileType.BLOCKER -> {
                cellPaint.color = blockerColor
                canvas.drawRoundRect(rect, gap, gap, cellPaint)
                strokePaint.color = Color.parseColor("#57545E")
                strokePaint.strokeWidth = gap * 0.7f
                // cracks
                canvas.drawLine(rect.left + rect.width() * 0.25f, rect.top + rect.height() * 0.2f,
                    rect.left + rect.width() * 0.55f, rect.top + rect.height() * 0.55f, strokePaint)
                canvas.drawLine(rect.left + rect.width() * 0.55f, rect.top + rect.height() * 0.55f,
                    rect.left + rect.width() * 0.35f, rect.top + rect.height() * 0.85f, strokePaint)
                canvas.drawLine(rect.left + rect.width() * 0.55f, rect.top + rect.height() * 0.55f,
                    rect.left + rect.width() * 0.85f, rect.top + rect.height() * 0.7f, strokePaint)
                textPaint.color = Color.parseColor("#2E2B33")
                textPaint.textSize = scale * cellSize * 0.26f
                canvas.drawText(
                    tile.timer.toString(),
                    rect.right - rect.width() * 0.18f,
                    rect.bottom - rect.height() * 0.1f,
                    textPaint
                )
            }
            Game2048.TileType.BOMB -> {
                cellPaint.color = bombColor
                canvas.drawRoundRect(rect, gap, gap, cellPaint)
                strokePaint.color =
                    if (tile.timer <= 3) Color.parseColor("#FF3B30") else Color.parseColor("#F2A65A")
                strokePaint.strokeWidth = gap * 0.8f
                canvas.drawRoundRect(rect, gap, gap, strokePaint)
                textPaint.color = strokePaint.color
                textPaint.textSize = scale * cellSize * 0.5f
                canvas.drawText(tile.timer.toString(), rect.centerX(), textBaseline(rect.centerY()), textPaint)
                textPaint.textSize = scale * cellSize * 0.2f
                canvas.drawText("BOMB", rect.centerX(), rect.bottom - rect.height() * 0.08f, textPaint)
            }
        }
    }

    private fun textBaseline(centerY: Float) =
        centerY - (textPaint.descent() + textPaint.ascent()) / 2f

    private fun drawEffects(canvas: Canvas, dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0) {
                it.remove()
                continue
            }
            p.x += p.vx * dt / 1000f
            p.y += p.vy * dt / 1000f
            p.vy += cellSize * 6f * dt / 1000f // gravity
            fxPaint.color = p.color
            fxPaint.alpha = (255 * p.life / p.maxLife).toInt()
            canvas.drawCircle(p.x, p.y, p.size * (p.life / p.maxLife), fxPaint)
        }
        val ft = floatTexts.iterator()
        while (ft.hasNext()) {
            val t = ft.next()
            t.life -= dt
            if (t.life <= 0) {
                ft.remove()
                continue
            }
            t.y -= cellSize * 1.2f * dt / 1000f
            fxTextPaint.color = t.color
            fxTextPaint.alpha = (255 * t.life / t.maxLife).toInt()
            fxTextPaint.textSize = t.size
            canvas.drawText(t.text, t.x, t.y, fxTextPaint)
        }
    }

    private fun drawComboBanner(canvas: Canvas, time: Long) {
        if (time >= comboFlashUntil) return
        val remain = (comboFlashUntil - time) / 1100f
        val pulse = 1f + 0.12f * sin(time / 90.0).toFloat()
        fxTextPaint.color = Color.parseColor("#FFC300")
        fxTextPaint.alpha = (255 * remain.coerceAtMost(0.6f) / 0.6f).toInt()
        fxTextPaint.textSize = cellSize * 0.42f * pulse
        canvas.drawText(
            "COMBO ×$comboShown",
            width / 2f,
            boardTop + boardSide * 0.42f,
            fxTextPaint
        )
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
