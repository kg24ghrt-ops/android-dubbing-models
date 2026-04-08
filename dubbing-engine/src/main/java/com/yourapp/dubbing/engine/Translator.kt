package com.yourapp.dubbing.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.davidecaroselli.sentencepiece.SentencePieceProcessor
import java.io.File

class Translator(context: Context, modelPath: String) {
    private val ortEnv = OrtEnvironment.getEnvironment()
    private lateinit var session: OrtSession
    private lateinit var spProcessor: SentencePieceProcessor

    init {
        session = ortEnv.createSession(modelPath)
        // Load SentencePiece model from same directory
        val spModelPath = File(modelPath).parent + "/sentencepiece.bpe.model"
        spProcessor = SentencePieceProcessor(spModelPath)
    }

    suspend fun translateBatch(texts: List<String>, srcLang: String, tgtLang: String): List<String> {
        return texts.map { translateSingle(it, srcLang, tgtLang) }
    }

    private fun translateSingle(text: String, srcLang: String, tgtLang: String): String {
        // Prepend language token (NLLB requires e.g., "spa_Latn" for Spanish)
        val langToken = getLanguageToken(srcLang)
        val inputText = "$langToken $text"

        // Encode with SentencePiece
        val inputIds = spProcessor.encodeAsIds(inputText).toLongArray()

        // Create ONNX tensor (shape: [1, sequence_length])
        val inputTensor = OnnxTensor.createTensor(ortEnv, arrayOf(inputIds))

        // Run inference
        val outputs = session.run(mapOf("input_ids" to inputTensor))

        // Decode output token IDs
        val outputIds = (outputs.first().value as Array<LongArray>).first()
        val translatedText = spProcessor.decode(outputIds.map { it.toInt() }.toIntArray())

        // Remove language token from output if present
        return cleanOutput(translatedText)
    }

    private fun getLanguageToken(langCode: String): String {
        return when (langCode) {
            "es" -> "spa_Latn"
            "ja" -> "jpn_Jpan"
            "ko" -> "kor_Hang"
            "fr" -> "fra_Latn"
            "zh" -> "zho_Hans"
            else -> "eng_Latn" // fallback
        }
    }

    private fun cleanOutput(text: String): String {
        // Remove language token prefix if it appears
        return text.replace(Regex("^[a-z]{3}_[A-Z][a-z]{3} "), "")
    }

    fun close() {
        session.close()
        ortEnv.close()
        spProcessor.close()
    }
}