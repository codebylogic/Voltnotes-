package com.shivam.vaultnotes.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.vaultnotes.data.codec.NoteHtmlCodec
import com.shivam.vaultnotes.data.model.ActiveFormattingState
import com.shivam.vaultnotes.data.model.MathSpanRange
import com.shivam.vaultnotes.data.model.RichTextData
import com.shivam.vaultnotes.data.model.StyleSpanRange
import com.shivam.vaultnotes.ui.theme.LocalVaultPalette
import ru.noties.jlatexmath.JLatexMathDrawable
import java.util.UUID

/**
 * Cached JLaTeXMath bitmap engine + AnnotatedString span editor.
 * Supports:
 * - Bold, Italic, Underline, Text Color, Translucent Highlighter, Font Size (10.sp..30.sp)
 * - Inline LaTeX math bitmaps rendered via JLaTeXMath scaled to 1.2x line height
 */
object LatexBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(120) {}

    fun getOrRender(latex: String, textSizePx: Float, textColorArgb: Int): Pair<ImageBitmap, Pair<Int, Int>> {
        val cleanTex = latex.trim().ifEmpty { "x" }
        val key = "$cleanTex|${textSizePx.toInt()}|$textColorArgb"
        val cached = cache.get(key)
        if (cached != null) {
            return cached to (cached.width to cached.height)
        }

        val bitmap = try {
            val drawable = JLatexMathDrawable.builder(cleanTex)
                .textSize(textSizePx)
                .color(textColorArgb)
                .background(0x00000000)
                .padding(4)
                .align(JLatexMathDrawable.ALIGN_LEFT)
                .build()
            val w = drawable.intrinsicWidth.coerceAtLeast(24)
            val h = drawable.intrinsicHeight.coerceAtLeast(24)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bmp
        } catch (_: Throwable) {
            val fallback = NoteHtmlCodec.texToReadableFallback(cleanTex)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColorArgb
                textSize = textSizePx * 0.85f
                isFakeBoldText = true
            }
            val w = (paint.measureText(fallback).toInt() + 16).coerceAtLeast(32)
            val h = (textSizePx * 1.2f).toInt().coerceAtLeast(28)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val fm = paint.fontMetrics
            val baseline = (h - fm.bottom - fm.top) / 2f
            canvas.drawText(fallback, 8f, baseline, paint)
            bmp
        }

        val imgBitmap = bitmap.asImageBitmap()
        cache.put(key, imgBitmap)
        return imgBitmap to (imgBitmap.width to imgBitmap.height)
    }
}

object RichTextSpanEngine {

    fun parseCssColor(cssColor: String?): Color? {
        if (cssColor.isNullOrBlank()) return null
        val trimmed = cssColor.trim()
        return try {
            if (trimmed.startsWith("rgba(", ignoreCase = true) || trimmed.startsWith("rgb(", ignoreCase = true)) {
                val inside = trimmed.substringAfter("(").substringBefore(")")
                val parts = inside.split(",").map { it.trim() }
                val r = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 255) ?: 255
                val g = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 255) ?: 235
                val b = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 255) ?: 59
                val aFloat = parts.getOrNull(3)?.toFloatOrNull() ?: 1f
                val a = if (aFloat <= 1f) (aFloat * 255f).toInt().coerceIn(0, 255) else aFloat.toInt().coerceIn(0, 255)
                Color(r, g, b, a)
            } else if (trimmed.startsWith("#")) {
                Color(android.graphics.Color.parseColor(trimmed))
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun buildAnnotatedStringFromRichText(
        richText: RichTextData,
        forInlineOverlay: Boolean = false
    ): AnnotatedString {
        return buildAnnotatedString {
            val text = richText.text
            val mathByStart = richText.mathSpans.associateBy { it.start }

            var i = 0
            while (i < text.length) {
                val mathSpan = mathByStart[i]
                if (mathSpan != null) {
                    appendInlineContent(id = mathSpan.id, alternateText = NoteHtmlCodec.MATH_OBJECT_CHAR.toString())
                    if (!forInlineOverlay) {
                        addStyle(
                            style = SpanStyle(color = Color.Transparent),
                            start = i,
                            end = (i + 1).coerceAtMost(text.length)
                        )
                    }
                    i = mathSpan.end.coerceAtLeast(i + 1)
                } else {
                    append(text[i])
                    i++
                }
            }

            for (span in richText.spans) {
                val start = span.start.coerceIn(0, text.length)
                val end = span.end.coerceIn(start, text.length)
                if (start < end) {
                    addStyle(
                        style = SpanStyle(
                            color = if (forInlineOverlay) {
                                Color.Transparent
                            } else {
                                parseCssColor(span.textColorHex) ?: Color.Unspecified
                            },
                            background = if (forInlineOverlay) {
                                Color.Unspecified
                            } else {
                                parseCssColor(span.highlightColorHex) ?: Color.Unspecified
                            },
                            fontSize = span.fontSizeSp?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                            fontWeight = if (span.isBold) FontWeight.Bold else null,
                            fontStyle = if (span.isItalic) FontStyle.Italic else null,
                            textDecoration = if (forInlineOverlay) {
                                null
                            } else if (span.isUnderline) {
                                TextDecoration.Underline
                            } else {
                                null
                            }
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }

    fun inspectFormattingAtSelection(richText: RichTextData, selection: TextRange): ActiveFormattingState {
        if (richText.text.isEmpty()) return ActiveFormattingState()
        val probeIndex = if (selection.collapsed) {
            (selection.start - 1).coerceIn(0, (richText.text.length - 1).coerceAtLeast(0))
        } else {
            selection.min.coerceIn(0, (richText.text.length - 1).coerceAtLeast(0))
        }

        var isBold = false
        var isItalic = false
        var isUnderline = false
        var textColorHex: String? = null
        var highlightColorHex: String? = null
        var fontSizeSp = 16

        for (span in richText.spans) {
            if (probeIndex >= span.start && probeIndex < span.end) {
                if (span.isBold) isBold = true
                if (span.isItalic) isItalic = true
                if (span.isUnderline) isUnderline = true
                if (span.textColorHex != null) textColorHex = span.textColorHex
                if (span.highlightColorHex != null) highlightColorHex = span.highlightColorHex
                if (span.fontSizeSp != null) fontSizeSp = span.fontSizeSp
            }
        }

        return ActiveFormattingState(
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            textColorHex = textColorHex,
            highlightColorHex = highlightColorHex,
            fontSizeSp = fontSizeSp
        )
    }

    /**
     * Adjusts existing style and math spans when text changes between [oldData] and [newText],
     * applying [activeFormat] to any newly inserted characters.
     */
    fun applyTextEdit(
        oldData: RichTextData,
        newText: String,
        activeFormat: ActiveFormattingState
    ): RichTextData {
        val oldText = oldData.text
        if (oldText == newText) return oldData

        // Find common prefix and suffix
        var prefixLen = 0
        val minLen = minOf(oldText.length, newText.length)
        while (prefixLen < minLen && oldText[prefixLen] == newText[prefixLen]) {
            prefixLen++
        }

        var oldSuffixIdx = oldText.length
        var newSuffixIdx = newText.length
        while (oldSuffixIdx > prefixLen && newSuffixIdx > prefixLen &&
            oldText[oldSuffixIdx - 1] == newText[newSuffixIdx - 1]
        ) {
            oldSuffixIdx--
            newSuffixIdx--
        }

        val removedCount = oldSuffixIdx - prefixLen
        val insertedCount = newSuffixIdx - prefixLen
        val delta = insertedCount - removedCount

        val updatedSpans = mutableListOf<StyleSpanRange>()
        for (span in oldData.spans) {
            when {
                span.end <= prefixLen -> updatedSpans.add(span)
                span.start >= oldSuffixIdx -> updatedSpans.add(
                    span.copy(start = span.start + delta, end = span.end + delta)
                )
                else -> {
                    // Span overlaps edited region
                    val newStart = if (span.start < prefixLen) span.start else (prefixLen + insertedCount)
                    val newEnd = if (span.end > oldSuffixIdx) span.end + delta else prefixLen
                    if (newStart < newEnd) {
                        updatedSpans.add(span.copy(start = newStart, end = newEnd))
                    }
                }
            }
        }

        // Apply active formatting to newly inserted text range
        if (insertedCount > 0) {
            val insertedStyle = StyleSpanRange(
                start = prefixLen,
                end = prefixLen + insertedCount,
                isBold = activeFormat.isBold,
                isItalic = activeFormat.isItalic,
                isUnderline = activeFormat.isUnderline,
                textColorHex = activeFormat.textColorHex,
                highlightColorHex = activeFormat.highlightColorHex,
                fontSizeSp = if (activeFormat.fontSizeSp != 16) activeFormat.fontSizeSp else null
            )
            if (!insertedStyle.isEmptyFormat) {
                updatedSpans.add(insertedStyle)
            }
        }

        val updatedMath = mutableListOf<MathSpanRange>()
        for (math in oldData.mathSpans) {
            when {
                math.end <= prefixLen -> {
                    if (math.start < newText.length && newText[math.start] == NoteHtmlCodec.MATH_OBJECT_CHAR) {
                        updatedMath.add(math)
                    }
                }
                math.start >= oldSuffixIdx -> {
                    val shiftedStart = math.start + delta
                    val shiftedEnd = math.end + delta
                    if (shiftedStart >= 0 && shiftedStart < newText.length &&
                        newText[shiftedStart] == NoteHtmlCodec.MATH_OBJECT_CHAR
                    ) {
                        updatedMath.add(math.copy(start = shiftedStart, end = shiftedEnd))
                    }
                }
            }
        }

        return RichTextData(
            text = newText,
            spans = NoteHtmlCodec.normalizeSpans(updatedSpans, newText.length),
            mathSpans = updatedMath
        )
    }

    /**
     * Applies formatting change to a non-collapsed [selection] range inside [richText].
     */
    fun applyFormattingToSelection(
        richText: RichTextData,
        selection: TextRange,
        transform: (StyleSpanRange) -> StyleSpanRange
    ): RichTextData {
        val selStart = selection.min.coerceIn(0, richText.text.length)
        val selEnd = selection.max.coerceIn(selStart, richText.text.length)
        if (selStart >= selEnd) return richText

        val charStyles = Array(richText.text.length) { idx ->
            var s = StyleSpanRange(start = idx, end = idx + 1)
            for (span in richText.spans) {
                if (idx >= span.start && idx < span.end) {
                    s = s.copy(
                        isBold = s.isBold || span.isBold,
                        isItalic = s.isItalic || span.isItalic,
                        isUnderline = s.isUnderline || span.isUnderline,
                        textColorHex = span.textColorHex ?: s.textColorHex,
                        highlightColorHex = span.highlightColorHex ?: s.highlightColorHex,
                        fontSizeSp = span.fontSizeSp ?: s.fontSizeSp
                    )
                }
            }
            s
        }

        for (i in selStart until selEnd) {
            charStyles[i] = transform(charStyles[i])
        }

        return RichTextData(
            text = richText.text,
            spans = NoteHtmlCodec.normalizeSpans(charStyles.toList(), richText.text.length),
            mathSpans = richText.mathSpans
        )
    }

    /**
     * Inserts an inline LaTeX math formula at [cursorPosition] inside [richText].
     */
    fun insertLatexMathAtCursor(
        richText: RichTextData,
        cursorPosition: Int,
        latex: String
    ): Pair<RichTextData, Int> {
        val cleanPos = cursorPosition.coerceIn(0, richText.text.length)
        val token = NoteHtmlCodec.MATH_OBJECT_CHAR.toString()
        val newText = richText.text.substring(0, cleanPos) + token + richText.text.substring(cleanPos)

        val shiftedSpans = richText.spans.mapNotNull { span ->
            when {
                span.end <= cleanPos -> span
                span.start >= cleanPos -> span.copy(start = span.start + 1, end = span.end + 1)
                else -> span.copy(end = span.end + 1)
            }
        }

        val shiftedMath = richText.mathSpans.map { math ->
            if (math.start >= cleanPos) {
                math.copy(start = math.start + 1, end = math.end + 1)
            } else {
                math
            }
        } + MathSpanRange(
            id = UUID.randomUUID().toString(),
            start = cleanPos,
            end = cleanPos + 1,
            latex = latex,
            displayFallback = NoteHtmlCodec.texToReadableFallback(latex)
        )

        val updated = RichTextData(
            text = newText,
            spans = NoteHtmlCodec.normalizeSpans(shiftedSpans, newText.length),
            mathSpans = shiftedMath.sortedBy { it.start }
        )
        return updated to (cleanPos + 1)
    }
}

/**
 * Identifies which rich-text field is currently focused in the Editor so formatting tools and math insertion
 * work seamlessly inside ParagraphBlocks, TableBlock cells, and ToggleBlock summaries/bodies.
 */
sealed interface EditorFocusTarget {
    data class Paragraph(val blockId: String) : EditorFocusTarget
    data class TableCell(val blockId: String, val rowIndex: Int, val colIndex: Int) : EditorFocusTarget
    data class ToggleSummary(val blockId: String) : EditorFocusTarget
    data class ToggleBodyParagraph(val blockId: String, val paragraphId: String) : EditorFocusTarget
}

@Composable
fun RichTextBlockEditor(
    richTextData: RichTextData,
    placeholder: String,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    activeFormatting: ActiveFormattingState,
    focusRequester: FocusRequester? = null,
    externalSelectionTrigger: Pair<Int, Long>? = null,
    onFocusAcquired: (TextRange) -> Unit,
    onSelectionChanged: (TextRange, ActiveFormattingState) -> Unit,
    onRichTextChanged: (RichTextData, TextRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val density: Density = LocalDensity.current

    val annotatedString = remember(richTextData) {
        RichTextSpanEngine.buildAnnotatedStringFromRichText(richTextData)
    }

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                annotatedString = annotatedString,
                selection = TextRange(annotatedString.length)
            )
        )
    }

    // Keep AnnotatedString in sync when updated externally (e.g., Undo/Redo, Formatting toolbar, Math insertion)
    if (textFieldValue.annotatedString != annotatedString) {
        val coercedStart = textFieldValue.selection.start.coerceIn(0, annotatedString.length)
        val coercedEnd = textFieldValue.selection.end.coerceIn(0, annotatedString.length)
        textFieldValue = textFieldValue.copy(
            annotatedString = annotatedString,
            selection = TextRange(coercedStart, coercedEnd)
        )
    }

    LaunchedEffect(externalSelectionTrigger?.second) {
        if (externalSelectionTrigger != null) {
            val pos = externalSelectionTrigger.first.coerceIn(0, annotatedString.length)
            textFieldValue = textFieldValue.copy(selection = TextRange(pos))
        }
    }

    // Build InlineTextContent map for all MathSpanRange items in this RichTextData
    val baseLineHeightSp = if (textStyle.lineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) {
        textStyle.lineHeight.value
    } else {
        25f
    }
    val targetMathHeightPx = with(density) { (baseLineHeightSp * 1.2f).sp.toPx() }
    val textColorArgb = palette.textPrimary.toArgb()

    val inlineContentMap = remember(richTextData.mathSpans, textColorArgb, targetMathHeightPx) {
        richTextData.mathSpans.associate { mathSpan ->
            val (imgBitmap, dims) = LatexBitmapCache.getOrRender(
                latex = mathSpan.latex,
                textSizePx = targetMathHeightPx * 0.72f,
                textColorArgb = textColorArgb
            )
            val widthSp = with(density) { (dims.first.toDp().value + 8f).sp }
            val heightSp = with(density) { (dims.second.toDp().value + 4f).coerceAtLeast(baseLineHeightSp * 1.1f).sp }

            mathSpan.id to InlineTextContent(
                placeholder = Placeholder(
                    width = widthSp,
                    height = heightSp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp)
                        .background(
                            color = palette.activeToolIndigo.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = imgBitmap,
                        contentDescription = mathSpan.latex,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }

    val inlineOverlayAnnotatedString = remember(richTextData) {
        RichTextSpanEngine.buildAnnotatedStringFromRichText(richTextData, forInlineOverlay = true)
    }

    val effectiveModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue: TextFieldValue ->
            val oldSelection = textFieldValue.selection
            val textChanged = newValue.text != richTextData.text
            textFieldValue = newValue

            if (textChanged) {
                val updatedRich = RichTextSpanEngine.applyTextEdit(
                    oldData = richTextData,
                    newText = newValue.text,
                    activeFormat = activeFormatting
                )
                onRichTextChanged(updatedRich, newValue.selection)
            } else if (newValue.selection != oldSelection) {
                val inspected = RichTextSpanEngine.inspectFormattingAtSelection(richTextData, newValue.selection)
                onSelectionChanged(newValue.selection, inspected)
            }
        },
        textStyle = textStyle.copy(color = palette.textPrimary),
        cursorBrush = SolidColor(palette.activeToolIndigo),
        modifier = effectiveModifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.isFocused) {
                    onFocusAcquired(textFieldValue.selection)
                }
            },
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.TopStart) {
                if (richTextData.text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = palette.textSecondary.copy(alpha = 0.4f))
                    )
                }
                if (inlineContentMap.isNotEmpty()) {
                    Text(
                        text = inlineOverlayAnnotatedString,
                        style = textStyle.copy(color = Color.Transparent),
                        inlineContent = inlineContentMap
                    )
                }
                innerTextField()
            }
        }
    )
}
