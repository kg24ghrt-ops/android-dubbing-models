package com.yourapp.dubbing.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.dubbing.engine.DubbingPipeline
import com.yourapp.dubbing.engine.DubbingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DubbingViewModel(application: Application) : AndroidViewModel(application) {
    private val pipeline = DubbingPipeline(application)
    private val _dubbingState = MutableStateFlow<DubbingState>(DubbingState.Progress("Ready"))
    val dubbingState: StateFlow<DubbingState> = _dubbingState.asStateFlow()
    private var videoUri: Uri? = null
    
    fun setVideoUri(uri: Uri) {
        videoUri = uri
    }
    
    fun startProcessing(context: Context) {
        viewModelScope.launch {
            val uri = videoUri ?: run {
                _dubbingState.value = DubbingState.Error("No video selected")
                return@launch
            }
            // For demo, assume Spanish. In production, add language selection UI.
            val sourceLang = "es"
            
            pipeline.initializeModels(sourceLang) { msg ->
                _dubbingState.value = DubbingState.Progress(msg)
            }.onFailure { e ->
                _dubbingState.value = DubbingState.Error(e.message ?: "Initialization failed")
                return@launch
            }
            
            pipeline.processVideo(uri, sourceLang) { state ->
                _dubbingState.value = state
            }
        }
    }
}