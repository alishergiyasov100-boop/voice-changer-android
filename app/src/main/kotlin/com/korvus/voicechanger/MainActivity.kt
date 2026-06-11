package com.korvus.voicechanger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.korvus.voicechanger.ui.HomeScreen
import com.korvus.voicechanger.ui.theme.VoiceChangerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoiceChangerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0E0E10)) {
                    HomeScreen()
                }
            }
        }
    }
}
