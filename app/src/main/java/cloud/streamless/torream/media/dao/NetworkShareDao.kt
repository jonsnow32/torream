package cloud.streamless.torream.media.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cloud.streamless.torream.media.entities.NetworkShareEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkShareDao {
  @Query("SELECT * FROM network_share ORDER BY created_at DESC")
  fun observeAll(): Flow<List<NetworkShareEntity>>

  @Query("SELECT * FROM network_share WHERE id = :id")
  suspend fun getById(id: Long): NetworkShareEntity?

  @Insert
  suspend fun insert(share: NetworkShareEntity): Long

  @Update
  suspend fun update(share: NetworkShareEntity)

  @Delete
  suspend fun delete(share: NetworkShareEntity)

  @Query("DELETE FROM network_share WHERE id = :id")
  suspend fun deleteById(id: Long)
}
