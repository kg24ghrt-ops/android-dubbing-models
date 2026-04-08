package com.yourapp.dubbing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yourapp.dubbing.ui.PlayerScreen
import com.yourapp.dubbing.ui.ProcessingScreen
import com.yourapp.dubbing.ui.VideoPickerScreen
import com.yourapp.dubbing.ui.theme.VideoDubbingAppTheme
import com.yourapp.dubbing.viewmodel.DubbingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VideoDubbingAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val viewModel: DubbingViewModel = viewModel()
                    
                    NavHost(navController, startDestination = "picker") {
                        composable("picker") {
                            VideoPickerScreen(navController, viewModel)
                        }
                        composable("processing") {
                            ProcessingScreen(navController, viewModel)
                        }
                        composable("player/{videoPath}") { backStackEntry ->
                            val path = backStackEntry.arguments?.getString("videoPath")
                            PlayerScreen(path)
                        }
                    }
                }
            }
        }
    }
}