package com.msgs.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.msgs.app.engine.Engine
import com.msgs.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val pendingConversation = mutableStateOf<String?>(null)
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            AppRoot(pendingConversation.value) { pendingConversation.value = null }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            Engine.status.value.paired
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        AppState.visible = true
    }

    override fun onPause() {
        super.onPause()
        AppState.visible = false
    }

    /** Opens the chat a notification points at, matching by id first and then by title. */
    private fun handle(intent: Intent?) {
        val id = intent?.getStringExtra("conv_id")
        val title = intent?.getStringExtra("conv_title")
        if (id == null && title == null) return
        val convs = Engine.conversations.value
        val resolved = id?.takeIf { convs.containsKey(it) }
            ?: title?.let { t -> convs.values.firstOrNull { it.title == t }?.id }
            ?: id
        if (resolved != null) pendingConversation.value = resolved
    }
}
