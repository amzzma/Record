package com.yutaca.record.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yutaca.record.data.dao.AttachmentDao
import com.yutaca.record.data.dao.CustomMetaDataDao
import com.yutaca.record.data.dao.ModificationHistoryDao
import com.yutaca.record.data.dao.NotebookDao
import com.yutaca.record.data.dao.RecordDao
import com.yutaca.record.data.dao.TreeNodeDao
import com.yutaca.record.data.entity.AttachmentEntity
import com.yutaca.record.data.entity.CustomMetaDataEntity
import com.yutaca.record.data.entity.ModificationHistoryEntity
import com.yutaca.record.data.entity.NotebookEntity
import com.yutaca.record.data.entity.RecordEntity
import com.yutaca.record.data.entity.TreeNodeEntity

@Database(
    entities = [
        NotebookEntity::class,
        TreeNodeEntity::class,
        RecordEntity::class,
        AttachmentEntity::class,
        CustomMetaDataEntity::class,
        ModificationHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notebookDao(): NotebookDao
    abstract fun treeNodeDao(): TreeNodeDao
    abstract fun recordDao(): RecordDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun customMetaDataDao(): CustomMetaDataDao
    abstract fun modificationHistoryDao(): ModificationHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "record_database"
                ).fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}