package cloud.app.csplayer.torrent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.FragmentTorrentBinding
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fragment for managing torrent downloads
 *
 * Usage example:
 *
 * // In your MainActivity or any activity
 * supportFragmentManager.beginTransaction()
 *     .replace(R.id.container, TorrentFragment())
 *     .commit()
 */

@AndroidEntryPoint
class TorrentFragment : Fragment() {
  private var binding by autoCleared<FragmentTorrentBinding>()
  private val viewModel: TorrentViewModel by viewModels()
  private lateinit var adapter: TorrentAdapter

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    val allGranted = permissions.values.all { it }
    if (allGranted) {
      Timber.d("TorrentFragment: Permissions granted")
    } else {
      Toast.makeText(
        requireContext(),
        "Storage permissions required for torrents",
        Toast.LENGTH_SHORT
      ).show()
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    binding = FragmentTorrentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    checkAndRequestPermissions()
    setupRecyclerView()
    setupObservers()
    setupListeners()
  }

  private fun checkAndRequestPermissions() {
    val permissions = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(
          requireContext(),
          Manifest.permission.POST_NOTIFICATIONS
        )
        != PackageManager.PERMISSION_GRANTED
      ) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
      }
    }

    if (permissions.isNotEmpty()) {
      requestPermissionLauncher.launch(permissions.toTypedArray())
    }
  }

  private fun setupRecyclerView() {
    adapter = TorrentAdapter(
      onPauseClick = { torrent ->
        viewModel.pauseTorrent(torrent.infoHash)
      },
      onResumeClick = { torrent ->
        viewModel.resumeTorrent(torrent.infoHash)
      },
      onRemoveClick = { torrent ->
        viewModel.removeTorrent(torrent.infoHash, deleteFiles = false)
      },
      onFileClick = { torrent ->
        val files = viewModel.getTorrentFiles(torrent.infoHash)
        showTorrentFiles(files)
      }
    )

    binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    binding.recyclerView.adapter = adapter
  }

  private fun setupObservers() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.torrents.collect { torrents ->
        adapter.submitList(torrents.values.toList())

        if (torrents.isEmpty()) {
          binding.emptyView.visibility = View.VISIBLE
          binding.recyclerView.visibility = View.GONE
        } else {
          binding.emptyView.visibility = View.GONE
          binding.recyclerView.visibility = View.VISIBLE
        }
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.uiState.collect { state ->
        when (state) {
          is TorrentUiState.Loading -> {
            binding.progressBar.visibility = View.VISIBLE
          }

          is TorrentUiState.Success -> {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
            viewModel.resetUiState()
          }

          is TorrentUiState.Error -> {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
            viewModel.resetUiState()
          }

          is TorrentUiState.Idle -> {
            binding.progressBar.visibility = View.GONE
          }
        }
      }
    }
  }

  private fun setupListeners() {
    binding.addMagnetButton.setOnClickListener {
      val magnetUri = binding.magnetInput.text.toString()
      if (magnetUri.isNotEmpty()) {
        if (TorrentUtils.isValidMagnet(magnetUri)) {
          viewModel.addMagnet(magnetUri)
          binding.magnetInput.text?.clear()
        } else {
          Toast.makeText(requireContext(), "Invalid magnet link", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  private fun showTorrentFiles(files: List<TorrentFile>) {
    // Show files in a dialog or navigate to a new screen
    val message = files.joinToString("\n") { file ->
      "${TorrentUtils.getFileExtension(file.path)}: ${TorrentUtils.formatBytes(file.size)}"
    }

    androidx.appcompat.app.AlertDialog.Builder(requireContext())
      .setTitle("Torrent Files")
      .setMessage(message)
      .setPositiveButton("OK", null)
      .show()
  }
}

/**
 * RecyclerView adapter for displaying torrent items
 */
class TorrentAdapter(
  private val onPauseClick: (TorrentState) -> Unit,
  private val onResumeClick: (TorrentState) -> Unit,
  private val onRemoveClick: (TorrentState) -> Unit,
  private val onFileClick: (TorrentState) -> Unit
) : RecyclerView.Adapter<TorrentAdapter.TorrentViewHolder>() {

  private var torrents = listOf<TorrentState>()

  fun submitList(newTorrents: List<TorrentState>) {
    torrents = newTorrents
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_torrent, parent, false)
    return TorrentViewHolder(view)
  }

  override fun onBindViewHolder(holder: TorrentViewHolder, position: Int) {
    holder.bind(torrents[position])
  }

  override fun getItemCount() = torrents.size

  inner class TorrentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val torrentName: TextView = itemView.findViewById(R.id.torrentName)
    private val torrentProgress: ProgressBar = itemView.findViewById(R.id.torrentProgress)
    private val torrentProgressText: TextView = itemView.findViewById(R.id.torrentProgressText)
    private val torrentInfo: TextView = itemView.findViewById(R.id.torrentInfo)
    private val pauseButton: Button = itemView.findViewById(R.id.pauseButton)
    private val resumeButton: Button = itemView.findViewById(R.id.resumeButton)
    private val filesButton: Button = itemView.findViewById(R.id.filesButton)
    private val removeButton: Button = itemView.findViewById(R.id.removeButton)

    fun bind(torrent: TorrentState) {
      torrentName.text = torrent.name
      torrentProgress.progress = (torrent.progress * 100).toInt()
      torrentProgressText.text = "${(torrent.progress * 100).toInt()}%"

      torrentInfo.text = buildString {
        append("Size: ${TorrentUtils.formatBytes(torrent.totalSize)}\n")
        append("Downloaded: ${TorrentUtils.formatBytes(torrent.downloadedSize)}\n")
        append("Speed: ${TorrentUtils.formatSpeed(torrent.downloadSpeed)}\n")
        append("Peers: ${torrent.numPeers}\n")
        append("Status: ${torrent.status.name}")
      }

      // Setup buttons based on status
      when (torrent.status) {
        TorrentDownloadStatus.DOWNLOADING, TorrentDownloadStatus.SEEDING -> {
          pauseButton.visibility = View.VISIBLE
          resumeButton.visibility = View.GONE
          pauseButton.setOnClickListener { onPauseClick(torrent) }
        }

        TorrentDownloadStatus.PAUSED -> {
          pauseButton.visibility = View.GONE
          resumeButton.visibility = View.VISIBLE
          resumeButton.setOnClickListener { onResumeClick(torrent) }
        }

        else -> {
          pauseButton.visibility = View.GONE
          resumeButton.visibility = View.GONE
        }
      }

      removeButton.setOnClickListener { onRemoveClick(torrent) }
      filesButton.setOnClickListener { onFileClick(torrent) }
    }
  }
}

