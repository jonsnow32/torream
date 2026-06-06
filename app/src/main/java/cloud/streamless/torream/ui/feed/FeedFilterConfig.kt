package cloud.streamless.torream.ui.feed

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import cloud.streamless.torream.utils.PREFERENCES_NAME

/**
 * Configuration for feed display and filtering
 */
data class FeedFilterConfig(
  val groupMode: GroupMode = GroupMode.FOLDERS,
  val viewMode: ViewMode = ViewMode.LIST,
  val sortBy: SortBy = SortBy.TITLE,
  val sortOrderMap: Map<SortBy, SortOrder> = mapOf(
    SortBy.TITLE to SortOrder.ASCENDING,
    SortBy.DURATION to SortOrder.DESCENDING,
    SortBy.DATE to SortOrder.DESCENDING,
    SortBy.SIZE to SortOrder.DESCENDING,
    SortBy.LOCATION to SortOrder.ASCENDING
  ),
  val showDuration: Boolean = true,
  val showExtension: Boolean = false,
  val showPath: Boolean = true,
  val showProgress: Boolean = true,
  val showResolution: Boolean = false,
  val showSize: Boolean = false,
  val showThumbnail: Boolean = true
) {
    // Helper property to get current sort order based on selected sortBy
    val sortOrder: SortOrder
        get() = sortOrderMap[sortBy] ?: SortOrder.ASCENDING

    // Helper function to update sort order for current sortBy
    fun withSortOrder(order: SortOrder): FeedFilterConfig {
        return copy(sortOrderMap = sortOrderMap + (sortBy to order))
    }
    enum class GroupMode(val value: String) {

        FOLDERS("folders"),
        CAROUSEL("carousel");

        companion object {
            fun fromValue(value: String): GroupMode = entries.find { it.value == value } ?: FOLDERS
        }
    }

  enum class ViewMode(val value: String) {
    GRID("grid"),
    LIST("list");

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
        private const val KEY_GROUP_MODE = "feed_filter_group_mode"
        private const val KEY_SORT_BY = "feed_filter_sort_by"
        private const val KEY_SHOW_DURATION = "feed_filter_show_duration"
        private const val KEY_SHOW_EXTENSION = "feed_filter_show_extension"
        private const val KEY_SHOW_PATH = "feed_filter_show_path"
        private const val KEY_SHOW_PROGRESS = "feed_filter_show_progress"
        private const val KEY_SHOW_RESOLUTION = "feed_filter_show_resolution"
        private const val KEY_SHOW_SIZE = "feed_filter_show_size"
        private const val KEY_SHOW_THUMBNAIL = "feed_filter_show_thumbnail"

        // Keys for each SortBy's SortOrder
        private const val KEY_SORT_ORDER_TITLE = "feed_filter_sort_order_title"
        private const val KEY_SORT_ORDER_DURATION = "feed_filter_sort_order_duration"
        private const val KEY_SORT_ORDER_DATE = "feed_filter_sort_order_date"
        private const val KEY_SORT_ORDER_SIZE = "feed_filter_sort_order_size"
        private const val KEY_SORT_ORDER_LOCATION = "feed_filter_sort_order_location"

        fun load(prefs: SharedPreferences): FeedFilterConfig {
            // Load sort order for each SortBy
            val sortOrderMap = mapOf(
                SortBy.TITLE to SortOrder.fromValue(
                    prefs.getString(KEY_SORT_ORDER_TITLE, SortOrder.ASCENDING.value) ?: SortOrder.ASCENDING.value
                ),
                SortBy.DURATION to SortOrder.fromValue(
                    prefs.getString(KEY_SORT_ORDER_DURATION, SortOrder.DESCENDING.value) ?: SortOrder.DESCENDING.value
                ),
                SortBy.DATE to SortOrder.fromValue(
                    prefs.getString(KEY_SORT_ORDER_DATE, SortOrder.DESCENDING.value) ?: SortOrder.DESCENDING.value
                ),
                SortBy.SIZE to SortOrder.fromValue(
                    prefs.getString(KEY_SORT_ORDER_SIZE, SortOrder.DESCENDING.value) ?: SortOrder.DESCENDING.value
                ),
                SortBy.LOCATION to SortOrder.fromValue(
                    prefs.getString(KEY_SORT_ORDER_LOCATION, SortOrder.ASCENDING.value) ?: SortOrder.ASCENDING.value
                )
            )

            return FeedFilterConfig(
                viewMode = ViewMode.fromValue(prefs.getString(KEY_VIEW_MODE, ViewMode.LIST.value) ?: ViewMode.LIST.value),
                groupMode = GroupMode.fromValue(prefs.getString(KEY_GROUP_MODE, GroupMode.FOLDERS.value) ?: GroupMode.FOLDERS.value),
                sortBy = SortBy.fromValue(prefs.getString(KEY_SORT_BY, SortBy.TITLE.value) ?: SortBy.TITLE.value),
                sortOrderMap = sortOrderMap,
                showDuration = prefs.getBoolean(KEY_SHOW_DURATION, true),
                showExtension = prefs.getBoolean(KEY_SHOW_EXTENSION, false),
                showPath = prefs.getBoolean(KEY_SHOW_PATH, true),
                showProgress = prefs.getBoolean(KEY_SHOW_PROGRESS, true),
                showResolution = prefs.getBoolean(KEY_SHOW_RESOLUTION, false),
                showSize = prefs.getBoolean(KEY_SHOW_SIZE, false),
                showThumbnail = prefs.getBoolean(KEY_SHOW_THUMBNAIL, true)
            )
        }

        fun save(prefs: SharedPreferences, config: FeedFilterConfig) {
            prefs.edit {
                putString(KEY_VIEW_MODE, config.viewMode.value)
                putString(KEY_GROUP_MODE, config.groupMode.value)
                putString(KEY_SORT_BY, config.sortBy.value)

                // Save sort order for each SortBy
                putString(KEY_SORT_ORDER_TITLE, config.sortOrderMap[SortBy.TITLE]?.value ?: SortOrder.ASCENDING.value)
                putString(KEY_SORT_ORDER_DURATION, config.sortOrderMap[SortBy.DURATION]?.value ?: SortOrder.DESCENDING.value)
                putString(KEY_SORT_ORDER_DATE, config.sortOrderMap[SortBy.DATE]?.value ?: SortOrder.DESCENDING.value)
                putString(KEY_SORT_ORDER_SIZE, config.sortOrderMap[SortBy.SIZE]?.value ?: SortOrder.DESCENDING.value)
                putString(KEY_SORT_ORDER_LOCATION, config.sortOrderMap[SortBy.LOCATION]?.value ?: SortOrder.ASCENDING.value)

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

