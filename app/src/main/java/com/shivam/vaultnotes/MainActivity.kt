package com.shivam.vaultnotes

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shivam.vaultnotes.data.local.AppDatabase
import com.shivam.vaultnotes.data.model.ClipboardOperation
import com.shivam.vaultnotes.data.model.ClipboardState
import com.shivam.vaultnotes.data.model.NoteDocument
import com.shivam.vaultnotes.data.model.VaultItem
import com.shivam.vaultnotes.data.storage.VaultRepository
import com.shivam.vaultnotes.ui.editor.EditorScreen
import com.shivam.vaultnotes.ui.home.HomeScreen
import com.shivam.vaultnotes.ui.theme.VaultNotesTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ActiveScreenState {
    data object Home : ActiveScreenState
    data class Editor(
        val noteRelativePath: String,
        val initialDocument: NoteDocument
    ) : ActiveScreenState
}

data class VaultUiState(
    val currentDirectoryPath: String = "",
    val items: List<VaultItem> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isDragSortMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val clipboardState: ClipboardState? = null,
    val vaultUriString: String = "",
    val isSafVault: Boolean = false,
    val activeScreen: ActiveScreenState = ActiveScreenState.Home
)

class VaultNotesViewModel(
    val repository: VaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.vaultConfigFlow.collect { config ->
                if (config != null) {
                    _uiState.update {
                        it.copy(
                            vaultUriString = config.vaultUriString,
                            isSafVault = config.isSafTree
                        )
                    }
                }
            }
        }
        refreshCurrentDirectory()
    }

    fun refreshCurrentDirectory() {
        viewModelScope.launch {
            val state = _uiState.value
            val loadedItems = if (state.isSearchActive && state.searchQuery.isNotBlank()) {
                repository.searchVault(state.searchQuery, state.currentDirectoryPath)
            } else {
                repository.listDirectory(state.currentDirectoryPath)
            }
            _uiState.update { it.copy(items = loadedItems) }
        }
    }

    fun navigateToDirectory(relativePath: String) {
        val clean = relativePath.trim('/')
        _uiState.update {
            it.copy(
                currentDirectoryPath = clean,
                selectedPaths = emptySet(),
                isSearchActive = false,
                searchQuery = ""
            )
        }
        refreshCurrentDirectory()
    }

    fun stepUpParentDirectory(): Boolean {
        val current = _uiState.value.currentDirectoryPath.trim('/')
        if (current.isEmpty()) return false
        val parent = current.substringBeforeLast('/', "")
        navigateToDirectory(parent)
        return true
    }

    fun toggleSearch(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (active) it.searchQuery else ""
            )
        }
        refreshCurrentDirectory()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        refreshCurrentDirectory()
    }

    fun toggleDragSortMode() {
        _uiState.update {
            it.copy(
                isDragSortMode = !it.isDragSortMode,
                selectedPaths = emptySet()
            )
        }
    }

    fun reorderItems(fromIndex: Int, toIndex: Int) {
        val currentList = _uiState.value.items.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        val moved = currentList.removeAt(fromIndex)
        currentList.add(toIndex, moved)
        _uiState.update { it.copy(items = currentList) }

        val dirPath = _uiState.value.currentDirectoryPath
        val orderedNames = currentList.map { it.fileName }
        viewModelScope.launch {
            repository.saveCustomSortOrder(dirPath, orderedNames)
        }
    }

    fun toggleSelectItem(relativePath: String) {
        _uiState.update { state ->
            val next = state.selectedPaths.toMutableSet()
            if (!next.add(relativePath)) {
                next.remove(relativePath)
            }
            state.copy(selectedPaths = next)
        }
    }

    fun selectAllItems() {
        _uiState.update { state ->
            state.copy(selectedPaths = state.items.map { it.relativePath }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    fun createNewFolder(folderName: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.createFolder(_uiState.value.currentDirectoryPath, folderName)
            res.onSuccess {
                refreshCurrentDirectory()
            }.onFailure { err ->
                onMessage(err.message ?: "Failed to create folder")
            }
        }
    }

    fun createAndOpenNewNote(onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.createNewNote(_uiState.value.currentDirectoryPath)
            res.onSuccess { (notePath, noteDoc) ->
                refreshCurrentDirectory()
                _uiState.update {
                    it.copy(
                        activeScreen = ActiveScreenState.Editor(
                            noteRelativePath = notePath,
                            initialDocument = noteDoc
                        )
                    )
                }
            }.onFailure { err ->
                onMessage(err.message ?: "Could not create note")
            }
        }
    }

    fun openExistingNote(noteRelativePath: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.loadNote(noteRelativePath)
            res.onSuccess { doc ->
                _uiState.update {
                    it.copy(
                        activeScreen = ActiveScreenState.Editor(
                            noteRelativePath = noteRelativePath,
                            initialDocument = doc
                        )
                    )
                }
            }.onFailure { err ->
                onMessage(err.message ?: "Could not open note")
            }
        }
    }

    fun closeEditorAndRefresh() {
        _uiState.update { it.copy(activeScreen = ActiveScreenState.Home) }
        refreshCurrentDirectory()
    }

    fun renameItem(item: VaultItem, newName: String, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.renameItem(item.relativePath, newName)
            res.onSuccess {
                refreshCurrentDirectory()
            }.onFailure { err ->
                onMessage(err.message ?: "Could not rename item")
            }
        }
    }

    fun deleteItems(paths: List<String>, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteItems(paths)
            res.onSuccess { count ->
                refreshCurrentDirectory()
                onMessage("Deleted $count ${if (count == 1) "item" else "items"}")
            }.onFailure { err ->
                onMessage(err.message ?: "Delete failed")
            }
        }
    }

    fun startClipboardOperation(operation: ClipboardOperation, sourcePaths: List<String>) {
        if (sourcePaths.isEmpty()) return
        _uiState.update {
            it.copy(
                clipboardState = ClipboardState(operation = operation, sourcePaths = sourcePaths),
                selectedPaths = emptySet()
            )
        }
    }

    fun cancelClipboard() {
        _uiState.update { it.copy(clipboardState = null) }
    }

    fun pasteClipboardHere(onMessage: (String) -> Unit) {
        val clip = _uiState.value.clipboardState ?: return
        val destDir = _uiState.value.currentDirectoryPath
        viewModelScope.launch {
            val result = when (clip.operation) {
                ClipboardOperation.MOVE -> repository.moveItems(clip.sourcePaths, destDir)
                ClipboardOperation.COPY -> repository.copyItems(clip.sourcePaths, destDir)
            }
            result.onSuccess { count ->
                _uiState.update { it.copy(clipboardState = null) }
                refreshCurrentDirectory()
                val actionLabel = if (clip.operation == ClipboardOperation.MOVE) "Moved" else "Copied"
                onMessage("$actionLabel $count ${if (count == 1) "item" else "items"}")
            }.onFailure { err ->
                onMessage(err.message ?: "Operation blocked")
            }
        }
    }

    fun setSafVaultTreeUri(uri: Uri, onMessage: (String) -> Unit) {
        viewModelScope.launch {
            val ok = repository.setSafVaultUri(uri)
            if (ok) {
                _uiState.update {
                    it.copy(
                        currentDirectoryPath = "",
                        selectedPaths = emptySet(),
                        clipboardState = null
                    )
                }
                refreshCurrentDirectory()
                onMessage("Switched to selected SAF Vault folder")
            } else {
                onMessage("Unable to access selected folder")
            }
        }
    }

    fun useDefaultInternalVault(onMessage: (String) -> Unit) {
        viewModelScope.launch {
            repository.switchToInternalDefaultVault()
            _uiState.update {
                it.copy(
                    currentDirectoryPath = "",
                    selectedPaths = emptySet(),
                    clipboardState = null
                )
            }
            refreshCurrentDirectory()
            onMessage("Switched to built-in Vault")
        }
    }
}

class VaultNotesViewModelFactory(
    private val repository: VaultRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VaultNotesViewModel(repository) as T
    }
}

open class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaultNotesTheme {
                VaultNotesRootApp(onExitApp = { finish() })
            }
        }
    }
}

@Composable
fun VaultNotesRootApp(
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val repository = remember(context) {
        val db = AppDatabase.getInstance(context)
        VaultRepository(context.applicationContext, db.vaultDao())
    }

    val viewModel: VaultNotesViewModel = viewModel(
        factory = VaultNotesViewModelFactory(repository)
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val safDirectoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            viewModel.setSafVaultTreeUri(treeUri) { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCurrentDirectory()
    }

    Crossfade(
        targetState = uiState.activeScreen,
        animationSpec = tween(220),
        label = "screenTransition"
    ) { screen ->
        when (screen) {
            is ActiveScreenState.Home -> {
                HomeScreen(
                    currentDirectoryPath = uiState.currentDirectoryPath,
                    items = uiState.items,
                    searchQuery = uiState.searchQuery,
                    isSearchActive = uiState.isSearchActive,
                    isDragSortMode = uiState.isDragSortMode,
                    selectedPaths = uiState.selectedPaths,
                    clipboardState = uiState.clipboardState,
                    vaultUriString = uiState.vaultUriString,
                    isSafVault = uiState.isSafVault,
                    snackbarHostState = snackbarHostState,
                    onNavigateToDirectory = viewModel::navigateToDirectory,
                    onStepUpParentDirectory = { viewModel.stepUpParentDirectory() },
                    onExitApp = onExitApp,
                    onOpenNote = { notePath ->
                        viewModel.openExistingNote(notePath) { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onCreateNewNote = {
                        viewModel.createAndOpenNewNote { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onCreateNewFolder = { folderName ->
                        viewModel.createNewFolder(folderName) { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onRenameItem = { item, newName ->
                        viewModel.renameItem(item, newName) { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onDeleteItems = { paths ->
                        viewModel.deleteItems(paths) { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onStartClipboardOperation = viewModel::startClipboardOperation,
                    onPasteClipboardHere = {
                        viewModel.pasteClipboardHere { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onCancelClipboard = viewModel::cancelClipboard,
                    onToggleSelectItem = viewModel::toggleSelectItem,
                    onSelectAll = viewModel::selectAllItems,
                    onClearSelection = viewModel::clearSelection,
                    onToggleSearch = viewModel::toggleSearch,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onToggleDragSortMode = viewModel::toggleDragSortMode,
                    onMoveItemInSortOrder = viewModel::reorderItems,
                    onPickSafVault = {
                        safDirectoryPickerLauncher.launch(null)
                    },
                    onUseDefaultVault = {
                        viewModel.useDefaultInternalVault { msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                )
            }

            is ActiveScreenState.Editor -> {
                EditorScreen(
                    initialNotePath = screen.noteRelativePath,
                    initialDocument = screen.initialDocument,
                    onResolveAssetUri = { relPath ->
                        repository.resolveAssetUri(relPath)
                    },
                    onSaveCroppedBitmap = { bmp ->
                        repository.saveCroppedImageToAssets(bmp)
                    },
                    onAtomicSaveNote = { currentPath, updatedDoc ->
                        repository.saveNoteAtomically(currentPath, updatedDoc)
                    },
                    onNavigateBack = {
                        viewModel.closeEditorAndRefresh()
                    }
                )
            }
        }
    }
}
