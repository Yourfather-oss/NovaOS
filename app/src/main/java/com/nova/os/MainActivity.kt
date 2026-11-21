package com.nova.os

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.nova.os.ui.NovaApp

class MainActivity : ComponentActivity() {

    private val homeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status/navigation icons on our dark interface.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent { NovaApp(homeSignal.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeSignal.intValue++
    }
}
