package com.yutaca.record.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yutaca.record.data.entity.TreeNodeEntity
import com.yutaca.record.data.repository.NotebookRepository
import com.yutaca.record.data.repository.RecordRepository
import com.yutaca.record.data.repository.TreeNodeRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== 三级数据模型 ====================

/** 第一级：大章节（如 "一、XXX", "二、XXX"） */
data class LevelOneChapter(
    val id: String,
    val title: String,
    val isExpanded: Boolean = false,
    val isFavorite: Boolean = false,
    val children: List<LevelTwoNode> = emptyList()
)

/** 第二级：既可以是中间文件夹（子章节），也可以是直接归属于一级章节的记录 */
sealed class LevelTwoNode {
    /** 中间文件夹（子章节），如 "1.1 基础概念" —— 可折叠，带记录 */
    data class Folder(
        val id: String, // treeNode id
        val title: String,
        val isExpanded: Boolean = false,
        val records: List<LevelThreeRecord> = emptyList()
    ) : LevelTwoNode()

    /** 叶子节点（记录），直接归属于一级章节 */
    data class Leaf(
        val id: String, // recordId（供导航用）
        val treeNodeId: String, // treeNode id（供删除/重命名用）
        val title: String
    ) : LevelTwoNode()
}

/** 第三级：具体的记录（必须归属于第二级的 Folder 下） */
data class LevelThreeRecord(
    val id: String, // recordId（供导航用）
    val treeNodeId: String, // treeNode id（供删除/重命名用）
    val title: String
)

/** 目录页的整体 UI 状态 */
data class NotebookDirectoryUiState(
    val notebookTitle: String = "",
    val chapters: List<LevelOneChapter> = emptyList(),
    val selectedTabIndex: Int = 0, // 0=章节, 1=关于
    val isLoading: Boolean = true,
    val aboutDescription: String = "",
    val aboutCreatedAt: Long = 0L,
    val aboutUpdatedAt: Long = 0L,
    val totalRecordCount: Int = 0,
    val aboutCoverImageUri: String = ""
)

class DirectoryViewModel(
    private val notebookId: Long,
    private val notebookRepository: NotebookRepository,
    private val treeNodeRepository: TreeNodeRepository,
    private val recordRepository: RecordRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotebookDirectoryUiState())
    val uiState: StateFlow<NotebookDirectoryUiState> = _uiState.asStateFlow()

    // 导航到记录详情
    private val _navigateToRecord = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToRecord: SharedFlow<Long> = _navigateToRecord.asSharedFlow()

    // 触发添加节点对话框
    private val _addNodeEvent = MutableSharedFlow<Unit>()
    val addNodeEvent: SharedFlow<Unit> = _addNodeEvent.asSharedFlow()

    /**
     * 展开/折叠状态存储
     * key 格式: "L1_${id}" 表示一级节点， "L2_${id}" 表示二级文件夹
     * value: true=展开, false=折叠
     */
    private val expandedState = mutableMapOf<String, Boolean>()

    init {
        loadNotebook()
        loadTree()
    }

    // ==================== 数据加载 ====================

    private fun loadNotebook() {
        viewModelScope.launch {
            val notebook = notebookRepository.getNotebookById(notebookId)
            _uiState.value = _uiState.value.copy(
                notebookTitle = notebook?.name ?: "",
                aboutDescription = notebook?.description ?: "",
                aboutCreatedAt = notebook?.createdAt ?: 0L,
                aboutUpdatedAt = notebook?.updatedAt ?: 0L,
                aboutCoverImageUri = notebook?.coverImageUri ?: ""
            )
        }
    }

    private fun loadTree() {
        viewModelScope.launch {
            treeNodeRepository.getAllNodesByNotebook(notebookId).collect { allNodes ->
                val chapters = buildThreeLevelTree(allNodes)
                val leafCount = countAllLeafRecords(chapters)
                _uiState.value = _uiState.value.copy(
                    chapters = chapters,
                    isLoading = false,
                    totalRecordCount = leafCount
                )
            }
        }
    }

    /**
     * 从扁平节点列表构建固定三级树结构
     *
     * 映射规则：
     * - Level 1: parentId == null && isLeaf == false
     * - Level 2 Folder: parentId == level1.id && isLeaf == false
     * - Level 2 Leaf: parentId == level1.id && isLeaf == true
     * - Level 3: parentId == level2.id && isLeaf == true
     */
    private fun buildThreeLevelTree(allNodes: List<TreeNodeEntity>): List<LevelOneChapter> {
        // 1. 提取一级节点（parentId == null 且不是叶子）
        val level1Nodes = allNodes.filter { it.parentId == null && !it.isLeaf }.sortedBy { it.sortOrder }

        return level1Nodes.map { l1 ->
            // 2. 从一级节点的子节点中分离出二级 Folder 和二级 Leaf
            val level2Candidates = allNodes.filter { it.parentId == l1.id }.sortedBy { it.sortOrder }

                val children = level2Candidates.mapNotNull { node ->
                    if (node.isLeaf) {
                        // 二级叶子节点（记录）
                        node.recordId?.let {
                            LevelTwoNode.Leaf(
                                id = it.toString(), // recordId（供导航用）
                                treeNodeId = node.id.toString(), // treeNode id（供删除/重命名用）
                                title = node.name
                            )
                        }
                    } else {
                        // 二级文件夹（子章节）
                        val level3Records = allNodes
                            .filter { it.parentId == node.id && it.isLeaf }
                            .sortedBy { it.sortOrder }
                            .mapNotNull {
                                LevelThreeRecord(
                                    id = it.recordId?.toString() ?: return@mapNotNull null,
                                    treeNodeId = it.id.toString(),
                                    title = it.name
                                )
                            }

                        LevelTwoNode.Folder(
                            id = node.id.toString(),
                            title = node.name,
                            isExpanded = expandedState["L2_${node.id}"] ?: false,
                            records = level3Records
                        )
                    }
                }

            LevelOneChapter(
                id = l1.id.toString(),
                title = l1.name,
                isExpanded = expandedState["L1_${l1.id}"] ?: false,
                isFavorite = l1.isFavorite,
                children = children
            )
        }
    }

    /**
     * 统计目录树中所有叶子记录的数量（包括二级 Leaf 和三级 Record）
     */
    private fun countAllLeafRecords(chapters: List<LevelOneChapter>): Int {
        var count = 0
        for (chapter in chapters) {
            for (child in chapter.children) {
                when (child) {
                    is LevelTwoNode.Leaf -> count++
                    is LevelTwoNode.Folder -> count += child.records.size
                }
            }
        }
        return count
    }

    /**
     * 获取可用于添加节点的"一级章节"列表（供对话框联动下拉使用）
     */
    fun getLevelOneChapters(): List<Pair<String, String>> {
        return _uiState.value.chapters.map { it.id to it.title }
    }

    /**
     * 获取所有一级章节的 ID 到 名称 的映射（不含自身，用于移动对话框排除自身）
     */
    fun getLevelOneChaptersExcludingSelf(selfId: String): List<Pair<String, String>> {
        return _uiState.value.chapters
            .filter { it.id != selfId }
            .map { it.id to it.title }
    }

    /**
     * 获取所有二级文件夹的 ID 到 名称 的映射（用于移动记录到三级）
     */
    fun getAllLevelTwoFolders(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (chapter in _uiState.value.chapters) {
            for (child in chapter.children) {
                if (child is LevelTwoNode.Folder) {
                    result.add(child.id to child.title)
                }
            }
        }
        return result
    }

    /**
     * 获取一级章节及其下所有二级文件夹的列表（用于移动二级叶子/三级记录到目标）
     * 返回 Pair<targetId, label>，其中 targetId 是 treeNode id
     * label 格式: "章节名 > 文件夹名" 或 "章节名 (直接)"
     */
    fun getLevelOneChaptersWithFolders(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (chapter in _uiState.value.chapters) {
            // 直接挂到一级章节下（作为二级叶子）
            result.add(chapter.id to "${chapter.title} (直接)")
            // 挂到该章节下的二级文件夹中（作为三级记录）
            for (child in chapter.children) {
                if (child is LevelTwoNode.Folder) {
                    result.add(child.id to "${chapter.title} > ${child.title}")
                }
            }
        }
        return result
    }

    /**
     * 获取可供二级文件夹移动的目标一级章节列表（排除自身当前所在章节）
     */
    fun getLevelOneChaptersForFolderMove(currentChapterId: String): List<Pair<String, String>> {
        return _uiState.value.chapters
            .filter { it.id != currentChapterId }
            .map { it.id to it.title }
    }

    /**
     * 获取可用于添加节点的"二级文件夹"列表（供对话框联动下拉使用）
     * 标签格式: "一级章节名 > 二级文件夹名"
     */
    fun getLevelTwoFolders(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (chapter in _uiState.value.chapters) {
            for (child in chapter.children) {
                if (child is LevelTwoNode.Folder) {
                    result.add(child.id to "${chapter.title} > ${child.title}")
                }
            }
        }
        return result
    }

    // ==================== Tab 切换 ====================

    fun setSelectedTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTabIndex = tab)
    }

    // ==================== 展开/折叠 ====================

    /**
     * 切换一级节点展开状态
     */
    fun toggleLevelOne(nodeId: String) {
        val key = "L1_$nodeId"
        expandedState[key] = !(expandedState[key] ?: false)
        loadTree()
    }

    /**
     * 切换二级文件夹展开状态
     */
    fun toggleLevelTwo(nodeId: String) {
        val key = "L2_$nodeId"
        expandedState[key] = !(expandedState[key] ?: false)
        loadTree()
    }

    // ==================== 添加节点 ====================

    /**
     * 点击底部添加卡片时触发事件，Screen 收到后弹出对话框
     */
    fun onAddNodeClicked() {
        viewModelScope.launch {
            _addNodeEvent.emit(Unit)
        }
    }

    /**
     * 添加节点
     *
     * @param name 节点名称
     * @param level 目标层级: 1=一级章节, 2=二级节点, 3=三级记录
     * @param parentId 父节点 ID（level=2 时为一级章节 ID；level=3 时为二级文件夹 ID；level=1 时不传）
     * @param isFolder 仅在 level=2 时有意义：true=创建二级文件夹, false=创建二级叶子记录
     */
    fun addNode(name: String, level: Int, parentId: String? = null, isFolder: Boolean = false) {
        viewModelScope.launch {
            when (level) {
                1 -> {
                    // 创建一级章节（parentId = null, isLeaf = false）
                    treeNodeRepository.createNode(
                        notebookId = notebookId,
                        parentId = null,
                        name = name,
                        isLeaf = false
                    )
                }
                2 -> {
                    val pId = parentId?.toLongOrNull()
                    if (isFolder) {
                        // 创建二级文件夹
                        treeNodeRepository.createNode(
                            notebookId = notebookId,
                            parentId = pId,
                            name = name,
                            isLeaf = false
                        )
                    } else {
                        // 创建二级叶子记录
                        val recordId = recordRepository.createRecord(name)
                        val nodeId = treeNodeRepository.createNode(
                            notebookId = notebookId,
                            parentId = pId,
                            name = name,
                            isLeaf = true,
                            recordId = recordId
                        )
                        // 添加修改历史，包含文件路径
                        val path = treeNodeRepository.getNodePath(nodeId)
                        recordRepository.addModificationHistory(recordId, "创建记录：$path")
                    }
                }
                3 -> {
                    val pId = parentId?.toLongOrNull()
                    if (pId != null) {
                        // 创建三级记录（挂到二级文件夹下）
                        val recordId = recordRepository.createRecord(name)
                        val nodeId = treeNodeRepository.createNode(
                            notebookId = notebookId,
                            parentId = pId,
                            name = name,
                            isLeaf = true,
                            recordId = recordId
                        )
                        // 添加修改历史，包含文件路径
                        val path = treeNodeRepository.getNodePath(nodeId)
                        recordRepository.addModificationHistory(recordId, "创建记录：$path")
                    }
                }
            }
        }
    }

    // ==================== 删除节点（级联） ====================

    /**
     * 删除节点及其所有子节点，同时清理对应的 Record 表记录
     */
    fun deleteNode(nodeId: String) {
        viewModelScope.launch {
            val id = nodeId.toLongOrNull() ?: return@launch

            // 1. 收集所有需要删除的 recordId（包括自身和递归子节点）
            val recordIdsToDelete = mutableListOf<Long>()

            // 先查自身
            val selfNode = treeNodeRepository.getNodeById(id)
            selfNode?.recordId?.let { recordIdsToDelete.add(it) }

            // 递归收集子节点中的记录 ID
            suspend fun collectRecordIds(parentId: Long) {
                val children = treeNodeRepository.getChildNodesOnce(parentId)
                for (child in children) {
                    child.recordId?.let { recordIdsToDelete.add(it) }
                    if (!child.isLeaf) {
                        collectRecordIds(child.id)
                    }
                }
            }
            collectRecordIds(id)

            // 2. 先删除 Record 表中的记录
            for (recordId in recordIdsToDelete.distinct()) {
                recordRepository.deleteRecord(recordId)
            }

            // 3. 再删除树节点（级联删除 tree_nodes）
            treeNodeRepository.deleteNodeCascade(id)
        }
    }

    // ==================== 重命名节点 ====================

    /**
     * 重命名节点，如果该节点是记录（有 recordId），同步更新 Record 表中的 title
     */
    fun renameNode(nodeId: String, newName: String) {
        viewModelScope.launch {
            val id = nodeId.toLongOrNull() ?: return@launch

            // 1. 先更新 tree_nodes.name
            treeNodeRepository.updateNodeName(id, newName)

            // 2. 如果该节点绑定了 record，同步更新 records.title
            val node = treeNodeRepository.getNodeById(id)
            if (node?.recordId != null) {
                val record = recordRepository.getRecordByIdOnce(node.recordId)
                if (record != null) {
                    recordRepository.saveRecord(record.copy(title = newName))
                }
            }
        }
    }

    // ==================== 更新目录描述 ====================

    /**
     * 更新目录描述，同时同步修改时间
     */
    fun updateDescription(newDescription: String) {
        viewModelScope.launch {
            val notebook = notebookRepository.getNotebookById(notebookId) ?: return@launch
            notebookRepository.updateNotebook(
                notebook.copy(
                    description = newDescription,
                    updatedAt = System.currentTimeMillis()
                )
            )
            loadNotebook()
        }
    }

    /**
     * 更新目录封面图片 URI
     */
    fun updateCoverImage(uri: String) {
        viewModelScope.launch {
            notebookRepository.updateCoverImage(notebookId, uri)
            _uiState.value = _uiState.value.copy(aboutCoverImageUri = uri)
        }
    }

    // ==================== 收藏/取消收藏 ====================

    /**
     * 切换一级章节的收藏状态
     * 点击星星图标时调用
     */
    fun toggleFavorite(nodeId: String) {
        viewModelScope.launch {
            val id = nodeId.toLongOrNull() ?: return@launch
            val node = treeNodeRepository.getNodeById(id) ?: return@launch
            treeNodeRepository.updateFavoriteStatus(id, !node.isFavorite)
        }
    }

    // ==================== 移动节点 ====================

    /**
     * 移动节点到新的父节点下
     *
     * 规则说明：
     * - 一级章节：不改变 parentId（保持为 null），仅重新排序（通过 moveNode 的 newParentId=null 实现放到末尾）
     * - 二级叶子记录：可移动到一级章节下（成为二级叶子）或二级文件夹下（成为三级记录）
     * - 二级文件夹：可移动到任意一级章节下（其内部子记录跟着走）
     * - 三级记录：可移动到一级章节下（成为二级叶子）或二级文件夹下（成为三级记录）
     *
     * @param nodeId 要移动的节点 ID（treeNode id）
     * @param newParentId 目标父节点 ID（null 表示移动到根节点/一级章节层级）
     */
    fun moveNode(nodeId: String, newParentId: String?) {
        viewModelScope.launch {
            val id = nodeId.toLongOrNull() ?: return@launch
            val targetParentId = newParentId?.toLongOrNull()

            // 移动前查询节点信息（用于记录修改历史）
            val node = treeNodeRepository.getNodeById(id)

            // 执行移动
            treeNodeRepository.moveNode(id, targetParentId, notebookId)

            // 如果该节点是叶子记录（有 recordId），在对应的 record 下追加修改历史
            if (node?.recordId != null) {
                // 获取目标父节点名称
                val targetName = if (targetParentId != null) {
                    treeNodeRepository.getNodeById(targetParentId)?.name ?: "未知"
                } else {
                    "根节点"
                }
                recordRepository.addModificationHistory(
                    node.recordId,
                    "移动记录：${node.name} → ${targetName}"
                )
            }
        }
    }

    // ==================== 导航 ====================

    /**
     * 点击记录节点时跳转
     */
    fun onRecordClicked(recordId: String) {
        viewModelScope.launch {
            recordId.toLongOrNull()?.let { _navigateToRecord.emit(it) }
        }
    }

    // ==================== Factory ====================

    class Factory(
        private val notebookId: Long,
        private val notebookRepository: NotebookRepository,
        private val treeNodeRepository: TreeNodeRepository,
        private val recordRepository: RecordRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DirectoryViewModel(
                notebookId, notebookRepository, treeNodeRepository, recordRepository
            ) as T
        }
    }
}