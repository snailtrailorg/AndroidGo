package org.snailtrail.androidgo.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.snailtrail.androidgo.game.BoardState
import org.snailtrail.androidgo.game.StoneColor
import kotlin.math.min
import kotlin.math.roundToInt

private val BoardBackground = Color(0xFFDEB887)
private val BoardLine = Color(0xFF2D1F14)
private val BlackStoneTop = Color(0xFF5A5A5A)
private val BlackStoneBottom = Color(0xFF1A1A1A)
private val WhiteStoneTop = Color(0xFFFFFFFF)
private val WhiteStoneBottom = Color(0xFFC8C8C8)
private val LastMoveMarker = Color(0xFFE53935)

private val starPoints = mapOf(
    9 to setOf(2 to 2, 2 to 6, 4 to 4, 6 to 2, 6 to 6),
    13 to setOf(3 to 3, 3 to 9, 6 to 6, 9 to 3, 9 to 9),
    19 to setOf(3 to 3, 3 to 9, 3 to 15, 9 to 3, 9 to 9, 9 to 15, 15 to 3, 15 to 9, 15 to 15)
)

private data class BoardLayout(
    val cellSize: Float,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * Compute board layout so the grid is centred in [width]×[height] with at least
 * [padding] pixels of breathing room on every side.
 */
private fun computeLayout(
    width: Float, height: Float, boardSize: Int, padding: Float
): BoardLayout {
    val steps = boardSize - 1
    val cellSize = min((width - 2 * padding) / steps, (height - 2 * padding) / steps)
    val boardPx = cellSize * steps
    return BoardLayout(
        cellSize = cellSize,
        offsetX = (width - boardPx) / 2f,
        offsetY = (height - boardPx) / 2f
    )
}

@Composable
fun GoBoardScreen(
    boardState: BoardState,
    onCellClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    territoryMap: Map<Pair<Int, Int>, StoneColor> = emptyMap()
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(BoardBackground)
    ) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val n = boardState.size
        val steps = n - 1

        // Padding: at least 12 dp so stones near the edge aren't clipped,
        // but never more than what leaves room for the grid itself.
        val padDp = 12.dp
        val padPx = with(density) { min(padDp.toPx(), min(containerW, containerH) * 0.04f) }
        val layout = remember(containerW, containerH, n) {
            computeLayout(containerW, containerH, n, padPx)
        }
        val cellSize = layout.cellSize
        val offsetX = layout.offsetX
        val offsetY = layout.offsetY
        val boardPx = cellSize * steps

        val lineWidth = with(density) { maxOf(cellSize * 0.025f, 1.2f.dp.toPx()) }
        val stoneRadius = cellSize * 0.44f
        val starRadius = cellSize * 0.15f
        val borderWidth = lineWidth * 2.0f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(boardState.size) {
                    detectTapGestures { tapOffset ->
                        val col = ((tapOffset.x - offsetX) / cellSize)
                            .roundToInt().coerceIn(0, n - 1)
                        val row = ((tapOffset.y - offsetY) / cellSize)
                            .roundToInt().coerceIn(0, n - 1)
                        onCellClick(row, col)
                    }
                }
        ) {
            // Board shadow
            drawRect(
                color = Color.Black.copy(alpha = 0.12f),
                topLeft = Offset(offsetX - cellSize * 0.10f, offsetY - cellSize * 0.10f),
                size = Size(boardPx + cellSize * 0.20f, boardPx + cellSize * 0.20f)
            )

            // Grid lines
            for (i in 0 until n) {
                val x = offsetX + i * cellSize
                val y = offsetY + i * cellSize
                drawLine(BoardLine, Offset(x, offsetY), Offset(x, offsetY + boardPx), lineWidth)
                drawLine(BoardLine, Offset(offsetX, y), Offset(offsetX + boardPx, y), lineWidth)
            }

            // Outer border
            drawRect(
                BoardLine,
                Offset(offsetX, offsetY),
                Size(boardPx, boardPx),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = borderWidth)
            )

            // Star points
            starPoints[n]?.forEach { (r, c) ->
                drawCircle(
                    BoardLine, starRadius,
                    Offset(offsetX + c * cellSize, offsetY + r * cellSize)
                )
            }

            // Stones (drawn before territory so markers overlay dead stones)
            boardState.stones.forEach { (pos, color) ->
                val (row, col) = pos
                drawStone(
                    offsetX + col * cellSize,
                    offsetY + row * cellSize,
                    stoneRadius, color
                )
            }

            // Territory markers (on top of stones so dead stones get visible overlay)
            val territoryHalfSize = cellSize * 0.16f
            territoryMap.forEach { (pos, color) ->
                val (row, col) = pos
                val tx = offsetX + col * cellSize
                val ty = offsetY + row * cellSize
                val onStone = boardState.stones.containsKey(pos)
                drawRect(
                    color = when (color) {
                        StoneColor.Black -> Color.Black.copy(alpha = if (onStone) 0.50f else 0.30f)
                        StoneColor.White -> Color.White.copy(alpha = if (onStone) 0.55f else 0.45f)
                    },
                    topLeft = Offset(tx - territoryHalfSize, ty - territoryHalfSize),
                    size = Size(territoryHalfSize * 2f, territoryHalfSize * 2f)
                )
            }

            // Last-move marker
            boardState.lastMove?.let { (row, col) ->
                val cx = offsetX + col * cellSize
                val cy = offsetY + row * cellSize
                val markerColor = if (boardState.stones[row to col] == StoneColor.Black)
                    Color.White else LastMoveMarker
                drawCircle(markerColor, stoneRadius * 0.22f, Offset(cx, cy))
            }
        }
    }
}

private fun DrawScope.drawStone(cx: Float, cy: Float, radius: Float, color: StoneColor) {
    val (topColor, bottomColor) = when (color) {
        StoneColor.Black -> BlackStoneTop to BlackStoneBottom
        StoneColor.White -> WhiteStoneTop to WhiteStoneBottom
    }

    // Shadow
    drawCircle(
        Color.Black.copy(alpha = 0.25f),
        radius,
        Offset(cx + radius * 0.08f, cy + radius * 0.12f)
    )

    // Stone body with gradient
    drawCircle(
        Brush.radialGradient(
            0.3f to topColor,
            1f to bottomColor,
            center = Offset(cx - radius * 0.25f, cy - radius * 0.3f),
            radius = radius * 1.5f
        ),
        radius,
        Offset(cx, cy)
    )
}
