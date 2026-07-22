package com.adamfoerster.mdhabits.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android [VaultFileSystem]. When the user has picked a vault folder, all access goes through the
 * Storage Access Framework using the persisted tree Uri in [AppSettings.vaultRef]; before a vault
 * is chosen it falls back to an app-private folder so data is never lost. The vault ref is read on
 * every call so a folder picked mid-session takes effect immediately.
 *
 * All operations are best-effort: a revoked permission or provider error degrades to an empty
 * list / null read instead of crashing (the state flows in the repositories keep the UI working).
 */
class AndroidVaultFileSystem(
    private val context: Context,
    private val settings: AppSettings,
) : VaultFileSystem {

    private fun treeUri(): Uri? = settings.vaultRef?.let { runCatching { Uri.parse(it) }.getOrNull() }

    private fun fallbackRoot(): File = File(context.filesDir, "vault")

    override suspend fun list(dir: String): List<String> = withContext(Dispatchers.IO) {
        val tree = treeUri()
        runCatching {
            if (tree != null) {
                listTree(tree, dir)
            } else {
                File(fallbackRoot(), dir).listFiles()?.map { it.name }.orEmpty()
            }
        }.getOrDefault(emptyList()).filter { it.endsWith(MD) }
    }

    override suspend fun read(dir: String, name: String): String? = withContext(Dispatchers.IO) {
        val tree = treeUri()
        runCatching {
            if (tree != null) {
                readTree(tree, dir, name)
            } else {
                File(File(fallbackRoot(), dir), name).takeIf { it.isFile }?.readText()
            }
        }.getOrNull()
    }

    override suspend fun listIn(ref: String, dir: String): List<String> = withContext(Dispatchers.IO) {
        val tree = runCatching { Uri.parse(ref) }.getOrNull() ?: return@withContext emptyList()
        runCatching { listTree(tree, dir) }.getOrDefault(emptyList()).filter { it.endsWith(MD) }
    }

    override suspend fun readIn(ref: String, dir: String, name: String): String? =
        withContext(Dispatchers.IO) {
            val tree = runCatching { Uri.parse(ref) }.getOrNull() ?: return@withContext null
            runCatching { readTree(tree, dir, name) }.getOrNull()
        }

    override suspend fun write(dir: String, name: String, content: String): Unit =
        withContext(Dispatchers.IO) {
            val tree = treeUri()
            runCatching {
                if (tree != null) {
                    val dirUri = findOrCreateDir(tree, dir) ?: return@runCatching
                    val fileUri = findChild(tree, dirUri, name)
                        ?: DocumentsContract.createDocument(
                            context.contentResolver, dirUri, MIME_MARKDOWN, name,
                        )
                        ?: return@runCatching
                    // "wt" truncates so a shorter note doesn't leave stale trailing content.
                    context.contentResolver.openOutputStream(fileUri, "wt")?.use {
                        it.write(content.encodeToByteArray())
                    }
                } else {
                    val folder = File(fallbackRoot(), dir).apply { mkdirs() }
                    File(folder, name).writeText(content)
                }
            }
        }

    override suspend fun delete(dir: String, name: String): Unit = withContext(Dispatchers.IO) {
        val tree = treeUri()
        runCatching {
            if (tree != null) {
                resolveFile(tree, dir, name)?.let {
                    DocumentsContract.deleteDocument(context.contentResolver, it)
                }
            } else {
                File(File(fallbackRoot(), dir), name).delete()
            }
        }
    }

    // ---- SAF helpers ----

    private fun listTree(tree: Uri, dir: String): List<String> {
        val dirUri = findChild(tree, rootDocUri(tree), dir) ?: return emptyList()
        return listChildren(tree, dirUri).map { it.second }
    }

    private fun readTree(tree: Uri, dir: String, name: String): String? {
        val fileUri = resolveFile(tree, dir, name) ?: return null
        return context.contentResolver.openInputStream(fileUri)?.use {
            it.readBytes().decodeToString()
        }
    }

    private fun rootDocUri(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun resolveFile(tree: Uri, dir: String, name: String): Uri? {
        val dirUri = findChild(tree, rootDocUri(tree), dir) ?: return null
        return findChild(tree, dirUri, name)
    }

    private fun findOrCreateDir(tree: Uri, dir: String): Uri? =
        findChild(tree, rootDocUri(tree), dir)
            ?: DocumentsContract.createDocument(
                context.contentResolver, rootDocUri(tree), DocumentsContract.Document.MIME_TYPE_DIR, dir,
            )

    /** (documentUri, displayName) of every child of [parentDocUri]. */
    private fun listChildren(tree: Uri, parentDocUri: Uri): List<Pair<Uri, String>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getDocumentId(parentDocUri),
        )
        val result = mutableListOf<Pair<Uri, String>>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                result += docUri to cursor.getString(1)
            }
        }
        return result
    }

    private fun findChild(tree: Uri, parentDocUri: Uri, displayName: String): Uri? =
        listChildren(tree, parentDocUri).firstOrNull { it.second == displayName }?.first

    private companion object {
        const val MD = ".md"
        const val MIME_MARKDOWN = "text/markdown"
    }
}
