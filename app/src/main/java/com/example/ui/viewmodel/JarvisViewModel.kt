package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.assistant.AccessibilityBridgeState
import com.example.assistant.CapturedNotification
import com.example.assistant.ContactMatch
import com.example.assistant.DeviceTelemetry
import com.example.assistant.ExecutionOutcome
import com.example.assistant.JarvisCommandRouter
import com.example.assistant.JarvisForegroundService
import com.example.assistant.NotificationInboxStore
import com.example.assistant.PendingSensitiveAction
import com.example.assistant.PhoneControlExecutor
import com.example.assistant.VoiceEngineManager
import com.example.assistant.VoiceListeningState
import com.example.assistant.WakeWordBus
import com.example.data.model.AssistantTask
import com.example.data.model.ChatMessageRecord
import com.example.data.model.DraftStatus
import com.example.data.model.MemoryCategory
import com.example.data.model.PreferredLanguage
import com.example.data.model.SocialDraft
import com.example.data.model.SocialPlatform
import com.example.data.model.TaskCategory
import com.example.data.model.UserMemory
import com.example.data.model.UserProfile
import com.example.data.remote.NewsHeadline
import com.example.data.remote.OpenAiChatClient
import com.example.data.remote.SmartServicesClient
import com.example.data.remote.WeatherReport
import com.example.data.repository.JarvisRepository
import com.example.data.repository.LocalDeviceSettings
import com.example.data.repository.PreferencesRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface SmartDataState<out T> {
    data object Idle : SmartDataState<Nothing>
    data object Loading : SmartDataState<Nothing>
    data class Ready<T>(val data: T) : SmartDataState<T>
    data class Error(val message: String) : SmartDataState<Nothing>
}

class JarvisViewModel(
    private val appContext: Context,
    private val currentUserId: String,
    private val displayName: String,
    private val repository: JarvisRepository,
    private val prefsRepository: PreferencesRepository,
    private val phoneControl: PhoneControlExecutor,
    private val smartServices: SmartServicesClient,
    private val openAiClient: OpenAiChatClient,
    val voiceEngine: VoiceEngineManager
) : ViewModel() {

    private val router = JarvisCommandRouter(phoneControl, smartServices, openAiClient, repository)

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { err -> emitErrorOrThrow(err, "profile") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val tasksState: StateFlow<UiState<List<AssistantTask>>> = repository.observeTasks(currentUserId)
        .map<List<AssistantTask>, UiState<List<AssistantTask>>> { UiState.Success(it) }
        .catch { err -> emitErrorOrThrow(err, "tasks") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val memoriesState: StateFlow<UiState<List<UserMemory>>> = repository.observeMemories(currentUserId)
        .map<List<UserMemory>, UiState<List<UserMemory>>> { UiState.Success(it) }
        .catch { err -> emitErrorOrThrow(err, "memories") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val messagesState: StateFlow<UiState<List<ChatMessageRecord>>> = repository.observeMessages(currentUserId)
        .map<List<ChatMessageRecord>, UiState<List<ChatMessageRecord>>> { UiState.Success(it) }
        .catch { err -> emitErrorOrThrow(err, "messages") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val socialDraftsState: StateFlow<UiState<List<SocialDraft>>> = repository.observeSocialDrafts(currentUserId)
        .map<List<SocialDraft>, UiState<List<SocialDraft>>> { UiState.Success(it) }
        .catch { err -> emitErrorOrThrow(err, "socialDrafts") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val localSettings: StateFlow<LocalDeviceSettings> = prefsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LocalDeviceSettings())

    val listeningState: StateFlow<VoiceListeningState> = voiceEngine.listeningState
    val isSpeaking: StateFlow<Boolean> = voiceEngine.isSpeaking
    val activeVoiceName: StateFlow<String> = voiceEngine.activeVoiceName
    val wakeGuardRunning: StateFlow<Boolean> = WakeWordBus.serviceRunning
    val notifications: StateFlow<List<CapturedNotification>> = NotificationInboxStore.notifications
    val notificationAccessConnected: StateFlow<Boolean> = NotificationInboxStore.isConnected
    val accessibilityConnected: StateFlow<Boolean> = AccessibilityBridgeState.isEnabled

    private val _telemetry = MutableStateFlow(phoneControl.getDeviceTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _weatherState = MutableStateFlow<SmartDataState<WeatherReport>>(SmartDataState.Idle)
    val weatherState: StateFlow<SmartDataState<WeatherReport>> = _weatherState.asStateFlow()

    private val _newsState = MutableStateFlow<SmartDataState<List<NewsHeadline>>>(SmartDataState.Idle)
    val newsState: StateFlow<SmartDataState<List<NewsHeadline>>> = _newsState.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingSensitiveAction?>(null)
    val pendingConfirmation: StateFlow<PendingSensitiveAction?> = _pendingConfirmation.asStateFlow()

    private val _pendingPermission = MutableStateFlow<Pair<String, String>?>(null)
    val pendingPermission: StateFlow<Pair<String, String>?> = _pendingPermission.asStateFlow()

    private val _contactMatches = MutableStateFlow<List<ContactMatch>>(emptyList())
    val contactMatches: StateFlow<List<ContactMatch>> = _contactMatches.asStateFlow()

    private val _selectedPhotoUri = MutableStateFlow<Uri?>(null)
    val selectedPhotoUri: StateFlow<Uri?> = _selectedPhotoUri.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _cameraTriggerCount = MutableStateFlow(0)
    val cameraTriggerCount: StateFlow<Int> = _cameraTriggerCount.asStateFlow()

    private val _photoPickerTriggerCount = MutableStateFlow(0)
    val photoPickerTriggerCount: StateFlow<Int> = _photoPickerTriggerCount.asStateFlow()

    init {
        voiceEngine.initTts()
        viewModelScope.launch {
            repository.ensureUserProfile(displayName)
        }
        viewModelScope.launch {
            profileState.collectLatest { state ->
                if (state is UiState.Success && state.data != null) {
                    voiceEngine.updateVoiceParameters(
                        state.data.voicePitch.toFloat(),
                        state.data.voiceRate.toFloat()
                    )
                }
            }
        }
        viewModelScope.launch {
            WakeWordBus.wakeCommands.collect { wakePhrase ->
                if (localSettings.value.micEnabled) {
                    sendCommand(wakePhrase)
                }
            }
        }
        refreshTelemetry()
        refreshWeather()
        refreshNews()
    }

    fun isOpenAiConfigured(): Boolean = openAiClient.isConfigured(localSettings.value.backendProxyUrl)

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun clearPendingPermission() {
        _pendingPermission.value = null
    }

    fun refreshTelemetry() {
        _telemetry.value = phoneControl.getDeviceTelemetry()
    }

    fun refreshWeather() {
        viewModelScope.launch {
            _weatherState.value = SmartDataState.Loading
            val coords = phoneControl.getLastCoarseCoordinates()
            val res = if (coords != null) {
                smartServices.fetchWeatherByCoordinates(coords.first, coords.second, "Current Location")
            } else {
                smartServices.fetchWeatherByCity(localSettings.value.weatherCity)
            }
            _weatherState.value = res.fold(
                onSuccess = { SmartDataState.Ready(it) },
                onFailure = { SmartDataState.Error(it.localizedMessage ?: "Weather unavailable") }
            )
        }
    }

    fun refreshNews() {
        viewModelScope.launch {
            _newsState.value = SmartDataState.Loading
            val pref = currentPreferredLanguage()
            val hindi = pref == PreferredLanguage.HINDI
            _newsState.value = smartServices.fetchDailyNews(hindi).fold(
                onSuccess = { SmartDataState.Ready(it) },
                onFailure = { SmartDataState.Error(it.localizedMessage ?: "News unavailable") }
            )
        }
    }

    fun sendCommand(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            _isThinking.value = true
            val prefLang = currentPreferredLanguage()
            val langTag = if (trimmed.any { it in '\u0900'..'\u097F' }) "hi-IN" else "en-IN"
            repository.addMessage("USER", trimmed, langTag)

            val history = (messagesState.value as? UiState.Success)?.data.orEmpty()
            val memories = (memoriesState.value as? UiState.Success)?.data.orEmpty()
            val tasks = (tasksState.value as? UiState.Success)?.data.orEmpty()

            val outcome = router.processCommand(
                rawInput = trimmed,
                preferredLanguage = prefLang,
                automationEnabled = localSettings.value.automationEnabled,
                weatherCity = localSettings.value.weatherCity,
                backendProxyUrl = localSettings.value.backendProxyUrl,
                history = history,
                memories = memories,
                tasks = tasks
            )

            repository.addMessage("ASSISTANT", outcome.replyText, langTag, outcome.actionSummary)
            if (localSettings.value.autoSpeakReplies) {
                voiceEngine.speak(outcome.replyText, prefLang)
            }
            outcome.pendingConfirmation?.let { _pendingConfirmation.value = it }
            outcome.permissionRequest?.let { _pendingPermission.value = it }
            if (outcome.openCameraRequested) _cameraTriggerCount.value += 1
            if (outcome.openPhotoPickerRequested) _photoPickerTriggerCount.value += 1
            refreshTelemetry()
            _isThinking.value = false
        }
    }

    fun confirmPendingAction(directSystemExecute: Boolean = false) {
        val action = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            when (action) {
                is PendingSensitiveAction.PhoneCall -> {
                    val res = phoneControl.dialOrCall(action.phoneNumber, directCallConfirmed = directSystemExecute)
                    handleOutcomeBanner(res)
                }
                is PendingSensitiveAction.SendSms -> {
                    val res = phoneControl.sendOrComposeSms(action.recipient, action.message, directSendConfirmed = directSystemExecute)
                    handleOutcomeBanner(res)
                }
                is PendingSensitiveAction.WhatsAppDraft -> {
                    val res = phoneControl.shareToWhatsApp(action.phoneOrLabel, action.message)
                    handleOutcomeBanner(res)
                }
                is PendingSensitiveAction.SocialShare -> {
                    val res = phoneControl.shareSocialPost(action.platform.name, action.caption, _selectedPhotoUri.value)
                    if (res is ExecutionOutcome.Success && !action.draftId.isNullOrBlank()) {
                        repository.markSocialDraftStatus(action.draftId, DraftStatus.SHARED)
                    }
                    handleOutcomeBanner(res)
                }
                is PendingSensitiveAction.SaveMemory -> {
                    val res = repository.addMemory(action.key, action.value, MemoryCategory.PREFERENCE)
                    _statusBanner.value = if (res.isSuccess) {
                        "Saved preference '${action.key}' to your memory."
                    } else {
                        "Failed to save memory: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                }
            }
        }
    }

    fun dismissPendingAction() {
        val action = _pendingConfirmation.value
        _pendingConfirmation.value = null
        if (action is PendingSensitiveAction.SocialShare && !action.draftId.isNullOrBlank()) {
            viewModelScope.launch {
                repository.markSocialDraftStatus(action.draftId, DraftStatus.CANCELLED)
            }
        }
        _statusBanner.value = "Action cancelled by user."
    }

    fun startVoiceInput(onNeedAudioPermission: () -> Unit) {
        if (!localSettings.value.micEnabled) {
            _statusBanner.value = "Microphone is disabled in Security Controls."
            return
        }
        val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            onNeedAudioPermission()
            return
        }
        voiceEngine.startListening(
            preferredLanguage = currentPreferredLanguage(),
            onResult = { spoken -> sendCommand(spoken) },
            onError = { err -> _statusBanner.value = err }
        )
    }

    fun stopVoiceInput() {
        voiceEngine.stopListening()
        voiceEngine.stopSpeaking()
    }

    fun toggleHandsFreeWakeWord(enabled: Boolean, onNeedAudioPermission: () -> Unit) {
        viewModelScope.launch {
            if (enabled) {
                val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasAudio) {
                    onNeedAudioPermission()
                    return@launch
                }
                prefsRepository.setHandsFreeWakeWord(true)
                repository.updateProfilePreferences(wakeWordEnabled = true)
                JarvisForegroundService.start(appContext)
                _statusBanner.value = "Hands-free 'Hey JARVIS' Voice Guard started."
            } else {
                prefsRepository.setHandsFreeWakeWord(false)
                repository.updateProfilePreferences(wakeWordEnabled = false)
                JarvisForegroundService.stop(appContext)
                _statusBanner.value = "Hands-free Voice Guard stopped."
            }
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setMicEnabled(enabled)
            if (!enabled) {
                voiceEngine.stopListening()
                JarvisForegroundService.stop(appContext)
                repository.updateProfilePreferences(wakeWordEnabled = false)
            }
            _statusBanner.value = if (enabled) "Microphone enabled." else "Microphone disabled (Kill-Switch active)."
        }
    }

    fun setAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setAutomationEnabled(enabled)
            repository.updateProfilePreferences(automationConfirmed = enabled)
            _statusBanner.value = if (enabled) "Device automation enabled." else "Device automation paused."
        }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setAutoSpeak(enabled)
            if (!enabled) voiceEngine.stopSpeaking()
        }
    }

    fun updateLanguage(lang: PreferredLanguage) {
        viewModelScope.launch {
            repository.updateProfilePreferences(preferredLanguage = lang)
            refreshNews()
        }
    }

    fun updateVoiceTuning(pitch: Float, rate: Float) {
        voiceEngine.updateVoiceParameters(pitch, rate)
        viewModelScope.launch {
            repository.updateProfilePreferences(voicePitch = pitch.toDouble(), voiceRate = rate.toDouble())
        }
    }

    fun testFemaleVoice() {
        val sample = when (currentPreferredLanguage()) {
            PreferredLanguage.HINDI -> "नमस्ते, मैं जार्विस वीआईपी हूँ। आपकी आवाज़ और सिस्टम कंट्रोल पूरी तरह तैयार हैं।"
            else -> "Hello! I am JARVIS VIP. Your bilingual female voice assistant is online and calibrated."
        }
        voiceEngine.speak(sample, currentPreferredLanguage())
    }

    fun updateWeatherCity(city: String) {
        viewModelScope.launch {
            prefsRepository.setWeatherCity(city)
            refreshWeather()
        }
    }

    fun updateBackendProxyUrl(url: String) {
        viewModelScope.launch {
            prefsRepository.setBackendProxyUrl(url)
            _statusBanner.value = if (url.isBlank()) {
                "Cleared custom backend proxy URL."
            } else {
                "Saved secure backend proxy URL."
            }
        }
    }

    fun addTaskItem(title: String, category: TaskCategory, dueLabel: String?) {
        viewModelScope.launch {
            val res = repository.addTask(title, category, dueLabel)
            _statusBanner.value = if (res.isSuccess) {
                "Added ${category.label}: $title"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to add task"
            }
        }
    }

    fun toggleTaskCompleted(taskId: String, completed: Boolean) {
        viewModelScope.launch {
            repository.toggleTaskCompleted(taskId, completed)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun addMemoryItem(key: String, value: String, category: MemoryCategory) {
        viewModelScope.launch {
            val res = repository.addMemory(key, value, category)
            _statusBanner.value = if (res.isSuccess) "Saved preference: $key" else "Failed to save preference"
        }
    }

    fun deleteMemoryItem(id: String) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearChatHistory() {
        val ids = (messagesState.value as? UiState.Success)?.data?.map { it.id }.orEmpty()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.clearConversation(ids)
            _statusBanner.value = "Cleared conversation history."
        }
    }

    fun setSelectedPhoto(uri: Uri?) {
        _selectedPhotoUri.value = uri
    }

    fun createCameraCaptureUri(): Uri = phoneControl.createCameraImageUri()

    fun searchContacts(query: String) {
        val (outcome, matches) = phoneControl.searchContacts(query)
        _contactMatches.value = matches
        if (outcome is ExecutionOutcome.PermissionRequired) {
            _pendingPermission.value = outcome.permission to outcome.rationale
        } else {
            handleOutcomeBanner(outcome)
        }
    }

    fun requestPhoneCall(contactName: String?, phone: String) {
        _pendingConfirmation.value = PendingSensitiveAction.PhoneCall(contactName, phone)
    }

    fun requestSms(recipient: String, message: String) {
        _pendingConfirmation.value = PendingSensitiveAction.SendSms(recipient, message)
    }

    fun requestWhatsApp(phoneOrLabel: String?, message: String) {
        _pendingConfirmation.value = PendingSensitiveAction.WhatsAppDraft(phoneOrLabel, message)
    }

    fun prepareSocialDraft(platform: SocialPlatform, caption: String, targetLabel: String?) {
        viewModelScope.launch {
            val hasPhoto = _selectedPhotoUri.value != null
            val res = repository.createSocialDraft(platform, caption, targetLabel, hasPhoto)
            if (res.isSuccess) {
                _pendingConfirmation.value = PendingSensitiveAction.SocialShare(
                    platform = platform,
                    caption = caption,
                    draftId = res.getOrNull()
                )
            } else {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save draft"
            }
        }
    }

    fun generateSocialCaptionWithAi(topic: String, platform: SocialPlatform, onGenerated: (String) -> Unit) {
        if (topic.isBlank()) return
        viewModelScope.launch {
            _isThinking.value = true
            val prompt = "Write an engaging ${platform.label} post caption (with 3 relevant hashtags) about: $topic"
            when (val res = openAiClient.chat(
                userMessage = prompt,
                preferredLanguage = currentPreferredLanguage(),
                history = emptyList(),
                memories = (memoriesState.value as? UiState.Success)?.data.orEmpty(),
                tasks = emptyList(),
                backendProxyUrl = localSettings.value.backendProxyUrl
            )) {
                is com.example.data.remote.OpenAiResult.Success -> onGenerated(res.reply)
                is com.example.data.remote.OpenAiResult.NeedsConfiguration -> _statusBanner.value = res.message
                is com.example.data.remote.OpenAiResult.Error -> _statusBanner.value = res.message
            }
            _isThinking.value = false
        }
    }

    fun launchApp(name: String) = handleOutcomeBanner(phoneControl.launchAppByName(name))
    fun openAppInfo(name: String) = handleOutcomeBanner(phoneControl.openAppInfoToClose(name))
    fun setVolumePercent(pct: Int) {
        handleOutcomeBanner(phoneControl.setMediaVolumePercent(pct))
        refreshTelemetry()
    }
    fun setAlarm(hour: Int, minute: Int, label: String) {
        handleOutcomeBanner(phoneControl.setAlarm(hour, minute, label))
        addTaskItem("Alarm $hour:${minute.toString().padStart(2, '0')} ($label)", TaskCategory.ALARM, "$hour:${minute.toString().padStart(2, '0')}")
    }
    fun addCalendarEvent(title: String, desc: String) = handleOutcomeBanner(phoneControl.insertCalendarEvent(title, desc))
    fun openWifi() = handleOutcomeBanner(phoneControl.openWifiSettings())
    fun openBluetooth() = handleOutcomeBanner(phoneControl.openBluetoothSettings())
    fun openDeviceSettings() = handleOutcomeBanner(phoneControl.openPhoneSettings())
    fun openBatteryOptimization() = handleOutcomeBanner(phoneControl.openBatteryOptimizationSettings())
    fun openNotificationSettings() = handleOutcomeBanner(phoneControl.openNotificationListenerSettings())
    fun openAccessibilitySettings() = handleOutcomeBanner(phoneControl.openAccessibilitySettings())
    fun openWebSearch(query: String) = handleOutcomeBanner(phoneControl.openWebSearch(query))
    fun openNavigation(dest: String) = handleOutcomeBanner(phoneControl.openNavigation(dest))
    fun openUrl(url: String, label: String) = handleOutcomeBanner(phoneControl.openWebUrl(url, label))
    fun clearNotificationsInbox() = NotificationInboxStore.clearAll()

    private fun handleOutcomeBanner(outcome: ExecutionOutcome) {
        when (outcome) {
            is ExecutionOutcome.Success -> _statusBanner.value = outcome.message
            is ExecutionOutcome.Failure -> _statusBanner.value = outcome.message
            is ExecutionOutcome.PermissionRequired -> _pendingPermission.value = outcome.permission to outcome.rationale
        }
    }

    private fun currentPreferredLanguage(): PreferredLanguage {
        val raw = (profileState.value as? UiState.Success)?.data?.preferredLanguage
        return PreferredLanguage.fromRaw(raw)
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<UiState<T>>.emitErrorOrThrow(
        error: Throwable,
        tag: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w("JarvisVM", "Error observing $tag", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load $tag"))
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.release()
    }
}
