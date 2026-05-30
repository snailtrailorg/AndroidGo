package org.snailtrail.androidgo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.snailtrail.androidgo.R
import org.snailtrail.androidgo.game.StoneColor

@Composable
fun GameInfoBar(
    blackName: String,
    whiteName: String,
    blackIsAI: Boolean,
    whiteIsAI: Boolean,
    currentPlayer: StoneColor,
    moveCount: Int,
    gameOver: Boolean,
    aiThinking: Boolean
) {
    val isBlackTurn = currentPlayer == StoneColor.Black && !gameOver

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF1A1A1A)))
        Text(
            " $blackName${if (blackIsAI) " (AI)" else ""}",
            fontSize = 13.sp,
            fontWeight = if (isBlackTurn) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            if (isBlackTurn) " ⌛" else "  ",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
        )
        if (aiThinking) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = when {
                gameOver -> stringResource(R.string.game_over)
                aiThinking -> stringResource(R.string.ai_thinking)
                else -> stringResource(R.string.move_count, moveCount)
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (!isBlackTurn) "⌛ " else "  ",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "${if (whiteIsAI) "(AI) " else ""}$whiteName ",
            fontSize = 13.sp,
            fontWeight = if (!isBlackTurn && !gameOver) FontWeight.Bold else FontWeight.Normal
        )
        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFF0F0F0)))
    }
}
