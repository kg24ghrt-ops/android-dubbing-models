package com.yourapp.dubbing.engine

import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class TranscriptSegment(val text: String, val startTime: Float, val endTime: Float)

class SpeechRecognizer(private val modelPath: String) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null

    suspend fun transcribeFile(audioFile: File): List<TranscriptSegment> = suspendCoroutine { cont ->
        try {
            model = Model(modelPath)
            // 16000 Hz is the sample rate expected by Vosk
            recognizer = Recognizer(model, 16000f)
            
            val segments = mutableListOf<TranscriptSegment>()
            val buffer = ByteArray(4096)
            
            FileInputStream(audioFile).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    // Feed audio data to recognizer
                    if (recognizer!!.acceptWaveForm(buffer, bytesRead)) {
                        // Result is ready
                        val result = recognizer!!.result
                        if (result != null) {
                            val text = extractTextFromJson(result)
                            if (text.isNotBlank()) {
                                segments.add(TranscriptSegment(text, 0f, 0f))
                            }
                        }
                    }
                }
                // Get final result
                val finalResult = recognizer!!.finalResult
                if (finalResult != null) {
                    val text = extractTextFromJson(finalResult)
                    if (text.isNotBlank()) {
                        segments.add(TranscriptSegment(text, 0f, 0f))
                    }
                }
            }
            
            recognizer?.close()
            model?.close()
            
            cont.resume(segments)
        } catch (e: Exception) {
            cont.resume(emptyList())
        }
    }

    private fun extractTextFromJson(json: String): String {
        // Extract the "text" field from Vosk JSON result
        val pattern = "\"text\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }
}