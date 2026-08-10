package cloud.streamless.torream.ui.browse.network

import cloud.streamless.torream.model.NetworkShare
import com.hierynomus.msfscc.FileAttributes

/** Lists an SMB directory. Connects fresh per call and closes afterwards - simple, avoids managing idle sessions. */
class SmbBrowseClient {

  fun list(share: NetworkShare, relativePath: String): List<BrowseEntry> {
    val subPath = relativePath.trim('/')
    val (shareName, fullPath) = SmbPathUtils.splitShareAndPath(share.basePath, subPath)

    SmbConnectionHandle.open(share.host, share.port, share.username, share.password).use { handle ->
      val diskShare = handle.openShare(shareName)
      val listing = diskShare.list(fullPath)
      return listing
        .filter { it.fileName != "." && it.fileName != ".." }
        .map { info ->
          val isDir = (info.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
          BrowseEntry(
            name = info.fileName,
            isDirectory = isDir,
            size = info.endOfFile,
            lastModified = info.lastWriteTime.toDate().time,
            relativePath = if (subPath.isEmpty()) info.fileName else "$subPath/${info.fileName}"
          )
        }
    }
  }
}
