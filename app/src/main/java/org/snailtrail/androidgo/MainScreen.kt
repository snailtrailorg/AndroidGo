package org.snailtrail.androidgo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.snailtrail.androidgo.game.EvalResult
import org.snailtrail.androidgo.game.Move
import org.snailtrail.androidgo.game.StoneColor
import org.snailtrail.androidgo.game.TerritoryScore

// ── Bottom bar ──

@Composable
fun BottomBar(
    gameOver: Boolean,
    aiThinking: Boolean,
    engineInitializing: Boolean = false,
    hasMoves: Boolean,
    showMoveNumbers: Boolean = false,
    showScore: Boolean = false,
    scoringInFlight: Boolean = false,
    onPass: () -> Unit,
    onUndo: () -> Unit,
    onToggleMoveNumbers: () -> Unit,
    onScore: () -> Unit,
    onEnd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = onPass, enabled = !gameOver && !aiThinking && !engineInitializing && !showScore,
            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.btn_pass), fontSize = 12.sp, maxLines = 1) }
        Button(
            onClick = onUndo, enabled = hasMoves && !gameOver && !aiThinking && !engineInitializing && !showScore,
            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.btn_undo), fontSize = 12.sp, maxLines = 1) }
        Button(
            onClick = onToggleMoveNumbers, enabled = hasMoves && !aiThinking && !engineInitializing,
            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = if (showMoveNumbers) ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) else ButtonDefaults.buttonColors()
        ) { Text(stringResource(R.string.btn_move_numbers), fontSize = 12.sp, maxLines = 1) }
        Button(
            onClick = onScore, enabled = hasMoves && !aiThinking && !engineInitializing && !scoringInFlight,
            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) { Text(stringResource(if (showScore) R.string.btn_continue else R.string.btn_score), fontSize = 12.sp, maxLines = 1) }
        Button(
            onClick = onEnd, enabled = !gameOver && !aiThinking && !engineInitializing && !showScore,
            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.btn_end), fontSize = 12.sp, maxLines = 1) }
    }
}

// ── Score card ──

@Composable
fun ScoreCard(score: TerritoryScore, blackName: String, whiteName: String, endGame: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.score_black, blackName, score.blackStones, score.blackTerritory, fmtScore(score.blackScore)),
                fontSize = 12.sp,
                lineHeight = 14.sp
            )
            Text(
                stringResource(R.string.score_white, whiteName, score.whiteStones, score.whiteTerritory, fmtScore(score.komi), fmtScore(score.whiteScore + score.komi)),
                fontSize = 12.sp,
                lineHeight = 14.sp
            )
            val diff = (score.blackScore - score.whiteScore) / 2f - score.komi
            Text(
                text = when {
                    diff > 0 -> stringResource(if (endGame) R.string.score_black_wins else R.string.score_black_leads, blackName, fmtScore(diff))
                    diff < 0 -> stringResource(if (endGame) R.string.score_white_wins else R.string.score_white_leads, whiteName, fmtScore(-diff))
                    else -> stringResource(R.string.score_draw)
                },
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── Eval / Score card: evaluation if available, otherwise territory ──

@Composable
fun EvalScoreCard(
    score: TerritoryScore?,
    eval: EvalResult?,
    blackName: String,
    whiteName: String,
    endGame: Boolean = false
) {
    if (eval != null) {
        val blackLead = -eval.scoreLead
        val blackWin = 1f - eval.whiteWin
        val winPct = (if (blackWin > 0.5f) blackWin else 1f - blackWin) * 100f
        val winSide = if (blackWin > 0.5f) blackName else whiteName
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(R.string.eval_label),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Winrate
                Text(
                    stringResource(R.string.eval_winrate, winSide, fmtScore(winPct)),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                // Score lead with error
                val leadAbs = kotlin.math.abs(blackLead)
                if (leadAbs > 0.5f || eval.scoreError > 0f) {
                    val leadText = when {
                        blackLead > 0.5f -> stringResource(R.string.eval_black_lead, blackName, fmtScore(blackLead))
                        blackLead < -0.5f -> stringResource(R.string.eval_white_lead, whiteName, fmtScore(-blackLead))
                        else -> stringResource(R.string.eval_even)
                    }
                    Text(
                        if (eval.scoreError > 0.5f)
                            stringResource(R.string.eval_score_uncertain, leadText, fmtScore(eval.scoreError))
                        else leadText,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }

    // Traditional territory scoring (fallback)
    if (score != null) {
        ScoreCard(score, blackName, whiteName, endGame)
    }
}

fun fmtScore(f: Float): String {
    if (f == f.toLong().toFloat()) return "${f.toInt()}"
    val s = String.format("%.2f", f)
    return if (s.endsWith("0")) s.dropLast(1) else s
}

// ── Move info card: shows full history of positions played multiple times ──

@Composable
fun MoveInfoCard(
    moveHistory: List<Move>,
    modifier: Modifier = Modifier
) {
    // Build position → list of move numbers
    val posMoves = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
    for ((idx, move) in moveHistory.withIndex()) {
        if (!move.isPass) {
            posMoves.getOrPut(move.stone.row to move.stone.col) { mutableListOf() }.add(idx + 1)
        }
    }
    // Filter to only positions with multiple moves
    val multi = posMoves.filter { it.value.size > 1 }.toList()

    if (multi.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                multi.joinToString(", ") { (_, nums) -> nums.sortedDescending().joinToString("=") },
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ── About dialog ──

@Composable
fun AboutDialog(onDismiss: () -> Unit, modelName: String = "") {
    val version = BuildConfig.VERSION_NAME
    val revision = BuildConfig.GIT_REVISION
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    painterResource(R.drawable.ic_app_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 8.dp)
                        .align(Alignment.CenterHorizontally),
                    tint = Color.Unspecified
                )
                Text(stringResource(R.string.about_desc), fontSize = 13.sp)
                Text(stringResource(R.string.about_version, version, revision), fontSize = 13.sp)
                Text(
                    stringResource(R.string.about_engines_supported),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row {
                    Text("  ·  ", fontSize = 12.sp)
                    Text(stringResource(R.string.about_gnugo), fontSize = 12.sp)
                }
                Row {
                    Text("  ·  ", fontSize = 12.sp)
                    Text(stringResource(R.string.about_katago, modelName), fontSize = 12.sp)
                }
                Text(
                    stringResource(R.string.about_oss_licenses),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row {
                    Text("  ·  ", fontSize = 12.sp)
                    Text(stringResource(R.string.about_oss_gnugo), fontSize = 12.sp)
                }
                Row {
                    Text("  ·  ", fontSize = 12.sp)
                    Text(stringResource(R.string.about_oss_katago), fontSize = 12.sp)
                }
                Row {
                    Text("  ·  ", fontSize = 12.sp)
                    Text(stringResource(R.string.about_oss_model), fontSize = 12.sp)
                }
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.about_privacy) + "  ", fontSize = 13.sp)
                    Text("androidgo.snailtrail.org/privacy/", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(R.string.about_powered_by), fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp)
            ) { Text(stringResource(R.string.about_close)) }
        }
    )
}
