package com.shivam.vaultnotes.data.model

import java.util.UUID

/**
 * Immutable AST representing a standalone HTML5 note document in VaultNotes.
 */
data class NoteDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val blocks: List<NoteBlock> = listOf(ParagraphBlock())
)

sealed interface NoteBlock {
    val id: String
}

data class ParagraphBlock(
    override val id: String = UUID.randomUUID().toString(),
    val content: RichTextData = RichTextData()
) : NoteBlock

data class ImageBlock(
    override val id: String = UUID.randomUUID().toString(),
    val relativePath: String, // e.g., "assets/img_20261116_113000.jpg"
    val altText: String = ""
) : NoteBlock

data class TableBlock(
    override val id: String = UUID.randomUUID().toString(),
    val columnWidthsDp: List<Int> = listOf(140, 180),
    val rows: List<List<RichTextData>> = listOf(
        listOf(RichTextData(), RichTextData()),
        listOf(RichTextData(), RichTextData())
    )
) : NoteBlock

data class ToggleBlock(
    override val id: String = UUID.randomUUID().toString(),
    val isExpanded: Boolean = true,
    val summary: RichTextData = RichTextData(text = "Toggle header"),
    val bodyBlocks: List<ParagraphBlock> = listOf(ParagraphBlock())
) : NoteBlock

/**
 * Rich text model backing ParagraphBlock, Table cells, and Toggle summaries/bodies.
 * Inline math spans are anchored at character ranges where a placeholder token exists in [text].
 */
data class RichTextData(
    val text: String = "",
    val spans: List<StyleSpanRange> = emptyList(),
    val mathSpans: List<MathSpanRange> = emptyList()
)

data class StyleSpanRange(
    val start: Int,
    val end: Int,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null,      // e.g. "#DC2626"
    val highlightColorHex: String? = null, // e.g. "rgba(255,235,59,0.45)" or "#73FFEB3B"
    val fontSizeSp: Int? = null            // 10..30
) {
    val isEmptyFormat: Boolean
        get() = !isBold && !isItalic && !isUnderline &&
            textColorHex == null && highlightColorHex == null && fontSizeSp == null
}

data class MathSpanRange(
    val id: String = UUID.randomUUID().toString(),
    val start: Int,
    val end: Int,
    val latex: String,
    val displayFallback: String = latex
)

/**
 * Active formatting state in the floating pill toolbar for selection or typing cursor.
 */
data class ActiveFormattingState(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String? = null,
    val highlightColorHex: String? = null,
    val fontSizeSp: Int = 16
)

/**
 * Represents a file or folder inside the Vault directory hierarchy.
 */
data class VaultItem(
    val relativePath: String, // e.g. "Study/Physics Notes.html" or "Study"
    val parentPath: String,   // e.g. "Study" or ""
    val fileName: String,     // e.g. "Physics Notes.html" or "Study"
    val displayName: String,  // e.g. "Physics Notes" or "" (if untitled note)
    val isDirectory: Boolean,
    val itemCount: Int = 0,
    val lastModified: Long = 0L,
    val subtitle: String = "",
    val noteId: String? = null
) {
    val isUntitledNote: Boolean
        get() = !isDirectory && (displayName.isBlank() || fileName.matches(UNTITLED_REGEX))

    companion object {
        val UNTITLED_REGEX = Regex("^Untitled(\\s+\\d+)?(\\.html)?$", RegexOption.IGNORE_CASE)
    }
}

enum class ClipboardOperation {
    MOVE,
    COPY
}

data class ClipboardState(
    val operation: ClipboardOperation,
    val sourcePaths: List<String>
)
