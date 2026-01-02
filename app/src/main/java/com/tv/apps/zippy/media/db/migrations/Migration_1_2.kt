package com.tv.apps.zippy.media.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 8 to 9
 * Adds 'speed' column to 'http_downloads' table
 */
object Migration_1_2 : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    // Add speed column to http_downloads table
//    db.execSQL(
//      "ALTER TABLE http_downloads ADD COLUMN speed INTEGER NOT NULL DEFAULT 0"
//    )
  }
}

