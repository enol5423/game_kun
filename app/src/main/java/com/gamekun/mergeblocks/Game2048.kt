package com.gamekun.mergeblocks

import kotlin.random.Random

/**
 * Pure 4x4 merge-game logic. grid[row][col] holds tile values (0 = empty).
 */
class Game2048 {

    companion object {
        const val SIZE = 4
    }

    var grid = Array(SIZE) { IntArray(SIZE) }
        private set
    var score = 0
        private set
    var isGameOver = false
        private set

    init {
        reset()
    }

    fun reset() {
        grid = Array(SIZE) { IntArray(SIZE) }
        score = 0
        isGameOver = false
        spawnTile()
        spawnTile()
    }

    /** Removes all of the smallest tiles on the board so play can continue. */
    fun revive() {
        val smallest = grid.flatMap { it.toList() }.filter { it > 0 }.minOrNull() ?: return
        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                if (grid[r][c] == smallest) grid[r][c] = 0
            }
        }
        isGameOver = false
        if (grid.all { row -> row.all { it == 0 } }) spawnTile()
    }

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    /** Returns true if the move changed the board. */
    fun move(dir: Direction): Boolean {
        if (isGameOver) return false
        val before = grid.map { it.clone() }

        when (dir) {
            Direction.LEFT -> for (r in 0 until SIZE) grid[r] = slideAndMerge(grid[r])
            Direction.RIGHT -> for (r in 0 until SIZE) {
                grid[r] = slideAndMerge(grid[r].reversedArray()).reversedArray()
            }
            Direction.UP -> for (c in 0 until SIZE) setColumn(c, slideAndMerge(getColumn(c)))
            Direction.DOWN -> for (c in 0 until SIZE) {
                setColumn(c, slideAndMerge(getColumn(c).reversedArray()).reversedArray())
            }
        }

        val moved = before.indices.any { r -> !before[r].contentEquals(grid[r]) }
        if (moved) {
            spawnTile()
            if (!hasMovesLeft()) isGameOver = true
        }
        return moved
    }

    private fun slideAndMerge(line: IntArray): IntArray {
        val tiles = line.filter { it != 0 }.toMutableList()
        var i = 0
        while (i < tiles.size - 1) {
            if (tiles[i] == tiles[i + 1]) {
                tiles[i] *= 2
                score += tiles[i]
                tiles.removeAt(i + 1)
            }
            i++
        }
        return IntArray(SIZE) { if (it < tiles.size) tiles[it] else 0 }
    }

    private fun getColumn(c: Int) = IntArray(SIZE) { r -> grid[r][c] }

    private fun setColumn(c: Int, col: IntArray) {
        for (r in 0 until SIZE) grid[r][c] = col[r]
    }

    private fun spawnTile() {
        val empty = ArrayList<Pair<Int, Int>>()
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            if (grid[r][c] == 0) empty.add(r to c)
        }
        if (empty.isEmpty()) return
        val (r, c) = empty[Random.nextInt(empty.size)]
        grid[r][c] = if (Random.nextInt(10) == 0) 4 else 2
    }

    private fun hasMovesLeft(): Boolean {
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            val v = grid[r][c]
            if (v == 0) return true
            if (c + 1 < SIZE && grid[r][c + 1] == v) return true
            if (r + 1 < SIZE && grid[r + 1][c] == v) return true
        }
        return false
    }
}
