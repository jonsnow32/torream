package cloud.streamless.torream.media.repository

import cloud.streamless.torream.model.NetworkShare
import cloud.streamless.torream.model.VideoLink
import kotlinx.coroutines.flow.Flow

interface NetworkShareRepository {
  fun observeShares(): Flow<List<NetworkShare>>
  suspend fun getShare(id: Long): NetworkShare?
  suspend fun saveShare(share: NetworkShare): Long
  suspend fun deleteShare(id: Long)
  fun buildVideoLink(share: NetworkShare, relativePath: String, fileName: String): VideoLink
}
