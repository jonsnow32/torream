package cloud.streamless.torream.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import cloud.streamless.torream.R
import cloud.streamless.torream.ai.AiManager
import cloud.streamless.torream.ai.rename.BatchRenamer
import cloud.streamless.torream.databinding.DialogBatchRenameBinding
import cloud.streamless.torream.ui.dialog.DockingDialog
import cloud.streamless.torream.utils.UIHelper.dismissSafe
import cloud.streamless.torream.utils.UnifiedFileFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BatchRenameDialog : DockingDialog() {

    companion object {
        private const val ARG_FILE_URIS = "file_uris"

        fun newInstance(fileUris: List<String>) = BatchRenameDialog().apply {
            arguments = Bundle().apply { putStringArrayList(ARG_FILE_URIS, ArrayList(fileUris)) }
        }
    }

    var onRenamed: (() -> Unit)? = null

    @Inject lateinit var aiManager: AiManager

    private var binding: DialogBatchRenameBinding? = null
    private val fileUris by lazy { arguments?.getStringArrayList(ARG_FILE_URIS)?.toList() ?: emptyList() }

    private class RenameRow(val uri: String, val newName: String, val checkBox: CheckBox)
    private val rows = mutableListOf<RenameRow>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val b = DialogBatchRenameBinding.inflate(inflater, container, false)
        binding = b
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = binding ?: return
        if (fileUris.isEmpty()) {
            dialog?.dismissSafe(activity)
            return
        }

        b.cancelBtt.setOnClickListener { dialog?.dismissSafe(activity) }
        b.applyBtt.setOnClickListener { applyRenames() }
        b.applyBtt.isEnabled = false

        loadSuggestions()
    }

    private fun loadSuggestions() {
        val b = binding ?: return
        val ctx = context ?: return

        b.loadingContainer.isVisible = true
        b.reviewScroll.isGone = true
        b.errorText.isGone = true

        viewLifecycleOwner.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                fileUris.mapNotNull { UnifiedFileFactory.fromPath(ctx, it) }
            }
            if (files.isEmpty()) {
                showError(getString(R.string.batch_rename_no_files))
                return@launch
            }

            val result = BatchRenamer.suggestNames(
                files.map { it.name },
                chatCompletion = aiManager::defaultChatCompletion
            )

            result.onSuccess { pairs -> showReview(files.map { it.uri.toString() }, pairs) }
                .onFailure { e ->
                    Timber.e(e, "Batch rename suggestion failed")
                    showError(e.message ?: getString(R.string.batch_rename_failed))
                }
        }
    }

    private fun showReview(fileUris: List<String>, pairs: List<Pair<String, String>>) {
        val b = binding ?: return
        val ctx = context ?: return
        b.loadingContainer.isGone = true
        b.reviewContainer.removeAllViews()
        rows.clear()

        fileUris.zip(pairs).forEach { (uri, pair) ->
            val (oldName, newName) = pair
            val row = LayoutInflater.from(ctx).inflate(R.layout.item_batch_rename_row, b.reviewContainer, false)
            val checkBox = row.findViewById<CheckBox>(R.id.renameCheckBox)
            checkBox.isChecked = oldName != newName && newName.isNotBlank()
            row.findViewById<TextView>(R.id.oldNameText).text = oldName
            row.findViewById<TextView>(R.id.newNameText).text = newName
            b.reviewContainer.addView(row)
            rows.add(RenameRow(uri, newName, checkBox))
        }

        b.reviewScroll.isVisible = true
        b.applyBtt.isEnabled = true
    }

    private fun showError(message: String) {
        val b = binding ?: return
        b.loadingContainer.isGone = true
        b.errorText.isVisible = true
        b.errorText.text = message
    }

    private fun applyRenames() {
        val ctx = context ?: return
        val b = binding ?: return
        val toApply = rows.filter { it.checkBox.isChecked && it.newName.isNotBlank() }
        if (toApply.isEmpty()) {
            dialog?.dismissSafe(activity)
            return
        }
        b.applyBtt.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val successCount = withContext(Dispatchers.IO) {
                toApply.count { row ->
                    UnifiedFileFactory.fromPath(ctx, row.uri)?.renameTo(row.newName) == true
                }
            }
            Toast.makeText(
                ctx,
                getString(R.string.batch_rename_result, successCount, toApply.size),
                Toast.LENGTH_SHORT
            ).show()
            onRenamed?.invoke()
            dialog?.dismissSafe(activity)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
