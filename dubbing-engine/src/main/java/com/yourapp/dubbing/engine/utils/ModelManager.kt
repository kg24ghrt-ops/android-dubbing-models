// dubbing-engine/src/main/java/com/yourapp/dubbing/engine/utils/ModelManager.kt

package com.yourapp.dubbing.engine.utils

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object ModelManager {

    // Vosk speech‑recognition models – these are still needed
    private val VOSK_URLS = mapOf(
        "es" to "https://alphacephei.com/vosk/models/vosk-model-small-es-0.22.zip",
        "ja" to "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
        "ko" to "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
        "fr" to "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
        "zh" to "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
    )

    /**
     * Downloads (if needed) the Vosk model for the given language.
     * @return absolute path to the model directory
     */
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

    /**
     * Stubbed – we no longer use a separate translation model.
     * @return a dummy path (no download occurs)
     */
    suspend fun ensureTranslatorModel(context: Context): String {
        // Translation is stubbed, so we don't need a real model.
        return "/dummy/translator"
    }

    /**
     * Stubbed – we now use Android's built‑in TTS (NekoSpeak) instead of Piper.
     * @return a dummy path (no download occurs)
     */
    suspend fun ensurePiperVoice(context: Context, voiceName: String): String {
        // TTS is handled by the system, no model download needed.
        return "/dummy/tts/voice"
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
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
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
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        tempFile.delete()
    }
}