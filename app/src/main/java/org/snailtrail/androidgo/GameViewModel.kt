package org.snailtrail.androidgo

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.snailtrail.androidgo.engine.EngineManager
import org.snailtrail.androidgo.engine.EngineType
import org.snailtrail.androidgo.game.*
import org.snailtrail.androidgo.ui.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// ── AppBusyState ──

enum class AppBusyState {
    Idle,
    Initializing,
    AiThinking,
    Evaluating,
    ShowingScore
}

// ── UiState ──

data class UiState(
    val boardSize: Int = 13,
    val blackConfig: PlayerConfig = PlayerConfig(),
    val whiteConfig: PlayerConfig = PlayerConfig(role = PlayerRole.AI, engine = AiEngine.GnuGo),
    val busyState: AppBusyState = AppBusyState.Idle,
    val busyMessage: String = "",
    val showScore: Boolean = false,
    val showMoveNumbers: Boolean = false,
    val currentEval: EvalResult? = null,
    val currentScore: TerritoryScore? = null,
    val loadedEngineType: EngineType? = null,
    val loadedAiDifficulty: Int = 5,
    val gpuTuningCompleted: Boolean = false,

    // Pages
    val currentPage: Page = Page.Game,

    // New game dialog
    val showNewGameDialog: Boolean = false,

    // About dialog
    val showAboutDialog: Boolean = false,

    // Review
    val reviewMoves: List<Pair<Int, Int>> = emptyList(),
    val reviewIndex: Int = 0,
    val reviewSize: Int = 19,
    val reviewKomi: Float = 3.75f,
    val reviewHandicap: Int = 0,
    val reviewEngineTypeName: String = "",
    val reviewAiDifficulty: Int = 5,

    // Toast
    val toastMessage: String? = null
) {
    /** Clean reset for loading a game from history or review. */
    fun resetToGame(): UiState = copy(
        busyState = AppBusyState.Idle,
        busyMessage = "",
        showScore = false,
        currentScore = null,
        currentEval = null,
        currentPage = Page.Game
    )
}

// ── Page ──

sealed class Page {
    data object Game : Page()
    data object History : Page()
    data object Review : Page()
}

// ── GameEvent ──

sealed class GameEvent {
    data class CellClick(val row: Int, val col: Int) : GameEvent()
    object Pass : GameEvent()
    object Undo : GameEvent()
    object ToggleMoveNumbers : GameEvent()
    object Score : GameEvent()
    object End : GameEvent()
    object ShowNewGameDialog : GameEvent()
    data class NewGame(val config: NewGameConfig) : GameEvent()
    object DismissNewGame : GameEvent()
    object ShowAbout : GameEvent()
    object DismissAbout : GameEvent()
    object SaveSgf : GameEvent()
    object GoToGame : GameEvent()
    object GoToHistory : GameEvent()
    data class LoadSgf(val parsed: ParsedSgf, val file: File) : GameEvent()
    data class ReviewSgf(val parsed: ParsedSgf) : GameEvent()
    data class ReviewIndexChange(val index: Int) : GameEvent()
    object LoadFromReview : GameEvent()
    object BackFromReview : GameEvent()
    object DismissToast : GameEvent()
}

// ── ViewModel ──

class GameViewModel(application: Application) : AndroidViewModel(application) {

    // ── Game logic ──
    val goGame = GoGame(13)
    lateinit var engineManager: EngineManager

    // ── Engine guards ──
    val aiEngineReady = AtomicBoolean(false)
    val aiGeneration = AtomicInteger(0)
    val aiMoveInFlight = AtomicBoolean(false)

    // ── GTP queue (Step 2) ──
    @OptIn(ExperimentalCoroutinesApi::class)
    private val gtpDispatcher = Dispatchers.IO.limitedParallelism(1)
    suspend fun <T> onGtp(block: suspend () -> T): T = withContext(gtpDispatcher) { block() }

    // ── State ──
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Convenience reads
    val boardState get() = goGame.state.value
    private fun currentState() = _state.value
    private fun update(f: (UiState) -> UiState) = _state.update(f)

    // ── Init (called from activity) ──
    fun init(engineMgr: EngineManager) {
        engineManager = engineMgr
        val ctx = getApplication<Application>()
        val prefs = ctx.getSharedPreferences(PrefKeys.NAME, android.content.Context.MODE_PRIVATE)

        val blackRole    = prefs.getEnum(PrefKeys.BLACK_ROLE,    PlayerRole.Human)
        val whiteRole    = prefs.getEnum(PrefKeys.WHITE_ROLE,    PlayerRole.AI)
        val blackEngine  = prefs.getEnum(PrefKeys.BLACK_ENGINE,  AiEngine.GnuGo)
        val whiteEngine  = prefs.getEnum(PrefKeys.WHITE_ENGINE,  AiEngine.GnuGo)
        val blackBackend = prefs.getEnum(PrefKeys.BLACK_BACKEND, ComputeBackend.CPU)
        val whiteBackend = prefs.getEnum(PrefKeys.WHITE_BACKEND, ComputeBackend.CPU)
        val boardSize    = prefs.getInt(PrefKeys.BOARD_SIZE, 13).coerceIn(9, 19)
        val handicap     = prefs.getInt(PrefKeys.HANDICAP, 0).coerceIn(0, 9)
        val blackDiff    = prefs.getInt(PrefKeys.BLACK_DIFFICULTY, 5).coerceIn(1, 10)
        val whiteDiff    = prefs.getInt(PrefKeys.WHITE_DIFFICULTY, 5).coerceIn(1, 10)
        val gpuTuned     = prefs.getBoolean(PrefKeys.KATAGO_GPU_TUNING_COMPLETED, false)

        val blackName = prefs.getString(PrefKeys.BLACK_NAME, null)
            ?: defaultName(ctx, blackRole, blackEngine, isBlack = true)
        val whiteName = prefs.getString(PrefKeys.WHITE_NAME, null)
            ?: defaultName(ctx, whiteRole, whiteEngine, isBlack = false)

        update {
            it.copy(
                boardSize = boardSize,
                blackConfig = PlayerConfig(blackRole, blackName, blackEngine, blackDiff, blackBackend),
                whiteConfig = PlayerConfig(whiteRole, whiteName, whiteEngine, whiteDiff, whiteBackend),
                gpuTuningCompleted = gpuTuned
            )
        }
        goGame.reset(boardSize)
        if (handicap > 0) goGame.setHandicap(handicap)
    }

    private fun defaultName(
        ctx: android.content.Context,
        role: PlayerRole,
        engine: AiEngine,
        isBlack: Boolean
    ): String = when (role) {
        PlayerRole.Human -> ctx.getString(
            if (isBlack) R.string.default_black_name else R.string.default_white_name
        )
        PlayerRole.AI -> ctx.getString(
            if (engine == AiEngine.GnuGo) R.string.engine_gnugo else R.string.engine_katago
        )
    }

    private inline fun <reified T : Enum<T>> android.content.SharedPreferences.getEnum(
        key: String,
        default: T
    ): T {
        val name = getString(key, null) ?: return default
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }

    override fun onCleared() {
        super.onCleared()
        // Run in a background thread — close() calls pthread_join which
        // may wait for the engine thread to exit and must not block the
        // main thread.
        Thread { engineManager.close() }.start()
    }

    // ── Event handler ──
    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.CellClick -> handleClick(event.row, event.col)
            GameEvent.Pass -> onPass()
            GameEvent.Undo -> onUndo()
            GameEvent.ToggleMoveNumbers -> update { it.copy(showMoveNumbers = !it.showMoveNumbers) }
            GameEvent.Score -> onScore()
            GameEvent.End -> onEnd()
            is GameEvent.ShowNewGameDialog -> update { it.copy(showNewGameDialog = true) }
            is GameEvent.NewGame -> startNewGame(event.config)
            GameEvent.DismissNewGame -> update { it.copy(showNewGameDialog = false) }
            GameEvent.ShowAbout -> update { it.copy(showAboutDialog = true) }
            GameEvent.DismissAbout -> update { it.copy(showAboutDialog = false) }
            GameEvent.SaveSgf -> saveSgf()
            GameEvent.GoToGame -> update { it.copy(currentPage = Page.Game) }
            GameEvent.GoToHistory -> update { it.copy(currentPage = Page.History) }
            is GameEvent.LoadSgf -> loadSgf(event.parsed, event.file)
            is GameEvent.ReviewSgf -> reviewSgf(event.parsed)
            is GameEvent.ReviewIndexChange -> update { it.copy(reviewIndex = event.index) }
            GameEvent.LoadFromReview -> loadFromReview()
            GameEvent.BackFromReview -> update { it.copy(currentPage = Page.History) }
            GameEvent.DismissToast -> update { it.copy(toastMessage = null) }
        }
    }

    // ── Autosave ──
    fun autosaveIfNeeded() {
        val st = boardState
        val ctx = getApplication<Application>()
        val autoSave = File(ctx.filesDir, "autosave.sgf")
        val stt = currentState()
        if (st.moveHistory.isNotEmpty() && !st.gameOver) {
            SgfUtil.exportToFile(st, autoSave, stt.blackConfig.name, stt.whiteConfig.name,
                engineTypeName(aiEngineType(stt.blackConfig, stt.whiteConfig)),
                aiDifficulty(stt.blackConfig, stt.whiteConfig))
        } else {
            autoSave.delete()
        }
    }

    // ── Restore autosave ──
    fun restoreAutosave(saveDir: File): Boolean {
        val autoSave = File(saveDir, "autosave.sgf")
        if (!autoSave.exists()) return false
        val parsed = SgfUtil.parseFromFile(autoSave) ?: return false
        if (parsed.moves.isEmpty()) return false

        goGame.reset(parsed.boardSize)
        if (parsed.handicap > 0) goGame.setHandicap(parsed.handicap)
        for ((row, col) in parsed.moves) {
            if (row < 0) goGame.pass()
            else if (!goGame.placeStone(row, col)) break
        }
        update { u ->
            u.copy(
                boardSize = parsed.boardSize,
                blackConfig = if (parsed.blackName.isNotEmpty()) u.blackConfig.copy(name = parsed.blackName) else u.blackConfig,
                whiteConfig = if (parsed.whiteName.isNotEmpty()) u.whiteConfig.copy(name = parsed.whiteName) else u.whiteConfig
            )
        }
        return true
    }

    // ── Public API for UI ──

    /** Called by GoBoard via GameEvent.CellClick. Routes through event handler. */
    fun requestAiMove() { triggerAiMove() }

    /** Start a new game with current settings without showing the dialog. */
    fun quickNewGame() {
        val prefs = getApplication<Application>()
            .getSharedPreferences(PrefKeys.NAME, android.content.Context.MODE_PRIVATE)
        startNewGame(NewGameConfig(
            boardSize = prefs.getInt(PrefKeys.BOARD_SIZE, 13).coerceIn(9, 19),
            handicap = prefs.getInt(PrefKeys.HANDICAP, 0).coerceIn(0, 9),
            blackPlayer = PlayerConfig(
                role = prefs.getEnum(PrefKeys.BLACK_ROLE, PlayerRole.Human),
                name = prefs.getString(PrefKeys.BLACK_NAME, null) ?: "",
                engine = prefs.getEnum(PrefKeys.BLACK_ENGINE, AiEngine.GnuGo),
                difficulty = prefs.getInt(PrefKeys.BLACK_DIFFICULTY, 5).coerceIn(1, 10),
                backend = prefs.getEnum(PrefKeys.BLACK_BACKEND, ComputeBackend.CPU)
            ),
            whitePlayer = PlayerConfig(
                role = prefs.getEnum(PrefKeys.WHITE_ROLE, PlayerRole.AI),
                name = prefs.getString(PrefKeys.WHITE_NAME, null) ?: "",
                engine = prefs.getEnum(PrefKeys.WHITE_ENGINE, AiEngine.GnuGo),
                difficulty = prefs.getInt(PrefKeys.WHITE_DIFFICULTY, 5).coerceIn(1, 10),
                backend = prefs.getEnum(PrefKeys.WHITE_BACKEND, ComputeBackend.CPU)
            )
        ))
    }

    fun updateScoreFromDeadStones() {
        if (currentState().busyState != AppBusyState.Idle) return
        update { it.copy(showScore = true, currentScore = null, currentEval = null, busyState = AppBusyState.Evaluating, busyMessage = ctx.getString(R.string.evaluating)) }
        viewModelScope.launch {
            val dead = onGtp { getDeadStonesForScoring(boardState) }
            val score = goGame.countTerritory(dead)
            update { it.copy(currentScore = score, busyState = AppBusyState.ShowingScore, busyMessage = "") }
        }
    }

    // ── Private helpers ──

    private val ctx get() = getApplication<Application>()

    private fun toast(msg: String) {
        update { it.copy(toastMessage = msg) }
    }

    // ── Click ──
    private fun handleClick(row: Int, col: Int) {
        if (boardState.gameOver) return

        val moveOk = goGame.placeStone(row, col)
        if (moveOk && !goGame.state.value.gameOver) {
            triggerAiMove()
        }
    }

    private fun isAiTurn(): Boolean {
        val st = currentState()
        val aiActive = st.blackConfig.role == PlayerRole.AI || st.whiteConfig.role == PlayerRole.AI
        if (!aiActive) return false
        return when (boardState.currentPlayer) {
            StoneColor.Black -> st.blackConfig.role == PlayerRole.AI
            StoneColor.White -> st.whiteConfig.role == PlayerRole.AI
        }
    }

    // ── Pass ──
    private fun onPass() {
        if (currentState().busyState == AppBusyState.AiThinking) {
            engineManager.interrupt()
            return
        }
        goGame.pass()
        update { it.copy(showScore = false, currentEval = null, currentScore = null) }
        if (!goGame.state.value.gameOver) {
            triggerAiMove()
        }
    }

    // ── Undo ──
    private fun onUndo() {
        val st = currentState()
        val aiActive = st.blackConfig.role == PlayerRole.AI || st.whiteConfig.role == PlayerRole.AI
        val hist = boardState.moveHistory

        update { it.copy(showScore = false) }

        if (aiActive && hist.size >= 2) {
            val last = hist.last()
            val doubleUndo = (last.stone.color == StoneColor.White && st.whiteConfig.role == PlayerRole.AI)
                || (last.stone.color == StoneColor.Black && st.blackConfig.role == PlayerRole.AI)
            if (doubleUndo) {
                goGame.undo(); goGame.undo()
                aiEngineReady.set(false)
            } else {
                goGame.undo()
                // Keep engine in sync after single human undo
                viewModelScope.launch { onGtp { engineManager.undo() } }
            }
        } else {
            goGame.undo()
            // Human-vs-human also needs engine in sync
            viewModelScope.launch { onGtp { engineManager.undo() } }
        }
    }

    // ── Score / Evaluate ──
    private fun onScore() {
        val st = currentState()
        if (st.showScore) {
            update { it.copy(showScore = false, currentScore = null, currentEval = null, busyState = AppBusyState.Idle, busyMessage = "") }
            return
        }
        if (st.busyState != AppBusyState.Idle) return

        update { it.copy(showScore = true, currentScore = null, currentEval = null, busyState = AppBusyState.Evaluating, busyMessage = ctx.getString(R.string.evaluating)) }

        viewModelScope.launch {
            if (boardState.gameOver) {
                // Endgame: traditional territory scoring with dead stone detection
                val dead = onGtp { getDeadStonesForScoring(boardState) }
                val score = goGame.countTerritory(dead)
                update { it.copy(currentScore = score, busyState = AppBusyState.ShowingScore, busyMessage = "") }
            } else {
                // Midgame: try KataGo neural net evaluation first,
                // fall back to traditional dead stone scoring for GNU Go
                val raw = onGtp { engineManager.analyze(100) }
                val result = parseAnalysis(raw, boardState.size, boardState.currentPlayer)
                if (result != null) {
                    update { it.copy(currentEval = result, busyState = AppBusyState.ShowingScore, busyMessage = "") }
                } else {
                    val dead = onGtp { getDeadStonesForScoring(boardState) }
                    val score = goGame.countTerritory(dead)
                    update { it.copy(currentScore = score, busyState = AppBusyState.ShowingScore, busyMessage = "") }
                }
            }
        }
    }

    // ── End (two passes) ──
    private fun onEnd() {
        val firstPassGameOver = goGame.state.value.gameOver
        goGame.pass()
        if (!goGame.state.value.gameOver) {
            goGame.pass()
        }
        // Sync pass moves to engine so dead-stone scoring is accurate
        viewModelScope.launch {
            onGtp { engineManager.playMove(-1, -1, true) }
            if (!firstPassGameOver) {
                onGtp { engineManager.playMove(-1, -1, false) }
            }
        }
    }

    // ── New game ──
    private fun startNewGame(config: NewGameConfig) {
        aiGeneration.incrementAndGet()
        update {
            it.copy(
                blackConfig = config.blackPlayer,
                whiteConfig = config.whitePlayer,
                busyState = AppBusyState.Initializing,
                busyMessage = ctx.getString(
                    if (!currentState().gpuTuningCompleted && (config.blackPlayer.backend == ComputeBackend.GPU || config.whitePlayer.backend == ComputeBackend.GPU))
                        R.string.engine_starting_gpu_tuning
                    else R.string.engine_starting
                ),
                showNewGameDialog = false,
                showScore = false,
                currentScore = null,
                currentEval = null,
                boardSize = config.boardSize
            )
        }
        aiEngineReady.set(false)

        goGame.reset(config.boardSize)
        if (config.handicap > 0) goGame.setHandicap(config.handicap)

        val aiActive = config.blackPlayer.role == PlayerRole.AI || config.whitePlayer.role == PlayerRole.AI
        if (!aiActive) {
            update { it.copy(busyState = AppBusyState.Idle, busyMessage = "") }
            return
        }

        viewModelScope.launch {
            try {
                onGtp { engineManager.close() }
                val aiPlayer = if (config.blackPlayer.role == PlayerRole.AI) config.blackPlayer else config.whitePlayer
                val engKomi = if (config.handicap > 0) config.handicap / 2f else 3.75f
                initAiEngine(aiPlayer, config.boardSize, engKomi)

                if (aiPlayer.backend == ComputeBackend.GPU && !currentState().gpuTuningCompleted) {
                    toast("${ctx.getString(R.string.toast_gpu_tuning_complete)}")
                }
                update { it.copy(busyState = AppBusyState.Idle, busyMessage = "") }

                val st = goGame.state.value
                val aiFirst = (st.currentPlayer == StoneColor.Black && config.blackPlayer.role == PlayerRole.AI)
                           || (st.currentPlayer == StoneColor.White && config.whitePlayer.role == PlayerRole.AI)
                if (aiFirst) triggerAiMove()
            } catch (e: Exception) {
                Log.e(TAG, "Engine start failed", e)
                update { it.copy(busyState = AppBusyState.Idle, busyMessage = "") }
                toast("${ctx.getString(R.string.toast_ai_start_failed, e.message ?: "")}")
            }
        }
    }

    // ── Init AI engine ──
    // Only one coroutine ever calls this: either startNewGame or triggerAiMove.
    // The app does not support AI-vs-AI, so there is no concurrent init scenario.
    // See memory: [[no-ai-vs-ai]]
    private suspend fun initAiEngine(aiPlayer: PlayerConfig, boardSize: Int, komi: Float) {
        if (aiEngineReady.get()) return

        val engineType = when {
            aiPlayer.engine == AiEngine.KataGo && aiPlayer.backend == ComputeBackend.GPU -> EngineType.KataGoGPU
            aiPlayer.engine == AiEngine.KataGo -> EngineType.KataGoCPU
            else -> EngineType.GnuGo
        }
        onGtp { engineManager.ensureEngine(engineType, aiPlayer.difficulty, aiPlayer.backend) }
        onGtp { engineManager.engineInit(boardSize, komi) }

        // Mark GPU tuning as complete after first successful init
        if (aiPlayer.backend == ComputeBackend.GPU && !currentState().gpuTuningCompleted) {
            ctx.getSharedPreferences(PrefKeys.NAME, android.content.Context.MODE_PRIVATE)
                .edit().putBoolean(PrefKeys.KATAGO_GPU_TUNING_COMPLETED, true).apply()
            update { it.copy(gpuTuningCompleted = true) }
        }

        val stones = goGame.state.value.stones
        for ((pos, color) in stones) {
            val (row, col) = pos
            onGtp { engineManager.playMove(row, col, color == StoneColor.Black) }
        }
        aiEngineReady.set(true)
    }

    // ── Trigger AI move ──
    private fun triggerAiMove() {
        val st = goGame.state.value
        val gen = aiGeneration.get()
        if (st.gameOver) return

        val aiTurn = when (st.currentPlayer) {
            StoneColor.Black -> currentState().blackConfig.role == PlayerRole.AI
            StoneColor.White -> currentState().whiteConfig.role == PlayerRole.AI
        }
        if (!aiTurn) return
        if (!aiMoveInFlight.compareAndSet(false, true)) return

        update { it.copy(busyState = AppBusyState.AiThinking, busyMessage = ctx.getString(R.string.ai_thinking)) }

        viewModelScope.launch {
            try {
                val justInitialized = !aiEngineReady.get()
                if (justInitialized) {
                    val aiPlayer = if (st.currentPlayer == StoneColor.Black) currentState().blackConfig else currentState().whiteConfig
                    onGtp { initAiEngine(aiPlayer, st.size, st.komi) }
                }

                if (!justInitialized) {
                    val lastMove = st.moveHistory.lastOrNull()
                    if (lastMove != null) {
                        onGtp { engineManager.playMove(lastMove.stone.row, lastMove.stone.col,
                            lastMove.stone.color == StoneColor.Black) }
                    }
                }

                val aiBlack = st.currentPlayer == StoneColor.Black
                val ok = onGtp { engineManager.generateMove(aiBlack) }

                if (ok) {
                    // Let the "AI thinking" indicator remain visible briefly
                    // so the user can perceive the AI has responded.
                    kotlinx.coroutines.delay(500)
                    if (aiGeneration.get() != gen) return@launch

                    val moveStr = engineManager.getLastGeneratedMove()
                    val (aiRow, aiCol) = gtpToBoardPos(moveStr, st.size)
                    if (aiRow >= 0 && aiCol >= 0) {
                        val placed = goGame.placeStone(aiRow, aiCol)
                        if (!placed) {
                            onGtp { engineManager.undo() }
                            goGame.pass()
                        }
                    } else {
                        goGame.pass()
                    }
                    // Brief pause to let the board state flow propagate before
                    // busyState is reset to Idle in the finally block below.
                    kotlinx.coroutines.delay(50)
                } else {
                    onGtp { engineManager.close() }
                    aiEngineReady.set(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "triggerAiMove gen=$gen exception: ${e.message}", e)
                toast("${ctx.getString(R.string.toast_ai_error, e.message ?: "")}")
                onGtp { engineManager.close() }
                aiEngineReady.set(false)
            } finally {
                update { it.copy(busyState = AppBusyState.Idle, busyMessage = "") }
                aiMoveInFlight.set(false)
                // If AI's move ended the game, trigger scoring now that busyState is clear
                if (goGame.state.value.gameOver) {
                    updateScoreFromDeadStones()
                }
            }
        }
    }

    // ── Save SGF ──
    private fun saveSgf() {
        try {
            val st = currentState()
            val dir = File(ctx.filesDir, SgfConstants.DIR)
            dir.mkdirs()
            val ts = SimpleDateFormat(SgfConstants.DATE_FORMAT, Locale.US).format(Date())
            val file = File(dir, "${SgfConstants.FILE_PREFIX}$ts${SgfConstants.FILE_SUFFIX}")
            SgfUtil.exportToFile(goGame.state.value, file, st.blackConfig.name, st.whiteConfig.name,
                engineTypeName(aiEngineType(st.blackConfig, st.whiteConfig)),
                aiDifficulty(st.blackConfig, st.whiteConfig))
            toast("${ctx.getString(R.string.toast_saved, file.name)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save SGF", e)
            toast("${ctx.getString(R.string.toast_save_failed, e.message ?: "")}")
        }
    }

    // ── Load SGF ──
    // lock → await sync → unlock → maybe trigger AI
    private fun loadSgf(parsed: ParsedSgf, file: File) {
        aiEngineReady.set(false)
        engineManager.close()
        val st = currentState()

        val newBlack = if (parsed.blackName.isNotEmpty()) st.blackConfig.copy(name = parsed.blackName) else st.blackConfig
        val newWhite = if (parsed.whiteName.isNotEmpty()) st.whiteConfig.copy(name = parsed.whiteName) else st.whiteConfig

        val finalBlack: PlayerConfig
        val finalWhite: PlayerConfig
        if (parsed.engineTypeName.isNotEmpty()) {
            val et = engineTypeFromName(parsed.engineTypeName)
            val etAi = et.toAiEngine()
            finalBlack = if (st.blackConfig.role == PlayerRole.AI)
                newBlack.copy(engine = etAi, difficulty = parsed.aiDifficulty) else newBlack
            finalWhite = if (st.whiteConfig.role == PlayerRole.AI)
                newWhite.copy(engine = etAi, difficulty = parsed.aiDifficulty) else newWhite
        } else {
            finalBlack = newBlack; finalWhite = newWhite
        }

        goGame.reset(parsed.boardSize)
        goGame.setKomi(parsed.komi)
        if (parsed.handicap > 0) goGame.setHandicap(parsed.handicap)
        for ((row, col) in parsed.moves) {
            if (row < 0) goGame.pass()
            else if (!goGame.placeStone(row, col)) break
        }

        // 1. lock UI — also switch to Game page so the overlay is visible
        update {
            it.copy(
                blackConfig = finalBlack,
                whiteConfig = finalWhite,
                busyState = AppBusyState.Initializing,
                busyMessage = ctx.getString(R.string.loading_game),
                currentPage = Page.Game,
                loadedEngineType = if (parsed.engineTypeName.isNotEmpty())
                    engineTypeFromName(parsed.engineTypeName) else null,
                loadedAiDifficulty = parsed.aiDifficulty
            )
        }

        // 2. await engine sync, 3. unlock, 4. trigger AI if needed
        val et = if (parsed.engineTypeName.isNotEmpty())
            engineTypeFromName(parsed.engineTypeName) else EngineType.KataGoCPU
        viewModelScope.launch {
            try {
                syncEngineToBoard(et, parsed.aiDifficulty, parsed.boardSize, parsed.komi, parsed.handicap)
                update { it.resetToGame() }
                toast("${ctx.getString(R.string.toast_load_complete)}")
                if (!goGame.state.value.gameOver && isAiTurn()) triggerAiMove()
            } catch (e: Exception) {
                update { it.copy(busyState = AppBusyState.Idle, busyMessage = "") }
                toast("${ctx.getString(R.string.toast_ai_start_failed, e.message ?: "")}")
            }
        }
        toast("${ctx.getString(R.string.toast_loaded, file.name)}")
    }

    // ── Review SGF ──
    private fun reviewSgf(parsed: ParsedSgf) {
        val st = currentState()
        update {
            it.copy(
                reviewMoves = parsed.moves,
                reviewSize = parsed.boardSize,
                reviewKomi = parsed.komi,
                reviewHandicap = parsed.handicap,
                reviewIndex = if (parsed.moves.isEmpty()) 0 else parsed.moves.size,
                reviewEngineTypeName = parsed.engineTypeName,
                reviewAiDifficulty = parsed.aiDifficulty,
                blackConfig = if (parsed.blackName.isNotEmpty()) st.blackConfig.copy(name = parsed.blackName) else st.blackConfig,
                whiteConfig = if (parsed.whiteName.isNotEmpty()) st.whiteConfig.copy(name = parsed.whiteName) else st.whiteConfig,
                currentPage = Page.Review
            )
        }
    }

    // ── Load from review ──
    // lock → await sync → unlock → maybe trigger AI
    private fun loadFromReview() {
        val st = currentState()
        aiEngineReady.set(false)
        engineManager.close()

        goGame.reset(st.reviewSize)
        goGame.setKomi(st.reviewKomi)
        if (st.reviewHandicap > 0) goGame.setHandicap(st.reviewHandicap)
        for ((row, col) in st.reviewMoves) {
            if (row < 0) goGame.pass()
            else if (!goGame.placeStone(row, col)) break
        }

        // 1. lock UI — also switch to Game page so the overlay is visible
        update { it.copy(busyState = AppBusyState.Initializing, busyMessage = ctx.getString(R.string.loading_game), currentPage = Page.Game) }

        // 2. await engine sync, 3. unlock, 4. trigger AI if needed
        val et = if (st.reviewEngineTypeName.isNotEmpty())
            engineTypeFromName(st.reviewEngineTypeName) else EngineType.KataGoCPU
        viewModelScope.launch {
            try {
                syncEngineToBoard(et, st.reviewAiDifficulty, st.reviewSize, st.reviewKomi, st.reviewHandicap)
                update { it.resetToGame() }
                toast("${ctx.getString(R.string.toast_load_complete)}")
                if (!goGame.state.value.gameOver && isAiTurn()) triggerAiMove()
            } catch (e: Exception) {
                update { it.copy(busyState = AppBusyState.Idle, busyMessage = "") }
            }
        }
    }

    // ── Engine sync ──

    /** Start a fresh engine and replay the current board state.
     *  Used by both [loadSgf] and [loadFromReview] so scoring works. */
    private suspend fun syncEngineToBoard(
        engineType: EngineType,
        difficulty: Int,
        boardSize: Int,
        komi: Float,
        handicap: Int
    ) {
        try {
            onGtp {
                engineManager.ensureEngine(engineType, difficulty, ComputeBackend.CPU)
                engineManager.engineInit(boardSize, komi)
                if (handicap > 0) {
                    engineManager.setHandicap(handicap)
                }
                for (move in goGame.state.value.moveHistory) {
                    engineManager.playMove(
                        move.stone.row, move.stone.col,
                        move.stone.color == StoneColor.Black)
                }
            }
        } catch (_: Exception) { /* scoring will return empty on engine failure */ }
    }

    // ── Engine helpers ──
    private fun aiEngineType(black: PlayerConfig, white: PlayerConfig): EngineType {
        val aiPlayer = when {
            black.role == PlayerRole.AI -> black
            white.role == PlayerRole.AI -> white
            else -> return EngineType.KataGoCPU
        }
        return when (aiPlayer.engine) {
            AiEngine.GnuGo -> EngineType.GnuGo
            AiEngine.KataGo -> when (aiPlayer.backend) {
                ComputeBackend.GPU -> EngineType.KataGoGPU
                ComputeBackend.CPU -> EngineType.KataGoCPU
            }
        }
    }

    private fun aiDifficulty(black: PlayerConfig, white: PlayerConfig): Int = when {
        black.role == PlayerRole.AI -> black.difficulty
        white.role == PlayerRole.AI -> white.difficulty
        else -> 5
    }

    private fun engineTypeFromName(name: String): EngineType = when (name) {
        "katago_gpu" -> EngineType.KataGoGPU
        "katago_cpu" -> EngineType.KataGoCPU
        else -> EngineType.GnuGo
    }

    private fun EngineType.toAiEngine(): AiEngine = when (this) {
        EngineType.GnuGo -> AiEngine.GnuGo
        EngineType.KataGoCPU, EngineType.KataGoGPU -> AiEngine.KataGo
    }

    private fun engineTypeName(et: EngineType): String = when (et) {
        EngineType.KataGoGPU -> "katago_gpu"
        EngineType.KataGoCPU -> "katago_cpu"
        EngineType.GnuGo -> "gnugo"
    }

    private fun getDeadStonesForScoring(state: BoardState): Set<Pair<Int, Int>> {
        return engineManager.getDeadStones(state.size)
    }

    companion object {
        private const val TAG = "AndroidGo"
    }
    }