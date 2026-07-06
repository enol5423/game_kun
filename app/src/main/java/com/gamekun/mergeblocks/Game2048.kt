package com.gamekun.mergeblocks

import kotlin.math.min
import kotlin.random.Random

/**
 * 4x4 merge-game engine with combo scoring, trap tiles and boosters.
 *
 * Trap tiles:
 *  - BLOCKER: an immovable stone that splits the board; crumbles after [BLOCKER_LIFE] moves.
 *  - BOMB: slides like a tile but never merges; its fuse ticks down each move and at zero
 *    it explodes, destroying itself and its orthogonal neighbours.
 *
 * Boosters (inventory is managed by the caller):
 *  - undo(): restores the state before the last action.
 *  - smash(r, c): removes any single tile (the only way to kill a blocker early).
 *  - shuffle(): randomly repositions every movable tile.
 *
 * Scoring: each move that merges extends a combo streak; merge points are
 * multiplied by min(streak, MAX_MULTIPLIER).
 */
class Game2048 {

    companion object {
        const val SIZE = 4
        const val MAX_MULTIPLIER = 4
        const val BLOCKER_LIFE = 10
        const val BOMB_FUSE = 8
        private const val MAX_BLOCKERS = 2
    }

    enum class TileType { NORMAL, BLOCKER, BOMB }
    enum class Direction { UP, DOWN, LEFT, RIGHT }

    class Tile(
        val id: Int,
        var value: Int,     // 0 for BLOCKER / BOMB
        val type: TileType,
        var timer: Int = 0  // BLOCKER: moves until it crumbles; BOMB: fuse
    )

    data class Cell(val r: Int, val c: Int)

    /** How a tile in the post-move board should be animated. */
    class TileAnim(
        val fromCells: List<Cell>,
        val isNew: Boolean = false,
        val isMerge: Boolean = false
    )

    class MoveResult(
        val anims: Map<Int, TileAnim>,
        val merges: List<Triple<Cell, Int, Int>>, // cell, new value, points awarded
        val explosions: List<Cell>,
        val crumbled: List<Cell>,
        val scoreGained: Int,
        val combo: Int
    )

    var board = Array(SIZE) { arrayOfNulls<Tile>(SIZE) }
        private set
    var score = 0
        private set
    var isGameOver = false
        private set
    var combo = 0
        private set

    private var moveCount = 0
    private var nextId = 1
    private var undoState: Snapshot? = null

    val canUndo: Boolean get() = undoState != null

    init {
        reset()
    }

    fun reset() {
        board = Array(SIZE) { arrayOfNulls<Tile>(SIZE) }
        score = 0
        combo = 0
        moveCount = 0
        isGameOver = false
        undoState = null
        spawnNumber()
        spawnNumber()
    }

    // ---------------------------------------------------------------- moves

    /** Returns null if the swipe changed nothing. */
    fun move(dir: Direction): MoveResult? {
        if (isGameOver) return null
        val before = snapshot()

        val anims = HashMap<Int, TileAnim>()
        val mergesRaw = ArrayList<Pair<Cell, Int>>() // cell, new value
        var baseGained = 0
        val newBoard = Array(SIZE) { arrayOfNulls<Tile>(SIZE) }

        // Blockers never move.
        forEachCell { r, c ->
            val t = board[r][c]
            if (t?.type == TileType.BLOCKER) newBoard[r][c] = t
        }

        for (line in linesFor(dir)) {
            var segStart = 0
            var i = 0
            while (i <= line.size) {
                val atEnd = i == line.size ||
                    board[line[i].r][line[i].c]?.type == TileType.BLOCKER
                if (atEnd) {
                    val segPos = line.subList(segStart, i)
                    val tiles = segPos.mapNotNull { p ->
                        board[p.r][p.c]
                            ?.takeIf { it.type != TileType.BLOCKER }
                            ?.let { it to p }
                    }
                    var out = 0
                    var j = 0
                    while (j < tiles.size) {
                        val (tile, from) = tiles[j]
                        val next = tiles.getOrNull(j + 1)
                        val dest = segPos[out]
                        if (tile.type == TileType.NORMAL && next != null &&
                            next.first.type == TileType.NORMAL &&
                            next.first.value == tile.value
                        ) {
                            val newTile = Tile(nextId++, tile.value * 2, TileType.NORMAL)
                            newBoard[dest.r][dest.c] = newTile
                            anims[newTile.id] =
                                TileAnim(listOf(from, next.second), isMerge = true)
                            mergesRaw.add(dest to newTile.value)
                            baseGained += newTile.value
                            j += 2
                        } else {
                            newBoard[dest.r][dest.c] = tile
                            if (dest != from) anims[tile.id] = TileAnim(listOf(from))
                            j++
                        }
                        out++
                    }
                    segStart = i + 1
                }
                i++
            }
        }

        var changed = false
        forEachCell { r, c ->
            if (board[r][c]?.id != newBoard[r][c]?.id) changed = true
        }
        if (!changed) return null

        undoState = before
        board = newBoard
        moveCount++

        combo = if (baseGained > 0) combo + 1 else 0
        val multiplier = if (combo > 0) min(combo, MAX_MULTIPLIER) else 1
        val gained = baseGained * multiplier
        score += gained
        val merges = mergesRaw.map { (cell, value) ->
            Triple(cell, value, value * multiplier)
        }

        // New number tile.
        spawnNumber()?.let { (tile, cell) ->
            anims[tile.id] = TileAnim(listOf(cell), isNew = true)
        }

        // Tick traps.
        val explosions = ArrayList<Cell>()
        val crumbled = ArrayList<Cell>()
        forEachCell { r, c ->
            val t = board[r][c] ?: return@forEachCell
            when (t.type) {
                TileType.BLOCKER -> if (--t.timer <= 0) {
                    board[r][c] = null
                    crumbled.add(Cell(r, c))
                }
                TileType.BOMB -> if (--t.timer <= 0) explosions.add(Cell(r, c))
                TileType.NORMAL -> Unit
            }
        }
        val blasted = ArrayList<Cell>()
        for (bomb in explosions) {
            for ((dr, dc) in listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val r = bomb.r + dr
                val c = bomb.c + dc
                if (r in 0 until SIZE && c in 0 until SIZE && board[r][c] != null) {
                    board[r][c] = null
                    blasted.add(Cell(r, c))
                }
            }
        }

        // Occasionally drop a trap on the board.
        spawnTraps(anims)

        isGameOver = !hasMovesLeft()
        return MoveResult(anims, merges, blasted, crumbled, gained, combo)
    }

    // ------------------------------------------------------------- boosters

    fun undo(): Boolean {
        val s = undoState ?: return false
        restore(s)
        undoState = null
        isGameOver = false
        return true
    }

    /** Removes any single tile — number, blocker or bomb. */
    fun smash(r: Int, c: Int): Boolean {
        if (board[r][c] == null) return false
        undoState = snapshot()
        board[r][c] = null
        isGameOver = !hasMovesLeft()
        return true
    }

    /** Randomly repositions every movable tile (blockers stay put). */
    fun shuffle(): Boolean {
        val movable = ArrayList<Tile>()
        val openCells = ArrayList<Cell>()
        forEachCell { r, c ->
            val t = board[r][c]
            if (t?.type == TileType.BLOCKER) return@forEachCell
            if (t != null) movable.add(t)
            openCells.add(Cell(r, c))
            board[r][c] = null
        }
        if (movable.isEmpty()) return false
        undoState = snapshot()
        openCells.shuffle()
        movable.forEachIndexed { i, tile ->
            val cell = openCells[i]
            board[cell.r][cell.c] = tile
        }
        isGameOver = !hasMovesLeft()
        return true
    }

    /** Post-game-over rescue: clears traps and every smallest number tile. */
    fun revive() {
        val smallest = allTiles()
            .filter { it.first.type == TileType.NORMAL }
            .minOfOrNull { it.first.value }
        forEachCell { r, c ->
            val t = board[r][c] ?: return@forEachCell
            if (t.type != TileType.NORMAL || t.value == smallest) board[r][c] = null
        }
        if (allTiles().isEmpty()) spawnNumber()
        combo = 0
        isGameOver = false
        undoState = null
    }

    // ------------------------------------------------------------ internals

    private fun spawnNumber(): Pair<Tile, Cell>? {
        val empty = emptyCells()
        if (empty.isEmpty()) return null
        val cell = empty.random()
        val tile = Tile(nextId++, if (Random.nextInt(10) == 0) 4 else 2, TileType.NORMAL)
        board[cell.r][cell.c] = tile
        return tile to cell
    }

    private fun spawnTraps(anims: HashMap<Int, TileAnim>) {
        val blockers = allTiles().count { it.first.type == TileType.BLOCKER }
        val bombs = allTiles().count { it.first.type == TileType.BOMB }

        if (moveCount >= 12 && moveCount % 12 == 0 &&
            blockers < MAX_BLOCKERS && emptyCells().size >= 5
        ) {
            val cell = emptyCells().random()
            val tile = Tile(nextId++, 0, TileType.BLOCKER, BLOCKER_LIFE)
            board[cell.r][cell.c] = tile
            anims[tile.id] = TileAnim(listOf(cell), isNew = true)
        }
        if (moveCount >= 24 && moveCount % 22 == 0 &&
            bombs == 0 && emptyCells().size >= 4
        ) {
            val cell = emptyCells().random()
            val tile = Tile(nextId++, 0, TileType.BOMB, BOMB_FUSE)
            board[cell.r][cell.c] = tile
            anims[tile.id] = TileAnim(listOf(cell), isNew = true)
        }
    }

    private fun hasMovesLeft(): Boolean {
        forEachCell { r, c ->
            val t = board[r][c] ?: return true
            if (t.type == TileType.NORMAL) {
                if (c + 1 < SIZE && board[r][c + 1]?.type == TileType.NORMAL &&
                    board[r][c + 1]?.value == t.value
                ) return true
                if (r + 1 < SIZE && board[r + 1][c]?.type == TileType.NORMAL &&
                    board[r + 1][c]?.value == t.value
                ) return true
            }
        }
        return false
    }

    private fun linesFor(dir: Direction): List<List<Cell>> = when (dir) {
        Direction.LEFT -> (0 until SIZE).map { r -> (0 until SIZE).map { c -> Cell(r, c) } }
        Direction.RIGHT -> (0 until SIZE).map { r -> (SIZE - 1 downTo 0).map { c -> Cell(r, c) } }
        Direction.UP -> (0 until SIZE).map { c -> (0 until SIZE).map { r -> Cell(r, c) } }
        Direction.DOWN -> (0 until SIZE).map { c -> (SIZE - 1 downTo 0).map { r -> Cell(r, c) } }
    }

    private inline fun forEachCell(action: (Int, Int) -> Unit) {
        for (r in 0 until SIZE) for (c in 0 until SIZE) action(r, c)
    }

    private fun emptyCells(): List<Cell> {
        val cells = ArrayList<Cell>()
        forEachCell { r, c -> if (board[r][c] == null) cells.add(Cell(r, c)) }
        return cells
    }

    private fun allTiles(): List<Pair<Tile, Cell>> {
        val tiles = ArrayList<Pair<Tile, Cell>>()
        forEachCell { r, c -> board[r][c]?.let { tiles.add(it to Cell(r, c)) } }
        return tiles
    }

    private class Snapshot(
        val board: Array<Array<Tile?>>,
        val score: Int,
        val combo: Int,
        val moveCount: Int
    )

    private fun snapshot() = Snapshot(
        Array(SIZE) { r ->
            Array(SIZE) { c ->
                board[r][c]?.let { Tile(it.id, it.value, it.type, it.timer) }
            }
        },
        score, combo, moveCount
    )

    private fun restore(s: Snapshot) {
        board = Array(SIZE) { r -> Array(SIZE) { c -> s.board[r][c] } }
        score = s.score
        combo = s.combo
        moveCount = s.moveCount
    }
}
