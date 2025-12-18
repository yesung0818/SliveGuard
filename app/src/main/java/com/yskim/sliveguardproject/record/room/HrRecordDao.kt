package com.yskim.sliveguardproject.record.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HrRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: HrRecordEntity)

    @Query("SELECT * FROM hr_record WHERE date = :date ORDER BY ts DESC")
    suspend fun listByDate(date: String): List<HrRecordEntity>

    @Query("DELETE FROM hr_record")
    suspend fun deleteAll()
}