package com.example.airecycleguide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.airecycleguide.ui.app.RecycleGuideApp
import com.example.airecycleguide.ui.theme.AiRecycleGuideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiRecycleGuideTheme {
                RecycleGuideApp()
            }
        }
    }
}
