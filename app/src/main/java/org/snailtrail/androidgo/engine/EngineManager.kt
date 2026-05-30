package org.snailtrail.androidgo.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class EngineType(val binaryName: String, val cliArgs: String) {
    GnuGo("libgnugo_engine.so", "--mode gtp"),
    KataGo("libkatago_engine.so", "gtp -config %CONFIG% -model %MODEL%")
}

class EngineManager(private val context: Context) {

    private var engine: GtpEngine? = null
    private var enginePath: String? = null
    private var currentType: EngineType? = null

    val isRunning: Boolean get() = engine?.isRunning() == true

    private val nativeLibDir: String by lazy {
        context.applicationInfo.nativeLibraryDir
    }

    suspend fun ensureEngine(type: EngineType = EngineType.GnuGo, difficulty: Int = 5): GtpEngine {
        if (type == currentType && engine?.isRunning() == true) {
            return engine!!
        }
        close()

        enginePath = withContext(Dispatchers.IO) {
            val path = "$nativeLibDir/${type.binaryName}"
            val f = File(path)
            if (f.exists() && !f.canExecute() && !f.setExecutable(true, true)) {
                Log.w(TAG, "Failed to set executable permission on $path")
            }
            path
        }

        val args = if (type == EngineType.KataGo) {
            val modelPath = withContext(Dispatchers.IO) { extractModel() }
            val configPath = withContext(Dispatchers.IO) { extractConfig() }
            type.cliArgs.replace("%MODEL%", modelPath)
                .replace("%CONFIG%", configPath)
        } else {
            type.cliArgs
        }

        val cmd = "$enginePath $args"
        Log.d(TAG, "Starting engine: $cmd")
        val e = GtpEngine()

        val ok = e.start(cmd)

        Log.d(TAG, "Engine start result: $ok, name=${e.state.value.engineName}")
        if (!ok) { e.close(); check(false) { "Failed to start ${type.name} engine" } }

        currentType = type
        engine = e
        return e
    }

    fun getEngine(): GtpEngine? = engine

    fun close() {
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
            val tmpFile = File(destDir, "$fileName.tmp")
            tmpFile.delete()
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
