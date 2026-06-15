package cloud.streamless.torream.media.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cloud.streamless.torream.media.entities.FolderEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface FolderDao {
  @Query("SELECT * FROM folders WHERE is_hidden = 0 AND is_private = 0 ORDER BY name ASC")
  fun observeAll(): Flow<List<FolderEntity>>

  @Query("SELECT * FROM folders WHERE is_hidden = 0 AND is_private = 0 ORDER BY name ASC LIMIT :limit OFFSET :offset")
  suspend fun getAllPaged(limit: Int, offset: Int): List<FolderEntity>

  @Query("SELECT COUNT(*) FROM folders WHERE is_hidden = 0 AND is_private = 0")
  suspend fun countAllFolders(): Int

  @Query("SELECT * FROM folders WHERE is_private = 1 ORDER BY name ASC LIMIT :limit OFFSET :offset")
  suspend fun getPrivatePaged(limit: Int, offset: Int): List<FolderEntity>

  @Query("SELECT COUNT(*) FROM folders WHERE is_private = 1")
  suspend fun countPrivate(): Int

  @Query("UPDATE folders SET is_private = :isPrivate WHERE path = :path")
  suspend fun setPrivate(path: String, isPrivate: Boolean)

  @Query("SELECT * FROM folders")
  suspend fun getAll(): List<FolderEntity>

  @Upsert
  suspend fun upsertAll(folders: List<FolderEntity>)

  @Query("DELETE FROM folders WHERE path IN (:paths)")
  suspend fun deleteByPaths(paths: List<String>)

  @Query("SELECT COUNT(*) FROM folders WHERE parent_path = :folderPath")
  suspend fun countChildFolders(folderPath: String): Int

  @Transaction
  suspend fun transaction(block: suspend () -> Unit) {
    block()
  }
}
