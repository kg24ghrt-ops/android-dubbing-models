// dubbing-engine/src/main/java/com/yourapp/dubbing/engine/DubbingPipeline.kt
package com.yourapp.dubbing.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yourapp.dubbing.engine.utils.ModelManager   // <-- ADD THIS
import java.io.File

// ... rest of file unchanged ...
class DubbingPipeline(private val context: Context) {

    private val audioExtractor = AudioExtractor()
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var translator: Translator
    private lateinit var genderDetector: GenderDetector
    private lateinit var ttsEngine: TextToSpeechEngine
    private lateinit var videoDubber: VideoDubber

    suspend fun initializeModels(languageCode: String, onProgress: (String) -> Unit): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                onProgress("Downloading Vosk model for $languageCode...")
                val voskModelPath = ModelManager.ensureVoskModel(context, languageCode)
                speechRecognizer = SpeechRecognizer(voskModelPath)

                onProgress("Loading translation model...")
                val translatorModelPath = ModelManager.ensureTranslatorModel(context)
                translator = Translator(context, translatorModelPath)

                onProgress("Initializing TTS engine...")
                // TextToSpeechEngine now only needs Context
                ttsEngine = TextToSpeechEngine(context)
                ttsEngine.initialize().getOrThrow()

                genderDetector = GenderDetector()
                videoDubber = VideoDubber(context)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun processVideo(
        inputUri: Uri,
        sourceLanguage: String,
        onProgress: (DubbingState) -> Unit
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                onProgress(DubbingState.ExtractingAudio)
                val audioFile = audioExtractor.extractAudio(context, inputUri)

                onProgress(DubbingState.RecognizingSpeech)
                val transcriptSegments = speechRecognizer.transcribeFile(audioFile)

                onProgress(DubbingState.DetectingGender)
                val isFemale = genderDetector.isFemaleVoice(audioFile)

                onProgress(DubbingState.Translating)
                val translatedSegments = translator.translateBatch(
                    transcriptSegments.map { it.text },
                    sourceLanguage,
                    "en"
                )

                onProgress(DubbingState.SynthesizingSpeech)
                val ttsAudioFile = ttsEngine.synthesizeWithEmotion(
                    translatedSegments,
                    transcriptSegments.map { it.endTime },
                    isFemale,
                    EmotionModulator.PunctuationRules
                )

                onProgress(DubbingState.MuxingVideo)
                val outputFile = videoDubber.replaceAudioTrack(inputUri, ttsAudioFile)

                onProgress(DubbingState.Completed(outputFile))
                Result.success(outputFile)
            } catch (e: Exception) {
                onProgress(DubbingState.Error(e.message ?: "Unknown error"))
                Result.failure(e)
            }
        }
    }
}

sealed class DubbingState {
    object ExtractingAudio : DubbingState()
    object RecognizingSpeech : DubbingState()
    object DetectingGender : DubbingState()
    object Translating : DubbingState()
    object SynthesizingSpeech : DubbingState()
    object MuxingVideo : DubbingState()
    data class Completed(val outputFile: File) : DubbingState()
    data class Error(val message: String) : DubbingState()
    data class Progress(val message: String) : DubbingState()
}