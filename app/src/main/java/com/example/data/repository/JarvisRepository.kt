package com.example.data.repository

import android.content.Context
import com.example.R
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
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JarvisRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Jarvis VIP cloud data.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun <T> Flow<T>.guardFirestoreErrors(
        uid: String,
        operationType: OperationType,
        path: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw (firestoreError ?: error)
    }

    fun observeProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session mismatch" }
        val docRef = userDoc(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .guardFirestoreErrors(uid, OperationType.GET, docRef.path)
        )
    }

    fun observeTasks(userId: String): Flow<List<AssistantTask>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session mismatch" }
        val col = userDoc(uid).collection("tasks")
        emitAll(
            col.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(AssistantTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .guardFirestoreErrors(uid, OperationType.LIST, col.path)
        )
    }

    fun observeMemories(userId: String): Flow<List<UserMemory>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session mismatch" }
        val col = userDoc(uid).collection("memories")
        emitAll(
            col.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserMemory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .guardFirestoreErrors(uid, OperationType.LIST, col.path)
        )
    }

    fun observeMessages(userId: String): Flow<List<ChatMessageRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session mismatch" }
        val col = userDoc(uid).collection("messages")
        emitAll(
            col.orderBy("createdAt", Query.Direction.ASCENDING)
                .limitToLast(60)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessageRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .guardFirestoreErrors(uid, OperationType.LIST, col.path)
        )
    }

    fun observeSocialDrafts(userId: String): Flow<List<SocialDraft>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Session mismatch" }
        val col = userDoc(uid).collection("socialDrafts")
        emitAll(
            col.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SocialDraft::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .guardFirestoreErrors(uid, OperationType.LIST, col.path)
        )
    }

    suspend fun ensureUserProfile(displayName: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            path = ref.path
            val existing = ref.get().await()
            if (!existing.exists()) {
                val cleanName = displayName.trim().take(80).ifBlank { "VIP Commander" }
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "preferredLanguage" to PreferredLanguage.BILINGUAL.name,
                    "voicePitch" to 1.08,
                    "voiceRate" to 1.0,
                    "wakeWordEnabled" to false,
                    "automationConfirmed" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfilePreferences(
        preferredLanguage: PreferredLanguage? = null,
        voicePitch: Double? = null,
        voiceRate: Double? = null,
        wakeWordEnabled: Boolean? = null,
        automationConfirmed: Boolean? = null,
        displayName: String? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            path = ref.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            preferredLanguage?.let { updates["preferredLanguage"] = it.name }
            voicePitch?.let { updates["voicePitch"] = it.coerceIn(0.5, 2.0) }
            voiceRate?.let { updates["voiceRate"] = it.coerceIn(0.5, 2.0) }
            wakeWordEnabled?.let { updates["wakeWordEnabled"] = it }
            automationConfirmed?.let { updates["automationConfirmed"] = it }
            displayName?.trim()?.take(80)?.takeIf { it.isNotEmpty() }?.let {
                updates["displayName"] = it
            }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addTask(
        title: String,
        category: TaskCategory,
        dueLabel: String? = null,
        notes: String? = null
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(160)
            require(cleanTitle.isNotEmpty()) { "Task title cannot be empty" }
            val ref = userDoc(uid).collection("tasks").document()
            path = ref.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "notes" to notes?.trim()?.take(1000)?.takeIf { it.isNotEmpty() },
                "category" to category.name,
                "dueLabel" to dueLabel?.trim()?.take(80)?.takeIf { it.isNotEmpty() },
                "completed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(taskId: String, completed: Boolean): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "Task ID required" }
            val ref = userDoc(uid).collection("tasks").document(taskId)
            path = ref.path
            ref.update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank()) { "Task ID required" }
            val ref = userDoc(uid).collection("tasks").document(taskId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addMemory(
        memoryKey: String,
        memoryValue: String,
        category: MemoryCategory = MemoryCategory.PREFERENCE
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memories"
        return try {
            val uid = requireUserId()
            val cleanKey = memoryKey.trim().take(80)
            val cleanVal = memoryValue.trim().take(500)
            require(cleanKey.isNotEmpty() && cleanVal.isNotEmpty()) { "Memory key and value required" }
            val ref = userDoc(uid).collection("memories").document()
            path = ref.path
            val payload = mapOf(
                "userId" to uid,
                "memoryKey" to cleanKey,
                "memoryValue" to cleanVal,
                "category" to category.name,
                "approved" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/memories/$memoryId"
        return try {
            val uid = requireUserId()
            require(memoryId.isNotBlank())
            val ref = userDoc(uid).collection("memories").document(memoryId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        role: String,
        content: String,
        languageTag: String = "bi",
        actionSummary: String? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/messages"
        return try {
            val uid = requireUserId()
            val cleanRole = if (role in setOf("USER", "ASSISTANT", "SYSTEM")) role else "ASSISTANT"
            val cleanContent = content.trim().take(4000)
            require(cleanContent.isNotEmpty()) { "Message content cannot be blank" }
            val cleanLang = languageTag.trim().take(16).ifBlank { "bi" }
            val ref = userDoc(uid).collection("messages").document()
            path = ref.path
            val payload = mapOf(
                "userId" to uid,
                "role" to cleanRole,
                "content" to cleanContent,
                "languageTag" to cleanLang,
                "actionSummary" to actionSummary?.trim()?.take(500)?.takeIf { it.isNotEmpty() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            ref.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun clearConversation(messageIds: List<String>): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/messages"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("messages")
            path = col.path
            val batch = db.batch()
            messageIds.filter { it.isNotBlank() }.take(200).forEach { id ->
                batch.delete(col.document(id))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createSocialDraft(
        platform: SocialPlatform,
        caption: String,
        targetLabel: String? = null,
        hasPhoto: Boolean = false
    ): Result<String> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/socialDrafts"
        return try {
            val uid = requireUserId()
            val cleanCaption = caption.trim().take(2000)
            require(cleanCaption.isNotEmpty()) { "Caption cannot be empty" }
            val ref = userDoc(uid).collection("socialDrafts").document()
            path = ref.path
            val payload = mapOf(
                "userId" to uid,
                "platform" to platform.name,
                "caption" to cleanCaption,
                "targetLabel" to targetLabel?.trim()?.take(120)?.takeIf { it.isNotEmpty() },
                "hasPhoto" to hasPhoto,
                "status" to DraftStatus.DRAFT.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markSocialDraftStatus(draftId: String, newStatus: DraftStatus): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/socialDrafts/$draftId"
        return try {
            val uid = requireUserId()
            require(draftId.isNotBlank())
            val ref = userDoc(uid).collection("socialDrafts").document(draftId)
            path = ref.path
            ref.update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
