package com.contest.pocketpharmacist.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "record_table")
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // 默认0，插入时自动变
    val medName: String, // 药名
    val date: String     // 日期
)