package org.snailtrail.androidgo.game

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SgfUtil {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun exportToFile(state: BoardState, file: File) {
        val sb = StringBuilder()
        sb.append("(;GM[1]FF[4]AP[${SgfConstants.APP_NAME}]\n")
        sb.append("SZ[${state.size}]KM[${state.komi}]HA[${state.handicap}]\n")
        sb.append("DT[${dateFormat.format(Date())}]\n")

        if (state.handicap > 0) {
            for ((pos, _) in state.stones) {
                sb.append("AB[${boardPosToGtp(pos.first, pos.second, state.size)}]")
            }
            sb.append("\n")
        }

        var blackTurn = true
        for (move in state.moveHistory) {
            val color = if (blackTurn) "B" else "W"
            when {
                move.capturedStones.isNotEmpty() -> {
                    // Regular move
                    sb.append(";${color}[${boardPosToGtp(move.stone.row, move.stone.col, state.size)}]")
                    // Add captured stones as comment
                    val caps = move.capturedStones.joinToString(",") {
                        boardPosToGtp(it.row, it.col, state.size)
                    }
                    if (caps.isNotEmpty()) sb.append("C[captured: $caps]")
                }
                else -> {
                    sb.append(";${color}[${boardPosToGtp(move.stone.row, move.stone.col, state.size)}]")
                }
            }
            sb.append("\n")
            blackTurn = !blackTurn
        }

        sb.append(")\n")
        file.writeText(sb.toString())
    }

    fun parseFromFile(file: File): ParsedSgf? {
        val content = file.readText().replace(Regex("\\s+"), " ")
        val openParen = content.indexOf('(')
        if (openParen < 0) return null

        val result = ParsedSgf()
        var i = openParen + 1

        while (i < content.length) {
            when {
                content[i] == ';' -> {
                    // Node: extract properties
                    i++
                    val node = parseNode(content, i)
                    result.nodes.add(node)
                    i = node.endIndex
                }
                content[i] == '(' -> {
                    // Variation — skip for now
                    i = skipVariation(content, i)
                }
                content[i] == ')' -> break
                else -> i++
            }
        }

        // Extract root properties
        for (node in result.nodes) {
            for ((key, value) in node.properties) {
                when (key) {
                    "SZ" -> result.boardSize = value.toIntOrNull() ?: 19
                    "KM" -> result.komi = value.toFloatOrNull() ?: 6.5f
                    "HA" -> result.handicap = value.toIntOrNull() ?: 0
                    "AB" -> gtpToBoardPos(value, result.boardSize)?.let { result.handicapStones.add(it) }
                }
            }
        }

        // Extract moves
        var blackTurn = true
        for (node in result.nodes) {
            val moveVal = when {
                blackTurn -> node.properties["B"]
                else -> node.properties["W"]
            }
            if (moveVal != null && moveVal.isNotEmpty()) {
                val pos = gtpToBoardPos(moveVal, result.boardSize)
                if (pos.first >= 0) result.moves.add(pos)
            }
            blackTurn = !blackTurn
        }

        return result
    }

    private fun parseNode(content: String, start: Int): SgfNode {
        val properties = mutableMapOf<String, String>()
        var i = start

        while (i < content.length) {
            val c = content[i]
            when {
                c == ';' || c == '(' || c == ')' -> break
                c.isUpperCase() -> {
                    // Key
                    val keyStart = i
                    i++
                    while (i < content.length && content[i].isUpperCase()) i++
                    val key = content.substring(keyStart, i)

                    // Value(s) — each in [...]
                    val values = mutableListOf<String>()
                    while (i < content.length && content[i] == '[') {
                        i++
                        val valStart = i
                        var depth = 1
                        while (i < content.length && depth > 0) {
                            when (content[i]) {
                                ']' -> depth--
                                '[' -> depth++
                            }
                            if (depth > 0) i++
                        }
                        values.add(content.substring(valStart, i))
                        i++ // skip ']'
                    }
                    properties[key] = values.joinToString(",")
                }
                else -> i++
            }
        }

        return SgfNode(properties, i)
    }

    private fun skipVariation(content: String, start: Int): Int {
        var depth = 1
        var i = start + 1
        while (i < content.length && depth > 0) {
            when (content[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            i++
        }
        return i
    }
}

data class ParsedSgf(
    var boardSize: Int = 19,
    var komi: Float = 6.5f,
    var handicap: Int = 0,
    val handicapStones: MutableList<Pair<Int, Int>> = mutableListOf(),
    val moves: MutableList<Pair<Int, Int>> = mutableListOf(),
    val nodes: MutableList<SgfNode> = mutableListOf()
)

data class SgfNode(
    val properties: Map<String, String>,
    val endIndex: Int
)
