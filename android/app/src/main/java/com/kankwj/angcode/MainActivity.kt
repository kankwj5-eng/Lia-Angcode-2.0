package com.kankwj.angcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.kankwj.angcode.ui.AngCodeApp
import com.kankwj.angcode.ui.theme.AngCodeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AngCodeTheme {
                AngCodeApp()
            }
        }
    }
}
