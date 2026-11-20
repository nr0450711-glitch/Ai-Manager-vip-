package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PreferredLanguage(val label: String, val bcp47: String) {
    BILINGUAL("Hindi + English", "en-IN"),
    ENGLISH("English", "en-IN"),
    HINDI("हिन्दी (Hindi)", "hi-IN");

    companion object {
        fun fromRaw(raw: String?): PreferredLanguage =
            entries.firstOrNull { it.name == raw } ?: BILINGUAL
    }
}

enum class TaskCategory(val label: String) {
    TASK("Task"),
    REMINDER("Reminder"),
    SCHEDULE("Schedule"),
    BIRTHDAY("Birthday"),
    ALARM("Alarm");

    companion object {
        fun fromRaw(raw: String?): TaskCategory =
            entries.firstOrNull { it.name == raw } ?: TASK
    }
}

enum class MemoryCategory(val label: String) {
    PREFERENCE("Preference"),
    CONTACT("Contact"),
    ROUTINE("Routine"),
    GENERAL("General");

    companion object {
        fun fromRaw(raw: String?): MemoryCategory =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}

enum class SocialPlatform(val label: String) {
    FACEBOOK("Facebook"),
    WHATSAPP("WhatsApp"),
    SMS("SMS");

    companion object {
        fun fromRaw(raw: String?): SocialPlatform =
            entries.firstOrNull { it.name == raw } ?: FACEBOOK
    }
}

enum class DraftStatus {
    DRAFT,
    SHARED,
    CANCELLED;

    companion object {
        fun fromRaw(raw: String?): DraftStatus =
            entries.firstOrNull { it.name == raw } ?: DRAFT
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "VIP User",
    val preferredLanguage: String = PreferredLanguage.BILINGUAL.name,
    val voicePitch: Double = 1.08,
    val voiceRate: Double = 1.0,
    val wakeWordEnabled: Boolean = false,
    val automationConfirmed: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserMemory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val memoryKey: String = "",
    val memoryValue: String = "",
    val category: String = MemoryCategory.PREFERENCE.name,
    val approved: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AssistantTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String? = null,
    val category: String = TaskCategory.TASK.name,
    val dueLabel: String? = null,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessageRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "USER",
    val content: String = "",
    val languageTag: String = "bi",
    val actionSummary: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SocialDraft(
    @DocumentId val id: String = "",
    val userId: String = "",
    val platform: String = SocialPlatform.FACEBOOK.name,
    val caption: String = "",
    val targetLabel: String? = null,
    val hasPhoto: Boolean = false,
    val status: String = DraftStatus.DRAFT.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
