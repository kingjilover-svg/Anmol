package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.BookmarkKind
import com.example.domain.BookmarkSnippet
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalUnknownColors
import com.example.util.DiffEngine
import com.example.util.DiffLineType
import com.example.util.DiffSummary
import com.example.util.ExportManager
import com.example.util.MarkdownSegment
import com.example.util.parseMarkdownSegments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RichMarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
    onSaveCodeToVault: (language: String, code: String) -> Unit,
    onCompareDiff: (language: String, code: String) -> Unit
) {
    val segments = remember(content) { parseMarkdownSegments(content) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (segments.isEmpty() && content.isNotBlank()) {
            ProseBlock(text = content)
        } else {
            segments.forEach { seg ->
                when (seg) {
                    is MarkdownSegment.Prose -> ProseBlock(text = seg.text)
                    is MarkdownSegment.CodeBlock -> CodeBlockCard(
                        language = seg.language,
                        code = seg.code,
                        isUnifiedDiff = seg.isDiff,
                        onSaveToVault = { onSaveCodeToVault(seg.language, seg.code) },
                        onCompareDiff = { onCompareDiff(seg.language, seg.code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProseBlock(text: String) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBg = LocalUnknownColors.current.codeHeader
    val lines = remember(text) { text.lines() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("### ") -> Text(
                    text = parseInlineMarkdown(trimmed.removePrefix("### "), primaryColor, codeBg),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
                trimmed.startsWith("## ") -> Text(
                    text = parseInlineMarkdown(trimmed.removePrefix("## "), primaryColor, codeBg),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
                trimmed.startsWith("# ") -> Text(
                    text = parseInlineMarkdown(trimmed.removePrefix("# "), primaryColor, codeBg),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Row(
                    modifier = Modifier.padding(start = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = parseInlineMarkdown(trimmed.drop(2), primaryColor, codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                else -> if (line.isBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                } else {
                    Text(
                        text = parseInlineMarkdown(line, primaryColor, codeBg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun parseInlineMarkdown(
    text: String,
    accentColor: Color,
    codeBg: Color
): AnnotatedString = buildAnnotatedString {
    var idx = 0
    while (idx < text.length) {
        when {
            text.startsWith("**", idx) -> {
                val end = text.indexOf("**", idx + 2)
                if (end > idx + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(idx + 2, end))
                    }
                    idx = end + 2
                } else {
                    append("**")
                    idx += 2
                }
            }
            text.startsWith("`", idx) -> {
                val end = text.indexOf('`', idx + 1)
                if (end > idx + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            background = codeBg,
                            color = accentColor
                        )
                    ) {
                        append(" ${text.substring(idx + 1, end)} ")
                    }
                    idx = end + 1
                } else {
                    append('`')
                    idx++
                }
            }
            text.startsWith("*", idx) -> {
                val end = text.indexOf('*', idx + 1)
                if (end > idx + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(idx + 1, end))
                    }
                    idx = end + 1
                } else {
                    append('*')
                    idx++
                }
            }
            else -> {
                append(text[idx])
                idx++
            }
        }
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    isUnifiedDiff: Boolean,
    onSaveToVault: () -> Unit,
    onCompareDiff: () -> Unit
) {
    val context = LocalContext.current
    val ext = LocalUnknownColors.current
    val lines = remember(code) { code.lines() }
    val shouldAutoCollapse = lines.size > 18
    var isCollapsed by remember(code) { mutableStateOf(shouldAutoCollapse) }
    var showLineNumbers by remember { mutableStateOf(true) }

    val displayedLines = remember(lines, isCollapsed) {
        if (isCollapsed) lines.take(10) else lines
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("code_block_card"),
        shape = RoundedCornerShape(14.dp),
        color = ext.codeSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ext.codeHeader)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${language.uppercase()} • ${lines.size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showLineNumbers = !showLineNumbers },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("code_toggle_line_numbers")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatListNumbered,
                            contentDescription = "Toggle line numbers",
                            tint = if (showLineNumbers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onCompareDiff,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("code_compare_diff_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.CompareArrows,
                            contentDescription = "Compare diff",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onSaveToVault,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("code_save_vault_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkAdd,
                            contentDescription = "Save to Vault",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            ExportManager.copyToClipboard(context, "Code", code)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("code_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy code",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                displayedLines.forEachIndexed { idx, line ->
                    val isAddedLine = isUnifiedDiff && line.startsWith("+") && !line.startsWith("+++")
                    val isRemovedLine = isUnifiedDiff && line.startsWith("-") && !line.startsWith("---")
                    val isHunkHeader = isUnifiedDiff && line.startsWith("@@")
                    val rowBg = when {
                        isAddedLine -> ext.diffAddBg
                        isRemovedLine -> ext.diffRemoveBg
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        isAddedLine -> ext.diffAddText
                        isRemovedLine -> ext.diffRemoveText
                        isHunkHeader -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .padding(horizontal = 12.dp, vertical = 1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showLineNumbers) {
                            Text(
                                text = (idx + 1).toString().padStart(3),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.width(34.dp)
                            )
                        }
                        Text(
                            text = highlightCodeLine(line, textColor, MaterialTheme.colorScheme.primary, ext.isDark, isAddedLine || isRemovedLine),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            if (shouldAutoCollapse) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = { isCollapsed = !isCollapsed },
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("code_expand_collapse_button")
                ) {
                    Icon(
                        imageVector = if (isCollapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isCollapsed) "Show all ${lines.size} lines" else "Collapse code block"
                    )
                }
            }
        }
    }
}

private val kotlinKeywords = setOf(
    "fun", "val", "var", "class", "object", "interface", "data", "sealed",
    "return", "if", "else", "when", "for", "while", "suspend", "import",
    "package", "private", "override", "true", "false", "null", "const", "def", "async", "await"
)

private fun highlightCodeLine(
    line: String,
    baseColor: Color,
    keywordColor: Color,
    isDark: Boolean,
    isDiffStyled: Boolean
): AnnotatedString = buildAnnotatedString {
    if (isDiffStyled) {
        withStyle(SpanStyle(color = baseColor)) { append(line) }
        return@buildAnnotatedString
    }
    val commentIdx = line.indexOf("//")
    val codePart = if (commentIdx >= 0) line.substring(0, commentIdx) else line
    val commentPart = if (commentIdx >= 0) line.substring(commentIdx) else ""
    val commentColor = if (isDark) Color(0xFF64748B) else Color(0xFF64748B)
    val stringColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669)

    val tokenRegex = Regex("""("[^"]*"|\b[a-zA-Z_][a-zA-Z0-9_]*\b)""")
    var last = 0
    for (match in tokenRegex.findAll(codePart)) {
        if (match.range.first > last) {
            withStyle(SpanStyle(color = baseColor)) {
                append(codePart.substring(last, match.range.first))
            }
        }
        val token = match.value
        val color = when {
            token.startsWith("\"") -> stringColor
            token in kotlinKeywords -> keywordColor
            else -> baseColor
        }
        val weight = if (token in kotlinKeywords) FontWeight.SemiBold else FontWeight.Normal
        withStyle(SpanStyle(color = color, fontWeight = weight)) {
            append(token)
        }
        last = match.range.last + 1
    }
    if (last < codePart.length) {
        withStyle(SpanStyle(color = baseColor)) {
            append(codePart.substring(last))
        }
    }
    if (commentPart.isNotEmpty()) {
        withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
            append(commentPart)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffViewerBottomSheet(
    candidateTitle: String,
    candidateCode: String,
    savedSnippets: List<BookmarkSnippet>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snippetsOnly = remember(savedSnippets) {
        savedSnippets.filter { it.kind == BookmarkKind.SNIPPET }
    }
    var baselineText by remember(candidateCode) { mutableStateOf("") }
    var selectedSnippetId by remember { mutableStateOf<String?>(null) }
    val ext = LocalUnknownColors.current

    val diffSummary by produceState(
        initialValue = DiffSummary(emptyList(), 0, 0, 0),
        key1 = baselineText,
        key2 = candidateCode
    ) {
        value = withContext(Dispatchers.Default) {
            DiffEngine.computeLineDiff(baselineText, candidateCode)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Compare Diff",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Candidate: $candidateTitle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = ext.diffAddBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "+${diffSummary.additions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.diffAddText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = ext.diffRemoveBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "-${diffSummary.removals}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.diffRemoveText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "=${diffSummary.unchanged}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (snippetsOnly.isNotEmpty()) {
                Text(
                    text = "Compare against saved Vault snippet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snippetsOnly, key = { it.id }) { snippet ->
                        FilterChip(
                            selected = selectedSnippetId == snippet.id,
                            onClick = {
                                selectedSnippetId = snippet.id
                                baselineText = snippet.content
                            },
                            label = { Text(snippet.title.take(24)) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = baselineText,
                onValueChange = {
                    selectedSnippetId = null
                    baselineText = it
                },
                label = { Text("Paste baseline code to compare against") },
                placeholder = { Text("Paste original snippet here...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("diff_baseline_input")
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 340.dp),
                shape = RoundedCornerShape(14.dp),
                color = ext.codeSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                    items(diffSummary.lines) { diffLine ->
                        val bg = when (diffLine.type) {
                            DiffLineType.ADDED -> ext.diffAddBg
                            DiffLineType.REMOVED -> ext.diffRemoveBg
                            DiffLineType.UNCHANGED -> Color.Transparent
                        }
                        val fg = when (diffLine.type) {
                            DiffLineType.ADDED -> ext.diffAddText
                            DiffLineType.REMOVED -> ext.diffRemoveText
                            DiffLineType.UNCHANGED -> MaterialTheme.colorScheme.onSurface
                        }
                        val prefix = when (diffLine.type) {
                            DiffLineType.ADDED -> "+"
                            DiffLineType.REMOVED -> "-"
                            DiffLineType.UNCHANGED -> " "
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bg)
                                .padding(horizontal = 10.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (diffLine.leftLineNumber?.toString() ?: "").padStart(3),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.width(30.dp)
                            )
                            Text(
                                text = (diffLine.rightLineNumber?.toString() ?: "").padStart(3),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.width(30.dp)
                            )
                            Text(
                                text = "$prefix ${diffLine.content}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = fg
                            )
                        }
                    }
                }
            }
        }
    }
}
