package com.yutaca.record.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yutaca.record.data.entity.CustomMetaDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomMetaDataDao {

    @Query("SELECT * FROM custom_meta_data WHERE recordId = :recordId ORDER BY id")
    fun getMetaDataByRecordId(recordId: Long): Flow<List<CustomMetaDataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metaData: CustomMetaDataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metaDataList: List<CustomMetaDataEntity>)

    @Query("SELECT * FROM custom_meta_data WHERE id = :id")
    suspend fun getById(id: Long): CustomMetaDataEntity?

    @Update
    suspend fun update(metaData: CustomMetaDataEntity)

    @Delete
    suspend fun delete(metaData: CustomMetaDataEntity)

    @Query("DELETE FROM custom_meta_data WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM custom_meta_data WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: Long)
}