package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.Conversation
import com.example.domain.Folder
import com.example.domain.FolderColor
import com.example.domain.ThemeMode
import com.example.ui.components.EditTextPromptDialog
import com.example.ui.components.FolderEditorDialog
import com.example.ui.components.MoveToFolderDialog
import com.example.ui.components.VoiceConversationOverlay
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.ImageStudioViewModel
import com.example.ui.viewmodel.SidebarFilter
import com.example.ui.viewmodel.WorkspaceViewModel
import kotlinx.coroutines.launch

enum class WorkspaceDestination {
    CHAT,
    IMAGE_STUDIO,
    VAULT,
    MEMORY_BANK,
    PERSONAS,
    SETTINGS,
    PLANS,
    ADMIN
}

@Composable
fun AdaptiveWorkspaceShell(
    workspaceViewModel: WorkspaceViewModel,
    imageStudioViewModel: ImageStudioViewModel,
    adminViewModel: AdminViewModel?,
    onGoogleSignInFromGuest: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by workspaceViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val voiceOverlayState by workspaceViewModel.voiceOverlayState.collectAsStateWithLifecycle()
    val rmsLevel by workspaceViewModel.voiceEngine.rmsLevel.collectAsStateWithLifecycle()
    val liveTranscript by workspaceViewModel.voiceEngine.liveTranscript.collectAsStateWithLifecycle()
    val prefs by workspaceViewModel.preferences.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            workspaceViewModel.clearSnackbar()
        }
    }

    var currentDestination by remember { mutableStateOf(WorkspaceDestination.CHAT) }

    BackHandler(enabled = currentDestination != WorkspaceDestination.CHAT) {
        currentDestination = WorkspaceDestination.CHAT
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        val sidebarContent: @Composable (closeDrawer: () -> Unit) -> Unit = { closeDrawer ->
            WorkspaceSidebarContent(
                viewModel = workspaceViewModel,
                currentDestination = currentDestination,
                onNavigate = { dest ->
                    currentDestination = dest
                    closeDrawer()
                },
                onNewChat = {
                    workspaceViewModel.startNewChat()
                    currentDestination = WorkspaceDestination.CHAT
                    closeDrawer()
                },
                onSelectConversation = { conv ->
                    workspaceViewModel.selectConversation(conv)
                    currentDestination = WorkspaceDestination.CHAT
                    closeDrawer()
                }
            )
        }

        val mainContent: @Composable (showDrawerButton: Boolean) -> Unit = { showDrawerButton ->
            val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentDestination) {
                        WorkspaceDestination.CHAT -> ChatScreen(
                            viewModel = workspaceViewModel,
                            showDrawerButton = showDrawerButton,
                            onOpenDrawer = openDrawer,
                            onOpenPersonas = { currentDestination = WorkspaceDestination.PERSONAS }
                        )
                        WorkspaceDestination.IMAGE_STUDIO -> ImageStudioScreen(
                            viewModel = imageStudioViewModel,
                            showDrawerButton = showDrawerButton,
                            onOpenDrawer = openDrawer
                        )
                        WorkspaceDestination.VAULT -> VaultScreen(
                            viewModel = workspaceViewModel,
                            showDrawerButton = showDrawerButton,
                            onOpenDrawer = openDrawer,
                            onUseInChat = { content ->
                                workspaceViewModel.updateComposerText(content)
                                currentDestination = WorkspaceDestination.CHAT
                            }
                        )
                        WorkspaceDestination.MEMORY_BANK -> MemoryBankScreen(
                            viewModel = workspaceViewModel,
                            showDrawerButton = showDrawerButton,
                            onOpenDrawer = openDrawer
                        )
                        WorkspaceDestination.PERSONAS -> PersonasScreen(
                            viewModel = workspaceViewModel,
                            showDrawerButton = showDrawerButton,
                            onOpenDrawer = openDrawer,
                            onUsePersonaInChat = { persona ->
                                workspaceViewModel.selectPersona(persona)
                                currentDestination = WorkspaceDestination.CHAT
                            }
                        )
                        WorkspaceDestination.SETTINGS -> ProfileSettingsScreen(
                            viewModel = workspaceViewModel,
                            showDrawerButton = showDrawerButton,
                            onOpenDrawer = openDrawer,
                            onNavigateToPlans = { currentDestination = WorkspaceDestination.PLANS },
                            onNavigateToAdmin = { currentDestination = WorkspaceDestination.ADMIN },
                            onGoogleSignInFromGuest = onGoogleSignInFromGuest,
                            onSignOut = onSignOut
                        )
                        WorkspaceDestination.PLANS -> PlansScreen(
                            viewModel = workspaceViewModel,
                            showDrawerButton = showDrawerButton,
                            onOpenDrawer = openDrawer
                        )
                        WorkspaceDestination.ADMIN -> {
                            if (adminViewModel != null) {
                                AdminConsoleScreen(
                                    adminViewModel = adminViewModel,
                                    showDrawerButton = showDrawerButton,
                                    onOpenDrawer = openDrawer
                                )
                            } else {
                                currentDestination = WorkspaceDestination.SETTINGS
                            }
                        }
                    }
                }
            }
        }

        if (isExpanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .width(304.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    sidebarContent {}
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    mainContent(false)
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(310.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        sidebarContent { scope.launch { drawerState.close() } }
                    }
                }
            ) {
                mainContent(true)
            }
        }
    }

    VoiceConversationOverlay(
        state = voiceOverlayState,
        rmsLevel = rmsLevel,
        liveTranscript = liveTranscript,
        autoLoopEnabled = prefs.voiceAutoLoopEnabled,
        onToggleAutoLoop = { workspaceViewModel.setVoiceAutoLoopEnabled(it) },
        onTogglePause = { workspaceViewModel.toggleVoicePause() },
        onInterruptBargeIn = { workspaceViewModel.interruptVoiceBargeIn() },
        onClose = { workspaceViewModel.closeVoiceOverlay() }
    )
}

@Composable
private fun WorkspaceSidebarContent(
    viewModel: WorkspaceViewModel,
    currentDestination: WorkspaceDestination,
    onNavigate: (WorkspaceDestination) -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (Conversation) -> Unit
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val sidebarFilter by viewModel.sidebarFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.sidebarSearch.collectAsStateWithLifecycle()

    var showFolderEditor by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<Folder?>(null) }
    var movingConversation by remember { mutableStateOf<Conversation?>(null) }
    var renamingConversation by remember { mutableStateOf<Conversation?>(null) }

    val filteredConversations = remember(conversations, sidebarFilter, searchQuery) {
        conversations.filter { conv ->
            val filterOk = when (val f = sidebarFilter) {
                is SidebarFilter.All -> true
                is SidebarFilter.Pinned -> conv.pinned
                is SidebarFilter.ByFolder -> conv.folderId == f.folderId
            }
            val searchOk = searchQuery.isBlank() ||
                conv.title.contains(searchQuery, ignoreCase = true) ||
                conv.lastMessagePreview.contains(searchQuery, ignoreCase = true)
            filterOk && searchOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Brand + Quick Theme Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_unknown_mark),
                    contentDescription = "UNKNOWN AI",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
                Column {
                    Text("UNKNOWN AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (viewModel.isGuestMode) "Guest Mode" else "Synced",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(
                onClick = {
                    val next = when (prefs.themeMode) {
                        ThemeMode.DAYLIGHT -> ThemeMode.DARK
                        ThemeMode.DARK -> ThemeMode.AUTO
                        ThemeMode.AUTO -> ThemeMode.DAYLIGHT
                    }
                    viewModel.setThemeMode(next)
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("sidebar_quick_theme_toggle")
            ) {
                Icon(
                    imageVector = when (prefs.themeMode) {
                        ThemeMode.DAYLIGHT -> Icons.Outlined.LightMode
                        ThemeMode.DARK -> Icons.Outlined.DarkMode
                        ThemeMode.AUTO -> Icons.Outlined.SettingsBrightness
                    },
                    contentDescription = "Cycle theme (${prefs.themeMode.label})"
                )
            }
        }

        Button(
            onClick = onNewChat,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("sidebar_new_chat_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat")
        }

        // Primary Workspace Destinations
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SidebarNavItem(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = "Chat Workspace",
                selected = currentDestination == WorkspaceDestination.CHAT,
                onClick = { onNavigate(WorkspaceDestination.CHAT) }
            )
            SidebarNavItem(
                icon = Icons.Outlined.AutoAwesome,
                label = "Image Studio",
                selected = currentDestination == WorkspaceDestination.IMAGE_STUDIO,
                onClick = { onNavigate(WorkspaceDestination.IMAGE_STUDIO) }
            )
            SidebarNavItem(
                icon = Icons.Outlined.Bookmarks,
                label = "Vault & Bookmarks",
                selected = currentDestination == WorkspaceDestination.VAULT,
                onClick = { onNavigate(WorkspaceDestination.VAULT) }
            )
            SidebarNavItem(
                icon = Icons.Outlined.Psychology,
                label = "AI Memory Bank",
                selected = currentDestination == WorkspaceDestination.MEMORY_BANK,
                onClick = { onNavigate(WorkspaceDestination.MEMORY_BANK) }
            )
            SidebarNavItem(
                icon = Icons.Outlined.Face,
                label = "Personas",
                selected = currentDestination == WorkspaceDestination.PERSONAS,
                onClick = { onNavigate(WorkspaceDestination.PERSONAS) }
            )
            SidebarNavItem(
                icon = Icons.Outlined.WorkspacePremium,
                label = "Plans & Pricing",
                selected = currentDestination == WorkspaceDestination.PLANS,
                onClick = { onNavigate(WorkspaceDestination.PLANS) }
            )
            if (profile?.isAdmin == true) {
                SidebarNavItem(
                    icon = Icons.Outlined.AdminPanelSettings,
                    label = "Admin Console",
                    selected = currentDestination == WorkspaceDestination.ADMIN,
                    onClick = { onNavigate(WorkspaceDestination.ADMIN) }
                )
            }
            SidebarNavItem(
                icon = Icons.Outlined.Settings,
                label = "Profile & Settings",
                selected = currentDestination == WorkspaceDestination.SETTINGS,
                onClick = { onNavigate(WorkspaceDestination.SETTINGS) }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Search & Folders
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSidebarSearch(it) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            placeholder = { Text("Filter chats...", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "FOLDERS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = {
                    editingFolder = null
                    showFolderEditor = true
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("sidebar_add_folder_button")
            ) {
                Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Create folder", modifier = Modifier.size(18.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = sidebarFilter is SidebarFilter.All,
                onClick = { viewModel.setSidebarFilter(SidebarFilter.All) },
                label = { Text("All") }
            )
            FilterChip(
                selected = sidebarFilter is SidebarFilter.Pinned,
                onClick = { viewModel.setSidebarFilter(SidebarFilter.Pinned) },
                label = { Text("Pinned") }
            )
            folders.forEach { folder ->
                val isSelected = (sidebarFilter as? SidebarFilter.ByFolder)?.folderId == folder.id
                val color = FolderColor.fromKey(folder.colorKey).color
                var showFolderMenu by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) showFolderMenu = true
                            else viewModel.setSidebarFilter(SidebarFilter.ByFolder(folder.id))
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        },
                        label = { Text(folder.name) }
                    )
                    DropdownMenu(
                        expanded = showFolderMenu,
                        onDismissRequest = { showFolderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit folder") },
                            onClick = {
                                showFolderMenu = false
                                editingFolder = folder
                                showFolderEditor = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete folder") },
                            onClick = {
                                showFolderMenu = false
                                viewModel.deleteFolder(folder.id)
                            }
                        )
                    }
                }
            }
        }

        // Conversations List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredConversations, key = { it.id }) { conv ->
                val isActive = conv.id == activeConvId && currentDestination == WorkspaceDestination.CHAT
                var showConvMenu by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectConversation(conv) }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (conv.pinned) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(end = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = conv.title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (conv.lastMessagePreview.isNotBlank()) {
                                Text(
                                    text = conv.lastMessagePreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { showConvMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Conversation actions", modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = showConvMenu,
                                onDismissRequest = { showConvMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (conv.pinned) "Unpin" else "Pin") },
                                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                    onClick = {
                                        showConvMenu = false
                                        viewModel.togglePinConversation(conv)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to folder") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) },
                                    onClick = {
                                        showConvMenu = false
                                        movingConversation = conv
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    onClick = {
                                        showConvMenu = false
                                        renamingConversation = conv
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        showConvMenu = false
                                        viewModel.deleteConversation(conv.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFolderEditor) {
        FolderEditorDialog(
            initialFolder = editingFolder,
            onDismiss = { showFolderEditor = false },
            onSave = { name, colorKey ->
                viewModel.saveFolder(editingFolder?.id, name, colorKey)
                showFolderEditor = false
            }
        )
    }

    movingConversation?.let { conv ->
        MoveToFolderDialog(
            currentFolderId = conv.folderId,
            folders = folders,
            onDismiss = { movingConversation = null },
            onSelectFolder = { folderId ->
                viewModel.moveConversationToFolder(conv, folderId)
                movingConversation = null
            }
        )
    }

    renamingConversation?.let { conv ->
        EditTextPromptDialog(
            title = "Rename Conversation",
            initialText = conv.title,
            confirmLabel = "Save",
            onDismiss = { renamingConversation = null },
            onConfirm = { newTitle ->
                viewModel.renameConversation(conv, newTitle)
                renamingConversation = null
            }
        )
    }
}

@Composable
private fun SidebarNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
