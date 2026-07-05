package com.yutaca.record.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yutaca.record.data.entity.RecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Query("SELECT * FROM records WHERE id = :id")
    fun getRecordById(id: Long): Flow<RecordEntity?>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun getRecordByIdOnce(id: Long): RecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<RecordEntity>)

    @Update
    suspend fun update(record: RecordEntity)

    @Query("SELECT * FROM records WHERE content LIKE :keyword OR title LIKE :keyword")
    suspend fun searchByContent(keyword: String): List<RecordEntity>

    @Delete
    suspend fun delete(record: RecordEntity)

    @Query("DELETE FROM records WHERE id IN (:recordIds)")
    suspend fun deleteByIds(recordIds: List<Long>)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
