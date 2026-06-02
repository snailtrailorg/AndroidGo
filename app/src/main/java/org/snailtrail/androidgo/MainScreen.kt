package org.snailtrail.androidgo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.snailtrail.androidgo.game.TerritoryScore

// ── Bottom bar ──

@Composable
fun BottomBar(
    gameOver: Boolean,
    aiThinking: Boolean,
    engineInitializing: Boolean = false,
    hasMoves: Boolean,
    canRedo: Boolean = false,
    showScore: Boolean = false,
    scoringInFlight: Boolean = false,
    onPass: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
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
            onClick = onRedo, enabled = canRedo && !gameOver && !aiThinking && !engineInitializing && !showScore,
            modifier = Modifier.defaultMinSize(minWidth = 0.dp, minHeight = 32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.btn_redo), fontSize = 12.sp, maxLines = 1) }
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
                fontSize = 14.sp
            )
            Text(
                stringResource(R.string.score_white, whiteName, score.whiteStones, score.whiteTerritory, fmtScore(score.komi), fmtScore(score.whiteScore + score.komi)),
                fontSize = 14.sp
            )
            val diff = (score.blackScore - score.whiteScore) / 2f - score.komi
            Text(
                text = when {
                    diff > 0 -> stringResource(if (endGame) R.string.score_black_wins else R.string.score_black_leads, blackName, fmtScore(diff))
                    diff < 0 -> stringResource(if (endGame) R.string.score_white_wins else R.string.score_white_leads, whiteName, fmtScore(-diff))
                    else -> stringResource(R.string.score_draw)
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun fmtScore(f: Float): String {
    if (f == f.toLong().toFloat()) return "${f.toInt()}"
    val s = String.format("%.2f", f)
    return if (s.endsWith("0")) s.dropLast(1) else s
}

// ── About dialog ──

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.about_version), fontSize = 13.sp)
                Text(stringResource(R.string.about_desc), fontSize = 13.sp)
                Text(stringResource(R.string.about_engines), fontSize = 13.sp)
                Text(stringResource(R.string.about_powered_by), fontSize = 13.sp)
                Text(stringResource(R.string.about_github), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary)
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
