package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.service.OrbPhase
import com.example.ui.SetupUiState
import com.example.ui.components.CosmicVoiceOrb
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.SapphireCore
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ApiSetupScreen(
    initialWakeWord: String,
    setupState: SetupUiState,
    onConnectClick: (apiKey: String, wakeWord: String) -> Unit
) {
    var apiKey by rememberSaveable { mutableStateOf("") }
    var wakeWord by rememberSaveable { mutableStateOf(initialWakeWord.ifBlank { "Telefonim" }) }
    var showKey by rememberSaveable { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        SapphireCore.copy(alpha = 0.18f),
                        NeuralViolet.copy(alpha = 0.08f),
                        ObsidianBg
                    )
                )
            )
            .safeDrawingPadding()
            .imePadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            CosmicVoiceOrb(
                phase = if (setupState.isChecking) OrbPhase.THINKING else OrbPhase.PASSIVE_WAKE_LISTENING,
                audioLevel = 0.25f,
                size = 136.dp,
                testTagName = "setup_orb"
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Telefonim AI",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Groq API kalitini bir marta kiriting. Ilova umrbod saqlab qoladi va bepul AI modellarga avtomatik ulanadi.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianSurfaceBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = "1. Groq API Kaliti",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricCyan,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("groq_api_key_input"),
                    label = { Text("gsk_... kalitingizni joylashtiring") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = TextPrimary
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = "API kaliti",
                            tint = ElectricCyan
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showKey) "Yashirish" else "Ko'rsatish",
                                tint = TextSecondary
                            )
                        }
                    },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ObsidianSurfaceBorder,
                        focusedContainerColor = ObsidianSurfaceElevated,
                        unfocusedContainerColor = ObsidianSurfaceElevated
                    )
                )

                Text(
                    text = "2. Chaqiruv Nomi (Ovozli uyg'otish so'zi)",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricCyan,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = wakeWord,
                    onValueChange = { wakeWord = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_wake_word_input"),
                    label = { Text("Masalan: Telefonim") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = "Chaqiruv nomi",
                            tint = ElectricCyan
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ObsidianSurfaceBorder,
                        focusedContainerColor = ObsidianSurfaceElevated,
                        unfocusedContainerColor = ObsidianSurfaceElevated
                    )
                )

                if (setupState.errorMessage != null) {
                    Text(
                        text = setupState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoralError
                    )
                }

                Button(
                    onClick = { onConnectClick(apiKey, wakeWord) },
                    enabled = !setupState.isChecking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("connect_groq_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color(0xFF002026)
                    )
                ) {
                    if (setupState.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = Color(0xFF002026)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Ulanmoqda...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ulash va Boshlash", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurface.copy(alpha = 0.7f))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Bepul Groq API kalitini console.groq.com saytidan olishingiz mumkin. Kalit faqat telefoningiz xotirasida saqlanadi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
