package com.yourapp.dubbing.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yourapp.dubbing.engine.DubbingState
import com.yourapp.dubbing.viewmodel.DubbingViewModel

@Composable
fun ProcessingScreen(navController: NavController, viewModel: DubbingViewModel) {
    val state by viewModel.dubbingState.collectAsState()
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.startProcessing(context)
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val currentState = state) {
            is DubbingState.Error -> {
                Text("Error: ${currentState.message}", color = Color.Red)
                Button(onClick = { navController.popBackStack() }) {
                    Text("Go Back")
                }
            }
            is DubbingState.Completed -> {
                Text("Dubbing complete!")
                Button(onClick = {
                    navController.navigate("player/${Uri.encode(currentState.outputFile.absolutePath)}")
                }) {
                    Text("Play Dubbed Video")
                }
            }
            else -> {
                CircularProgressIndicator()
                Text(
                    text = when (currentState) {
                        DubbingState.ExtractingAudio -> "Extracting audio..."
                        DubbingState.RecognizingSpeech -> "Recognizing speech..."
                        DubbingState.DetectingGender -> "Detecting gender..."
                        DubbingState.Translating -> "Translating..."
                        DubbingState.SynthesizingSpeech -> "Synthesizing voice..."
                        DubbingState.MuxingVideo -> "Creating final video..."
                        is DubbingState.Progress -> currentState.message
                        else -> "Processing..."
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}