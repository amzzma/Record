package com.yutaca.record.data.repository

import com.yutaca.record.data.dao.AttachmentDao
import com.yutaca.record.data.dao.CustomMetaDataDao
import com.yutaca.record.data.dao.ModificationHistoryDao
import com.yutaca.record.data.dao.NotebookDao
import com.yutaca.record.data.dao.RecordDao
import com.yutaca.record.data.dao.TreeNodeDao
import com.yutaca.record.data.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow

class NotebookRepository(
    private val notebookDao: NotebookDao,
    private val treeNodeDao: TreeNodeDao,
    private val recordDao: RecordDao,
    private val attachmentDao: AttachmentDao,
    private val customMetaDataDao: CustomMetaDataDao,
    private val modificationHistoryDao: ModificationHistoryDao
) {

    val allNotebooks: Flow<List<NotebookEntity>> = notebookDao.getAllNotebooks()

    /** 一次性查询全部记录本（不订阅变化） */
    suspend fun getAllNotebooksOnce(): List<NotebookEntity> {
        return notebookDao.getAllNotebooksOnce()
    }

    suspend fun getNotebookById(id: Long): NotebookEntity? {
        return notebookDao.getNotebookById(id)
    }

    fun getNotebookByIdFlow(id: Long): Flow<NotebookEntity?> {
        return notebookDao.getNotebookByIdFlow(id)
    }

    suspend fun createNotebook(name: String, description: String = ""): Long {
        val notebook = NotebookEntity(
            name = name,
            description = description
        )
        return notebookDao.insert(notebook)
    }

    suspend fun updateNotebook(notebook: NotebookEntity) {
        notebookDao.update(notebook)
    }

    suspend fun updateCoverImage(id: Long, uri: String) {
        notebookDao.updateCoverImage(id, uri)
    }

    suspend fun deleteNotebook(id: Long) {
        notebookDao.deleteById(id)
    }

    /**
     * 深度删除记录本，级联删除所有关联数据：
     * 1. 删除该记录本下所有记录关联的附件、元数据、修改历史
     * 2. 删除所有记录
     * 3. 删除记录本本身（tree_nodes 由外键 CASCADE 自动删除）
     * 4. 清理封面图片、附件等本地物理文件
     */
    suspend fun deleteNotebookCascade(id: Long) {
        // 先获取封面图片路径（需在删记录本前拿到）
        val notebook = notebookDao.getNotebookById(id)
        val coverImageUri = notebook?.coverImageUri?.takeIf { it.isNotBlank() }

        // 获取该记录本下的所有 tree node，提取所有 recordId
        val allNodes = treeNodeDao.getAllNodesByNotebookOnce(id)
        val recordIds = allNodes.mapNotNull { it.recordId }

        if (recordIds.isNotEmpty()) {
            // ★ 先查询所有附件，获取文件路径（必须在删数据库记录之前）
            val attachments = attachmentDao.getAttachmentsByRecordIdsOnce(recordIds)
            for (attachment in attachments) {
                deleteLocalFile(attachment.fileUri)
            }

            // 批量删除关联数据
            attachmentDao.deleteByRecordIds(recordIds)
            customMetaDataDao.deleteByRecordIds(recordIds)
            modificationHistoryDao.deleteByRecordIds(recordIds)

            // 批量删除记录
            recordDao.deleteByIds(recordIds)
        }

        // 删除记录本（tree_nodes 由外键 CASCADE 自动删除）
        notebookDao.deleteById(id)

        // 清理封面图片（仅 file:// 协议的 URI，content:// 属于其他应用跳过）
        coverImageUri?.let { uriString -> deleteLocalFile(uriString) }
    }

    /**
     * 删除本应用本地的物理文件。
     * 仅处理 file:// 协议的 URI，content:// 等外部 URI 跳过。
     */
    private fun deleteLocalFile(uriString: String) {
        try {
            val uri = android.net.Uri.parse(uriString)
            if (uri.scheme == "file") {
                val file = java.io.File(uri.path!!)
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // 静默忽略，不阻塞删除流程
        }
    }
}