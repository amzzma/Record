package com.yutaca.record.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yutaca.record.data.entity.TreeNodeEntity
import com.yutaca.record.data.repository.NotebookRepository
import com.yutaca.record.data.repository.TreeNodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FavoriteItem(
    val node: TreeNodeEntity,
    val notebookName: String = ""
)

class FavoritesViewModel(
    private val treeNodeRepository: TreeNodeRepository,
    private val notebookRepository: NotebookRepository
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val favorites: StateFlow<List<FavoriteItem>> = _favorites.asStateFlow()

    init {
        loadFavorites()
    }

    /**
     * 一次性查询收藏列表（快照），不监听数据库实时变化。
     * 这样在目录页取消收藏后，收藏页不会立即移除该项。
     * 用户切走再切回时由 Screen 调用 refresh() 触发重新加载。
     */
    private fun loadFavorites() {
        viewModelScope.launch {
            val nodes = treeNodeRepository.getFavoriteNodesOnce()
            val items = nodes.map { node ->
                val notebook = notebookRepository.getNotebookById(node.notebookId)
                val notebookName = notebook?.name ?: ""
                FavoriteItem(node = node, notebookName = notebookName)
            }
            _favorites.value = items
        }
    }

    /**
     * 切换收藏状态：更新数据库并同步更新本地 _favorites 列表。
     */
    fun toggleFavorite(nodeId: Long) {
        viewModelScope.launch {
            val item = _favorites.value.find { it.node.id == nodeId } ?: return@launch
            val newState = !item.node.isFavorite
            treeNodeRepository.updateFavoriteStatus(nodeId, newState)
            _favorites.value = _favorites.value.map { f ->
                if (f.node.id == nodeId) {
                    f.copy(node = f.node.copy(isFavorite = newState))
                } else f
            }
        }
    }

    /** 重新加载收藏列表（切回收藏页时调用） */
    fun refresh() {
        loadFavorites()
    }

    class Factory(
        private val treeNodeRepository: TreeNodeRepository,
        private val notebookRepository: NotebookRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FavoritesViewModel(treeNodeRepository, notebookRepository) as T
        }
    }
}