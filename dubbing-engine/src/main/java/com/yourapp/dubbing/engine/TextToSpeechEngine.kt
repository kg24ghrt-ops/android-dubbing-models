package com.yourapp.dubbing.engine

import android.content.Context
import com.reecedunn.espeak.SpeechSynthesizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TextToSpeechEngine(private val context: Context) {
    private var synthesizer: SpeechSynthesizer? = null

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            synthesizer = SpeechSynthesizer()
            synthesizer?.setLanguage("en")
            synthesizer?.setVoice("en")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun synthesizeWithEmotion(
        texts: List<String>,
        durations: List<Float>,
        isFemale: Boolean,
        emotionRules: EmotionModulator.RuleSet
    ): File = withContext(Dispatchers.IO) {
        val text = texts.joinToString(" ")
        val outputFile = File(context.cacheDir, "tts_output.wav")

        // eSpeak generates PCM 16-bit mono at 22050 Hz
        val samples = synthesizer?.synthesize(text) ?: ShortArray(0)

        writeWavFile(outputFile, samples, 22050)
        outputFile
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val byteBuffer = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF header
        byteBuffer.put("RIFF".toByteArray())
        byteBuffer.putInt(36 + samples.size * 2)
        byteBuffer.put("WAVE".toByteArray())
        // fmt subchunk
        byteBuffer.put("fmt ".toByteArray())
        byteBuffer.putInt(16)
        byteBuffer.putShort(1)
        byteBuffer.putShort(1)
        byteBuffer.putInt(sampleRate)
        byteBuffer.putInt(sampleRate * 2)
        byteBuffer.putShort(2)
        byteBuffer.putShort(16)
        // data subchunk
        byteBuffer.put("data".toByteArray())
        byteBuffer.putInt(samples.size * 2)

        for (sample in samples) {
            byteBuffer.putShort(sample)
        }

        FileOutputStream(file).use { it.write(byteBuffer.array()) }
    }

    fun shutdown() {
        synthesizer?.stop()
        synthesizer = null
    }
}