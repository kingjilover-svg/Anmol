package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.domain.ChatMessage
import com.example.domain.Conversation
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun formatAsMarkdown(conversation: Conversation, messages: List<ChatMessage>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return buildString {
            appendLine("# ${conversation.title}")
            appendLine()
            appendLine("- **Model**: `${conversation.modelId}`")
            appendLine("- **Exported**: ${sdf.format(Date())}")
            appendLine()
            appendLine("---")
            appendLine()
            messages.forEach { msg ->
                val author = if (msg.role == "user") "You" else "UNKNOWN AI"
                appendLine("### $author")
                if (msg.attachments.isNotEmpty()) {
                    appendLine("_Attachments: ${msg.attachments.joinToString(", ")}_")
                    appendLine()
                }
                appendLine(msg.content)
                appendLine()
            }
        }
    }

    fun formatAsPlainText(conversation: Conversation, messages: List<ChatMessage>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return buildString {
            appendLine(conversation.title.uppercase(Locale.getDefault()))
            appendLine("Model: ${conversation.modelId} | Exported: ${sdf.format(Date())}")
            appendLine("=".repeat(48))
            appendLine()
            messages.forEach { msg ->
                val author = if (msg.role == "user") "YOU" else "UNKNOWN AI"
                appendLine("[$author]")
                if (msg.attachments.isNotEmpty()) {
                    appendLine("Attachments: ${msg.attachments.joinToString(", ")}")
                }
                appendLine(msg.content)
                appendLine()
                appendLine("-".repeat(32))
                appendLine()
            }
        }
    }

    fun shareTextExport(context: Context, title: String, content: String, extension: String, mimeType: String) {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(36).ifBlank { "conversation" }
        val file = File(exportsDir, "${safeName}_${System.currentTimeMillis()}.$extension")
        file.writeText(content)
        shareFile(context, file, mimeType, "Share $title")
    }

    fun exportAndSharePdf(context: Context, conversation: Conversation, messages: List<ChatMessage>) {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = conversation.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(36).ifBlank { "conversation" }
        val file = File(exportsDir, "${safeName}_${System.currentTimeMillis()}.pdf")

        val pageWidth = 595 // A4 pt
        val pageHeight = 842
        val margin = 44f
        val contentWidth = (pageWidth - margin * 2).toInt()

        val doc = PdfDocument()
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin + 12f

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 10f
        }
        val rolePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 11f
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }

        fun finishCurrentPageAndStartNext() {
            // Footer
            canvas.drawText("UNKNOWN AI — Page $pageNum", margin, pageHeight - 24f, metaPaint)
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = page.canvas
            y = margin + 8f
        }

        fun drawWrappedLayout(text: String, paint: TextPaint, extraSpaceAfter: Float) {
            val paragraphs = text.ifBlank { " " }.split("\n")
            for (para in paragraphs) {
                val safePara = para.ifEmpty { " " }
                val layout = StaticLayout.Builder.obtain(
                    safePara, 0, safePara.length, paint, contentWidth
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.15f)
                    .build()

                for (lineIdx in 0 until layout.lineCount) {
                    val lineTop = layout.getLineTop(lineIdx)
                    val lineBottom = layout.getLineBottom(lineIdx)
                    val lineHeight = (lineBottom - lineTop).toFloat()
                    if (y + lineHeight > pageHeight - margin - 20f) {
                        finishCurrentPageAndStartNext()
                    }
                    val start = layout.getLineStart(lineIdx)
                    val end = layout.getLineEnd(lineIdx)
                    val lineText = safePara.substring(start, end)
                    canvas.drawText(lineText, margin, y - paint.ascent(), paint)
                    y += lineHeight
                }
            }
            y += extraSpaceAfter
        }

        drawWrappedLayout(conversation.title, titlePaint, 4f)
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        drawWrappedLayout("Model: ${conversation.modelId} • Exported ${sdf.format(Date())}", metaPaint, 10f)
        canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
        y += 16f

        messages.forEach { msg ->
            if (y > pageHeight - margin - 60f) {
                finishCurrentPageAndStartNext()
            }
            val author = if (msg.role == "user") "YOU" else "UNKNOWN AI"
            rolePaint.color = if (msg.role == "user") Color.parseColor("#0F172A") else Color.parseColor("#2563EB")
            drawWrappedLayout(author, rolePaint, 2f)
            if (msg.attachments.isNotEmpty()) {
                drawWrappedLayout("Attachments: ${msg.attachments.joinToString(", ")}", metaPaint, 4f)
            }
            drawWrappedLayout(msg.content, bodyPaint, 12f)
            if (y + 10f < pageHeight - margin) {
                canvas.drawLine(margin, y - 4f, pageWidth - margin, y - 4f, dividerPaint)
                y += 8f
            }
        }

        canvas.drawText("UNKNOWN AI — Page $pageNum", margin, pageHeight - 24f, metaPaint)
        doc.finishPage(page)

        FileOutputStream(file).use { out ->
            doc.writeTo(out)
        }
        doc.close()

        shareFile(context, file, "application/pdf", "Export PDF: ${conversation.title}")
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
