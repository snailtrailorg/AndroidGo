package org.snailtrail.androidgo.game

/**
 * Parse kata-raw-nn output, extracting ownership map and score lead.
 * Ownership values: 0=black, 1=white, ~0.5=unsettled (not included).
 * Returns pair of (ownership map, score lead from white's perspective).
 */
fun parseKataOwnership(raw: String, boardSize: Int): Pair<Map<Pair<Int, Int>, StoneColor>, Float> {
    val ownership = mutableMapOf<Pair<Int, Int>, StoneColor>()
    var scoreLead = 0f
    val lines = raw.lines()
    var inOwnership = false
    var row = 0

    for (line in lines) {
        if (line.startsWith("whiteLead ")) {
            scoreLead = line.substringAfter("whiteLead ").trim().toFloatOrNull() ?: 0f
        }
        if (inOwnership) {
            val vals = line.trim().split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
            for ((col, v) in vals.withIndex()) {
                if (col < boardSize && row < boardSize) {
                    if (v < 0.30f) ownership[row to col] = StoneColor.Black
                    else if (v > 0.70f) ownership[row to col] = StoneColor.White
                }
            }
            row++
            if (row >= boardSize) inOwnership = false
        }
        if (line.startsWith("whiteOwnership")) {
            inOwnership = true; row = 0
        }
    }
    return ownership to scoreLead
}
