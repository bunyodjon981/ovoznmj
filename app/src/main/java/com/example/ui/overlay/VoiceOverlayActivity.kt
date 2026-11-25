package com.example.ui.overlay

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AssistantForegroundService
import com.example.service.AssistantRuntimeState
import com.example.ui.theme.TelefonimTheme

class VoiceOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(OverlayOrbManager.LOCKSCREEN_ORB_NOTIFICATION_ID)

        setContent {
            TelefonimTheme {
                val state by AssistantRuntimeState.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.isOverlayVisible, state.temporarilyYieldForUnlock) {
                    if (!state.isOverlayVisible || state.temporarilyYieldForUnlock) {
                        finish()
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            AssistantForegroundService.dismissOverlay(this@VoiceOverlayActivity)
                            finish()
                        }
                ) {
                    FloatingOrbOverlayContent(
                        onDismiss = {
                            AssistantForegroundService.dismissOverlay(this@VoiceOverlayActivity)
                            finish()
                        }
                    )
                }
            }
        }
    }
}
