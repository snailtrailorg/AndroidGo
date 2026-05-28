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
}

object SgfConstants {
    const val DIR = "sgf"
    const val DATE_FORMAT = "yyyyMMdd_HHmmss"
    const val FILE_PREFIX = "game_"
    const val FILE_SUFFIX = ".sgf"
    const val APP_NAME = "AndroidGo:1.0"
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
    var col = cleaned[0] - 'A'
    if (col >= 8) col-- // skip I
    val rowStr = cleaned.substring(1)
    val gtpRow = rowStr.toIntOrNull() ?: return -1 to -1
    val row = boardSize - gtpRow
    if (row !in 0 until boardSize || col !in 0 until boardSize) return -1 to -1
    return row to col
}

/** Convert board (row, col) to GTP coordinate string. */
fun boardPosToGtp(row: Int, col: Int, boardSize: Int): String {
    var c = 'A' + col
    if (c >= 'I') c++
    val gtpRow = boardSize - row
    return "$c$gtpRow"
}
