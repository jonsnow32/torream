package cloud.app.csplayer.ui.feed

import android.content.Context
import androidx.core.content.edit
import cloud.app.csplayer.utils.PREFERENCES_NAME

/**
 * Configuration for feed display and filtering
 */
data class FeedFilterConfig(
  val groupMode: GroupMode = GroupMode.TREE,
  val viewMode: ViewMode = ViewMode.LIST,
  val sortBy: SortBy = SortBy.TITLE,
  val sortOrder: SortOrder = SortOrder.ASCENDING,
  val showDuration: Boolean = true,
  val showExtension: Boolean = false,
  val showPath: Boolean = true,
  val showProgress: Boolean = true,
  val showResolution: Boolean = false,
  val showSize: Boolean = false,
  val showThumbnail: Boolean = true
) {
    enum class GroupMode(val value: String) {
        TREE("tree"),
        FOLDERS("folders"),
        CAROUSEL("carousel");

        companion object {
            fun fromValue(value: String): GroupMode = entries.find { it.value == value } ?: TREE
        }
    }

  enum class ViewMode(val value: String) {
    LIST("list"),
    GRID("grid");

    companion object {
      fun fromValue(value: String): ViewMode = entries.find { it.value == value } ?: LIST
    }
  }
    enum class SortBy(val value: String) {
        TITLE("title"),
        DURATION("duration"),
        DATE("date"),
        SIZE("size"),
        LOCATION("location");

        companion object {
            fun fromValue(value: String): SortBy = entries.find { it.value == value } ?: TITLE
        }
    }

    enum class SortOrder(val value: String) {
        ASCENDING("asc"),
        DESCENDING("desc");

        companion object {
            fun fromValue(value: String): SortOrder = entries.find { it.value == value } ?: ASCENDING
        }
    }

    companion object {
        private const val KEY_VIEW_MODE = "feed_filter_view_mode"
        private const val KEY_SORT_BY = "feed_filter_sort_by"
        private const val KEY_SORT_ORDER = "feed_filter_sort_order"
        private const val KEY_SHOW_DURATION = "feed_filter_show_duration"
        private const val KEY_SHOW_EXTENSION = "feed_filter_show_extension"
        private const val KEY_SHOW_PATH = "feed_filter_show_path"
        private const val KEY_SHOW_PROGRESS = "feed_filter_show_progress"
        private const val KEY_SHOW_RESOLUTION = "feed_filter_show_resolution"
        private const val KEY_SHOW_SIZE = "feed_filter_show_size"
        private const val KEY_SHOW_THUMBNAIL = "feed_filter_show_thumbnail"

        fun load(context: Context): FeedFilterConfig {
            val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            return FeedFilterConfig(
                groupMode = GroupMode.fromValue(prefs.getString(KEY_VIEW_MODE, GroupMode.TREE.value) ?: GroupMode.TREE.value),
                sortBy = SortBy.fromValue(prefs.getString(KEY_SORT_BY, SortBy.TITLE.value) ?: SortBy.TITLE.value),
                sortOrder = SortOrder.fromValue(prefs.getString(KEY_SORT_ORDER, SortOrder.ASCENDING.value) ?: SortOrder.ASCENDING.value),
                showDuration = prefs.getBoolean(KEY_SHOW_DURATION, true),
                showExtension = prefs.getBoolean(KEY_SHOW_EXTENSION, false),
                showPath = prefs.getBoolean(KEY_SHOW_PATH, true),
                showProgress = prefs.getBoolean(KEY_SHOW_PROGRESS, true),
                showResolution = prefs.getBoolean(KEY_SHOW_RESOLUTION, false),
                showSize = prefs.getBoolean(KEY_SHOW_SIZE, false),
                showThumbnail = prefs.getBoolean(KEY_SHOW_THUMBNAIL, true)
            )
        }

        fun save(context: Context, config: FeedFilterConfig) {
            val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putString(KEY_VIEW_MODE, config.groupMode.value)
                putString(KEY_SORT_BY, config.sortBy.value)
                putString(KEY_SORT_ORDER, config.sortOrder.value)
                putBoolean(KEY_SHOW_DURATION, config.showDuration)
                putBoolean(KEY_SHOW_EXTENSION, config.showExtension)
                putBoolean(KEY_SHOW_PATH, config.showPath)
                putBoolean(KEY_SHOW_PROGRESS, config.showProgress)
                putBoolean(KEY_SHOW_RESOLUTION, config.showResolution)
                putBoolean(KEY_SHOW_SIZE, config.showSize)
                putBoolean(KEY_SHOW_THUMBNAIL, config.showThumbnail)
            }
        }
    }
}

