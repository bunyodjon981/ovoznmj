package com.example.ui.overlay

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.R
import com.example.TelefonimApplication
import com.example.service.AssistantRuntimeState
import com.example.service.OrbPhase
import com.example.service.PhoneControlAccessibilityService
import com.example.ui.components.CosmicVoiceOrb
import com.example.ui.theme.AmberThinking
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurfaceBorder
import com.example.ui.theme.TelefonimTheme
import com.example.ui.theme.TextPrimary

private class OverlayViewLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

class OverlayOrbManager(private val context: Context) {

    companion object {
        const val LOCKSCREEN_ORB_NOTIFICATION_ID = 4102
    }

    private var activeWindowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayViewLifecycleOwner? = null

    fun showOverlay(onDismissRequest: () -> Unit) {
        AssistantRuntimeState.update {
            it.copy(isOverlayVisible = true, temporarilyYieldForUnlock = false)
        }

        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isLockedOrAsleep = km.isKeyguardLocked || !pm.isInteractive

        // 1. When locked or screen asleep:
        // Android hides TYPE_APPLICATION_OVERLAY / TYPE_ACCESSIBILITY_OVERLAY behind Keyguard.
        // Launching VoiceOverlayActivity via FullScreenIntent + direct startActivity puts it ABOVE Lock Screen!
        if (isLockedOrAsleep) {
            removeWindowViewOnly()
            launchLockScreenOverlayActivity()
            return
        }

        // 2. When unlocked: attach floating overlay window over any app
        val accService = PhoneControlAccessibilityService.instance
        if (accService != null) {
            if (composeView != null) return
            val wm = accService.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val added = attachWindowOverlay(
                hostContext = accService,
                wm = wm,
                layoutType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                onDismissRequest = onDismissRequest
            )
            if (added) return
        }

        if (Settings.canDrawOverlays(context)) {
            if (composeView != null) return
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val added = attachWindowOverlay(
                hostContext = context,
                wm = wm,
                layoutType = layoutType,
                onDismissRequest = onDismissRequest
            )
            if (added) return
        }

        // 3. Fallback Activity if no overlay permission granted
        removeWindowViewOnly()
        launchLockScreenOverlayActivity()
    }

    private fun launchLockScreenOverlayActivity() {
        val intent = Intent(context, VoiceOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }

        // FullScreenIntent guarantees lock-screen display even under Android 10-16 BAL restrictions
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            4102,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            TelefonimApplication.CHANNEL_LOCKSCREEN_ORB_ID
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Telefonim AI")
            .setContentText("Tinglamoqda...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(LOCKSCREEN_ORB_NOTIFICATION_ID, notification)

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun attachWindowOverlay(
        hostContext: Context,
        wm: WindowManager,
        layoutType: Int,
        onDismissRequest: () -> Unit
    ): Boolean {
        val owner = OverlayViewLifecycleOwner().apply { onCreate() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 110
        }

        val view = ComposeView(hostContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                TelefonimTheme {
                    FloatingOrbOverlayContent(onDismiss = onDismissRequest)
                }
            }
        }

        return try {
            wm.addView(view, params)
            activeWindowManager = wm
            composeView = view
            lifecycleOwner = owner
            true
        } catch (_: Exception) {
            owner.onDestroy()
            false
        }
    }

    fun hideOverlay() {
        AssistantRuntimeState.update {
            it.copy(
                isOverlayVisible = false,
                temporarilyYieldForUnlock = false,
                countdownSeconds = null
            )
        }
        cancelLockScreenNotification(context)
        removeWindowViewOnly()
    }

    fun cancelLockScreenNotification(ctx: Context) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(LOCKSCREEN_ORB_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    private fun removeWindowViewOnly() {
        composeView?.let { view ->
            try {
                activeWindowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        activeWindowManager = null
        composeView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }
}

@Composable
fun FloatingOrbOverlayContent(
    onDismiss: () -> Unit
) {
    val state by AssistantRuntimeState.state.collectAsStateWithLifecycle()

    val crispShadow = Shadow(
        color = Color.Black.copy(alpha = 0.92f),
        offset = Offset(0f, 2f),
        blurRadius = 10f
    )

    AnimatedVisibility(
        visible = state.isOverlayVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(min = 240.dp, max = 330.dp)
                .padding(12.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                CosmicVoiceOrb(
                    phase = state.orbPhase,
                    audioLevel = state.audioLevel,
                    size = 156.dp,
                    onClick = null,
                    testTagName = "floating_overlay_orb"
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg.copy(alpha = 0.82f))
                        .border(1.dp, ObsidianSurfaceBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Yopish",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val statusLabel = when {
                state.countdownSeconds != null -> "Yopilishiga ${state.countdownSeconds} soniya..."
                state.orbPhase == OrbPhase.ACTIVE_LISTENING -> "Tinglamoqdaman..."
                state.orbPhase == OrbPhase.THINKING -> "O'ylayapman..."
                state.orbPhase == OrbPhase.EXECUTING -> "Bajarmoqda..."
                state.orbPhase == OrbPhase.SPEAKING -> "${state.wakeWord} gapirmoqda"
                else -> state.wakeWord
            }

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelLarge.copy(shadow = crispShadow),
                color = if (state.countdownSeconds != null) AmberThinking else ElectricCyan,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (state.liveTranscript.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.liveTranscript,
                    style = MaterialTheme.typography.bodyLarge.copy(shadow = crispShadow),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (state.aiResponse.isNotBlank() && state.orbPhase != OrbPhase.ACTIVE_LISTENING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.aiResponse,
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = crispShadow),
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
