package com.shivam.vaultnotes.data.codec

import com.shivam.vaultnotes.data.model.ImageBlock
import com.shivam.vaultnotes.data.model.MathSpanRange
import com.shivam.vaultnotes.data.model.NoteBlock
import com.shivam.vaultnotes.data.model.NoteDocument
import com.shivam.vaultnotes.data.model.ParagraphBlock
import com.shivam.vaultnotes.data.model.RichTextData
import com.shivam.vaultnotes.data.model.StyleSpanRange
import com.shivam.vaultnotes.data.model.TableBlock
import com.shivam.vaultnotes.data.model.ToggleBlock
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.UUID

/**
 * Lossless HTML5 parser and serializer using Jsoup.
 * Converts between standalone browser-readable HTML5 files and [NoteDocument] AST.
 */
object NoteHtmlCodec {

    const val MATH_OBJECT_CHAR = '\uFFFC'
    private val INLINE_DOLLAR_MATH_REGEX = Regex("\\$([^$]+)\\$")

    fun parseHtml(html: String, fallbackTitle: String = ""): NoteDocument {
        if (html.isBlank()) {
            return NoteDocument(
                id = UUID.randomUUID().toString(),
                title = fallbackTitle,
                blocks = listOf(ParagraphBlock())
            )
        }

        val doc: Document = Jsoup.parse(html)
        val noteId = doc.selectFirst("meta[name=note-id]")?.attr("content")
            ?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        val h1Title = doc.selectFirst("h1.note-title")?.text()
            ?: doc.title().takeIf { it.isNotBlank() }
            ?: fallbackTitle

        val bodyContainer = doc.selectFirst("div.note-body") ?: doc.body()
        val blocks = mutableListOf<NoteBlock>()

        if (bodyContainer != null) {
            for (child in bodyContainer.children()) {
                if (child.tagName() == "h1" && child.hasClass("note-title")) continue
                when (child.tagName().lowercase()) {
                    "p", "div" -> {
                        // Check if paragraph only contains a single img
                        val singleImg = if (child.childrenSize() == 1 && child.child(0).tagName() == "img" && child.ownText().isBlank()) {
                            child.child(0)
                        } else null

                        if (singleImg != null) {
                            val src = singleImg.attr("src")
                            if (src.isNotBlank()) {
                                blocks.add(
                                    ImageBlock(
                                        id = singleImg.attr("data-block-id").ifBlank { UUID.randomUUID().toString() },
                                        relativePath = src,
                                        altText = singleImg.attr("alt")
                                    )
                                )
                            }
                        } else {
                            blocks.add(
                                ParagraphBlock(
                                    id = child.attr("data-block-id").ifBlank { UUID.randomUUID().toString() },
                                    content = parseRichTextFromElement(child)
                                )
                            )
                        }
                    }
                    "img" -> {
                        val src = child.attr("src")
                        if (src.isNotBlank()) {
                            blocks.add(
                                ImageBlock(
                                    id = child.attr("data-block-id").ifBlank { UUID.randomUUID().toString() },
                                    relativePath = src,
                                    altText = child.attr("alt")
                                )
                            )
                        }
                    }
                    "table" -> {
                        blocks.add(parseTableElement(child))
                    }
                    "details" -> {
                        blocks.add(parseDetailsElement(child))
                    }
                }
            }
        }

        if (blocks.isEmpty()) {
            blocks.add(ParagraphBlock())
        }

        return NoteDocument(
            id = noteId,
            title = h1Title,
            blocks = blocks
        )
    }

    fun serializeToHtml(document: NoteDocument): String {
        val escapedTitle = Entities.escape(document.title)
        val sb = StringBuilder()
        sb.appendLine("<!doctype html>")
        sb.appendLine("<html>")
        sb.appendLine("<head>")
        sb.appendLine("  <meta charset=\"utf-8\">")
        sb.appendLine("  <meta name=\"note-id\" content=\"${Entities.escape(document.id)}\">")
        sb.appendLine("  <title>$escapedTitle</title>")
        sb.appendLine("  <style>")
        sb.appendLine("    body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; max-width: 720px; margin: 32px auto; padding: 0 20px; line-height: 1.65; color: #0F172A; }")
        sb.appendLine("    table { border-collapse: collapse; margin: 16px 0; }")
        sb.appendLine("    td, th { border: 1px solid #CBD5E1; padding: 10px; vertical-align: top; }")
        sb.appendLine("    details { border: 1px solid #E2E8F0; border-radius: 12px; padding: 12px 16px; margin: 12px 0; background: #F8FAFC; }")
        sb.appendLine("    summary { font-weight: 600; cursor: pointer; }")
        sb.appendLine("  </style>")
        sb.appendLine("</head>")
        sb.appendLine("<body>")
        sb.appendLine("  <h1 class=\"note-title\">$escapedTitle</h1>")
        sb.appendLine("  <div class=\"note-body\">")

        for (block in document.blocks) {
            when (block) {
                is ParagraphBlock -> {
                    sb.append("    <p>")
                    sb.append(serializeRichTextToHtml(block.content))
                    sb.appendLine("</p>")
                }
                is ImageBlock -> {
                    val src = Entities.escape(block.relativePath)
                    val altAttr = if (block.altText.isNotBlank()) " alt=\"${Entities.escape(block.altText)}\"" else ""
                    sb.appendLine("    <img src=\"$src\"$altAttr style=\"max-width:100%;border-radius:12px;\">")
                }
                is TableBlock -> {
                    sb.appendLine("    <table>")
                    if (block.columnWidthsDp.isNotEmpty()) {
                        sb.append("      <colgroup>")
                        for (width in block.columnWidthsDp) {
                            sb.append("<col style=\"width:${width}px\">")
                        }
                        sb.appendLine("</colgroup>")
                    }
                    sb.appendLine("      <tbody>")
                    for (row in block.rows) {
                        sb.append("        <tr>")
                        for (cell in row) {
                            sb.append("<td>")
                            sb.append(serializeRichTextToHtml(cell))
                            sb.append("</td>")
                        }
                        sb.appendLine("</tr>")
                    }
                    sb.appendLine("      </tbody>")
                    sb.appendLine("    </table>")
                }
                is ToggleBlock -> {
                    val openAttr = if (block.isExpanded) " open" else ""
                    sb.append("    <details$openAttr><summary>")
                    sb.append(serializeRichTextToHtml(block.summary))
                    sb.append("</summary>")
                    for (p in block.bodyBlocks) {
                        sb.append("<p>")
                        sb.append(serializeRichTextToHtml(p.content))
                        sb.append("</p>")
                    }
                    sb.appendLine("</details>")
                }
            }
        }

        sb.appendLine("  </div>")
        sb.appendLine("</body>")
        sb.append("</html>")
        return sb.toString()
    }

    private data class ActiveFormat(
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val textColorHex: String? = null,
        val highlightColorHex: String? = null,
        val fontSizeSp: Int? = null
    )

    private fun parseRichTextFromElement(element: Element): RichTextData {
        val textBuilder = StringBuilder()
        val spans = mutableListOf<StyleSpanRange>()
        val mathSpans = mutableListOf<MathSpanRange>()

        fun walkNodes(nodes: List<Node>, inherited: ActiveFormat) {
            for (node in nodes) {
                when (node) {
                    is TextNode -> {
                        val raw = node.wholeText
                        // Also support inline $...$ LaTeX notation inside text nodes
                        var lastIdx = 0
                        for (match in INLINE_DOLLAR_MATH_REGEX.findAll(raw)) {
                            if (match.range.first > lastIdx) {
                                val chunk = raw.substring(lastIdx, match.range.first)
                                appendFormattedChunk(chunk, inherited, textBuilder, spans)
                            }
                            val tex = match.groupValues[1].trim()
                            val start = textBuilder.length
                            textBuilder.append(MATH_OBJECT_CHAR)
                            val end = textBuilder.length
                            mathSpans.add(
                                MathSpanRange(
                                    id = UUID.randomUUID().toString(),
                                    start = start,
                                    end = end,
                                    latex = tex,
                                    displayFallback = texToReadableFallback(tex)
                                )
                            )
                            lastIdx = match.range.last + 1
                        }
                        if (lastIdx < raw.length) {
                            val remaining = raw.substring(lastIdx)
                            appendFormattedChunk(remaining, inherited, textBuilder, spans)
                        }
                    }
                    is Element -> {
                        val tag = node.tagName().lowercase()
                        if (tag == "br") {
                            textBuilder.append("\n")
                        } else if (tag == "span" && node.hasClass("math")) {
                            val tex = node.attr("data-tex").ifBlank { node.text() }
                            val fallback = node.text().ifBlank { texToReadableFallback(tex) }
                            val start = textBuilder.length
                            textBuilder.append(MATH_OBJECT_CHAR)
                            val end = textBuilder.length
                            mathSpans.add(
                                MathSpanRange(
                                    id = UUID.randomUUID().toString(),
                                    start = start,
                                    end = end,
                                    latex = tex,
                                    displayFallback = fallback
                                )
                            )
                        } else {
                            val updatedFormat = mergeElementFormat(node, inherited)
                            walkNodes(node.childNodes(), updatedFormat)
                        }
                    }
                }
            }
        }

        walkNodes(element.childNodes(), ActiveFormat())
        return RichTextData(
            text = textBuilder.toString(),
            spans = normalizeSpans(spans, textBuilder.length),
            mathSpans = mathSpans
        )
    }

    private fun appendFormattedChunk(
        chunk: String,
        format: ActiveFormat,
        textBuilder: StringBuilder,
        spans: MutableList<StyleSpanRange>
    ) {
        if (chunk.isEmpty()) return
        val start = textBuilder.length
        textBuilder.append(chunk)
        val end = textBuilder.length
        val span = StyleSpanRange(
            start = start,
            end = end,
            isBold = format.isBold,
            isItalic = format.isItalic,
            isUnderline = format.isUnderline,
            textColorHex = format.textColorHex,
            highlightColorHex = format.highlightColorHex,
            fontSizeSp = format.fontSizeSp
        )
        if (!span.isEmptyFormat) {
            spans.add(span)
        }
    }

    private fun mergeElementFormat(el: Element, base: ActiveFormat): ActiveFormat {
        val tag = el.tagName().lowercase()
        var isBold = base.isBold || tag == "b" || tag == "strong"
        var isItalic = base.isItalic || tag == "i" || tag == "em"
        var isUnderline = base.isUnderline || tag == "u"
        var textColor = base.textColorHex
        var highlightColor = base.highlightColorHex
        var fontSize = base.fontSizeSp

        val styleAttr = el.attr("style")
        if (styleAttr.isNotBlank()) {
            val declarations = styleAttr.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            for (decl in declarations) {
                val parts = decl.split(":", limit = 2)
                if (parts.size != 2) continue
                val prop = parts[0].trim().lowercase()
                val value = parts[1].trim()
                when (prop) {
                    "font-weight" -> {
                        if (value.equals("bold", ignoreCase = true) || (value.toIntOrNull() ?: 400) >= 600) {
                            isBold = true
                        }
                    }
                    "font-style" -> {
                        if (value.equals("italic", ignoreCase = true)) {
                            isItalic = true
                        }
                    }
                    "text-decoration", "text-decoration-line" -> {
                        if (value.contains("underline", ignoreCase = true)) {
                            isUnderline = true
                        }
                    }
                    "color" -> {
                        textColor = value
                    }
                    "background-color", "background" -> {
                        highlightColor = value
                    }
                    "font-size" -> {
                        val px = value.lowercase().removeSuffix("px").removeSuffix("pt").removeSuffix("sp").trim().toFloatOrNull()
                        if (px != null) {
                            fontSize = px.toInt().coerceIn(10, 30)
                        }
                    }
                }
            }
        }

        return ActiveFormat(
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            textColorHex = textColor,
            highlightColorHex = highlightColor,
            fontSizeSp = fontSize
        )
    }

    private fun parseTableElement(tableEl: Element): TableBlock {
        val colElements = tableEl.select("colgroup col")
        val widths = mutableListOf<Int>()
        for (col in colElements) {
            val style = col.attr("style")
            val widthMatch = Regex("width\\s*:\\s*(\\d+)px", RegexOption.IGNORE_CASE).find(style)
            val w = widthMatch?.groupValues?.get(1)?.toIntOrNull() ?: 150
            widths.add(w.coerceIn(60, 320))
        }

        val rowElements = tableEl.select("tr")
        val parsedRows = mutableListOf<List<RichTextData>>()
        var maxCols = widths.size

        for (tr in rowElements) {
            val cells = tr.select("td, th")
            if (cells.isNotEmpty()) {
                val rowData = cells.map { parseRichTextFromElement(it) }
                parsedRows.add(rowData)
                if (rowData.size > maxCols) maxCols = rowData.size
            }
        }

        if (maxCols == 0) maxCols = 2
        while (widths.size < maxCols) {
            widths.add(150)
        }
        val normalizedRows = if (parsedRows.isEmpty()) {
            listOf(List(maxCols) { RichTextData() })
        } else {
            parsedRows.map { row ->
                if (row.size < maxCols) row + List(maxCols - row.size) { RichTextData() }
                else row.take(maxCols)
            }
        }

        return TableBlock(
            id = UUID.randomUUID().toString(),
            columnWidthsDp = widths.take(maxCols),
            rows = normalizedRows
        )
    }

    private fun parseDetailsElement(detailsEl: Element): ToggleBlock {
        val isOpen = detailsEl.hasAttr("open")
        val summaryEl = detailsEl.selectFirst("summary")
        val summaryRich = if (summaryEl != null) {
            parseRichTextFromElement(summaryEl)
        } else {
            RichTextData(text = "Toggle")
        }

        val paragraphs = mutableListOf<ParagraphBlock>()
        for (child in detailsEl.children()) {
            if (child.tagName().equals("summary", ignoreCase = true)) continue
            if (child.tagName().equals("p", ignoreCase = true) || child.tagName().equals("div", ignoreCase = true)) {
                paragraphs.add(
                    ParagraphBlock(
                        id = UUID.randomUUID().toString(),
                        content = parseRichTextFromElement(child)
                    )
                )
            }
        }

        if (paragraphs.isEmpty()) {
            val ownText = detailsEl.ownText()
            paragraphs.add(
                ParagraphBlock(
                    id = UUID.randomUUID().toString(),
                    content = RichTextData(text = ownText)
                )
            )
        }

        return ToggleBlock(
            id = UUID.randomUUID().toString(),
            isExpanded = isOpen,
            summary = summaryRich,
            bodyBlocks = paragraphs
        )
    }

    fun serializeRichTextToHtml(richText: RichTextData): String {
        val text = richText.text
        if (text.isEmpty()) return ""

        val mathByStart = richText.mathSpans.associateBy { it.start }
        val sb = StringBuilder()

        var i = 0
        while (i < text.length) {
            val mathSpan = mathByStart[i]
            if (mathSpan != null) {
                val escapedTex = Entities.escape(mathSpan.latex)
                val fallback = Entities.escape(mathSpan.displayFallback.ifBlank { texToReadableFallback(mathSpan.latex) })
                sb.append("<span class=\"math\" data-tex=\"$escapedTex\">$fallback</span>")
                i = mathSpan.end.coerceAtLeast(i + 1)
                continue
            }

            if (text[i] == '\n') {
                sb.append("<br>")
                i++
                continue
            }

            // Find contiguous run with identical effective style that doesn't hit a math span or newline
            val currentStyle = effectiveStyleAt(i, richText.spans)
            var runEnd = i + 1
            while (runEnd < text.length &&
                !mathByStart.containsKey(runEnd) &&
                text[runEnd] != '\n' &&
                effectiveStyleAt(runEnd, richText.spans) == currentStyle
            ) {
                runEnd++
            }

            val rawSegment = Entities.escape(text.substring(i, runEnd))
            val cssProps = mutableListOf<String>()
            currentStyle.textColorHex?.let { cssProps.add("color:$it") }
            currentStyle.highlightColorHex?.let { cssProps.add("background-color:$it") }
            if (currentStyle.isBold) cssProps.add("font-weight:bold")
            if (currentStyle.isItalic) cssProps.add("font-style:italic")
            if (currentStyle.isUnderline) cssProps.add("text-decoration:underline")
            currentStyle.fontSizeSp?.let { cssProps.add("font-size:${it}px") }

            if (cssProps.isEmpty()) {
                sb.append(rawSegment)
            } else {
                val styleStr = cssProps.joinToString(";") + ";"
                sb.append("<span style=\"$styleStr\">$rawSegment</span>")
            }

            i = runEnd
        }

        return sb.toString()
    }

    private fun effectiveStyleAt(index: Int, spans: List<StyleSpanRange>): ActiveFormat {
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var textColor: String? = null
        var highlightColor: String? = null
        var fontSize: Int? = null

        for (span in spans) {
            if (index >= span.start && index < span.end) {
                if (span.isBold) isBold = true
                if (span.isItalic) isItalic = true
                if (span.isUnderline) isUnderline = true
                if (span.textColorHex != null) textColor = span.textColorHex
                if (span.highlightColorHex != null) highlightColor = span.highlightColorHex
                if (span.fontSizeSp != null) fontSize = span.fontSizeSp
            }
        }

        return ActiveFormat(
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            textColorHex = textColor,
            highlightColorHex = highlightColor,
            fontSizeSp = fontSize
        )
    }

    fun normalizeSpans(spans: List<StyleSpanRange>, textLength: Int): List<StyleSpanRange> {
        if (spans.isEmpty() || textLength <= 0) return emptyList()
        val result = mutableListOf<StyleSpanRange>()
        var i = 0
        while (i < textLength) {
            val style = effectiveStyleAt(i, spans)
            var end = i + 1
            while (end < textLength && effectiveStyleAt(end, spans) == style) {
                end++
            }
            val merged = StyleSpanRange(
                start = i,
                end = end,
                isBold = style.isBold,
                isItalic = style.isItalic,
                isUnderline = style.isUnderline,
                textColorHex = style.textColorHex,
                highlightColorHex = style.highlightColorHex,
                fontSizeSp = style.fontSizeSp
            )
            if (!merged.isEmptyFormat) {
                result.add(merged)
            }
            i = end
        }
        return result
    }

    fun texToReadableFallback(tex: String): String {
        return tex
            .replace("\\frac{1}{2}", "½")
            .replace("\\frac{1}{4}", "¼")
            .replace("\\frac{3}{4}", "¾")
            .replace("\\cdot", "·")
            .replace("\\times", "×")
            .replace("\\div", "÷")
            .replace("\\pi", "π")
            .replace("\\theta", "θ")
            .replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\delta", "δ")
            .replace("\\lambda", "λ")
            .replace("\\sigma", "σ")
            .replace("\\omega", "ω")
            .replace("\\sum", "∑")
            .replace("\\int", "∫")
            .replace("\\sqrt", "√")
            .replace("\\leq", "≤")
            .replace("\\geq", "≥")
            .replace("\\neq", "≠")
            .replace("\\infty", "∞")
            .replace("\\rightarrow", "→")
            .replace("^2", "²")
            .replace("^3", "³")
    }
}
