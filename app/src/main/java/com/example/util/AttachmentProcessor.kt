package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.domain.PendingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

object AttachmentProcessor {
    const val MAX_PDF_BYTES: Long = 18L * 1024L * 1024L // 18 MB
    private const val MAX_TEXT_CHARS = 80_000

    suspend fun loadImageAttachment(context: Context, uri: Uri): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "image/jpeg"
                val name = resolveDisplayName(context, uri) ?: "image_${System.currentTimeMillis()}.jpg"
                val bitmap = decodeDownsampledBitmap(context, uri, 1600)
                    ?: return@withContext Result.failure(IllegalArgumentException("Unable to decode selected image."))
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                val bytes = out.toByteArray()
                Result.success(
                    PendingAttachment(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        mimeType = mimeType,
                        sizeBytes = bytes.size.toLong(),
                        isImage = true,
                        isPdf = false,
                        bytes = bytes,
                        previewUriString = uri.toString()
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loadDocumentAttachment(context: Context, uri: Uri): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                val name = resolveDisplayName(context, uri) ?: "document"
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(IllegalArgumentException("Unable to read selected file."))

                val lower = name.lowercase()
                val isPdf = mimeType == "application/pdf" || lower.endsWith(".pdf")

                if (isPdf) {
                    if (rawBytes.size.toLong() > MAX_PDF_BYTES) {
                        return@withContext Result.failure(
                            IllegalArgumentException("PDF exceeds the 18 MB limit (${formatFileSize(rawBytes.size.toLong())}).")
                        )
                    }
                    return@withContext Result.success(
                        PendingAttachment(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            mimeType = "application/pdf",
                            sizeBytes = rawBytes.size.toLong(),
                            isImage = false,
                            isPdf = true,
                            bytes = rawBytes
                        )
                    )
                }

                val isSupportedText = mimeType.startsWith("text/") ||
                    mimeType.contains("json") ||
                    mimeType.contains("xml") ||
                    mimeType.contains("csv") ||
                    lower.endsWith(".md") ||
                    lower.endsWith(".txt") ||
                    lower.endsWith(".json") ||
                    lower.endsWith(".kt") ||
                    lower.endsWith(".kts") ||
                    lower.endsWith(".java") ||
                    lower.endsWith(".csv") ||
                    lower.endsWith(".log") ||
                    lower.endsWith(".py") ||
                    lower.endsWith(".js") ||
                    lower.endsWith(".ts") ||
                    lower.endsWith(".xml") ||
                    lower.endsWith(".yaml") ||
                    lower.endsWith(".yml")

                if (!isSupportedText) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Unsupported file type ($mimeType). Choose a PDF (<=18 MB) or text/code file.")
                    )
                }

                if (rawBytes.size.toLong() > MAX_PDF_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("File exceeds 18 MB size limit.")
                    )
                }

                val decoded = rawBytes.toString(Charsets.UTF_8).take(MAX_TEXT_CHARS)
                Result.success(
                    PendingAttachment(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        mimeType = mimeType,
                        sizeBytes = rawBytes.size.toLong(),
                        isImage = false,
                        isPdf = false,
                        textContent = decoded
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun decodeDownsampledBitmap(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
