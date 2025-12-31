package com.tv.apps.zippy.media.converters

import androidx.room.TypeConverter
import com.tv.apps.zippy.model.SubtitleData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room database type converters
 * Converts complex types to and from database-storable types
 */
class Converters {
  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Convert List<SubtitleData> to JSON string for storage
   */
  @TypeConverter
  fun fromSubtitleDataList(value: List<SubtitleData>?): String? {
    return if (value == null) null else json.encodeToString(value)
  }

  /**
   * Convert JSON string back to List<SubtitleData>
   */
  @TypeConverter
  fun toSubtitleDataList(value: String?): List<SubtitleData>? {
    return if (value == null) null else json.decodeFromString(value)
  }
}

