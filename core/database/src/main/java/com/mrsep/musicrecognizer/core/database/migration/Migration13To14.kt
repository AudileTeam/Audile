package com.mrsep.musicrecognizer.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val Migration13To14 = object : Migration(13, 14) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE track ADD COLUMN link_jiosaavn TEXT")
    }
}
