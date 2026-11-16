package com.shivam.vaultnotes.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.vaultnotes.data.model.ClipboardOperation
import com.shivam.vaultnotes.data.model.ClipboardState
import com.shivam.vaultnotes.data.model.VaultItem
import com.shivam.vaultnotes.ui.theme.LocalVaultPalette

@Composable
fun SheetDragHandle() {
    val palette = LocalVaultPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.textSecondary.copy(alpha = 0.35f))
        )
    }
}

/**
 * Contextual Multi-Select Top Bar triggered on Long-Press.
 * Displays close (×), "3 selected" pill badge, and "Select All" (☑) button.
 */
@Composable
fun MultiSelectTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("clear_selection_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear selection",
                    tint = palette.textPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.activeToolIndigo.copy(alpha = 0.14f),
                border = BorderStroke(0.8.dp, palette.activeToolIndigo.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = palette.activeToolIndigo,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = palette.softTintedSurface,
            border = BorderStroke(0.8.dp, palette.hairlineBorder),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onSelectAll)
                .testTag("select_all_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = "Select All",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Select All",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = palette.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

/**
 * Floating Multi-Select Action Pill docked at the bottom when items are selected.
 */
@Composable
fun MultiSelectBottomBar(
    selectedCount: Int,
    onMoveSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = palette.softTintedSurface,
        border = BorderStroke(0.8.dp, palette.hairlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionPillButton(
                icon = Icons.Outlined.DriveFileMove,
                label = "Move",
                tint = palette.textPrimary,
                enabled = selectedCount > 0,
                onClick = onMoveSelected,
                testTag = "batch_move_button"
            )
            ActionPillButton(
                icon = Icons.Outlined.ContentCopy,
                label = "Copy",
                tint = palette.textPrimary,
                enabled = selectedCount > 0,
                onClick = onCopySelected,
                testTag = "batch_copy_button"
            )
            ActionPillButton(
                icon = Icons.Outlined.DeleteOutline,
                label = "Delete",
                tint = palette.dangerRed,
                enabled = selectedCount > 0,
                onClick = onDeleteSelected,
                testTag = "batch_delete_button"
            )
        }
    }
}

@Composable
private fun ActionPillButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalVaultPalette.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.cardSurface,
        border = BorderStroke(0.8.dp, palette.hairlineBorder),
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else tint.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    color = if (enabled) tint else tint.copy(alpha = 0.4f)
                )
            )
        }
    }
}

/**
 * Floating Clipboard Paste Dock for Move & Copy destination navigation.
 * Displays "Moving N items" / "Copying N items" with [+ Folder], [Paste Here], and [× Cancel].
 */
@Composable
fun ClipboardPasteDock(
    clipboardState: ClipboardState,
    onNewFolderClick: () -> Unit,
    onPasteHereClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val count = clipboardState.sourcePaths.size
    val itemWord = if (count == 1) "1 item" else "$count items"
    val verb = if (clipboardState.operation == ClipboardOperation.MOVE) "Moving" else "Copying"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding()
            .shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = palette.softTintedSurface,
        border = BorderStroke(0.8.dp, palette.hairlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$verb $itemWord",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    color = palette.textPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // [+ Folder]
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.cardSurface,
                    border = BorderStroke(0.8.dp, palette.hairlineBorder),
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onNewFolderClick)
                        .testTag("clipboard_new_folder_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CreateNewFolder,
                            contentDescription = "New Folder",
                            tint = palette.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+ Folder",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // [Paste Here] solid primary button
                Button(
                    onClick = onPasteHereClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryBrandAccent,
                        contentColor = palette.onPrimaryBrandAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("paste_here_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Paste Here",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.onPrimaryBrandAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                // [× Cancel]
                IconButton(
                    onClick = onCancelClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("cancel_clipboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Cancel",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Item Overflow ModalBottomSheet with Rename, Move to…, Copy to…, and Delete (in Danger red).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemOverflowBottomSheet(
    item: VaultItem,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMoveTo: () -> Unit,
    onCopyTo: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.cardSurface,
        dragHandle = { SheetDragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = if (item.isUntitledNote) "Untitled" else item.displayName.ifBlank { item.fileName },
                style = MaterialTheme.typography.titleMedium.copy(color = palette.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelMedium.copy(color = palette.textSecondary),
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            SheetActionRow(
                icon = Icons.Outlined.DriveFileRenameOutline,
                label = "Rename",
                tint = palette.textPrimary,
                onClick = {
                    onDismiss()
                    onRename()
                },
                testTag = "sheet_rename_action"
            )
            SheetActionRow(
                icon = Icons.Outlined.DriveFileMove,
                label = "Move to…",
                tint = palette.textPrimary,
                onClick = {
                    onDismiss()
                    onMoveTo()
                },
                testTag = "sheet_move_action"
            )
            SheetActionRow(
                icon = Icons.Outlined.ContentCopy,
                label = "Copy to…",
                tint = palette.textPrimary,
                onClick = {
                    onDismiss()
                    onCopyTo()
                },
                testTag = "sheet_copy_action"
            )
            SheetActionRow(
                icon = Icons.Outlined.DeleteOutline,
                label = "Delete",
                tint = palette.dangerRed,
                onClick = {
                    onDismiss()
                    onDelete()
                },
                testTag = "sheet_delete_action"
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalVaultPalette.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.softTintedSurface.copy(alpha = 0.65f),
        border = BorderStroke(0.8.dp, palette.hairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(color = tint)
            )
        }
    }
}

/**
 * ModalBottomSheet for creating a new folder or renaming a file/folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputBottomSheet(
    title: String,
    initialValue: String,
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val palette = LocalVaultPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.cardSurface,
        dragHandle = { SheetDragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(color = palette.textPrimary)
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        text = placeholder,
                        color = palette.textSecondary.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.activeToolIndigo,
                    unfocusedBorderColor = palette.hairlineBorder,
                    focusedTextColor = palette.textPrimary,
                    unfocusedTextColor = palette.textPrimary,
                    cursorColor = palette.activeToolIndigo
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (text.trim().isNotEmpty()) {
                            onConfirm(text.trim())
                            onDismiss()
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("sheet_text_input")
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, palette.hairlineBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel", color = palette.textPrimary)
                }
                Button(
                    onClick = {
                        if (text.trim().isNotEmpty()) {
                            onConfirm(text.trim())
                            onDismiss()
                        }
                    },
                    enabled = text.trim().isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primaryBrandAccent,
                        contentColor = palette.onPrimaryBrandAccent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sheet_confirm_button")
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

/**
 * Clean ModalBottomSheet asking confirmation before permanent SAF deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteConfirmationBottomSheet(
    itemCount: Int,
    singleItemName: String?,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.cardSurface,
        dragHandle = { SheetDragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = if (itemCount == 1 && !singleItemName.isNullOrBlank()) {
                    "Delete \"$singleItemName\"?"
                } else {
                    "Delete $itemCount items?"
                },
                style = MaterialTheme.typography.titleMedium.copy(color = palette.textPrimary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This will permanently delete the selected notes and folders from your Vault storage. This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium.copy(color = palette.textSecondary)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, palette.hairlineBorder),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel", color = palette.textPrimary)
                }
                Button(
                    onClick = {
                        onConfirmDelete()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.dangerRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("confirm_delete_button")
                ) {
                    Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Vault Switcher ModalBottomSheet allowing the user to pick any SAF folder (`ACTION_OPEN_DOCUMENT_TREE`)
 * or use the built-in offline Vault directory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSwitcherBottomSheet(
    currentVaultUri: String,
    isSafTree: Boolean,
    onDismiss: () -> Unit,
    onPickSafDirectory: () -> Unit,
    onUseDefaultVault: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.cardSurface,
        dragHandle = { SheetDragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Vault Storage Directory",
                style = MaterialTheme.typography.titleMedium.copy(color = palette.textPrimary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isSafTree) {
                    "Active SAF Folder: $currentVaultUri"
                } else {
                    "Active: Built-in Offline Vault (Standalone HTML5 + /assets)"
                },
                style = MaterialTheme.typography.labelMedium.copy(color = palette.textSecondary)
            )
            Spacer(modifier = Modifier.height(16.dp))

            SheetActionRow(
                icon = Icons.Outlined.FolderOpen,
                label = "Choose SAF Vault Folder (Device Storage)…",
                tint = palette.activeToolIndigo,
                onClick = {
                    onDismiss()
                    onPickSafDirectory()
                },
                testTag = "pick_saf_vault_button"
            )

            if (isSafTree) {
                SheetActionRow(
                    icon = Icons.Outlined.Storage,
                    label = "Switch to Built-in App Vault",
                    tint = palette.textPrimary,
                    onClick = {
                        onDismiss()
                        onUseDefaultVault()
                    },
                    testTag = "use_default_vault_button"
                )
            }
        }
    }
}
