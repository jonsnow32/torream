package com.tv.apps.zippy.media.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
  tableName = "folders",
  indices = [Index(value = ["path"], unique = true), Index(value = ["parent_path"])]
)
data class FolderEntity(
  @PrimaryKey @ColumnInfo(name = "path") val path: String,
  @ColumnInfo(name = "name") val name: String,
  @ColumnInfo(name = "parent_path") val parentPath: String,
  @ColumnInfo(name = "modified") val modified: Long,
  @ColumnInfo(name = "media_count") val mediaCount: Int = 0,
  @ColumnInfo(name = "child_count") val childCount: Int = 0,
  @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false
)
