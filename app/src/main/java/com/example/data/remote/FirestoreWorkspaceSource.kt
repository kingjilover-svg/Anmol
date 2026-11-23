package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.domain.BookmarkKind
import com.example.domain.BookmarkSnippet
import com.example.domain.ChatMessage
import com.example.domain.Conversation
import com.example.domain.Folder
import com.example.domain.FolderColor
import com.example.domain.MemoryCategory
import com.example.domain.MemoryEntry
import com.example.domain.MessageReaction
import com.example.domain.Persona
import com.example.domain.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

class FirestoreWorkspaceSource(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun <T> Flow<T>.withFirestoreLogging(operation: OperationType, path: String, uid: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, operation, path)
            }
            throw error
        }

    suspend fun ensureUserProfile(firebaseUser: FirebaseUser): Result<UserProfile> {
        val uid = firebaseUser.uid
        val path = "users/$uid"
        return try {
            val snap = userDoc(uid).get().await()
            val isAdminMarker = try {
                db.collection("admins").document(uid).get().await().exists()
            } catch (_: Exception) {
                false
            }
            val bootstrappedAdmin = firebaseUser.isEmailVerified &&
                firebaseUser.email.equals("u4115518@gmail.com", ignoreCase = true)

            if (!snap.exists()) {
                val role = if (isAdminMarker || bootstrappedAdmin) "admin" else "user"
                val displayName = (firebaseUser.displayName?.takeIf { it.isNotBlank() }
                    ?: firebaseUser.email?.substringBefore("@")
                    ?: "UNKNOWN User").take(100)
                val payload = mapOf(
                    "userId" to uid,
                    "email" to (firebaseUser.email ?: "").take(200),
                    "displayName" to displayName,
                    "photoUrl" to firebaseUser.photoUrl?.toString()?.take(500),
                    "role" to role,
                    "planTier" to "starter",
                    "messageCount" to 0L,
                    "imageCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).nonNullMap()
                userDoc(uid).set(payload).await()
                if (bootstrappedAdmin) {
                    runCatching {
                        db.collection("admins").document(uid).set(
                            mapOf(
                                "uid" to uid,
                                "email" to (firebaseUser.email ?: "u4115518@gmail.com"),
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }
            val fresh = userDoc(uid).get().await()
                .toObject(FirestoreUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val domain = fresh?.toDomain() ?: UserProfile(
                userId = uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "UNKNOWN User",
                photoUrl = firebaseUser.photoUrl?.toString(),
                role = if (isAdminMarker || bootstrappedAdmin) "admin" else "user"
            )
            Result.success(domain)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { snap ->
                    snap.toObject(FirestoreUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toDomain()
                }
                .withFirestoreLogging(OperationType.GET, path, uid)
        )
    }

    suspend fun updateLastActiveConversation(uid: String, conversationId: String): Result<Unit> {
        val path = "users/$uid"
        return try {
            require(requireUserId() == uid)
            userDoc(uid).update(
                mapOf(
                    "lastActiveConversationId" to conversationId.take(128),
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

    suspend fun updateUserPlanTier(uid: String, planTier: String): Result<Unit> {
        val path = "users/$uid"
        return try {
            require(requireUserId() == uid)
            userDoc(uid).update(
                mapOf(
                    "planTier" to planTier,
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

    suspend fun incrementUsageCounter(uid: String, isImage: Boolean): Result<Unit> {
        val path = "users/$uid"
        return try {
            require(requireUserId() == uid)
            val field = if (isImage) "imageCount" else "messageCount"
            userDoc(uid).update(
                mapOf(
                    field to FieldValue.increment(1L),
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

    // --- Admin Console ---
    fun observeAllUsersForAdmin(adminUid: String): Flow<List<UserProfile>> = flow {
        emitAll(
            db.collection("users")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreUserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                }
                .withFirestoreLogging(OperationType.LIST, "users", adminUid)
        )
    }

    suspend fun adminUpdateUser(targetUserId: String, role: String, planTier: String): Result<Unit> {
        val path = "users/$targetUserId"
        return try {
            requireUserId()
            require(targetUserId.isNotBlank())
            userDoc(targetUserId).update(
                mapOf(
                    "role" to role,
                    "planTier" to planTier,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (role == "admin") {
                val targetSnap = userDoc(targetUserId).get().await()
                val email = targetSnap.getString("email").orEmpty().ifBlank { "admin@unknown.ai" }
                db.collection("admins").document(targetUserId).set(
                    mapOf(
                        "uid" to targetUserId,
                        "email" to email,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                runCatching { db.collection("admins").document(targetUserId).delete().await() }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Folders ---
    fun observeFolders(uid: String): Flow<List<Folder>> = flow {
        val path = "users/$uid/folders"
        emitAll(
            userDoc(uid).collection("folders")
                .orderBy("sortOrder", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreFolder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                }
                .withFirestoreLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun upsertFolder(uid: String, folder: Folder, isNew: Boolean): Result<Unit> {
        val path = "users/$uid/folders/${folder.id}"
        return try {
            require(requireUserId() == uid)
            val ref = userDoc(uid).collection("folders").document(folder.id)
            if (isNew) {
                val payload = mapOf(
                    "userId" to uid,
                    "name" to folder.name.trim().take(60),
                    "colorKey" to FolderColor.fromKey(folder.colorKey).key,
                    "sortOrder" to folder.sortOrder.toLong().coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            } else {
                ref.update(
                    mapOf(
                        "name" to folder.name.trim().take(60),
                        "colorKey" to FolderColor.fromKey(folder.colorKey).key,
                        "sortOrder" to folder.sortOrder.toLong().coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(uid: String, folderId: String): Result<Unit> {
        val path = "users/$uid/folders/$folderId"
        return try {
            require(requireUserId() == uid)
            userDoc(uid).collection("folders").document(folderId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Conversations ---
    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val path = "users/$uid/conversations"
        emitAll(
            userDoc(uid).collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreConversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                        .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAtMillis })
                }
                .withFirestoreLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun createConversation(uid: String, conversation: Conversation): Result<Unit> {
        val path = "users/$uid/conversations/${conversation.id}"
        return try {
            require(requireUserId() == uid)
            val payload = mapOf(
                "userId" to uid,
                "title" to conversation.title.ifBlank { "New Chat" }.take(120),
                "folderId" to conversation.folderId?.take(128),
                "pinned" to conversation.pinned,
                "modelId" to conversation.modelId.take(64),
                "personaId" to conversation.personaId.take(128),
                "lastMessagePreview" to conversation.lastMessagePreview.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).nonNullMap()
            userDoc(uid).collection("conversations").document(conversation.id).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateConversation(uid: String, conversation: Conversation): Result<Unit> {
        val path = "users/$uid/conversations/${conversation.id}"
        return try {
            require(requireUserId() == uid)
            val updates = mutableMapOf<String, Any>(
                "title" to conversation.title.ifBlank { "New Chat" }.take(120),
                "pinned" to conversation.pinned,
                "modelId" to conversation.modelId.take(64),
                "personaId" to conversation.personaId.take(128),
                "lastMessagePreview" to conversation.lastMessagePreview.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (conversation.folderId != null) {
                updates["folderId"] = conversation.folderId.take(128)
            } else {
                updates["folderId"] = FieldValue.delete()
            }
            userDoc(uid).collection("conversations").document(conversation.id).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(uid: String, conversationId: String): Result<Unit> {
        val path = "users/$uid/conversations/$conversationId"
        return try {
            require(requireUserId() == uid)
            val msgColl = userDoc(uid).collection("conversations").document(conversationId).collection("messages")
            val msgs = msgColl.get().await()
            for (doc in msgs.documents) {
                doc.reference.delete().await()
            }
            userDoc(uid).collection("conversations").document(conversationId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Messages ---
    fun observeMessages(uid: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val path = "users/$uid/conversations/$conversationId/messages"
        emitAll(
            userDoc(uid).collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("sequenceIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                }
                .withFirestoreLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun createMessage(uid: String, message: ChatMessage): Result<Unit> {
        val path = "users/$uid/conversations/${message.conversationId}/messages/${message.id}"
        return try {
            require(requireUserId() == uid)
            val payload = mapOf(
                "userId" to uid,
                "conversationId" to message.conversationId,
                "role" to message.role,
                "content" to message.content.take(100_000),
                "attachments" to message.attachments.take(10),
                "reaction" to message.reaction.value,
                "followUps" to message.followUps.take(4),
                "partialTimeout" to message.partialTimeout,
                "sequenceIndex" to message.sequenceIndex.toLong().coerceAtLeast(0L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDoc(uid).collection("conversations").document(message.conversationId)
                .collection("messages").document(message.id).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMessage(uid: String, message: ChatMessage): Result<Unit> {
        val path = "users/$uid/conversations/${message.conversationId}/messages/${message.id}"
        return try {
            require(requireUserId() == uid)
            userDoc(uid).collection("conversations").document(message.conversationId)
                .collection("messages").document(message.id)
                .update(
                    mapOf(
                        "content" to message.content.take(100_000),
                        "attachments" to message.attachments.take(10),
                        "reaction" to message.reaction.value,
                        "followUps" to message.followUps.take(4),
                        "partialTimeout" to message.partialTimeout,
                        "sequenceIndex" to message.sequenceIndex.toLong().coerceAtLeast(0L),
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

    suspend fun deleteMessagesFromSequence(
        uid: String,
        conversationId: String,
        fromSequenceIndex: Int
    ): Result<Unit> {
        val path = "users/$uid/conversations/$conversationId/messages"
        return try {
            require(requireUserId() == uid)
            val snap = userDoc(uid).collection("conversations").document(conversationId)
                .collection("messages")
                .whereGreaterThanOrEqualTo("sequenceIndex", fromSequenceIndex.toLong())
                .get().await()
            for (doc in snap.documents) {
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Personas ---
    fun observePersonas(uid: String): Flow<List<Persona>> = flow {
        val path = "users/$uid/personas"
        emitAll(
            userDoc(uid).collection("personas")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestorePersona::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                }
                .withFirestoreLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun upsertPersona(uid: String, persona: Persona, isNew: Boolean): Result<Unit> {
        val path = "users/$uid/personas/${persona.id}"
        return try {
            require(requireUserId() == uid)
            val ref = userDoc(uid).collection("personas").document(persona.id)
            if (isNew) {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "name" to persona.name.trim().take(60),
                        "tagline" to persona.tagline.trim().take(140),
                        "systemInstruction" to persona.systemInstruction.trim().take(4000),
                        "builtIn" to persona.builtIn,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "name" to persona.name.trim().take(60),
                        "tagline" to persona.tagline.trim().take(140),
                        "systemInstruction" to persona.systemInstruction.trim().take(4000),
                        "builtIn" to persona.builtIn,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePersona(uid: String, personaId: String): Result<Unit> {
        val path = "users/$uid/personas/$personaId"
        return try {
            require(requireUserId() == uid)
            userDoc(uid).collection("personas").document(personaId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Memories ---
    fun observeMemories(uid: String): Flow<List<MemoryEntry>> = flow {
        val path = "users/$uid/memories"
        emitAll(
            userDoc(uid).collection("memories")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreMemoryEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                }
                .withFirestoreLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun upsertMemory(uid: String, memory: MemoryEntry, isNew: Boolean): Result<Unit> {
        val path = "users/$uid/memories/${memory.id}"
        return try {
            require(requireUserId() == uid)
            val ref = userDoc(uid).collection("memories").document(memory.id)
            if (isNew) {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "category" to memory.category.label,
                        "content" to memory.content.trim().take(1000),
                        "enabled" to memory.enabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "category" to memory.category.label,
                        "content" to memory.content.trim().take(1000),
                        "enabled" to memory.enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(uid: String, memoryId: String): Result<Unit> {
        val path = "users/$uid/memories/$memoryId"
        return try {
            require(requireUserId() == uid)
            userDoc(uid).collection("memories").document(memoryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Bookmarks & Vault ---
    fun observeBookmarks(uid: String): Flow<List<BookmarkSnippet>> = flow {
        val path = "users/$uid/bookmarks"
        emitAll(
            userDoc(uid).collection("bookmarks")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreBookmarkSnippet::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.toDomain() }
                }
                .withFirestoreLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun createBookmark(uid: String, bookmark: BookmarkSnippet): Result<Unit> {
        val path = "users/$uid/bookmarks/${bookmark.id}"
        return try {
            require(requireUserId() == uid)
            val payload = mapOf(
                "userId" to uid,
                "kind" to bookmark.kind.value,
                "title" to bookmark.title.trim().take(120),
                "content" to bookmark.content.take(30_000),
                "language" to bookmark.language?.take(40),
                "sourceConversationId" to bookmark.sourceConversationId?.take(128),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).nonNullMap()
            userDoc(uid).collection("bookmarks").document(bookmark.id).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBookmark(uid: String, bookmarkId: String): Result<Unit> {
        val path = "users/$uid/bookmarks/$bookmarkId"
        return try {
            require(requireUserId() == uid)
            userDoc(uid).collection("bookmarks").document(bookmarkId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}

private fun FirestoreUserProfile.toDomain() = UserProfile(
    userId = userId.ifBlank { id },
    email = email,
    displayName = displayName.ifBlank { "UNKNOWN User" },
    photoUrl = photoUrl,
    role = role,
    planTier = planTier,
    lastActiveConversationId = lastActiveConversationId,
    messageCount = messageCount,
    imageCount = imageCount,
    createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    updatedAtMillis = updatedAt?.toDate()?.time ?: 0L
)

private fun FirestoreFolder.toDomain() = Folder(
    id = id,
    name = name,
    colorKey = colorKey,
    sortOrder = sortOrder.toInt(),
    createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    updatedAtMillis = updatedAt?.toDate()?.time ?: 0L
)

private fun FirestoreConversation.toDomain() = Conversation(
    id = id,
    title = title,
    folderId = folderId,
    pinned = pinned,
    modelId = modelId,
    personaId = personaId,
    lastMessagePreview = lastMessagePreview,
    createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    updatedAtMillis = updatedAt?.toDate()?.time ?: 0L
)

private fun FirestoreChatMessage.toDomain() = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    attachments = attachments,
    reaction = MessageReaction.fromValue(reaction),
    followUps = followUps,
    partialTimeout = partialTimeout,
    sequenceIndex = sequenceIndex.toInt(),
    createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    updatedAtMillis = updatedAt?.toDate()?.time ?: 0L
)

private fun FirestorePersona.toDomain() = Persona(
    id = id,
    name = name,
    tagline = tagline,
    systemInstruction = systemInstruction,
    builtIn = builtIn,
    createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    updatedAtMillis = updatedAt?.toDate()?.time ?: 0L
)

private fun FirestoreMemoryEntry.toDomain() = MemoryEntry(
    id = id,
    category = MemoryCategory.fromLabel(category),
    content = content,
    enabled = enabled,
    createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    updatedAtMillis = updatedAt?.toDate()?.time ?: 0L
)

private fun FirestoreBookmarkSnippet.toDomain() = BookmarkSnippet(
    id = id,
    kind = BookmarkKind.fromValue(kind),
    title = title,
    content = content,
    language = language,
    sourceConversationId = sourceConversationId,
    createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    updatedAtMillis = updatedAt?.toDate()?.time ?: 0L
)
