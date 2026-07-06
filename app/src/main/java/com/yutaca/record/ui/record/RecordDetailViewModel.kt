package com.yutaca.record.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yutaca.record.data.entity.AttachmentEntity
import com.yutaca.record.data.entity.CustomMetaDataEntity
import com.yutaca.record.data.entity.ModificationHistoryEntity
import com.yutaca.record.data.repository.NotebookRepository
import com.yutaca.record.data.repository.RecordRepository
import com.yutaca.record.data.repository.TreeNodeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordDetailUiState(
    val title: String = "",
    val content: String = "",
    val version: Int = 1,
    val attachments: List<AttachmentEntity> = emptyList(),
    val metaData: List<CustomMetaDataEntity> = emptyList(),
    val modificationHistory: List<ModificationHistoryEntity> = emptyList(),
    val isLoading: Boolean = true
)

class RecordDetailViewModel(
    private val recordId: Long,
    private val recordRepository: RecordRepository,
    private val treeNodeRepository: TreeNodeRepository,
    private val notebookRepository: NotebookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordDetailUiState())
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()

    /** 防抖时间戳写入任务，500ms 内多次操作合并为一次写入 */
    private var timestampJob: Job? = null

    init {
        loadRecord()
        // 附件/元数据/历史使用一次性查询，避免持久Flow订阅造成频繁推送
        loadAttachmentsOnce()
        loadMetaDataOnce()
        loadModificationHistoryOnce()
    }

    /**
     * 一次性加载记录，避免持久 Flow 订阅导致每次 DB 变化都推送
     */
    private fun loadRecord() {
        viewModelScope.launch {
            val record = recordRepository.getRecordByIdOnce(recordId)
            if (record != null) {
                _uiState.value = _uiState.value.copy(
                    title = record.title,
                    content = record.content,
                    version = record.version,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 手动刷新记录内容（写操作后需要回读时才调用）
     */
    private fun refreshRecord() {
        viewModelScope.launch {
            val record = recordRepository.getRecordByIdOnce(recordId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                title = record.title,
                content = record.content,
                version = record.version
            )
        }
    }

    // ==================== 一次性数据加载（仅在构造时调用） ====================

    private fun loadAttachmentsOnce() {
        viewModelScope.launch {
            val attachments = recordRepository.getAttachmentsByRecordIdOnce(recordId)
            _uiState.value = _uiState.value.copy(attachments = attachments)
        }
    }

    private fun loadMetaDataOnce() {
        viewModelScope.launch {
            val metaData = recordRepository.getMetaDataByRecordIdOnce(recordId)
            _uiState.value = _uiState.value.copy(metaData = metaData)
        }
    }

    private fun loadModificationHistoryOnce() {
        viewModelScope.launch {
            val history = recordRepository.getModificationHistoryByRecordIdOnce(recordId)
            _uiState.value = _uiState.value.copy(modificationHistory = history)
        }
    }

    // ==================== 手动刷新方法（在写操作后调用） ====================

    private fun refreshAttachments() {
        viewModelScope.launch {
            val attachments = recordRepository.getAttachmentsByRecordIdOnce(recordId)
            _uiState.value = _uiState.value.copy(attachments = attachments)
        }
    }

    private fun refreshMetaData() {
        viewModelScope.launch {
            val metaData = recordRepository.getMetaDataByRecordIdOnce(recordId)
            _uiState.value = _uiState.value.copy(metaData = metaData)
        }
    }

    private fun refreshModificationHistory() {
        viewModelScope.launch {
            val history = recordRepository.getModificationHistoryByRecordIdOnce(recordId)
            _uiState.value = _uiState.value.copy(modificationHistory = history)
        }
    }

    // ==================== 业务方法 ====================

    fun saveContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content)
        viewModelScope.launch {
            val current = recordRepository.getRecordByIdOnce(recordId) ?: return@launch
            recordRepository.saveRecord(current.copy(content = content, updatedAt = System.currentTimeMillis()))
            recordRepository.addModificationHistory(recordId, "修改内容")
            debounceUpdateNotebookTimestamp()

            // 手动更新版本号，减少不必要的重组
            _uiState.value = _uiState.value.copy(version = current.version + 1)
            refreshModificationHistory()
        }
    }

    fun saveTitle(title: String) {
        val oldTitle = _uiState.value.title
        _uiState.value = _uiState.value.copy(title = title)
        viewModelScope.launch {
            val current = recordRepository.getRecordByIdOnce(recordId) ?: return@launch
            recordRepository.saveRecord(current.copy(title = title, updatedAt = System.currentTimeMillis()))
            // 同步更新目录树中对应节点的名称
            val treeNode = treeNodeRepository.getNodeByRecordId(recordId)
            if (treeNode != null) {
                treeNodeRepository.updateNodeName(treeNode.id, title)
            }
            recordRepository.addModificationHistory(recordId, "修改标题：$oldTitle → $title")
            debounceUpdateNotebookTimestamp()

            refreshModificationHistory()
        }
    }

    fun addAttachment(fileName: String, fileUri: String, fileType: String) {
        viewModelScope.launch {
            recordRepository.addAttachment(
                AttachmentEntity(
                    recordId = recordId,
                    fileName = fileName,
                    fileUri = fileUri,
                    fileType = fileType
                )
            )
            recordRepository.addModificationHistory(recordId, "添加附件：$fileName")
            debounceUpdateNotebookTimestamp()

            refreshAttachments()
            refreshModificationHistory()
        }
    }

    fun deleteAttachment(attachmentId: Long) {
        viewModelScope.launch {
            // 先查附件名称用于记录历史
            val attachment = recordRepository.getAttachmentById(attachmentId)
            val fileName = attachment?.fileName ?: "未知"
            recordRepository.deleteAttachment(attachmentId)
            if (attachment != null) {
                recordRepository.addModificationHistory(recordId, "删除附件：$fileName")
            }
            debounceUpdateNotebookTimestamp()

            refreshAttachments()
            refreshModificationHistory()
        }
    }

    fun addMetaData(key: String, value: String) {
        viewModelScope.launch {
            recordRepository.addMetaData(
                CustomMetaDataEntity(
                    recordId = recordId,
                    key = key,
                    value = value
                )
            )
            recordRepository.addModificationHistory(recordId, "添加元数据：$key = $value")
            debounceUpdateNotebookTimestamp()

            refreshMetaData()
            refreshModificationHistory()
        }
    }

    fun updateMetaData(metaDataId: Long, key: String, value: String) {
        viewModelScope.launch {
            // 先查旧值用于记录历史
            val oldMeta = recordRepository.getMetaDataById(metaDataId)
            val oldInfo = if (oldMeta != null) "${oldMeta.key} = ${oldMeta.value}" else "未知"
            recordRepository.updateMetaData(
                CustomMetaDataEntity(
                    id = metaDataId,
                    recordId = recordId,
                    key = key,
                    value = value
                )
            )
            recordRepository.addModificationHistory(recordId, "修改元数据：$oldInfo → $key = $value")
            debounceUpdateNotebookTimestamp()

            refreshMetaData()
            refreshModificationHistory()
        }
    }

    fun deleteMetaData(metaDataId: Long) {
        viewModelScope.launch {
            // 先查元数据内容用于记录历史
            val meta = recordRepository.getMetaDataById(metaDataId)
            val info = if (meta != null) "${meta.key} = ${meta.value}" else "未知"
            recordRepository.deleteMetaData(metaDataId)
            recordRepository.addModificationHistory(recordId, "删除元数据：$info")
            debounceUpdateNotebookTimestamp()

            refreshMetaData()
            refreshModificationHistory()
        }
    }

    /**
     * 防抖方式更新 notebook.updatedAt 时间戳，500ms 内多次操作合并为一次写入
     */
    private fun debounceUpdateNotebookTimestamp() {
        timestampJob?.cancel()
        timestampJob = viewModelScope.launch {
            delay(500)
            val treeNode = treeNodeRepository.getNodeByRecordId(recordId) ?: return@launch
            val notebook = notebookRepository.getNotebookById(treeNode.notebookId) ?: return@launch
            notebookRepository.updateNotebook(notebook.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    class Factory(
        private val recordId: Long,
        private val recordRepository: RecordRepository,
        private val treeNodeRepository: TreeNodeRepository,
        private val notebookRepository: NotebookRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordDetailViewModel(recordId, recordRepository, treeNodeRepository, notebookRepository) as T
        }
    }
}