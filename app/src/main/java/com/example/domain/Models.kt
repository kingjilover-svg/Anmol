package com.example.domain

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.TagAmber
import com.example.ui.theme.TagCobalt
import com.example.ui.theme.TagEmerald
import com.example.ui.theme.TagRose
import com.example.ui.theme.TagSlate
import com.example.ui.theme.TagViolet

enum class ThemeMode(val label: String) {
    DAYLIGHT("Daylight"),
    DARK("Dark"),
    AUTO("Auto")
}

enum class GeminiModelOption(
    val modelId: String,
    val displayName: String,
    val badge: String,
    val description: String
) {
    FLASH_38(
        modelId = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash",
        badge = "Default",
        description = "Balanced frontier speed & intelligence"
    ),
    FLASH_LITE_35(
        modelId = "gemini-3.5-flash-lite",
        displayName = "Gemini 3.5 Flash-Lite",
        badge = "Ultra Fast",
        description = "Instant lightweight replies & formatting"
    ),
    ARGON_4(
        modelId = "gemini-4-argon",
        displayName = "Gemini 4 Argon",
        badge = "Reasoning",
        description = "Deep coding, math & STEM architecture"
    );

    companion object {
        fun fromId(id: String): GeminiModelOption =
            entries.firstOrNull { it.modelId == id } ?: FLASH_38
    }
}

enum class ImageModelOption(
    val modelId: String,
    val displayName: String,
    val subtitle: String
) {
    NANO_BANANA_21(
        modelId = "gemini-nano-banana-2.1",
        displayName = "Nano Banana 2.1",
        subtitle = "Fast creative synthesis"
    ),
    PRO_IMAGE_3(
        modelId = "gemini-3-pro-image",
        displayName = "Gemini 3 Pro Image",
        subtitle = "Studio-grade typography & detail"
    );

    companion object {
        fun fromId(id: String): ImageModelOption =
            entries.firstOrNull { it.modelId == id } ?: NANO_BANANA_21
    }
}

enum class StudioAspectRatio(val label: String, val ratioValue: Float) {
    SQUARE_1_1("1:1", 1f),
    LANDSCAPE_16_9("16:9", 16f / 9f),
    PORTRAIT_9_16("9:16", 9f / 16f),
    CLASSIC_4_3("4:3", 4f / 3f),
    TALL_3_4("3:4", 3f / 4f)
}

enum class PlanTier(
    val id: String,
    val title: String,
    val price: String,
    val cadence: String,
    val highlights: List<String>
) {
    STARTER(
        id = "starter",
        title = "Starter",
        price = "$0",
        cadence = "Free forever",
        highlights = listOf(
            "Gemini 3.8 Flash & 3.5 Flash-Lite",
            "Offline-first Guest Mode & Cloud Sync",
            "PDF & Code Q&A up to 18 MB",
            "Interactive Diff Viewer & Vault"
        )
    ),
    PRO(
        id = "pro",
        title = "Pro",
        price = "$19",
        cadence = "/ month",
        highlights = listOf(
            "Priority Gemini 4 Argon Reasoning",
            "Image Studio (Nano Banana 2.1 & Pro)",
            "Unlimited Custom Personas & Memory",
            "Multi-page Branded PDF Exports"
        )
    ),
    TEAM(
        id = "team",
        title = "Team",
        price = "$49",
        cadence = "/ seat / mo",
        highlights = listOf(
            "Everything in Pro + Admin Console",
            "Team-wide Role & Usage Governance",
            "Higher Throughput & Context Limits",
            "Dedicated Priority Support"
        )
    );

    companion object {
        fun fromId(id: String): PlanTier =
            entries.firstOrNull { it.id == id } ?: STARTER
    }
}

enum class FolderColor(val key: String, val label: String, val color: Color) {
    COBALT("cobalt", "Cobalt", TagCobalt),
    EMERALD("emerald", "Emerald", TagEmerald),
    AMBER("amber", "Amber", TagAmber),
    ROSE("rose", "Rose", TagRose),
    VIOLET("violet", "Violet", TagViolet),
    SLATE("slate", "Slate", TagSlate);

    companion object {
        fun fromKey(key: String): FolderColor =
            entries.firstOrNull { it.key == key } ?: COBALT
    }
}

enum class MemoryCategory(val label: String) {
    PREFERENCE("Preference"),
    PROJECT("Project"),
    BIO_ROLE("Bio & Role"),
    STYLE("Style");

    companion object {
        fun fromLabel(label: String): MemoryCategory =
            entries.firstOrNull { it.label == label } ?: PREFERENCE
    }
}

enum class MessageReaction(val value: String) {
    NONE("none"),
    LIKED("liked"),
    DISLIKED("disliked");

    companion object {
        fun fromValue(v: String): MessageReaction =
            entries.firstOrNull { it.value == v } ?: NONE
    }
}

enum class BookmarkKind(val value: String) {
    ANSWER("answer"),
    SNIPPET("snippet");

    companion object {
        fun fromValue(v: String): BookmarkKind =
            entries.firstOrNull { it.value == v } ?: ANSWER
    }
}

data class UserProfile(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "Guest User",
    val photoUrl: String? = null,
    val role: String = "user",
    val planTier: String = "starter",
    val lastActiveConversationId: String? = null,
    val messageCount: Long = 0L,
    val imageCount: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val isAdmin: Boolean get() = role == "admin"
}

data class Folder(
    val id: String,
    val name: String,
    val colorKey: String = FolderColor.COBALT.key,
    val sortOrder: Int = 0,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class Conversation(
    val id: String,
    val title: String,
    val folderId: String? = null,
    val pinned: Boolean = false,
    val modelId: String = GeminiModelOption.FLASH_38.modelId,
    val personaId: String = BuiltInPersonas.ASSISTANT_ID,
    val lastMessagePreview: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: String, // "user" or "model"
    val content: String,
    val attachments: List<String> = emptyList(),
    val reaction: MessageReaction = MessageReaction.NONE,
    val followUps: List<String> = emptyList(),
    val partialTimeout: Boolean = false,
    val sequenceIndex: Int = 0,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class Persona(
    val id: String,
    val name: String,
    val tagline: String,
    val systemInstruction: String,
    val builtIn: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class MemoryEntry(
    val id: String,
    val category: MemoryCategory,
    val content: String,
    val enabled: Boolean = true,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class BookmarkSnippet(
    val id: String,
    val kind: BookmarkKind,
    val title: String,
    val content: String,
    val language: String? = null,
    val sourceConversationId: String? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class ImageCreation(
    val id: String,
    val prompt: String,
    val modelId: String,
    val aspectRatio: String,
    val isEdit: Boolean,
    val localImagePath: String,
    val createdAtMillis: Long
)

data class PendingAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isImage: Boolean,
    val isPdf: Boolean,
    val textContent: String? = null,
    val bytes: ByteArray? = null,
    val previewUriString: String? = null
)

data class StarterTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    val prompt: String,
    val personaId: String
)

object BuiltInPersonas {
    const val ASSISTANT_ID = "preset_unknown_assistant"
    const val ARCHITECT_ID = "preset_code_architect"
    const val EDITOR_ID = "preset_concise_editor"
    const val COACH_ID = "preset_socratic_coach"
    const val STRATEGIST_ID = "preset_product_strategist"

    val presets: List<Persona> = listOf(
        Persona(
            id = ASSISTANT_ID,
            name = "UNKNOWN Assistant",
            tagline = "Balanced reasoning, clean formatting & precision",
            systemInstruction = "You are UNKNOWN AI, a calm, accurate, and thoughtful AI assistant. Structure answers cleanly with clear headings, concise prose, and runnable fenced code blocks when helpful.",
            builtIn = true
        ),
        Persona(
            id = ARCHITECT_ID,
            name = "Code Architect",
            tagline = "Systems design, Kotlin/Compose, diffs & edge cases",
            systemInstruction = "You are a Staff Software Architect. Prioritize modular architecture, concurrency safety, explicit error handling, and show unified diffs or well-typed code blocks with clear rationale.",
            builtIn = true
        ),
        Persona(
            id = EDITOR_ID,
            name = "Concise Editor",
            tagline = "Sharp prose, zero fluff, executive clarity",
            systemInstruction = "You are a rigorous Senior Editor. Rewrite or answer with maximum clarity, active voice, and zero filler. Lead with the core conclusion followed by crisp bullet points.",
            builtIn = true
        ),
        Persona(
            id = COACH_ID,
            name = "Socratic Coach",
            tagline = "Guided inquiry to sharpen thinking & mastery",
            systemInstruction = "You are a Socratic Coach. Guide the user toward deep understanding by clarifying assumptions, giving mental models, and asking targeted follow-up questions alongside helpful explanations.",
            builtIn = true
        ),
        Persona(
            id = STRATEGIST_ID,
            name = "Product Strategist",
            tagline = "Trade-offs, roadmaps, UX positioning & specs",
            systemInstruction = "You are a Principal Product Strategist. Frame problems around user outcomes, measurable metrics, prioritized trade-offs, and crisp execution milestones.",
            builtIn = true
        )
    )

    val starterTemplates: List<StarterTemplate> = listOf(
        StarterTemplate(
            id = "tpl_arch",
            title = "Review Architecture",
            subtitle = "Audit concurrency, state & edge cases",
            prompt = "Review this Kotlin StateFlow architecture for race conditions and lifecycle leaks, and suggest a unified diff fix:",
            personaId = ARCHITECT_ID
        ),
        StarterTemplate(
            id = "tpl_brief",
            title = "Executive Brief",
            subtitle = "Distill dense specs into key decisions",
            prompt = "Turn the following product notes into a crisp 1-page executive brief with risks, owners, and next steps:",
            personaId = EDITOR_ID
        ),
        StarterTemplate(
            id = "tpl_learn",
            title = "Socratic Deep Dive",
            subtitle = "Master a complex concept step by step",
            prompt = "Teach me how Myers diffing works using intuition first, then walk me through a concrete example and test my understanding.",
            personaId = COACH_ID
        ),
        StarterTemplate(
            id = "tpl_launch",
            title = "Launch Strategy",
            subtitle = "Positioning, pricing & rollout plan",
            prompt = "Help me design a 30-day launch plan for an offline-first developer productivity app, including positioning and success metrics.",
            personaId = STRATEGIST_ID
        )
    )
}
