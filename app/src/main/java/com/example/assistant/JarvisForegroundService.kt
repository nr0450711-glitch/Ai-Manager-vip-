package com.example.assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object WakeWordBus {
    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _wakeCommands = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val wakeCommands: SharedFlow<String> = _wakeCommands.asSharedFlow()

    fun setRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun emitWakeCommand(command: String) {
        _wakeCommands.tryEmit(command)
    }
}

class JarvisForegroundService : Service() {
    companion object {
        const val ACTION_START = "com.example.action.START_VOICE_GUARD"
        const val ACTION_STOP = "com.example.action.STOP_VOICE_GUARD"
        private const val CHANNEL_ID = "jarvis_voice_guard_channel"
        private const val NOTIFICATION_ID = 7701

        fun start(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        val hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudio) {
            stopSelfCleanly()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        active = true
        WakeWordBus.setRunning(true)
        scheduleNextListen(300L)
        return START_STICKY
    }

    private fun scheduleNextListen(delayMs: Long) {
        if (!active) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (active) beginWakeListen()
        }, delayMs)
    }

    private fun beginWakeListen() {
        if (!active || !SpeechRecognizer.isRecognitionAvailable(this)) return
        try {
            recognizer?.destroy()
            val sr = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    scheduleNextListen(1200L)
                }

                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    handleHeardPhrase(text)
                    scheduleNextListen(800L)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            sr.startListening(intent)
        } catch (_: Exception) {
            scheduleNextListen(2000L)
        }
    }

    private fun handleHeardPhrase(raw: String) {
        if (raw.isBlank()) return
        val lower = raw.lowercase()
        val wakeRegex = Regex("""^(hey\s+jarvis|ok\s+jarvis|jarvis|हे\s+जार्विस|जार्विस)[,:\s]*""")
        if (wakeRegex.containsMatchIn(lower) || "hey jarvis" in lower || "हे जार्विस" in lower) {
            val cleaned = raw.replace(Regex("""(?i)(hey\s+jarvis|ok\s+jarvis|jarvis|हे\s+जार्विस|जार्विस)[,:\s]*"""), "").trim()
            WakeWordBus.emitWakeCommand(cleaned.ifBlank { "Hey JARVIS" })
        }
    }

    private fun stopSelfCleanly() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        WakeWordBus.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        WakeWordBus.setRunning(false)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.foreground_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher_round)
        .setContentTitle("JARVIS VIP Voice Guard Active")
        .setContentText("Listening for \"Hey JARVIS\". Tap to open or stop.")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            0,
            "Stop Guard",
            PendingIntent.getService(
                this,
                1,
                Intent(this, JarvisForegroundService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()
}
