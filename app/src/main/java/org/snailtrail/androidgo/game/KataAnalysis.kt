package org.snailtrail.androidgo.game

/**
 * Result of one kata-analyze sample.
 * scoreLead is from white's perspective (positive = white leads).
 */
data class EvalResult(
    val ownership: Map<Pair<Int, Int>, StoneColor>,
    val whiteWin: Float,
    val scoreLead: Float,
    val scoreError: Float
)

/**
 * Parse one kata-analyze line.
 * The app config uses reportAnalysisWinratesAs=SIDETOMOVE, so winrate,
 * scoreLead and ownership are reported from [currentPlayer]'s perspective.
 * Convert them to stable white-perspective fields for UI display.
 */
fun parseKataAnalyze(line: String, boardSize: Int, currentPlayer: StoneColor): EvalResult? {
    if (!line.startsWith("info ")) return null
    val tokens = line.trim().split("\\s+".toRegex())
    var winrate = Float.NaN
    var scoreLead = Float.NaN
    var scoreStdev = 0f
    val rawOwnership = mutableListOf<Float>()

    var i = 0
    while (i < tokens.size) {
        when (tokens[i]) {
            "winrate" -> {
                winrate = tokens.getOrNull(i + 1)?.toFloatOrNull() ?: Float.NaN
                i += 2
            }
            "scoreLead", "scoreMean" -> {
                scoreLead = tokens.getOrNull(i + 1)?.toFloatOrNull() ?: scoreLead
                i += 2
            }
            "scoreStdev" -> {
                scoreStdev = tokens.getOrNull(i + 1)?.toFloatOrNull() ?: 0f
                i += 2
            }
            "ownership" -> {
                i++
                val need = boardSize * boardSize
                while (i < tokens.size && rawOwnership.size < need) {
                    tokens[i].toFloatOrNull()?.let { rawOwnership.add(it) }
                    i++
                }
            }
            else -> i++
        }
    }

    if (winrate.isNaN() || scoreLead.isNaN()) return null

    val whiteWin = when (currentPlayer) {
        StoneColor.White -> winrate
        StoneColor.Black -> 1f - winrate
    }
    val whiteLead = when (currentPlayer) {
        StoneColor.White -> scoreLead
        StoneColor.Black -> -scoreLead
    }

    val ownership = mutableMapOf<Pair<Int, Int>, StoneColor>()
    for (idx in rawOwnership.indices) {
        val r = idx / boardSize
        val c = idx % boardSize
        if (r >= boardSize || c >= boardSize) continue
        val v = rawOwnership[idx]
        when (currentPlayer) {
            StoneColor.Black -> when {
                v > 0.20f -> ownership[r to c] = StoneColor.Black
                v < -0.20f -> ownership[r to c] = StoneColor.White
            }
            StoneColor.White -> when {
                v > 0.20f -> ownership[r to c] = StoneColor.White
                v < -0.20f -> ownership[r to c] = StoneColor.Black
            }
        }
    }

    return EvalResult(ownership, whiteWin, whiteLead, scoreStdev)
}
