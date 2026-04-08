package com.yourapp.dubbing.engine

import java.io.File

class GenderDetector {
    companion object {
        init {
            System.loadLibrary("pitch_detector")
        }
    }
    
    private external fun nativeEstimatePitch(audioFilePath: String): Float
    
    fun isFemaleVoice(audioFile: File): Boolean {
        val pitch = nativeEstimatePitch(audioFile.absolutePath)
        return pitch > 165f
    }
}