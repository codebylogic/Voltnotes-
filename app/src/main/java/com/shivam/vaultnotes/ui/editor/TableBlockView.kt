package com.shivam.vaultnotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.vaultnotes.data.model.ActiveFormattingState
import com.shivam.vaultnotes.data.model.RichTextData
import com.shivam.vaultnotes.data.model.TableBlock
import com.shivam.vaultnotes.ui.theme.LocalVaultPalette
import kotlin.math.roundToInt

/**
 * TableBlockView:
 * - Wrapped in `Modifier.horizontalScroll(rememberScrollState())` so wide tables scroll sideways
 *   independently of the vertical note body.
 * - Focused tables display a floating mini-pill above them (`+ Row`, `- Row`, `+ Col`, `- Col`, `×`)
 *   and draggable column resize handles (`60.dp`–`320.dp`).
 * - Every cell supports full rich text and inline LaTeX math.
 */
@Composable
fun TableBlockView(
    block: TableBlock,
    isTableFocused: Boolean,
    focusedCell: Pair<Int, Int>?,
    activeFormatting: ActiveFormattingState,
    externalSelectionTrigger: Pair<Int, Long>?,
    onCellFocus: (Int, Int, TextRange) -> Unit,
    onCellSelectionChanged: (Int, Int, TextRange, ActiveFormattingState) -> Unit,
    onCellRichTextChanged: (Int, Int, RichTextData, TextRange) -> Unit,
    onColumnWidthChanged: (Int, Int) -> Unit,
    onAddRow: () -> Unit,
    onRemoveRow: () -> Unit,
    onAddColumn: () -> Unit,
    onRemoveColumn: () -> Unit,
    onDeleteTable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val density = LocalDensity.current
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("table_block_${block.id}")
    ) {
        // Floating Mini-Pill above focused table (+ Row, - Row, + Col, - Col, Delete)
        AnimatedVisibility(
            visible = isTableFocused,
            enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)),
            exit = fadeOut(spring(dampingRatio = 0.8f, stiffness = 400f))
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.softTintedSurface,
                border = BorderStroke(0.8.dp, palette.hairlineBorder),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .shadow(3.dp, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TableMiniPillChip(label = "+ Row", onClick = onAddRow, testTag = "table_add_row")
                    TableMiniPillChip(
                        label = "- Row",
                        enabled = block.rows.size > 1,
                        onClick = onRemoveRow,
                        testTag = "table_remove_row"
                    )
                    Box(
                        modifier = Modifier
                            .width(0.8.dp)
                            .height(18.dp)
                            .background(palette.hairlineBorder)
                    )
                    TableMiniPillChip(label = "+ Col", onClick = onAddColumn, testTag = "table_add_col")
                    TableMiniPillChip(
                        label = "- Col",
                        enabled = (block.rows.firstOrNull()?.size ?: 1) > 1,
                        onClick = onRemoveColumn,
                        testTag = "table_remove_col"
                    )
                    Box(
                        modifier = Modifier
                            .width(0.8.dp)
                            .height(18.dp)
                            .background(palette.hairlineBorder)
                    )
                    IconButton(
                        onClick = onDeleteTable,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("table_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Delete table",
                            tint = palette.dangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Horizontally scrollable table grid with draggable column resize handles (60.dp - 320.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.8.dp, palette.hairlineBorder, RoundedCornerShape(12.dp))
                    .background(palette.cardSurface)
            ) {
                // Column Resize Handle Strip when table is focused
                if (isTableFocused) {
                    Row(
                        modifier = Modifier
                            .background(palette.softTintedSurface)
                            .border(0.5.dp, palette.hairlineBorder)
                    ) {
                        block.columnWidthsDp.forEachIndexed { colIndex, colWidthDp ->
                            var accumulatedDragPx by remember(colIndex, colWidthDp) {
                                mutableFloatStateOf(with(density) { colWidthDp.dp.toPx() })
                            }

                            Box(
                                modifier = Modifier
                                    .width(colWidthDp.dp)
                                    .height(18.dp)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = "${colWidthDp}px",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 10.sp,
                                        color = palette.textSecondary
                                    ),
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                // Draggable handle on the right edge of the column
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(palette.activeToolIndigo.copy(alpha = 0.25f))
                                        .pointerInput(colIndex) {
                                            detectHorizontalDragGestures { change, dragAmount ->
                                                change.consume()
                                                accumulatedDragPx += dragAmount
                                                val newWidthDp = with(density) {
                                                    accumulatedDragPx.toDp().value.roundToInt()
                                                }.coerceIn(60, 320)
                                                onColumnWidthChanged(colIndex, newWidthDp)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }

                // Table Rows & Cells
                block.rows.forEachIndexed { rowIndex, rowCells ->
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min)
                    ) {
                        rowCells.forEachIndexed { colIndex, cellRichData ->
                            val colWidth = block.columnWidthsDp.getOrElse(colIndex) { 150 }.dp
                            val isThisCellFocused = isTableFocused &&
                                focusedCell?.first == rowIndex &&
                                focusedCell.second == colIndex
                            val isHeaderRow = rowIndex == 0

                            Box(
                                modifier = Modifier
                                    .width(colWidth)
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            isThisCellFocused -> palette.activeToolIndigo.copy(alpha = 0.06f)
                                            isHeaderRow -> palette.softTintedSurface.copy(alpha = 0.45f)
                                            else -> palette.cardSurface
                                        }
                                    )
                                    .border(
                                        width = if (isThisCellFocused) 1.2.dp else 0.5.dp,
                                        color = if (isThisCellFocused) palette.activeToolIndigo else palette.hairlineBorder
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                RichTextBlockEditor(
                                    richTextData = cellRichData,
                                    placeholder = if (isHeaderRow) "Header" else "",
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = if (isHeaderRow) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    activeFormatting = activeFormatting,
                                    externalSelectionTrigger = if (isThisCellFocused) externalSelectionTrigger else null,
                                    onFocusAcquired = { sel ->
                                        onCellFocus(rowIndex, colIndex, sel)
                                    },
                                    onSelectionChanged = { sel, fmt ->
                                        onCellSelectionChanged(rowIndex, colIndex, sel, fmt)
                                    },
                                    onRichTextChanged = { updated, sel ->
                                        onCellRichTextChanged(rowIndex, colIndex, updated, sel)
                                    }
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
private fun TableMiniPillChip(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalVaultPalette.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.cardSurface,
        border = BorderStroke(0.8.dp, palette.hairlineBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) palette.textPrimary else palette.textSecondary.copy(alpha = 0.4f)
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
