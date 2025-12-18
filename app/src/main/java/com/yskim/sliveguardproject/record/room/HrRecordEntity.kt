package com.yskim.sliveguardproject.record.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hr_record")
data class HrRecordEntity(
    @PrimaryKey val ts: Long,
    val date: String,
    val time: String,
    val hr: Int,
    val stage: String,
    val isAlert: Boolean
)