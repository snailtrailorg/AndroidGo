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
            if (move.isPass) {
                sb.append(";${color}[]\n")
            } else if (move.capturedStones.isNotEmpty()) {
                sb.append(";${color}[${boardPosToGtp(move.stone.row, move.stone.col, state.size)}]")
                val caps = move.capturedStones.joinToString(",") {
                    boardPosToGtp(it.row, it.col, state.size)
                }
                if (caps.isNotEmpty()) sb.append("C[captured: $caps]")
                sb.append("\n")
            } else {
                sb.append(";${color}[${boardPosToGtp(move.stone.row, move.stone.col, state.size)}]\n")
            }
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
                    // Variation — attach to the last parsed node
                    val (varNodes, endIdx) = parseVariation(content, i)
                    if (result.nodes.isNotEmpty()) {
                        val lastNode = result.nodes.last()
                        result.nodes[result.nodes.size - 1] = lastNode.copy(
                            variations = lastNode.variations + listOf(varNodes)
                        )
                    }
                    i = endIdx
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

        // Extract moves (skip root node — it has header properties only)
        var blackTurn = true
        for (node in result.nodes.drop(1)) {
            val moveVal = when {
                blackTurn -> node.properties["B"]
                else -> node.properties["W"]
            }
            // Pass move: empty string, "tt", or "pass" → (-1, -1)
            val isPass = moveVal == null || moveVal.isEmpty() || moveVal == "tt" || moveVal == "pass"
            if (isPass) {
                result.moves.add(-1 to -1)
            } else {
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

    private fun parseVariation(content: String, start: Int): Pair<List<SgfNode>, Int> {
        val nodes = mutableListOf<SgfNode>()
        var depth = 1
        var i = start + 1
        while (i < content.length && depth > 0) {
            when {
                content[i] == ';' -> {
                    i++
                    val node = parseNode(content, i)
                    // Sub-variations inside this node
                    var endIdx = node.endIndex
                    while (endIdx < content.length && content[endIdx] == '(') {
                        val (subNodes, subEnd) = parseVariation(content, endIdx)
                        // Add sub-variation moves to the node's variations
                        i = subEnd
                        endIdx = subEnd
                    }
                    nodes.add(node)
                    i = endIdx
                }
                content[i] == '(' -> depth++
                content[i] == ')' -> depth--
            }
            if (depth > 0) i++
        }
        return nodes to i
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
    val endIndex: Int,
    val variations: List<List<SgfNode>> = emptyList()
)
