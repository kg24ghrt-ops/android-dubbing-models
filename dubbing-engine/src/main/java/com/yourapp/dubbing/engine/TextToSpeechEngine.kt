// TextToSpeechEngine.kt
package com.yourapp.dubbing.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

class TextToSpeechEngine(private val context: Context) {
    private var tts: TextToSpeech? = null

    suspend fun initialize(): Result<Unit> = suspendCancellableCoroutine { cont ->
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    cont.resume(Result.failure(Exception("English not supported")))
                } else {
                    cont.resume(Result.success(Unit))
                }
            } else {
                cont.resume(Result.failure(Exception("TTS init failed")))
            }
        }
        cont.invokeOnCancellation { tts?.shutdown() }
    }

    suspend fun synthesizeWithEmotion(
        texts: List<String>,
        durations: List<Float>,
        isFemale: Boolean,
        emotionRules: EmotionModulator.RuleSet
    ): File = suspendCancellableCoroutine { cont ->
        val textToSynthesize = texts.firstOrNull() ?: ""
        val outputFile = File(context.cacheDir, "tts_output.wav")

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                cont.resume(outputFile)
            }
            override fun onError(utteranceId: String?) {
                cont.resume(File.createTempFile("error", ".wav", context.cacheDir))
            }
        })

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "dubbingUtterance")
        tts?.synthesizeToFile(textToSynthesize, params, outputFile, "dubbingUtterance")
    }

    fun shutdown() {
        tts?.shutdown()
    }
}