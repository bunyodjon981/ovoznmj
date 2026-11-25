package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AssistantSettings
import com.example.service.AssistantRuntimeUiState
import com.example.service.OrbPhase
import com.example.ui.PermissionsHealth
import com.example.ui.components.CosmicVoiceOrb
import com.example.ui.theme.AmberThinking
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SapphireCore
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CoreOrbScreen(
    settings: AssistantSettings,
    runtimeState: AssistantRuntimeUiState,
    permissions: PermissionsHealth,
    onStartStopClick: () -> Unit,
    onMicListenClick: () -> Unit,
    onSendCommand: (String) -> Unit,
    onOpenPermissions: () -> Unit
) {
    var inputCommand by rememberSaveable { mutableStateOf("") }

    val quickCommands = listOf(
        "Telegramni och",
        "Telefonimni qulfdan och",
        "Chiroqni yoq",
        "Soat necha bo'ldi?",
        "Oyimga qo'ng'iroq qil",
        "Ekranda nima bor?",
        "Ovozni ko'tar"
    )

    val wakeNamesLabel = if (settings.wakeWords.size > 1) {
        "\"${settings.wakeWord}\" +${settings.wakeWords.size - 1}"
    } else {
        "\"${settings.wakeWord}\""
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SapphireCore.copy(alpha = 0.14f),
                        ObsidianBg,
                        ObsidianBg
                    )
                )
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Top Status Header
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ObsidianSurface)
                        .border(1.dp, ObsidianSurfaceBorder, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                if (runtimeState.isServiceRunning) EmeraldActive else TextMuted
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chaqiruv: $wakeNamesLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ObsidianSurface)
                        .border(
                            1.dp,
                            if (runtimeState.isOnline) ElectricCyan.copy(alpha = 0.35f)
                            else AmberThinking.copy(alpha = 0.45f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (runtimeState.isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = if (runtimeState.isOnline) ElectricCyan else AmberThinking,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (runtimeState.isOnline) {
                            settings.activeModel.substringAfterLast("/").take(18)
                        } else {
                            "Offlayn rejim"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (runtimeState.isOnline) ElectricCyan else AmberThinking,
                        fontFamily = JetBrainsMonoFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!permissions.micGranted || !permissions.accessibilityGranted || !permissions.overlayGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AmberThinking.copy(alpha = 0.12f))
                        .border(1.dp, AmberThinking.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .clickable { onOpenPermissions() }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("permissions_banner")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = AmberThinking,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Qulf ekrani va to'liq boshqaruv uchun ruxsatlarni yoqing →",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            CosmicVoiceOrb(
                phase = runtimeState.orbPhase,
                audioLevel = runtimeState.audioLevel,
                size = 210.dp,
                onClick = onMicListenClick,
                testTagName = "main_voice_orb"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = when {
                    runtimeState.countdownSeconds != null -> "Yopilishiga ${runtimeState.countdownSeconds} soniya..."
                    runtimeState.orbPhase == OrbPhase.IDLE -> if (runtimeState.isServiceRunning) "Tayyor" else "Kutish holatida"
                    runtimeState.orbPhase == OrbPhase.PASSIVE_WAKE_LISTENING -> "24/7 Jim tinglamoqda — $wakeNamesLabel deng"
                    runtimeState.orbPhase == OrbPhase.ACTIVE_LISTENING -> "Gapiring, tinglayapman..."
                    runtimeState.orbPhase == OrbPhase.THINKING -> "Groq AI o'ylayapti..."
                    runtimeState.orbPhase == OrbPhase.EXECUTING -> "Telefonda bajarilmoqda..."
                    runtimeState.orbPhase == OrbPhase.SPEAKING -> "Javob bermoqdaman..."
                    else -> "Tayyor"
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    runtimeState.countdownSeconds != null -> AmberThinking
                    runtimeState.orbPhase == OrbPhase.ACTIVE_LISTENING -> EmeraldActive
                    runtimeState.orbPhase == OrbPhase.THINKING || runtimeState.orbPhase == OrbPhase.EXECUTING -> AmberThinking
                    runtimeState.orbPhase == OrbPhase.SPEAKING || runtimeState.orbPhase == OrbPhase.PASSIVE_WAKE_LISTENING -> ElectricCyan
                    else -> TextSecondary
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onStartStopClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("start_stop_assistant_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (runtimeState.isServiceRunning) CoralError.copy(alpha = 0.9f) else ElectricCyan,
                        contentColor = if (runtimeState.isServiceRunning) Color.White else Color(0xFF002026)
                    )
                ) {
                    Icon(
                        imageVector = if (runtimeState.isServiceRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (runtimeState.isServiceRunning) "To'xtatish" else "START (24/7)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onMicListenClick,
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("speak_now_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianSurfaceElevated,
                        contentColor = ElectricCyan
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = "Hozir gapirish",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Gapirish",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            AnimatedVisibility(
                visible = runtimeState.liveTranscript.isNotBlank() || runtimeState.aiResponse.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.28f), RoundedCornerShape(22.dp))
                        .padding(16.dp)
                ) {
                    if (runtimeState.liveTranscript.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = EmeraldActive,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = runtimeState.liveTranscript,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (runtimeState.aiResponse.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = runtimeState.aiResponse,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                        }
                    }

                    if (runtimeState.lastActionSummary.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianSurfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = NeuralViolet,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = runtimeState.lastActionSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tezkor buyruqlar (Offlayn va Online)",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                quickCommands.forEach { cmd ->
                    Surface(
                        onClick = { onSendCommand(cmd) },
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianSurfaceBorder),
                        modifier = Modifier.testTag("quick_chip_${cmd.take(10)}")
                    ) {
                        Text(
                            text = cmd,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = inputCommand,
                    onValueChange = { inputCommand = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("text_command_input"),
                    placeholder = { Text("Yozma buyruq bering...") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputCommand.isNotBlank()) {
                                onSendCommand(inputCommand)
                                inputCommand = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ObsidianSurfaceBorder,
                        focusedContainerColor = ObsidianSurface,
                        unfocusedContainerColor = ObsidianSurface
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputCommand.isNotBlank()) {
                            onSendCommand(inputCommand)
                            inputCommand = ""
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ElectricCyan)
                        .testTag("send_text_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Yuborish",
                        tint = Color(0xFF002026)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
