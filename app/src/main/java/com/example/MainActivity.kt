package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.screens.ApiSetupScreen
import com.example.ui.screens.CapabilitiesScreen
import com.example.ui.screens.CoreOrbScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.TelefonimTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

object AppRoutes {
    const val CORE = "core"
    const val HISTORY = "history"
    const val CAPABILITIES = "capabilities"
    const val SETTINGS = "settings"
}

private data class NavTabItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TelefonimTheme {
                val viewModel: MainViewModel = viewModel()
                TelefonimAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun TelefonimAppRoot(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startServicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshPermissions()
        val micOk = results[Manifest.permission.RECORD_AUDIO] ?: permissions.micGranted
        if (micOk) {
            viewModel.toggleAssistantService()
        }
    }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions()
    }

    val voiceTriggerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            viewModel.triggerVoiceListen()
        }
    }

    if (settings.groqApiKey.isBlank()) {
        ApiSetupScreen(
            initialWakeWord = settings.wakeWord,
            setupState = setupState,
            onConnectClick = { key, wake ->
                viewModel.validateAndSaveApiKey(key, wake)
            }
        )
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.CORE

    val tabs = listOf(
        NavTabItem(
            route = AppRoutes.CORE,
            label = "Asosiy",
            selectedIcon = Icons.Rounded.AutoAwesome,
            unselectedIcon = Icons.Outlined.AutoAwesome,
            testTag = "nav_core"
        ),
        NavTabItem(
            route = AppRoutes.HISTORY,
            label = "Tarix",
            selectedIcon = Icons.Rounded.History,
            unselectedIcon = Icons.Outlined.History,
            testTag = "nav_history"
        ),
        NavTabItem(
            route = AppRoutes.CAPABILITIES,
            label = "Ruxsatlar",
            selectedIcon = Icons.Rounded.Security,
            unselectedIcon = Icons.Outlined.Security,
            testTag = "nav_capabilities"
        ),
        NavTabItem(
            route = AppRoutes.SETTINGS,
            label = "Sozlama",
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            testTag = "nav_settings"
        )
    )

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val useRail = maxWidth >= 840.dp

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        contentColor = TextPrimary
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                    indicatorColor = ObsidianSurfaceElevated
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = ObsidianSurface,
                        contentColor = TextPrimary
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                    indicatorColor = ObsidianSurfaceElevated
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoutes.CORE
                    ) {
                        composable(AppRoutes.CORE) {
                            CoreOrbScreen(
                                settings = settings,
                                runtimeState = runtimeState,
                                permissions = permissions,
                                onStartStopClick = {
                                    if (runtimeState.isServiceRunning) {
                                        viewModel.toggleAssistantService()
                                    } else {
                                        val needed = buildList {
                                            if (!permissions.micGranted) add(Manifest.permission.RECORD_AUDIO)
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.notificationsGranted) {
                                                add(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            if (!permissions.contactsGranted) add(Manifest.permission.READ_CONTACTS)
                                            if (!permissions.callGranted) add(Manifest.permission.CALL_PHONE)
                                        }
                                        if (needed.isNotEmpty()) {
                                            startServicePermissionLauncher.launch(needed.toTypedArray())
                                        } else {
                                            viewModel.toggleAssistantService()
                                        }
                                    }
                                },
                                onMicListenClick = {
                                    if (!permissions.micGranted) {
                                        voiceTriggerLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.triggerVoiceListen()
                                    }
                                },
                                onSendCommand = { cmd ->
                                    viewModel.sendTextCommand(cmd)
                                },
                                onOpenPermissions = {
                                    navigateToTab(AppRoutes.CAPABILITIES)
                                }
                            )
                        }

                        composable(AppRoutes.HISTORY) {
                            HistoryScreen(
                                history = history,
                                onClearHistory = { viewModel.clearHistory() }
                            )
                        }

                        composable(AppRoutes.CAPABILITIES) {
                            CapabilitiesScreen(
                                permissions = permissions,
                                onRequestMic = {
                                    singlePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onOpenAccessibility = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                onOpenOverlay = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                onRequestContacts = {
                                    singlePermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                },
                                onRequestCall = {
                                    singlePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                                },
                                onRequestSms = {
                                    singlePermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                },
                                onRequestNotifications = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        singlePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }

                        composable(AppRoutes.SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                setupState = setupState,
                                onSaveWakeWords = { viewModel.saveWakeWords(it) },
                                onSaveUnlockPin = { viewModel.saveUnlockPin(it) },
                                onUpdateApiKey = { viewModel.validateAndSaveApiKey(it) },
                                onRefreshModels = { viewModel.refreshFreeModels() },
                                onSelectModel = { viewModel.selectModel(it) },
                                onToggleAutoFreeModel = { viewModel.setAutoFreeModel(it) },
                                onToggleVoiceFeedback = { viewModel.setVoiceFeedback(it) },
                                onToggleAutoDismiss = { viewModel.setAutoDismissOverlay(it) },
                                onToggleContinuousWake = { viewModel.setContinuousWake(it) },
                                onTestVoice = { viewModel.testVoiceOutput() }
                            )
                        }
                    }
                }
            }
        }
    }
}
