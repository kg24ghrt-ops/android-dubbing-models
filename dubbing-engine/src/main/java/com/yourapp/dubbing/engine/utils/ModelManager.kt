package com.yourapp.dubbing.engine.utils

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object ModelManager {

    // Vosk speech-recognition models
    private val VOSK_URLS = mapOf(
        "es" to "https://alphacephei.com/vosk/models/vosk-model-small-es-0.22.zip",
        "ja" to "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
        "ko" to "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
        "fr" to "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
        "zh" to "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
    )

    // Piper voice model for Sherpa-ONNX (English, female, medium quality)
    private const val PIPER_MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/piper-lessac-medium.tar.bz2"
    private const val PIPER_VOICE_NAME = "en_US-lessac-medium"

    suspend fun ensureVoskModel(context: Context, languageCode: String): String {
        val modelDir = File(context.getExternalFilesDir(null), "models/vosk/$languageCode")
        if (!modelDir.exists() || modelDir.listFiles()?.isEmpty() == true) {
            modelDir.mkdirs()
            val url = VOSK_URLS[languageCode]
                ?: throw IllegalArgumentException("Unsupported language: $languageCode")
            downloadAndExtractZip(url, modelDir)
        }
        return modelDir.absolutePath
    }

    suspend fun ensureTranslatorModel(context: Context): String {
        // Translation is stubbed – will be implemented later with NLLB-200
        return "/dummy/translator"
    }

    /**
     * Downloads (if needed) the Piper voice model for Sherpa-ONNX TTS.
     * @return absolute path to the directory containing .onnx and tokens.txt
     */
    suspend fun ensurePiperVoice(context: Context, voiceName: String = PIPER_VOICE_NAME): String {
        val modelDir = File(context.getExternalFilesDir(null), "models/tts/piper")
        val onnxFile = File(modelDir, "$voiceName.onnx")
        val tokensFile = File(modelDir, "tokens.txt")

        if (!onnxFile.exists() || !tokensFile.exists()) {
            modelDir.mkdirs()
            downloadAndExtractTarBz2(PIPER_MODEL_URL, modelDir)
        }
        return modelDir.absolutePath
    }

    // ---------- Helper functions ----------

    private suspend fun downloadFile(url: String, destination: File) {
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code} for $url")
            }
            response.body?.byteStream()?.use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
        }
    }

    private suspend fun downloadAndExtractZip(url: String, destDir: File) {
        val tempFile = File.createTempFile("model", ".zip")
        downloadFile(url, tempFile)
        ZipFile(tempFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        tempFile.delete()
    }

    private suspend fun downloadAndExtractTarBz2(url: String, destDir: File) {
        val tempFile = File.createTempFile("model", ".tar.bz2")
        downloadFile(url, tempFile)

        BZip2CompressorInputStream(BufferedInputStream(tempFile.inputStream())).use { bzIn ->
            TarArchiveInputStream(bzIn).use { tarIn ->
                var entry = tarIn.nextTarEntry
                while (entry != null) {
                    val outputFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { fos ->
                            tarIn.copyTo(fos)
                        }
                    }
                    entry = tarIn.nextTarEntry
                }
            }
        }
        tempFile.delete()
    }
}