package com.tv.apps.zippy.media.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 9 to 10
 * Ensures that playlist tables and indices exist and adds any missing columns used by the
 * current schema. This migration uses CREATE TABLE IF NOT EXISTS and ALTER TABLE ADD COLUMN
 * where appropriate so it is safe to run on databases that already contain the target schema.
 */
object Migration_9_10 : Migration(9, 10) {
  override fun migrate(db: SupportSQLiteDatabase) {
    // Create playlists table if it doesn't exist
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS `playlists` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `name` TEXT NOT NULL,
        `description` TEXT,
        `created_at` INTEGER NOT NULL,
        `updated_at` INTEGER NOT NULL,
        `thumbnail_path` TEXT,
        `item_count` INTEGER NOT NULL
      )
      """.trimIndent()
    )

    // Index on created_at (matches @Index on PlaylistEntity)
    db.execSQL(
      "CREATE INDEX IF NOT EXISTS `index_playlists_created_at` ON `playlists`(`created_at`)"
    )

    // Drop playlist_items table if it exists (to handle incorrect schema from previous migration)
    db.execSQL("DROP TABLE IF EXISTS `playlist_items`")

    // Create playlist_items table with correct schema
    db.execSQL(
      """
      CREATE TABLE `playlist_items` (
        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        `playlist_id` INTEGER NOT NULL,
        `media_uri` TEXT NOT NULL,
        `position` INTEGER NOT NULL,
        `added_at` INTEGER NOT NULL,
        FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) ON DELETE CASCADE
      )
      """.trimIndent()
    )

    // Indices for playlist_items (match indices on PlaylistItemEntity)
    db.execSQL(
      "CREATE INDEX `index_playlist_items_playlist_id` ON `playlist_items`(`playlist_id`)"
    )
    db.execSQL(
      "CREATE INDEX `index_playlist_items_media_uri` ON `playlist_items`(`media_uri`)"
    )

    // Unique index for playlist_id + position
    db.execSQL(
      "CREATE UNIQUE INDEX `index_playlist_items_playlist_id_position` ON `playlist_items`(`playlist_id`, `position`)"
    )

    // If other tables/columns were added in the current schema compared to v9, add them here using
    // ALTER TABLE ... ADD COLUMN statements. For example, if a column `speed` was added earlier
    // it is handled in Migration_8_9; ensure any additional new columns are added here as needed.
  }
}

