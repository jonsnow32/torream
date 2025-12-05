package cloud.app.csplayer.utils

import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/**
 * Unified file abstraction interface for working with different file storage backends
 * Can use either SafeFile or FileKUniFile implementation
 */
interface UnifiedFile {
  fun createFile(displayName: String, mimeType: String = "application/octet-stream"): UnifiedFile?
  fun createDirectory(displayName: String): UnifiedFile?

  val uri: Uri
  val name: String
  val type: String?
  val filePath: String?
  val isDirectory: Boolean
  val isFile: Boolean

  fun lastModified(): Long
  fun length(): Long
  fun canRead(): Boolean
  fun canWrite(): Boolean
  fun delete(): Boolean
  fun exists(): Boolean
  fun listFiles(): Array<UnifiedFile>?
  fun findFile(displayName: String, ignoreCase: Boolean = false): UnifiedFile?
  fun renameTo(displayName: String): Boolean
  fun openInputStream(): InputStream
  fun openOutputStream(append: Boolean = false): OutputStream
}

