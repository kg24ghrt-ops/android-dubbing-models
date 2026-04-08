package com.yourapp.dubbing.engine

import android.content.Context

class Translator(context: Context, modelPath: String) {

    // Stub: returns input text unchanged (no ONNX or SentencePiece)
    suspend fun translateBatch(texts: List<String>, srcLang: String, tgtLang: String): List<String> {
        return texts
    }

    fun close() {
        // No resources to release
    }
}