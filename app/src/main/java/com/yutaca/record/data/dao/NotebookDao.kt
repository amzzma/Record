package com.yutaca.record.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yutaca.record.data.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {

    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun getAllNotebooks(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebookById(id: Long): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE id = :id")
    fun getNotebookByIdFlow(id: Long): Flow<NotebookEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notebook: NotebookEntity): Long

    @Update
    suspend fun update(notebook: NotebookEntity)

    @Delete
    suspend fun delete(notebook: NotebookEntity)

    @Query("UPDATE notebooks SET coverImageUri = :uri WHERE id = :id")
    suspend fun updateCoverImage(id: Long, uri: String)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun deleteById(id: Long)
}