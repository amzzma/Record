package com.yutaca.record.data.repository

import com.yutaca.record.data.dao.TreeNodeDao
import com.yutaca.record.data.entity.TreeNodeEntity
import kotlinx.coroutines.flow.Flow

class TreeNodeRepository(private val treeNodeDao: TreeNodeDao) {

    fun getRootNodes(notebookId: Long): Flow<List<TreeNodeEntity>> {
        return treeNodeDao.getRootNodes(notebookId)
    }

    fun getChildNodes(parentId: Long): Flow<List<TreeNodeEntity>> {
        return treeNodeDao.getChildNodes(parentId)
    }

    suspend fun getChildNodesOnce(parentId: Long): List<TreeNodeEntity> {
        return treeNodeDao.getChildNodesOnce(parentId)
    }

    fun getAllNodesByNotebook(notebookId: Long): Flow<List<TreeNodeEntity>> {
        return treeNodeDao.getAllNodesByNotebook(notebookId)
    }

    suspend fun getAllNodesByNotebookOnce(notebookId: Long): List<TreeNodeEntity> {
        return treeNodeDao.getAllNodesByNotebookOnce(notebookId)
    }

    suspend fun getNodeById(id: Long): TreeNodeEntity? {
        return treeNodeDao.getNodeById(id)
    }

    suspend fun getNodeByRecordId(recordId: Long): TreeNodeEntity? {
        return treeNodeDao.getNodeByRecordId(recordId)
    }

    suspend fun createNode(
        notebookId: Long,
        parentId: Long?,
        name: String,
        isLeaf: Boolean,
        recordId: Long? = null
    ): Long {
        val maxOrder = if (parentId == null) {
            treeNodeDao.getMaxSortOrderForRoot(notebookId)
        } else {
            treeNodeDao.getMaxSortOrderForChildren(parentId)
        }
        val node = TreeNodeEntity(
            notebookId = notebookId,
            parentId = parentId,
            name = name,
            isLeaf = isLeaf,
            recordId = recordId,
            sortOrder = maxOrder + 1
        )
        return treeNodeDao.insert(node)
    }

    /**
     * 创建节点时指定排序（用于导入场景，保留原始排序）
     */
    suspend fun createNode(
        notebookId: Long,
        parentId: Long?,
        name: String,
        isLeaf: Boolean,
        recordId: Long? = null,
        sortOrder: Int
    ): Long {
        val node = TreeNodeEntity(
            notebookId = notebookId,
            parentId = parentId,
            name = name,
            isLeaf = isLeaf,
            recordId = recordId,
            sortOrder = sortOrder
        )
        return treeNodeDao.insert(node)
    }

    suspend fun updateNode(node: TreeNodeEntity) {
        treeNodeDao.update(node)
    }

    suspend fun updateNodeName(id: Long, newName: String) {
        treeNodeDao.updateName(id, newName)
    }

    /**
     * 移动节点到新的父节点下（追加到末尾）
     *
     * @param id 要移动的节点 ID
     * @param newParentId 新的父节点 ID（null 表示移动到根节点/一级章节层级）
     * @param notebookId 笔记本 ID（用于计算根节点下的 sortOrder）
     */
    suspend fun moveNode(id: Long, newParentId: Long?, notebookId: Long) {
        val maxOrder = if (newParentId == null) {
            treeNodeDao.getMaxSortOrderForRoot(notebookId)
        } else {
            treeNodeDao.getMaxSortOrderForChildren(newParentId)
        }
        treeNodeDao.updateParent(id, newParentId, maxOrder + 1)
    }

    /**
     * 在同一层级中重新排序（仅更改 sortOrder）
     */
    suspend fun reorderNode(id: Long, newSortOrder: Int) {
        treeNodeDao.updateSortOrder(id, newSortOrder)
    }

    /**
     * 获取节点在树中的完整路径（从根到自身的带分隔符路径）
     * 例如：一级章节 > 二级文件夹 > 三级记录
     *
     * @param nodeId 目标节点 ID
     * @return 路径字符串，如 "第一章 > 1.1 基础概念"；如果是根级别节点则返回自身名称
     */
    suspend fun getNodePath(nodeId: Long): String {
        val pathSegments = mutableListOf<String>()
        var currentId: Long? = nodeId
        while (currentId != null) {
            val node = treeNodeDao.getNodeById(currentId) ?: break
            pathSegments.add(node.name)
            currentId = node.parentId
        }
        return pathSegments.reversed().joinToString(" > ")
    }

    fun getFavoriteNodes(): Flow<List<TreeNodeEntity>> {
        return treeNodeDao.getFavoriteNodes()
    }

    suspend fun getFavoriteNodesOnce(): List<TreeNodeEntity> {
        return treeNodeDao.getFavoriteNodesOnce()
    }

    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean) {
        treeNodeDao.updateFavoriteStatus(id, isFavorite)
    }

    /**
     * 删除节点及其所有子节点（级联删除）
     * 先删除子节点，再删除自身
     */
    suspend fun deleteNodeCascade(id: Long) {
        // 先删除子节点
        treeNodeDao.deleteByParentId(id)
        // 再删除自身
        treeNodeDao.deleteById(id)
    }
}
