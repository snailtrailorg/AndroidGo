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
    val showScore: Boolean = false,
    val showMoveNumbers: Boolean = false,
    val currentEval: EvalResult? = null,
    val currentScore: TerritoryScore? = null,
    val loadedEngineType: EngineType? = null,
    val loadedAiDifficulty: Int = 5,

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

    // Toast
    val toastMessage: String? = null
)

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

    // ── Engine guards (leave them for now; migrate into AppBusyState gradually) ──
    val aiEngineReady = AtomicBoolean(false)
    val aiEngineInitializing = AtomicBoolean(false)
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
    private fun s() = _state.value
    private fun update(f: (UiState) -> UiState) = _state.update(f)

    // ── Init (called from activity) ──
    fun init(engineMgr: EngineManager, savedConfig: NewGameConfig) {
        engineManager = engineMgr
        update {
            it.copy(
                boardSize = savedConfig.boardSize,
                blackConfig = savedConfig.blackPlayer,
                whiteConfig = savedConfig.whitePlayer
            )
        }
        goGame.reset(savedConfig.boardSize)
        if (savedConfig.handicap > 0) goGame.setHandicap(savedConfig.handicap)
    }

    override fun onCleared() {
        super.onCleared()
        engineManager.close()
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
        val stt = s()
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

    /** Called by GoBoardScreen via GameEvent.CellClick. Routes through event handler. */
    fun requestAiMove() { triggerAiMove() }

    fun updateScoreFromDeadStones() {
        update { it.copy(showScore = true, currentScore = null, currentEval = null, busyState = AppBusyState.Evaluating) }
        viewModelScope.launch {
            val dead = onGtp { getDeadStonesForScoring(boardState) }
            val score = goGame.countTerritory(dead)
            update { it.copy(currentScore = score, busyState = AppBusyState.ShowingScore) }
        }
    }

    // ── Private helpers ──

    private val ctx get() = getApplication<Application>()

    private fun toast(msg: String) {
        update { it.copy(toastMessage = msg) }
    }

    // ── Board input guard ──
    private fun boardLocked(): Boolean {
        val b = s().busyState
        return b == AppBusyState.AiThinking || b == AppBusyState.Initializing ||
               b == AppBusyState.Evaluating || s().showScore
    }

    private fun isAiTurn(): Boolean {
        val st = s()
        val aiActive = st.blackConfig.role == PlayerRole.AI || st.whiteConfig.role == PlayerRole.AI
        if (!aiActive) return false
        return when (boardState.currentPlayer) {
            StoneColor.Black -> st.blackConfig.role == PlayerRole.AI
            StoneColor.White -> st.whiteConfig.role == PlayerRole.AI
        }
    }

    // ── Click ──
    private fun handleClick(row: Int, col: Int) {
        if (boardLocked()) return
        if (boardState.gameOver) return
        if (isAiTurn()) return

        val moveOk = goGame.placeStone(row, col)
        if (moveOk && !goGame.state.value.gameOver) {
            triggerAiMove()
        }
    }

    // ── Pass ──
    private fun onPass() {
        if (s().busyState == AppBusyState.AiThinking) {
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
        val st = s()
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
            }
        } else {
            goGame.undo()
        }
    }

    // ── Score / Evaluate ──
    private fun onScore() {
        val st = s()
        if (st.showScore) {
            update { it.copy(showScore = false, currentScore = null, currentEval = null, busyState = AppBusyState.Idle) }
            return
        }
        if (st.busyState != AppBusyState.Idle) return

        update { it.copy(showScore = true, currentScore = null, currentEval = null, busyState = AppBusyState.Evaluating) }

        viewModelScope.launch {
            if (boardState.gameOver) {
                // Endgame: traditional territory scoring with dead stone detection
                val dead = onGtp { getDeadStonesForScoring(boardState) }
                val score = goGame.countTerritory(dead)
                update { it.copy(currentScore = score, busyState = AppBusyState.ShowingScore) }
            } else {
                // Midgame: try KataGo neural net evaluation first,
                // fall back to traditional dead stone scoring for GNU Go
                val raw = onGtp { engineManager.analyze(100) }
                val result = parseKataAnalyze(raw, boardState.size, boardState.currentPlayer)
                if (result != null) {
                    update { it.copy(currentEval = result, busyState = AppBusyState.ShowingScore) }
                } else {
                    val dead = onGtp { getDeadStonesForScoring(boardState) }
                    val score = goGame.countTerritory(dead)
                    update { it.copy(currentScore = score, busyState = AppBusyState.ShowingScore) }
                }
            }
        }
    }

    // ── End (two passes) ──
    private fun onEnd() {
        goGame.pass()
        if (!goGame.state.value.gameOver) {
            goGame.pass()
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
                showNewGameDialog = false,
                showScore = false,
                currentScore = null,
                currentEval = null,
                boardSize = config.boardSize
            )
        }
        aiEngineReady.set(false)
        aiEngineInitializing.set(false)

        goGame.reset(config.boardSize)
        if (config.handicap > 0) goGame.setHandicap(config.handicap)

        val aiActive = config.blackPlayer.role == PlayerRole.AI || config.whitePlayer.role == PlayerRole.AI
        if (!aiActive) {
            update { it.copy(busyState = AppBusyState.Idle) }
            return
        }

        viewModelScope.launch {
            try {
                onGtp { engineManager.close() }
                val aiPlayer = if (config.blackPlayer.role == PlayerRole.AI) config.blackPlayer else config.whitePlayer
                val engKomi = if (config.handicap > 0) config.handicap / 2f else 3.75f
                initAiEngine(aiPlayer, config.boardSize, engKomi)

                update { it.copy(busyState = AppBusyState.Idle) }

                val st = goGame.state.value
                val aiFirst = (st.currentPlayer == StoneColor.Black && config.blackPlayer.role == PlayerRole.AI)
                           || (st.currentPlayer == StoneColor.White && config.whitePlayer.role == PlayerRole.AI)
                if (aiFirst) triggerAiMove()
            } catch (e: Exception) {
                Log.e(TAG, "Engine start failed", e)
                update { it.copy(busyState = AppBusyState.Idle) }
                toast("${ctx.getString(R.string.toast_ai_start_failed, e.message ?: "")}")
            }
        }
    }

    // ── Init AI engine ──
    private suspend fun initAiEngine(aiPlayer: PlayerConfig, boardSize: Int, komi: Float) {
        if (aiEngineReady.get()) return
        if (!aiEngineInitializing.compareAndSet(false, true)) {
            var waited = 0
            while (aiEngineInitializing.get() && waited < 300) {
                kotlinx.coroutines.delay(100)
                waited++
            }
            if (aiEngineReady.get()) return
            if (aiEngineInitializing.get()) throw IllegalStateException("AI engine init timeout")
        }
        try {
            val engineType = when {
                aiPlayer.engine == AiEngine.KataGo && aiPlayer.backend == ComputeBackend.GPU -> EngineType.KataGoGPU
                aiPlayer.engine == AiEngine.KataGo -> EngineType.KataGoCPU
                else -> EngineType.GnuGo
            }
            onGtp { engineManager.ensureEngine(engineType, aiPlayer.difficulty, aiPlayer.backend) }
            onGtp { engineManager.engineInit(boardSize, komi) }

            val stones = goGame.state.value.stones
            for ((pos, color) in stones) {
                val (row, col) = pos
                onGtp { engineManager.playMove(row, col, color == StoneColor.Black) }
            }
            aiEngineReady.set(true)
        } catch (e: Exception) {
            aiEngineReady.set(false)
            throw e
        } finally {
            aiEngineInitializing.set(false)
        }
    }

    // ── Trigger AI move ──
    private fun triggerAiMove() {
        val st = goGame.state.value
        val gen = aiGeneration.get()
        if (st.gameOver) return

        val aiTurn = when (st.currentPlayer) {
            StoneColor.Black -> s().blackConfig.role == PlayerRole.AI
            StoneColor.White -> s().whiteConfig.role == PlayerRole.AI
        }
        if (!aiTurn) return
        if (!aiMoveInFlight.compareAndSet(false, true)) return

        update { it.copy(busyState = AppBusyState.AiThinking) }

        viewModelScope.launch {
            try {
                val justInitialized = !aiEngineReady.get()
                if (justInitialized) {
                    val aiPlayer = if (st.currentPlayer == StoneColor.Black) s().blackConfig else s().whiteConfig
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
                update { it.copy(busyState = AppBusyState.Idle) }
                aiMoveInFlight.set(false)
            }
        }
    }

    // ── Save SGF ──
    private fun saveSgf() {
        try {
            val st = s()
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
    private fun loadSgf(parsed: ParsedSgf, file: File) {
        aiEngineReady.set(false)
        engineManager.close()
        val st = s()

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

        update {
            it.copy(
                blackConfig = finalBlack,
                whiteConfig = finalWhite,
                showScore = false,
                currentScore = null,
                currentEval = null,
                loadedEngineType = if (parsed.engineTypeName.isNotEmpty())
                    engineTypeFromName(parsed.engineTypeName) else null,
                loadedAiDifficulty = parsed.aiDifficulty,
                currentPage = Page.Game
            )
        }
        toast("${ctx.getString(R.string.toast_loaded, file.name)}")
    }

    // ── Review SGF ──
    private fun reviewSgf(parsed: ParsedSgf) {
        val st = s()
        update {
            it.copy(
                reviewMoves = parsed.moves,
                reviewSize = parsed.boardSize,
                reviewKomi = parsed.komi,
                reviewHandicap = parsed.handicap,
                reviewIndex = if (parsed.moves.isEmpty()) 0 else parsed.moves.size,
                blackConfig = if (parsed.blackName.isNotEmpty()) st.blackConfig.copy(name = parsed.blackName) else st.blackConfig,
                whiteConfig = if (parsed.whiteName.isNotEmpty()) st.whiteConfig.copy(name = parsed.whiteName) else st.whiteConfig,
                currentPage = Page.Review
            )
        }
    }

    // ── Load from review ──
    private fun loadFromReview() {
        val st = s()
        aiEngineReady.set(false)
        engineManager.close()

        goGame.reset(st.reviewSize)
        goGame.setKomi(st.reviewKomi)
        for ((row, col) in st.reviewMoves) {
            if (row < 0) goGame.pass()
            else if (!goGame.placeStone(row, col)) break
        }

        update {
            it.copy(
                showScore = false,
                currentScore = null,
                currentEval = null,
                currentPage = Page.Game
            )
        }
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