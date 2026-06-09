package org.snailtrail.androidgo

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.snailtrail.androidgo.game.StoneColor
import org.snailtrail.androidgo.engine.EngineManager
import org.snailtrail.androidgo.game.PrefKeys
import org.snailtrail.androidgo.game.SgfUtil
import org.snailtrail.androidgo.ui.*
import org.snailtrail.androidgo.ui.board.GoBoardScreen
import org.snailtrail.androidgo.ui.theme.AndroidGoTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var engineManager: EngineManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engineManager = EngineManager(applicationContext)

        val prefs = getSharedPreferences(PrefKeys.NAME, MODE_PRIVATE)

        enableEdgeToEdge()
        setContent {
            AndroidGoTheme {
                AndroidGoScreen(engineManager, prefs, savedInstanceState)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Auto-save is handled via ViewModel in a LaunchedEffect
    }

    override fun onDestroy() {
        super.onDestroy()
        engineManager.close()
    }
}

@Composable
fun AndroidGoScreen(
    engineManager: EngineManager,
    prefs: android.content.SharedPreferences,
    savedInstanceState: Bundle?
) {
    val vm: GameViewModel = viewModel()
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    // Init on first composition
    LaunchedEffect(Unit) {
        val savedConfig = loadConfigFromPrefs(prefs)
        vm.init(engineManager, savedConfig)

        // Restore autosave if returning from background
        val autoSaveDir = File(ctx.filesDir, "sgf")
        if (savedInstanceState != null) {
            vm.restoreAutosave(ctx.filesDir)
        }
    }

    // End-game auto-scoring
    val boardState by vm.goGame.state.collectAsState()
    LaunchedEffect(boardState.gameOver) {
        if (boardState.gameOver && !boardLocked(state)) {
            vm.updateScoreFromDeadStones()
        }
    }

    // Toast handler
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            vm.onEvent(GameEvent.DismissToast)
        }
    }

    // Auto-save on stop
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                vm.autosaveIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Initial AI trigger
    LaunchedEffect(Unit) {
        val s = state
        val aiActive = s.blackConfig.role == PlayerRole.AI || s.whiteConfig.role == PlayerRole.AI
        if (aiActive && vm.boardState.moveHistory.isEmpty() && !vm.boardState.gameOver) {
            vm.requestAiMove()
        }
    }

    val aiActive = state.blackConfig.role == PlayerRole.AI || state.whiteConfig.role == PlayerRole.AI
    val isAiTurn = aiActive && (
        (boardState.currentPlayer == StoneColor.Black && state.blackConfig.role == PlayerRole.AI) ||
        (boardState.currentPlayer == StoneColor.White && state.whiteConfig.role == PlayerRole.AI)
    )

    when (state.currentPage) {
        Page.Game -> {
            GamePage(state, vm, boardState, isAiTurn)
        }
        Page.History -> {
            HistoryScreen(
                sgfDir = File(LocalContext.current.filesDir, "sgf"),
                onLoad = { parsed, file -> vm.onEvent(GameEvent.LoadSgf(parsed, file)) },
                onReview = { parsed -> vm.onEvent(GameEvent.ReviewSgf(parsed)) },
                onDelete = { file -> file.delete() },
                onBack = { vm.onEvent(GameEvent.GoToHistory) } // actually Back
            )
        }
        Page.Review -> {
            ReviewScreen(
                moves = state.reviewMoves,
                boardSize = state.reviewSize,
                komi = state.reviewKomi,
                currentIndex = state.reviewIndex,
                handicap = state.reviewHandicap,
                blackName = state.blackConfig.name,
                whiteName = state.whiteConfig.name,
                onIndexChange = { vm.onEvent(GameEvent.ReviewIndexChange(it)) },
                onBack = { vm.onEvent(GameEvent.BackFromReview) },
                onLoad = { vm.onEvent(GameEvent.LoadFromReview) }
            )
        }
    }

    if (state.showNewGameDialog) {
        NewGameDialog(
            onConfirm = { vm.onEvent(GameEvent.NewGame(it)) },
            onDismiss = { vm.onEvent(GameEvent.DismissNewGame) }
        )
    }
    if (state.showAboutDialog) {
        AboutDialog(
            onDismiss = { vm.onEvent(GameEvent.DismissAbout) },
            modelName = engineManager.modelName
        )
    }
}

@Composable private fun GamePage(
    state: UiState,
    vm: GameViewModel,
    boardState: org.snailtrail.androidgo.game.BoardState,
    isAiTurn: Boolean
) {
    val busy = state.busyState

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            TitleBar(
                onMenuNewGame = { vm.onEvent(GameEvent.ShowNewGameDialog) },
                onMenuSave = { vm.onEvent(GameEvent.SaveSgf) },
                onMenuHistory = { vm.onEvent(GameEvent.GoToHistory) },
                onMenuAbout = { vm.onEvent(GameEvent.ShowAbout) },
                aiThinking = busy == AppBusyState.AiThinking,
                engineInitializing = busy == AppBusyState.Initializing
            )

            GameInfoBar(
                blackName = state.blackConfig.name,
                whiteName = state.whiteConfig.name,
                blackIsAI = state.blackConfig.role == PlayerRole.AI,
                whiteIsAI = state.whiteConfig.role == PlayerRole.AI,
                currentPlayer = boardState.currentPlayer,
                moveCount = boardState.moveHistory.size,
                gameOver = boardState.gameOver,
                aiThinking = busy == AppBusyState.AiThinking
            )

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GoBoardScreen(
                    boardState = boardState,
                    onCellClick = { row, col -> vm.onEvent(GameEvent.CellClick(row, col)) },
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    territoryMap = when {
                        state.showScore && state.currentEval != null -> state.currentEval!!.ownership
                        state.showScore && state.currentScore != null -> state.currentScore!!.territoryMap
                        else -> emptyMap()
                    },
                    showMoveNumbers = state.showMoveNumbers
                )

                if (busy == AppBusyState.Initializing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f))
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp), strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(stringResource(R.string.engine_starting),
                            color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }

                if (busy == AppBusyState.AiThinking) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f))
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(stringResource(R.string.ai_thinking),
                            color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            // Score card: spinner while evaluating; result card when done
            if (state.showScore && state.currentEval == null && state.currentScore == null && busy == AppBusyState.Evaluating) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (state.showScore && (state.currentScore != null || state.currentEval != null)) {
                EvalScoreCard(
                    score = state.currentScore,
                    eval = state.currentEval,
                    blackName = state.blackConfig.name,
                    whiteName = state.whiteConfig.name,
                    endGame = boardState.gameOver
                )
            }

            BottomBar(
                gameOver = boardState.gameOver,
                aiThinking = busy == AppBusyState.AiThinking,
                engineInitializing = busy == AppBusyState.Initializing,
                hasMoves = boardState.moveHistory.isNotEmpty(),
                showMoveNumbers = state.showMoveNumbers,
                showScore = state.showScore,
                scoringInFlight = busy == AppBusyState.Evaluating,
                onPass = { vm.onEvent(GameEvent.Pass) },
                onUndo = { vm.onEvent(GameEvent.Undo) },
                onToggleMoveNumbers = { vm.onEvent(GameEvent.ToggleMoveNumbers) },
                onScore = { vm.onEvent(GameEvent.Score) },
                onEnd = { vm.onEvent(GameEvent.End) }
            )
        }
    }
}

private fun boardLocked(state: UiState): Boolean {
    val b = state.busyState
    return b == AppBusyState.AiThinking || b == AppBusyState.Initializing ||
           b == AppBusyState.Evaluating || state.showScore
}

private fun loadConfigFromPrefs(prefs: android.content.SharedPreferences): NewGameConfig {
    fun <T : Enum<T>> enumValue(name: String, default: T): T {
        val n = prefs.getString(name, null) ?: return default
        return default.javaClass.enumConstants?.firstOrNull { it.name == n } ?: default
    }
    return NewGameConfig(
        boardSize = prefs.getInt(PrefKeys.BOARD_SIZE, 13).coerceIn(9, 19),
        handicap = prefs.getInt(PrefKeys.HANDICAP, 0).coerceIn(0, 9),
        blackPlayer = PlayerConfig(
            role = enumValue(PrefKeys.BLACK_ROLE, PlayerRole.Human),
            name = prefs.getString(PrefKeys.BLACK_NAME, "") ?: "",
            engine = enumValue(PrefKeys.BLACK_ENGINE, AiEngine.GnuGo),
            difficulty = prefs.getInt(PrefKeys.BLACK_DIFFICULTY, 5).coerceIn(1, 10)
        ),
        whitePlayer = PlayerConfig(
            role = enumValue(PrefKeys.WHITE_ROLE, PlayerRole.AI),
            name = prefs.getString(PrefKeys.WHITE_NAME, "") ?: "",
            engine = enumValue(PrefKeys.WHITE_ENGINE, AiEngine.GnuGo),
            difficulty = prefs.getInt(PrefKeys.WHITE_DIFFICULTY, 5).coerceIn(1, 10)
        )
    )
}