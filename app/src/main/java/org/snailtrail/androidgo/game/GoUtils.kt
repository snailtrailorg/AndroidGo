package org.snailtrail.androidgo.game

object PrefKeys {
    const val NAME = "game_prefs"
    const val BOARD_SIZE = "boardSize"
    const val KOMI = "komi"
    const val HANDICAP = "handicap"
    const val BLACK_ROLE = "blackRole"
    const val BLACK_NAME = "blackName"
    const val BLACK_ENGINE = "blackEngine"
    const val BLACK_DIFFICULTY = "blackDifficulty"
    const val WHITE_ROLE = "whiteRole"
    const val WHITE_NAME = "whiteName"
    const val WHITE_ENGINE = "whiteEngine"
    const val WHITE_DIFFICULTY = "whiteDifficulty"
    const val BLACK_BACKEND = "blackBackend"
    const val WHITE_BACKEND = "whiteBackend"
    const val FIRST_RUN_COMPLETED = "firstRunCompleted"
    const val KATAGO_GPU_TUNING_COMPLETED = "katagoGpuTuningCompleted"
}

/**
 * Convert GTP coordinate (e.g. "D4", "K10") to board (row, col).
 * Returns (-1, -1) for pass/resign/empty.
 */
fun gtpToBoardPos(coord: String, boardSize: Int): Pair<Int, Int> {
    val cleaned = coord.trim().uppercase()
    if (cleaned.isEmpty() || cleaned == "PASS" || cleaned == "RESIGN" || cleaned == "TT")
        return -1 to -1
    if (cleaned.length < 2) return -1 to -1
    // Handle SGF two-letter format (e.g. "DD", "KJ")
    if (cleaned.length == 2 && cleaned[1].isLetter()) {
        return sgfToBoardPos(cleaned, boardSize)
    }
    var col = cleaned[0] - 'A'
    if (col >= 8) col-- // skip I
    val rowStr = cleaned.substring(1)
    val gtpRow = rowStr.toIntOrNull() ?: return -1 to -1
    val row = boardSize - gtpRow
    if (row !in 0 until boardSize || col !in 0 until boardSize) return -1 to -1
    return row to col
}

/** Parse SGF two-letter coordinate (e.g. "dd" = left-bottom). */
fun sgfToBoardPos(coord: String, boardSize: Int): Pair<Int, Int> {
    val cleaned = coord.trim().lowercase()
    if (cleaned.length < 2) return -1 to -1
    val col = cleaned[0] - 'a'
    val row = cleaned[1] - 'a'
    if (row !in 0 until boardSize || col !in 0 until boardSize) return -1 to -1
    return row to col
}

fun computeHandicapPositions(boardSize: Int, handicap: Int): List<Pair<Int, Int>> {
    if (handicap <= 0) return emptyList()
    val edge = if (boardSize == 9) 2 else 3
    val far = boardSize - 1 - edge
    val center = boardSize / 2
    val placements = listOf(
        far to far, edge to edge, edge to far, far to edge,
        center to center, center to edge, center to far, far to center, edge to center
    )
    return placements.take(handicap.coerceAtMost(9))
}

/** Convert board (row, col) to GTP coordinate string. */
fun boardPosToGtp(row: Int, col: Int, boardSize: Int): String {
    var c = 'A' + col
    if (c >= 'I') c++
    val gtpRow = boardSize - row
    return "$c$gtpRow"
}

/** Convert board (row, col) to SGF two-letter coordinate (e.g. "dd", "kj"). */
fun boardPosToSgf(row: Int, col: Int, boardSize: Int): String {
    val colLetter = 'a' + col
    val rowLetter = 'a' + row  // row 0 = bottom = 'a'
    return "$colLetter$rowLetter"
}
