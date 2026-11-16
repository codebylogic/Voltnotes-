package com.shivam.vaultnotes.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.vaultnotes.data.model.ClipboardOperation
import com.shivam.vaultnotes.data.model.ClipboardState
import com.shivam.vaultnotes.data.model.VaultItem
import com.shivam.vaultnotes.ui.theme.LocalVaultPalette
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Screen 1: Modern File-Manager Home Screen.
 * - Collapsing Editorial Header & Horizontal Breadcrumb Pill Strip
 * - 64.dp Modern Squircle File & Folder Cards
 * - Reorderable Custom Drag-Sort Mode (`1.03x` scale + `8.dp` shadow + Room DB persistence)
 * - Full Single & Batch Multi-Select File Manager (Move, Copy, Delete, Rename)
 * - Floating Dual-Pill Action Dock (`📁+` New Folder & `+ New Note`)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    currentDirectoryPath: String,
    items: List<VaultItem>,
    searchQuery: String,
    isSearchActive: Boolean,
    isDragSortMode: Boolean,
    selectedPaths: Set<String>,
    clipboardState: ClipboardState?,
    vaultUriString: String,
    isSafVault: Boolean,
    snackbarHostState: SnackbarHostState,
    onNavigateToDirectory: (String) -> Unit,
    onStepUpParentDirectory: () -> Unit,
    onExitApp: () -> Unit,
    onOpenNote: (String) -> Unit,
    onCreateNewNote: () -> Unit,
    onCreateNewFolder: (String) -> Unit,
    onRenameItem: (VaultItem, String) -> Unit,
    onDeleteItems: (List<String>) -> Unit,
    onStartClipboardOperation: (ClipboardOperation, List<String>) -> Unit,
    onPasteClipboardHere: () -> Unit,
    onCancelClipboard: () -> Unit,
    onToggleSelectItem: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleDragSortMode: () -> Unit,
    onMoveItemInSortOrder: (Int, Int) -> Unit,
    onPickSafVault: () -> Unit,
    onUseDefaultVault: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    val isMultiSelectMode = selectedPaths.isNotEmpty()
    val isInSubfolder = currentDirectoryPath.trim('/').isNotEmpty()

    // Strict System Back Button Hierarchy (Section 3.4 of PRD)
    BackHandler(enabled = true) {
        when {
            isSearchActive -> onToggleSearch(false)
            isMultiSelectMode -> onClearSelection()
            clipboardState != null -> onCancelClipboard()
            isDragSortMode -> onToggleDragSortMode()
            isInSubfolder -> onStepUpParentDirectory()
            else -> onExitApp()
        }
    }

    val isCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 48
        }
    }

    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        onMoveItemInSortOrder(from.index, to.index)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    var overflowTargetItem by remember { mutableStateOf<VaultItem?>(null) }
    var renameTargetItem by remember { mutableStateOf<VaultItem?>(null) }
    var showNewFolderSheet by remember { mutableStateOf(false) }
    var pendingDeletePaths by remember { mutableStateOf<List<String>?>(null) }
    var showVaultSwitcherSheet by remember { mutableStateOf(false) }

    val currentFolderTitle = remember(currentDirectoryPath) {
        val clean = currentDirectoryPath.trim('/')
        if (clean.isEmpty()) "Vault" else clean.substringAfterLast('/')
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.backgroundCanvas)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: Contextual Multi-Select OR Editorial Collapsing Header
            if (isMultiSelectMode) {
                MultiSelectTopBar(
                    selectedCount = selectedPaths.size,
                    onClearSelection = onClearSelection,
                    onSelectAll = onSelectAll
                )
            } else {
                EditorialCollapsingHeader(
                    title = currentFolderTitle,
                    isInSubfolder = isInSubfolder,
                    isCollapsed = isCollapsed,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    isDragSortMode = isDragSortMode,
                    onBackClick = onStepUpParentDirectory,
                    onToggleSearch = { onToggleSearch(!isSearchActive) },
                    onSearchQueryChange = onSearchQueryChange,
                    onToggleDragSort = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleDragSortMode()
                    },
                    onOpenVaultSwitcher = { showVaultSwitcherSheet = true }
                )
            }

            // Horizontal Breadcrumb Pill Strip (`Vault › Study › Physics`)
            BreadcrumbPillStrip(
                currentDirectoryPath = currentDirectoryPath,
                onBreadcrumbClick = onNavigateToDirectory
            )

            // File & Folder Cards List or Empty State
            if (items.isEmpty()) {
                EmptyDirectoryView(
                    isSearch = isSearchActive && searchQuery.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 112.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("vault_items_list")
                ) {
                    items(
                        items = items,
                        key = { it.relativePath }
                    ) { item ->
                        ReorderableItem(
                            state = reorderableLazyListState,
                            key = item.relativePath
                        ) { isDragging ->
                            val scale by animateFloatAsState(
                                targetValue = if (isDragging) 1.03f else 1f,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                                label = "dragScale"
                            )
                            val elevation by animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 2.dp,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                                label = "dragElevation"
                            )

                            val isSelected = item.relativePath in selectedPaths

                            VaultFileFolderCard(
                                item = item,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = isSelected,
                                isDragSortMode = isDragSortMode,
                                scale = scale,
                                shadowElevation = elevation,
                                dragHandleModifier = with(this) {
                                    Modifier.draggableHandle(
                                        onDragStarted = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    )
                                },
                                onClick = {
                                    when {
                                        isMultiSelectMode -> onToggleSelectItem(item.relativePath)
                                        item.isDirectory -> onNavigateToDirectory(item.relativePath)
                                        else -> onOpenNote(item.relativePath)
                                    }
                                },
                                onLongClick = {
                                    if (!isDragSortMode && clipboardState == null) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleSelectItem(item.relativePath)
                                    }
                                },
                                onOverflowClick = {
                                    overflowTargetItem = item
                                }
                            )
                        }
                    }
                }
            }
        }

        // Bottom Docks: Multi-Select Pill OR Clipboard Paste Dock OR Dual-Pill Action Dock
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            when {
                isMultiSelectMode -> {
                    MultiSelectBottomBar(
                        selectedCount = selectedPaths.size,
                        onMoveSelected = {
                            val paths = selectedPaths.toList()
                            onClearSelection()
                            onStartClipboardOperation(ClipboardOperation.MOVE, paths)
                        },
                        onCopySelected = {
                            val paths = selectedPaths.toList()
                            onClearSelection()
                            onStartClipboardOperation(ClipboardOperation.COPY, paths)
                        },
                        onDeleteSelected = {
                            pendingDeletePaths = selectedPaths.toList()
                        }
                    )
                }
                clipboardState != null -> {
                    ClipboardPasteDock(
                        clipboardState = clipboardState,
                        onNewFolderClick = { showNewFolderSheet = true },
                        onPasteHereClick = onPasteClipboardHere,
                        onCancelClick = onCancelClipboard
                    )
                }
                else -> {
                    FloatingBottomActionDock(
                        onNewFolderClick = { showNewFolderSheet = true },
                        onNewNoteClick = onCreateNewNote,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .navigationBarsPadding()
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp)
                .navigationBarsPadding()
        )
    }

    // ModalBottomSheets
    overflowTargetItem?.let { item ->
        ItemOverflowBottomSheet(
            item = item,
            onDismiss = { overflowTargetItem = null },
            onRename = { renameTargetItem = item },
            onMoveTo = {
                onStartClipboardOperation(ClipboardOperation.MOVE, listOf(item.relativePath))
            },
            onCopyTo = {
                onStartClipboardOperation(ClipboardOperation.COPY, listOf(item.relativePath))
            },
            onDelete = {
                pendingDeletePaths = listOf(item.relativePath)
            }
        )
    }

    renameTargetItem?.let { item ->
        val initial = if (item.isUntitledNote) "" else item.displayName.ifBlank { item.fileName.removeSuffix(".html") }
        TextInputBottomSheet(
            title = if (item.isDirectory) "Rename Folder" else "Rename Note",
            initialValue = initial,
            placeholder = if (item.isDirectory) "Folder name" else "Note title",
            confirmLabel = "Rename",
            onDismiss = { renameTargetItem = null },
            onConfirm = { newName ->
                onRenameItem(item, newName)
            }
        )
    }

    if (showNewFolderSheet) {
        TextInputBottomSheet(
            title = "New Folder",
            initialValue = "",
            placeholder = "e.g., Study, Projects, Archive",
            confirmLabel = "Create Folder",
            onDismiss = { showNewFolderSheet = false },
            onConfirm = { folderName ->
                onCreateNewFolder(folderName)
            }
        )
    }

    pendingDeletePaths?.let { pathsToDelete ->
        val singleName = if (pathsToDelete.size == 1) {
            items.find { it.relativePath == pathsToDelete.first() }?.let {
                if (it.isUntitledNote) "Untitled" else it.displayName.ifBlank { it.fileName }
            }
        } else null

        DeleteConfirmationBottomSheet(
            itemCount = pathsToDelete.size,
            singleItemName = singleName,
            onDismiss = { pendingDeletePaths = null },
            onConfirmDelete = {
                onDeleteItems(pathsToDelete)
                onClearSelection()
            }
        )
    }

    if (showVaultSwitcherSheet) {
        VaultSwitcherBottomSheet(
            currentVaultUri = vaultUriString,
            isSafTree = isSafVault,
            onDismiss = { showVaultSwitcherSheet = false },
            onPickSafDirectory = onPickSafVault,
            onUseDefaultVault = onUseDefaultVault
        )
    }
}

@Composable
private fun EditorialCollapsingHeader(
    title: String,
    isInSubfolder: Boolean,
    isCollapsed: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    isDragSortMode: Boolean,
    onBackClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleDragSort: () -> Unit,
    onOpenVaultSwitcher: () -> Unit
) {
    val palette = LocalVaultPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isInSubfolder) {
                    Surface(
                        shape = CircleShape,
                        color = palette.softTintedSurface,
                        border = BorderStroke(0.8.dp, palette.hairlineBorder),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onBackClick)
                            .testTag("folder_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                                contentDescription = "Parent folder",
                                tint = palette.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Text(
                    text = title,
                    style = if (isCollapsed) {
                        MaterialTheme.typography.titleMedium.copy(color = palette.textPrimary)
                    } else {
                        MaterialTheme.typography.headlineLarge.copy(color = palette.textPrimary)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right Action Buttons: Search (🔍), Custom Drag-Sort (⇅), Vault Switcher (📁)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularGhostTopButton(
                    icon = Icons.Outlined.Search,
                    contentDescription = "Search",
                    isActive = isSearchActive,
                    onClick = onToggleSearch,
                    testTag = "top_search_button"
                )
                CircularGhostTopButton(
                    icon = Icons.Outlined.SwapVert,
                    contentDescription = "Custom Drag-Sort",
                    isActive = isDragSortMode,
                    onClick = onToggleDragSort,
                    testTag = "top_dragsort_button"
                )
                CircularGhostTopButton(
                    icon = Icons.Outlined.FolderOpen,
                    contentDescription = "Vault Switcher",
                    isActive = false,
                    onClick = onOpenVaultSwitcher,
                    testTag = "top_vault_switcher_button"
                )
            }
        }

        // Expandable Sleek Pill Search Bar
        AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)),
            exit = fadeOut(spring(dampingRatio = 0.8f, stiffness = 400f))
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = palette.softTintedSurface,
                border = BorderStroke(0.8.dp, palette.hairlineBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp)
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search notes & folders…",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = palette.textSecondary.copy(alpha = 0.65f)
                                )
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.textPrimary),
                            cursorBrush = SolidColor(palette.activeToolIndigo),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_input_field")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularGhostTopButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalVaultPalette.current
    val bgColor = if (isActive) palette.activeToolIndigo.copy(alpha = 0.16f) else palette.softTintedSurface
    val borderColor = if (isActive) palette.activeToolIndigo else palette.hairlineBorder
    val iconTint = if (isActive) palette.activeToolIndigo else palette.textPrimary

    Surface(
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Horizontal Breadcrumb Pill Strip (`Vault › Study › Physics`) allowing one-tap jump to any ancestor folder.
 */
@Composable
fun BreadcrumbPillStrip(
    currentDirectoryPath: String,
    onBreadcrumbClick: (String) -> Unit
) {
    val palette = LocalVaultPalette.current
    val segments = remember(currentDirectoryPath) {
        val clean = currentDirectoryPath.trim('/')
        if (clean.isEmpty()) emptyList() else clean.split('/').filter { it.isNotBlank() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BreadcrumbChip(
            label = "Vault",
            isCurrent = segments.isEmpty(),
            onClick = { onBreadcrumbClick("") }
        )

        var accumulatedPath = ""
        segments.forEachIndexed { index, segment ->
            accumulatedPath = if (accumulatedPath.isEmpty()) segment else "$accumulatedPath/$segment"
            val targetPath = accumulatedPath
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(16.dp)
            )
            BreadcrumbChip(
                label = segment,
                isCurrent = index == segments.lastIndex,
                onClick = { onBreadcrumbClick(targetPath) }
            )
        }
    }
}

@Composable
private fun BreadcrumbChip(
    label: String,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalVaultPalette.current
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isCurrent) palette.softTintedSurface else palette.backgroundCanvas,
        border = BorderStroke(
            width = 0.8.dp,
            color = if (isCurrent) palette.hairlineBorder else palette.hairlineBorder.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (isCurrent) palette.textPrimary else palette.textSecondary,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * 64.dp Modern Squircle File & Folder Card (`RoundedCornerShape(14.dp)`, `0.8.dp` hairline border).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultFileFolderCard(
    item: VaultItem,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    isDragSortMode: Boolean,
    scale: Float,
    shadowElevation: androidx.compose.ui.unit.Dp,
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOverflowClick: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val borderColor = if (isSelected) palette.activeToolIndigo else palette.hairlineBorder
    val containerColor = if (isSelected) {
        palette.activeToolIndigo.copy(alpha = 0.08f)
    } else {
        palette.cardSurface
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.4.dp else 0.8.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(scale)
            .shadow(shadowElevation, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("vault_item_${item.fileName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Squircle Badge (40.dp x 40.dp, RoundedCornerShape(10.dp))
            val badgeBg = when {
                isMultiSelectMode && isSelected -> palette.activeToolIndigo.copy(alpha = 0.16f)
                item.isDirectory -> palette.folderBadgeBg
                else -> palette.noteBadgeBg
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                if (isMultiSelectMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) palette.activeToolIndigo else palette.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (item.isDirectory) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Folder",
                        tint = palette.folderBadgeIcon,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = "Note",
                        tint = palette.noteBadgeIcon,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center Content: Primary Name (15.sp SemiBold) & Secondary Subtitle (12.sp TextSecondary)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (item.isUntitledNote) {
                    Text(
                        text = "Untitled",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = palette.textSecondary,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = item.displayName.ifBlank { item.fileName.removeSuffix(".html") },
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = palette.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = palette.textSecondary,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Trailing Action: Drag Handle (≡) in Arrange mode OR Overflow (⋮) in Normal mode
            if (isDragSortMode) {
                Box(
                    modifier = dragHandleModifier
                        .size(48.dp)
                        .testTag("drag_handle_${item.fileName}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = "Reorder item",
                        tint = palette.activeToolIndigo,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else if (!isMultiSelectMode) {
                IconButton(
                    onClick = onOverflowClick,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("overflow_menu_${item.fileName}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More actions",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Custom Empty State: Centered minimal vector icon inside a 64.dp soft circle,
 * "Nothing here yet" (16.sp SemiBold), and "Tap + New Note below to start writing" (13.sp TextSecondary).
 */
@Composable
fun EmptyDirectoryView(
    isSearch: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(palette.softTintedSurface)
                .border(0.8.dp, palette.hairlineBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearch) Icons.Outlined.Search else Icons.Outlined.NoteAdd,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isSearch) "No matching notes" else "Nothing here yet",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isSearch) "Try a different keyword or folder" else "Tap + New Note below to start writing",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                color = palette.textSecondary
            )
        )
    }
}

/**
 * Floating Bottom Action Dock (Normal Mode, Bottom-Right, 16.dp margin):
 * - Secondary Icon Pill (`48.dp × 48.dp`, `RoundedCornerShape(14.dp)`, `Card Surface` + `0.8.dp` border): New Folder (`📁+`)
 * - Primary Action Pill (`48.dp` height, `RoundedCornerShape(14.dp)`, `Primary Brand Accent` fill): `+ New Note`
 */
@Composable
fun FloatingBottomActionDock(
    onNewFolderClick: () -> Unit,
    onNewNoteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Secondary Icon Pill: New Folder (📁+)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = palette.cardSurface,
            border = BorderStroke(0.8.dp, palette.hairlineBorder),
            modifier = Modifier
                .size(48.dp)
                .shadow(4.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onNewFolderClick)
                .testTag("new_folder_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.CreateNewFolder,
                    contentDescription = "New Folder",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Primary Action Pill: + New Note
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = palette.primaryBrandAccent,
            modifier = Modifier
                .height(48.dp)
                .shadow(5.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onNewNoteClick)
                .testTag("new_note_button")
        ) {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = palette.onPrimaryBrandAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "New Note",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = palette.onPrimaryBrandAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
