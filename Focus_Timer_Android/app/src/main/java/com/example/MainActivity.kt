package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.FocusTimerApp
import com.example.ui.theme.BgDark
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FocusViewModel


class MainActivity : ComponentActivity() {

    private val viewModel: FocusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Wake and show over lockscreen when alarm triggers on Android 15
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        setContent {
            val isRunning by viewModel.isRunning.collectAsState()

            // Keep screen on ONLY when a timer or stopwatch is actively running
            DisposableEffect(isRunning) {
                if (isRunning) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            val currentTheme by viewModel.currentAppTheme.collectAsState()

            MyApplicationTheme(appTheme = currentTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = currentTheme.bgBase
                ) {
                    FocusTimerApp(viewModel = viewModel)
                }
            }
        }
    }
}

