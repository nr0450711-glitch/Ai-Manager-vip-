package com.example.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.model.PreferredLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

sealed interface VoiceListeningState {
    data object Idle : VoiceListeningState
    data class Listening(val partialText: String = "", val rmsDb: Float = 0f) : VoiceListeningState
    data class Error(val message: String) : VoiceListeningState
}

class VoiceEngineManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _listeningState = MutableStateFlow<VoiceListeningState>(VoiceListeningState.Idle)
    val listeningState: StateFlow<VoiceListeningState> = _listeningState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeVoiceName = MutableStateFlow("Initializing female voice...")
    val activeVoiceName: StateFlow<String> = _activeVoiceName.asStateFlow()

    private var currentPitch = 1.08f
    private var currentRate = 1.0f

    fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                configureFemaleVoice(Locale("en", "IN"))
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            } else {
                _activeVoiceName.value = "System TTS unavailable"
            }
        }
    }

    fun updateVoiceParameters(pitch: Float, rate: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        currentRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setPitch(currentPitch)
        tts?.setSpeechRate(currentRate)
    }

    fun speak(text: String, preferredLanguage: PreferredLanguage) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!ttsReady) initTts()

        val hasDevanagari = clean.any { it in '\u0900'..'\u097F' }
        val targetLocale = when {
            hasDevanagari || preferredLanguage == PreferredLanguage.HINDI -> Locale("hi", "IN")
            else -> Locale("en", "IN")
        }
        configureFemaleVoice(targetLocale)
        tts?.setPitch(currentPitch)
        tts?.setSpeechRate(currentRate)
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
    }

    private fun configureFemaleVoice(locale: Locale) {
        val engine = tts ?: return
        val voices = try {
            engine.voices.orEmpty()
        } catch (_: Exception) {
            emptySet<Voice>()
        }

        val matchingLocale = voices.filter { v ->
            v.locale.language == locale.language && !v.isNetworkConnectionRequired
        }.ifEmpty {
            voices.filter { it.locale.language == locale.language }
        }

        val femaleCandidate = matchingLocale.firstOrNull { v ->
            val name = v.name.lowercase()
            "female" in name || "-f-" in name || "f00" in name || "hia" in name ||
                "hid" in name || "ena" in name || "end" in name || "sfg" in name || "iob" in name
        } ?: matchingLocale.firstOrNull()

        if (femaleCandidate != null) {
            engine.voice = femaleCandidate
            _activeVoiceName.value = "Female AI (${femaleCandidate.locale.displayLanguage})"
        } else {
            engine.language = locale
            _activeVoiceName.value = "Female AI (${locale.displayLanguage})"
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        preferredLanguage: PreferredLanguage,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isSpeechRecognitionAvailable()) {
            val msg = "On-device Speech Recognition service is unavailable on this device."
            _listeningState.value = VoiceListeningState.Error(msg)
            onError(msg)
            return
        }

        stopSpeaking()
        stopListening()

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _listeningState.value = VoiceListeningState.Listening("Listening…")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val prev = _listeningState.value
                if (prev is VoiceListeningState.Listening) {
                    _listeningState.value = prev.copy(rmsDb = rmsdB.coerceIn(0f, 12f))
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand clearly — please try again"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    else -> "Voice input error ($error)"
                }
                _listeningState.value = VoiceListeningState.Idle
                onError(msg)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                _listeningState.value = VoiceListeningState.Idle
                if (best.isNotEmpty()) {
                    onResult(best)
                } else {
                    onError("No speech recognized.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (partial.isNotEmpty()) {
                    val prev = _listeningState.value
                    val rms = (prev as? VoiceListeningState.Listening)?.rmsDb ?: 0f
                    _listeningState.value = VoiceListeningState.Listening(partial, rms)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val langTag = when (preferredLanguage) {
            PreferredLanguage.HINDI -> "hi-IN"
            PreferredLanguage.ENGLISH -> "en-IN"
            PreferredLanguage.BILINGUAL -> "en-IN"
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        _listeningState.value = VoiceListeningState.Listening()
        recognizer.startListening(intent)
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        _listeningState.value = VoiceListeningState.Idle
    }

    fun release() {
        stopListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
