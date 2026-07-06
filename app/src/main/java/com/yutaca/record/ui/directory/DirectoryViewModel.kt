package com.yutaca.record.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yutaca.record.data.entity.TreeNodeEntity
import com.yutaca.record.data.repository.NotebookRepository
import com.yutaca.record.data.repository.RecordRepository
import com.yutaca.record.data.repository.TreeNodeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    /** 当前选中的标签页 */
    private val _selectedTabIndex = MutableStateFlow(0)

    /** 展开/折叠状态存储（用 StateFlow 驱动，修改时自动触发重组） */
    private val _expandedKeys = MutableStateFlow<Set<String>>(emptySet())

    /** 防抖时间戳写入任务，500ms 内多次操作合并为一次写入 */
    private var timestampJob: Job? = null

    /**
     * 使用 Room Flow 自动订阅笔记本信息和目录树变化。
     * notebookRepository.getNotebookByIdFlow(notebookId) 和
     * treeNodeRepository.getAllNodesByNotebook(notebookId) 都是 Room Flow，
     * 当数据库中对应数据发生变化时自动通知。
     * Room Flow 有内存缓存，重新订阅时立即发射上次结果，回到此页时无需等待数据库查询。
     */
    val uiState: StateFlow<NotebookDirectoryUiState> = combine(
        notebookRepository.getNotebookByIdFlow(notebookId),
        treeNodeRepository.getAllNodesByNotebook(notebookId),
        _selectedTabIndex,
        _expandedKeys
    ) { notebook, allNodes, selectedTab, expandedKeys ->
        val chapters = buildThreeLevelTree(allNodes, expandedKeys)
        val leafCount = countAllLeafRecords(chapters)
        NotebookDirectoryUiState(
            notebookTitle = notebook?.name ?: "",
            chapters = chapters,
            selectedTabIndex = selectedTab,
            isLoading = false,
            aboutDescription = notebook?.description ?: "",
            aboutCreatedAt = notebook?.createdAt ?: 0L,
            aboutUpdatedAt = notebook?.updatedAt ?: 0L,
            totalRecordCount = leafCount,
            aboutCoverImageUri = notebook?.coverImageUri ?: ""
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotebookDirectoryUiState(isLoading = true)
    )

    // 导航到记录详情
    private val _navigateToRecord = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToRecord: SharedFlow<Long> = _navigateToRecord.asSharedFlow()

    // 触发添加节点对话框
    private val _addNodeEvent = MutableSharedFlow<Unit>()
    val addNodeEvent: SharedFlow<Unit> = _addNodeEvent.asSharedFlow()

    /**
     * 从扁平节点列表构建固定三级树结构
     *
     * 映射规则：
     * - Level 1: parentId == null && isLeaf == false
     * - Level 2 Folder: parentId == level1.id && isLeaf == false
     * - Level 2 Leaf: parentId == level1.id && isLeaf == true
     * - Level 3: parentId == level2.id && isLeaf == true
     *
     * 使用 groupBy 一次遍历构建父子映射，避免 O(n²) 的重复 filter 扫描
     */
    private fun buildThreeLevelTree(
        allNodes: List<TreeNodeEntity>,
        expandedKeys: Set<String>
    ): List<LevelOneChapter> {
        // 一次遍历构建父节点 ID -> 子节点列表的映射
        val childrenMap = allNodes.groupBy { it.parentId }

        // 1. 提取一级节点（parentId == null 且不是叶子）
        val level1Nodes = allNodes.filter { it.parentId == null && !it.isLeaf }.sortedBy { it.sortOrder }

        return level1Nodes.map { l1 ->
            // 2. 从一级节点的子节点中分离出二级 Folder 和二级 Leaf
            val level2Candidates = (childrenMap[l1.id] ?: emptyList()).sortedBy { it.sortOrder }

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
                    val level3Records = (childrenMap[node.id] ?: emptyList())
                        .filter { it.isLeaf }
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
                        isExpanded = expandedKeys.contains("L2_${node.id}"),
                        records = level3Records
                    )
                }
            }

            LevelOneChapter(
                id = l1.id.toString(),
                title = l1.name,
                isExpanded = expandedKeys.contains("L1_${l1.id}"),
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
        return uiState.value.chapters.map { it.id to it.title }
    }

    /**
     * 获取所有一级章节的 ID 到 名称 的映射（不含自身，用于移动对话框排除自身）
     */
    fun getLevelOneChaptersExcludingSelf(selfId: String): List<Pair<String, String>> {
        return uiState.value.chapters
            .filter { it.id != selfId }
            .map { it.id to it.title }
    }

    /**
     * 获取所有二级文件夹的 ID 到 名称 的映射（用于移动记录到三级）
     */
    fun getAllLevelTwoFolders(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (chapter in uiState.value.chapters) {
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
        for (chapter in uiState.value.chapters) {
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
        return uiState.value.chapters
            .filter { it.id != currentChapterId }
            .map { it.id to it.title }
    }

    /**
     * 获取可用于添加节点的"二级文件夹"列表（供对话框联动下拉使用）
     * 标签格式: "一级章节名 > 二级文件夹名"
     */
    fun getLevelTwoFolders(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        for (chapter in uiState.value.chapters) {
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
        _selectedTabIndex.value = tab
    }

    // ==================== 展开/折叠 ====================

    /**
     * 切换一级节点展开状态
     */
    fun toggleLevelOne(nodeId: String) {
        val key = "L1_$nodeId"
        val current = _expandedKeys.value
        _expandedKeys.value = if (key in current) current - key else current + key
    }

    /**
     * 切换二级文件夹展开状态
     */
    fun toggleLevelTwo(nodeId: String) {
        val key = "L2_$nodeId"
        val current = _expandedKeys.value
        _expandedKeys.value = if (key in current) current - key else current + key
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
                        treeNodeRepository.createNode(
                            notebookId = notebookId,
                            parentId = pId,
                            name = name,
                            isLeaf = false
                        )
                    } else {
                        val recordId = recordRepository.createRecord(name)
                        val nodeId = treeNodeRepository.createNode(
                            notebookId = notebookId,
                            parentId = pId,
                            name = name,
                            isLeaf = true,
                            recordId = recordId
                        )
                        val path = treeNodeRepository.getNodePath(nodeId)
                        recordRepository.addModificationHistory(recordId, "创建记录：$path")
                    }
                }
                3 -> {
                    val pId = parentId?.toLongOrNull()
                    if (pId != null) {
                        val recordId = recordRepository.createRecord(name)
                        val nodeId = treeNodeRepository.createNode(
                            notebookId = notebookId,
                            parentId = pId,
                            name = name,
                            isLeaf = true,
                            recordId = recordId
                        )
                        val path = treeNodeRepository.getNodePath(nodeId)
                        recordRepository.addModificationHistory(recordId, "创建记录：$path")
                    }
                }
            }
            // Room Flow 会自动通知 UI，无需手动 refresh
            debounceUpdateNotebookTimestamp()
        }
    }

    // ==================== 删除节点（级联） ====================

    fun deleteNode(nodeId: String) {
        viewModelScope.launch {
            val id = nodeId.toLongOrNull() ?: return@launch

            val recordIdsToDelete = mutableListOf<Long>()

            val selfNode = treeNodeRepository.getNodeById(id)
            selfNode?.recordId?.let { recordIdsToDelete.add(it) }

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

            for (recordId in recordIdsToDelete.distinct()) {
                recordRepository.deleteRecord(recordId)
            }

            treeNodeRepository.deleteNodeCascade(id)
            // Room Flow 会自动通知 UI
            debounceUpdateNotebookTimestamp()
        }
    }

    // ==================== 重命名节点 ====================

    fun renameNode(nodeId: String, newName: String) {
        viewModelScope.launch {
            val id = nodeId.toLongOrNull() ?: return@launch

            treeNodeRepository.updateNodeName(id, newName)

            val node = treeNodeRepository.getNodeById(id)
            if (node?.recordId != null) {
                val record = recordRepository.getRecordByIdOnce(node.recordId)
                if (record != null) {
                    recordRepository.saveRecord(record.copy(title = newName))
                }
            }
            // Room Flow 会自动通知 UI
            debounceUpdateNotebookTimestamp()
        }
    }

    // ==================== 更新目录描述 ====================

    fun updateDescription(newDescription: String) {
        viewModelScope.launch {
            val notebook = notebookRepository.getNotebookById(notebookId) ?: return@launch
            notebookRepository.updateNotebook(
                notebook.copy(
                    description = newDescription,
                    updatedAt = System.currentTimeMillis()
                )
            )
            // Room Flow 会自动通知 UI
        }
    }

    /**
     * 更新目录封面图片 URI
     */
    fun updateCoverImage(uri: String) {
        viewModelScope.launch {
            notebookRepository.updateCoverImage(notebookId, uri)
            // Room Flow 会自动通知 UI
            debounceUpdateNotebookTimestamp()
        }
    }

    // ==================== 更新 "最后修改" 时间 ====================

    /**
     * 防抖方式更新 notebook.updatedAt 时间戳，500ms 内多次操作合并为一次写入
     */
    private fun debounceUpdateNotebookTimestamp() {
        timestampJob?.cancel()
        timestampJob = viewModelScope.launch {
            delay(500)
            val notebook = notebookRepository.getNotebookById(notebookId) ?: return@launch
            notebookRepository.updateNotebook(notebook.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    // ==================== 收藏/取消收藏 ====================

    fun toggleFavorite(nodeId: String) {
        viewModelScope.launch {
            val id = nodeId.toLongOrNull() ?: return@launch
            val node = treeNodeRepository.getNodeById(id) ?: return@launch
            treeNodeRepository.updateFavoriteStatus(id, !node.isFavorite)
            // Room Flow 会自动通知 UI
            debounceUpdateNotebookTimestamp()
        }
    }

    // ==================== 移动节点 ====================

    fun moveNode(nodeId: String, newParentId: String?) {
        viewModelScope.launch {
            val id = nodeId.toLongOrNull() ?: return@launch
            val targetParentId = newParentId?.toLongOrNull()

            val node = treeNodeRepository.getNodeById(id)

            treeNodeRepository.moveNode(id, targetParentId, notebookId)

            if (node?.recordId != null) {
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
            // Room Flow 会自动通知 UI
            debounceUpdateNotebookTimestamp()
        }
    }

    fun moveChapterToPosition(chapterId: String, targetIndex: Int) {
        viewModelScope.launch {
            // Read current chapters from the StateFlow value (safe in coroutine)
            val localChapters = uiState.value.chapters.toMutableList()
            val currentIndex = localChapters.indexOfFirst { it.id == chapterId }
            if (currentIndex == -1) return@launch

            val chapterToMove = localChapters.removeAt(currentIndex)
            val adjustedIndex = if (currentIndex < targetIndex) targetIndex - 1 else targetIndex
            localChapters.add(adjustedIndex.coerceIn(0, localChapters.size), chapterToMove)

            localChapters.forEachIndexed { idx, ch ->
                val nodeId = ch.id.toLongOrNull() ?: return@forEachIndexed
                treeNodeRepository.reorderNode(nodeId, idx)
            }
            // Room Flow 会自动通知 UI
            debounceUpdateNotebookTimestamp()
        }
    }

    // ==================== 导航 ====================

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