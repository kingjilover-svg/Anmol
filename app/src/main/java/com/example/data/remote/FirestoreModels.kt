package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: String = "user",
    val planTier: String = "starter",
    val lastActiveConversationId: String? = null,
    val messageCount: Long = 0L,
    val imageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreFolder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorKey: String = "cobalt",
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreConversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val folderId: String? = null,
    val pinned: Boolean = false,
    val modelId: String = "gemini-3.8-flash",
    val personaId: String = "preset_unknown_assistant",
    val lastMessagePreview: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val content: String = "",
    val attachments: List<String> = emptyList(),
    val reaction: String = "none",
    val followUps: List<String> = emptyList(),
    val partialTimeout: Boolean = false,
    val sequenceIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestorePersona(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val tagline: String = "",
    val systemInstruction: String = "",
    val builtIn: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreMemoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "Preference",
    val content: String = "",
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreBookmarkSnippet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val kind: String = "answer",
    val title: String = "",
    val content: String = "",
    val language: String? = null,
    val sourceConversationId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

internal fun Map<String, Any?>.nonNullMap(): Map<String, Any> {
    @Suppress("UNCHECKED_CAST")
    return filterValues { it != null } as Map<String, Any>
}

internal fun serverTimestampsForCreate(): Map<String, Any> = mapOf(
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)
