package cloud.app.csplayer.media.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cloud.app.csplayer.media.entities.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
  @Query("SELECT * FROM media")
  fun observeAll(): Flow<List<MediaEntity>>

  @Query("SELECT * FROM media")
  suspend fun getAll(): List<MediaEntity>

  @Query("SELECT * FROM media LIMIT :limit OFFSET :offset")
  suspend fun getAllPaged(limit: Int, offset: Int): List<MediaEntity>

  @Query("SELECT * FROM media WHERE mime_type LIKE :mimeTypePattern LIMIT :limit OFFSET :offset")
  suspend fun getAllPagedFiltered(mimeTypePattern: String, limit: Int, offset: Int): List<MediaEntity>

  @Query("SELECT * FROM media WHERE parent_path = :folderPath")
  suspend fun getByFolder(folderPath: String): List<MediaEntity>

  @Query("SELECT * FROM media WHERE parent_path = :folderPath LIMIT :limit OFFSET :offset")
  suspend fun getByFolderPaged(folderPath: String, limit: Int, offset: Int): List<MediaEntity>

  @Query("SELECT * FROM media WHERE parent_path = :folderPath AND mime_type LIKE :mimeTypePattern LIMIT :limit OFFSET :offset")
  suspend fun getByFolderPagedFiltered(folderPath: String, mimeTypePattern: String, limit: Int, offset: Int): List<MediaEntity>

  @Query("SELECT COUNT(*) FROM media WHERE parent_path = :folderPath")
  suspend fun countMediaInFolder(folderPath: String): Int

  @Query("SELECT COUNT(*) FROM media")
  suspend fun countAllMedia(): Int

  @Upsert
  suspend fun upsertAll(media: List<MediaEntity>)

  @Query("DELETE FROM media WHERE uri IN (:uris)")
  suspend fun deleteByUris(uris: List<String>)

  @Transaction
  suspend fun transaction(block: suspend () -> Unit) {
    block()
  }
}
