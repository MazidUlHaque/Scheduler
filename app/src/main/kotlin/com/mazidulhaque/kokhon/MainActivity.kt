package com.mazidulhaque.kokhon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonTheme
import com.mazidulhaque.kokhon.ui.home.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val clock = (application as KokhonApplication).container.clock
        setContent {
            KokhonTheme {
                HomeScreen(clock = clock)
            }
        }
    }
}
