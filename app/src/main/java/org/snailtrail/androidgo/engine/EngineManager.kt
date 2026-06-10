package org.snailtrail.androidgo.engine

import android.content.Context
import android.util.Log
import org.snailtrail.androidgo.ui.ComputeBackend
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class EngineType(val binaryName: String, val cliArgs: String) {
    GnuGo("libgnugo.so", "--mode gtp --level %LEVEL%"),
    KataGoGPU("libkatago_gpu.so", "gtp -config %CONFIG% -model %MODEL% -override-config %OVERRIDE%"),
    KataGoCPU("libkatago_cpu.so", "gtp -config %CONFIG% -model %MODEL% -override-config %OVERRIDE%")
}

class EngineManager(private val context: Context) {

    private val mutex = Mutex()
    private var engine: GtpEngine? = null
    private var currentType: EngineType? = null

    val isRunning: Boolean get() = engine?.isRunning() == true

    val modelName: String get() = MODEL_NAME

    private fun maxVisitsForDifficulty(level: Int): Int =
        VISIT_LEVELS.getOrElse(level.coerceIn(1, 10) - 1) { 500 }

    suspend fun ensureEngine(type: EngineType = EngineType.GnuGo, difficulty: Int = 5, backend: ComputeBackend = ComputeBackend.CPU): GtpEngine = mutex.withLock {
        if (type == currentType && engine?.isRunning() == true) {
            return@withLock engine ?: throw IllegalStateException("Engine marked running but is null")
        }
        close()

        val args = when (type) {
            EngineType.KataGoGPU, EngineType.KataGoCPU -> {
                val modelPath = withContext(Dispatchers.IO) { extractModel() }
                val configPath = withContext(Dispatchers.IO) { extractConfig() }
                val visits = maxVisitsForDifficulty(difficulty)
                val override = "maxVisits=$visits,maxTime=300"
                type.cliArgs.replace("%MODEL%", modelPath)
                    .replace("%CONFIG%", configPath)
                    .replace("%OVERRIDE%", override)
            }
            EngineType.GnuGo -> type.cliArgs.replace("%LEVEL%", difficulty.toString())
        }

        val cmd = "${type.binaryName} $args"
        Log.d(TAG, "Starting engine: $cmd")
        val e = GtpEngine()

        val ok = e.start(cmd)

        Log.d(TAG, "Engine start result: $ok, name=${e.state.value.engineName}")
        if (!ok) { e.close(); check(false) { "Failed to start ${type.name} engine" } }

        currentType = type
        engine = e
        return e
    }

    @Deprecated("Use proxy methods instead", level = DeprecationLevel.ERROR)
    fun getEngine(): GtpEngine? = engine

    // ── GTP proxy methods ──

    fun analyze(centiseconds: Int = 100): String {
        return engine?.analyze(centiseconds) ?: ""
    }

    fun interrupt() {
        engine?.interrupt()
    }

    fun playMove(row: Int, col: Int, black: Boolean): Boolean {
        return engine?.playMove(row, col, black) ?: false
    }

    fun generateMove(black: Boolean): Boolean {
        return engine?.generateMove(black) ?: false
    }

    fun engineInit(boardSize: Int, komi: Float): Boolean {
        return engine?.init(boardSize, komi) ?: false
    }

    fun setHandicap(n: Int): Boolean {
        return engine?.setFixedHandicap(n) ?: false
    }

    fun undo(): Boolean {
        return engine?.undo() ?: false
    }

    fun getLastGeneratedMove(): String {
        return engine?.getLastGeneratedMove() ?: ""
    }

    fun getDeadStones(boardSize: Int): Set<Pair<Int, Int>> {
        return try { engine?.getDeadStones(boardSize) ?: emptySet() } catch (_: Exception) { emptySet() }
    }

    fun close() {
        engine?.interrupt()
        Thread.sleep(200)  // give engine time to process the interrupt signal
        engine?.close()
        engine = null
        currentType = null
    }

    private suspend fun extractModel(): String = withContext(Dispatchers.IO) {
        extractAsset("engine/katago_model.txt", "katago_model.txt")
    }

    private suspend fun extractConfig(): String = withContext(Dispatchers.IO) {
        extractAsset("engine/katago.cfg", "katago.cfg")
    }

    private fun extractAsset(assetPath: String, fileName: String): String {
        val destDir = File(context.filesDir, "engine/model")
        destDir.mkdirs()
        val destFile = File(destDir, fileName)

        if (!destFile.exists()) {
            val tmpFile = File(destDir, "$fileName.${System.currentTimeMillis()}.tmp")
            context.assets.open(assetPath).use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            try {
                Files.move(tmpFile.toPath(), destFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                // Atomic move failed (e.g. cross-filesystem) — fall back to non-atomic
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
            }
        }

        return destFile.absolutePath
    }

    companion object {
        private const val TAG = "EngineManager"
    }
}
