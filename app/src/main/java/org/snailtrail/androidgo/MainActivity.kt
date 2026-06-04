package org.snailtrail.androidgo

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import org.snailtrail.androidgo.engine.EngineManager
import org.snailtrail.androidgo.engine.EngineType
import org.snailtrail.androidgo.game.BoardState
import org.snailtrail.androidgo.game.GoGame
import org.snailtrail.androidgo.game.PrefKeys
import org.snailtrail.androidgo.game.SgfConstants
import org.snailtrail.androidgo.game.SgfUtil
import org.snailtrail.androidgo.game.StoneColor
import org.snailtrail.androidgo.game.TerritoryScore
import org.snailtrail.androidgo.game.gtpToBoardPos
import org.snailtrail.androidgo.game.parseKataOwnership
import org.snailtrail.androidgo.ui.GameInfoBar
import org.snailtrail.androidgo.ui.NewGameConfig
import org.snailtrail.androidgo.ui.NewGameDialog
import org.snailtrail.androidgo.ui.TitleBar
import org.snailtrail.androidgo.ui.PlayerConfig
import org.snailtrail.androidgo.ui.PlayerRole
import org.snailtrail.androidgo.ui.AiEngine
import org.snailtrail.androidgo.ui.ComputeBackend
import org.snailtrail.androidgo.ui.board.GoBoardScreen
import org.snailtrail.androidgo.ui.theme.AndroidGoTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val goGame = GoGame(13)
    private lateinit var engineManager: EngineManager
    private lateinit var blackConfig: PlayerConfig
    private lateinit var whiteConfig: PlayerConfig
    private val aiEngineReady = AtomicBoolean(false)
    private val aiEngineInitializing = AtomicBoolean(false)
    private val aiGeneration = java.util.concurrent.atomic.AtomicInteger(0)
    private val aiMoveInFlight = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engineManager = EngineManager(applicationContext)

        val prefs = getSharedPreferences(PrefKeys.NAME, MODE_PRIVATE)
        val savedConfig = loadConfigFromPrefs(prefs)
        blackConfig = savedConfig.blackPlayer
        whiteConfig = savedConfig.whitePlayer

        // If returning from background, restore auto-saved game
        val autoSave = File(filesDir, "autosave.sgf")
        val restored = if (savedInstanceState != null && autoSave.exists()) {
            val parsed = SgfUtil.parseFromFile(autoSave)
            if (parsed != null && parsed.moves.isNotEmpty()) {
                goGame.reset(parsed.boardSize)
                if (parsed.handicap > 0) goGame.setHandicap(parsed.handicap)
                for ((row, col) in parsed.moves) {
                    if (row < 0) goGame.pass()
                    else if (!goGame.placeStone(row, col)) break
                }
                if (parsed.blackName.isNotEmpty()) blackConfig = PlayerConfig(name = parsed.blackName)
                if (parsed.whiteName.isNotEmpty()) whiteConfig = PlayerConfig(name = parsed.whiteName)
                true
            } else false
        } else { autoSave.delete(); false }

        if (!restored) {
            goGame.reset(savedConfig.boardSize)
            if (savedConfig.handicap > 0) goGame.setHandicap(savedConfig.handicap)
        }

        enableEdgeToEdge()
        setContent {
            AndroidGoTheme {
                MainScreen()
            }
        }
    }

    @Composable
    private fun MainScreen() {
        val boardState by goGame.state.collectAsState()
        var aiThinking by remember { mutableStateOf(false) }
        var engineInitializing by remember { mutableStateOf(false) }
        var initialAiTriggered by remember { mutableStateOf(false) }

        // Trigger initial AI move if AI plays first
        LaunchedEffect(Unit) {
            if (!initialAiTriggered) {
                initialAiTriggered = true
                val s = goGame.state.value
                val aiActive = blackConfig.role == PlayerRole.AI || whiteConfig.role == PlayerRole.AI
                if (aiActive && s.moveHistory.isEmpty() && !s.gameOver) {
                    val aiFirst = when (s.currentPlayer) {
                        StoneColor.Black -> blackConfig.role == PlayerRole.AI
                        StoneColor.White -> whiteConfig.role == PlayerRole.AI
                    }
                    if (aiFirst) {
                        triggerAiMove { aiThinking = it }
                    }
                }
            }
        }

        var showNewGameDialog by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }
        var showScore by remember { mutableStateOf(false) }
        var currentScore by remember { mutableStateOf<TerritoryScore?>(null) }
        var scoringInFlight by remember { mutableStateOf(false) }

        // Page state
        var currentPage by remember { mutableStateOf<Page>(Page.Game) }
        // Review state
        var reviewMoves by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
        var reviewIndex by remember { mutableIntStateOf(0) }
        var reviewSize by remember { mutableIntStateOf(19) }
        var reviewKomi by remember { mutableStateOf(3.75f) }
        var reviewHandicap by remember { mutableIntStateOf(0) }

        val aiActive = blackConfig.role == PlayerRole.AI || whiteConfig.role == PlayerRole.AI
        val isAiTurn = aiActive && (
            (boardState.currentPlayer == StoneColor.Black && blackConfig.role == PlayerRole.AI) ||
            (boardState.currentPlayer == StoneColor.White && whiteConfig.role == PlayerRole.AI)
        )

        // End-game: auto-score with dead stone detection via KataGo
        LaunchedEffect(boardState.gameOver) {
            if (boardState.gameOver && !scoringInFlight) {
                scoringInFlight = true
                showScore = true
                currentScore = null  // trigger spinner
                withContext(Dispatchers.IO) {
                    val dead = getDeadStonesForScoring(boardState)
                    withContext(Dispatchers.Main) {
                        currentScore = goGame.countTerritory(dead)
                        scoringInFlight = false
                    }
                }
            }
        }

        when (currentPage) {
            Page.Game -> {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .statusBarsPadding()
                    ) {
                        // ── Title bar ──
                        TitleBar(
                            onMenuNewGame = { showNewGameDialog = true },
                            onMenuSave = { saveSgf() },
                            onMenuHistory = { currentPage = Page.History },
                            onMenuAbout = { showAboutDialog = true }
                        )
                        // ── Game info bar ──
                        GameInfoBar(
                            blackName = blackConfig.name,
                            whiteName = whiteConfig.name,
                            blackIsAI = blackConfig.role == PlayerRole.AI,
                            whiteIsAI = whiteConfig.role == PlayerRole.AI,
                            currentPlayer = boardState.currentPlayer,
                            moveCount = boardState.moveHistory.size,
                            gameOver = boardState.gameOver,
                            aiThinking = aiThinking
                        )

                        // ── Board + score ──
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            GoBoardScreen(
                                boardState = boardState,
                                onCellClick = { row, col ->
                                    if (aiThinking || showScore || scoringInFlight || engineInitializing) {
                                        return@GoBoardScreen
                                    }
                                    val curState = goGame.state.value
                                    if (curState.gameOver) return@GoBoardScreen
                                    if (isAiTurn) return@GoBoardScreen
                                    val moveOk = goGame.placeStone(row, col)
                                    if (moveOk && !goGame.state.value.gameOver) {
                                        triggerAiMove(aiThinkingState = { aiThinking = it })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                territoryMap = if (showScore) currentScore?.territoryMap ?: emptyMap() else emptyMap()
                            )

                            // ── Engine initializing overlay ──
                            if (engineInitializing) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .background(Color.Black.copy(alpha = 0.25f))
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        stringResource(R.string.engine_starting),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }

                        // ── Score card / loading ──
                        if (showScore && currentScore == null) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        } else if (showScore && currentScore != null) {
                            ScoreCard(
                                score = currentScore!!,
                                blackName = blackConfig.name,
                                whiteName = whiteConfig.name,
                                endGame = boardState.gameOver
                            )
                        }

                        // ── Bottom buttons ──
                        BottomBar(
                            gameOver = boardState.gameOver,
                            aiThinking = aiThinking,
                            engineInitializing = engineInitializing,
                            hasMoves = boardState.moveHistory.isNotEmpty(),
                            canRedo = boardState.redoStack.isNotEmpty(),
                            showScore = showScore,
                            scoringInFlight = scoringInFlight,
                            onPass = {
                                if (aiThinking) {
                                    // Just interrupt — the running genmove will return naturally
                                    engineManager.getEngine()?.interrupt()
                                } else {
                                    goGame.pass()
                                    showScore = false
                                    if (!goGame.state.value.gameOver) {
                                        triggerAiMove(aiThinkingState = { aiThinking = it })
                                    }
                                }
                            },
                            onUndo = {
                                showScore = false
                                val hist = boardState.moveHistory
                                if (aiActive && hist.size >= 2) {
                                    val last = hist.last()
                                    val prev = hist[hist.size - 2]
                                    // Undo both the AI's move and the human's move
                                    val doubleUndo = (last.stone.color == StoneColor.White && whiteConfig.role == PlayerRole.AI)
                                        || (last.stone.color == StoneColor.Black && blackConfig.role == PlayerRole.AI)
                                    if (doubleUndo) {
                                        goGame.undo(); goGame.undo()
                                        aiEngineReady.set(false)
                                    } else {
                                        goGame.undo()
                                    }
                                } else {
                                    goGame.undo()
                                }
                            },
                            onRedo = {
                                showScore = false
                                goGame.redo()
                            },
                            onScore = {
                                if (showScore) {
                                    showScore = false
                                    currentScore = null
                                } else if (!scoringInFlight) {
                                    scoringInFlight = true
                                    showScore = true
                                    currentScore = null
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val dead = getDeadStonesForScoring(boardState)
                                        withContext(Dispatchers.Main) {
                                            currentScore = goGame.countTerritory(dead)
                                            scoringInFlight = false
                                        }
                                    }
                                }
                            },
                            onEnd = {
                                goGame.pass()
                                if (!goGame.state.value.gameOver) {
                                    goGame.pass()
                                }
                            }
                        )
                    }
                }

                if (showNewGameDialog) {
                    NewGameDialog(
                        onConfirm = { config ->
                            showNewGameDialog = false
                            showScore = false
                            currentScore = null
                            scoringInFlight = false
                            engineInitializing = true
                            startNewGame(config,
                                aiThinkingState = { aiThinking = it },
                                onEngineReady = { engineInitializing = false }
                            )
                        },
                        onDismiss = { showNewGameDialog = false }
                    )
                }
            }

            Page.History -> {
                HistoryScreen(
                    sgfDir = File(filesDir, SgfConstants.DIR),
                    onLoad = { parsed, file ->
                        aiEngineReady.set(false)
                        engineManager.close()
                        showScore = false
                        currentScore = null
                        scoringInFlight = false
                        // Keep current AI config, only update names from SGF
                        if (parsed.blackName.isNotEmpty()) blackConfig = blackConfig.copy(name = parsed.blackName)
                        if (parsed.whiteName.isNotEmpty()) whiteConfig = whiteConfig.copy(name = parsed.whiteName)
                        goGame.reset(parsed.boardSize)
                        goGame.setKomi(parsed.komi)
                        if (parsed.handicap > 0) goGame.setHandicap(parsed.handicap)
                        for ((row, col) in parsed.moves) {
                            if (row < 0) goGame.pass()
                            else if (!goGame.placeStone(row, col)) break
                        }
                        Toast.makeText(this@MainActivity, getString(R.string.toast_loaded, file.name), Toast.LENGTH_SHORT).show()
                        currentPage = Page.Game
                    },
                    onReview = { parsed ->
                        reviewMoves = parsed.moves.toList()
                        reviewSize = parsed.boardSize
                        reviewKomi = parsed.komi
                        reviewHandicap = parsed.handicap
                        reviewIndex = if (parsed.moves.isEmpty()) 0 else parsed.moves.size
                        if (parsed.blackName.isNotEmpty()) blackConfig = blackConfig.copy(name = parsed.blackName)
                        if (parsed.whiteName.isNotEmpty()) whiteConfig = whiteConfig.copy(name = parsed.whiteName)
                        currentPage = Page.Review
                    },
                    onDelete = { file -> file.delete() },
                    onBack = { currentPage = Page.Game }
                )
            }

            Page.Review -> {
                ReviewScreen(
                    moves = reviewMoves,
                    boardSize = reviewSize,
                    komi = reviewKomi,
                    currentIndex = reviewIndex,
                    handicap = reviewHandicap,
                    blackName = blackConfig.name,
                    whiteName = whiteConfig.name,
                    onIndexChange = { reviewIndex = it },
                    onBack = { currentPage = Page.Game },
                    onLoad = {
                        val parsed = org.snailtrail.androidgo.game.ParsedSgf(
                            boardSize = reviewSize,
                            komi = reviewKomi
                        )
                        parsed.moves.addAll(reviewMoves)
                        aiEngineReady.set(false)
                        engineManager.close()
                        showScore = false
                        currentScore = null
                        scoringInFlight = false
                        if (parsed.blackName.isNotEmpty()) blackConfig = blackConfig.copy(name = parsed.blackName)
                        if (parsed.whiteName.isNotEmpty()) whiteConfig = whiteConfig.copy(name = parsed.whiteName)
                        goGame.reset(reviewSize)
                        goGame.setKomi(reviewKomi)
                        for ((row, col) in reviewMoves) {
                            if (row < 0) goGame.pass()
                            else if (!goGame.placeStone(row, col)) break
                        }
                        currentPage = Page.Game
                    }
                )
            }
        }

        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }
    }

    private fun startNewGame(config: NewGameConfig, aiThinkingState: (Boolean) -> Unit, onEngineReady: () -> Unit) {
        aiGeneration.incrementAndGet()
        blackConfig = config.blackPlayer
        whiteConfig = config.whitePlayer
        aiEngineReady.set(false)
        aiEngineInitializing.set(false)
        engineManager.close()

        goGame.reset(config.boardSize)

        if (config.handicap > 0) {
            goGame.setHandicap(config.handicap)
        }

        val aiActive = blackConfig.role == PlayerRole.AI || whiteConfig.role == PlayerRole.AI
        if (!aiActive) { onEngineReady(); return }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val aiPlayer = if (blackConfig.role == PlayerRole.AI) blackConfig else whiteConfig
                val engKomi = if (config.handicap > 0) config.handicap / 2f else 3.75f
                initAiEngine(aiPlayer, config.boardSize, engKomi)

                withContext(Dispatchers.Main) {
                    onEngineReady()

                    // If AI plays first, trigger the only genmove entry point
                    val state = goGame.state.value
                    val aiFirst = (state.currentPlayer == StoneColor.Black && blackConfig.role == PlayerRole.AI)
                               || (state.currentPlayer == StoneColor.White && whiteConfig.role == PlayerRole.AI)
                    if (aiFirst) {
                        triggerAiMove(aiThinkingState)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Engine start failed", e)
                withContext(Dispatchers.Main) {
                    onEngineReady()
                    Toast.makeText(this@MainActivity, getString(R.string.toast_ai_start_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun initAiEngine(aiPlayer: PlayerConfig, boardSize: Int, komi: Float) {
        if (aiEngineReady.get()) return
        if (!aiEngineInitializing.compareAndSet(false, true)) {
            var waited = 0
            while (aiEngineInitializing.get() && waited < 300) {
                kotlinx.coroutines.delay(100)
                waited++
            }
            if (aiEngineReady.get()) return
            if (aiEngineInitializing.get()) throw IllegalStateException(getString(R.string.error_init_timeout))
        }
        try {
            val engineType = if (aiPlayer.engine == AiEngine.KataGo) EngineType.KataGo else EngineType.GnuGo
            val engine = engineManager.ensureEngine(engineType, aiPlayer.difficulty, aiPlayer.backend)
            engine.init(boardSize, komi)

            val stones = goGame.state.value.stones
            for ((pos, color) in stones) {
                val (row, col) = pos
                engine.playMove(row, col, color == StoneColor.Black)
            }
            aiEngineReady.set(true)
        } catch (e: Exception) {
            aiEngineReady.set(false)
            throw e
        } finally {
            aiEngineInitializing.set(false)
        }
    }

    private fun triggerAiMove(aiThinkingState: (Boolean) -> Unit) {
        val state = goGame.state.value
        val gen = aiGeneration.get()
        if (state.gameOver) return

        val aiTurn = when (state.currentPlayer) {
            StoneColor.Black -> blackConfig.role == PlayerRole.AI
            StoneColor.White -> whiteConfig.role == PlayerRole.AI
        }
        if (!aiTurn) return

        if (!aiMoveInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "triggerAiMove: already in flight, skipping gen=$gen")
            return
        }

        lifecycleScope.launch {
            aiThinkingState(true)
            try {
                val justInitialized = !aiEngineReady.get()
                if (justInitialized) {
                    val aiPlayer = if (state.currentPlayer == StoneColor.Black) blackConfig else whiteConfig
                    withContext(Dispatchers.IO) { initAiEngine(aiPlayer, state.size, state.komi) }
                }

                val engine = engineManager.getEngine() ?: run {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, R.string.toast_ai_not_ready, Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Only send last move if engine wasn't just initialized (it already has all stones)
                if (!justInitialized) {
                    val lastMove = state.moveHistory.lastOrNull()
                    if (lastMove != null) {
                        withContext(Dispatchers.IO) {
                            engine.playMove(lastMove.stone.row, lastMove.stone.col,
                                lastMove.stone.color == StoneColor.Black)
                        }
                    }
                }

                val aiBlack = state.currentPlayer == StoneColor.Black
                val ok = withContext(Dispatchers.IO) { engine.generateMove(aiBlack) }

                if (ok) {
                    // Natural-feeling delay before AI places its stone
                    kotlinx.coroutines.delay(500)
                    // Check if game was reset while we were waiting
                    if (aiGeneration.get() != gen) {
                        Log.d(TAG, "triggerAiMove gen=$gen DISCARDED (current gen=${aiGeneration.get()})")
                        return@launch
                    }
                    val moveStr = engine.getLastGeneratedMove()
                    val (aiRow, aiCol) = gtpToBoardPos(moveStr, state.size)
                    if (aiRow >= 0 && aiCol >= 0) {
                        withContext(Dispatchers.Main) {
                            val placed = goGame.placeStone(aiRow, aiCol)
                            if (!placed) {
                                // Engine already made the move — undo it to stay synced
                                withContext(Dispatchers.IO) { engine.undo() }
                                goGame.pass()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) { goGame.pass() }
                    }
                    // Let Compose process the board update before clearing thinking indicator.
                    // Without this, the stone placement and aiThinking=false can merge into
                    // one frame, sometimes skipping the board redraw.
                    kotlinx.coroutines.delay(50)
                } else {
                    // Engine pipe broken (e.g. killed by new game) — don't touch board
                    withContext(Dispatchers.IO) { engineManager.close() }
                    aiEngineReady.set(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "triggerAiMove gen=$gen exception: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_ai_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
                withContext(Dispatchers.IO) { engineManager.close() }
                aiEngineReady.set(false)
            } finally {
                aiThinkingState(false)
                aiMoveInFlight.set(false)
            }
        }
    }

    private fun saveSgf() {
        try {
            val dir = File(filesDir, SgfConstants.DIR)
            dir.mkdirs()
            val ts = SimpleDateFormat(SgfConstants.DATE_FORMAT, Locale.US).format(Date())
            val file = File(dir, "${SgfConstants.FILE_PREFIX}$ts${SgfConstants.FILE_SUFFIX}")
            SgfUtil.exportToFile(goGame.state.value, file, blackConfig.name, whiteConfig.name)
            Toast.makeText(this, getString(R.string.toast_saved, file.name), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save SGF", e)
            Toast.makeText(this, getString(R.string.toast_save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        val state = goGame.state.value
        val autoSave = File(filesDir, "autosave.sgf")
        if (state.moveHistory.isNotEmpty() && !state.gameOver) {
            SgfUtil.exportToFile(state, autoSave, blackConfig.name, whiteConfig.name)
        } else {
            autoSave.delete()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engineManager.close()
    }

    private suspend fun getDeadStonesForScoring(state: BoardState): Set<Pair<Int, Int>> {
        // Try the current engine first (both GNU Go and KataGo support
        // final_status_list dead as a standard GTP command).
        val engine = engineManager.getEngine()
        if (engine != null) {
            val direct = try { engine.getDeadStones() } catch (_: Exception) { null }
            if (direct != null) return direct
        }
        // Fallback: start a temporary KataGo for dead stone detection.
        // This handles Human vs Human games or when the current engine fails.
        val tempMgr = EngineManager(applicationContext)
        return try {
            tempMgr.ensureEngine(EngineType.KataGo, 5, ComputeBackend.CPU)
            val e = tempMgr.getEngine()!!
            e.init(state.size, state.komi)
            for ((pos, color) in state.stones) {
                e.playMove(pos.first, pos.second, color == StoneColor.Black)
            }
            e.pass(true)
            e.pass(false)
            e.getDeadStones()
        } catch (_: Exception) {
            emptySet()
        } finally {
            tempMgr.close()
        }
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
                name = prefs.getString(PrefKeys.BLACK_NAME, getString(R.string.default_black_name)) ?: getString(R.string.default_black_name),
                engine = enumValue(PrefKeys.BLACK_ENGINE, AiEngine.GnuGo),
                difficulty = prefs.getInt(PrefKeys.BLACK_DIFFICULTY, 5).coerceIn(1, 10)
            ),
            whitePlayer = PlayerConfig(
                role = enumValue(PrefKeys.WHITE_ROLE, PlayerRole.AI),
                name = prefs.getString(PrefKeys.WHITE_NAME, "GNU Go") ?: "GNU Go",
                engine = enumValue(PrefKeys.WHITE_ENGINE, AiEngine.GnuGo),
                difficulty = prefs.getInt(PrefKeys.WHITE_DIFFICULTY, 5).coerceIn(1, 10)
            )
        )
    }

    companion object {
        private const val TAG = "AndroidGo"
    }
}

// ── Page enum ──

private sealed class Page {
    data object Game : Page()
    data object History : Page()
    data object Review : Page()
}

// BottomBar, ScoreCard, fmtScore, AboutDialog are now in MainScreen.kt
