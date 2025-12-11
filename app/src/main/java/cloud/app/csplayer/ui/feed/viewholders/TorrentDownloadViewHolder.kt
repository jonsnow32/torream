package cloud.app.csplayer.ui.feed.viewholders

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.ItemTorrentDownloadBinding
import cloud.app.csplayer.download.DownloadRepository
import cloud.app.csplayer.download.DownloadStatus
import cloud.app.csplayer.ui.feed.FeedAction
import cloud.app.csplayer.ui.feed.FeedData
import cloud.app.csplayer.ui.feed.FeedViewHolder
import cloud.app.csplayer.utils.UnifiedFile
import cloud.app.csplayer.utils.UnifiedFileFactory
import cloud.app.csplayer.utils.loadThumbnail
import coil.load
import timber.log.Timber
import java.io.File

class TorrentDownloadViewHolder(
  val parent: ViewGroup,
  val clickListener: FeedAction,
  val downloadRepository: DownloadRepository,
  val binding: ItemTorrentDownloadBinding = ItemTorrentDownloadBinding.inflate(
    LayoutInflater.from(parent.context), parent, false
  )
) : FeedViewHolder<FeedData.TorrentDownloadItem>(binding.root) {

  override fun bind(feed: FeedData.TorrentDownloadItem) {
    // Bind data from feed (already updated by LibraryViewModel which observes DownloadState)
    binding.title.text = feed.title

    val progress = feed.downloadState.progress.coerceIn(0, 100)
    updateUI(
      feed,
      progress,
      feed.downloadState.status,
      feed.downloadState.numSeeds,
      feed.downloadState.numPeers,
      feed.downloadState.speed
    )

    // Click listeners
    binding.root.setOnClickListener {
      clickListener.onItemClick(feed)
    }
    binding.root.setOnLongClickListener {
      clickListener.onItemLongClick(feed)
      true
    }
    binding.btnAction.setOnClickListener {
      clickListener.onItemClick(feed)
    }
  }

  private fun updateUI(
    feed: FeedData.TorrentDownloadItem,
    progress: Int,
    status: DownloadStatus,
    seeds: Int,
    peers: Int,
    speed: Long
  ) {
    binding.progressBar.progress = progress
    binding.progressBar.isIndeterminate = progress == 0
    val statusText = when (status) {
      DownloadStatus.REPAIRING -> {
        parent.context.getString(R.string.preparing)
      }
      DownloadStatus.SEEDING -> {
        parent.context.getString(R.string.seeding)
      }
      DownloadStatus.PAUSED -> parent.context.getString(R.string.paused)
      DownloadStatus.FINISHED, DownloadStatus.COMPLETED -> {
        // Load thumbnail from downloaded file
        feed.downloadState.task.targetPath.let { filePath ->
          try {
            val torrentDir = UnifiedFileFactory.fromFile(parent.context, File(filePath))

            if (torrentDir != null && torrentDir.exists()) {
              // Strategy 1: Try to find an existing image file in the torrent
              val imagePath = findImageInTorrent(torrentDir)

              if (imagePath != null) {
                // Found an image file, load it directly with Coil
                Timber.d("Loading image from torrent: $imagePath")
                binding.thumbnail.load(File(imagePath)) {
                  crossfade(true)
                  placeholder(android.R.drawable.ic_menu_gallery)
                  error(android.R.drawable.ic_menu_close_clear_cancel)
                }
              } else {
                // Strategy 2: Find largest video file and generate thumbnail from it
                val videoPath = findLargestFileSize(torrentDir)

                if (videoPath != null) {
                  // Generate thumbnail from video file using FFmpeg
                  Timber.d("Generating thumbnail from video: $videoPath")
                  binding.thumbnail.loadThumbnail(
                    videoPath,
                    placeholderRes = android.R.drawable.ic_menu_gallery,
                    errorRes = android.R.drawable.ic_menu_close_clear_cancel
                  )
                } else {
                  Timber.w("No image or video files found in torrent directory: $filePath")
                }
              }
            } else {
              Timber.w("Torrent directory not accessible: $filePath")
            }
          } catch (e: Exception) {
            Timber.e(e, "Failed to load thumbnail for torrent: $filePath")
          }
        }
        // Display user-friendly save path
        binding.txtSavePath.text = formatSavePath(feed.downloadState.task.targetPath)
        binding.txtSavePath.visibility = android.view.View.VISIBLE
        parent.context.getString(R.string.finished)
      }
      DownloadStatus.DOWNLOADING -> {
        Timber.i("feed.downloadState.task.downloadedFilePath = ${feed.downloadState.task.fileName}")
        parent.context.getString(R.string.downloading)
      }
      DownloadStatus.FAILED -> parent.context.getString(R.string.error)
      else -> {
        parent.context.getString(R.string.downloading)
      }
    }

    // Build progress text with size info
    val sizeText = if (feed.downloadState.totalBytes > 0) {
      "${formatFileSize(feed.downloadState.downloadedBytes)} / ${formatFileSize(feed.downloadState.totalBytes)}"
    } else {
      formatFileSize(feed.downloadState.downloadedBytes)
    }

    binding.txtProgress.text = parent.context.getString(
      R.string.torrent_progress_with_peers,
      progress,
      statusText,
      seeds,
      peers
    ) + " • ${formatSpeed(speed)} • $sizeText"


    // Update PieFetchButton state and progress
    val buttonState = when (status) {
      DownloadStatus.PAUSED -> cloud.app.csplayer.ui.download.PieFetchButton.State.PAUSED
      DownloadStatus.DOWNLOADING -> cloud.app.csplayer.ui.download.PieFetchButton.State.DOWNLOADING
      DownloadStatus.FINISHED, DownloadStatus.COMPLETED -> cloud.app.csplayer.ui.download.PieFetchButton.State.COMPLETED
      DownloadStatus.FAILED -> cloud.app.csplayer.ui.download.PieFetchButton.State.ERROR
      else -> cloud.app.csplayer.ui.download.PieFetchButton.State.IDLE
    }

    binding.btnAction.setState(buttonState)
    binding.btnAction.setProgress(progress.toFloat())
    binding.btnAction.contentDescription = if (status == DownloadStatus.PAUSED) {
      parent.context.getString(R.string.resume)
    } else {
      parent.context.getString(R.string.pause)
    }
  }

  private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
      bytesPerSecond <= 0 -> "0 B/s"
      bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
      bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
      else -> "${String.format("%.1f", bytesPerSecond / (1024.0 * 1024.0))} MB/s"
    }
  }

  private fun formatSavePath(targetPath: String): String {
    val file = UnifiedFileFactory.fromUri(binding.root.context, targetPath.toUri())
    return file?.filePath ?: targetPath
  }

  @SuppressLint("DefaultLocale")
  private fun formatFileSize(bytes: Long): String {
    return when {
      bytes <= 0 -> "0 B"
      bytes < 1024 -> "$bytes B"
      bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
      bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024.0 * 1024.0))} MB"
      else -> "${String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
  }

  private fun findLargestFileSize(torrentDir: UnifiedFile): String? {
    val videoFiles = if (torrentDir.exists()) {
      val files = findVideoFilesRecursively(torrentDir)
      Timber.d("Found ${files.size} video files in torrent")
      files.forEachIndexed { index, filePath ->
        val size = try {
          File(filePath).length()
        } catch (e: Exception) {
          0L
        }
        Timber.d("  [$index] $filePath (${size / (1024 * 1024)} MB)")
      }
      files
    } else {
      emptyList()
    }

    // Use the largest video file as the main file for thumbnail
    val mainVideoFilePath = videoFiles.maxByOrNull { filePath ->
      try {
        File(filePath).length()
      } catch (e: Exception) {
        0L
      }
    }
    Timber.i("✓ Final downloadedFilePath for torrent: $mainVideoFilePath")
    return mainVideoFilePath
  }

  private fun findImageInTorrent(torrentDir: UnifiedFile): String? {
    val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    var foundImagePath: String? = null

    fun scan(dir: UnifiedFile) {
      try {
        dir.listFiles()?.forEach { file: UnifiedFile ->
          if (file.isDirectory) {
            // Recursively scan subdirectories
            scan(file)
          } else {
            // Check if it's an image file
            val fileName = file.name ?: ""
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension in imageExtensions) {
              // Get file path for MediaStore scanning
              val filePath = file.filePath
              if (filePath != null && foundImagePath == null) {
                foundImagePath = filePath
                Timber.d("Found image file in torrent: $filePath")
              }
            }
          }
        }
      } catch (e: Exception) {
        Timber.w(e, "Error scanning directory for images: ${dir.uri}")
      }
    }

    scan(torrentDir)
    return foundImagePath
  }

  private fun findVideoFilesRecursively(directory: UnifiedFile): List<String> {
    val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp")
    val result = mutableListOf<String>()

    fun scan(dir: UnifiedFile) {
      try {
        dir.listFiles()?.forEach { file: UnifiedFile ->
          if (file.isDirectory) {
            // Recursively scan subdirectories
            scan(file)
          } else {
            // Check if it's a video file
            val fileName = file.name ?: ""
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension in videoExtensions) {
              // Get file path for MediaStore scanning
              val filePath = file.filePath
              if (filePath != null) {
                result.add(filePath)
              } else {
                Timber.w("Cannot get file path for: ${file.uri}")
              }
            }
          }
        }
      } catch (e: Exception) {
        Timber.w(e, "Error scanning directory: ${dir.uri}")
      }
    }

    scan(directory)
    return result
  }
}
