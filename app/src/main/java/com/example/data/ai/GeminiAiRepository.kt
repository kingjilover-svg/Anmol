package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.domain.ChatMessage
import com.example.domain.MemoryEntry
import com.example.domain.PendingAttachment
import com.example.domain.Persona
import com.example.domain.StudioAspectRatio
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAiRepository"

sealed interface StreamEvent {
    data class Chunk(val accumulatedText: String) : StreamEvent
    data class Completed(val fullText: String) : StreamEvent
    data class TimedOut(val partialText: String) : StreamEvent
}

class GeminiAiRepository {

    private val enhancerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = content {
                text(
                    "You are a prompt engineering specialist. Rewrite the user's draft prompt to be clearer, more specific, and better structured while preserving their original intent. Output ONLY the improved prompt text with no quotes or preamble."
                )
            }
        )
    }

    private val followUpSchema = Schema.obj(
        properties = mapOf(
            "suggestions" to Schema.array(
                items = Schema.string(description = "A concise follow-up prompt (3 to 7 words)"),
                description = "Exactly 3 relevant next questions or actions"
            )
        )
    )

    private val followUpModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = followUpSchema
            }
        )
    }

    fun streamChatReply(
        modelId: String,
        persona: Persona,
        memories: List<MemoryEntry>,
        globalMemoryEnabled: Boolean,
        history: List<ChatMessage>,
        userPrompt: String,
        attachments: List<PendingAttachment>
    ): Flow<StreamEvent> = flow {
        val memorySection = if (globalMemoryEnabled) {
            val active = memories.filter { it.enabled }
            if (active.isEmpty()) ""
            else buildString {
                append("\n\nPersistent User Memory Bank (respect these facts and preferences):")
                active.forEach { m ->
                    append("\n- [${m.category.label}] ${m.content}")
                }
            }
        } else {
            ""
        }

        val fullSystemInstruction = persona.systemInstruction + memorySection
        val model = Firebase.ai.generativeModel(
            modelName = modelId,
            systemInstruction = content { text(fullSystemInstruction) }
        )

        val chatHistory: List<Content> = history.map { msg ->
            val role = if (msg.role == "user") "user" else "model"
            content(role = role) {
                if (msg.attachments.isNotEmpty()) {
                    text("[Attachments: ${msg.attachments.joinToString(", ")}]\n${msg.content}")
                } else {
                    text(msg.content)
                }
            }
        }

        val outgoingContent = content(role = "user") {
            attachments.forEach { att ->
                when {
                    att.isImage && att.bytes != null -> {
                        val bmp = BitmapFactory.decodeByteArray(att.bytes, 0, att.bytes.size)
                        if (bmp != null) {
                            image(bmp)
                        }
                    }
                    att.isPdf && att.bytes != null -> {
                        inlineData(att.bytes, "application/pdf")
                    }
                    att.textContent != null -> {
                        text("--- Attached File: ${att.name} (${att.mimeType}) ---\n${att.textContent}\n--- End File ---")
                    }
                }
            }
            if (userPrompt.isNotBlank()) {
                text(userPrompt)
            } else if (attachments.isNotEmpty()) {
                text("Please analyze the attached file(s) and summarize key insights.")
            }
        }

        val chat = model.startChat(history = chatHistory)
        val accumulated = StringBuilder()

        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(outgoingContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(StreamEvent.Chunk(accumulated.toString()))
                    }
                }
            }
            val finalOutput = accumulated.toString()
            if (finalOutput.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
            emit(StreamEvent.Completed(finalOutput))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat streaming timed out after 60s", e)
            emit(StreamEvent.TimedOut(accumulated.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat streaming failed", e)
            throw e
        }
    }

    suspend fun enhancePrompt(draft: String): Result<String> {
        return try {
            val response = withTimeout(30_000L) {
                enhancerModel.generateContent(draft)
            }
            val enhanced = response.text?.trim()
            if (!enhanced.isNullOrBlank()) {
                Result.success(enhanced)
            } else {
                Result.failure(IllegalStateException("Prompt enhancer returned an empty result."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Prompt enhancement failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateFollowUps(userPrompt: String, assistantReply: String): List<String> {
        return try {
            val response = withTimeout(30_000L) {
                followUpModel.generateContent(
                    "Based on this exchange, suggest 3 concise, high-value follow-up prompts the user might tap next.\n\nUser: ${userPrompt.take(800)}\n\nAssistant: ${assistantReply.take(1600)}"
                )
            }
            val jsonString = response.text ?: return emptyList()
            val obj = JSONObject(jsonString)
            val arr = obj.optJSONArray("suggestions") ?: return emptyList()
            buildList {
                for (i in 0 until minOf(arr.length(), 3)) {
                    val item = arr.optString(i).trim()
                    if (item.isNotBlank()) add(item)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Follow-up generation skipped: ${e.message}")
            emptyList()
        }
    }

    suspend fun generateStudioImage(
        prompt: String,
        aspectRatio: StudioAspectRatio,
        modelId: String,
        sourceBitmap: Bitmap?
    ): Result<Bitmap> {
        return try {
            val sdkAspectRatio = when (aspectRatio) {
                StudioAspectRatio.SQUARE_1_1 -> AspectRatio.SQUARE_1x1
                StudioAspectRatio.LANDSCAPE_16_9 -> AspectRatio.LANDSCAPE_16x9
                StudioAspectRatio.PORTRAIT_9_16 -> AspectRatio.PORTRAIT_9x16
                StudioAspectRatio.CLASSIC_4_3 -> AspectRatio.LANDSCAPE_4x3
                StudioAspectRatio.TALL_3_4 -> AspectRatio.PORTRAIT_3x4
            }

            val config = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(sdkAspectRatio)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }

            val imageModel = Firebase.ai.generativeModel(
                modelName = modelId,
                generationConfig = config
            )

            val response = withTimeout(60_000L) {
                if (sourceBitmap != null) {
                    imageModel.generateContent(
                        content {
                            image(sourceBitmap)
                            text(prompt)
                        }
                    )
                } else {
                    imageModel.generateContent(prompt)
                }
            }

            val bitmap = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image

            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(IllegalStateException("No image was returned by the model."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Image Studio generation timed out", e)
            Result.failure(IllegalStateException("Image generation timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image Studio generation failed", e)
            Result.failure(e)
        }
    }
}
