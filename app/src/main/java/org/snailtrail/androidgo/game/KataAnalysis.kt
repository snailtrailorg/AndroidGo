package org.snailtrail.androidgo.game

/**
 * Result of a kata-raw-nn neural net evaluation.
 */
data class EvalResult(
    val ownership: Map<Pair<Int, Int>, StoneColor>,
    val whiteWin: Float,       // 0.0–1.0, white win probability
    val scoreLead: Float,      // estimated score lead from white's perspective
    val scoreError: Float      // estimated error in scoreLead (1 std dev)
)

/**
 * Parse kata-raw-nn output.
 * Ownership values are normalized against the predicted score lead
 * so that the visual territory markers match the lead direction.
 */
fun parseKataOwnership(raw: String, boardSize: Int): EvalResult? {
    var whiteWin = 0.5f
    var scoreLead = 0f
    var scoreError = 0f
    val lines = raw.lines()

    // Pass 1: extract score fields
    for (line in lines) {
        when {
            line.startsWith("whiteWin ") ->
                whiteWin = line.substringAfter("whiteWin ").trim().toFloatOrNull() ?: 0.5f
            line.startsWith("whiteLead ") ->
                scoreLead = line.substringAfter("whiteLead ").trim().toFloatOrNull() ?: 0f
            line.startsWith("shorttermScoreError ") ->
                scoreError = line.substringAfter("shorttermScoreError ").trim().toFloatOrNull() ?: 0f
        }
    }

    // Pass 2: find and parse the LAST ownership block
    var blockStart = -1
    for (i in lines.indices) {
        if (lines[i].startsWith("whiteOwnership")) blockStart = i
    }
    if (blockStart < 0) return null

    val rawValues = mutableListOf<Float>()
    var inBlock = false
    var row = 0
    for (i in blockStart until lines.size) {
        val line = lines[i]
        when {
            line.startsWith("whiteOwnership") -> { inBlock = true; row = 0 }
            inBlock -> {
                val vals = line.trim().split("\\s+".toRegex()).mapNotNull { it.toFloatOrNull() }
                for (c in 0 until minOf(vals.size, boardSize)) {
                    rawValues.add(vals[c])
                }
                row++
                if (row >= boardSize) inBlock = false
            }
        }
    }

    if (rawValues.size != boardSize * boardSize) {
        android.util.Log.w("KataAnalysis",
            "ownership size mismatch: got ${rawValues.size}, expected ${boardSize * boardSize}")
    }

    // Normalize: shift raw ownership values toward the predicted score lead.
    // The NN ownership head may be globally offset; we compensate so that
    // the sign of each value aligns with the lead direction.
    val rawSum = rawValues.sum()
    val bias = if (boardSize > 0 && rawValues.isNotEmpty())
        (scoreLead - rawSum) / rawValues.size else 0f

    val ownership = mutableMapOf<Pair<Int, Int>, StoneColor>()

    for (i in rawValues.indices) {
        val r = i / boardSize
        val c = i % boardSize
        if (r < boardSize && c < boardSize) {
            val v = rawValues[i] + bias
            when {
                v < -0.10f -> ownership[r to c] = StoneColor.Black
                v >  0.10f -> ownership[r to c] = StoneColor.White
            }
        }
    }

    val hasEval = whiteWin != 0.5f || scoreLead != 0f || scoreError > 0f
    return if (hasEval) EvalResult(ownership, whiteWin, scoreLead, scoreError) else null
}
