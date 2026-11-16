package com.shivam.vaultnotes.data.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.shivam.vaultnotes.data.codec.NoteHtmlCodec
import com.shivam.vaultnotes.data.local.DirectorySortOrderEntity
import com.shivam.vaultnotes.data.local.VaultConfigEntity
import com.shivam.vaultnotes.data.local.VaultDao
import com.shivam.vaultnotes.data.model.NoteDocument
import com.shivam.vaultnotes.data.model.ParagraphBlock
import com.shivam.vaultnotes.data.model.RichTextData
import com.shivam.vaultnotes.data.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * SAF DocumentFile & local Vault storage engine.
 * Supports:
 * - SAF Tree URI (`ACTION_OPEN_DOCUMENT_TREE`) with full persistable permissions
 * - Built-in offline default Vault seeded with PRD sample notes & folders on first run
 * - Atomic `.tmp_<uuid>.html` write & flush engine
 * - Recursive Move, Copy, Delete, Rename with Self-Containment Guard, UUID regeneration, and asset path preservation
 */
class VaultRepository(
    private val context: Context,
    private val vaultDao: VaultDao
) {
    val vaultConfigFlow: Flow<VaultConfigEntity?> = vaultDao.observeVaultConfig()

    private val folderDateFormat = SimpleDateFormat("dd MMM", Locale.US)
    private val noteDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val assetTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    suspend fun ensureVaultInitialized(): DocumentFile = withContext(Dispatchers.IO) {
        val config = vaultDao.getVaultConfig()
        if (config != null && config.isSafTree) {
            val uri = Uri.parse(config.vaultUriString)
            val treeDoc = DocumentFile.fromTreeUri(context, uri)
            if (treeDoc != null && treeDoc.exists() && treeDoc.canRead()) {
                ensureAssetsFolder(treeDoc)
                return@withContext treeDoc
            }
        }

        // Default local Vault inside app storage so the app works immediately out of the box
        val defaultDir = File(context.filesDir, "Vault")
        val isFirstSeed = !defaultDir.exists()
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        val docRoot = DocumentFile.fromFile(defaultDir)
        ensureAssetsFolder(docRoot)

        if (isFirstSeed || (defaultDir.listFiles()?.size ?: 0) <= 1) {
            seedDefaultVaultSampleNotes(defaultDir)
        }

        if (config == null) {
            vaultDao.saveVaultConfig(
                VaultConfigEntity(
                    id = 0,
                    vaultUriString = Uri.fromFile(defaultDir).toString(),
                    isSafTree = false
                )
            )
        }
        docRoot
    }

    suspend fun setSafVaultUri(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
            // Ignore if URI comes from a provider that doesn't require persistable permission
        }
        val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
        if (!doc.canRead()) return@withContext false
        ensureAssetsFolder(doc)
        vaultDao.saveVaultConfig(
            VaultConfigEntity(
                id = 0,
                vaultUriString = treeUri.toString(),
                isSafTree = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        true
    }

    suspend fun switchToInternalDefaultVault() = withContext(Dispatchers.IO) {
        val defaultDir = File(context.filesDir, "Vault")
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
            seedDefaultVaultSampleNotes(defaultDir)
        }
        vaultDao.saveVaultConfig(
            VaultConfigEntity(
                id = 0,
                vaultUriString = Uri.fromFile(defaultDir).toString(),
                isSafTree = false,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun listDirectory(relativePath: String): List<VaultItem> = withContext(Dispatchers.IO) {
        val root = ensureVaultInitialized()
        val targetDir = resolveDirectory(root, relativePath) ?: return@withContext emptyList()
        val sortOrders = vaultDao.getSortOrdersForDirectory(relativePath.trim('/'))
            .associate { it.fileName to it.sortIndex }

        val children = targetDir.listFiles()
        val items = mutableListOf<VaultItem>()

        for (file in children) {
            val name = file.name ?: continue
            // Hide internal temp files and root assets directory
            if (name.startsWith(".tmp_") || name.startsWith(".")) continue
            if (relativePath.trim('/').isEmpty() && name.equals("assets", ignoreCase = true) && file.isDirectory) {
                continue
            }

            if (file.isDirectory) {
                val childCount = file.listFiles().count { child ->
                    val n = child.name ?: ""
                    !n.startsWith(".") && !n.startsWith(".tmp_") &&
                        (child.isDirectory || n.endsWith(".html", ignoreCase = true))
                }
                val lastMod = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                val rel = if (relativePath.trim('/').isEmpty()) name else "${relativePath.trim('/')}/$name"
                val countLabel = if (childCount == 1) "1 item" else "$childCount items"
                items.add(
                    VaultItem(
                        relativePath = rel,
                        parentPath = relativePath.trim('/'),
                        fileName = name,
                        displayName = name,
                        isDirectory = true,
                        itemCount = childCount,
                        lastModified = lastMod,
                        subtitle = "$countLabel • ${folderDateFormat.format(Date(lastMod))}"
                    )
                )
            } else if (name.endsWith(".html", ignoreCase = true)) {
                val lastMod = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                val rel = if (relativePath.trim('/').isEmpty()) name else "${relativePath.trim('/')}/$name"
                val baseName = name.removeSuffix(".html").removeSuffix(".HTML")
                val parsedDoc = readNoteQuickHeader(file, baseName)
                val isUntitledName = baseName.matches(VaultItem.UNTITLED_REGEX)
                val displayTitle = when {
                    parsedDoc.title.isNotBlank() && !parsedDoc.title.matches(VaultItem.UNTITLED_REGEX) -> parsedDoc.title
                    !isUntitledName -> baseName
                    else -> ""
                }
                items.add(
                    VaultItem(
                        relativePath = rel,
                        parentPath = relativePath.trim('/'),
                        fileName = name,
                        displayName = displayTitle,
                        isDirectory = false,
                        itemCount = 0,
                        lastModified = lastMod,
                        subtitle = "Edited ${noteDateFormat.format(Date(lastMod))}",
                        noteId = parsedDoc.id
                    )
                )
            }
        }

        items.sortedWith(
            Comparator { a, b ->
                val idxA = sortOrders[a.fileName]
                val idxB = sortOrders[b.fileName]
                when {
                    idxA != null && idxB != null -> idxA.compareTo(idxB)
                    idxA != null -> -1
                    idxB != null -> 1
                    a.isDirectory != b.isDirectory -> if (a.isDirectory) -1 else 1
                    else -> a.fileName.lowercase().compareTo(b.fileName.lowercase())
                }
            }
        )
    }

    suspend fun searchVault(query: String, currentDirectoryPath: String): List<VaultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext listDirectory(currentDirectoryPath)
        val root = ensureVaultInitialized()
        val results = mutableListOf<VaultItem>()

        fun walk(dir: DocumentFile, relDir: String) {
            for (child in dir.listFiles()) {
                val name = child.name ?: continue
                if (name.startsWith(".") || name.startsWith(".tmp_")) continue
                if (relDir.isEmpty() && name.equals("assets", ignoreCase = true) && child.isDirectory) continue

                val childRel = if (relDir.isEmpty()) name else "$relDir/$name"
                if (child.isDirectory) {
                    if (name.contains(trimmed, ignoreCase = true)) {
                        val lastMod = child.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                        val count = child.listFiles().count {
                            val n = it.name ?: ""
                            !n.startsWith(".") && (it.isDirectory || n.endsWith(".html", true))
                        }
                        results.add(
                            VaultItem(
                                relativePath = childRel,
                                parentPath = relDir,
                                fileName = name,
                                displayName = name,
                                isDirectory = true,
                                itemCount = count,
                                lastModified = lastMod,
                                subtitle = "${if (relDir.isEmpty()) "Vault" else relDir} • ${folderDateFormat.format(Date(lastMod))}"
                            )
                        )
                    }
                    walk(child, childRel)
                } else if (name.endsWith(".html", ignoreCase = true)) {
                    val html = readDocumentFileText(child)
                    val baseName = name.removeSuffix(".html").removeSuffix(".HTML")
                    val doc = NoteHtmlCodec.parseHtml(html, baseName)
                    val matchesName = name.contains(trimmed, ignoreCase = true) || doc.title.contains(trimmed, ignoreCase = true)
                    val matchesBody = html.contains(trimmed, ignoreCase = true)
                    if (matchesName || matchesBody) {
                        val lastMod = child.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                        val isUntitled = baseName.matches(VaultItem.UNTITLED_REGEX) && doc.title.isBlank()
                        results.add(
                            VaultItem(
                                relativePath = childRel,
                                parentPath = relDir,
                                fileName = name,
                                displayName = if (isUntitled) "" else doc.title.ifBlank { baseName },
                                isDirectory = false,
                                lastModified = lastMod,
                                subtitle = "${if (relDir.isEmpty()) "Vault" else relDir} • Edited ${noteDateFormat.format(Date(lastMod))}",
                                noteId = doc.id
                            )
                        )
                    }
                }
            }
        }

        walk(root, "")
        results
    }

    suspend fun saveCustomSortOrder(directoryPath: String, orderedFileNames: List<String>) = withContext(Dispatchers.IO) {
        val cleanDir = directoryPath.trim('/')
        val entities = orderedFileNames.mapIndexed { index, fileName ->
            DirectorySortOrderEntity(
                compositeKey = "$cleanDir|$fileName",
                directoryPath = cleanDir,
                fileName = fileName,
                sortIndex = index
            )
        }
        vaultDao.upsertSortOrders(entities)
    }

    suspend fun createFolder(parentRelativePath: String, rawFolderName: String): Result<VaultItem> = withContext(Dispatchers.IO) {
        val cleanName = sanitizeFileName(rawFolderName)
        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Folder name cannot be empty"))
        }
        val root = ensureVaultInitialized()
        val parentDir = resolveDirectory(root, parentRelativePath)
            ?: return@withContext Result.failure(IllegalStateException("Parent directory not found"))

        val uniqueName = findUniqueName(parentDir, cleanName, isDirectory = true)
        val created = parentDir.createDirectory(uniqueName)
            ?: return@withContext Result.failure(IllegalStateException("Could not create folder"))

        val cleanParent = parentRelativePath.trim('/')
        val relPath = if (cleanParent.isEmpty()) uniqueName else "$cleanParent/$uniqueName"
        val now = System.currentTimeMillis()
        Result.success(
            VaultItem(
                relativePath = relPath,
                parentPath = cleanParent,
                fileName = uniqueName,
                displayName = uniqueName,
                isDirectory = true,
                itemCount = 0,
                lastModified = now,
                subtitle = "0 items • ${folderDateFormat.format(Date(now))}"
            )
        )
    }

    suspend fun createNewNote(parentRelativePath: String): Result<Pair<String, NoteDocument>> = withContext(Dispatchers.IO) {
        val root = ensureVaultInitialized()
        val parentDir = resolveDirectory(root, parentRelativePath)
            ?: return@withContext Result.failure(IllegalStateException("Parent directory not found"))

        var index = 1
        while (parentDir.findFile("Untitled $index.html") != null) {
            index++
        }
        val fileName = "Untitled $index.html"
        val doc = NoteDocument(
            id = UUID.randomUUID().toString(),
            title = "",
            blocks = listOf(ParagraphBlock(content = RichTextData()))
        )
        val html = NoteHtmlCodec.serializeToHtml(doc)
        val createdFile = createOrOverwriteHtmlFile(parentDir, fileName, html)
            ?: return@withContext Result.failure(IllegalStateException("Failed to create note file"))

        val actualName = createdFile.name ?: fileName
        val cleanParent = parentRelativePath.trim('/')
        val relPath = if (cleanParent.isEmpty()) actualName else "$cleanParent/$actualName"
        Result.success(relPath to doc)
    }

    suspend fun loadNote(noteRelativePath: String): Result<NoteDocument> = withContext(Dispatchers.IO) {
        val root = ensureVaultInitialized()
        val file = resolveFile(root, noteRelativePath)
            ?: return@withContext Result.failure(IllegalArgumentException("Note file not found: $noteRelativePath"))
        val html = readDocumentFileText(file)
        val baseName = (file.name ?: "").removeSuffix(".html").removeSuffix(".HTML")
        val fallbackTitle = if (baseName.matches(VaultItem.UNTITLED_REGEX)) "" else baseName
        val doc = NoteHtmlCodec.parseHtml(html, fallbackTitle)
        Result.success(doc)
    }

    /**
     * Atomic SAF write engine:
     * 1. Writes to `.tmp_<uuid>.html` in the note's directory and flushes stream.
     * 2. Writes/replaces target `.html` file and removes `.tmp_<uuid>.html`.
     * 3. If the note title changed, safely renames the `.html` file on disk and returns the updated relative path.
     */
    suspend fun saveNoteAtomically(
        currentRelativePath: String,
        document: NoteDocument
    ): Result<String> = withContext(Dispatchers.IO) {
        val root = ensureVaultInitialized()
        val cleanRel = currentRelativePath.trim('/')
        val parentRel = cleanRel.substringBeforeLast('/', "")
        val currentFileName = cleanRel.substringAfterLast('/')

        val parentDir = resolveDirectory(root, parentRel)
            ?: return@withContext Result.failure(IllegalStateException("Parent folder missing"))

        val htmlContent = NoteHtmlCodec.serializeToHtml(document)

        // Step 1: Write to temporary file `.tmp_<uuid>.html` and flush
        val tempFileName = ".tmp_${UUID.randomUUID().toString().take(8)}.html"
        val tempFile = createRawFileInDir(parentDir, tempFileName)
        if (tempFile != null) {
            writeTextToDocumentFile(tempFile, htmlContent)
        }

        // Step 2: Determine desired file name from note title
        val sanitizedTitle = sanitizeFileName(document.title)
        val desiredFileName = if (sanitizedTitle.isBlank()) {
            if (currentFileName.matches(VaultItem.UNTITLED_REGEX)) {
                currentFileName
            } else {
                findUniqueUntitledName(parentDir, currentFileName)
            }
        } else {
            val candidate = "$sanitizedTitle.html"
            if (candidate.equals(currentFileName, ignoreCase = true)) {
                candidate
            } else {
                findUniqueName(parentDir, candidate, isDirectory = false, ignoreExistingName = currentFileName)
            }
        }

        // Step 3: Write to target file and clean up temp file
        val existingFile = parentDir.findFile(currentFileName)
        var finalFileName = currentFileName

        if (existingFile != null) {
            writeTextToDocumentFile(existingFile, htmlContent)
            if (desiredFileName != currentFileName) {
                val renamed = try {
                    existingFile.renameTo(desiredFileName)
                } catch (_: Exception) {
                    false
                }
                if (renamed) {
                    finalFileName = existingFile.name ?: desiredFileName
                    updateSortOrderFileName(parentRel, currentFileName, finalFileName)
                }
            }
        } else {
            val created = createOrOverwriteHtmlFile(parentDir, desiredFileName, htmlContent)
            finalFileName = created?.name ?: desiredFileName
        }

        tempFile?.delete()

        val updatedRelPath = if (parentRel.isEmpty()) finalFileName else "$parentRel/$finalFileName"
        Result.success(updatedRelPath)
    }

    suspend fun renameItem(relativePath: String, newRawName: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanRel = relativePath.trim('/')
        val parentRel = cleanRel.substringBeforeLast('/', "")
        val oldFileName = cleanRel.substringAfterLast('/')

        val sanitized = sanitizeFileName(newRawName.removeSuffix(".html").removeSuffix(".HTML"))
        if (sanitized.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
        }

        val root = ensureVaultInitialized()
        val parentDir = resolveDirectory(root, parentRel)
            ?: return@withContext Result.failure(IllegalStateException("Parent directory not found"))
        val targetDoc = parentDir.findFile(oldFileName)
            ?: return@withContext Result.failure(IllegalStateException("Item not found"))

        val isDir = targetDoc.isDirectory
        val candidateName = if (isDir) sanitized else "$sanitized.html"
        val finalName = findUniqueName(parentDir, candidateName, isDir, ignoreExistingName = oldFileName)

        if (!isDir) {
            // Update <title> and <h1 class="note-title"> inside HTML before renaming
            val html = readDocumentFileText(targetDoc)
            val parsed = NoteHtmlCodec.parseHtml(html, sanitized).copy(title = sanitized)
            writeTextToDocumentFile(targetDoc, NoteHtmlCodec.serializeToHtml(parsed))
        }

        val renamed = try {
            targetDoc.renameTo(finalName)
        } catch (_: Exception) {
            false
        }

        if (!renamed) {
            return@withContext Result.failure(IllegalStateException("Failed to rename item"))
        }

        val actualNewName = targetDoc.name ?: finalName
        updateSortOrderFileName(parentRel, oldFileName, actualNewName)

        val newRel = if (parentRel.isEmpty()) actualNewName else "$parentRel/$actualNewName"
        Result.success(newRel)
    }

    suspend fun deleteItems(relativePaths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        val root = ensureVaultInitialized()
        var deletedCount = 0
        for (rel in relativePaths) {
            val cleanRel = rel.trim('/')
            val parentRel = cleanRel.substringBeforeLast('/', "")
            val fileName = cleanRel.substringAfterLast('/')
            val itemDoc = resolveFile(root, cleanRel) ?: continue
            val isDir = itemDoc.isDirectory
            if (deleteDocumentRecursively(itemDoc)) {
                deletedCount++
                vaultDao.deleteSortOrderItem(parentRel, fileName)
                if (isDir) {
                    vaultDao.deleteSortOrdersUnderPath(cleanRel)
                }
            }
        }
        Result.success(deletedCount)
    }

    /**
     * Moves one or more files/folders into [destinationDirectoryPath].
     * Includes Self-Containment Guard blocking moving a folder into itself or its own subfolders.
     */
    suspend fun moveItems(
        sourceRelativePaths: List<String>,
        destinationDirectoryPath: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        val destClean = destinationDirectoryPath.trim('/')

        // Self-Containment Guard check first
        for (src in sourceRelativePaths) {
            val srcClean = src.trim('/')
            if (destClean.equals(srcClean, ignoreCase = true) ||
                destClean.startsWith("$srcClean/", ignoreCase = true)
            ) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot move a folder into itself or one of its subfolders.")
                )
            }
        }

        val root = ensureVaultInitialized()
        val destDir = resolveDirectory(root, destClean)
            ?: return@withContext Result.failure(IllegalStateException("Destination directory not found"))

        var movedCount = 0
        for (src in sourceRelativePaths) {
            val srcClean = src.trim('/')
            val srcParent = srcClean.substringBeforeLast('/', "")
            if (srcParent.equals(destClean, ignoreCase = true)) {
                // Already in destination directory
                movedCount++
                continue
            }
            val srcDoc = resolveFile(root, srcClean) ?: continue
            val srcName = srcDoc.name ?: continue

            val copiedOk = copyDocumentTree(
                source = srcDoc,
                destParentDir = destDir,
                regenerateNoteUuids = false,
                isSameDirectoryCopy = false
            )
            if (copiedOk) {
                deleteDocumentRecursively(srcDoc)
                vaultDao.deleteSortOrderItem(srcParent, srcName)
                if (srcDoc.isDirectory) {
                    vaultDao.deleteSortOrdersUnderPath(srcClean)
                }
                movedCount++
            }
        }
        Result.success(movedCount)
    }

    /**
     * Copies one or more files/folders into [destinationDirectoryPath].
     * Regenerates `<meta name="note-id">` UUID for copied notes and resolves name collisions (` - Copy`).
     */
    suspend fun copyItems(
        sourceRelativePaths: List<String>,
        destinationDirectoryPath: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        val destClean = destinationDirectoryPath.trim('/')

        // Guard against copying a parent directory into its own descendant
        for (src in sourceRelativePaths) {
            val srcClean = src.trim('/')
            if (destClean.equals(srcClean, ignoreCase = true) ||
                destClean.startsWith("$srcClean/", ignoreCase = true)
            ) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot copy a folder into itself or its own subfolder.")
                )
            }
        }

        val root = ensureVaultInitialized()
        val destDir = resolveDirectory(root, destClean)
            ?: return@withContext Result.failure(IllegalStateException("Destination directory not found"))

        var copiedCount = 0
        for (src in sourceRelativePaths) {
            val srcClean = src.trim('/')
            val srcParent = srcClean.substringBeforeLast('/', "")
            val srcDoc = resolveFile(root, srcClean) ?: continue
            val sameDir = srcParent.equals(destClean, ignoreCase = true)
            if (copyDocumentTree(
                    source = srcDoc,
                    destParentDir = destDir,
                    regenerateNoteUuids = true,
                    isSameDirectoryCopy = sameDir
                )
            ) {
                copiedCount++
            }
        }
        Result.success(copiedCount)
    }

    suspend fun saveCroppedImageToAssets(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        val root = ensureVaultInitialized()
        val assetsDir = ensureAssetsFolder(root)
            ?: return@withContext Result.failure(IllegalStateException("Could not create assets folder"))

        val timestamp = assetTimestampFormat.format(Date())
        var fileName = "img_$timestamp.jpg"
        var counter = 1
        while (assetsDir.findFile(fileName) != null) {
            fileName = "img_${timestamp}_$counter.jpg"
            counter++
        }

        val imageDoc = createRawImageFileInDir(assetsDir, fileName)
            ?: return@withContext Result.failure(IllegalStateException("Could not create image file in assets"))

        val saved = context.contentResolver.openOutputStream(imageDoc.uri, "wt")?.use { out ->
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            ok
        } ?: false

        if (!saved) {
            return@withContext Result.failure(IllegalStateException("Failed to write image bytes"))
        }

        val actualName = imageDoc.name ?: fileName
        Result.success("assets/$actualName")
    }

    suspend fun resolveAssetUri(relativePath: String): Uri? = withContext(Dispatchers.IO) {
        val root = ensureVaultInitialized()
        resolveFile(root, relativePath.trim('/'))?.uri
    }

    // ---- Internal Helpers ----

    private fun ensureAssetsFolder(root: DocumentFile): DocumentFile? {
        return root.findFile("assets")?.takeIf { it.isDirectory }
            ?: root.createDirectory("assets")
    }

    private fun resolveDirectory(root: DocumentFile, relativePath: String): DocumentFile? {
        val clean = relativePath.trim('/')
        if (clean.isEmpty()) return root
        var current: DocumentFile = root
        for (segment in clean.split('/')) {
            if (segment.isBlank()) continue
            val next = current.findFile(segment)?.takeIf { it.isDirectory } ?: return null
            current = next
        }
        return current
    }

    private fun resolveFile(root: DocumentFile, relativePath: String): DocumentFile? {
        val clean = relativePath.trim('/')
        if (clean.isEmpty()) return root
        val parts = clean.split('/').filter { it.isNotBlank() }
        var current: DocumentFile = root
        for (i in parts.indices) {
            val next = current.findFile(parts[i]) ?: return null
            current = next
        }
        return current
    }

    private fun readDocumentFileText(doc: DocumentFile): String {
        return try {
            context.contentResolver.openInputStream(doc.uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun readNoteQuickHeader(doc: DocumentFile, fallbackTitle: String): NoteDocument {
        val html = readDocumentFileText(doc)
        return NoteHtmlCodec.parseHtml(html, fallbackTitle)
    }

    private fun writeTextToDocumentFile(doc: DocumentFile, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun createRawFileInDir(dir: DocumentFile, exactFileName: String): DocumentFile? {
        dir.findFile(exactFileName)?.delete()
        val uri = dir.uri
        if (uri.scheme == "file") {
            val parentFile = File(uri.path ?: return null)
            val target = File(parentFile, exactFileName)
            target.createNewFile()
            return DocumentFile.fromFile(target)
        }
        return dir.createFile("text/html", exactFileName)
    }

    private fun createRawImageFileInDir(dir: DocumentFile, exactFileName: String): DocumentFile? {
        dir.findFile(exactFileName)?.delete()
        val uri = dir.uri
        if (uri.scheme == "file") {
            val parentFile = File(uri.path ?: return null)
            val target = File(parentFile, exactFileName)
            target.createNewFile()
            return DocumentFile.fromFile(target)
        }
        return dir.createFile("image/jpeg", exactFileName)
    }

    private fun createOrOverwriteHtmlFile(dir: DocumentFile, fileName: String, html: String): DocumentFile? {
        val normalizedName = if (fileName.endsWith(".html", ignoreCase = true)) fileName else "$fileName.html"
        val existing = dir.findFile(normalizedName)
        if (existing != null && !existing.isDirectory) {
            writeTextToDocumentFile(existing, html)
            return existing
        }
        val created = createRawFileInDir(dir, normalizedName) ?: return null
        writeTextToDocumentFile(created, html)
        return created
    }

    private fun findUniqueName(
        parentDir: DocumentFile,
        desiredName: String,
        isDirectory: Boolean,
        ignoreExistingName: String? = null
    ): String {
        val existingNames = parentDir.listFiles()
            .mapNotNull { it.name }
            .filter { ignoreExistingName == null || !it.equals(ignoreExistingName, ignoreCase = true) }
            .map { it.lowercase() }
            .toSet()

        if (desiredName.lowercase() !in existingNames) return desiredName

        val base = if (isDirectory) desiredName else desiredName.removeSuffix(".html").removeSuffix(".HTML")
        val ext = if (isDirectory) "" else ".html"
        var counter = 2
        while (true) {
            val candidate = "$base ($counter)$ext"
            if (candidate.lowercase() !in existingNames) return candidate
            counter++
        }
    }

    private fun findUniqueCopyName(
        parentDir: DocumentFile,
        originalName: String,
        isDirectory: Boolean,
        forceCopySuffix: Boolean
    ): String {
        val existingNames = parentDir.listFiles()
            .mapNotNull { it.name?.lowercase() }
            .toSet()

        if (!forceCopySuffix && originalName.lowercase() !in existingNames) {
            return originalName
        }

        val base = if (isDirectory) originalName else originalName.removeSuffix(".html").removeSuffix(".HTML")
        val ext = if (isDirectory) "" else ".html"
        val firstCopy = "$base - Copy$ext"
        if (firstCopy.lowercase() !in existingNames) return firstCopy

        var counter = 2
        while (true) {
            val candidate = "$base - Copy ($counter)$ext"
            if (candidate.lowercase() !in existingNames) return candidate
            counter++
        }
    }

    private fun findUniqueUntitledName(parentDir: DocumentFile, ignoreName: String?): String {
        var idx = 1
        while (true) {
            val candidate = "Untitled $idx.html"
            if (candidate.equals(ignoreName, ignoreCase = true) || parentDir.findFile(candidate) == null) {
                return candidate
            }
            idx++
        }
    }

    private fun copyDocumentTree(
        source: DocumentFile,
        destParentDir: DocumentFile,
        regenerateNoteUuids: Boolean,
        isSameDirectoryCopy: Boolean
    ): Boolean {
        val srcName = source.name ?: return false
        val targetName = findUniqueCopyName(
            parentDir = destParentDir,
            originalName = srcName,
            isDirectory = source.isDirectory,
            forceCopySuffix = isSameDirectoryCopy
        )

        if (source.isDirectory) {
            val newDir = destParentDir.createDirectory(targetName) ?: return false
            for (child in source.listFiles()) {
                val childName = child.name ?: continue
                if (childName.startsWith(".tmp_")) continue
                copyDocumentTree(
                    source = child,
                    destParentDir = newDir,
                    regenerateNoteUuids = regenerateNoteUuids,
                    isSameDirectoryCopy = false
                )
            }
            return true
        } else if (srcName.endsWith(".html", ignoreCase = true)) {
            val rawHtml = readDocumentFileText(source)
            val baseTargetTitle = targetName.removeSuffix(".html").removeSuffix(".HTML")
            val parsed = NoteHtmlCodec.parseHtml(rawHtml, baseTargetTitle)
            val updatedDoc = parsed.copy(
                id = if (regenerateNoteUuids) UUID.randomUUID().toString() else parsed.id,
                title = if (parsed.title.isBlank() && baseTargetTitle.matches(VaultItem.UNTITLED_REGEX)) {
                    ""
                } else {
                    baseTargetTitle
                }
            )
            val newHtml = NoteHtmlCodec.serializeToHtml(updatedDoc)
            return createOrOverwriteHtmlFile(destParentDir, targetName, newHtml) != null
        } else {
            val created = createRawFileInDir(destParentDir, targetName) ?: return false
            return try {
                context.contentResolver.openInputStream(source.uri)?.use { input ->
                    context.contentResolver.openOutputStream(created.uri, "wt")?.use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun deleteDocumentRecursively(doc: DocumentFile): Boolean {
        if (doc.isDirectory) {
            for (child in doc.listFiles()) {
                deleteDocumentRecursively(child)
            }
        }
        return doc.delete()
    }

    private suspend fun updateSortOrderFileName(directoryPath: String, oldName: String, newName: String) {
        val cleanDir = directoryPath.trim('/')
        val currentOrders = vaultDao.getSortOrdersForDirectory(cleanDir)
        val matched = currentOrders.find { it.fileName == oldName } ?: return
        vaultDao.deleteSortOrderItem(cleanDir, oldName)
        vaultDao.upsertSortOrders(
            listOf(
                DirectorySortOrderEntity(
                    compositeKey = "$cleanDir|$newName",
                    directoryPath = cleanDir,
                    fileName = newName,
                    sortIndex = matched.sortIndex
                )
            )
        )
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(80)
    }

    private fun seedDefaultVaultSampleNotes(vaultRoot: File) {
        val assetsDir = File(vaultRoot, "assets").apply { mkdirs() }
        val sampleAssetFile = File(assetsDir, "img_20261116_113000.jpg")
        if (!sampleAssetFile.exists()) {
            try {
                val bmp = Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, 640f, 320f,
                        Color.parseColor("#1E293B"),
                        Color.parseColor("#4F46E5"),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, 640f, 320f, bgPaint)
                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(40, 255, 255, 255)
                    strokeWidth = 2f
                }
                for (x in 0..640 step 40) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), 320f, gridPaint)
                for (y in 0..320 step 40) canvas.drawLine(0f, y.toFloat(), 640f, y.toFloat(), gridPaint)
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 28f
                    isFakeBoldText = true
                }
                canvas.drawText("Kinematics Diagram • Vault Asset", 48f, 170f, textPaint)
                sampleAssetFile.outputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            } catch (_: Exception) {
            }
        }

        val studyDir = File(vaultRoot, "Study").apply { mkdirs() }
        val physicsNote = File(studyDir, "Physics Notes.html")
        if (!physicsNote.exists()) {
            physicsNote.writeText(
                """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="note-id" content="550e8400-e29b-41d4-a716-446655440000">
                  <title>Physics Notes</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; max-width: 720px; margin: 32px auto; padding: 0 20px; line-height: 1.65; color: #0F172A; }
                    table { border-collapse: collapse; margin: 16px 0; }
                    td, th { border: 1px solid #CBD5E1; padding: 10px; vertical-align: top; }
                    details { border: 1px solid #E2E8F0; border-radius: 12px; padding: 12px 16px; margin: 12px 0; background: #F8FAFC; }
                    summary { font-weight: 600; cursor: pointer; }
                  </style>
                </head>
                <body>
                  <h1 class="note-title">Physics Notes</h1>
                  <div class="note-body">
                    <p>Kinetic energy: <span class="math" data-tex="K = \frac{1}{2}mv^2">K = ½mv²</span></p>
                    <p><span style="color:#DC2626;font-weight:bold;font-size:18px;">Important</span> <span style="background-color:rgba(255,235,59,0.45);">highlight</span>.</p>
                    <img src="assets/img_20261116_113000.jpg" style="max-width:100%;border-radius:12px;">
                    <table>
                      <colgroup><col style="width:140px"><col style="width:180px"></colgroup>
                      <tbody><tr><td>Mass</td><td>5 kg</td></tr><tr><td>Velocity</td><td>12 m/s</td></tr></tbody>
                    </table>
                    <details open><summary>Derivation</summary><p>Work <span class="math" data-tex="W = F \cdot d">W = F · d</span></p></details>
                  </div>
                </body>
                </html>
                """.trimIndent()
            )
        }

        val projectIdeas = File(vaultRoot, "Project Ideas.html")
        if (!projectIdeas.exists()) {
            projectIdeas.writeText(
                """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="note-id" content="770e8400-e29b-41d4-a716-446655440011">
                  <title>Project Ideas</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; max-width: 720px; margin: 32px auto; padding: 0 20px; line-height: 1.65; color: #0F172A; }
                    table { border-collapse: collapse; margin: 16px 0; }
                    td, th { border: 1px solid #CBD5E1; padding: 10px; vertical-align: top; }
                    details { border: 1px solid #E2E8F0; border-radius: 12px; padding: 12px 16px; margin: 12px 0; background: #F8FAFC; }
                    summary { font-weight: 600; cursor: pointer; }
                  </style>
                </head>
                <body>
                  <h1 class="note-title">Project Ideas</h1>
                  <div class="note-body">
                    <p><span style="font-weight:bold;font-size:18px;">VaultNotes v2.2 Architecture</span> — 100% offline HTML5 notes with <span style="background-color:rgba(255,235,59,0.45);">lossless Jsoup serialization</span> and inline LaTeX math <span class="math" data-tex="E = mc^2">E = mc²</span>.</p>
                    <table>
                      <colgroup><col style="width:150px"><col style="width:200px"></colgroup>
                      <tbody><tr><td>Module</td><td>Status</td></tr><tr><td>Rich Block Editor</td><td>Production Ready</td></tr><tr><td>SAF File Manager</td><td>Atomic Write Enabled</td></tr></tbody>
                    </table>
                    <details open><summary>Next Milestones</summary><p>Test custom drag-sort ordering and multi-select batch move/copy across nested folders.</p></details>
                  </div>
                </body>
                </html>
                """.trimIndent()
            )
        }

        val untitled1 = File(vaultRoot, "Untitled 1.html")
        if (!untitled1.exists()) {
            untitled1.writeText(
                """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="note-id" content="880e8400-e29b-41d4-a716-446655440022">
                  <title></title>
                </head>
                <body>
                  <h1 class="note-title"></h1>
                  <div class="note-body">
                    <p>Quick scratchpad note — tap to add a title or insert math formulas with Σ.</p>
                  </div>
                </body>
                </html>
                """.trimIndent()
            )
        }
    }
}
