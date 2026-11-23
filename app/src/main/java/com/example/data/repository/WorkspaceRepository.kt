package com.example.data.repository

import com.example.data.local.GuestWorkspaceDao
import com.example.data.local.LocalBookmarkEntity
import com.example.data.local.LocalConversationEntity
import com.example.data.local.LocalFolderEntity
import com.example.data.local.LocalMemoryEntity
import com.example.data.local.LocalMessageEntity
import com.example.data.local.LocalPersonaEntity
import com.example.data.local.StudioImageDao
import com.example.data.local.StudioImageEntity
import com.example.data.preferences.SettingsDataStore
import com.example.data.remote.FirestoreWorkspaceSource
import com.example.domain.BookmarkKind
import com.example.domain.BookmarkSnippet
import com.example.domain.BuiltInPersonas
import com.example.domain.ChatMessage
import com.example.domain.Conversation
import com.example.domain.Folder
import com.example.domain.ImageCreation
import com.example.domain.MemoryCategory
import com.example.domain.MemoryEntry
import com.example.domain.MessageReaction
import com.example.domain.Persona
import com.example.domain.UserProfile
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private const val LIST_SEPARATOR = "\u001F"

class WorkspaceRepository(
    private val guestDao: GuestWorkspaceDao,
    private val studioImageDao: StudioImageDao,
    private val remote: FirestoreWorkspaceSource,
    private val settings: SettingsDataStore
) {
    // --- User Profile ---
    suspend fun ensureRemoteProfile(user: FirebaseUser): Result<UserProfile> =
        remote.ensureUserProfile(user)

    fun observeUserProfile(uid: String?): Flow<UserProfile?> =
        if (uid == null) {
            settings.preferencesFlow.map { prefs ->
                UserProfile(
                    userId = "guest",
                    email = "",
                    displayName = "Guest Workspace",
                    role = "user",
                    planTier = prefs.previewPlanTier.id,
                    lastActiveConversationId = prefs.lastGuestConversationId,
                    messageCount = prefs.guestMessageCount,
                    imageCount = prefs.guestImageCount
                )
            }
        } else {
            remote.observeUserProfile(uid)
        }

    suspend fun updateLastActiveConversation(uid: String?, conversationId: String) {
        if (uid == null) {
            settings.setLastGuestConversationId(conversationId)
        } else {
            remote.updateLastActiveConversation(uid, conversationId)
        }
    }

    suspend fun updatePlanTier(uid: String?, planTierId: String): Result<Unit> {
        return if (uid == null) {
            settings.setPreviewPlanTier(com.example.domain.PlanTier.fromId(planTierId))
            Result.success(Unit)
        } else {
            remote.updateUserPlanTier(uid, planTierId)
        }
    }

    suspend fun incrementUsage(uid: String?, isImage: Boolean) {
        if (uid == null) {
            if (isImage) settings.incrementGuestImageCount()
            else settings.incrementGuestMessageCount()
        } else {
            remote.incrementUsageCounter(uid, isImage)
        }
    }

    // --- Folders ---
    fun observeFolders(uid: String?): Flow<List<Folder>> =
        if (uid == null) {
            guestDao.observeFolders().map { list -> list.map { it.toDomain() } }
        } else {
            remote.observeFolders(uid)
        }

    suspend fun saveFolder(uid: String?, folder: Folder, isNew: Boolean): Result<Unit> {
        return if (uid == null) {
            val now = System.currentTimeMillis()
            val entity = LocalFolderEntity(
                id = folder.id,
                name = folder.name.trim().take(60),
                colorKey = folder.colorKey,
                sortOrder = folder.sortOrder,
                createdAtMillis = if (isNew) now else folder.createdAtMillis.takeIf { it > 0 } ?: now,
                updatedAtMillis = now
            )
            guestDao.upsertFolder(entity)
            Result.success(Unit)
        } else {
            remote.upsertFolder(uid, folder, isNew)
        }
    }

    suspend fun deleteFolder(uid: String?, folderId: String): Result<Unit> {
        return if (uid == null) {
            guestDao.clearFolderFromConversations(folderId)
            guestDao.deleteFolder(folderId)
            Result.success(Unit)
        } else {
            remote.deleteFolder(uid, folderId)
        }
    }

    // --- Conversations ---
    fun observeConversations(uid: String?): Flow<List<Conversation>> =
        if (uid == null) {
            guestDao.observeConversations().map { list -> list.map { it.toDomain() } }
        } else {
            remote.observeConversations(uid)
        }

    suspend fun createConversation(uid: String?, conversation: Conversation): Result<Unit> {
        return if (uid == null) {
            val now = System.currentTimeMillis()
            guestDao.upsertConversation(
                LocalConversationEntity(
                    id = conversation.id,
                    title = conversation.title,
                    folderId = conversation.folderId,
                    pinned = conversation.pinned,
                    modelId = conversation.modelId,
                    personaId = conversation.personaId,
                    lastMessagePreview = conversation.lastMessagePreview,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            Result.success(Unit)
        } else {
            remote.createConversation(uid, conversation)
        }
    }

    suspend fun updateConversation(uid: String?, conversation: Conversation): Result<Unit> {
        return if (uid == null) {
            val now = System.currentTimeMillis()
            guestDao.upsertConversation(
                LocalConversationEntity(
                    id = conversation.id,
                    title = conversation.title,
                    folderId = conversation.folderId,
                    pinned = conversation.pinned,
                    modelId = conversation.modelId,
                    personaId = conversation.personaId,
                    lastMessagePreview = conversation.lastMessagePreview,
                    createdAtMillis = conversation.createdAtMillis.takeIf { it > 0 } ?: now,
                    updatedAtMillis = now
                )
            )
            Result.success(Unit)
        } else {
            remote.updateConversation(uid, conversation)
        }
    }

    suspend fun getConversationOnce(uid: String?, conversationId: String): Conversation? {
        return if (uid == null) {
            guestDao.getConversationOnce(conversationId)?.toDomain()
        } else {
            null
        }
    }

    suspend fun updateConversationPreview(
        uid: String?,
        conversationId: String,
        previewText: String,
        fallbackConversation: Conversation? = null
    ): Result<Unit> {
        val cleanPreview = previewText.replace("\n", " ").take(140)
        return if (uid == null) {
            guestDao.updateConversationPreview(
                id = conversationId,
                preview = cleanPreview,
                updatedAtMillis = System.currentTimeMillis()
            )
            Result.success(Unit)
        } else if (fallbackConversation != null) {
            remote.updateConversation(uid, fallbackConversation.copy(lastMessagePreview = cleanPreview))
        } else {
            Result.success(Unit)
        }
    }

    suspend fun deleteConversation(uid: String?, conversationId: String): Result<Unit> {
        return if (uid == null) {
            guestDao.deleteMessagesForConversation(conversationId)
            guestDao.deleteConversation(conversationId)
            Result.success(Unit)
        } else {
            remote.deleteConversation(uid, conversationId)
        }
    }

    // --- Messages ---
    fun observeMessages(uid: String?, conversationId: String?): Flow<List<ChatMessage>> {
        if (conversationId.isNullOrBlank()) return flowOf(emptyList())
        return if (uid == null) {
            guestDao.observeMessages(conversationId).map { list -> list.map { it.toDomain() } }
        } else {
            remote.observeMessages(uid, conversationId)
        }
    }

    suspend fun getMessagesOnce(uid: String?, conversationId: String): List<ChatMessage> {
        return if (uid == null) {
            guestDao.getMessagesOnce(conversationId).map { it.toDomain() }
        } else {
            emptyList()
        }
    }

    suspend fun createMessage(uid: String?, message: ChatMessage): Result<Unit> {
        return if (uid == null) {
            val now = System.currentTimeMillis()
            guestDao.upsertMessage(message.toEntity(now, now))
            Result.success(Unit)
        } else {
            remote.createMessage(uid, message)
        }
    }

    suspend fun updateMessage(uid: String?, message: ChatMessage): Result<Unit> {
        return if (uid == null) {
            val now = System.currentTimeMillis()
            val created = message.createdAtMillis.takeIf { it > 0 } ?: now
            guestDao.upsertMessage(message.toEntity(created, now))
            Result.success(Unit)
        } else {
            remote.updateMessage(uid, message)
        }
    }

    suspend fun deleteMessagesFromSequence(
        uid: String?,
        conversationId: String,
        fromSequenceIndex: Int
    ): Result<Unit> {
        return if (uid == null) {
            guestDao.deleteMessagesFromSequence(conversationId, fromSequenceIndex)
            Result.success(Unit)
        } else {
            remote.deleteMessagesFromSequence(uid, conversationId, fromSequenceIndex)
        }
    }

    // --- Personas ---
    fun observeAllPersonas(uid: String?): Flow<List<Persona>> {
        val customFlow = if (uid == null) {
            guestDao.observePersonas().map { list -> list.map { it.toDomain() } }
        } else {
            remote.observePersonas(uid)
        }
        return customFlow.map { custom ->
            BuiltInPersonas.presets + custom.filter { !it.builtIn }
        }
    }

    suspend fun saveCustomPersona(uid: String?, persona: Persona, isNew: Boolean): Result<Unit> {
        return if (uid == null) {
            val now = System.currentTimeMillis()
            guestDao.upsertPersona(
                LocalPersonaEntity(
                    id = persona.id,
                    name = persona.name.trim().take(60),
                    tagline = persona.tagline.trim().take(140),
                    systemInstruction = persona.systemInstruction.trim().take(4000),
                    builtIn = false,
                    createdAtMillis = if (isNew) now else persona.createdAtMillis.takeIf { it > 0 } ?: now,
                    updatedAtMillis = now
                )
            )
            Result.success(Unit)
        } else {
            remote.upsertPersona(uid, persona.copy(builtIn = false), isNew)
        }
    }

    suspend fun deleteCustomPersona(uid: String?, personaId: String): Result<Unit> {
        return if (uid == null) {
            guestDao.deletePersona(personaId)
            Result.success(Unit)
        } else {
            remote.deletePersona(uid, personaId)
        }
    }

    // --- Memories ---
    fun observeMemories(uid: String?): Flow<List<MemoryEntry>> =
        if (uid == null) {
            guestDao.observeMemories().map { list -> list.map { it.toDomain() } }
        } else {
            remote.observeMemories(uid)
        }

    suspend fun saveMemory(uid: String?, memory: MemoryEntry, isNew: Boolean): Result<Unit> {
        return if (uid == null) {
            val now = System.currentTimeMillis()
            guestDao.upsertMemory(
                LocalMemoryEntity(
                    id = memory.id,
                    category = memory.category.label,
                    content = memory.content.trim().take(1000),
                    enabled = memory.enabled,
                    createdAtMillis = if (isNew) now else memory.createdAtMillis.takeIf { it > 0 } ?: now,
                    updatedAtMillis = now
                )
            )
            Result.success(Unit)
        } else {
            remote.upsertMemory(uid, memory, isNew)
        }
    }

    suspend fun deleteMemory(uid: String?, memoryId: String): Result<Unit> {
        return if (uid == null) {
            guestDao.deleteMemory(memoryId)
            Result.success(Unit)
        } else {
            remote.deleteMemory(uid, memoryId)
        }
    }

    // --- Bookmarks & Vault ---
    fun observeBookmarks(uid: String?): Flow<List<BookmarkSnippet>> =
        if (uid == null) {
            guestDao.observeBookmarks().map { list -> list.map { it.toDomain() } }
        } else {
            remote.observeBookmarks(uid)
        }

    suspend fun saveBookmark(uid: String?, bookmark: BookmarkSnippet): Result<Unit> {
        return if (uid == null) {
            val now = System.currentTimeMillis()
            guestDao.upsertBookmark(
                LocalBookmarkEntity(
                    id = bookmark.id,
                    kind = bookmark.kind.value,
                    title = bookmark.title.trim().take(120),
                    content = bookmark.content.take(30_000),
                    language = bookmark.language?.take(40),
                    sourceConversationId = bookmark.sourceConversationId,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            Result.success(Unit)
        } else {
            remote.createBookmark(uid, bookmark)
        }
    }

    suspend fun deleteBookmark(uid: String?, bookmarkId: String): Result<Unit> {
        return if (uid == null) {
            guestDao.deleteBookmark(bookmarkId)
            Result.success(Unit)
        } else {
            remote.deleteBookmark(uid, bookmarkId)
        }
    }

    // --- Studio Images (Local Room Gallery) ---
    fun observeStudioImages(): Flow<List<ImageCreation>> =
        studioImageDao.observeImages().map { list -> list.map { it.toDomain() } }

    suspend fun saveStudioImage(creation: ImageCreation) {
        studioImageDao.insertImage(
            StudioImageEntity(
                id = creation.id,
                prompt = creation.prompt,
                modelId = creation.modelId,
                aspectRatio = creation.aspectRatio,
                isEdit = creation.isEdit,
                localImagePath = creation.localImagePath,
                createdAtMillis = creation.createdAtMillis
            )
        )
    }

    suspend fun deleteStudioImage(id: String) {
        studioImageDao.deleteImage(id)
    }

    // --- Guest-to-Cloud Automatic Migration on Sign-In ---
    suspend fun hasGuestDataToMigrate(): Boolean {
        return guestDao.getAllConversationsOnce().isNotEmpty() ||
            guestDao.getAllFoldersOnce().isNotEmpty() ||
            guestDao.getAllPersonasOnce().isNotEmpty() ||
            guestDao.getAllMemoriesOnce().isNotEmpty() ||
            guestDao.getAllBookmarksOnce().isNotEmpty()
    }

    suspend fun migrateGuestWorkspaceToCloud(uid: String): Result<Int> {
        return try {
            var migratedCount = 0
            guestDao.getAllFoldersOnce().forEach { folder ->
                remote.upsertFolder(uid, folder.toDomain(), isNew = true).getOrThrow()
                migratedCount++
            }
            guestDao.getAllPersonasOnce().forEach { persona ->
                remote.upsertPersona(uid, persona.toDomain(), isNew = true).getOrThrow()
                migratedCount++
            }
            guestDao.getAllMemoriesOnce().forEach { memory ->
                remote.upsertMemory(uid, memory.toDomain(), isNew = true).getOrThrow()
                migratedCount++
            }
            guestDao.getAllBookmarksOnce().forEach { bookmark ->
                remote.createBookmark(uid, bookmark.toDomain()).getOrThrow()
                migratedCount++
            }
            val conversations = guestDao.getAllConversationsOnce()
            conversations.forEach { conv ->
                remote.createConversation(uid, conv.toDomain()).getOrThrow()
                migratedCount++
            }
            guestDao.getAllMessagesOnce().forEach { msg ->
                remote.createMessage(uid, msg.toDomain()).getOrThrow()
                migratedCount++
            }

            // Clear local guest store once safely migrated
            guestDao.clearAllMessages()
            guestDao.clearAllConversations()
            guestDao.clearAllFolders()
            guestDao.clearAllPersonas()
            guestDao.clearAllMemories()
            guestDao.clearAllBookmarks()
            settings.setGuestSessionActive(false)
            Result.success(migratedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Admin Console ---
    fun observeAllUsersForAdmin(adminUid: String): Flow<List<UserProfile>> =
        remote.observeAllUsersForAdmin(adminUid)

    suspend fun adminUpdateUser(targetUserId: String, role: String, planTier: String): Result<Unit> =
        remote.adminUpdateUser(targetUserId, role, planTier)
}

private fun LocalFolderEntity.toDomain() = Folder(
    id = id, name = name, colorKey = colorKey, sortOrder = sortOrder,
    createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis
)

private fun LocalConversationEntity.toDomain() = Conversation(
    id = id, title = title, folderId = folderId, pinned = pinned, modelId = modelId,
    personaId = personaId, lastMessagePreview = lastMessagePreview,
    createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis
)

private fun LocalMessageEntity.toDomain() = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    attachments = if (attachmentsJoined.isBlank()) emptyList() else attachmentsJoined.split(LIST_SEPARATOR),
    reaction = MessageReaction.fromValue(reaction),
    followUps = if (followUpsJoined.isBlank()) emptyList() else followUpsJoined.split(LIST_SEPARATOR),
    partialTimeout = partialTimeout,
    sequenceIndex = sequenceIndex,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis
)

private fun ChatMessage.toEntity(created: Long, updated: Long) = LocalMessageEntity(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    attachmentsJoined = attachments.joinToString(LIST_SEPARATOR),
    reaction = reaction.value,
    followUpsJoined = followUps.joinToString(LIST_SEPARATOR),
    partialTimeout = partialTimeout,
    sequenceIndex = sequenceIndex,
    createdAtMillis = created,
    updatedAtMillis = updated
)

private fun LocalPersonaEntity.toDomain() = Persona(
    id = id, name = name, tagline = tagline, systemInstruction = systemInstruction,
    builtIn = builtIn, createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis
)

private fun LocalMemoryEntity.toDomain() = MemoryEntry(
    id = id, category = MemoryCategory.fromLabel(category), content = content,
    enabled = enabled, createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis
)

private fun LocalBookmarkEntity.toDomain() = BookmarkSnippet(
    id = id, kind = BookmarkKind.fromValue(kind), title = title, content = content,
    language = language, sourceConversationId = sourceConversationId,
    createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis
)

private fun StudioImageEntity.toDomain() = ImageCreation(
    id = id, prompt = prompt, modelId = modelId, aspectRatio = aspectRatio,
    isEdit = isEdit, localImagePath = localImagePath, createdAtMillis = createdAtMillis
)
