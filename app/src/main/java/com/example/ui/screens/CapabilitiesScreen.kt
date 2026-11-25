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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.PermissionsHealth
import com.example.ui.theme.AmberThinking
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceBorder
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CapabilitiesScreen(
    permissions: PermissionsHealth,
    onRequestMic: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onRequestContacts: () -> Unit,
    onRequestCall: () -> Unit,
    onRequestSms: () -> Unit,
    onRequestNotifications: () -> Unit
) {
    val percent = (permissions.readinessFraction * 100).toInt()

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Imkoniyatlar va Ruxsatlar",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Yordamchi telefondagi barcha ilovalarni ko'rishi, boshqarishi va 24/7 ishlashi uchun ruxsatlarni yoqing.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // Readiness Card
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tayyorlik darajasi",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (percent == 100) EmeraldActive else ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                LinearProgressIndicator(
                    progress = { permissions.readinessFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = if (percent == 100) EmeraldActive else ElectricCyan,
                    trackColor = ObsidianSurfaceElevated,
                    strokeCap = StrokeCap.Round
                )
            }

            PermissionItemCard(
                icon = Icons.Rounded.Mic,
                title = "Mikrofon (24/7 Tinglash)",
                description = "Chaqiruv so'zi va ovozli buyruqlarni orqa fonda tinglash uchun zarur.",
                isGranted = permissions.micGranted,
                buttonTag = "enable_mic_button",
                onEnable = onRequestMic
            )

            PermissionItemCard(
                icon = Icons.Rounded.AccessibilityNew,
                title = "Ekran Nazorati (Accessibility)",
                description = "Ekrandagi matnlarni ko'rish, Telegram/ilovalarga kirish, bosish va yozish uchun.",
                isGranted = permissions.accessibilityGranted,
                buttonTag = "enable_accessibility_button",
                onEnable = onOpenAccessibility
            )

            PermissionItemCard(
                icon = Icons.Rounded.Layers,
                title = "Suzuvchi Dumaloq Animatsiya",
                description = "Ilovadan chiqqanda ham boshqa ilovalar ustida animatsiyali oyna chiqishi uchun.",
                isGranted = permissions.overlayGranted,
                buttonTag = "enable_overlay_button",
                onEnable = onOpenOverlay
            )

            PermissionItemCard(
                icon = Icons.Rounded.Contacts,
                title = "Kontaktlar",
                description = "\"Oyimga qo'ng'iroq qil\" kabi buyruqlarda kontaktlarni topish uchun.",
                isGranted = permissions.contactsGranted,
                buttonTag = "enable_contacts_button",
                onEnable = onRequestContacts
            )

            PermissionItemCard(
                icon = Icons.Rounded.Call,
                title = "Telefon Qo'ng'irog'i",
                description = "Kontakt yoki aytilgan raqamga avtomatik qo'ng'iroq qilish uchun.",
                isGranted = permissions.callGranted,
                buttonTag = "enable_call_button",
                onEnable = onRequestCall
            )

            PermissionItemCard(
                icon = Icons.Rounded.Sms,
                title = "SMS Yuborish",
                description = "Kontaktlarga to'g'ridan-to'g'ri SMS xabar yuborish uchun.",
                isGranted = permissions.smsGranted,
                buttonTag = "enable_sms_button",
                onEnable = onRequestSms
            )

            PermissionItemCard(
                icon = Icons.Rounded.Notifications,
                title = "Bildirishnomalar",
                description = "24/7 orqa fon xizmatini barqaror ushlab turish uchun.",
                isGranted = permissions.notificationsGranted,
                buttonTag = "enable_notifications_button",
                onEnable = onRequestNotifications
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonTag: String,
    onEnable: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ObsidianSurface)
            .border(
                1.dp,
                if (isGranted) EmeraldActive.copy(alpha = 0.32f) else ObsidianSurfaceBorder,
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isGranted) EmeraldActive.copy(alpha = 0.14f)
                    else AmberThinking.copy(alpha = 0.14f)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) EmeraldActive else AmberThinking,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        if (isGranted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Yoqilgan",
                tint = EmeraldActive,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Button(
                onClick = onEnable,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Color(0xFF002026)
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag(buttonTag)
            ) {
                Text("Yoqish", fontWeight = FontWeight.Bold)
            }
        }
    }
}
