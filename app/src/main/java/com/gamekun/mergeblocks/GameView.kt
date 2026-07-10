package com.gamekun.mergeblocks

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Renders the board with slide/merge/spawn animations, particle bursts,
 * floating score text, combo banner, trap-tile theming and screen shake.
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
        set(value) {
            field = value
            hoverR = -1; hoverC = -1
            invalidate()
        }
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
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
    private val blockerDark = Color.parseColor("#5B5763")
    private val blockerLight = Color.parseColor("#726D7C")
    private val hazardYellow = Color.parseColor("#FFC300")
    private val bombShellColor = Color.parseColor("#1B1420")
    private val bombShineColor = Color.parseColor("#4A3F55")
    private val bombTileBg = Color.parseColor("#2A2130")

    // -------------------------------------------------------- animation

    /** id -> where it slid/appeared from, for the currently animating action. */
    private var activeAnims: Map<Int, Game2048.TileAnim>? = null
    private var progress = 1f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
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
        val color: Int, val size: Float,
        val shard: Boolean = false, var angle: Float = 0f
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
    private var shakeMagnitude = 1f
    private var flashUntil = 0L
    private var flashColor = Color.WHITE

    /** Banner used for shuffle/undo feedback ("SHUFFLED!", "UNDONE"). */
    private var actionBannerUntil = 0L
    private var actionBannerText = ""
    private var actionBannerColor = Color.WHITE

    // Hammer hover-highlight (finger-down preview before release).
    private var hoverR = -1
    private var hoverC = -1

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

    private fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val c = ((x - boardLeft - gap) / (cellSize + gap)).toInt()
        val r = ((y - boardTop - gap) / (cellSize + gap)).toInt()
        return if (r in 0 until Game2048.SIZE && c in 0 until Game2048.SIZE) r to c else null
    }

    // ------------------------------------------------------------ input

    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                if (hammerMode) updateHover(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (hammerMode) updateHover(event.x, event.y)
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
                hoverR = -1; hoverC = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateHover(x: Float, y: Float) {
        val cell = cellAt(x, y)
        hoverR = cell?.first ?: -1
        hoverC = cell?.second ?: -1
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun doMove(dir: Game2048.Direction) {
        val result = game.move(dir) ?: return
        activeAnims = result.anims
        startAnimator(170)
        spawnMoveEffects(result)
        onBoardChanged?.invoke()
        if (game.isGameOver) postDelayed({ onGameOver?.invoke() }, 350)
    }

    private fun handleHammerTap(x: Float, y: Float) {
        val (r, c) = cellAt(x, y) ?: return
        val tile = game.board[r][c]
        if (tile != null && game.smash(r, c)) {
            val color = when (tile.type) {
                Game2048.TileType.BLOCKER -> blockerLight
                Game2048.TileType.BOMB -> Color.parseColor("#FF6B35")
                Game2048.TileType.NORMAL -> tileColors[tile.value] ?: Color.WHITE
            }
            shatter(cellCenterX(c), cellCenterY(r), color)
            burst(cellCenterX(c), cellCenterY(r), Color.WHITE, 16, big = true)
            shake(250, 1f)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            activeAnims = null
            onHammerHit?.invoke(true)
            onBoardChanged?.invoke()
            invalidate()
        } else {
            onHammerHit?.invoke(false)
        }
    }

    /** Snapshot of every tile's current cell, keyed by tile id. */
    private fun capturePositions(): Map<Int, Game2048.Cell> {
        val map = HashMap<Int, Game2048.Cell>()
        for (r in 0 until Game2048.SIZE) for (c in 0 until Game2048.SIZE) {
            game.board[r][c]?.let { map[it.id] = Game2048.Cell(r, c) }
        }
        return map
    }

    private fun buildRepositionAnims(oldPositions: Map<Int, Game2048.Cell>): Map<Int, Game2048.TileAnim> {
        val anims = HashMap<Int, Game2048.TileAnim>()
        for (r in 0 until Game2048.SIZE) for (c in 0 until Game2048.SIZE) {
            val tile = game.board[r][c] ?: continue
            val from = oldPositions[tile.id]
            if (from != null) {
                if (from.r != r || from.c != c) anims[tile.id] = Game2048.TileAnim(listOf(from))
            } else {
                anims[tile.id] = Game2048.TileAnim(listOf(Game2048.Cell(r, c)), isNew = true)
            }
        }
        return anims
    }

    /** Undo booster: slides tiles back to where they were, with a rewind flash. */
    fun animateUndo(): Boolean {
        val before = capturePositions()
        if (!game.undo()) return false
        activeAnims = buildRepositionAnims(before)
        startAnimator(260)
        flashUntil = now() + 260
        flashColor = Color.parseColor("#4FC3F7")
        showActionBanner("↩ UNDONE", Color.parseColor("#4FC3F7"))
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        invalidate()
        return true
    }

    /** Shuffle booster: scatters tiles to new spots with a sparkle burst. */
    fun animateShuffle(): Boolean {
        val before = capturePositions()
        if (!game.shuffle()) return false
        activeAnims = buildRepositionAnims(before)
        startAnimator(320)
        for (i in 0 until 18) {
            val angle = Random.nextFloat() * (2 * Math.PI).toFloat()
            val r = boardSide * 0.5f * Random.nextFloat()
            burst(
                boardLeft + boardSide / 2f + cos(angle) * r,
                boardTop + boardSide / 2f + sin(angle) * r,
                hazardYellow, 1
            )
        }
        showActionBanner("🔀 SHUFFLED", Color.parseColor("#C79BEB"))
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        invalidate()
        return true
    }

    private fun startAnimator(durationMs: Long) {
        animator.cancel()
        animator.duration = durationMs
        animator.start()
    }

    private fun showActionBanner(text: String, color: Int) {
        actionBannerText = text
        actionBannerColor = color
        actionBannerUntil = now() + 900
    }

    /** Redraw after an external change (revive/new game) with no slide animation. */
    fun refresh() {
        activeAnims = null
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
                    if (result.combo >= 2) hazardYellow else Color.WHITE,
                    cellSize * if (result.combo >= 2) 0.30f else 0.24f
                )
            )
        }
        if (result.merges.isNotEmpty()) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        for (cell in result.explosions) {
            val cx = cellCenterX(cell.c)
            val cy = cellCenterY(cell.r)
            burst(cx, cy, Color.parseColor("#FF6B35"), 26, big = true)
            shatter(cx, cy, Color.parseColor("#FFD23F"))
        }
        if (result.explosions.isNotEmpty()) {
            shake(400, 1.6f)
            flashUntil = now() + 180
            flashColor = Color.parseColor("#FF6B35")
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        for (cell in result.crumbled) {
            burst(cellCenterX(cell.c), cellCenterY(cell.r), blockerLight, 16)
            shatter(cellCenterX(cell.c), cellCenterY(cell.r), blockerLight)
        }
        if (result.combo >= 2) {
            comboShown = min(result.combo, Game2048.MAX_MULTIPLIER)
            comboFlashUntil = now() + 1100
        }
        if (particles.isNotEmpty() || floatTexts.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun shake(durationMs: Long, magnitude: Float) {
        shakeUntil = now() + durationMs
        shakeMagnitude = magnitude
    }

    private fun burst(x: Float, y: Float, color: Int, count: Int, big: Boolean = false) {
        repeat(count) {
            val angle = Random.nextFloat() * (2 * Math.PI).toFloat()
            val speed = cellSize * (if (big) 4f else 2.5f) * (0.4f + Random.nextFloat())
            particles.add(
                Particle(
                    x, y,
                    speed * cos(angle), speed * sin(angle),
                    450f, 450f, color,
                    cellSize * (0.04f + Random.nextFloat() * 0.06f)
                )
            )
        }
    }

    /** Radiating cracked-glass style shards for smashes/explosions. */
    private fun shatter(x: Float, y: Float, color: Int) {
        val shardCount = 10
        repeat(shardCount) { i ->
            val angle = (2 * Math.PI * i / shardCount).toFloat() + Random.nextFloat() * 0.3f
            val speed = cellSize * (3.2f + Random.nextFloat() * 1.5f)
            particles.add(
                Particle(
                    x, y,
                    speed * cos(angle), speed * sin(angle),
                    350f, 350f, color,
                    cellSize * 0.18f,
                    shard = true, angle = Math.toDegrees(angle.toDouble()).toFloat()
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
                (Random.nextFloat() - 0.5f) * cellSize * 0.12f * shakeMagnitude,
                (Random.nextFloat() - 0.5f) * cellSize * 0.12f * shakeMagnitude
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
        val anims = if (progress < 1f) activeAnims else null
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
                    val slide = progress.coerceAtMost(1f)
                    val from = anim.fromCells[0]
                    val x = lerp(cellX(from.c), cellX(c), slide)
                    val y = lerp(cellY(from.r), cellY(r), slide)
                    drawTile(canvas, tile, x, y, 1f)
                }
            }
        }

        drawHoverHighlight(canvas, time)
        drawHammerFrame(canvas, time)
        drawEffects(canvas, dt)
        drawFullScreenFlash(canvas, time)
        drawComboBanner(canvas, time)
        drawActionBanner(canvas, time)

        val hasBomb = game.board.any { row -> row.any { it?.type == Game2048.TileType.BOMB } }
        if (particles.isNotEmpty() || floatTexts.isNotEmpty() ||
            time < shakeUntil || time < comboFlashUntil || time < flashUntil ||
            time < actionBannerUntil || hammerMode || hasBomb || progress < 1f
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
            Game2048.TileType.NORMAL -> drawNormalTile(canvas, rect, valueOverride ?: tile.value, scale)
            Game2048.TileType.BLOCKER -> drawBlockerTile(canvas, rect, tile)
            Game2048.TileType.BOMB -> drawBombTile(canvas, rect, tile)
        }
    }

    private fun drawNormalTile(canvas: Canvas, rect: RectF, value: Int, scale: Float) {
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

    /** Rock slab wrapped in yellow/black hazard tape with a padlock — reads instantly as "trap". */
    private fun drawBlockerTile(canvas: Canvas, rect: RectF, tile: Game2048.Tile) {
        cellPaint.color = blockerDark
        canvas.drawRoundRect(rect, gap, gap, cellPaint)
        val inset = rect.width() * 0.09f
        cellPaint.color = blockerLight
        canvas.drawRoundRect(
            RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset * 1.5f),
            gap * 0.7f, gap * 0.7f, cellPaint
        )

        // Hazard tape border: two dashed strokes offset from each other.
        val stripe = rect.width() * 0.11f
        val inner = RectF(
            rect.left + stripe / 2f, rect.top + stripe / 2f,
            rect.right - stripe / 2f, rect.bottom - stripe / 2f
        )
        strokePaint.strokeWidth = stripe
        strokePaint.color = hazardYellow
        strokePaint.pathEffect = DashPathEffect(floatArrayOf(stripe, stripe), 0f)
        canvas.drawRoundRect(inner, gap, gap, strokePaint)
        strokePaint.color = Color.parseColor("#1E1B24")
        strokePaint.pathEffect = DashPathEffect(floatArrayOf(stripe, stripe), stripe)
        canvas.drawRoundRect(inner, gap, gap, strokePaint)
        strokePaint.pathEffect = null

        // Padlock icon: body + shackle + keyhole.
        val lockW = rect.width() * 0.32f
        val lockH = lockW * 0.82f
        val lockLeft = rect.centerX() - lockW / 2f
        val lockTop = rect.centerY() - lockH / 2f + rect.height() * 0.08f
        val lockRect = RectF(lockLeft, lockTop, lockLeft + lockW, lockTop + lockH)
        cellPaint.color = Color.parseColor("#2E2B33")
        canvas.drawRoundRect(lockRect, gap * 0.5f, gap * 0.5f, cellPaint)

        strokePaint.color = Color.parseColor("#2E2B33")
        strokePaint.strokeWidth = lockW * 0.17f
        val shackleRect = RectF(
            lockRect.centerX() - lockW * 0.26f, lockTop - lockH * 0.62f,
            lockRect.centerX() + lockW * 0.26f, lockTop + lockH * 0.18f
        )
        canvas.drawArc(shackleRect, 180f, 180f, false, strokePaint)

        cellPaint.color = blockerLight
        canvas.drawCircle(lockRect.centerX(), lockRect.centerY() - lockH * 0.05f, lockW * 0.09f, cellPaint)

        // Countdown badge, top-right corner.
        val badgeR = rect.width() * 0.155f
        val badgeCx = rect.right - badgeR * 1.3f
        val badgeCy = rect.top + badgeR * 1.3f
        cellPaint.color = hazardYellow
        canvas.drawCircle(badgeCx, badgeCy, badgeR, cellPaint)
        strokePaint.color = Color.parseColor("#1E1B24")
        strokePaint.strokeWidth = badgeR * 0.16f
        canvas.drawCircle(badgeCx, badgeCy, badgeR * 0.92f, strokePaint)
        textPaint.color = Color.parseColor("#2E2B33")
        textPaint.textSize = badgeR * 1.15f
        canvas.drawText(tile.timer.toString(), badgeCx, textBaseline(badgeCy), textPaint)
    }

    /** Round bomb body with a lit fuse; glows and speeds up its flicker as the fuse runs down. */
    private fun drawBombTile(canvas: Canvas, rect: RectF, tile: Game2048.Tile) {
        cellPaint.color = bombTileBg
        canvas.drawRoundRect(rect, gap, gap, cellPaint)

        val urgent = tile.timer <= 3
        val t = now()
        val pulse = 1f + (if (urgent) 0.12f else 0.05f) *
            sin(t / (if (urgent) 90.0 else 220.0)).toFloat()
        val glowColor = if (urgent) Color.parseColor("#FF3B30") else Color.parseColor("#FF8C42")

        val bodyCx = rect.centerX()
        val bodyCy = rect.centerY() + rect.height() * 0.08f
        val bodyR = rect.width() * 0.30f * pulse

        cellPaint.color = glowColor
        cellPaint.setShadowLayer(rect.width() * 0.22f, 0f, 0f, glowColor)
        canvas.drawCircle(bodyCx, bodyCy, bodyR, cellPaint)
        cellPaint.clearShadowLayer()

        cellPaint.color = bombShellColor
        canvas.drawCircle(bodyCx, bodyCy, bodyR * 0.9f, cellPaint)
        cellPaint.color = bombShineColor
        canvas.drawCircle(bodyCx - bodyR * 0.32f, bodyCy - bodyR * 0.32f, bodyR * 0.22f, cellPaint)

        // Fuse curling up-right from the body.
        strokePaint.pathEffect = null
        strokePaint.color = Color.parseColor("#C9A66B")
        strokePaint.strokeWidth = rect.width() * 0.045f
        val fuseStartX = bodyCx + bodyR * 0.55f
        val fuseStartY = bodyCy - bodyR * 0.78f
        val tipX = fuseStartX + bodyR * 0.15f
        val tipY = fuseStartY - bodyR * 1.25f
        val fusePath = Path().apply {
            moveTo(fuseStartX, fuseStartY)
            quadTo(fuseStartX + bodyR * 0.45f, fuseStartY - bodyR * 0.7f, tipX, tipY)
        }
        canvas.drawPath(fusePath, strokePaint)

        // Flickering spark.
        val flicker = 0.75f + 0.25f * sin(t / 60.0).toFloat()
        val sparkColor = if ((t / 90) % 2 == 0L) Color.parseColor("#FFD23F") else Color.parseColor("#FF6B35")
        cellPaint.color = sparkColor
        cellPaint.setShadowLayer(rect.width() * 0.16f, 0f, 0f, sparkColor)
        canvas.drawCircle(tipX, tipY, bodyR * 0.24f * flicker, cellPaint)
        cellPaint.clearShadowLayer()

        textPaint.color = Color.WHITE
        textPaint.textSize = bodyR * 0.95f
        canvas.drawText(tile.timer.toString(), bodyCx, textBaseline(bodyCy), textPaint)
    }

    private fun textBaseline(centerY: Float) =
        centerY - (textPaint.descent() + textPaint.ascent()) / 2f

    /** While hammer mode is armed: pulsing "marching ants" border around the whole board. */
    private fun drawHammerFrame(canvas: Canvas, time: Long) {
        if (!hammerMode) return
        val dashLen = gap * 3f
        strokePaint.strokeWidth = gap * 1.4f
        strokePaint.color = hazardYellow
        strokePaint.pathEffect = DashPathEffect(
            floatArrayOf(dashLen, gap * 2f),
            (time % 900L) / 900f * (dashLen + gap * 2f)
        )
        val half = strokePaint.strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(boardLeft + half, boardTop + half, boardLeft + boardSide - half, boardTop + boardSide - half),
            gap * 1.6f, gap * 1.6f, strokePaint
        )
        strokePaint.pathEffect = null
    }

    private fun drawHoverHighlight(canvas: Canvas, time: Long) {
        if (!hammerMode || hoverR < 0 || hoverC < 0) return
        val rect = cellRect(cellX(hoverC), cellY(hoverR), 1f)
        val pulse = 1f + 0.06f * sin(time / 70.0).toFloat()
        val scaled = cellRect(cellX(hoverC), cellY(hoverR), pulse)
        strokePaint.pathEffect = null
        strokePaint.color = hazardYellow
        strokePaint.strokeWidth = gap
        canvas.drawRoundRect(scaled, gap, gap, strokePaint)
        overlayPaint.color = hazardYellow
        overlayPaint.alpha = 60
        canvas.drawRoundRect(rect, gap, gap, overlayPaint)
    }

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
            val fade = p.life / p.maxLife
            if (p.shard) {
                canvas.save()
                canvas.translate(p.x, p.y)
                canvas.rotate(p.angle)
                val len = p.size * fade
                canvas.drawRect(-len / 6f, -len / 2f, len / 6f, len / 2f, fxPaint)
                canvas.restore()
            } else {
                canvas.drawCircle(p.x, p.y, p.size * fade, fxPaint)
            }
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

    /** Quick full-board color flash for explosions (orange) and undo (cyan). */
    private fun drawFullScreenFlash(canvas: Canvas, time: Long) {
        if (time >= flashUntil) return
        val remain = (flashUntil - time) / 260f
        overlayPaint.color = flashColor
        overlayPaint.alpha = (140 * remain.coerceIn(0f, 1f)).toInt()
        canvas.drawRoundRect(
            RectF(boardLeft, boardTop, boardLeft + boardSide, boardTop + boardSide),
            gap * 1.6f, gap * 1.6f, overlayPaint
        )
    }

    private fun drawComboBanner(canvas: Canvas, time: Long) {
        if (time >= comboFlashUntil) return
        val remain = (comboFlashUntil - time) / 1100f
        val pulse = 1f + 0.12f * sin(time / 90.0).toFloat()
        fxTextPaint.color = hazardYellow
        fxTextPaint.alpha = (255 * remain.coerceAtMost(0.6f) / 0.6f).toInt()
        fxTextPaint.textSize = cellSize * 0.42f * pulse
        canvas.drawText(
            "COMBO ×$comboShown",
            width / 2f,
            boardTop + boardSide * 0.42f,
            fxTextPaint
        )
    }

    private fun drawActionBanner(canvas: Canvas, time: Long) {
        if (time >= actionBannerUntil) return
        val remain = (actionBannerUntil - time) / 900f
        val pop = if (remain > 0.75f) 1f + (1f - remain) * 0.8f else 1f
        fxTextPaint.color = actionBannerColor
        fxTextPaint.alpha = (255 * remain.coerceAtMost(0.7f) / 0.7f).toInt()
        fxTextPaint.textSize = cellSize * 0.34f * pop
        canvas.drawText(actionBannerText, width / 2f, boardTop + boardSide * 0.42f, fxTextPaint)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
