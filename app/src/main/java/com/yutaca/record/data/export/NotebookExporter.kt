package com.yutaca.record.data.export

import android.content.Context
import android.net.Uri
import com.yutaca.record.data.entity.AttachmentEntity
import com.yutaca.record.data.entity.CustomMetaDataEntity
import com.yutaca.record.data.entity.ModificationHistoryEntity
import com.yutaca.record.data.repository.NotebookRepository
import com.yutaca.record.data.repository.RecordRepository
import com.yutaca.record.data.repository.TreeNodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 记录本导出器 — 将指定记录本导出为 .recordbook 文件（ZIP 包）。
 */
class NotebookExporter(
    private val context: Context,
    private val notebookRepository: NotebookRepository,
    private val recordRepository: RecordRepository,
    private val treeNodeRepository: TreeNodeRepository
) {

    /**
     * 导出指定记录本到 outputFile。
     *
     * @param notebookId 要导出的记录本 ID
     * @param outputFile 输出文件路径，建议后缀为 .recordbook
     * @return Result.success(Unit) 或 Result.failure(exception)
     */
    suspend fun export(notebookId: Long, outputFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. 查询 Notebook
            val notebook = notebookRepository.getNotebookById(notebookId)
                ?: return@withContext Result.failure(IllegalArgumentException("Notebook not found: $notebookId"))

            // 2. 查询所有 TreeNode
            val treeNodes = treeNodeRepository.getAllNodesByNotebookOnce(notebookId)

            // 3. 建立 refId 映射: DB ID -> UUID
            val nodeRefMap = mutableMapOf<Long, String>()
            val recordRefMap = mutableMapOf<Long, String>()
            val configTreeNodes = mutableListOf<TreeNodeItem>()
            val configRecords = mutableListOf<RecordItem>()

            // 第 1 遍：所有节点先分配 refId，确保父节点一定在 Map 中
            for (node in treeNodes) {
                nodeRefMap[node.id] = UUID.randomUUID().toString()
                if (node.isLeaf && node.recordId != null) {
                    if (node.recordId !in recordRefMap) {
                        recordRefMap[node.recordId] = UUID.randomUUID().toString()
                    }
                }
            }

            // 第 2 遍：构建 TreeNodeItem（parentRefId 一定可解析）
            for (node in treeNodes) {
                configTreeNodes.add(
                    TreeNodeItem(
                        refId = nodeRefMap[node.id]!!,
                        parentRefId = node.parentId?.let { nodeRefMap[it] },
                        name = node.name,
                        isLeaf = node.isLeaf,
                        recordRefId = node.recordId?.let { recordRefMap[it] },
                        isFavorite = node.isFavorite,
                        sortOrder = node.sortOrder
                    )
                )
            }

            // 4. 遍历每个需要导出的记录，获取完整数据
            val attachmentFileMap = mutableMapOf<String, Uri>() // refId -> original URI

            for ((recordId, recordRef) in recordRefMap) {
                val recordEntity = recordRepository.getRecordByIdOnce(recordId)
                    ?: continue

                // 附件
                val attachments = recordRepository.getAttachmentsByRecordIdOnce(recordId)
                val configAttachments = mutableListOf<AttachmentItem>()
                for (attachment in attachments) {
                    val attachRef = UUID.randomUUID().toString()
                    configAttachments.add(
                        AttachmentItem(
                            refId = attachRef,
                            fileName = attachment.fileName,
                            fileUri = attachment.fileUri,
                            fileType = attachment.fileType,
                            fileSize = attachment.fileSize
                        )
                    )
                    attachmentFileMap[attachRef] = Uri.parse(attachment.fileUri)
                }

                // 自定义元数据
                val metaData = recordRepository.getMetaDataByRecordIdOnce(recordId)
                val configMetaData = metaData.map {
                    MetaDataItem(key = it.key, value = it.value)
                }

                // 修改历史
                val history = recordRepository.getModificationHistoryByRecordIdOnce(recordId)
                val configHistory = history.map {
                    ModificationHistoryItem(timestamp = it.timestamp, message = it.message)
                }

                configRecords.add(
                    RecordItem(
                        refId = recordRef,
                        title = recordEntity.title,
                        content = recordEntity.content,
                        version = recordEntity.version,
                        createdAt = recordEntity.createdAt,
                        updatedAt = recordEntity.updatedAt,
                        attachments = configAttachments,
                        metaData = configMetaData,
                        modificationHistory = configHistory
                    )
                )
            }

            // 5. 组装 NotebookConfig
            val config = NotebookConfig(
                version = 1,
                exportedAt = System.currentTimeMillis(),
                notebook = NotebookItem(
                    name = notebook.name,
                    description = notebook.description,
                    coverColor = notebook.coverColor,
                    coverImageUri = notebook.coverImageUri,
                    createdAt = notebook.createdAt,
                    updatedAt = notebook.updatedAt
                ),
                treeNodes = configTreeNodes,
                records = configRecords
            )

            // 6. 序列化为 JSON
            val json = config.toJson()

            // 7. 写入 ZIP 文件
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                // 7a. 写入 notebook.json
                zos.putNextEntry(ZipEntry("notebook.json"))
                zos.write(json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 7b. 写入附件文件到 attachments/ 目录
                for ((attachRef, uri) in attachmentFileMap) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                            ?: continue
                        // 从原 URI 中提取文件名，用于 ZIP 内存储
                        val fileName = uri.lastPathSegment ?: "file"
                        val entryName = "attachments/${attachRef}_${fileName}"
                        zos.putNextEntry(ZipEntry(entryName))
                        BufferedInputStream(inputStream).use { bis ->
                            bis.copyTo(zos, bufferSize = 8192)
                        }
                        zos.closeEntry()
                    } catch (_: Exception) {
                        // 如果附件文件无法读取，跳过该附件（不中断导出）
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}