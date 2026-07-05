package com.yutaca.record.data.repository

import com.yutaca.record.data.dao.AttachmentDao
import com.yutaca.record.data.dao.CustomMetaDataDao
import com.yutaca.record.data.dao.ModificationHistoryDao
import com.yutaca.record.data.dao.RecordDao
import com.yutaca.record.data.entity.AttachmentEntity
import com.yutaca.record.data.entity.CustomMetaDataEntity
import com.yutaca.record.data.entity.ModificationHistoryEntity
import com.yutaca.record.data.entity.RecordEntity
import kotlinx.coroutines.flow.Flow

class RecordRepository(
    private val recordDao: RecordDao,
    private val attachmentDao: AttachmentDao,
    private val customMetaDataDao: CustomMetaDataDao,
    private val modificationHistoryDao: ModificationHistoryDao
) {

    fun getRecordById(id: Long): Flow<RecordEntity?> {
        return recordDao.getRecordById(id)
    }

    suspend fun getRecordByIdOnce(id: Long): RecordEntity? {
        return recordDao.getRecordByIdOnce(id)
    }

    suspend fun createRecord(title: String, content: String = ""): Long {
        val record = RecordEntity(
            title = title,
            content = content
        )
        return recordDao.insert(record)
    }

    suspend fun createRecordWithTimestamps(
        title: String,
        content: String,
        createdAt: Long,
        updatedAt: Long
    ): Long {
        val record = RecordEntity(
            title = title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
        return recordDao.insert(record)
    }

    suspend fun saveRecord(record: RecordEntity) {
        recordDao.update(record.copy(version = record.version + 1))
    }

    suspend fun searchByContent(keyword: String): List<RecordEntity> {
        return recordDao.searchByContent(keyword)
    }

    suspend fun deleteRecord(id: Long) {
        recordDao.delete(RecordEntity(id = id, title = ""))
    }

    // --- Attachments ---

    fun getAttachmentsByRecordId(recordId: Long): Flow<List<AttachmentEntity>> {
        return attachmentDao.getAttachmentsByRecordId(recordId)
    }

    suspend fun getAttachmentById(id: Long): AttachmentEntity? {
        return attachmentDao.getById(id)
    }

    suspend fun getAttachmentsByRecordIdOnce(recordId: Long): List<AttachmentEntity> {
        return attachmentDao.getAttachmentsByRecordIdOnce(recordId)
    }

    suspend fun addAttachment(attachment: AttachmentEntity): Long {
        return attachmentDao.insert(attachment)
    }

    suspend fun deleteAttachment(id: Long) {
        attachmentDao.deleteById(id)
    }

    // --- Custom Meta Data ---

    fun getMetaDataByRecordId(recordId: Long): Flow<List<CustomMetaDataEntity>> {
        return customMetaDataDao.getMetaDataByRecordId(recordId)
    }

    suspend fun getMetaDataById(id: Long): CustomMetaDataEntity? {
        return customMetaDataDao.getById(id)
    }

    suspend fun getMetaDataByRecordIdOnce(recordId: Long): List<CustomMetaDataEntity> {
        return customMetaDataDao.getMetaDataByRecordIdOnce(recordId)
    }

    suspend fun addMetaData(metaData: CustomMetaDataEntity): Long {
        return customMetaDataDao.insert(metaData)
    }

    suspend fun updateMetaData(metaData: CustomMetaDataEntity) {
        customMetaDataDao.update(metaData)
    }

    suspend fun deleteMetaData(id: Long) {
        customMetaDataDao.deleteById(id)
    }

    suspend fun deleteAllMetaDataByRecordId(recordId: Long) {
        customMetaDataDao.deleteByRecordId(recordId)
    }

    // --- Modification History ---

    fun getModificationHistoryByRecordId(recordId: Long): Flow<List<ModificationHistoryEntity>> {
        return modificationHistoryDao.getHistoryByRecordId(recordId)
    }

    suspend fun getModificationHistoryByRecordIdOnce(recordId: Long): List<ModificationHistoryEntity> {
        return modificationHistoryDao.getHistoryByRecordIdOnce(recordId)
    }

    suspend fun addModificationHistory(recordId: Long, message: String, timestamp: Long = System.currentTimeMillis()) {
        modificationHistoryDao.insert(
            ModificationHistoryEntity(
                recordId = recordId,
                message = message,
                timestamp = timestamp
            )
        )
    }

    suspend fun deleteModificationHistoryByRecordId(recordId: Long) {
        modificationHistoryDao.deleteByRecordId(recordId)
    }
}
