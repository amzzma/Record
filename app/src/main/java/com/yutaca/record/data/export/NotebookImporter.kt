package com.yutaca.record.data.export

import android.content.Context
import com.yutaca.record.data.entity.AttachmentEntity
import com.yutaca.record.data.entity.CustomMetaDataEntity
import com.yutaca.record.data.entity.TreeNodeEntity
import com.yutaca.record.data.repository.NotebookRepository
import com.yutaca.record.data.repository.RecordRepository
import com.yutaca.record.data.repository.TreeNodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 记录本导入器 — 从 .recordbook 文件（ZIP 包）导入记录本及其全部数据。
 */
class NotebookImporter(
    private val context: Context,
    private val notebookRepository: NotebookRepository,
    private val recordRepository: RecordRepository,
    private val treeNodeRepository: TreeNodeRepository
) {

    /**
     * 从指定文件导入记录本。
     *
     * @param inputFile .recordbook 文件
     * @return Result.success(新建的记录本 ID) 或 Result.failure(exception)
     */
    suspend fun import(inputFile: File): Result<Long> = withContext(Dispatchers.IO) {
        try
        {
            // 1. 读取 ZIP 中的 notebook.json + 收集附件
            val json: String
            val attachBuffers = mutableMapOf<String, ByteArray>() // refId -> file bytes

            ZipInputStream(inputFile.inputStream().buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                var jsonFound = false
                var tempJson = ""

                while (entry != null) {
                    val name = entry.name
                    if (name == "notebook.json") {
                        tempJson = zis.readBytes().toString(Charsets.UTF_8)
                        jsonFound = true
                    } else if (name.startsWith("attachments/") && name.contains('_')) {
                        // 格式: attachments/{refId}_{fileName}
                        val refId = name.substringAfter("attachments/").substringBefore('_')
                        attachBuffers[refId] = zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }

                if (!jsonFound) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid .recordbook file: notebook.json not found")
                    )
                }
                json = tempJson
            }

            // 2. 反序列化
            val config = NotebookConfig.fromJson(json)

            // 3. 创建新记录本
            val notebookId = notebookRepository.createNotebook(
                name = config.notebook.name,
                description = config.notebook.description
            )

            // 4. 稍后更新封面信息（如果存在）
            if (config.notebook.coverColor != 0xFFF5F5F5.toLong() || config.notebook.coverImageUri.isNotEmpty()) {
                val notebook = notebookRepository.getNotebookById(notebookId)
                if (notebook != null) {
                    notebookRepository.updateNotebook(
                        notebook.copy(
                            coverColor = config.notebook.coverColor,
                            coverImageUri = config.notebook.coverImageUri
                        )
                    )
                }
            }

            // 5. 导入树节点 — 建立 oldRefId -> newId 映射
            val nodeIdMap = mutableMapOf<String, Long>()   // old refId -> new DB id
            val recordIdMap = mutableMapOf<String, Long>() // old recordRefId -> new recordId (DB id)

            // 5a. 分两遍：先导入所有叶子节点（记录），再处理树节点
            // 先遍历所有记录对应的叶子节点，创建记录
            for (node in config.treeNodes) {
                if (node.isLeaf && node.recordRefId != null) {
                    val recordItem = config.records.find { it.refId == node.recordRefId }
                    val recordId = if (recordItem != null) {
                        importRecord(recordItem, attachBuffers)
                    } else {
                        importEmptyRecord()
                    }
                    recordIdMap[node.recordRefId] = recordId
                }
            }

            // 5b. 导入所有树节点（此时 recordRefId 已映射）
            for (node in config.treeNodes) {
                val newParentId = node.parentRefId?.let { oldRef -> nodeIdMap[oldRef] }
                val newRecordId = if (node.isLeaf && node.recordRefId != null) {
                    recordIdMap[node.recordRefId]
                } else {
                    null
                }

                val newId = treeNodeRepository.createNode(
                    notebookId = notebookId,
                    name = node.name,
                    isLeaf = node.isLeaf,
                    parentId = newParentId,
                    recordId = newRecordId,
                    sortOrder = node.sortOrder
                )
                nodeIdMap[node.refId] = newId
            }

            // 6. 更新所有节点的排序（由于 createNode 不传 favorite，需要额外更新）
            // 注意：isFavorite 和 sortOrder 需要在 update 时设置
            for (node in config.treeNodes) {
                val newId = nodeIdMap[node.refId] ?: continue
                treeNodeRepository.updateNode(
                    TreeNodeEntity(
                        id = newId,
                        notebookId = notebookId,
                        parentId = node.parentRefId?.let { nodeIdMap[it] },
                        name = node.name,
                        isLeaf = node.isLeaf,
                        recordId = if (node.isLeaf && node.recordRefId != null) {
                            recordIdMap[node.recordRefId]
                        } else {
                            null
                        },
                        isFavorite = node.isFavorite,
                        sortOrder = node.sortOrder
                    )
                )
            }

            Result.success(notebookId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 导入一个记录（含附件、元数据、修改历史）。
     */
    private suspend fun importRecord(
        recordItem: RecordItem,
        attachBuffers: Map<String, ByteArray>
    ): Long {
        // 创建记录（保留原始创建/修改时间）
        val recordId = recordRepository.createRecordWithTimestamps(
            title = recordItem.title,
            content = recordItem.content,
            createdAt = recordItem.createdAt,
            updatedAt = recordItem.updatedAt
        )
        // 更新版本号（如果有）
        if (recordItem.version > 1) {
            val entity = recordRepository.getRecordByIdOnce(recordId)
            if (entity != null) {
                recordRepository.saveRecord(entity.copy(version = recordItem.version))
            }
        }

        // 导入附件
        for (attachment in recordItem.attachments) {
            val fileBytes = attachBuffers[attachment.refId]
            if (fileBytes != null) {
                // 将附件保存到应用缓存目录
                val cacheDir = File(context.cacheDir, "imported_attachments")
                cacheDir.mkdirs()
                val destFile = File(cacheDir, "${UUID.randomUUID()}_${attachment.fileName}")
                FileOutputStream(destFile).use { fos ->
                    fos.write(fileBytes)
                }
                // 使用文件 URI 存入数据库
                recordRepository.addAttachment(
                    AttachmentEntity(
                        recordId = recordId,
                        fileName = attachment.fileName,
                        fileUri = destFile.toURI().toString(),
                        fileType = attachment.fileType,
                        fileSize = attachment.fileSize
                    )
                )
            } else {
                // 如果附件文件不存在，只记录 URI 但可能不可访问
                recordRepository.addAttachment(
                    AttachmentEntity(
                        recordId = recordId,
                        fileName = attachment.fileName,
                        fileUri = attachment.fileUri,
                        fileType = attachment.fileType,
                        fileSize = attachment.fileSize
                    )
                )
            }
        }

        // 导入元数据
        for (meta in recordItem.metaData) {
            recordRepository.addMetaData(
                CustomMetaDataEntity(
                    recordId = recordId,
                    key = meta.key,
                    value = meta.value
                )
            )
        }

        // 导入修改历史（保留原始时间戳）
        for (history in recordItem.modificationHistory) {
            recordRepository.addModificationHistory(
                recordId = recordId,
                message = history.message,
                timestamp = history.timestamp
            )
        }

        return recordId
    }

    /**
     * 导入一个空记录（供没有 recordItem 的叶子节点使用）。
     */
    private suspend fun importEmptyRecord(): Long {
        return recordRepository.createRecord(title = "", content = "")
    }
}