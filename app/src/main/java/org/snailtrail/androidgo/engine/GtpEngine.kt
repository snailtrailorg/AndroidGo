package org.snailtrail.androidgo.engine

import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.snailtrail.androidgo.game.gtpToBoardPos

data class EngineState(
    val running: Boolean = false,
    val engineName: String = "",
    val engineVersion: String = "",
    val boardSize: Int = 19,
    val isBlackTurn: Boolean = true,
    val consecutivePasses: Int = 0,
    val finalScore: String = "",
    val estimatedScore: String = ""
)

class GtpEngine : Closeable {

    private var nativePtr: Long = 0
    private var closed = false
    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    companion object {
        init {
            System.loadLibrary("gtp_client")
        }
    }

    init {
        nativePtr = nativeCreate()
        check(nativePtr != 0L) { "Failed to create native GTP client" }
    }

    fun start(command: String): Boolean {
        check(!closed) { "Engine is closed" }
        val ok = nativeStart(nativePtr, command)
        if (ok) {
            _state.value = _state.value.copy(
                running = true,
                engineName = nativeGetEngineName(nativePtr),
                engineVersion = nativeGetEngineVersion(nativePtr)
            )
        }
        return ok
    }

    fun stop() {
        if (closed) return
        nativeStop(nativePtr)
        _state.value = _state.value.copy(running = false)
    }

    fun init(boardSize: Int = 19, komi: Float = 3.75f): Boolean {
        check(!closed) { "Engine is closed" }
        val ok = nativeInit(nativePtr, boardSize, komi)
        if (ok) {
            _state.value = _state.value.copy(
                boardSize = boardSize,
                isBlackTurn = true,
                consecutivePasses = 0
            )
        }
        return ok
    }

    fun playMove(row: Int, col: Int, black: Boolean): Boolean {
        check(!closed) { "Engine is closed" }
        val ok = nativePlayMove(nativePtr, black, row, col)
        if (ok) {
            _state.value = _state.value.copy(
                isBlackTurn = !black,
                consecutivePasses = 0
            )
        }
        return ok
    }

    fun pass(black: Boolean): Boolean {
        check(!closed) { "Engine is closed" }
        val ok = nativePass(nativePtr, black)
        if (ok) {
            val newPasses = _state.value.consecutivePasses + 1
            _state.value = _state.value.copy(
                isBlackTurn = !black,
                consecutivePasses = newPasses
            )
        }
        return ok
    }

    fun generateMove(black: Boolean): Boolean {
        check(!closed) { "Engine is closed" }
        val ok = nativeGenerateMove(nativePtr, black)
        if (ok) {
            _state.value = _state.value.copy(isBlackTurn = !black)
        }
        return ok
    }

    fun undo(): Boolean {
        check(!closed) { "Engine is closed" }
        val ok = nativeUndo(nativePtr)
        if (ok) {
            _state.value = _state.value.copy(
                isBlackTurn = !_state.value.isBlackTurn,
                consecutivePasses = 0
            )
        }
        return ok
    }

    fun getFinalScore(): String = nativeFinalScore(nativePtr)

    fun getEstimatedScore(): String = nativeEstimatedScore(nativePtr)

    fun getLastGeneratedMove(): String = nativeGetLastGeneratedMove(nativePtr)

    fun getDeadStones(): Set<Pair<Int, Int>> {
        if (closed) return emptySet()
        val boardSize = _state.value.boardSize
        val resp = nativeDeadStones(nativePtr)
        val result = mutableSetOf<Pair<Int, Int>>()
        for (coord in resp.split(" ")) {
            val pos = gtpToBoardPos(coord, boardSize)
            if (pos.first >= 0) result.add(pos)
        }
        return result
    }

    fun interrupt() {
        if (closed) return
        nativeInterrupt(nativePtr)
    }

    fun isRunning(): Boolean = !closed && nativeIsRunning(nativePtr)

    fun isBlackTurn(): Boolean = nativeIsBlackTurn(nativePtr)

    override fun close() {
        if (closed) return
        closed = true
        stop()
        nativeDestroy(nativePtr)
        nativePtr = 0
    }

    // --- JNI native methods ---

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(ptr: Long)
    private external fun nativeStart(ptr: Long, command: String): Boolean
    private external fun nativeAttachToProcess(ptr: Long, pid: Int, stdinFd: Int, stdoutFd: Int): Boolean
    private external fun nativeStop(ptr: Long)
    private external fun nativeIsRunning(ptr: Long): Boolean
    private external fun nativeInit(ptr: Long, boardSize: Int, komi: Float): Boolean
    private external fun nativePlayMove(ptr: Long, black: Boolean, row: Int, col: Int): Boolean
    private external fun nativePass(ptr: Long, black: Boolean): Boolean
    private external fun nativeGenerateMove(ptr: Long, black: Boolean): Boolean
    private external fun nativeUndo(ptr: Long): Boolean
    private external fun nativeGetEngineName(ptr: Long): String
    private external fun nativeGetEngineVersion(ptr: Long): String
    private external fun nativeFinalScore(ptr: Long): String
    private external fun nativeEstimatedScore(ptr: Long): String
    private external fun nativeGetBoardSize(ptr: Long): Int
    private external fun nativeIsBlackTurn(ptr: Long): Boolean
    private external fun nativeGetConsecutivePasses(ptr: Long): Int
    private external fun nativeGetLastGeneratedMove(ptr: Long): String
    private external fun nativeInterrupt(ptr: Long)
    private external fun nativeDeadStones(ptr: Long): String
    private external fun nativeGetStones(ptr: Long, black: Boolean): String
}
