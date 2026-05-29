package org.snailtrail.androidgo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.snailtrail.androidgo.game.GoGame
import org.snailtrail.androidgo.game.ParsedSgf
import org.snailtrail.androidgo.game.SgfUtil
import org.snailtrail.androidgo.game.StoneColor
import org.snailtrail.androidgo.R
import org.snailtrail.androidgo.ui.GameInfoBar
import org.snailtrail.androidgo.ui.TitleBar
import org.snailtrail.androidgo.ui.board.GoBoardScreen
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import org.snailtrail.androidgo.game.SgfConstants

private val SGF_DATE_FORMAT = SimpleDateFormat(SgfConstants.DATE_FORMAT, Locale.US)

private data class SgfEntry(
    val file: File,
    val name: String,
    val boardSize: Int,
    val moveCount: Int,
    val date: String
)

@Composable
fun HistoryScreen(
    sgfDir: File,
    onMenuNewGame: () -> Unit = {},
    onMenuSave: () -> Unit = {},
    onMenuHistory: () -> Unit = {},
    onMenuAbout: () -> Unit = {},
    onLoad: (ParsedSgf, File) -> Unit,
    onReview: (ParsedSgf) -> Unit,
    onBack: () -> Unit
) {
    val entries = remember {
        val list = mutableListOf<SgfEntry>()
        sgfDir.mkdirs()
        sgfDir.listFiles()?.filter { it.extension.equals("sgf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { file ->
                val parsed = SgfUtil.parseFromFile(file)
                val name = file.nameWithoutExtension
                val date = try {
                    val ts = SGF_DATE_FORMAT.parse(name.removePrefix(SgfConstants.FILE_PREFIX))
                    if (ts != null) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(ts) else ""
                } catch (_: Exception) { "" }
                list.add(SgfEntry(
                    file = file,
                    name = name,
                    boardSize = parsed?.boardSize ?: 19,
                    moveCount = parsed?.moves?.size ?: 0,
                    date = date
                ))
            }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Title bar
        TitleBar(
            onMenuNewGame = onMenuNewGame,
            onMenuSave = onMenuSave,
            onMenuHistory = onMenuHistory,
            onMenuAbout = onMenuAbout
        )

        // Sub-title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.history_title), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = onBack) { Text(stringResource(R.string.history_back)) }
        }

        Spacer(Modifier.height(4.dp))

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.date.ifEmpty { entry.name },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    stringResource(R.string.history_entry_desc,
                                        stringResource(R.string.history_board_size, entry.boardSize),
                                        stringResource(R.string.history_moves, entry.moveCount)),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val parsed = SgfUtil.parseFromFile(entry.file)
                                    if (parsed != null) onLoad(parsed, entry.file)
                                }) {
                                    Text(stringResource(R.string.history_load), fontSize = 12.sp)
                                }
                                Button(onClick = {
                                    val parsed = SgfUtil.parseFromFile(entry.file)
                                    if (parsed != null) onReview(parsed)
                                }) {
                                    Text(stringResource(R.string.history_review), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Review screen ──

@Composable
fun ReviewScreen(
    moves: List<Pair<Int, Int>>,
    boardSize: Int,
    komi: Float,
    currentIndex: Int,
    blackName: String = "",
    whiteName: String = "",
    onIndexChange: (Int) -> Unit,
    onBack: () -> Unit,
    onLoad: () -> Unit
) {
    val game = remember { GoGame(boardSize).also { it.setKomi(komi) } }
    var displayIndex by remember { mutableIntStateOf(currentIndex) }

    val boardState = remember(game, displayIndex) {
        game.reset(boardSize)
        game.setKomi(komi)
        for (i in 0 until displayIndex.coerceAtMost(moves.size)) {
            val (row, col) = moves[i]
            if (row < 0) game.pass()
            else if (!game.placeStone(row, col)) break
        }
        game.state.value
    }

    val currentColor = if (displayIndex % 2 == 0) StoneColor.Black else StoneColor.White
    val isEnd = displayIndex >= moves.size
    val navButtonColors = ButtonDefaults.buttonColors(
        disabledContainerColor = ButtonDefaults.buttonColors().containerColor.copy(alpha = 0.35f),
        disabledContentColor = ButtonDefaults.buttonColors().contentColor.copy(alpha = 0.45f)
    )

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.review_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_back),
                        contentDescription = stringResource(R.string.review_back),
                        modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = {
                    onIndexChange(displayIndex)
                    onLoad()
                }, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.ic_load),
                        contentDescription = stringResource(R.string.review_load),
                        modifier = Modifier.size(24.dp))
                }
            }

            // Game info bar
            GameInfoBar(
                blackName = blackName.ifEmpty { stringResource(R.string.default_black_name) },
                whiteName = whiteName.ifEmpty { stringResource(R.string.default_white_name) },
                blackIsAI = false,
                whiteIsAI = false,
                currentPlayer = currentColor,
                moveCount = displayIndex,
                gameOver = isEnd,
                aiThinking = false
            )

            // Board — centered vertically, same as main screen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GoBoardScreen(
                    boardState = boardState,
                    onCellClick = { _, _ -> },
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            }

            // Navigation bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
            Button(
                onClick = { displayIndex = 0 },
                enabled = displayIndex > 0,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                colors = navButtonColors
            ) { Text("⏮", fontSize = 16.sp) }
            Button(
                onClick = { displayIndex = (displayIndex - 1).coerceAtLeast(0) },
                enabled = displayIndex > 0,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                colors = navButtonColors
            ) { Text("◀", fontSize = 16.sp) }
            Text(
                "$displayIndex / ${moves.size}",
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Button(
                onClick = { displayIndex = (displayIndex + 1).coerceAtMost(moves.size) },
                enabled = displayIndex < moves.size,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                colors = navButtonColors
            ) { Text("▶", fontSize = 16.sp) }
            Button(
                onClick = { displayIndex = moves.size },
                enabled = displayIndex < moves.size,
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                colors = navButtonColors
            ) { Text("⏭", fontSize = 16.sp) }
            }
        } // Column
    } // Scaffold
}
