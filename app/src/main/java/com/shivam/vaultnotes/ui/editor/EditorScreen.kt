package com.shivam.vaultnotes.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.shivam.vaultnotes.data.model.ActiveFormattingState
import com.shivam.vaultnotes.data.model.ImageBlock
import com.shivam.vaultnotes.data.model.NoteBlock
import com.shivam.vaultnotes.data.model.NoteDocument
import com.shivam.vaultnotes.data.model.ParagraphBlock
import com.shivam.vaultnotes.data.model.RichTextData
import com.shivam.vaultnotes.data.model.TableBlock
import com.shivam.vaultnotes.data.model.ToggleBlock
import com.shivam.vaultnotes.ui.crop.ImageCropScreen
import com.shivam.vaultnotes.ui.theme.LocalVaultPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class ActiveToolbarPopover {
    NONE,
    MATH_KEYBOARD,
    TEXT_COLOR,
    HIGHLIGHTER,
    FONT_SIZE
}

/**
 * Screen 2: Distraction-Free Floating Studio Editor.
 * - Row 1: Minimalist Studio Header (44.dp height, Back button, Live status pill `✓ Saved` / `Saving…`, Undo/Redo segmented pill up to 100 states)
 * - Row 2: Editorial Title Input (`26.sp` Bold, `-0.3.sp` tracking, updates `<h1 class="note-title">` & renames `.html` file)
 * - Row 3: Scrollable Block-Based Rich Body (`ParagraphBlock`, `ImageBlock`, `TableBlock`, `ToggleBlock`)
 * - Row 4: Floating Pill Formatting Toolbar (`46.dp` height, 11 tools in exact order)
 * - 1-Second Change-Driven Auto-Save + Immediate Lifecycle Flush (`ON_PAUSE`, `ON_STOP`, Back press)
 */
@Composable
fun EditorScreen(
    initialNotePath: String,
    initialDocument: NoteDocument,
    onResolveAssetUri: suspend (String) -> Uri?,
    onSaveCroppedBitmap: suspend (android.graphics.Bitmap) -> Result<String>,
    onAtomicSaveNote: suspend (String, NoteDocument) -> Result<String>,
    onNavigateBack: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var currentNotePath by remember(initialNotePath) { mutableStateOf(initialNotePath) }
    var document by remember(initialDocument.id) { mutableStateOf(initialDocument) }

    // Session Undo / Redo stacks (up to 100 states)
    val undoStack = remember(initialDocument.id) { mutableStateListOf<NoteDocument>() }
    val redoStack = remember(initialDocument.id) { mutableStateListOf<NoteDocument>() }

    // Auto-Save state tracking
    var documentRevision by remember { mutableIntStateOf(0) }
    var isDirty by remember { mutableStateOf(false) }
    var isSavingNow by remember { mutableStateOf(false) }

    // Active focus & formatting state
    var focusedTarget by remember {
        val firstParaId = initialDocument.blocks.filterIsInstance<ParagraphBlock>().firstOrNull()?.id ?: ""
        mutableStateOf<EditorFocusTarget>(EditorFocusTarget.Paragraph(firstParaId))
    }
    var currentSelection by remember { mutableStateOf(TextRange.Zero) }
    var activeFormatting by remember { mutableStateOf(ActiveFormattingState()) }
    var externalSelectionTrigger by remember { mutableStateOf<Pair<Int, Long>?>(null) }

    // Toolbar Popovers & BottomSheets
    var activePopover by remember { mutableStateOf(ActiveToolbarPopover.NONE) }
    var showTableSheet by remember { mutableStateOf(false) }
    var showCustomColorPicker by remember { mutableStateOf(false) }
    var isCustomPickerForHighlighter by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val primaryParagraphFocusRequester = remember { FocusRequester() }

    fun pushUndoSnapshot(beforeChange: NoteDocument) {
        if (undoStack.lastOrNull() != beforeChange) {
            undoStack.add(beforeChange)
            if (undoStack.size > 100) {
                undoStack.removeAt(0)
            }
            redoStack.clear()
        }
    }

    fun updateDocument(newDoc: NoteDocument, recordUndo: Boolean = true) {
        if (newDoc == document) return
        if (recordUndo) {
            pushUndoSnapshot(document)
        }
        document = newDoc
        isDirty = true
        documentRevision++
    }

    // 1-Second Change-Driven Auto-Save Engine
    LaunchedEffect(documentRevision) {
        if (!isDirty || documentRevision == 0) return@LaunchedEffect
        delay(1000L)
        isSavingNow = true
        val saveRes = onAtomicSaveNote(currentNotePath, document)
        saveRes.onSuccess { updatedPath ->
            currentNotePath = updatedPath
            isDirty = false
        }
        isSavingNow = false
    }

    // Immediate Lifecycle Flush on ON_PAUSE / ON_STOP
    val currentDocState by rememberUpdatedState(document)
    val currentPathState by rememberUpdatedState(currentNotePath)
    val currentDirtyState by rememberUpdatedState(isDirty)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if ((event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) && currentDirtyState) {
                scope.launch {
                    onAtomicSaveNote(currentPathState, currentDocState).onSuccess {
                        currentNotePath = it
                        isDirty = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun flushAndNavigateBack() {
        scope.launch {
            if (isDirty) {
                isSavingNow = true
                onAtomicSaveNote(currentNotePath, document)
                isDirty = false
                isSavingNow = false
            }
            onNavigateBack()
        }
    }

    BackHandler(enabled = true) {
        when {
            pendingCropUri != null -> pendingCropUri = null
            activePopover != ActiveToolbarPopover.NONE -> activePopover = ActiveToolbarPopover.NONE
            else -> flushAndNavigateBack()
        }
    }

    // Zero-permission Android Photo Picker (`ActivityResultContracts.PickVisualMedia`)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropUri = uri
        }
    }

    // Helper to modify the currently focused RichTextData (Paragraph, TableCell, ToggleSummary, or ToggleBodyParagraph)
    fun transformFocusedRichText(transform: (RichTextData) -> Pair<RichTextData, Int?>) {
        val target = focusedTarget
        val updatedBlocks = document.blocks.map { block ->
            when {
                target is EditorFocusTarget.Paragraph && block is ParagraphBlock && block.id == target.blockId -> {
                    val (newRich, newCursor) = transform(block.content)
                    if (newCursor != null) {
                        currentSelection = TextRange(newCursor)
                        externalSelectionTrigger = newCursor to System.nanoTime()
                    }
                    block.copy(content = newRich)
                }
                target is EditorFocusTarget.TableCell && block is TableBlock && block.id == target.blockId -> {
                    val newRows = block.rows.mapIndexed { rIdx, row ->
                        if (rIdx == target.rowIndex) {
                            row.mapIndexed { cIdx, cell ->
                                if (cIdx == target.colIndex) {
                                    val (newRich, newCursor) = transform(cell)
                                    if (newCursor != null) {
                                        currentSelection = TextRange(newCursor)
                                        externalSelectionTrigger = newCursor to System.nanoTime()
                                    }
                                    newRich
                                } else cell
                            }
                        } else row
                    }
                    block.copy(rows = newRows)
                }
                target is EditorFocusTarget.ToggleSummary && block is ToggleBlock && block.id == target.blockId -> {
                    val (newRich, newCursor) = transform(block.summary)
                    if (newCursor != null) {
                        currentSelection = TextRange(newCursor)
                        externalSelectionTrigger = newCursor to System.nanoTime()
                    }
                    block.copy(summary = newRich)
                }
                target is EditorFocusTarget.ToggleBodyParagraph && block is ToggleBlock && block.id == target.blockId -> {
                    val newBody = block.bodyBlocks.map { para ->
                        if (para.id == target.paragraphId) {
                            val (newRich, newCursor) = transform(para.content)
                            if (newCursor != null) {
                                currentSelection = TextRange(newCursor)
                                externalSelectionTrigger = newCursor to System.nanoTime()
                            }
                            para.copy(content = newRich)
                        } else para
                    }
                    block.copy(bodyBlocks = newBody)
                }
                else -> block
            }
        }
        updateDocument(document.copy(blocks = updatedBlocks))
    }

    fun insertBlockAfterFocused(newBlock: NoteBlock) {
        val activeBlockId = when (val t = focusedTarget) {
            is EditorFocusTarget.Paragraph -> t.blockId
            is EditorFocusTarget.TableCell -> t.blockId
            is EditorFocusTarget.ToggleSummary -> t.blockId
            is EditorFocusTarget.ToggleBodyParagraph -> t.blockId
        }
        val idx = document.blocks.indexOfFirst { it.id == activeBlockId }
        val mutable = document.blocks.toMutableList()
        val trailingParagraph = ParagraphBlock()
        if (idx >= 0) {
            mutable.add(idx + 1, newBlock)
            // Ensure a trailing paragraph exists so user can always type below inserted blocks
            if (idx + 2 >= mutable.size || mutable[idx + 2] !is ParagraphBlock) {
                mutable.add(idx + 2, trailingParagraph)
            }
        } else {
            mutable.add(newBlock)
            mutable.add(trailingParagraph)
        }
        updateDocument(document.copy(blocks = mutable))
    }

    // Full-screen In-App Image Cropper overlay when importing an image
    val cropUri = pendingCropUri
    if (cropUri != null) {
        ImageCropScreen(
            sourceUri = cropUri,
            onCancel = { pendingCropUri = null },
            onCropConfirmed = { croppedBitmap ->
                pendingCropUri = null
                scope.launch {
                    val savedResult = onSaveCroppedBitmap(croppedBitmap)
                    savedResult.onSuccess { relativeAssetPath ->
                        insertBlockAfterFocused(
                            ImageBlock(
                                id = UUID.randomUUID().toString(),
                                relativePath = relativeAssetPath
                            )
                        )
                    }
                }
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.backgroundCanvas)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
    ) {
        // Row 1 — Minimalist Studio Header (44.dp height, 12.dp horizontal padding)
        StudioHeaderBar(
            isDirtyOrSaving = isDirty || isSavingNow,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            onBackClick = { flushAndNavigateBack() },
            onUndoClick = {
                if (undoStack.isNotEmpty()) {
                    val previous = undoStack.removeAt(undoStack.lastIndex)
                    redoStack.add(document)
                    document = previous
                    isDirty = true
                    documentRevision++
                }
            },
            onRedoClick = {
                if (redoStack.isNotEmpty()) {
                    val next = redoStack.removeAt(redoStack.lastIndex)
                    undoStack.add(document)
                    document = next
                    isDirty = true
                    documentRevision++
                }
            }
        )

        // Row 2 — Editorial Title Input (20.dp horizontal padding, 12.dp top / 8.dp bottom)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 8.dp)
        ) {
            BasicTextField(
                value = document.title,
                onValueChange = { newTitle ->
                    updateDocument(document.copy(title = newTitle))
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = palette.textPrimary,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    letterSpacing = (-0.3).sp,
                    fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(palette.activeToolIndigo),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_title_input"),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (document.title.isEmpty()) {
                            Text(
                                text = "Note title",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    color = palette.textPrimary.copy(alpha = 0.35f),
                                    fontSize = 26.sp,
                                    lineHeight = 32.sp,
                                    letterSpacing = (-0.3).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(palette.hairlineBorder)
            )
        }

        // Row 3 — Scrollable Block-Based Rich Body (Modifier.weight(1f), 20.dp horizontal padding)
        val firstParagraphId = document.blocks.filterIsInstance<ParagraphBlock>().firstOrNull()?.id

        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("editor_blocks_list")
        ) {
            items(
                items = document.blocks,
                key = { it.id }
            ) { block ->
                when (block) {
                    is ParagraphBlock -> {
                        val isFocusedPara = (focusedTarget as? EditorFocusTarget.Paragraph)?.blockId == block.id
                        RichTextBlockEditor(
                            richTextData = block.content,
                            placeholder = if (block.id == firstParagraphId && document.blocks.size == 1) {
                                "Start writing, add formulas (Σ), tables (⊞), or toggles (▸)…"
                            } else {
                                ""
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 25.sp
                            ),
                            activeFormatting = activeFormatting,
                            focusRequester = if (block.id == firstParagraphId) primaryParagraphFocusRequester else null,
                            externalSelectionTrigger = if (isFocusedPara) externalSelectionTrigger else null,
                            onFocusAcquired = { sel ->
                                focusedTarget = EditorFocusTarget.Paragraph(block.id)
                                currentSelection = sel
                                activeFormatting = RichTextSpanEngine.inspectFormattingAtSelection(block.content, sel)
                            },
                            onSelectionChanged = { sel, fmt ->
                                focusedTarget = EditorFocusTarget.Paragraph(block.id)
                                currentSelection = sel
                                activeFormatting = fmt
                            },
                            onRichTextChanged = { updatedRich, sel ->
                                focusedTarget = EditorFocusTarget.Paragraph(block.id)
                                currentSelection = sel
                                val newBlocks = document.blocks.map {
                                    if (it is ParagraphBlock && it.id == block.id) it.copy(content = updatedRich) else it
                                }
                                updateDocument(document.copy(blocks = newBlocks))
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    is ImageBlock -> {
                        VaultImageBlockItem(
                            block = block,
                            onResolveAssetUri = onResolveAssetUri,
                            onRemoveImage = {
                                val remaining = document.blocks.filterNot { it.id == block.id }
                                    .ifEmpty { listOf(ParagraphBlock()) }
                                updateDocument(document.copy(blocks = remaining))
                            }
                        )
                    }

                    is TableBlock -> {
                        val tableFocus = focusedTarget as? EditorFocusTarget.TableCell
                        val isThisTableFocused = tableFocus?.blockId == block.id
                        val focusedCellPair = if (isThisTableFocused && tableFocus != null) {
                            tableFocus.rowIndex to tableFocus.colIndex
                        } else null

                        TableBlockView(
                            block = block,
                            isTableFocused = isThisTableFocused,
                            focusedCell = focusedCellPair,
                            activeFormatting = activeFormatting,
                            externalSelectionTrigger = if (isThisTableFocused) externalSelectionTrigger else null,
                            onCellFocus = { rIdx, cIdx, sel ->
                                focusedTarget = EditorFocusTarget.TableCell(block.id, rIdx, cIdx)
                                currentSelection = sel
                                val cellData = block.rows.getOrNull(rIdx)?.getOrNull(cIdx) ?: RichTextData()
                                activeFormatting = RichTextSpanEngine.inspectFormattingAtSelection(cellData, sel)
                            },
                            onCellSelectionChanged = { rIdx, cIdx, sel, fmt ->
                                focusedTarget = EditorFocusTarget.TableCell(block.id, rIdx, cIdx)
                                currentSelection = sel
                                activeFormatting = fmt
                            },
                            onCellRichTextChanged = { rIdx, cIdx, updatedCell, sel ->
                                focusedTarget = EditorFocusTarget.TableCell(block.id, rIdx, cIdx)
                                currentSelection = sel
                                val updatedRows = block.rows.mapIndexed { r, row ->
                                    if (r == rIdx) {
                                        row.mapIndexed { c, existing -> if (c == cIdx) updatedCell else existing }
                                    } else row
                                }
                                val newBlocks = document.blocks.map {
                                    if (it is TableBlock && it.id == block.id) it.copy(rows = updatedRows) else it
                                }
                                updateDocument(document.copy(blocks = newBlocks))
                            },
                            onColumnWidthChanged = { colIdx, newWidthDp ->
                                val updatedWidths = block.columnWidthsDp.mapIndexed { idx, w ->
                                    if (idx == colIdx) newWidthDp else w
                                }
                                val newBlocks = document.blocks.map {
                                    if (it is TableBlock && it.id == block.id) it.copy(columnWidthsDp = updatedWidths) else it
                                }
                                updateDocument(document.copy(blocks = newBlocks), recordUndo = false)
                            },
                            onAddRow = {
                                val colCount = block.columnWidthsDp.size.coerceAtLeast(1)
                                val updatedRows = block.rows + listOf(List(colCount) { RichTextData() })
                                val newBlocks = document.blocks.map {
                                    if (it is TableBlock && it.id == block.id) it.copy(rows = updatedRows) else it
                                }
                                updateDocument(document.copy(blocks = newBlocks))
                            },
                            onRemoveRow = {
                                if (block.rows.size > 1) {
                                    val updatedRows = block.rows.dropLast(1)
                                    val newBlocks = document.blocks.map {
                                        if (it is TableBlock && it.id == block.id) it.copy(rows = updatedRows) else it
                                    }
                                    updateDocument(document.copy(blocks = newBlocks))
                                }
                            },
                            onAddColumn = {
                                val updatedWidths = block.columnWidthsDp + 150
                                val updatedRows = block.rows.map { it + RichTextData() }
                                val newBlocks = document.blocks.map {
                                    if (it is TableBlock && it.id == block.id) {
                                        it.copy(columnWidthsDp = updatedWidths, rows = updatedRows)
                                    } else it
                                }
                                updateDocument(document.copy(blocks = newBlocks))
                            },
                            onRemoveColumn = {
                                if (block.columnWidthsDp.size > 1) {
                                    val updatedWidths = block.columnWidthsDp.dropLast(1)
                                    val updatedRows = block.rows.map { it.dropLast(1) }
                                    val newBlocks = document.blocks.map {
                                        if (it is TableBlock && it.id == block.id) {
                                            it.copy(columnWidthsDp = updatedWidths, rows = updatedRows)
                                        } else it
                                    }
                                    updateDocument(document.copy(blocks = newBlocks))
                                }
                            },
                            onDeleteTable = {
                                val remaining = document.blocks.filterNot { it.id == block.id }
                                    .ifEmpty { listOf(ParagraphBlock()) }
                                updateDocument(document.copy(blocks = remaining))
                            }
                        )
                    }

                    is ToggleBlock -> {
                        val isSummaryFocused = (focusedTarget as? EditorFocusTarget.ToggleSummary)?.blockId == block.id
                        val bodyFocus = focusedTarget as? EditorFocusTarget.ToggleBodyParagraph
                        val focusedBodyParaId = if (bodyFocus?.blockId == block.id) bodyFocus.paragraphId else null

                        ToggleBlockView(
                            block = block,
                            isSummaryFocused = isSummaryFocused,
                            focusedBodyParagraphId = focusedBodyParaId,
                            activeFormatting = activeFormatting,
                            externalSelectionTrigger = if (isSummaryFocused || focusedBodyParaId != null) externalSelectionTrigger else null,
                            onToggleExpanded = {
                                val newBlocks = document.blocks.map {
                                    if (it is ToggleBlock && it.id == block.id) it.copy(isExpanded = !it.isExpanded) else it
                                }
                                updateDocument(document.copy(blocks = newBlocks))
                            },
                            onSummaryFocus = { sel ->
                                focusedTarget = EditorFocusTarget.ToggleSummary(block.id)
                                currentSelection = sel
                                activeFormatting = RichTextSpanEngine.inspectFormattingAtSelection(block.summary, sel)
                            },
                            onSummarySelectionChanged = { sel, fmt ->
                                focusedTarget = EditorFocusTarget.ToggleSummary(block.id)
                                currentSelection = sel
                                activeFormatting = fmt
                            },
                            onSummaryChanged = { updatedSummary, sel ->
                                focusedTarget = EditorFocusTarget.ToggleSummary(block.id)
                                currentSelection = sel
                                val newBlocks = document.blocks.map {
                                    if (it is ToggleBlock && it.id == block.id) it.copy(summary = updatedSummary) else it
                                }
                                updateDocument(document.copy(blocks = newBlocks))
                            },
                            onBodyParagraphFocus = { paraId, sel ->
                                focusedTarget = EditorFocusTarget.ToggleBodyParagraph(block.id, paraId)
                                currentSelection = sel
                                val para = block.bodyBlocks.find { it.id == paraId }?.content ?: RichTextData()
                                activeFormatting = RichTextSpanEngine.inspectFormattingAtSelection(para, sel)
                            },
                            onBodyParagraphSelectionChanged = { paraId, sel, fmt ->
                                focusedTarget = EditorFocusTarget.ToggleBodyParagraph(block.id, paraId)
                                currentSelection = sel
                                activeFormatting = fmt
                            },
                            onBodyParagraphChanged = { paraId, updatedPara, sel ->
                                focusedTarget = EditorFocusTarget.ToggleBodyParagraph(block.id, paraId)
                                currentSelection = sel
                                val updatedBody = block.bodyBlocks.map {
                                    if (it.id == paraId) it.copy(content = updatedPara) else it
                                }
                                val newBlocks = document.blocks.map {
                                    if (it is ToggleBlock && it.id == block.id) it.copy(bodyBlocks = updatedBody) else it
                                }
                                updateDocument(document.copy(blocks = newBlocks))
                            },
                            onDeleteToggle = {
                                val remaining = document.blocks.filterNot { it.id == block.id }
                                    .ifEmpty { listOf(ParagraphBlock()) }
                                updateDocument(document.copy(blocks = remaining))
                            }
                        )
                    }
                }
            }
        }

        // Floating Popover Strips 8.dp above the Formatting Toolbar (Colour, Highlighter, Font Size)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            when (activePopover) {
                ActiveToolbarPopover.TEXT_COLOR -> {
                    FloatingColorSwatchStrip(
                        presets = TEXT_COLOR_PRESETS,
                        selectedCssColor = activeFormatting.textColorHex,
                        onSelectColor = { newColorHex ->
                            activeFormatting = activeFormatting.copy(textColorHex = newColorHex)
                            if (!currentSelection.collapsed) {
                                transformFocusedRichText { rich ->
                                    RichTextSpanEngine.applyFormattingToSelection(rich, currentSelection) {
                                        it.copy(textColorHex = newColorHex)
                                    } to null
                                }
                            }
                        },
                        onOpenCustomPicker = {
                            isCustomPickerForHighlighter = false
                            showCustomColorPicker = true
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                ActiveToolbarPopover.HIGHLIGHTER -> {
                    FloatingColorSwatchStrip(
                        presets = HIGHLIGHTER_PRESETS,
                        selectedCssColor = activeFormatting.highlightColorHex,
                        onSelectColor = { newHighlightCss ->
                            activeFormatting = activeFormatting.copy(highlightColorHex = newHighlightCss)
                            if (!currentSelection.collapsed) {
                                transformFocusedRichText { rich ->
                                    RichTextSpanEngine.applyFormattingToSelection(rich, currentSelection) {
                                        it.copy(highlightColorHex = newHighlightCss)
                                    } to null
                                }
                            }
                        },
                        onOpenCustomPicker = {
                            isCustomPickerForHighlighter = true
                            showCustomColorPicker = true
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                ActiveToolbarPopover.FONT_SIZE -> {
                    FloatingFontSizePill(
                        currentSizeSp = activeFormatting.fontSizeSp,
                        onSizeChange = { newSizeSp ->
                            activeFormatting = activeFormatting.copy(fontSizeSp = newSizeSp)
                            if (!currentSelection.collapsed) {
                                transformFocusedRichText { rich ->
                                    RichTextSpanEngine.applyFormattingToSelection(rich, currentSelection) {
                                        it.copy(fontSizeSp = newSizeSp)
                                    } to null
                                }
                            }
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                else -> {}
            }
        }

        // Row 4 — Floating Pill Formatting Toolbar (46.dp height, 12.dp horizontal margin, 8.dp bottom margin)
        FloatingPillFormattingToolbar(
            activeFormatting = activeFormatting,
            activePopover = activePopover,
            onToolTextClick = {
                activePopover = ActiveToolbarPopover.NONE
                try {
                    primaryParagraphFocusRequester.requestFocus()
                } catch (_: Exception) {
                }
                keyboardController?.show()
            },
            onToolSumClick = {
                activePopover = if (activePopover == ActiveToolbarPopover.MATH_KEYBOARD) {
                    ActiveToolbarPopover.NONE
                } else {
                    keyboardController?.hide()
                    ActiveToolbarPopover.MATH_KEYBOARD
                }
            },
            onToolImportImageClick = {
                activePopover = ActiveToolbarPopover.NONE
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onToolTableClick = {
                activePopover = ActiveToolbarPopover.NONE
                showTableSheet = true
            },
            onToolToggleClick = {
                activePopover = ActiveToolbarPopover.NONE
                insertBlockAfterFocused(
                    ToggleBlock(
                        id = UUID.randomUUID().toString(),
                        isExpanded = true,
                        summary = RichTextData(text = "Section detail"),
                        bodyBlocks = listOf(ParagraphBlock())
                    )
                )
            },
            onToolColourClick = {
                activePopover = if (activePopover == ActiveToolbarPopover.TEXT_COLOR) {
                    ActiveToolbarPopover.NONE
                } else {
                    ActiveToolbarPopover.TEXT_COLOR
                }
            },
            onToolBoldClick = {
                val nextBold = !activeFormatting.isBold
                activeFormatting = activeFormatting.copy(isBold = nextBold)
                if (!currentSelection.collapsed) {
                    transformFocusedRichText { rich ->
                        RichTextSpanEngine.applyFormattingToSelection(rich, currentSelection) {
                            it.copy(isBold = nextBold)
                        } to null
                    }
                }
            },
            onToolItalicClick = {
                val nextItalic = !activeFormatting.isItalic
                activeFormatting = activeFormatting.copy(isItalic = nextItalic)
                if (!currentSelection.collapsed) {
                    transformFocusedRichText { rich ->
                        RichTextSpanEngine.applyFormattingToSelection(rich, currentSelection) {
                            it.copy(isItalic = nextItalic)
                        } to null
                    }
                }
            },
            onToolUnderlineClick = {
                val nextUnderline = !activeFormatting.isUnderline
                activeFormatting = activeFormatting.copy(isUnderline = nextUnderline)
                if (!currentSelection.collapsed) {
                    transformFocusedRichText { rich ->
                        RichTextSpanEngine.applyFormattingToSelection(rich, currentSelection) {
                            it.copy(isUnderline = nextUnderline)
                        } to null
                    }
                }
            },
            onToolHighlighterClick = {
                activePopover = if (activePopover == ActiveToolbarPopover.HIGHLIGHTER) {
                    ActiveToolbarPopover.NONE
                } else {
                    ActiveToolbarPopover.HIGHLIGHTER
                }
            },
            onToolFontSizeClick = {
                activePopover = if (activePopover == ActiveToolbarPopover.FONT_SIZE) {
                    ActiveToolbarPopover.NONE
                } else {
                    ActiveToolbarPopover.FONT_SIZE
                }
            }
        )

        // Custom Mathematical Keyboard Panel (220.dp height) when Sum (Σ) is active
        AnimatedVisibility(
            visible = activePopover == ActiveToolbarPopover.MATH_KEYBOARD,
            enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)),
            exit = fadeOut(spring(dampingRatio = 0.8f, stiffness = 400f))
        ) {
            MathKeyboardPanel(
                onInsertLatex = { latex ->
                    transformFocusedRichText { rich ->
                        val (updatedRich, nextCursorPos) = RichTextSpanEngine.insertLatexMathAtCursor(
                            richText = rich,
                            cursorPosition = currentSelection.max,
                            latex = latex
                        )
                        updatedRich to nextCursorPos
                    }
                },
                onClose = { activePopover = ActiveToolbarPopover.NONE }
            )
        }
    }

    // Table Row/Col Picker ModalBottomSheet
    if (showTableSheet) {
        InsertTableBottomSheet(
            onDismiss = { showTableSheet = false },
            onInsertTable = { rows, cols ->
                val defaultColWidth = (300 / cols).coerceIn(110, 180)
                insertBlockAfterFocused(
                    TableBlock(
                        id = UUID.randomUUID().toString(),
                        columnWidthsDp = List(cols) { defaultColWidth },
                        rows = List(rows) { List(cols) { RichTextData() } }
                    )
                )
            }
        )
    }

    // Custom HSV / Hex Picker ModalBottomSheet
    if (showCustomColorPicker) {
        CustomColorPickerBottomSheet(
            isHighlighter = isCustomPickerForHighlighter,
            onDismiss = { showCustomColorPicker = false },
            onColorSelected = { cssValue ->
                if (isCustomPickerForHighlighter) {
                    activeFormatting = activeFormatting.copy(highlightColorHex = cssValue)
                    if (!currentSelection.collapsed) {
                        transformFocusedRichText { rich ->
                            RichTextSpanEngine.applyFormattingToSelection(rich, currentSelection) {
                                it.copy(highlightColorHex = cssValue)
                            } to null
                        }
                    }
                } else {
                    activeFormatting = activeFormatting.copy(textColorHex = cssValue)
                    if (!currentSelection.collapsed) {
                        transformFocusedRichText { rich ->
                            RichTextSpanEngine.applyFormattingToSelection(rich, currentSelection) {
                                it.copy(textColorHex = cssValue)
                            } to null
                        }
                    }
                }
            }
        )
    }
}

/**
 * Row 1 — Minimalist Studio Header (44.dp height, 12.dp horizontal padding)
 */
@Composable
private fun StudioHeaderBar(
    isDirtyOrSaving: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onBackClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val pulseTransition = rememberInfiniteTransition(label = "savingPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "savingAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Back button (←) returning to Home (immediately flushes auto-save)
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(40.dp)
                .testTag("editor_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back to Vault",
                tint = palette.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Center: Subtle live status pill (`✓ Saved` in muted 12.sp, pulsing softly to `Saving…` while dirty)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = palette.softTintedSurface,
            border = BorderStroke(0.8.dp, palette.hairlineBorder)
        ) {
            Text(
                text = if (isDirtyOrSaving) "Saving…" else "✓ Saved",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    color = if (isDirtyOrSaving) palette.activeToolIndigo else palette.textSecondary
                ),
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .alpha(if (isDirtyOrSaving) pulseAlpha else 1f)
                    .testTag("editor_save_status")
            )
        }

        // Right: Segmented pill (`RoundedCornerShape(10.dp)`, `Soft Tinted Surface`) containing Undo (`↶`) and Redo (`↷`)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = palette.softTintedSurface,
            border = BorderStroke(0.8.dp, palette.hairlineBorder)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                        .clickable(enabled = canUndo, onClick = onUndoClick)
                        .testTag("editor_undo_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = "Undo",
                        tint = palette.textPrimary.copy(alpha = if (canUndo) 1f else 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .width(0.8.dp)
                        .height(20.dp)
                        .background(palette.hairlineBorder)
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                        .clickable(enabled = canRedo, onClick = onRedoClick)
                        .testTag("editor_redo_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Redo,
                        contentDescription = "Redo",
                        tint = palette.textPrimary.copy(alpha = if (canRedo) 1f else 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * ImageBlock item: Displays cropped image from `vault/assets/` (`RoundedCornerShape(12.dp)`,
 * max width `100%`, `0.8.dp` border) with a clean floating remove (`×`) chip on tap.
 */
@Composable
private fun VaultImageBlockItem(
    block: ImageBlock,
    onResolveAssetUri: suspend (String) -> Uri?,
    onRemoveImage: () -> Unit
) {
    val palette = LocalVaultPalette.current
    var resolvedUri by remember(block.relativePath) { mutableStateOf<Uri?>(null) }
    var showRemoveOverlay by remember { mutableStateOf(true) }

    LaunchedEffect(block.relativePath) {
        resolvedUri = onResolveAssetUri(block.relativePath)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(0.8.dp, palette.hairlineBorder, RoundedCornerShape(12.dp))
            .background(palette.softTintedSurface)
            .clickable { showRemoveOverlay = !showRemoveOverlay }
            .testTag("image_block_${block.id}")
    ) {
        if (resolvedUri != null) {
            AsyncImage(
                model = resolvedUri,
                contentDescription = block.altText.ifBlank { "Note image" },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 380.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = block.relativePath,
                    style = MaterialTheme.typography.labelMedium.copy(color = palette.textSecondary)
                )
            }
        }

        if (showRemoveOverlay) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.3f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemoveImage)
                    .testTag("remove_image_${block.id}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove image",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Row 4 — Floating Pill Formatting Toolbar (`46.dp` height, `12.dp` horizontal margin, `8.dp` bottom margin).
 * Contains 11 tools in exact order:
 * 1. Text (`Tt`)
 * 2. Sum (`Σ`)
 * 3. Import (`🖼️`)
 * 4. Table (`⊞`)
 * 5. Toggle (`▸`)
 * 6. Colour (`A`)
 * 7. Bold (`B`)
 * 8. Italic (`I`)
 * 9. Underline (`U`)
 * 10. Highlighter (`🖍️`)
 * 11. Font Size (`16px ▾`)
 */
@Composable
fun FloatingPillFormattingToolbar(
    activeFormatting: ActiveFormattingState,
    activePopover: ActiveToolbarPopover,
    onToolTextClick: () -> Unit,
    onToolSumClick: () -> Unit,
    onToolImportImageClick: () -> Unit,
    onToolTableClick: () -> Unit,
    onToolToggleClick: () -> Unit,
    onToolColourClick: () -> Unit,
    onToolBoldClick: () -> Unit,
    onToolItalicClick: () -> Unit,
    onToolUnderlineClick: () -> Unit,
    onToolHighlighterClick: () -> Unit,
    onToolFontSizeClick: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val scrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.softTintedSurface,
        border = BorderStroke(0.8.dp, palette.hairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .height(46.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .testTag("floating_formatting_toolbar")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Text (Tt)
                ToolbarIconChip(
                    icon = Icons.Outlined.TextFields,
                    label = "Tt",
                    isActive = false,
                    onClick = onToolTextClick,
                    testTag = "tool_1_text"
                )

                // 2. Sum (Σ)
                ToolbarIconChip(
                    icon = Icons.Outlined.Functions,
                    label = "Σ",
                    isActive = activePopover == ActiveToolbarPopover.MATH_KEYBOARD,
                    onClick = onToolSumClick,
                    testTag = "tool_2_sum"
                )

                // 3. Import (🖼️)
                ToolbarIconChip(
                    icon = Icons.Outlined.Image,
                    label = "Import",
                    isActive = false,
                    onClick = onToolImportImageClick,
                    testTag = "tool_3_import"
                )

                // 4. Table (⊞)
                ToolbarIconChip(
                    icon = Icons.Outlined.TableChart,
                    label = "Table",
                    isActive = false,
                    onClick = onToolTableClick,
                    testTag = "tool_4_table"
                )

                // 5. Toggle (▸)
                ToolbarIconChip(
                    icon = Icons.Outlined.PlayArrow,
                    label = "Toggle",
                    isActive = false,
                    onClick = onToolToggleClick,
                    testTag = "tool_5_toggle"
                )

                // 6. Colour (A)
                ToolbarIconChip(
                    icon = Icons.Outlined.FormatColorText,
                    label = "Colour",
                    isActive = activePopover == ActiveToolbarPopover.TEXT_COLOR || activeFormatting.textColorHex != null,
                    accentDotColor = RichTextSpanEngine.parseCssColor(activeFormatting.textColorHex),
                    onClick = onToolColourClick,
                    testTag = "tool_6_colour"
                )

                // 7. Bold (B)
                ToolbarIconChip(
                    icon = Icons.Outlined.FormatBold,
                    label = "Bold",
                    isActive = activeFormatting.isBold,
                    onClick = onToolBoldClick,
                    testTag = "tool_7_bold"
                )

                // 8. Italic (I)
                ToolbarIconChip(
                    icon = Icons.Outlined.FormatItalic,
                    label = "Italic",
                    isActive = activeFormatting.isItalic,
                    onClick = onToolItalicClick,
                    testTag = "tool_8_italic"
                )

                // 9. Underline (U)
                ToolbarIconChip(
                    icon = Icons.Outlined.FormatUnderlined,
                    label = "Underline",
                    isActive = activeFormatting.isUnderline,
                    onClick = onToolUnderlineClick,
                    testTag = "tool_9_underline"
                )

                // 10. Highlighter (🖍️)
                ToolbarIconChip(
                    icon = Icons.Outlined.BorderColor,
                    label = "Highlight",
                    isActive = activePopover == ActiveToolbarPopover.HIGHLIGHTER || activeFormatting.highlightColorHex != null,
                    accentDotColor = RichTextSpanEngine.parseCssColor(activeFormatting.highlightColorHex),
                    onClick = onToolHighlighterClick,
                    testTag = "tool_10_highlighter"
                )

                // 11. Font Size (16px ▾)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (activePopover == ActiveToolbarPopover.FONT_SIZE) {
                        palette.activeToolIndigo.copy(alpha = 0.15f)
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onToolFontSizeClick)
                        .testTag("tool_11_fontsize")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${activeFormatting.fontSizeSp}px ▾",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activePopover == ActiveToolbarPopover.FONT_SIZE) {
                                    palette.activeToolIndigo
                                } else {
                                    palette.textPrimary
                                }
                            )
                        )
                    }
                }
            }

            // Subtle right-edge gradient fade indicating horizontal scrollability
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(24.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, palette.softTintedSurface)
                        )
                    )
            )
        }
    }
}

@Composable
private fun ToolbarIconChip(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    accentDotColor: Color? = null,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalVaultPalette.current
    val bgColor = if (isActive) palette.activeToolIndigo.copy(alpha = 0.15f) else Color.Transparent
    val iconTint = if (isActive) palette.activeToolIndigo else palette.textPrimary

    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        if (accentDotColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = 14.dp, height = 3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(accentDotColor)
            )
        }
    }
}
