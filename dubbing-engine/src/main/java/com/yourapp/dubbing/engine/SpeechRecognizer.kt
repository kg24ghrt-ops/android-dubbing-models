package com.yourapp.dubbing.engine

import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
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
            recognizer = Recognizer(model, 16000f)
            val service = SpeechService(recognizer, 16000f)
            val segments = mutableListOf<TranscriptSegment>()
            
            service.setRecognitionListener(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {}
                override fun onResult(hypothesis: String?) {
                    hypothesis?.let { json ->
                        // Parse Vosk result to get words with timings
                        // Example: {"result":[{"word":"hola","start":0.5,"end":0.8}]}
                        // For simplicity, we just take the full text.
                        val text = extractTextFromJson(json)
                        segments.add(TranscriptSegment(text, 0f, 0f))
                    }
                }
                override fun onFinalResult(hypothesis: String?) {
                    // Last result
                }
                override fun onError(exception: Exception?) {
                    cont.resume(emptyList())
                }
                override fun onTimeout() {}
            })
            
            FileInputStream(audioFile).use { fis ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    service.acceptWaveForm(buffer, bytesRead)
                }
            }
            service.stop()
            cont.resume(segments)
        } catch (e: Exception) {
            cont.resume(emptyList())
        }
    }
    
    private fun extractTextFromJson(json: String): String {
        // Simple regex, better to use JSON parser
        val pattern = "\"text\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }
}