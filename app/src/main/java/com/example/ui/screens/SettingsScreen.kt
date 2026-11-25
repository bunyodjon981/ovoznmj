package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.AssistantSettings
import com.example.data.SettingsDataStore
import com.example.ui.SetupUiState
import com.example.ui.theme.CoralError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AssistantSettings,
    setupState: SetupUiState,
    onSaveWakeWords: (List<String>) -> Unit,
    onSaveUnlockPin: (String) -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    onToggleAutoFreeModel: (Boolean) -> Unit,
    onToggleVoiceFeedback: (Boolean) -> Unit,
    onToggleAutoDismiss: (Boolean) -> Unit,
    onToggleContinuousWake: (Boolean) -> Unit,
    onTestVoice: () -> Unit
) {
    val wakeDrafts = remember(settings.wakeWords) {
        mutableStateListOf<String>().apply {
            addAll(settings.wakeWords.ifEmpty { listOf("Telefonim") })
        }
    }
    var wakeSavedToast by rememberSaveable { mutableStateOf(false) }

    var pinDraft by rememberSaveable(settings.unlockPin) { mutableStateOf(settings.unlockPin) }
    var showPin by rememberSaveable { mutableStateOf(false) }
    var pinSavedToast by rememberSaveable { mutableStateOf(false) }

    var editingKey by rememberSaveable { mutableStateOf(false) }
    var newApiKeyDraft by rememberSaveable { mutableStateOf("") }

    val maskedKey = if (settings.groqApiKey.length > 10) {
        "${settings.groqApiKey.take(6)}••••••${settings.groqApiKey.takeLast(4)}"
    } else "Saqlangan"

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Sozlamalar",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            // 1. Up to 10 Custom Wake Words with '+' Button
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianSurfaceBorder, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chaqiruv nomlari (${wakeDrafts.size}/${SettingsDataStore.MAX_WAKE_WORDS})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FilledTonalIconButton(
                        onClick = {
                            if (wakeDrafts.size < SettingsDataStore.MAX_WAKE_WORDS) {
                                val nextDefault = if (wakeDrafts.size == 1 && wakeDrafts.none { it.equals("Android", true) }) {
                                    "Android"
                                } else ""
                                wakeDrafts.add(nextDefault)
                                wakeSavedToast = false
                            }
                        },
                        enabled = wakeDrafts.size < SettingsDataStore.MAX_WAKE_WORDS,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = ElectricCyan.copy(alpha = 0.2f),
                            contentColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .testTag("add_wake_word_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Yangi chaqiruv nomi qo'shish"
                        )
                    }
                }

                Text(
                    text = "+ tugmasini bosib 10 tagacha chaqiruv nomini qo'shing (masalan: Telefonim, Android). Qaysi birini aytsangiz ham ilova darhol eshitadi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                wakeDrafts.forEachIndexed { index, value ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { updated ->
                                wakeDrafts[index] = updated
                                wakeSavedToast = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wake_word_input_$index"),
                            singleLine = true,
                            label = {
                                Text(
                                    if (index == 0) "1-nom (masalan: Telefonim)"
                                    else "${index + 1}-nom (masalan: Android)"
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = ObsidianSurfaceBorder,
                                focusedContainerColor = ObsidianSurfaceElevated,
                                unfocusedContainerColor = ObsidianSurfaceElevated
                            )
                        )

                        if (wakeDrafts.size > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    wakeDrafts.removeAt(index)
                                    wakeSavedToast = false
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("remove_wake_word_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "O'chirish",
                                    tint = CoralError
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onSaveWakeWords(wakeDrafts.toList())
                        wakeSavedToast = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_wake_word_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color(0xFF002026)
                    )
                ) {
                    Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chaqiruv nomlarini saqlash", fontWeight = FontWeight.Bold)
                }

                if (wakeSavedToast) {
                    Text(
                        text = "Saqlandi: ${settings.wakeWords.joinToString(", ")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldActive
                    )
                }
            }

            // 2. Automatic Lock Screen Unlock PIN
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianSurfaceBorder, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Qulfni avtomatik ochish (PIN / Parol)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = "\"Telefonimni qulfdan och\" deganingizda ilova blok ekranini surib shu kodni avtomatik teradi. Kod faqat telefoningizda saqlanadi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = pinDraft,
                        onValueChange = {
                            pinDraft = it
                            pinSavedToast = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("unlock_pin_input"),
                        singleLine = true,
                        label = { Text("Ekran PIN yoki paroli") },
                        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        trailingIcon = {
                            IconButton(onClick = { showPin = !showPin }) {
                                Icon(
                                    imageVector = if (showPin) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (showPin) "Yashirish" else "Ko'rsatish",
                                    tint = TextSecondary
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = ObsidianSurfaceBorder,
                            focusedContainerColor = ObsidianSurfaceElevated,
                            unfocusedContainerColor = ObsidianSurfaceElevated
                        )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onSaveUnlockPin(pinDraft)
                            pinSavedToast = true
                        },
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("save_unlock_pin_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color(0xFF002026)
                        )
                    ) {
                        Text("Saqlash", fontWeight = FontWeight.Bold)
                    }
                }

                if (pinSavedToast) {
                    Text(
                        text = if (pinDraft.isBlank()) "Qulf kodi o'chirildi" else "Qulf kodi saqlandi",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldActive
                    )
                }
            }

            // 3. Groq API & Free Models Auto-Selection
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianSurfaceBorder, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Groq API va Bepul Modellar",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text(
                            text = "Saqlangan kalit",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = maskedKey,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldActive,
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    }
                    OutlinedButton(
                        onClick = { editingKey = !editingKey },
                        modifier = Modifier.testTag("toggle_edit_api_key")
                    ) {
                        Text(if (editingKey) "Bekor" else "Almashtirish")
                    }
                }

                if (editingKey) {
                    OutlinedTextField(
                        value = newApiKeyDraft,
                        onValueChange = { newApiKeyDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_api_key_input"),
                        label = { Text("Yangi Groq API kaliti (gsk_...)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = {
                            onUpdateApiKey(newApiKeyDraft)
                            editingKey = false
                            newApiKeyDraft = ""
                        },
                        enabled = !setupState.isChecking && newApiKeyDraft.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("confirm_new_api_key_button")
                    ) {
                        Text("Yangi kalitni saqlash")
                    }
                }

                SettingSwitchRow(
                    title = "Avtomatik bepul model",
                    subtitle = "Limit to'lsa boshqa bepul Groq modeliga avtomatik o'tadi",
                    checked = settings.autoFreeModel,
                    onCheckedChange = onToggleAutoFreeModel
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Faol model: ${settings.activeModel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                        fontFamily = JetBrainsMonoFontFamily,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onRefreshModels,
                        enabled = !setupState.isChecking,
                        modifier = Modifier.testTag("refresh_models_button")
                    ) {
                        if (setupState.isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yangilash")
                    }
                }

                if (setupState.statusMessage != null) {
                    Text(
                        text = setupState.statusMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldActive
                    )
                }
                if (setupState.errorMessage != null) {
                    Text(
                        text = setupState.errorMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = CoralError
                    )
                }

                if (settings.discoveredModels.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        settings.discoveredModels.forEach { modelId ->
                            val selected = modelId == settings.activeModel
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) ElectricCyan.copy(alpha = 0.2f)
                                        else ObsidianSurfaceElevated
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) ElectricCyan else ObsidianSurfaceBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSelectModel(modelId) }
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = modelId.substringAfterLast("/"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) ElectricCyan else TextSecondary,
                                        fontFamily = JetBrainsMonoFontFamily
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Voice & Background Behavior
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianSurfaceBorder, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                SettingSwitchRow(
                    title = "Ovozli javob (TTS)",
                    subtitle = "AI javoblarini o'zbekcha aniq ovozda o'qib eshittiradi",
                    checked = settings.voiceFeedbackEnabled,
                    onCheckedChange = onToggleVoiceFeedback
                )
                SettingSwitchRow(
                    title = "24/7 Orqa fonda tinglash",
                    subtitle = "Ekran o'chgan va qulf holatda ham chaqiruv so'zini kutadi",
                    checked = settings.continuousWakeListening,
                    onCheckedChange = onToggleContinuousWake
                )
                SettingSwitchRow(
                    title = "Avtomatik yopish",
                    subtitle = "Javob berilgandan keyin suzuvchi aylana o'zi yopiladi",
                    checked = settings.autoDismissOverlay,
                    onCheckedChange = onToggleAutoDismiss
                )

                OutlinedButton(
                    onClick = onTestVoice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("test_tts_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ovozni sinab ko'rish")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF002026),
                checkedTrackColor = ElectricCyan
            )
        )
    }
}
