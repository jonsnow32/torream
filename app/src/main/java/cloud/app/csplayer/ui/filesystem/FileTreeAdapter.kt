package cloud.app.csplayer.ui.filesystem

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemFileTreeNodeBinding
import cloud.app.csplayer.utils.KUniFile
import timber.log.Timber
import kotlin.collections.filter


/**
 * Represents a node in the file tree
 */
data class FileTreeNode(
  val file: KUniFile,
  val name: String,
  val isDirectory: Boolean,
  val level: Int,
  var isExpanded: Boolean = false,
  var children: List<FileTreeNode>? = null
) {
  /**
   * Convert to serializable data for saving to preferences
   */
  fun toSerializable(): SerializableFileNode {
    return SerializableFileNode(
      path = file.filePath ?: file.uri.toString(),
      name = name,
      isDirectory = isDirectory
    )
  }

  companion object {
    /**
     * Create FileTreeNode from SerializableFileNode
     * Handles both file paths and SAF URIs
     */
    fun fromSerializable(context: android.content.Context, serializable: SerializableFileNode, level: Int = 0): FileTreeNode? {
      Timber.d("fromSerializable() - path: ${serializable.path}, name: ${serializable.name}")

      val uniFile = try {
        if (serializable.path.startsWith("content://")) {
          // SAF URI
          Timber.d("  Parsing as SAF URI...")
          val uri = android.net.Uri.parse(serializable.path)
          Timber.d("  URI parsed: $uri")
          val result = KUniFile.fromUri(context, uri)
          Timber.d("  KUniFile.fromUri result: ${result?.name ?: "null"}")
          result
        } else {
          // File path
          Timber.d("  Parsing as file path...")
          KUniFile.fromFile(context, java.io.File(serializable.path))
        }
      } catch (e: Exception) {
        Timber.e(e, "  ✗ Failed to create KUniFile from: ${serializable.path}")
        null
      }

      return if (uniFile != null) {
        Timber.d("  ✓ Successfully created FileTreeNode")
        FileTreeNode(
          file = uniFile,
          name = serializable.name,
          isDirectory = serializable.isDirectory,
          level = level
        )
      } else {
        Timber.w("  ✗ uniFile is null, returning null")
        null
      }
    }
  }
}

/**
 * Serializable representation of FileTreeNode for saving to preferences
 * Only contains essential data (path/uri, name, isDirectory)
 */
data class SerializableFileNode(
  val path: String, // Can be file path or URI string
  val name: String,
  val isDirectory: Boolean
)

/**
 * Adapter for file tree RecyclerView
 */
class FileTreeAdapter(
  private val context: android.content.Context,
  private val onNodeClick: (FileTreeNode) -> Unit,
  private val onNodeDeleted: ((FileTreeNode) -> Unit)? = null
) : RecyclerView.Adapter<FileTreeAdapter.ViewHolder>() {

  private val visibleNodes = mutableListOf<FileTreeNode>()
  private val expandedNodes = mutableSetOf<String>() // Track expanded nodes by path

  fun setRootNodes(nodes: List<FileTreeNode>) {
    visibleNodes.clear()
    visibleNodes.addAll(nodes)
    notifyDataSetChanged()
  }

  fun expandNode(node: FileTreeNode) {
    val position = visibleNodes.indexOf(node)
    if (position == -1) return

    // Load children if not loaded
    if (node.children == null) {
      loadChildren(node)
    }

    node.isExpanded = true
    expandedNodes.add(node.file.filePath ?: "")

    // Insert children after this node
    val children = node.children ?: emptyList()
    visibleNodes.addAll(position + 1, children)

    notifyItemChanged(position) // Update arrow icon
    notifyItemRangeInserted(position + 1, children.size)
  }

  fun collapseNode(node: FileTreeNode) {
    val position = visibleNodes.indexOf(node)
    if (position == -1) return

    node.isExpanded = false
    expandedNodes.remove(node.file.filePath ?: "")

    // Remove all children (and their children recursively)
    val childrenToRemove = mutableListOf<FileTreeNode>()
    var i = position + 1
    while (i < visibleNodes.size && visibleNodes[i].level > node.level) {
      childrenToRemove.add(visibleNodes[i])
      i++
    }

    visibleNodes.removeAll(childrenToRemove)

    notifyItemChanged(position) // Update arrow icon
    if (childrenToRemove.isNotEmpty()) {
      notifyItemRangeRemoved(position + 1, childrenToRemove.size)
    }
  }

  private fun loadChildren(node: FileTreeNode) {
    try {
      val files = node.file.listFiles()
      if (files != null) {
        val children = files
          .filter { file ->
            // Show both directories and files, skip hidden/system items
            val name = file.name ?: return@filter false
            // Skip hidden files/folders (starting with .)
            if (name.startsWith(".")) return@filter false
            // Skip system directories
            if (file.isDirectory && isSystemDirectory(name)) return@filter false
            // Show everything else (files and folders)
            true
          }
          .sortedWith(compareBy(
            // Sort directories first, then files
            { !it.isDirectory },
            // Then sort by name
            { it.name?.lowercase() }
          ))
          .map { file ->
            FileTreeNode(
              file = file,
              name = file.name ?: "Unknown",
              isDirectory = file.isDirectory,
              level = node.level + 1
            )
          }

        node.children = children
        Timber.d("Loaded ${children.size} children (dirs + files) for ${node.name}")
      } else {
        node.children = emptyList()
        Timber.w("No files found in ${node.name}")
      }
    } catch (e: Exception) {
      Timber.e(e, "Error loading children for ${node.name}")
      node.children = emptyList()
    }
  }

  private fun isSystemDirectory(name: String): Boolean {
    return name.lowercase() in setOf(
      "android", "data", "obb", "cache", ".thumbnails", ".trash"
    )
  }

  /**
   * Select a folder and all its children recursively
   */
  private fun selectFolderWithChildren(node: FileTreeNode) {
    // Add this node
    FileTreePreferences.addSelectedFolder(context, node)

    // Load children if not loaded yet
    if (node.children == null && node.isDirectory) {
      loadChildren(node)
    }

    // Recursively add all children
    node.children?.forEach { child ->
      selectFolderWithChildren(child)
    }
  }

  /**
   * Unselect a folder, all its children, and all its parent folders
   */
  private fun unselectFolderWithChildrenAndParents(node: FileTreeNode) {
    // Remove this node
    FileTreePreferences.removeSelectedFolder(context, node)

    // Load children if not loaded yet
    if (node.children == null && node.isDirectory) {
      loadChildren(node)
    }

    // Recursively remove all children
    node.children?.forEach { child ->
      unselectFolderWithChildren(child)
    }

    // Unselect all parent folders
    unselectParentFolders(node)
  }

  /**
   * Recursively unselect a folder and all its children
   */
  private fun unselectFolderWithChildren(node: FileTreeNode) {
    // Remove this node
    FileTreePreferences.removeSelectedFolder(context, node)

    // Load children if not loaded yet
    if (node.children == null && node.isDirectory) {
      loadChildren(node)
    }

    // Recursively remove all children
    node.children?.forEach { child ->
      unselectFolderWithChildren(child)
    }
  }

  /**
   * Unselect all parent folders of the given node
   */
  private fun unselectParentFolders(node: FileTreeNode) {
    // Find parent node by comparing paths
    val nodePath = node.file.filePath ?: return

    // Check all visible nodes for potential parents
    visibleNodes.forEach { potentialParent ->
      val parentPath = potentialParent.file.filePath ?: return@forEach

      // If this node's path starts with parent's path and is a child
      if (nodePath.startsWith(parentPath) &&
          nodePath != parentPath &&
          potentialParent.level < node.level) {

        // Unselect parent
        FileTreePreferences.removeSelectedFolder(context, potentialParent)
        Timber.d("Unselected parent folder: ${potentialParent.name}")
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = ItemFileTreeNodeBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(visibleNodes[position])
  }

  override fun getItemCount(): Int = visibleNodes.size

  inner class ViewHolder(
    private val binding: ItemFileTreeNodeBinding
  ) : RecyclerView.ViewHolder(binding.root) {

    fun bind(node: FileTreeNode) {
      binding.tvName.text = node.name

      // Set indentation based on level
      val indent = node.level * 24 // 24dp per level
      binding.root.setPadding(indent, 8, 16, 8)

      // Show expand/collapse icon for directories
      if (node.isDirectory) {
        binding.ivIcon.visibility = View.VISIBLE
        binding.ivIcon.rotation = if (node.isExpanded) 90f else 0f
        binding.ivIcon.setImageResource(R.drawable.ic_keyboard_arrow_right)
      } else {
        binding.ivIcon.visibility = View.GONE
      }

      // Set folder icon
      binding.ivFolder.setImageResource(
        if (node.isDirectory) R.drawable.ic_folder else R.drawable.ic_file
      )

      // Handle delete button
      binding.btnDelete.setOnClickListener {
        Timber.d("Delete clicked for ${node.name}")

        // Remove from preferences
        FileTreePreferences.removeSelectedFolder(context, node)


        // Notify callback
        onNodeDeleted?.invoke(node)
      }

      binding.root.setOnClickListener {
        onNodeClick(node)
      }
    }
  }
}
