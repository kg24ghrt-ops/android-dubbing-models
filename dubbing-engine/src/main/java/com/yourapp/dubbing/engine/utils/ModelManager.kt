// dubbing-engine/src/main/java/com/yourapp/dubbing/engine/utils/ModelManager.kt

package com.yourapp.dubbing.engine.utils

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object ModelManager {
    // No more BASE_URL—each model has its own direct URL

    private val VOSK_URLS = mapOf(
        "es" to "https://alphacephei.com/vosk/models/vosk-model-small-es-0.22.zip",
        "ja" to "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
        "ko" to "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
        "fr" to "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
        "zh" to "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
    )

    private const val PIPER_VOICE_ONNX = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx"
    private const val PIPER_VOICE_JSON = "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json"

    private const val NLLB_MODEL_ONNX = "https://huggingface.co/Xenova/nllb-200-distilled-600M/resolve/main/onnx/decoder_model_merged_quantized.onnx"
    private const val NLLB_SPM_MODEL = "https://huggingface.co/Xenova/nllb-200-distilled-600M/resolve/main/sentencepiece.bpe.model"
    private const val NLLB_TOKENIZER_JSON = "https://huggingface.co/Xenova/nllb-200-distilled-600M/raw/main/tokenizer.json"

    suspend fun ensureVoskModel(context: Context, languageCode: String): String {
        val modelDir = File(context.getExternalFilesDir(null), "models/vosk/$languageCode")
        if (!modelDir.exists() || modelDir.listFiles()?.isEmpty() == true) {
            modelDir.mkdirs()
            val url = VOSK_URLS[languageCode] ?: throw IllegalArgumentException("Unsupported language: $languageCode")
            downloadAndExtractZip(url, modelDir)
        }
        return modelDir.absolutePath
    }

    suspend fun ensureTranslatorModel(context: Context): String {
        val modelDir = File(context.getExternalFilesDir(null), "models/translator")
        val modelFile = File(modelDir, "decoder_model_merged_quantized.onnx")
        val spmFile = File(modelDir, "sentencepiece.bpe.model")
        val tokenizerJsonFile = File(modelDir, "tokenizer.json")

        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        if (!modelFile.exists()) {
            downloadFile(NLLB_MODEL_ONNX, modelFile)
        }
        if (!spmFile.exists()) {
            downloadFile(NLLB_SPM_MODEL, spmFile)
        }
        if (!tokenizerJsonFile.exists()) {
            downloadFile(NLLB_TOKENIZER_JSON, tokenizerJsonFile)
        }

        return modelFile.absolutePath
    }

    suspend fun ensurePiperVoice(context: Context, voiceName: String): String {
        val voiceDir = File(context.getExternalFilesDir(null), "models/piper/$voiceName")
        if (!voiceDir.exists()) {
            voiceDir.mkdirs()
            downloadFile(PIPER_VOICE_ONNX, File(voiceDir, "$voiceName.onnx"))
            downloadFile(PIPER_VOICE_JSON, File(voiceDir, "$voiceName.onnx.json"))
        }
        return File(voiceDir, "$voiceName.onnx").absolutePath
    }

    private suspend fun downloadFile(url: String, destination: File) {
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Download failed: ${response.code} for $url")
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
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }
                }
            }
        }
        tempFile.delete()
    }
}