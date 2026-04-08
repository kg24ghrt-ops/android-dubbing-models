package com.yourapp.dubbing.engine

import java.io.File

class GenderDetector {
    // Stub: always returns female (or male) - no native dependency
    fun isFemaleVoice(audioFile: File): Boolean {
        // TODO: Replace with actual pitch detection later
        return true
    }
}