package com.yutaca.record.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yutaca.record.data.entity.ModificationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModificationHistoryDao {

    @Query("SELECT * FROM modification_history WHERE recordId = :recordId ORDER BY timestamp ASC")
    fun getHistoryByRecordId(recordId: Long): Flow<List<ModificationHistoryEntity>>

    @Query("SELECT * FROM modification_history WHERE recordId = :recordId ORDER BY timestamp ASC")
    suspend fun getHistoryByRecordIdOnce(recordId: Long): List<ModificationHistoryEntity>

    @Insert
    suspend fun insert(history: ModificationHistoryEntity): Long

    @Query("DELETE FROM modification_history WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: Long)
}