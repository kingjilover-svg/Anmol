package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.BookmarkKind
import com.example.domain.MemoryCategory
import com.example.domain.MemoryEntry
import com.example.domain.Persona
import com.example.ui.components.PersonaEditorSheet
import com.example.ui.viewmodel.WorkspaceViewModel
import com.example.util.ExportManager

@Composable
fun VaultScreen(
    viewModel: WorkspaceViewModel,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onUseInChat: (String) -> Unit
) {
    val context = LocalContext.current
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = All, 1 = Answers, 2 = Snippets

    val filtered = remember(bookmarks, searchQuery, selectedTab) {
        bookmarks.filter { item ->
            val tabMatch = when (selectedTab) {
                1 -> item.kind == BookmarkKind.ANSWER
                2 -> item.kind == BookmarkKind.SNIPPET
                else -> true
            }
            val queryMatch = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.content.contains(searchQuery, ignoreCase = true)
            tabMatch && queryMatch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showDrawerButton) {
                        IconButton(onClick = onOpenDrawer, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open sidebar")
                        }
                    }
                    Column {
                        Text("Vault & Bookmarks", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Saved answers & reusable code/prompt snippets",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("All") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Answers") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Snippets") })
                }
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .align(Alignment.CenterHorizontally)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search vault...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vault_search_input")
            )

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No saved bookmarks or snippets yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = (item.language ?: item.kind.value).uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { onUseInChat(item.content) },
                                        modifier = Modifier.testTag("vault_use_in_chat_${item.id}")
                                    ) {
                                        Text("Use in Chat")
                                    }
                                    Row {
                                        IconButton(
                                            onClick = {
                                                ExportManager.copyToClipboard(context, item.title, item.content)
                                                viewModel.showMessage("Copied to clipboard")
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteBookmark(item.id) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryBankScreen(
    viewModel: WorkspaceViewModel,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf<MemoryCategory?>(null) }
    var editingMemory by remember { mutableStateOf<MemoryEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(memories, selectedCategory) {
        if (selectedCategory == null) memories
        else memories.filter { it.category == selectedCategory }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingMemory = null
                    showAddDialog = true
                },
                modifier = Modifier.testTag("memory_add_fab")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add memory")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (showDrawerButton) {
                                IconButton(onClick = onOpenDrawer, modifier = Modifier.minimumInteractiveComponentSize()) {
                                    Icon(Icons.Outlined.Menu, contentDescription = "Open sidebar")
                                }
                            }
                            Column {
                                Text("AI Memory Bank", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Persistent context across conversations",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Global", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = prefs.globalMemoryEnabled,
                                onCheckedChange = { viewModel.setGlobalMemoryEnabled(it) },
                                modifier = Modifier.testTag("memory_global_switch")
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All (${memories.size})") }
                    )
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No memories in this category yet. Tap + or 'Remember' on any AI message.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { mem ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = mem.category.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = mem.content,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Switch(
                                        checked = mem.enabled,
                                        onCheckedChange = { viewModel.toggleMemoryEnabled(mem) }
                                    )
                                    IconButton(
                                        onClick = {
                                            editingMemory = mem
                                            showAddDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Edit memory")
                                    }
                                    IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete memory",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MemoryEditorDialog(
            initial = editingMemory,
            onDismiss = { showAddDialog = false },
            onSave = { cat, content ->
                viewModel.saveMemory(editingMemory?.id, cat, content, editingMemory?.enabled ?: true)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun MemoryEditorDialog(
    initial: MemoryEntry?,
    onDismiss: () -> Unit,
    onSave: (MemoryCategory, String) -> Unit
) {
    var category by remember(initial) { mutableStateOf(initial?.category ?: MemoryCategory.PREFERENCE) }
    var content by remember(initial) { mutableStateOf(initial?.content.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add AI Memory" else "Edit AI Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { if (it.length <= 1000) content = it },
                    label = { Text("Memory instruction") },
                    placeholder = { Text("e.g. Always write Kotlin examples using Jetpack Compose.") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(category, content) },
                enabled = content.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PersonasScreen(
    viewModel: WorkspaceViewModel,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    onUsePersonaInChat: (Persona) -> Unit
) {
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val activePersonaId by viewModel.selectedPersonaId.collectAsStateWithLifecycle()
    var editingPersona by remember { mutableStateOf<Persona?>(null) }
    var showSheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingPersona = null
                    showSheet = true
                },
                modifier = Modifier.testTag("persona_add_fab")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Create persona")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showDrawerButton) {
                            IconButton(onClick = onOpenDrawer, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Open sidebar")
                            }
                        }
                        Column {
                            Text("AI Personas", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Built-in specialists & custom system instructions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(personas, key = { it.id }) { persona ->
                    val isSelected = persona.id == activePersonaId
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(persona.name, style = MaterialTheme.typography.titleMedium)
                                    if (persona.tagline.isNotBlank()) {
                                        Text(
                                            persona.tagline,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (persona.builtIn) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = if (persona.builtIn) "PRESET" else "CUSTOM",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = persona.systemInstruction,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { onUsePersonaInChat(persona) }) {
                                    Text(if (isSelected) "Active in Chat" else "Use in Chat")
                                }
                                if (!persona.builtIn) {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingPersona = persona
                                                showSheet = true
                                            }
                                        ) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Edit persona")
                                        }
                                        IconButton(onClick = { viewModel.deleteCustomPersona(persona.id) }) {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = "Delete persona",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        PersonaEditorSheet(
            initialPersona = editingPersona,
            onDismiss = { showSheet = false },
            onSave = { name, tagline, instruction ->
                viewModel.saveCustomPersona(editingPersona?.id, name, tagline, instruction)
                showSheet = false
            }
        )
    }
}
