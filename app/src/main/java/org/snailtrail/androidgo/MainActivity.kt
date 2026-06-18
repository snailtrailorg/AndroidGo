package org.snailtrail.androidgo

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import org.snailtrail.androidgo.game.StoneColor
import org.snailtrail.androidgo.engine.EngineManager
import org.snailtrail.androidgo.game.PrefKeys
import org.snailtrail.androidgo.game.SgfUtil
import org.snailtrail.androidgo.ui.*
import org.snailtrail.androidgo.ui.board.GoBoard
import org.snailtrail.androidgo.ui.theme.AndroidGoTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var engineManager: EngineManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engineManager = EngineManager(applicationContext)

        enableEdgeToEdge()
        setContent {
            AndroidGoTheme {
                AndroidGoScreen(engineManager, savedInstanceState)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Auto-save is handled via ViewModel in a LaunchedEffect
    }
}

@Composable
fun AndroidGoScreen(
    engineManager: EngineManager,
    savedInstanceState: Bundle?
) {
    val vm: GameViewModel = viewModel()
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    // Init on first composition — ViewModel reads prefs directly
    LaunchedEffect(Unit) {
        vm.init(engineManager)

        // Restore autosave if returning from background
        val autoSaveDir = File(ctx.filesDir, "sgf")
        if (savedInstanceState != null) {
            vm.restoreAutosave(ctx.filesDir)
        }
    }

    // End-game auto-scoring
    val boardState by vm.goGame.state.collectAsState()
    LaunchedEffect(boardState.gameOver) {
        if (boardState.gameOver) {
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

    when (state.currentPage) {
        Page.Game -> {
            GamePage(state, vm, boardState)
        }
        Page.History -> {
            val ctx = LocalContext.current
            val sgfDir = File(ctx.filesDir, "sgf")
            var historyRefreshTrigger by remember { mutableIntStateOf(0) }
            HistoryScreen(
                sgfDir = sgfDir,
                refreshTrigger = historyRefreshTrigger,
                onLoad = { parsed, file -> vm.onEvent(GameEvent.LoadSgf(parsed, file)) },
                onReview = { parsed -> vm.onEvent(GameEvent.ReviewSgf(parsed)) },
                onDelete = { file -> file.delete(); historyRefreshTrigger++ },
                onShare = { file ->
                    if (!file.exists()) {
                        Toast.makeText(ctx, ctx.getString(R.string.toast_file_not_found), Toast.LENGTH_SHORT).show()
                        return@HistoryScreen
                    }
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/x-go-sgf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.history_share)))
                },
                onBack = { vm.onEvent(GameEvent.GoToGame) }
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
        GameSettingsDialog(
            onSave = { vm.onEvent(GameEvent.DismissNewGame) },
            onSaveAndStart = { vm.onEvent(GameEvent.NewGame(it)) },
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
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(PrefKeys.NAME, android.content.Context.MODE_PRIVATE) }
    val firstRunPref = remember { prefs.getBoolean(PrefKeys.FIRST_RUN_COMPLETED, false) }
    var showFirstRunGuide by remember { mutableStateOf(!firstRunPref) }
    var guideStep by remember { mutableIntStateOf(0) }
    var buttonLayouts by remember { mutableStateOf<Map<String, ButtonLayout>>(emptyMap()) }
    var infoBarBottom by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TitleBar(
                    onMenuNewGame = { vm.quickNewGame() },
                    onMenuSettings = { vm.onEvent(GameEvent.ShowNewGameDialog) },
                    onMenuSave = { vm.onEvent(GameEvent.SaveSgf) },
                    onMenuHistory = { vm.onEvent(GameEvent.GoToHistory) },
                    onMenuAbout = { vm.onEvent(GameEvent.ShowAbout) },
                    onButtonLayout = { key, layout ->
                        buttonLayouts = buttonLayouts + (key to layout)
                    }
                )

            GameInfoBar(
                blackName = state.blackConfig.name,
                whiteName = state.whiteConfig.name,
                blackIsAI = state.blackConfig.role == PlayerRole.AI,
                whiteIsAI = state.whiteConfig.role == PlayerRole.AI,
                currentPlayer = boardState.currentPlayer,
                moveCount = boardState.moveHistory.size,
                gameOver = boardState.gameOver,
                onLayout = { infoBarBottom = it }
            )

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                GoBoard(
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
            }

            // Score card: result card when done
            if (state.showScore && (state.currentScore != null || state.currentEval != null)) {
                EvalScoreCard(
                    score = state.currentScore,
                    eval = state.currentEval,
                    blackName = state.blackConfig.name,
                    whiteName = state.whiteConfig.name,
                    endGame = boardState.gameOver
                )
            }

            // Move info card: only when move numbers toggled and score not showing
            if (state.showMoveNumbers && !state.showScore) {
                MoveInfoCard(moveHistory = boardState.moveHistory)
            }

            BottomBar(
                gameOver = boardState.gameOver,
                hasMoves = boardState.moveHistory.isNotEmpty(),
                showMoveNumbers = state.showMoveNumbers,
                showScore = state.showScore,
                onPass = { vm.onEvent(GameEvent.Pass) },
                onUndo = { vm.onEvent(GameEvent.Undo) },
                onToggleMoveNumbers = { vm.onEvent(GameEvent.ToggleMoveNumbers) },
                onScore = { vm.onEvent(GameEvent.Score) },
                onEnd = { vm.onEvent(GameEvent.End) }
            )
        }
    } // Scaffold

    // Full-screen busy overlay: blocks all input during non-idle states
    val busy = state.busyState
    if (busy == AppBusyState.Initializing || busy == AppBusyState.AiThinking || busy == AppBusyState.Evaluating) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume clicks */ },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp), strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.busyMessage.isNotEmpty()) {
                    Text(
                        text = state.busyMessage,
                        color = Color.White, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    // First-run step-by-step guide overlay
    if (showFirstRunGuide) {
        FirstRunGuide(
            buttonLayouts = buttonLayouts,
            infoBarBottom = infoBarBottom,
            currentStep = guideStep,
            onPrev = { guideStep-- },
            onNext = {
                if (guideStep >= 4) {
                    showFirstRunGuide = false
                    prefs.edit().putBoolean(PrefKeys.FIRST_RUN_COMPLETED, true).apply()
                } else {
                    guideStep++
                }
            },
            onSkip = {
                showFirstRunGuide = false
                prefs.edit().putBoolean(PrefKeys.FIRST_RUN_COMPLETED, true).apply()
            }
        )
    }
    } // Box
}

