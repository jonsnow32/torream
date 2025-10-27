package cloud.app.csplayer.datastore.app

import android.content.Context
import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat

import cloud.app.csplayer.datastore.DataStore
import cloud.app.csplayer.datastore.account.Account
import cloud.app.csplayer.datastore.app.helper.BOOKMARK_FOLDER
import cloud.app.csplayer.datastore.app.helper.BookmarkItem
import cloud.app.csplayer.datastore.app.helper.PlayerSettingItem
import cloud.app.csplayer.datastore.app.helper.UriHistoryItem
import cloud.app.csplayer.model.MediaItem
import cloud.app.csplayer.model.SaveCaptionStyle
import cloud.app.csplayer.ui.subtitles.DEF_SUBS_ELEVATION


const val ExtensionFolder = "extensionDir"
const val FAVORITE_FOLDER = "favorites"
const val SEARCH_HISTORY_FOLDER = "search_history"
const val URI_HISTORY_FOLDER = "uri_history"
const val PLAYER_SETTING_FOLDER = "player_setting"
const val USERS_FOLDER = "users"
const val PlaybackProgressFolder = "history_progress"
const val DOWNLOAD_FOLDER = "downloads"

class AppDataStore(val context: Context, val account: Account) :
  DataStore(context.getSharedPreferences("account_${account.getSlug()}", Context.MODE_PRIVATE)) {

  fun getAllBookmarks(): List<BookmarkItem>? {
    return getAll<BookmarkItem>("$BOOKMARK_FOLDER/")?.sortedByDescending { it.lastUpdated }
  }

  fun addToBookmark(data: BookmarkItem?) {
    if (data == null) return
    set("$BOOKMARK_FOLDER/${data.item.id}", data)
  }

  fun addToBookmark(mediaItem: MediaItem?, type: String) {
    if (mediaItem == null) return
    when (type) {
      "Watching" -> addToBookmark(BookmarkItem.Watching(0, null, mediaItem))
      "Completed" -> addToBookmark(BookmarkItem.Completed(null, mediaItem))
      "OnHold" -> addToBookmark(BookmarkItem.OnHold(mediaItem))
      "Dropped" -> addToBookmark(BookmarkItem.Dropped(mediaItem))
      "PlanToWatch" -> addToBookmark(BookmarkItem.PlanToWatch(mediaItem))
      else -> removeBookmark(mediaItem)
    }
  }

  fun findBookmark(mediaItem: MediaItem?): BookmarkItem? {
    return get<BookmarkItem>("$BOOKMARK_FOLDER/${mediaItem?.id}")
  }

  fun removeBookmark(mediaItem: MediaItem?) {
    if (mediaItem == null) return
    removeKey(
      "$BOOKMARK_FOLDER/${mediaItem.id}"
    )
  }


  fun addFavoritesData(data: MediaItem?) {
    if (data == null) return
    set("$FAVORITE_FOLDER/${data.id}", data)
  }

  fun removeFavoritesData(data: MediaItem?) {
    if (data == null) return
    removeKey(
      "$FAVORITE_FOLDER/${data.id}"
    )
  }

  fun getFavorites(): List<MediaItem>? {
    return getAll<MediaItem>(FAVORITE_FOLDER)
  }

  fun getFavoritesData(slug: String?): Boolean {
    if (slug == null) return false
    val data = get<MediaItem>("$FAVORITE_FOLDER/${slug}")
    return data != null;
  }

  fun saveUriHistory(item: UriHistoryItem) {
    return set("$URI_HISTORY_FOLDER/${item.id}", item)
  }

  fun getUriHistory(): List<UriHistoryItem>? {
    return getAll<UriHistoryItem>(
      "$URI_HISTORY_FOLDER/"
    )?.sortedByDescending { it.lastUpdated }
  }

  fun deleteUriHistory(item: UriHistoryItem) {
    return removeKey("$URI_HISTORY_FOLDER/${item.id}")
  }

  fun cleanUriHistory() {
    return removeKey("$URI_HISTORY_FOLDER/")
  }


  @UnstableApi
  fun getPlayerSetting(): PlayerSettingItem {
    return get<PlayerSettingItem>("$PLAYER_SETTING_FOLDER/") ?: PlayerSettingItem(
      subtitleStyle = SaveCaptionStyle(
        foregroundColor = getDefColor(0),
        backgroundColor = getDefColor(2),
        windowColor = getDefColor(3),
        edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
        edgeColor = getDefColor(1),
        typeface = null,
        typefaceFilePath = null,
        elevation = DEF_SUBS_ELEVATION,
        fixedTextSize = null,
      )
    )
  }

  private fun getDefColor(id: Int): Int {
    return when (id) {
      0 -> Color.WHITE
      1 -> Color.BLACK
      2 -> Color.TRANSPARENT
      3 -> Color.TRANSPARENT
      else -> Color.TRANSPARENT
    }
  }

}
