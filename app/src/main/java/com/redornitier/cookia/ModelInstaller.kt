package com.redornitier.cookia

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class InstallProgress(
    val totalFiles: Int,
    val copiedFiles: Int
)

object ModelInstaller {

    /**
     * Copia recursivamente el directorio assets/models/<modelId> a
     * /sdcard/Android/data/<pkg>/files/mlc-llm/weights/<modelId>
     * solo si aún no existe.
     *
     * @param onProgress callback opcional para progreso de copiado.
     * @return Directorio destino con los pesos.
     */
    suspend fun installIfNeeded(
        context: Context,
        modelId: String,
        onProgress: (InstallProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val am = context.assets
        val srcRoot = "models/$modelId"
        val dstRoot = File(context.getExternalFilesDir(null), "mlc-llm/weights/$modelId")

        if (dstRoot.exists() && dstRoot.listFiles()?.isNotEmpty() == true) {
            return@withContext dstRoot
        }

        val total = countAssets(am, srcRoot)
        var copied = 0

        copyAssetDir(
            am = am,
            assetDir = srcRoot,
            outDir = dstRoot
        ) { copiedName ->
            copied++
            onProgress(InstallProgress(totalFiles = total, copiedFiles = copied))
        }

        return@withContext dstRoot
    }

    private fun countAssets(am: AssetManager, dir: String): Int {
        val list = am.list(dir) ?: return 0
        var count = 0
        for (name in list) {
            val path = if (dir.isEmpty()) name else "$dir/$name"
            val children = am.list(path)
            if (children != null && children.isNotEmpty()) {
                count += countAssets(am, path)
            } else {
                count += 1
            }
        }
        return count
    }

    private fun copyAssetDir(
        am: AssetManager,
        assetDir: String,
        outDir: File,
        onFileCopied: (String) -> Unit
    ) {
        outDir.mkdirs()
        val list = am.list(assetDir) ?: return
        for (name in list) {
            val inPath = if (assetDir.isEmpty()) name else "$assetDir/$name"
            val children = am.list(inPath)
            val out = File(outDir, name)
            if (children != null && children.isNotEmpty()) {
                copyAssetDir(am, inPath, out, onFileCopied)
            } else {
                am.open(inPath).use { input ->
                    FileOutputStream(out).use { output ->
                        input.copyTo(output, bufferSize = 128 * 1024)
                    }
                }
                onFileCopied(name)
            }
        }
    }
}
