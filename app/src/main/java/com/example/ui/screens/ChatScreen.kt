package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.BuiltInPersonas
import com.example.domain.ChatMessage
import com.example.domain.GeminiModelOption
import com.example.domain.MessageReaction
import com.example.domain.PendingAttachment
import com.example.domain.Persona
import com.example.ui.components.DiffViewerBottomSheet
import com.example.ui.components.EditTextPromptDialog
import com.example.ui.components.RichMarkdownContent
import com.example.ui.theme.LocalUnknownColors
import com.example.ui.viewmodel.WorkspaceViewModel
import com.example.util.AttachmentProcessor
import com.example.util.ExportManager

@Composable
fun ChatScreen(
    viewModel: WorkspaceViewModel,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenPersonas: () -> Unit
) {
    val context = LocalContext.current
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val originalDraft by viewModel.originalDraftBeforeEnhance.collectAsStateWithLifecycle()
    val isEnhancing by viewModel.isEnhancingPrompt.collectAsStateWithLifecycle()
    val attachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val selectedModelId by viewModel.selectedModelId.collectAsStateWithLifecycle()
    val selectedPersonaId by viewModel.selectedPersonaId.collectAsStateWithLifecycle()
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val isListeningDictation by viewModel.voiceEngine.isListening.collectAsStateWithLifecycle()
    val speakingMessageId by viewModel.voiceEngine.speakingMessageId.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.voiceEngine.isSpeaking.collectAsStateWithLifecycle()

    val selectedModel = GeminiModelOption.fromId(selectedModelId)
    val activePersona = personas.firstOrNull { it.id == selectedPersonaId } ?: BuiltInPersonas.presets.first()

    var showModelMenu by remember { mutableStateOf(false) }
    var showPersonaMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var diffTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.addImageAttachment(context, uri)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.addDocumentAttachment(context, uri)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingAudioAction?.invoke()
        } else {
            viewModel.showMessage("Microphone permission is needed for voice input.")
        }
        pendingAudioAction = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingAudioAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, streamingText.length) {
        val total = messages.size + if (isGenerating) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Top Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (showDrawerButton) {
                            IconButton(
                                onClick = onOpenDrawer,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_open_drawer_button")
                            ) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Open sidebar")
                            }
                        }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { showModelMenu = true }
                                    .testTag("chat_model_selector")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedModel.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Outlined.ExpandMore,
                                        contentDescription = "Select model",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false }
                            ) {
                                GeminiModelOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "${option.displayName} (${option.badge})",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                                Text(
                                                    text = option.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            showModelMenu = false
                                            viewModel.selectModel(option)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Box {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { showPersonaMenu = true }
                                    .testTag("chat_persona_selector")
                            ) {
                                Text(
                                    text = activePersona.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showPersonaMenu,
                                onDismissRequest = { showPersonaMenu = false }
                            ) {
                                personas.forEach { persona ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(persona.name, style = MaterialTheme.typography.labelLarge)
                                                if (persona.tagline.isNotBlank()) {
                                                    Text(
                                                        persona.tagline,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            showPersonaMenu = false
                                            viewModel.selectPersona(persona)
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Manage Personas...") },
                                    onClick = {
                                        showPersonaMenu = false
                                        onOpenPersonas()
                                    }
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                runWithMicPermission { viewModel.openVoiceOverlay() }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_voice_overlay_button")
                        ) {
                            Icon(
                                Icons.Outlined.GraphicEq,
                                contentDescription = "Real-time voice conversation",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showExportMenu = true },
                                enabled = messages.isNotEmpty(),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_export_button")
                            ) {
                                Icon(Icons.Outlined.IosShare, contentDescription = "Export conversation")
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export & Share PDF") },
                                    onClick = {
                                        showExportMenu = false
                                        viewModel.exportConversationPdf(context)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Markdown (.md)") },
                                    onClick = {
                                        showExportMenu = false
                                        viewModel.exportConversationMarkdown(context, share = true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy Markdown") },
                                    onClick = {
                                        showExportMenu = false
                                        viewModel.exportConversationMarkdown(context, share = false)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Plain Text (.txt)") },
                                    onClick = {
                                        showExportMenu = false
                                        viewModel.exportConversationPlainText(context, share = true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy Plain Text") },
                                    onClick = {
                                        showExportMenu = false
                                        viewModel.exportConversationPlainText(context, share = false)
                                    }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        // Message Stream or Empty State
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (messages.isEmpty() && !isGenerating) {
                ChatEmptyState(
                    onSelectTemplate = { tpl ->
                        val p = personas.firstOrNull { it.id == tpl.personaId }
                        if (p != null) viewModel.selectPersona(p)
                        viewModel.updateComposerText(tpl.prompt)
                    },
                    onSelectPersona = { p -> viewModel.selectPersona(p) },
                    personas = personas,
                    activePersonaId = activePersona.id
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatMessageItem(
                            message = msg,
                            isSpeakingThis = isSpeaking && speakingMessageId == msg.id,
                            onCopy = {
                                ExportManager.copyToClipboard(context, "Message", msg.content)
                                viewModel.showMessage("Copied to clipboard")
                            },
                            onReadAloud = {
                                if (isSpeaking && speakingMessageId == msg.id) {
                                    viewModel.voiceEngine.stopSpeaking()
                                } else {
                                    viewModel.voiceEngine.speak(msg.content, msg.id)
                                }
                            },
                            onToggleLike = { viewModel.toggleReaction(msg, MessageReaction.LIKED) },
                            onToggleDislike = { viewModel.toggleReaction(msg, MessageReaction.DISLIKED) },
                            onRemember = { viewModel.rememberFromMessage(msg) },
                            onBookmark = { viewModel.bookmarkMessage(msg) },
                            onEditUserMessage = { editingMessage = msg },
                            onRegenerate = { viewModel.regenerateAssistantResponse(msg) },
                            onFollowUpClick = { suggestion -> viewModel.sendMessage(suggestion) },
                            onSaveCodeToVault = { lang, code ->
                                viewModel.saveCodeSnippetToVault("$lang snippet", lang, code)
                            },
                            onCompareDiff = { lang, code ->
                                diffTarget = "${lang.uppercase()} snippet" to code
                            }
                        )
                    }

                    if (isGenerating) {
                        item(key = "streaming_assistant") {
                            StreamingAssistantBubble(
                                accumulatedText = streamingText,
                                onStop = { viewModel.stopGeneration() },
                                onSaveCodeToVault = { lang, code ->
                                    viewModel.saveCodeSnippetToVault("$lang snippet", lang, code)
                                },
                                onCompareDiff = { lang, code ->
                                    diffTarget = "${lang.uppercase()} snippet" to code
                                }
                            )
                        }
                    }
                }
            }
        }

        // Error Retry Banner
        AnimatedVisibility(visible = !chatError.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = chatError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { viewModel.retryLastError() },
                        modifier = Modifier.testTag("chat_error_retry_button")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry")
                    }
                }
            }
        }

        // Composer Area
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Restore Original Prompt Undo Pill
                AnimatedVisibility(visible = originalDraft != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { viewModel.restoreOriginalDraft() }
                                .testTag("composer_restore_original_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Undo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Prompt enhanced • Restore original",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Pending Attachments Row
                AnimatedVisibility(visible = attachments.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(attachments, key = { it.id }) { att ->
                            PendingAttachmentBadge(
                                attachment = att,
                                onRemove = { viewModel.removePendingAttachment(att.id) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        IconButton(
                            onClick = { showAttachMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("composer_attach_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add attachment")
                        }
                        DropdownMenu(
                            expanded = showAttachMenu,
                            onDismissRequest = { showAttachMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Attach Image (Photo Picker)") },
                                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                onClick = {
                                    showAttachMenu = false
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Attach PDF (<=18 MB) or Code/Text") },
                                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                onClick = {
                                    showAttachMenu = false
                                    documentPickerLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "text/*",
                                            "application/json",
                                            "application/xml",
                                            "*/*"
                                        )
                                    )
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = composerText,
                        onValueChange = { viewModel.updateComposerText(it) },
                        placeholder = {
                            Text(
                                if (isListeningDictation) "Listening for dictation..."
                                else "Message UNKNOWN AI..."
                            )
                        },
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_composer_input")
                    )

                    IconButton(
                        onClick = { viewModel.enhanceComposerPrompt() },
                        enabled = composerText.isNotBlank() && !isEnhancing && !isGenerating,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("composer_enhance_button")
                    ) {
                        if (isEnhancing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Outlined.AutoFixHigh,
                                contentDescription = "Enhance prompt",
                                tint = if (composerText.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            runWithMicPermission {
                                if (isListeningDictation) {
                                    viewModel.voiceEngine.stopListening()
                                } else {
                                    viewModel.voiceEngine.startListening(
                                        onFinalResult = { spoken ->
                                            val merged = if (composerText.isBlank()) spoken
                                            else "$composerText $spoken"
                                            viewModel.updateComposerText(merged)
                                        },
                                        onError = { err -> viewModel.showMessage(err) }
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("composer_dictation_button")
                    ) {
                        Icon(
                            Icons.Outlined.Mic,
                            contentDescription = "Voice dictation",
                            tint = if (isListeningDictation) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            if (isGenerating) viewModel.stopGeneration()
                            else viewModel.sendMessage()
                        },
                        enabled = isGenerating || composerText.isNotBlank() || attachments.isNotEmpty(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isGenerating) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("composer_send_stop_button")
                    ) {
                        Icon(
                            imageVector = if (isGenerating) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                            contentDescription = if (isGenerating) "Stop generation" else "Send message"
                        )
                    }
                }
            }
        }
    }

    editingMessage?.let { target ->
        EditTextPromptDialog(
            title = "Edit Message & Regenerate",
            initialText = target.content,
            confirmLabel = "Save & Regenerate",
            onDismiss = { editingMessage = null },
            onConfirm = { newText ->
                editingMessage = null
                viewModel.editUserMessageAndRegenerate(target, newText)
            }
        )
    }

    diffTarget?.let { (title, code) ->
        DiffViewerBottomSheet(
            candidateTitle = title,
            candidateCode = code,
            savedSnippets = bookmarks,
            onDismiss = { diffTarget = null }
        )
    }
}

@Composable
private fun ChatEmptyState(
    personas: List<Persona>,
    activePersonaId: String,
    onSelectPersona: (Persona) -> Unit,
    onSelectTemplate: (com.example.domain.StarterTemplate) -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "What are we building today?",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Attach images or PDFs up to 18 MB, switch Gemini models anytime, or tap a starter template below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "ACTIVE PERSONA",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            personas.forEach { p ->
                val selected = p.id == activePersonaId
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectPersona(p) }
                ) {
                    Text(
                        text = p.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "STARTER TEMPLATES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BuiltInPersonas.starterTemplates.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { tpl ->
                        Card(
                            onClick = { onSelectTemplate(tpl) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("starter_template_${tpl.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = tpl.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tpl.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    isSpeakingThis: Boolean,
    onCopy: () -> Unit,
    onReadAloud: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onRemember: () -> Unit,
    onBookmark: () -> Unit,
    onEditUserMessage: () -> Unit,
    onRegenerate: () -> Unit,
    onFollowUpClick: (String) -> Unit,
    onSaveCodeToVault: (String, String) -> Unit,
    onCompareDiff: (String, String) -> Unit
) {
    val isUser = message.role == "user"
    val ext = LocalUnknownColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                color = ext.userBubble,
                border = BorderStroke(1.dp, ext.userBubbleBorder),
                modifier = Modifier.widthIn(max = 560.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (message.attachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            message.attachments.forEach { badge ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.AttachFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onEditUserMessage,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit message",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy message",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (message.partialTimeout) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "Partial response (60s timeout reached)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    RichMarkdownContent(
                        content = message.content,
                        onSaveCodeToVault = onSaveCodeToVault,
                        onCompareDiff = onCompareDiff
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCopy, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onReadAloud, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(
                                if (isSpeakingThis) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = if (isSpeakingThis) "Stop reading" else "Read aloud",
                                tint = if (isSpeakingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onToggleLike, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(
                                Icons.Outlined.ThumbUp,
                                contentDescription = "Like",
                                tint = if (message.reaction == MessageReaction.LIKED) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onToggleDislike, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(
                                Icons.Outlined.ThumbDown,
                                contentDescription = "Dislike",
                                tint = if (message.reaction == MessageReaction.DISLIKED) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onRemember, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(
                                Icons.Outlined.Psychology,
                                contentDescription = "Remember to Memory Bank",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onBookmark, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(
                                Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark answer",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onRegenerate, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Regenerate response",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (message.followUps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(message.followUps) { suggestion ->
                        AssistChip(
                            onClick = { onFollowUpClick(suggestion) },
                            label = {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantBubble(
    accumulatedText: String,
    onStop: () -> Unit,
    onSaveCodeToVault: (String, String) -> Unit,
    onCompareDiff: (String, String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "Streaming response...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = onStop) {
                    Text("Stop")
                }
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
            if (accumulatedText.isNotBlank()) {
                RichMarkdownContent(
                    content = accumulatedText,
                    onSaveCodeToVault = onSaveCodeToVault,
                    onCompareDiff = onCompareDiff
                )
            }
        }
    }
}

@Composable
private fun PendingAttachmentBadge(
    attachment: PendingAttachment,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (attachment.isImage) Icons.Outlined.Image else Icons.Outlined.Description,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = attachment.name.take(22),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = AttachmentProcessor.formatFileSize(attachment.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove attachment", modifier = Modifier.size(14.dp))
            }
        }
    }
}
