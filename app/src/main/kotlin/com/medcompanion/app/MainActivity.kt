package com.medcompanion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.medcompanion.app.ui.MedCompanionRoot
import com.medcompanion.app.ui.theme.MedCompanionTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single activity hosting all Compose screens. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MedCompanionTheme {
                MedCompanionRoot()
            }
        }
    }
}
