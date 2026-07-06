package com.yutaca.record.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yutaca.record.data.entity.NotebookEntity
import com.yutaca.record.data.repository.NotebookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    UPDATED_DESC("更新时间 新→旧"),
    UPDATED_ASC("更新时间 旧→新"),
    CREATED_DESC("创建时间 新→旧"),
    CREATED_ASC("创建时间 旧→新"),
    NAME_ASC("名称 A→Z"),
    NAME_DESC("名称 Z→A")
}

data class HomeUiState(
    val notebooks: List<NotebookEntity> = emptyList(),
    val isLoading: Boolean = true,
    val sortMode: SortMode = SortMode.UPDATED_DESC
)

class HomeViewModel(
    private val notebookRepository: NotebookRepository
) : ViewModel() {

    /** 当前排序模式 */
    private val _sortMode = MutableStateFlow(SortMode.UPDATED_DESC)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    /**
     * 使用 Room Flow 自动订阅笔记本列表变化。
     * 通过 combine 与排序模式合并，排序也在 Flow 算子中完成。
     * SharingStarted.WhileSubscribed(5000) 表示：
     * - 当屏幕可见时保持活跃订阅
     * - 屏幕切到后台后保留 5 秒再取消，避免频繁重启 Flow
     * - Room Flow 有内存缓存，重新订阅时立即发射上次结果
     */
    val uiState: StateFlow<HomeUiState> = combine(
        notebookRepository.allNotebooks,
        _sortMode
    ) { notebooks, mode ->
        val sorted = when (mode) {
            SortMode.UPDATED_DESC -> notebooks.sortedByDescending { it.updatedAt }
            SortMode.UPDATED_ASC -> notebooks.sortedBy { it.updatedAt }
            SortMode.CREATED_DESC -> notebooks.sortedByDescending { it.createdAt }
            SortMode.CREATED_ASC -> notebooks.sortedBy { it.createdAt }
            SortMode.NAME_ASC -> notebooks.sortedBy { it.name }
            SortMode.NAME_DESC -> notebooks.sortedByDescending { it.name }
        }
        HomeUiState(
            notebooks = sorted,
            isLoading = false,
            sortMode = mode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun createNotebook(name: String, description: String = "") {
        viewModelScope.launch {
            notebookRepository.createNotebook(name, description)
            // Room Flow 会自动通知 UI，无需手动 refresh
        }
    }

    fun renameNotebook(id: Long, newName: String) {
        viewModelScope.launch {
            val notebook = notebookRepository.getNotebookById(id) ?: return@launch
            notebookRepository.updateNotebook(notebook.copy(name = newName))
            // Room Flow 会自动通知 UI
        }
    }

    fun deleteNotebook(id: Long) {
        viewModelScope.launch {
            notebookRepository.deleteNotebookCascade(id)
            // Room Flow 会自动通知 UI
        }
    }

    class Factory(private val notebookRepository: NotebookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(notebookRepository) as T
        }
    }
}