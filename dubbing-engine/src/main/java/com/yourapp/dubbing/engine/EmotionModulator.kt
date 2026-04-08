package com.yourapp.dubbing.engine

object EmotionModulator {
    interface RuleSet {
        fun getPitchMultiplier(text: String): Float
        fun getSpeedMultiplier(text: String): Float
    }
    
    object PunctuationRules : RuleSet {
        override fun getPitchMultiplier(text: String): Float = when {
            text.contains("!") -> 1.15f
            text.contains("?") -> 1.05f
            text.contains("...") -> 0.90f
            else -> 1.0f
        }
        
        override fun getSpeedMultiplier(text: String): Float = when {
            text.contains("!") -> 1.10f
            text.contains("?") -> 0.95f
            text.contains("...") -> 0.85f
            else -> 1.0f
        }
    }
}