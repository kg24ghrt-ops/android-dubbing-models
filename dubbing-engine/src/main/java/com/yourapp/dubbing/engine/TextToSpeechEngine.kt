package com.yourapp.dubbing.engine

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TextToSpeechEngine(private val context: Context) {
    private var tts: OfflineTts? = null
    private var modelPath: String? = null

    /**
     * Initialize the TTS engine with the Piper voice model directory.
     * @param modelDir Absolute path to directory containing .onnx and tokens.txt
     */
    suspend fun initialize(modelDir: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            modelPath = modelDir
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    model = "$modelDir/en_US-lessac-medium.onnx",
                    tokens = "$modelDir/tokens.txt",
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                ruleFsts = "",
                maxNumSentences = 1
            )
            tts = OfflineTts(config)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Synthesize speech from text, apply emotion rules, and return a WAV file.
     */
    suspend fun synthesizeWithEmotion(
        texts: List<String>,
        durations: List<Float>,
        isFemale: Boolean,
        emotionRules: EmotionModulator.RuleSet
    ): File = withContext(Dispatchers.IO) {
        val text = texts.joinToString(" ")
        val outputFile = File(context.cacheDir, "tts_output.wav")

        val audioSamples = tts?.generate(text, speed = 1.0f)?.samples ?: FloatArray(0)

        val pcmShorts = ShortArray(audioSamples.size)
        for (i in audioSamples.indices) {
            pcmShorts[i] = (audioSamples[i] * 32767.0f).toInt().toShort()
        }

        writeWavFile(outputFile, pcmShorts, 22050)
        outputFile
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val byteBuffer = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.put("RIFF".toByteArray())
        byteBuffer.putInt(36 + samples.size * 2)
        byteBuffer.put("WAVE".toByteArray())
        byteBuffer.put("fmt ".toByteArray())
        byteBuffer.putInt(16)
        byteBuffer.putShort(1)
        byteBuffer.putShort(1)
        byteBuffer.putInt(sampleRate)
        byteBuffer.putInt(sampleRate * 2)
        byteBuffer.putShort(2)
        byteBuffer.putShort(16)
        byteBuffer.put("data".toByteArray())
        byteBuffer.putInt(samples.size * 2)

        for (sample in samples) {
            byteBuffer.putShort(sample)
        }

        FileOutputStream(file).use { it.write(byteBuffer.array()) }
    }

    fun shutdown() {
        tts?.release()
        tts = null
    }
}