package com.example.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long
)

object NotificationInboxStore {
    private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }

    fun addOrUpdate(item: CapturedNotification) {
        val filtered = _notifications.value.filterNot { it.key == item.key }
        _notifications.value = (listOf(item) + filtered).take(20)
    }

    fun remove(key: String) {
        _notifications.value = _notifications.value.filterNot { it.key == key }
    }

    fun clearAll() {
        _notifications.value = emptyList()
    }
}

class JarvisNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationInboxStore.setConnected(true)
        try {
            activeNotifications?.take(15)?.forEach { pushNotification(it) }
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationInboxStore.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { pushNotification(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let { NotificationInboxStore.remove(it) }
    }

    private fun pushNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && body.isBlank()) return
        NotificationInboxStore.addOrUpdate(
            CapturedNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                title = title.ifBlank { sbn.packageName },
                text = body.take(180),
                postedAtMillis = sbn.postTime
            )
        )
    }
}
