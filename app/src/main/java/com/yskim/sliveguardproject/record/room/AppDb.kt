package com.yskim.sliveguardproject.record.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HrRecordEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun hrRecordDao(): HrRecordDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        fun get(ctx: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDb::class.java,
                    "sliveguard.db"
                ).build().also { INSTANCE = it }
            }
    }
}