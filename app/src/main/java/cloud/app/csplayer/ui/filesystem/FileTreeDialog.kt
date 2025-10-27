package cloud.app.csplayer.ui.filesystem

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.DialogFileTreeBinding
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.KUniFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Dialog showing a file tree structure with expandable folders.
 * Uses SAF (Storage Access Framework) similar to Poweramp.
 * User picks folders via ACTION_OPEN_DOCUMENT_TREE, then app scans content.
 */
@AndroidEntryPoint
class FileTreeDialog : DialogFragment() {
  private var binding by autoCleared<DialogFileTreeBinding>()
  private lateinit var adapter: FileTreeAdapter
  private var onFolderSelected: ((KUniFile) -> Unit)? = null

  // SAF Document Tree picker - Similar to Poweramp
  private val openDocumentTreeLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    Timber.d("=== openDocumentTreeLauncher callback fired ===")
    Timber.d("URI received: $uri")

    if (uri != null) {
      Timber.d("Document tree selected: $uri")

      // Take persistable permission
      val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                      Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      try {
        Timber.d("Taking persistable permission...")
        requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
        Timber.d("✓ Persistable permission granted for: $uri")

        // Verify permission was taken
        val persistedUris = requireContext().contentResolver.persistedUriPermissions
        Timber.d("Total persisted permissions: ${persistedUris.size}")
        persistedUris.forEach {
          Timber.d("  - ${it.uri} (read: ${it.isReadPermission}, write: ${it.isWritePermission})")
        }
      } catch (e: Exception) {
        Timber.e(e, "❌ Failed to take persistable permission")
      }

      // Scan the selected folder
      scanFolder(uri)
    } else {
      Timber.w("❌ No folder selected (URI is null - user cancelled)")
      binding.progressBar.visibility = View.GONE
      binding.emptyView.visibility = View.VISIBLE
      binding.emptyView.text = getString(R.string.no_folder_selected)
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    Timber.d("onCreateDialog() called - FileTreeDialog")
    binding = DialogFileTreeBinding.inflate(layoutInflater)

    setupRecyclerView()
    showSavedFoldersOrPrompt()

    // Setup Add Folder button
    binding.btnAddFolder.setOnClickListener {
      Timber.d("Add Folder button clicked (in layout)")
      openFolderPicker()
    }

    val dialog = MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.select_folder)
      .setView(binding.root)
      .create()

    Timber.d("Dialog created, returning...")
    return dialog
  }

  private fun showSavedFoldersOrPrompt() {
    // Load saved folders from preferences
    val savedFolders = FileTreePreferences.loadSelectedFolders(requireContext())

    if (savedFolders.isEmpty()) {
      // No saved folders, show prompt
      binding.progressBar.visibility = View.GONE
      binding.recyclerView.visibility = View.GONE
      binding.emptyView.visibility = View.VISIBLE
      binding.emptyView.text = getString(R.string.tap_add_folder_to_start)
    } else {
      // Show saved folders
      binding.emptyView.visibility = View.GONE
      binding.recyclerView.visibility = View.VISIBLE
      adapter.setRootNodes(savedFolders)
    }
  }

  private fun openFolderPicker() {
    Timber.d("Opening SAF folder picker")

    // Optional: Set initial URI to guide user
    val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      DocumentsContract.buildRootUri(
        "com.android.externalstorage.documents",
        "primary"
      )
    } else {
      null
    }

    openDocumentTreeLauncher.launch(initialUri)
  }

  private fun scanFolder(uri: Uri) {
    Timber.d("scanFolder() called with URI: $uri")
    binding.progressBar.visibility = View.VISIBLE
    binding.recyclerView.visibility = View.GONE
    binding.emptyView.visibility = View.GONE

    // Scan folder in background
    Thread {
      try {
        Timber.d("Creating DocumentFile from tree URI...")
        val documentFile = DocumentFile.fromTreeUri(requireContext(), uri)
        if (documentFile == null) {
          Timber.e("DocumentFile.fromTreeUri returned null for URI: $uri")
          showError("Cannot access selected folder (DocumentFile is null)")
          return@Thread
        }

        if (!documentFile.exists()) {
          Timber.e("DocumentFile exists() returned false")
          showError("Cannot access selected folder (doesn't exist)")
          return@Thread
        }

        Timber.d("✓ DocumentFile created: ${documentFile.name}, isDirectory: ${documentFile.isDirectory}")

        // Convert to KUniFile
        Timber.d("Converting to KUniFile...")
        val kuniFile = KUniFile.fromUri(requireContext(), uri)
        if (kuniFile == null) {
          Timber.e("KUniFile.fromUri returned null for URI: $uri")
          showError("Cannot create KUniFile from URI")
          return@Thread
        }

        Timber.d("✓ KUniFile created: ${kuniFile.name}, uri: ${kuniFile.uri}")

        // Create FileTreeNode for the selected folder
        val folderNode = FileTreeNode(
          file = kuniFile,
          name = documentFile.name ?: "Selected Folder",
          isDirectory = true,
          level = 0
        )

        Timber.d("✓ FileTreeNode created: ${folderNode.name}")

        // Save to preferences
        Timber.d("Saving to preferences...")
        FileTreePreferences.addSelectedFolder(requireContext(), folderNode)
        Timber.d("✓ Saved to preferences")

        // Reload all saved folders
        Timber.d("Reloading all folders from preferences...")
        val allFolders = FileTreePreferences.loadSelectedFolders(requireContext())
        Timber.d("✓ Loaded ${allFolders.size} folders from preferences")

        requireActivity().runOnUiThread {
          binding.progressBar.visibility = View.GONE

          if (allFolders.isEmpty()) {
            Timber.w("No folders loaded! Showing empty view")
            binding.recyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.text = getString(R.string.no_folders_available)
          } else {
            Timber.d("Setting ${allFolders.size} folders to adapter")
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            adapter.setRootNodes(allFolders)
            Timber.d("✓ Adapter updated with ${allFolders.size} folders")
          }

          Timber.d("✅ Folder added successfully: ${folderNode.name}")
        }
      } catch (e: Exception) {
        Timber.e(e, "❌ Error scanning folder")
        showError("Error scanning folder: ${e.message}")
      }
    }.start()
  }

  private fun showError(message: String) {
    requireActivity().runOnUiThread {
      binding.progressBar.visibility = View.GONE
      binding.recyclerView.visibility = View.GONE
      binding.emptyView.visibility = View.VISIBLE
      binding.emptyView.text = message
    }
  }

  private fun setupRecyclerView() {
    adapter = FileTreeAdapter(
      context = requireContext(),
      onNodeClick = { node ->
        if (node.isDirectory) {
          if (node.isExpanded) {
            adapter.collapseNode(node)
          } else {
            adapter.expandNode(node)
          }
        } else {
          // File selected
          onFolderSelected?.invoke(node.file)
          dismiss()
        }
      },
      onNodeDeleted = { node ->
        Timber.d("Node deleted from preferences: ${node.name}")
        // Reload folders after deletion
        showSavedFoldersOrPrompt()
      }
    )

    binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    binding.recyclerView.adapter = adapter
  }

  companion object {
    fun newInstance(
      onFolderSelected: (KUniFile) -> Unit
    ): FileTreeDialog {
      return FileTreeDialog().apply {
        this.onFolderSelected = onFolderSelected
      }
    }
  }
}
