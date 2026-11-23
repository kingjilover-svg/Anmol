package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.GeminiAiRepository
import com.example.data.ai.StreamEvent
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.WorkspaceRepository
import com.example.domain.BookmarkKind
import com.example.domain.BookmarkSnippet
import com.example.domain.BuiltInPersonas
import com.example.domain.ChatMessage
import com.example.domain.Conversation
import com.example.domain.Folder
import com.example.domain.GeminiModelOption
import com.example.domain.MemoryCategory
import com.example.domain.MemoryEntry
import com.example.domain.MessageReaction
import com.example.domain.PendingAttachment
import com.example.domain.Persona
import com.example.domain.PlanTier
import com.example.domain.ThemeMode
import com.example.domain.UserProfile
import com.example.util.AttachmentProcessor
import com.example.util.ExportManager
import com.example.util.VoiceEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "WorkspaceViewModel"

sealed interface SidebarFilter {
    data object All : SidebarFilter
    data object Pinned : SidebarFilter
    data class ByFolder(val folderId: String) : SidebarFilter
}

enum class VoiceLoopPhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    PAUSED
}

data class VoiceOverlayState(
    val isVisible: Boolean = false,
    val phase: VoiceLoopPhase = VoiceLoopPhase.IDLE,
    val transcript: String = "",
    val replyPreview: String = "",
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModel(
    private val uid: String?,
    private val repository: WorkspaceRepository,
    private val aiRepository: GeminiAiRepository,
    private val settings: SettingsDataStore,
    val voiceEngine: VoiceEngine
) : ViewModel() {

    val isGuestMode: Boolean get() = uid == null

    val preferences: StateFlow<AppPreferences> = settings.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AppPreferences())

    private val _workspaceError = MutableStateFlow<String?>(null)
    val workspaceError: StateFlow<String?> = _workspaceError.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(uid)
        .catch { err -> handleStreamError("profile", err); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val folders: StateFlow<List<Folder>> = repository.observeFolders(uid)
        .catch { err -> handleStreamError("folders", err); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val conversations: StateFlow<List<Conversation>> = repository.observeConversations(uid)
        .catch { err -> handleStreamError("conversations", err); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val personas: StateFlow<List<Persona>> = repository.observeAllPersonas(uid)
        .catch { err -> handleStreamError("personas", err); emit(BuiltInPersonas.presets) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), BuiltInPersonas.presets)

    val memories: StateFlow<List<MemoryEntry>> = repository.observeMemories(uid)
        .catch { err -> handleStreamError("memories", err); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val bookmarks: StateFlow<List<BookmarkSnippet>> = repository.observeBookmarks(uid)
        .catch { err -> handleStreamError("bookmarks", err); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val activeMessages: StateFlow<List<ChatMessage>> = _activeConversationId
        .flatMapLatest { convId ->
            repository.observeMessages(uid, convId)
                .catch { err -> handleStreamError("messages", err); emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _sidebarFilter = MutableStateFlow<SidebarFilter>(SidebarFilter.All)
    val sidebarFilter: StateFlow<SidebarFilter> = _sidebarFilter.asStateFlow()

    private val _sidebarSearch = MutableStateFlow("")
    val sidebarSearch: StateFlow<String> = _sidebarSearch.asStateFlow()

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _originalDraftBeforeEnhance = MutableStateFlow<String?>(null)
    val originalDraftBeforeEnhance: StateFlow<String?> = _originalDraftBeforeEnhance.asStateFlow()

    private val _isEnhancingPrompt = MutableStateFlow(false)
    val isEnhancingPrompt: StateFlow<Boolean> = _isEnhancingPrompt.asStateFlow()

    private val _pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
    val pendingAttachments: StateFlow<List<PendingAttachment>> = _pendingAttachments.asStateFlow()

    private val _streamingMessageId = MutableStateFlow<String?>(null)
    val streamingMessageId: StateFlow<String?> = _streamingMessageId.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _pendingRetryPayload = MutableStateFlow<Pair<String, List<PendingAttachment>>?>(null)

    private val _selectedModelId = MutableStateFlow(GeminiModelOption.FLASH_38.modelId)
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _selectedPersonaId = MutableStateFlow(BuiltInPersonas.ASSISTANT_ID)
    val selectedPersonaId: StateFlow<String> = _selectedPersonaId.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _voiceOverlayState = MutableStateFlow(VoiceOverlayState())
    val voiceOverlayState: StateFlow<VoiceOverlayState> = _voiceOverlayState.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var voiceAiJob: Job? = null
    private var restoredInitialConversation = false

    init {
        viewModelScope.launch {
            preferences.collect { prefs ->
                voiceEngine.setSpeechRate(prefs.ttsSpeechRate)
                if (_activeConversationId.value == null) {
                    _selectedModelId.value = prefs.selectedModelId
                }
            }
        }
        // Automatically restore last active conversation on launch
        viewModelScope.launch {
            combine(userProfile, conversations) { profile, convs ->
                profile to convs
            }.collect { (profile, convs) ->
                if (!restoredInitialConversation && convs.isNotEmpty() && _activeConversationId.value == null) {
                    val lastId = profile?.lastActiveConversationId
                    val target = convs.firstOrNull { it.id == lastId } ?: convs.first()
                    selectConversation(target)
                    restoredInitialConversation = true
                }
            }
        }
    }

    private fun handleStreamError(scope: String, error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Stream error in $scope", root)
        _workspaceError.value = root.localizedMessage ?: "Failed to sync $scope"
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun setSidebarFilter(filter: SidebarFilter) {
        _sidebarFilter.value = filter
    }

    fun setSidebarSearch(query: String) {
        _sidebarSearch.value = query
    }

    fun updateComposerText(text: String) {
        _composerText.value = text
        if (text.isBlank()) {
            _originalDraftBeforeEnhance.value = null
        }
    }

    fun selectConversation(conversation: Conversation) {
        _activeConversationId.value = conversation.id
        _selectedModelId.value = conversation.modelId
        _selectedPersonaId.value = conversation.personaId
        _chatError.value = null
        viewModelScope.launch {
            repository.updateLastActiveConversation(uid, conversation.id)
        }
    }

    fun startNewChat() {
        stopGeneration()
        _activeConversationId.value = null
        _chatError.value = null
        _pendingRetryPayload.value = null
    }

    fun selectModel(modelOption: GeminiModelOption) {
        _selectedModelId.value = modelOption.modelId
        viewModelScope.launch {
            settings.setSelectedModelId(modelOption.modelId)
            val conv = conversations.value.firstOrNull { it.id == _activeConversationId.value }
            if (conv != null) {
                repository.updateConversation(uid, conv.copy(modelId = modelOption.modelId))
            }
        }
    }

    fun selectPersona(persona: Persona) {
        _selectedPersonaId.value = persona.id
        viewModelScope.launch {
            val conv = conversations.value.firstOrNull { it.id == _activeConversationId.value }
            if (conv != null) {
                repository.updateConversation(uid, conv.copy(personaId = persona.id))
            }
        }
    }

    fun addImageAttachment(context: Context, uri: Uri) {
        viewModelScope.launch {
            AttachmentProcessor.loadImageAttachment(context, uri)
                .onSuccess { att -> _pendingAttachments.value = _pendingAttachments.value + att }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to attach image." }
        }
    }

    fun addDocumentAttachment(context: Context, uri: Uri) {
        viewModelScope.launch {
            AttachmentProcessor.loadDocumentAttachment(context, uri)
                .onSuccess { att -> _pendingAttachments.value = _pendingAttachments.value + att }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to attach document." }
        }
    }

    fun removePendingAttachment(attachmentId: String) {
        _pendingAttachments.value = _pendingAttachments.value.filterNot { it.id == attachmentId }
    }

    fun enhanceComposerPrompt() {
        val current = _composerText.value.trim()
        if (current.isBlank() || _isEnhancingPrompt.value) return
        viewModelScope.launch {
            _isEnhancingPrompt.value = true
            val original = _composerText.value
            aiRepository.enhancePrompt(current)
                .onSuccess { improved ->
                    _originalDraftBeforeEnhance.value = original
                    _composerText.value = improved
                }
                .onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Could not enhance prompt."
                }
            _isEnhancingPrompt.value = false
        }
    }

    fun restoreOriginalDraft() {
        val original = _originalDraftBeforeEnhance.value ?: return
        _composerText.value = original
        _originalDraftBeforeEnhance.value = null
    }

    fun sendMessage(overridePrompt: String? = null) {
        if (_isGenerating.value) return
        val prompt = (overridePrompt ?: _composerText.value).trim()
        val attachments = if (overridePrompt == null) _pendingAttachments.value else emptyList()
        if (prompt.isBlank() && attachments.isEmpty()) return

        if (overridePrompt == null) {
            _composerText.value = ""
            _originalDraftBeforeEnhance.value = null
            _pendingAttachments.value = emptyList()
        }
        _chatError.value = null
        _pendingRetryPayload.value = prompt to attachments

        viewModelScope.launch {
            val convId = ensureActiveConversation(prompt, attachments)
            val currentMessages = activeMessages.value
            val nextSeq = (currentMessages.maxOfOrNull { it.sequenceIndex } ?: -1) + 1
            val attachmentLabels = attachments.map {
                "${it.name} (${AttachmentProcessor.formatFileSize(it.sizeBytes)})"
            }

            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString().replace("-", ""),
                conversationId = convId,
                role = "user",
                content = prompt.ifBlank { "Analyze attached file(s)." },
                attachments = attachmentLabels,
                sequenceIndex = nextSeq
            )
            repository.createMessage(uid, userMessage)
            repository.incrementUsage(uid, isImage = false)
            updateConversationPreview(convId, userMessage.content)

            launchAssistantStream(
                conversationId = convId,
                history = currentMessages,
                userPrompt = prompt,
                attachments = attachments,
                assistantSequenceIndex = nextSeq + 1
            )
        }
    }

    fun retryLastError() {
        val payload = _pendingRetryPayload.value ?: return
        val convId = _activeConversationId.value ?: return
        val msgs = activeMessages.value
        val lastUser = msgs.lastOrNull { it.role == "user" } ?: return
        val historyBefore = msgs.filter { it.sequenceIndex < lastUser.sequenceIndex }
        _chatError.value = null
        viewModelScope.launch {
            repository.deleteMessagesFromSequence(uid, convId, lastUser.sequenceIndex + 1)
            launchAssistantStream(
                conversationId = convId,
                history = historyBefore,
                userPrompt = payload.first.ifBlank { lastUser.content },
                attachments = payload.second,
                assistantSequenceIndex = lastUser.sequenceIndex + 1
            )
        }
    }

    fun editUserMessageAndRegenerate(message: ChatMessage, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isBlank() || _isGenerating.value) return
        val convId = message.conversationId
        viewModelScope.launch {
            val updated = message.copy(content = trimmed)
            repository.updateMessage(uid, updated)
            repository.deleteMessagesFromSequence(uid, convId, message.sequenceIndex + 1)
            val historyBefore = activeMessages.value.filter { it.sequenceIndex < message.sequenceIndex }
            _chatError.value = null
            _pendingRetryPayload.value = trimmed to emptyList()
            launchAssistantStream(
                conversationId = convId,
                history = historyBefore,
                userPrompt = trimmed,
                attachments = emptyList(),
                assistantSequenceIndex = message.sequenceIndex + 1
            )
        }
    }

    fun regenerateAssistantResponse(assistantMessage: ChatMessage) {
        if (_isGenerating.value) return
        val convId = assistantMessage.conversationId
        val msgs = activeMessages.value
        val previousUser = msgs.lastOrNull {
            it.role == "user" && it.sequenceIndex < assistantMessage.sequenceIndex
        } ?: return
        val historyBefore = msgs.filter { it.sequenceIndex < previousUser.sequenceIndex }
        _chatError.value = null
        _pendingRetryPayload.value = previousUser.content to emptyList()
        viewModelScope.launch {
            repository.deleteMessagesFromSequence(uid, convId, assistantMessage.sequenceIndex)
            launchAssistantStream(
                conversationId = convId,
                history = historyBefore,
                userPrompt = previousUser.content,
                attachments = emptyList(),
                assistantSequenceIndex = assistantMessage.sequenceIndex
            )
        }
    }

    fun stopGeneration() {
        val job = activeGenerationJob ?: return
        val convId = _activeConversationId.value
        val partial = _streamingText.value
        val msgId = _streamingMessageId.value
        job.cancel()
        activeGenerationJob = null
        _isGenerating.value = false
        _streamingMessageId.value = null
        _streamingText.value = ""

        if (convId != null && msgId != null && partial.isNotBlank()) {
            viewModelScope.launch {
                val nextSeq = (activeMessages.value.maxOfOrNull { it.sequenceIndex } ?: 0) + 1
                repository.createMessage(
                    uid,
                    ChatMessage(
                        id = msgId,
                        conversationId = convId,
                        role = "model",
                        content = partial,
                        sequenceIndex = nextSeq
                    )
                )
                updateConversationPreview(convId, partial)
            }
        }
    }

    private suspend fun ensureActiveConversation(
        prompt: String,
        attachments: List<PendingAttachment>
    ): String {
        val existingId = _activeConversationId.value
        if (existingId != null) return existingId

        val newId = UUID.randomUUID().toString().replace("-", "")
        val titleSource = prompt.ifBlank { attachments.firstOrNull()?.name ?: "New Chat" }
        val cleanTitle = titleSource.lineSequence().firstOrNull().orEmpty().take(48).ifBlank { "New Chat" }
        val conv = Conversation(
            id = newId,
            title = cleanTitle,
            folderId = (sidebarFilter.value as? SidebarFilter.ByFolder)?.folderId,
            pinned = false,
            modelId = _selectedModelId.value,
            personaId = _selectedPersonaId.value,
            lastMessagePreview = titleSource.take(120)
        )
        repository.createConversation(uid, conv)
        _activeConversationId.value = newId
        repository.updateLastActiveConversation(uid, newId)
        return newId
    }

    private suspend fun updateConversationPreview(convId: String, previewText: String) {
        val conv = conversations.value.firstOrNull { it.id == convId }
        repository.updateConversationPreview(
            uid = uid,
            conversationId = convId,
            previewText = previewText,
            fallbackConversation = conv
        )
    }

    private fun launchAssistantStream(
        conversationId: String,
        history: List<ChatMessage>,
        userPrompt: String,
        attachments: List<PendingAttachment>,
        assistantSequenceIndex: Int
    ) {
        activeGenerationJob?.cancel()
        val msgId = UUID.randomUUID().toString().replace("-", "")
        _streamingMessageId.value = msgId
        _streamingText.value = ""
        _isGenerating.value = true

        val activePersona = personas.value.firstOrNull { it.id == _selectedPersonaId.value }
            ?: BuiltInPersonas.presets.first()
        val prefs = preferences.value

        activeGenerationJob = viewModelScope.launch {
            try {
                aiRepository.streamChatReply(
                    modelId = _selectedModelId.value,
                    persona = activePersona,
                    memories = memories.value,
                    globalMemoryEnabled = prefs.globalMemoryEnabled,
                    history = history,
                    userPrompt = userPrompt,
                    attachments = attachments
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Chunk -> {
                            _streamingText.value = event.accumulatedText
                        }
                        is StreamEvent.Completed -> {
                            _isGenerating.value = false
                            _streamingMessageId.value = null
                            _streamingText.value = ""
                            val followUps = if (prefs.autoFollowUpsEnabled) {
                                aiRepository.generateFollowUps(userPrompt, event.fullText)
                            } else emptyList()
                            val savedMsg = ChatMessage(
                                id = msgId,
                                conversationId = conversationId,
                                role = "model",
                                content = event.fullText,
                                followUps = followUps,
                                sequenceIndex = assistantSequenceIndex
                            )
                            repository.createMessage(uid, savedMsg)
                            updateConversationPreview(conversationId, event.fullText)
                            _pendingRetryPayload.value = null
                        }
                        is StreamEvent.TimedOut -> {
                            _isGenerating.value = false
                            _streamingMessageId.value = null
                            _streamingText.value = ""
                            if (event.partialText.isNotBlank()) {
                                val partialMsg = ChatMessage(
                                    id = msgId,
                                    conversationId = conversationId,
                                    role = "model",
                                    content = event.partialText,
                                    partialTimeout = true,
                                    sequenceIndex = assistantSequenceIndex
                                )
                                repository.createMessage(uid, partialMsg)
                                updateConversationPreview(conversationId, event.partialText)
                            }
                            _chatError.value = "Response timed out after 60s. Partial output was preserved."
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isGenerating.value = false
                _streamingMessageId.value = null
                _streamingText.value = ""
                _chatError.value = e.localizedMessage ?: "Failed to generate response."
            }
        }
    }

    // --- Message Actions ---
    fun toggleReaction(message: ChatMessage, target: MessageReaction) {
        val next = if (message.reaction == target) MessageReaction.NONE else target
        viewModelScope.launch {
            repository.updateMessage(uid, message.copy(reaction = next))
        }
    }

    fun rememberFromMessage(message: ChatMessage) {
        val snippet = message.content.lineSequence().firstOrNull { it.isNotBlank() }?.take(240)
            ?: return
        viewModelScope.launch {
            repository.saveMemory(
                uid,
                MemoryEntry(
                    id = UUID.randomUUID().toString().replace("-", ""),
                    category = MemoryCategory.PREFERENCE,
                    content = snippet,
                    enabled = true
                ),
                isNew = true
            )
            _snackbarMessage.value = "Saved to AI Memory Bank"
        }
    }

    fun bookmarkMessage(message: ChatMessage) {
        val title = message.content.lineSequence().firstOrNull { it.isNotBlank() }
            ?.replace(Regex("[#*]"), "")?.trim()?.take(60)
            ?: "Saved Answer"
        viewModelScope.launch {
            repository.saveBookmark(
                uid,
                BookmarkSnippet(
                    id = UUID.randomUUID().toString().replace("-", ""),
                    kind = BookmarkKind.ANSWER,
                    title = title,
                    content = message.content,
                    sourceConversationId = message.conversationId
                )
            )
            _snackbarMessage.value = "Bookmarked to Vault"
        }
    }

    fun saveCodeSnippetToVault(title: String, language: String, code: String) {
        viewModelScope.launch {
            repository.saveBookmark(
                uid,
                BookmarkSnippet(
                    id = UUID.randomUUID().toString().replace("-", ""),
                    kind = BookmarkKind.SNIPPET,
                    title = title.ifBlank { "$language snippet" }.take(120),
                    content = code,
                    language = language,
                    sourceConversationId = _activeConversationId.value
                )
            )
            _snackbarMessage.value = "Code snippet saved to Vault"
        }
    }

    fun deleteBookmark(id: String) {
        viewModelScope.launch {
            repository.deleteBookmark(uid, id)
        }
    }

    // --- Folders & Conversation Organization ---
    fun saveFolder(existingId: String?, name: String, colorKey: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val isNew = existingId == null
            val existing = folders.value.firstOrNull { it.id == existingId }
            val folder = Folder(
                id = existingId ?: UUID.randomUUID().toString().replace("-", ""),
                name = name.trim().take(60),
                colorKey = colorKey,
                sortOrder = existing?.sortOrder ?: folders.value.size,
                createdAtMillis = existing?.createdAtMillis ?: 0L
            )
            repository.saveFolder(uid, folder, isNew)
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            if ((sidebarFilter.value as? SidebarFilter.ByFolder)?.folderId == folderId) {
                _sidebarFilter.value = SidebarFilter.All
            }
            conversations.value.filter { it.folderId == folderId }.forEach { conv ->
                repository.updateConversation(uid, conv.copy(folderId = null))
            }
            repository.deleteFolder(uid, folderId)
        }
    }

    fun togglePinConversation(conversation: Conversation) {
        viewModelScope.launch {
            repository.updateConversation(uid, conversation.copy(pinned = !conversation.pinned))
        }
    }

    fun moveConversationToFolder(conversation: Conversation, folderId: String?) {
        viewModelScope.launch {
            repository.updateConversation(uid, conversation.copy(folderId = folderId))
        }
    }

    fun renameConversation(conversation: Conversation, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.updateConversation(uid, conversation.copy(title = newTitle.trim().take(120)))
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (_activeConversationId.value == conversationId) {
                _activeConversationId.value = null
            }
            repository.deleteConversation(uid, conversationId)
        }
    }

    // --- Personas ---
    fun saveCustomPersona(existingId: String?, name: String, tagline: String, instruction: String) {
        if (name.isBlank() || instruction.isBlank()) return
        viewModelScope.launch {
            val isNew = existingId == null
            val existing = personas.value.firstOrNull { it.id == existingId }
            val persona = Persona(
                id = existingId ?: UUID.randomUUID().toString().replace("-", ""),
                name = name.trim(),
                tagline = tagline.trim(),
                systemInstruction = instruction.trim(),
                builtIn = false,
                createdAtMillis = existing?.createdAtMillis ?: 0L
            )
            repository.saveCustomPersona(uid, persona, isNew)
            _snackbarMessage.value = if (isNew) "Persona created" else "Persona updated"
        }
    }

    fun deleteCustomPersona(personaId: String) {
        viewModelScope.launch {
            if (_selectedPersonaId.value == personaId) {
                _selectedPersonaId.value = BuiltInPersonas.ASSISTANT_ID
            }
            repository.deleteCustomPersona(uid, personaId)
        }
    }

    // --- Memories ---
    fun saveMemory(existingId: String?, category: MemoryCategory, content: String, enabled: Boolean = true) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val isNew = existingId == null
            val existing = memories.value.firstOrNull { it.id == existingId }
            repository.saveMemory(
                uid,
                MemoryEntry(
                    id = existingId ?: UUID.randomUUID().toString().replace("-", ""),
                    category = category,
                    content = content.trim(),
                    enabled = enabled,
                    createdAtMillis = existing?.createdAtMillis ?: 0L
                ),
                isNew
            )
        }
    }

    fun toggleMemoryEnabled(entry: MemoryEntry) {
        viewModelScope.launch {
            repository.saveMemory(uid, entry.copy(enabled = !entry.enabled), isNew = false)
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(uid, memoryId)
        }
    }

    // --- Settings & Theme ---
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setGlobalMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setGlobalMemoryEnabled(enabled) }
    }

    fun setAutoFollowUpsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoFollowUpsEnabled(enabled) }
    }

    fun setVoiceAutoLoopEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setVoiceAutoLoopEnabled(enabled) }
    }

    fun setTtsSpeechRate(rate: Float) {
        viewModelScope.launch { settings.setTtsSpeechRate(rate) }
    }

    fun selectPlanTier(tier: PlanTier) {
        viewModelScope.launch {
            repository.updatePlanTier(uid, tier.id)
                .onSuccess { _snackbarMessage.value = "${tier.title} plan active" }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to update plan" }
        }
    }

    // --- Exports ---
    fun exportConversationMarkdown(context: Context, share: Boolean) {
        val conv = conversations.value.firstOrNull { it.id == _activeConversationId.value } ?: return
        val md = ExportManager.formatAsMarkdown(conv, activeMessages.value)
        if (share) {
            ExportManager.shareTextExport(context, conv.title, md, "md", "text/markdown")
        } else {
            ExportManager.copyToClipboard(context, conv.title, md)
            _snackbarMessage.value = "Copied Markdown to clipboard"
        }
    }

    fun exportConversationPlainText(context: Context, share: Boolean) {
        val conv = conversations.value.firstOrNull { it.id == _activeConversationId.value } ?: return
        val txt = ExportManager.formatAsPlainText(conv, activeMessages.value)
        if (share) {
            ExportManager.shareTextExport(context, conv.title, txt, "txt", "text/plain")
        } else {
            ExportManager.copyToClipboard(context, conv.title, txt)
            _snackbarMessage.value = "Copied plain text to clipboard"
        }
    }

    fun exportConversationPdf(context: Context) {
        val conv = conversations.value.firstOrNull { it.id == _activeConversationId.value } ?: return
        viewModelScope.launch {
            runCatching {
                ExportManager.exportAndSharePdf(context, conv, activeMessages.value)
            }.onFailure {
                _snackbarMessage.value = it.localizedMessage ?: "Failed to export PDF"
            }
        }
    }

    // --- Real-Time Voice Conversation Overlay ---
    fun openVoiceOverlay() {
        _voiceOverlayState.value = VoiceOverlayState(
            isVisible = true,
            phase = VoiceLoopPhase.LISTENING,
            transcript = "",
            replyPreview = ""
        )
        startVoiceListeningTurn()
    }

    fun closeVoiceOverlay() {
        voiceAiJob?.cancel()
        voiceEngine.stopListening()
        voiceEngine.stopSpeaking()
        _voiceOverlayState.value = VoiceOverlayState(isVisible = false, phase = VoiceLoopPhase.IDLE)
    }

    fun toggleVoicePause() {
        val current = _voiceOverlayState.value
        if (current.phase == VoiceLoopPhase.PAUSED) {
            _voiceOverlayState.value = current.copy(phase = VoiceLoopPhase.LISTENING, errorMessage = null)
            startVoiceListeningTurn()
        } else {
            voiceAiJob?.cancel()
            voiceEngine.stopListening()
            voiceEngine.stopSpeaking()
            _voiceOverlayState.value = current.copy(phase = VoiceLoopPhase.PAUSED)
        }
    }

    fun interruptVoiceBargeIn() {
        voiceAiJob?.cancel()
        voiceEngine.stopSpeaking()
        _voiceOverlayState.value = _voiceOverlayState.value.copy(
            phase = VoiceLoopPhase.LISTENING,
            errorMessage = null
        )
        startVoiceListeningTurn()
    }

    private fun startVoiceListeningTurn() {
        if (!_voiceOverlayState.value.isVisible) return
        _voiceOverlayState.value = _voiceOverlayState.value.copy(
            phase = VoiceLoopPhase.LISTENING,
            errorMessage = null
        )
        voiceEngine.startListening(
            onFinalResult = { spokenText ->
                processVoiceTurn(spokenText)
            },
            onError = { errMsg ->
                _voiceOverlayState.value = _voiceOverlayState.value.copy(
                    phase = VoiceLoopPhase.PAUSED,
                    errorMessage = errMsg
                )
            }
        )
    }

    private fun processVoiceTurn(spokenText: String) {
        voiceAiJob?.cancel()
        _voiceOverlayState.value = _voiceOverlayState.value.copy(
            phase = VoiceLoopPhase.THINKING,
            transcript = spokenText,
            replyPreview = "",
            errorMessage = null
        )

        voiceAiJob = viewModelScope.launch {
            val convId = ensureActiveConversation(spokenText, emptyList())
            val history = activeMessages.value
            val nextSeq = (history.maxOfOrNull { it.sequenceIndex } ?: -1) + 1
            repository.createMessage(
                uid,
                ChatMessage(
                    id = UUID.randomUUID().toString().replace("-", ""),
                    conversationId = convId,
                    role = "user",
                    content = spokenText,
                    sequenceIndex = nextSeq
                )
            )
            repository.incrementUsage(uid, isImage = false)
            updateConversationPreview(convId, spokenText)

            val persona = personas.value.firstOrNull { it.id == _selectedPersonaId.value }
                ?: BuiltInPersonas.presets.first()
            val prefs = preferences.value

            try {
                aiRepository.streamChatReply(
                    modelId = _selectedModelId.value,
                    persona = persona,
                    memories = memories.value,
                    globalMemoryEnabled = prefs.globalMemoryEnabled,
                    history = history,
                    userPrompt = spokenText,
                    attachments = emptyList()
                ).collect { ev ->
                    when (ev) {
                        is StreamEvent.Chunk -> {
                            _voiceOverlayState.value = _voiceOverlayState.value.copy(
                                replyPreview = ev.accumulatedText
                            )
                        }
                        is StreamEvent.Completed -> {
                            val replyMsgId = UUID.randomUUID().toString().replace("-", "")
                            repository.createMessage(
                                uid,
                                ChatMessage(
                                    id = replyMsgId,
                                    conversationId = convId,
                                    role = "model",
                                    content = ev.fullText,
                                    sequenceIndex = nextSeq + 1
                                )
                            )
                            updateConversationPreview(convId, ev.fullText)
                            _voiceOverlayState.value = _voiceOverlayState.value.copy(
                                phase = VoiceLoopPhase.SPEAKING,
                                replyPreview = ev.fullText
                            )
                            voiceEngine.speak(ev.fullText, replyMsgId) {
                                viewModelScope.launch {
                                    if (_voiceOverlayState.value.isVisible &&
                                        preferences.value.voiceAutoLoopEnabled &&
                                        _voiceOverlayState.value.phase == VoiceLoopPhase.SPEAKING
                                    ) {
                                        startVoiceListeningTurn()
                                    } else if (_voiceOverlayState.value.isVisible) {
                                        _voiceOverlayState.value = _voiceOverlayState.value.copy(
                                            phase = VoiceLoopPhase.PAUSED
                                        )
                                    }
                                }
                            }
                        }
                        is StreamEvent.TimedOut -> {
                            _voiceOverlayState.value = _voiceOverlayState.value.copy(
                                phase = VoiceLoopPhase.PAUSED,
                                replyPreview = ev.partialText,
                                errorMessage = "Voice response timed out."
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _voiceOverlayState.value = _voiceOverlayState.value.copy(
                    phase = VoiceLoopPhase.PAUSED,
                    errorMessage = e.localizedMessage ?: "Voice inference failed."
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeGenerationJob?.cancel()
        voiceAiJob?.cancel()
        voiceEngine.release()
    }

    companion object {
        fun provideFactory(
            uid: String?,
            repository: WorkspaceRepository,
            aiRepository: GeminiAiRepository,
            settings: SettingsDataStore,
            voiceEngine: VoiceEngine
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WorkspaceViewModel(uid, repository, aiRepository, settings, voiceEngine)
            }
        }
    }
}
