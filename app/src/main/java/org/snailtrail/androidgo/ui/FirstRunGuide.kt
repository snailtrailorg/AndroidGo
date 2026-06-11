package org.snailtrail.androidgo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.snailtrail.androidgo.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// ── Guide steps ─────────────────────────────────────────────────────

private data class GuideStep(
    val key: String,
    val iconRes: Int,
    val labelRes: Int
)

private val guideSteps = listOf(
    GuideStep("new_game", R.drawable.ic_new_game, R.string.first_run_guide_new_game),
    GuideStep("settings", R.drawable.ic_settings, R.string.first_run_guide_settings),
    GuideStep("save", R.drawable.ic_save, R.string.first_run_guide_save),
    GuideStep("history", R.drawable.ic_history, R.string.first_run_guide_history),
    GuideStep("about", R.drawable.ic_about, R.string.first_run_guide_about)
)

// ── Unified callout shape ───────────────────────────────────────────
//
//   Single Shape = arrow + card body, rendered by one Surface.
//   Arrow aims at the button centre, then retreats so the tip
//   never intrudes into the button.
//
//              ┌──────┐  ← button (not drawn)
//              │  ●   │  ← button centre  (bx,bh)
//              └──────┘
//                 │ retreatDist
//              ┌──△──┐  ← arrow tip (flattened 2dp for shadow)
//             ╱      ╲
//            ╱  36°   ╲
//           ╱          ╲
//     ╔════╝     card   ╚════╗  ← card top (shape Y = arrowH)
//     ║                      ║
//     ║      card body       ║
//     ╚══════════════════════╝

private data class ArrowTip(
    val x: Float,       // in root coords
    val y: Float         // in root coords
)

/**
 * Compute the arrow tip position (root coords).
 *
 *  - direction: card centre → button centre
 *  - retreat:   back off from button centre by (btnDiagHalf + 4dp) so
 *               the tip never overlaps the button
 */
private fun computeArrowTip(
    btnCx: Float, btnCy: Float,
    btnSize: IntSize,
    cardCx: Float, cardCy: Float,
    retreatExtraPx: Float
): ArrowTip {
    val diagHalf = sqrt((btnSize.width * btnSize.width + btnSize.height * btnSize.height).toFloat()) / 2f
    val retreat = diagHalf + retreatExtraPx

    // unit vector from button centre → card centre
    val dx = cardCx - btnCx
    val dy = cardCy - btnCy
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1f) return ArrowTip(btnCx, btnCy - retreat) // fallback: straight up

    val ux = dx / len
    val uy = dy / len

    // retreat from button centre along the cardward direction
    return ArrowTip(btnCx + ux * retreat, btnCy + uy * retreat)
}

/**
 * Card shape with an arrow protruding from the top edge.
 * Arrow tip is at shape Y = 0, card body starts at Y = arrowH.
 *
 * @param arrowTipX  arrow tip X in shape coords
 * @param arrowTipY  arrow tip Y in shape coords (<= 0)
 * @param arrowBaseLeftX  left intersection on card top edge (shape Y = arrowH)
 * @param arrowBaseRightX right intersection on card top edge
 * @param arrowH     vertical distance from tip to card top (shape coords)
 */
private class CalloutShape(
    private val arrowTipX: Float,
    private val arrowTipY: Float,
    private val arrowBaseLeftX: Float,
    private val arrowBaseRightX: Float,
    private val arrowH: Float,
    private val tipFlatHalf: Float  // half-width of the flattened arrow tip (for shadow)
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { 12.dp.toPx() }
        val w = size.width
        val h = size.height

        // Clamp arrow base intersections to card top edge (within corners)
        val xL = arrowBaseLeftX.coerceIn(r, w - r)
        val xR = arrowBaseRightX.coerceIn(r, w - r)

        // Arrow tip with a tiny flat cap (anti-shadow-aliasing)
        val tipL = arrowTipX - tipFlatHalf
        val tipR = arrowTipX + tipFlatHalf

        val path = Path().apply {
            // Start at arrow tip left
            moveTo(tipL, arrowTipY)
            // Tip flat top
            lineTo(tipR, arrowTipY)
            // Right side down to card top
            lineTo(xR, arrowH)
            // Card top edge L→R (right intersection → right corner)
            lineTo(w - r, arrowH)
            // Top-right corner
            arcTo(Rect(w - 2 * r, arrowH, w, arrowH + 2 * r), 270f, 90f, false)
            // Right edge
            lineTo(w, h - r)
            // Bottom-right corner
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            // Bottom edge
            lineTo(r, h)
            // Bottom-left corner
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            // Left edge
            lineTo(0f, arrowH + r)
            // Top-left corner
            arcTo(Rect(0f, arrowH, 2 * r, arrowH + 2 * r), 180f, 90f, false)
            // Card top edge R→L (left corner → left intersection)
            lineTo(xL, arrowH)
            // Left side up to tip
            lineTo(tipL, arrowTipY)
            close()
        }
        return Outline.Generic(path)
    }
}

// ── Main composable ─────────────────────────────────────────────────

@Composable
fun FirstRunGuide(
    buttonLayouts: Map<String, ButtonLayout>,
    infoBarBottom: Float,
    currentStep: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    // Wait until layout info is available
    if (infoBarBottom <= 0f) return
    val step = guideSteps.getOrNull(currentStep) ?: return
    val layout = buttonLayouts[step.key] ?: return

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }

    val cardW = 280.dp
    val cardWPx = with(density) { cardW.toPx() }
    val arrowHPx = with(density) { 12.dp.toPx() }   // stem height inside shape
    val cardBodyHPx = with(density) { 150.dp.toPx() }
    val cardTotalH = arrowHPx + cardBodyHPx
    val infoGapPx = with(density) { 16.dp.toPx() }
    val retreatExtraPx = 0f
    val tipFlatPx = with(density) { 1.dp.toPx() }   // half-width of flattened tip
    val marginPx = with(density) { 16.dp.toPx() }

    // ── Card position: centred, below info bar ──
    val cardX = (screenW - cardWPx) / 2f
    val surfaceY = infoBarBottom + infoGapPx   // Surface top = info bar bottom + gap

    val cardCx = cardX + cardWPx / 2f
    val cardCy = surfaceY + arrowHPx + cardBodyHPx / 2f

    // ── Button geometry ──
    val btnCx = layout.position.x + layout.size.width / 2f
    val btnCy = layout.position.y + layout.size.height / 2f

    // ── Arrow tip (retreated from button centre) ──
    val tip = computeArrowTip(btnCx, btnCy, layout.size, cardCx, cardCy, retreatExtraPx)

    // ── Arrow centre line (from tip toward card centre) ──
    val dirDx = cardCx - tip.x
    val dirDy = cardCy - tip.y
    val dirLen = sqrt(dirDx * dirDx + dirDy * dirDy)
    val ux = if (dirLen > 0f) dirDx / dirLen else 0f
    val uy = if (dirLen > 0f) dirDy / dirLen else 1f
    val centreAngle = atan2(ux.toDouble(), uy.toDouble())  // from vertical

    // 36° total spread; sides diverge by ±18° from centre line
    val spreadAngleRad = Math.toRadians(18.0)

    // Distance from tip to card top in root coords
    val distToCardTop = (surfaceY + arrowHPx) - tip.y   // card top in root = surfaceY + arrowHPx
    if (distToCardTop <= 0f) return  // arrow tip is below card — shouldn't happen

    // Intersections of the two sides with card top (Y = surfaceY + arrowHPx in root)
    val xL = tip.x + (distToCardTop * tan(centreAngle - spreadAngleRad)).toFloat()
    val xR = tip.x + (distToCardTop * tan(centreAngle + spreadAngleRad)).toFloat()

    // ── Shape geometry (in shape coords: origin = surface top-left) ──
    val shapeTipX = tip.x - cardX
    val shapeTipY = tip.y - surfaceY   // negative (tip is above card top)
    val shapeBaseL = xL - cardX
    val shapeBaseR = xR - cardX

    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.primary
    val isLastStep = currentStep == guideSteps.lastIndex

    Box(Modifier.fillMaxSize()) {
        // Dim background
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }

        // Single Surface with unified arrow+card shape
        Surface(
            modifier = Modifier
                .offset { IntOffset(cardX.roundToInt(), surfaceY.roundToInt()) }
                .width(cardW),
            shape = CalloutShape(
                arrowTipX = shapeTipX,
                arrowTipY = shapeTipY,
                arrowBaseLeftX = shapeBaseL,
                arrowBaseRightX = shapeBaseR,
                arrowH = arrowHPx,
                tipFlatHalf = tipFlatPx
            ),
            color = cardColor,
            shadowElevation = 6.dp
        ) {
            Column(
                // Content starts below the arrow stem
                modifier = Modifier
                    .padding(top = with(density) { arrowHPx.toDp() })
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.first_run_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                // Menu item row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.first_run_item_label),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painterResource(step.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = highlightColor
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Description row
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.first_run_desc_label),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(step.labelRes),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    stringResource(R.string.first_run_step_counter, currentStep + 1, guideSteps.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    if (currentStep > 0) {
                        Button(
                            onClick = onPrev,
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.first_run_prev), fontSize = 12.sp)
                        }
                    }
                    if (!isLastStep) {
                        Button(
                            onClick = onSkip,
                            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.first_run_skip), fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = highlightColor)
                    ) {
                        Text(
                            if (isLastStep) stringResource(R.string.first_run_done)
                            else stringResource(R.string.first_run_next),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
