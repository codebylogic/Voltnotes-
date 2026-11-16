package com.shivam.vaultnotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.vaultnotes.data.model.ActiveFormattingState
import com.shivam.vaultnotes.data.model.RichTextData
import com.shivam.vaultnotes.data.model.ToggleBlock
import com.shivam.vaultnotes.ui.theme.LocalVaultPalette

/**
 * ToggleBlockView:
 * Soft tinted container (`RoundedCornerShape(12.dp)`, `0.8.dp` border) with clickable header
 * (`▶`/`▼` spring-rotated chevron + editable summary) and `AnimatedVisibility` body container beneath it.
 */
@Composable
fun ToggleBlockView(
    block: ToggleBlock,
    isSummaryFocused: Boolean,
    focusedBodyParagraphId: String?,
    activeFormatting: ActiveFormattingState,
    externalSelectionTrigger: Pair<Int, Long>?,
    onToggleExpanded: () -> Unit,
    onSummaryFocus: (TextRange) -> Unit,
    onSummarySelectionChanged: (TextRange, ActiveFormattingState) -> Unit,
    onSummaryChanged: (RichTextData, TextRange) -> Unit,
    onBodyParagraphFocus: (String, TextRange) -> Unit,
    onBodyParagraphSelectionChanged: (String, TextRange, ActiveFormattingState) -> Unit,
    onBodyParagraphChanged: (String, RichTextData, TextRange) -> Unit,
    onDeleteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (block.isExpanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "toggleChevronRotation"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.softTintedSurface.copy(alpha = 0.7f),
        border = BorderStroke(0.8.dp, palette.hairlineBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("toggle_block_${block.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Header row: Spring-rotated ▶/▼ chevron + Editable Summary + Remove button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onToggleExpanded)
                        .testTag("toggle_chevron_${block.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = if (block.isExpanded) "Collapse toggle" else "Expand toggle",
                        tint = palette.activeToolIndigo,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(modifier = Modifier.weight(1f)) {
                    RichTextBlockEditor(
                        richTextData = block.summary,
                        placeholder = "Toggle summary…",
                        textStyle = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        activeFormatting = activeFormatting,
                        externalSelectionTrigger = if (isSummaryFocused) externalSelectionTrigger else null,
                        onFocusAcquired = onSummaryFocus,
                        onSelectionChanged = onSummarySelectionChanged,
                        onRichTextChanged = onSummaryChanged
                    )
                }

                IconButton(
                    onClick = onDeleteToggle,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove toggle block",
                        tint = palette.textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // AnimatedVisibility Body Container beneath summary
            AnimatedVisibility(
                visible = block.isExpanded,
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) +
                    fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)),
                exit = shrinkVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) +
                    fadeOut(spring(dampingRatio = 0.8f, stiffness = 400f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 34.dp, top = 8.dp, end = 4.dp, bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.8.dp)
                            .background(palette.hairlineBorder)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    block.bodyBlocks.forEach { para ->
                        val isThisBodyFocused = focusedBodyParagraphId == para.id
                        RichTextBlockEditor(
                            richTextData = para.content,
                            placeholder = "Write inside toggle…",
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 23.sp
                            ),
                            activeFormatting = activeFormatting,
                            externalSelectionTrigger = if (isThisBodyFocused) externalSelectionTrigger else null,
                            onFocusAcquired = { sel -> onBodyParagraphFocus(para.id, sel) },
                            onSelectionChanged = { sel, fmt -> onBodyParagraphSelectionChanged(para.id, sel, fmt) },
                            onRichTextChanged = { updated, sel -> onBodyParagraphChanged(para.id, updated, sel) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
