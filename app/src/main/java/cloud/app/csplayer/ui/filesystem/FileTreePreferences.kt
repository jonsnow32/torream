package cloud.app.csplayer.ui.filesystem

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Helper class to save and load FileTreeNode selections to/from SharedPreferences
 * Uses JSONObject/JSONArray for serialization (no external dependency needed)
 */
object FileTreePreferences {

    private const val KEY_SELECTED_FOLDERS = "selected_folders"

    /**
     * Save selected folders to SharedPreferences
     */
    fun saveSelectedFolders(context: Context, nodes: List<FileTreeNode>) {
        try {
            Timber.d("saveSelectedFolders() - Saving ${nodes.size} folders")
            val jsonArray = JSONArray()
            nodes.forEach { node ->
                val path = node.file.filePath ?: node.file.uri.toString()
                Timber.d("  - Serializing: ${node.name} (path: $path)")
                val jsonObject = JSONObject().apply {
                    put("path", path)
                    put("name", node.name)
                    put("isDirectory", node.isDirectory)
                }
                jsonArray.put(jsonObject)
            }

            val jsonString = jsonArray.toString()
            Timber.d("JSON to save (length: ${jsonString.length}): ${jsonString.take(200)}...")

            getPreferences(context).edit {
                putString(KEY_SELECTED_FOLDERS, jsonString)
            }

            Timber.d("✓ Saved ${nodes.size} folders to preferences")
        } catch (e: Exception) {
            Timber.e(e, "❌ Error saving selected folders")
        }
    }

    /**
     * Load selected folders from SharedPreferences
     */
    fun loadSelectedFolders(context: Context): List<FileTreeNode> {
        try {
            val json = getPreferences(context).getString(KEY_SELECTED_FOLDERS, null)
            if (json.isNullOrEmpty()) {
                Timber.d("loadSelectedFolders() - No saved folders found (empty/null)")
                return emptyList()
            }

            Timber.d("loadSelectedFolders() - JSON found (length: ${json.length}): ${json.take(200)}...")

            val jsonArray = JSONArray(json)
            val nodes = mutableListOf<FileTreeNode>()

            Timber.d("Parsing ${jsonArray.length()} folder entries...")
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val serializable = SerializableFileNode(
                    path = jsonObject.getString("path"),
                    name = jsonObject.getString("name"),
                    isDirectory = jsonObject.getBoolean("isDirectory")
                )

                Timber.d("  [$i] Deserializing: ${serializable.name} (path: ${serializable.path})")

                FileTreeNode.fromSerializable(context, serializable)?.let { node ->
                    nodes.add(node)
                    Timber.d("    ✓ Created FileTreeNode: ${node.name}")
                } ?: run {
                    Timber.w("    ✗ Failed to create FileTreeNode from ${serializable.name}")
                }
            }

            Timber.d("✓ Loaded ${nodes.size} folders from preferences")
            return nodes
        } catch (e: Exception) {
            Timber.e(e, "❌ Error loading selected folders")
            return emptyList()
        }
    }

    /**
     * Add a folder to selected folders
     */
    fun addSelectedFolder(context: Context, node: FileTreeNode) {
        val currentFolders = loadSelectedFolders(context).toMutableList()

        // Check if already exists
        if (currentFolders.none { it.file.filePath == node.file.filePath }) {
            currentFolders.add(node)
            saveSelectedFolders(context, currentFolders)
            Timber.d("Added folder: ${node.name}")
        } else {
            Timber.d("Folder already exists: ${node.name}")
        }
    }

    /**
     * Remove a folder from selected folders
     */
    fun removeSelectedFolder(context: Context, node: FileTreeNode) {
        val currentFolders = loadSelectedFolders(context).toMutableList()
        val removed = currentFolders.removeAll { it.file.filePath == node.file.filePath }

        if (removed) {
            saveSelectedFolders(context, currentFolders)
            Timber.d("Removed folder: ${node.name}")
        } else {
            Timber.d("Folder not found: ${node.name}")
        }
    }

    /**
     * Check if a folder is already selected
     */
    fun isFolderSelected(context: Context, node: FileTreeNode): Boolean {
        val selectedFolders = loadSelectedFolders(context)
        return selectedFolders.any { it.file.filePath == node.file.filePath }
    }

    /**
     * Clear all selected folders
     */
    fun clearSelectedFolders(context: Context) {
        getPreferences(context).edit {
            remove(KEY_SELECTED_FOLDERS)
        }

        Timber.d("Cleared all selected folders")
    }

    /**
     * Get selected folder paths as a Set<String>
     */
    fun getSelectedFolderPaths(context: Context): Set<String> {
        val folders = loadSelectedFolders(context)
        return folders.mapNotNull { it.file.filePath }.toSet()
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }
}

