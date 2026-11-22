package com.nova.os

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.nova.os.ui.NovaApp
import com.nova.os.data.MessageIntelligenceStore

class MainActivity : ComponentActivity() {

    private val homeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status/navigation icons on our dark interface.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        consumeSharedChat(intent)
        setContent { NovaApp(homeSignal.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) homeSignal.intValue++
        consumeSharedChat(intent)
    }

    private fun consumeSharedChat(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        val sharedFile = stream?.let { uri -> runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(100_000) }
        }.getOrNull() }
        val shared = sharedText?.takeIf { it.isNotBlank() } ?: sharedFile
        if (!shared.isNullOrBlank()) MessageIntelligenceStore.importExport(this, shared)
        intent.action = Intent.ACTION_MAIN
    }
}
