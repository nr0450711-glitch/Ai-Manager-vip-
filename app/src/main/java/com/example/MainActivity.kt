package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.assistant.PendingSensitiveAction
import com.example.assistant.PhoneControlExecutor
import com.example.assistant.VoiceEngineManager
import com.example.assistant.VoiceListeningState
import com.example.data.remote.OpenAiChatClient
import com.example.data.remote.SmartServicesClient
import com.example.data.repository.JarvisRepository
import com.example.data.repository.PreferencesRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ControlHubScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SocialStudioScreen
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JarvisVipTheme
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.PanelNavy
import com.example.ui.theme.PureIce
import com.example.ui.theme.VoidNavy
import com.example.ui.viewmodel.JarvisViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

private enum class JarvisTab(val label: String, val icon: ImageVector, val testTag: String) {
    HUD("HUD", Icons.Default.Dashboard, "nav_hud"),
    CHAT("Chat", Icons.AutoMirrored.Filled.Chat, "nav_chat"),
    CONTROL("Control", Icons.Default.Devices, "nav_control"),
    SOCIAL("Social", Icons.Default.Public, "nav_social"),
    SETTINGS("Settings", Icons.Default.Settings, "nav_settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JarvisVipTheme {
                JarvisRootApp()
            }
        }
    }
}

@Composable
fun JarvisRootApp() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            JarvisAuthenticatedShell(
                currentUserId = user.uid,
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "VIP User"
            )
        }
    }
}

@Composable
private fun JarvisAuthenticatedShell(
    currentUserId: String,
    displayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: JarvisViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val repository = JarvisRepository(app)
                val prefsRepo = PreferencesRepository(app)
                val phoneControl = PhoneControlExecutor(app)
                val smartServices = SmartServicesClient()
                val openAiClient = OpenAiChatClient()
                val voiceEngine = VoiceEngineManager(app)
                JarvisViewModel(
                    appContext = app,
                    currentUserId = currentUserId,
                    displayName = displayName,
                    repository = repository,
                    prefsRepository = prefsRepo,
                    phoneControl = phoneControl,
                    smartServices = smartServices,
                    openAiClient = openAiClient,
                    voiceEngine = voiceEngine
                )
            }
        }
    )

    var selectedTab by rememberSaveable { mutableStateOf(JarvisTab.HUD) }
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val socialDraftsState by viewModel.socialDraftsState.collectAsStateWithLifecycle()
    val localSettings by viewModel.localSettings.collectAsStateWithLifecycle()
    val listeningState by viewModel.listeningState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val activeVoiceName by viewModel.activeVoiceName.collectAsStateWithLifecycle()
    val wakeGuardRunning by viewModel.wakeGuardRunning.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val notificationAccessConnected by viewModel.notificationAccessConnected.collectAsStateWithLifecycle()
    val accessibilityConnected by viewModel.accessibilityConnected.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val newsState by viewModel.newsState.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val pendingPermission by viewModel.pendingPermission.collectAsStateWithLifecycle()
    val contactMatches by viewModel.contactMatches.collectAsStateWithLifecycle()
    val selectedPhotoUri by viewModel.selectedPhotoUri.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val cameraTriggerCount by viewModel.cameraTriggerCount.collectAsStateWithLifecycle()
    val photoPickerTriggerCount by viewModel.photoPickerTriggerCount.collectAsStateWithLifecycle()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput(onNeedAudioPermission = {})
        } else {
            viewModel.setMicEnabled(false)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.toggleHandsFreeWakeWord(true) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val genericPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.clearPendingPermission()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setSelectedPhoto(uri)
            selectedTab = JarvisTab.SOCIAL
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let {
                viewModel.setSelectedPhoto(it)
                selectedTab = JarvisTab.SOCIAL
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = viewModel.createCameraCaptureUri()
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCameraCapture() {
        val hasCam = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCam) {
            val uri = viewModel.createCameraCaptureUri()
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(cameraTriggerCount) {
        if (cameraTriggerCount > 0) launchCameraCapture()
    }

    LaunchedEffect(photoPickerTriggerCount) {
        if (photoPickerTriggerCount > 0) {
            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    if (selectedTab != JarvisTab.HUD) {
        BackHandler { selectedTab = JarvisTab.HUD }
    }

    val onMicToggle = {
        if (listeningState is VoiceListeningState.Listening || isSpeaking) {
            viewModel.stopVoiceInput()
        } else {
            viewModel.startVoiceInput {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val onToggleWakeWord: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.toggleHandsFreeWakeWord(enabled) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Scaffold(
        containerColor = VoidNavy,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            BoxWithConstraints {
                if (maxWidth < 720.dp) {
                    NavigationBar(
                        containerColor = DeepNavy.copy(alpha = 0.96f),
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        JarvisTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = VoidNavy,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan,
                                    unselectedIconColor = MutedSteel,
                                    unselectedTextColor = MutedSteel
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(VoidNavy, DeepNavy, VoidNavy)))
                .padding(innerPadding)
        ) {
            val useRail = maxWidth >= 720.dp
            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail) {
                    NavigationRail(
                        containerColor = DeepNavy.copy(alpha = 0.96f),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        JarvisTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = VoidNavy,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan,
                                    unselectedIconColor = MutedSteel,
                                    unselectedTextColor = MutedSteel
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier.widthIn(max = 840.dp).fillMaxSize()
                    when (selectedTab) {
                        JarvisTab.HUD -> DashboardScreen(
                            displayName = displayName,
                            listeningState = listeningState,
                            isSpeaking = isSpeaking,
                            isThinking = isThinking,
                            micEnabled = localSettings.micEnabled,
                            wakeGuardRunning = wakeGuardRunning,
                            openAiConfigured = viewModel.isOpenAiConfigured(),
                            telemetry = telemetry,
                            weatherState = weatherState,
                            newsState = newsState,
                            tasksState = tasksState,
                            onMicToggle = onMicToggle,
                            onQuickCommand = { cmd -> viewModel.sendCommand(cmd) },
                            onRefreshWeather = { viewModel.refreshWeather() },
                            onRefreshNews = { viewModel.refreshNews() },
                            onOpenNewsLink = { url, src -> viewModel.openUrl(url, src) },
                            onAddTask = { t, c, d -> viewModel.addTaskItem(t, c, d) },
                            onToggleTask = { id, done -> viewModel.toggleTaskCompleted(id, done) },
                            onDeleteTask = { id -> viewModel.deleteTask(id) },
                            modifier = contentModifier
                        )
                        JarvisTab.CHAT -> ChatScreen(
                            messagesState = messagesState,
                            listeningState = listeningState,
                            isThinking = isThinking,
                            isSpeaking = isSpeaking,
                            openAiConfigured = viewModel.isOpenAiConfigured(),
                            onSendMessage = { msg -> viewModel.sendCommand(msg) },
                            onMicToggle = onMicToggle,
                            onSpeakText = { text ->
                                val lang = com.example.data.model.PreferredLanguage.fromRaw(
                                    (profileState as? com.example.ui.viewmodel.UiState.Success)?.data?.preferredLanguage
                                )
                                viewModel.voiceEngine.speak(text, lang)
                            },
                            onClearChat = { viewModel.clearChatHistory() },
                            modifier = contentModifier
                        )
                        JarvisTab.CONTROL -> ControlHubScreen(
                            telemetry = telemetry,
                            contactMatches = contactMatches,
                            notifications = notifications,
                            notificationAccessConnected = notificationAccessConnected,
                            onLaunchApp = { app -> viewModel.launchApp(app) },
                            onOpenAppInfoToClose = { app -> viewModel.openAppInfo(app) },
                            onSearchContacts = { q -> viewModel.searchContacts(q) },
                            onCallContact = { name, num -> viewModel.requestPhoneCall(name, num) },
                            onSendSms = { rec, body -> viewModel.requestSms(rec, body) },
                            onWhatsApp = { target, body -> viewModel.requestWhatsApp(target, body) },
                            onSetVolume = { pct -> viewModel.setVolumePercent(pct) },
                            onSetAlarm = { h, m, lbl -> viewModel.setAlarm(h, m, lbl) },
                            onAddCalendarEvent = { t, d -> viewModel.addCalendarEvent(t, d) },
                            onOpenWifi = { viewModel.openWifi() },
                            onOpenBluetooth = { viewModel.openBluetooth() },
                            onOpenSettings = { viewModel.openDeviceSettings() },
                            onOpenCamera = { launchCameraCapture() },
                            onOpenSearch = { q -> viewModel.openWebSearch(q) },
                            onOpenNavigation = { d -> viewModel.openNavigation(d) },
                            onOpenNotificationSettings = { viewModel.openNotificationSettings() },
                            onClearNotifications = { viewModel.clearNotificationsInbox() },
                            modifier = contentModifier
                        )
                        JarvisTab.SOCIAL -> SocialStudioScreen(
                            selectedPhotoUri = selectedPhotoUri,
                            draftsState = socialDraftsState,
                            isThinking = isThinking,
                            onOpenFacebookApp = { viewModel.launchApp("Facebook") },
                            onPickPhoto = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onCapturePhoto = { launchCameraCapture() },
                            onClearPhoto = { viewModel.setSelectedPhoto(null) },
                            onGenerateCaptionWithAi = { topic, plat, cb ->
                                viewModel.generateSocialCaptionWithAi(topic, plat, cb)
                            },
                            onPrepareAndConfirmShare = { plat, cap, tgt ->
                                viewModel.prepareSocialDraft(plat, cap, tgt)
                            },
                            modifier = contentModifier
                        )
                        JarvisTab.SETTINGS -> SettingsScreen(
                            profileState = profileState,
                            memoriesState = memoriesState,
                            localSettings = localSettings,
                            activeVoiceName = activeVoiceName,
                            wakeGuardRunning = wakeGuardRunning,
                            notificationAccessConnected = notificationAccessConnected,
                            accessibilityConnected = accessibilityConnected,
                            openAiConfigured = viewModel.isOpenAiConfigured(),
                            onLanguageChange = { lang -> viewModel.updateLanguage(lang) },
                            onVoiceTuningChange = { p, r -> viewModel.updateVoiceTuning(p, r) },
                            onTestFemaleVoice = { viewModel.testFemaleVoice() },
                            onToggleMic = { viewModel.setMicEnabled(it) },
                            onToggleAutomation = { viewModel.setAutomationEnabled(it) },
                            onToggleAutoSpeak = { viewModel.setAutoSpeak(it) },
                            onToggleWakeWord = onToggleWakeWord,
                            onUpdateWeatherCity = { viewModel.updateWeatherCity(it) },
                            onUpdateBackendProxyUrl = { viewModel.updateBackendProxyUrl(it) },
                            onAddMemory = { k, v, c -> viewModel.addMemoryItem(k, v, c) },
                            onDeleteMemory = { viewModel.deleteMemoryItem(it) },
                            onOpenBatteryOptimization = { viewModel.openBatteryOptimization() },
                            onOpenNotificationSettings = { viewModel.openNotificationSettings() },
                            onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                            onSignOut = {
                                viewModel.stopVoiceInput()
                                signOutUser(credentialManager, scope)
                            },
                            modifier = contentModifier
                        )
                    }
                }
            }
        }
    }

    // Sensitive Action Confirmation Gate
    pendingConfirmation?.let { action ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingAction() },
            containerColor = PanelNavy,
            title = { Text(action.title, color = ElectricCyan) },
            text = { Text(action.summary, color = PureIce) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val directPermissionGranted = when (action) {
                        is PendingSensitiveAction.PhoneCall -> ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CALL_PHONE
                        ) == PackageManager.PERMISSION_GRANTED
                        is PendingSensitiveAction.SendSms -> ContextCompat.checkSelfPermission(
                            context, Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        else -> false
                    }
                    Button(
                        onClick = { viewModel.confirmPendingAction(directSystemExecute = directPermissionGranted) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy),
                        modifier = Modifier.testTag("confirm_sensitive_action_button")
                    ) {
                        Text(if (action is PendingSensitiveAction.SaveMemory) "Approve" else "Confirm & Proceed")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingAction() }) {
                    Text("Cancel", color = MutedSteel)
                }
            }
        )
    }

    // Runtime Permission Rationale Gate
    pendingPermission?.let { (permission, rationale) ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPendingPermission() },
            containerColor = PanelNavy,
            title = { Text("Permission Needed", color = ElectricCyan) },
            text = { Text(rationale, color = PureIce) },
            confirmButton = {
                Button(
                    onClick = { genericPermissionLauncher.launch(permission) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy)
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearPendingPermission() }) {
                    Text("Not Now", color = MutedSteel)
                }
            }
        )
    }
}
