package org.snailtrail.androidgo.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class StoneColor { Black, White }

data class Stone(val row: Int, val col: Int, val color: StoneColor)

data class Move(val stone: Stone, val capturedStones: List<Stone> = emptyList(), val isPass: Boolean = false)

data class BoardState(
    val size: Int = 19,
    val stones: Map<Pair<Int, Int>, StoneColor> = emptyMap(),
    val currentPlayer: StoneColor = StoneColor.Black,
    val moveHistory: List<Move> = emptyList(),
    val redoStack: List<Move> = emptyList(),
    val lastMove: Pair<Int, Int>? = null,
    val komi: Float = 3.75f,
    val handicap: Int = 0,
    val gameOver: Boolean = false,
    val consecutivePasses: Int = 0
)

class GoGame(initialSize: Int = 19) {

    private val _state = MutableStateFlow(BoardState(size = initialSize))
    val state: StateFlow<BoardState> = _state.asStateFlow()

    fun placeStone(row: Int, col: Int): Boolean {
        val s = _state.value
        if (s.gameOver) return false
        if (row !in 0 until s.size || col !in 0 until s.size) return false
        if (s.stones.containsKey(row to col)) return false

        val newStone = Stone(row, col, s.currentPlayer)
        val newStones = s.stones.toMutableMap()
        newStones[row to col] = s.currentPlayer

        // Check for captures
        val captured = findCapturedStones(newStones, s.currentPlayer.opponent(), s.size)
        captured.forEach { pos -> newStones.remove(pos) }

        // Basic ko: can't recapture immediately to the same position
        if (s.lastMove != null && captured.size == 1 && s.moveHistory.isNotEmpty()) {
            val prevMove = s.moveHistory.last()
            if (prevMove.capturedStones.size == 1 &&
                prevMove.capturedStones.first().let { it.row to it.col } == (row to col) &&
                prevMove.stone.let { it.row to it.col } == captured.first()
            ) {
                return false // ko rule
            }
        }

        // Suicide check: after placing, if the placed stone has no liberties and captures nothing
        if (captured.isEmpty() && !hasLiberty(newStones, row, col, s.size)) {
            return false // suicide
        }

        val move = Move(newStone, captured.map { Stone(it.first, it.second, s.currentPlayer.opponent()) })

        _state.update {
            it.copy(
                stones = newStones,
                currentPlayer = s.currentPlayer.opponent(),
                moveHistory = it.moveHistory + move,
                redoStack = emptyList(),
                lastMove = row to col,
                consecutivePasses = 0
            )
        }
        return true
    }

    fun pass() {
        _state.update { s ->
            val newPasses = s.consecutivePasses + 1
            val passMove = Move(Stone(-1, -1, s.currentPlayer), isPass = true)
            s.copy(
                currentPlayer = s.currentPlayer.opponent(),
                consecutivePasses = newPasses,
                gameOver = newPasses >= 2,
                lastMove = null,
                moveHistory = s.moveHistory + passMove,
                redoStack = emptyList()
            )
        }
    }

    fun undo() {
        _state.update { s ->
            if (s.moveHistory.isEmpty()) return@update s
            val lastMove = s.moveHistory.last()
            val newHistory = s.moveHistory.dropLast(1)
            val newStones = s.stones.toMutableMap()

            if (!lastMove.isPass) {
                newStones.remove(lastMove.stone.row to lastMove.stone.col)
                lastMove.capturedStones.forEach { cap ->
                    newStones[cap.row to cap.col] = cap.color
                }
            }

            val prevLast = newHistory.lastOrNull()
                ?.takeUnless { it.isPass }?.stone?.let { it.row to it.col }
            // Count consecutive passes in newHistory to restore state
            var passes = 0
            for (i in newHistory.indices.reversed()) {
                if (newHistory[i].isPass) passes++ else break
            }
            s.copy(
                stones = newStones,
                currentPlayer = s.currentPlayer.opponent(),
                moveHistory = newHistory,
                redoStack = s.redoStack + lastMove,
                lastMove = prevLast,
                consecutivePasses = passes,
                gameOver = false
            )
        }
    }

    fun redo(): Boolean {
        val s = _state.value
        if (s.redoStack.isEmpty()) return false
        val move = s.redoStack.last()
        val newStones = s.stones.toMutableMap()

        if (!move.isPass) {
            newStones[move.stone.row to move.stone.col] = move.stone.color
            val captured = findCapturedStones(newStones, move.stone.color.opponent(), s.size)
            captured.forEach { pos -> newStones.remove(pos) }
        }

        _state.update { it.copy(
            stones = newStones,
            currentPlayer = it.currentPlayer.opponent(),
            moveHistory = it.moveHistory + move,
            redoStack = it.redoStack.dropLast(1),
            lastMove = if (move.isPass) null else (move.stone.row to move.stone.col),
            consecutivePasses = if (move.isPass) it.consecutivePasses + 1 else 0,
            gameOver = move.isPass && it.consecutivePasses + 1 >= 2
        ) }
        return true
    }

    fun reset(size: Int = _state.value.size) {
        _state.value = BoardState(size = size)
    }

    fun setBoardSize(size: Int) {
        require(size in 9..19) { "Board size must be 9-19" }
        reset(size)
    }

    fun setHandicap(n: Int) {
        require(n in 0..9) { "Handicap must be 0-9" }
        if (n == 0) {
            _state.value = _state.value.copy(handicap = 0, currentPlayer = StoneColor.Black)
            return
        }
        val sz = _state.value.size
        val edge = if (sz == 9) 2 else 3
        val far = sz - 1 - edge
        val center = sz / 2
        // Standard handicap placement order: corners → center → edges
        val allPlacements = listOf(
            far to far,           // 1: upper right
            edge to edge,         // 2: lower left
            edge to far,          // 3: lower right
            far to edge,          // 4: upper left
            center to center,     // 5: center
            center to edge,       // 6: left edge
            center to far,        // 7: right edge
            far to center,        // 8: top edge
            edge to center,       // 9: bottom edge
        )
        val stones = mutableMapOf<Pair<Int, Int>, StoneColor>()
        for (i in 0 until n) {
            stones[allPlacements[i]] = StoneColor.Black  // weaker player takes Black
        }

        // Chinese rule: handicap → Black gets stones, White plays first, no komi
        _state.value = _state.value.copy(
            stones = stones,
            handicap = n,
            currentPlayer = StoneColor.White,
            moveHistory = emptyList(),
            redoStack = emptyList(),
            lastMove = null,
            consecutivePasses = 0,
            gameOver = false
        )
    }

    fun setKomi(komi: Float) {
        _state.update { it.copy(komi = komi) }
    }

    /**
     * Count territory using Chinese area scoring (数子法).
     * @param deadStones positions that are considered dead — flood fill treats
     *   them as empty, but they are NOT counted as territory (a stone sits there).
     *   Their positions ARE added to territoryMap so the UI can overlay a marker.
     */
    fun countTerritory(deadStones: Set<Pair<Int, Int>> = emptySet()): TerritoryScore {
        val s = _state.value
        val size = s.size
        val visited = Array(size) { BooleanArray(size) }
        var blackTerritory = 0
        var whiteTerritory = 0
        val territoryMap = mutableMapOf<Pair<Int, Int>, StoneColor>()

        for (r in 0 until size) {
            for (c in 0 until size) {
                val pos = r to c
                if (visited[r][c]) continue
                // Skip live stones (dead stones are treated as empty)
                val stone = s.stones[pos]
                if (stone != null && pos !in deadStones) continue

                // Flood fill region (empty points + dead stones)
                val region = mutableListOf<Pair<Int, Int>>()
                val borders = mutableSetOf<StoneColor>()
                val queue = ArrayDeque<Pair<Int, Int>>()
                queue.add(pos)
                visited[r][c] = true

                while (queue.isNotEmpty()) {
                    val (cr, cc) = queue.removeFirst()
                    region.add(cr to cc)

                    for ((dr, dc) in DIRECTIONS) {
                        val nr = cr + dr
                        val nc = cc + dc
                        if (nr !in 0 until size || nc !in 0 until size) continue
                        if (visited[nr][nc]) continue
                        val adj = s.stones[nr to nc]
                        if (adj != null && (nr to nc) !in deadStones) {
                            borders.add(adj)
                        } else {
                            visited[nr][nc] = true
                            queue.add(nr to nc)
                        }
                    }
                }

                // Region belongs to a player iff it borders only one color
                if (borders.size == 1) {
                    val owner = borders.first()
                    // Chinese rules: dead stones are removed, their positions become territory
                    val territoryCount = region.size
                    when (owner) {
                        StoneColor.Black -> blackTerritory += territoryCount
                        StoneColor.White -> whiteTerritory += territoryCount
                    }
                    for (point in region) {
                        territoryMap[point] = owner
                    }
                }
            }
        }

        // Chinese area scoring: live stones + territory
        val blackStones = s.stones.count { it.value == StoneColor.Black && (it.key !in deadStones) }
        val whiteStones = s.stones.count { it.value == StoneColor.White && (it.key !in deadStones) }
        // Komi: 3.75 for even game; handicap → 还子 = handicap/2
        val komi = if (s.handicap == 0) 3.75f else (s.handicap / 2f)
        val blackScore = blackStones + blackTerritory
        val whiteScore = whiteStones + whiteTerritory + komi

        return TerritoryScore(
            blackStones = blackStones,
            whiteStones = whiteStones,
            blackTerritory = blackTerritory,
            whiteTerritory = whiteTerritory,
            blackScore = blackScore.toFloat(),
            whiteScore = whiteScore.toFloat(),
            komi = komi,
            territoryMap = territoryMap
        )
    }

    companion object {
        private val DIRECTIONS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        fun hasLiberty(
            stones: Map<Pair<Int, Int>, StoneColor>,
            row: Int, col: Int, size: Int
        ): Boolean {
            val color = stones[row to col] ?: return true
            val visited = mutableSetOf<Pair<Int, Int>>()
            return checkLiberty(stones, row, col, color, size, visited)
        }

        private fun checkLiberty(
            stones: Map<Pair<Int, Int>, StoneColor>,
            row: Int, col: Int, color: StoneColor, size: Int,
            visited: MutableSet<Pair<Int, Int>>
        ): Boolean {
            val pos = row to col
            if (pos in visited) return false
            visited.add(pos)

            for ((dr, dc) in DIRECTIONS) {
                val nr = row + dr
                val nc = col + dc
                if (nr !in 0 until size || nc !in 0 until size) continue
                val adj = stones[nr to nc]
                if (adj == null) return true // liberty found
                if (adj == color && checkLiberty(stones, nr, nc, color, size, visited)) {
                    return true
                }
            }
            return false
        }

        fun findCapturedStones(
            stones: Map<Pair<Int, Int>, StoneColor>,
            color: StoneColor,
            size: Int
        ): Set<Pair<Int, Int>> {
            val captured = mutableSetOf<Pair<Int, Int>>()
            val checked = mutableSetOf<Pair<Int, Int>>()

            for ((pos, c) in stones) {
                if (c != color || pos in checked) continue
                val group = mutableSetOf<Pair<Int, Int>>()
                if (!findGroupLiberty(stones, pos.first, pos.second, color, size, group, checked)) {
                    captured.addAll(group)
                }
            }
            return captured
        }

        private fun findGroupLiberty(
            stones: Map<Pair<Int, Int>, StoneColor>,
            row: Int, col: Int, color: StoneColor, size: Int,
            group: MutableSet<Pair<Int, Int>>,
            checked: MutableSet<Pair<Int, Int>>
        ): Boolean {
            val pos = row to col
            if (pos in checked) return false
            checked.add(pos)
            group.add(pos)

            var hasLib = false
            for ((dr, dc) in DIRECTIONS) {
                val nr = row + dr
                val nc = col + dc
                if (nr !in 0 until size || nc !in 0 until size) continue
                val adj = stones[nr to nc]
                when {
                    adj == null -> hasLib = true
                    adj == color -> {
                        if (findGroupLiberty(stones, nr, nc, color, size, group, checked)) {
                            hasLib = true
                        }
                    }
                }
            }
            return hasLib
        }
    }
}

data class TerritoryScore(
    val blackStones: Int,
    val whiteStones: Int,
    val blackTerritory: Int,
    val whiteTerritory: Int,
    val blackScore: Float,
    val whiteScore: Float,
    val komi: Float,
    val territoryMap: Map<Pair<Int, Int>, StoneColor> = emptyMap()
)

fun StoneColor.opponent() = if (this == StoneColor.Black) StoneColor.White else StoneColor.Black
