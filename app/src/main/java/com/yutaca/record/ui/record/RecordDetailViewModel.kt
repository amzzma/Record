package com.yutaca.record.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yutaca.record.data.entity.AttachmentEntity
import com.yutaca.record.data.entity.CustomMetaDataEntity
import com.yutaca.record.data.entity.ModificationHistoryEntity
import com.yutaca.record.data.repository.RecordRepository
import com.yutaca.record.data.repository.TreeNodeRepository
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
    private val treeNodeRepository: TreeNodeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordDetailUiState())
    val uiState: StateFlow<RecordDetailUiState> = _uiState.asStateFlow()

    init {
        loadRecord()
        loadAttachments()
        loadMetaData()
        loadModificationHistory()
    }

    private fun loadRecord() {
        viewModelScope.launch {
            recordRepository.getRecordById(recordId).collect { record ->
                if (record != null) {
                    _uiState.value = _uiState.value.copy(
                        title = record.title,
                        content = record.content,
                        version = record.version
                    )
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun loadAttachments() {
        viewModelScope.launch {
            recordRepository.getAttachmentsByRecordId(recordId).collect { attachments ->
                _uiState.value = _uiState.value.copy(attachments = attachments)
            }
        }
    }

    private fun loadMetaData() {
        viewModelScope.launch {
            recordRepository.getMetaDataByRecordId(recordId).collect { metaData ->
                _uiState.value = _uiState.value.copy(metaData = metaData)
            }
        }
    }

    private fun loadModificationHistory() {
        viewModelScope.launch {
            recordRepository.getModificationHistoryByRecordId(recordId).collect { history ->
                _uiState.value = _uiState.value.copy(modificationHistory = history)
            }
        }
    }

    fun saveContent(content: String) {
        _uiState.value = _uiState.value.copy(content = content)
        viewModelScope.launch {
            val current = recordRepository.getRecordByIdOnce(recordId) ?: return@launch
            recordRepository.saveRecord(current.copy(content = content, updatedAt = System.currentTimeMillis()))
            recordRepository.addModificationHistory(recordId, "修改内容")
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
        }
    }

    fun deleteMetaData(metaDataId: Long) {
        viewModelScope.launch {
            // 先查元数据内容用于记录历史
            val meta = recordRepository.getMetaDataById(metaDataId)
            val info = if (meta != null) "${meta.key} = ${meta.value}" else "未知"
            recordRepository.deleteMetaData(metaDataId)
            recordRepository.addModificationHistory(recordId, "删除元数据：$info")
        }
    }

    class Factory(
        private val recordId: Long,
        private val recordRepository: RecordRepository,
        private val treeNodeRepository: TreeNodeRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordDetailViewModel(recordId, recordRepository, treeNodeRepository) as T
        }
    }
}