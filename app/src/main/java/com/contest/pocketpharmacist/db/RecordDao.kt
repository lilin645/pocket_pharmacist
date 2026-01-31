package com.contest.pocketpharmacist.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RecordDao {
    @Insert
    fun insert(record: Record)

    @Query("SELECT * FROM record_table")
    fun getAll(): List<Record>
}