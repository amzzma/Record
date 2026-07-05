package com.yutaca.record.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yutaca.record.data.entity.TreeNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TreeNodeDao {

    /** 获取记录本的根节点（notebookId 对应的顶级节点，即 parentId 为 null 的节点） */
    @Query("SELECT * FROM tree_nodes WHERE notebookId = :notebookId AND parentId IS NULL ORDER BY sortOrder")
    fun getRootNodes(notebookId: Long): Flow<List<TreeNodeEntity>>

    /** 获取指定父节点下的所有子节点 */
    @Query("SELECT * FROM tree_nodes WHERE parentId = :parentId ORDER BY sortOrder")
    fun getChildNodes(parentId: Long): Flow<List<TreeNodeEntity>>

    /** 一次性获取所有子节点（非 Flow，用于递归加载） */
    @Query("SELECT * FROM tree_nodes WHERE parentId = :parentId ORDER BY sortOrder")
    suspend fun getChildNodesOnce(parentId: Long): List<TreeNodeEntity>

    /** 获取记录本的所有 TreeNode（用于构建整棵树） */
    @Query("SELECT * FROM tree_nodes WHERE notebookId = :notebookId ORDER BY sortOrder")
    fun getAllNodesByNotebook(notebookId: Long): Flow<List<TreeNodeEntity>>

    /** 一次性获取记录本的所有 TreeNode（用于导出） */
    @Query("SELECT * FROM tree_nodes WHERE notebookId = :notebookId ORDER BY sortOrder")
    suspend fun getAllNodesByNotebookOnce(notebookId: Long): List<TreeNodeEntity>

    /** 获取单个节点 */
    @Query("SELECT * FROM tree_nodes WHERE id = :id")
    suspend fun getNodeById(id: Long): TreeNodeEntity?

    /** 根据 recordId 查找对应的 TreeNode（用于名称同步） */
    @Query("SELECT * FROM tree_nodes WHERE recordId = :recordId LIMIT 1")
    suspend fun getNodeByRecordId(recordId: Long): TreeNodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(node: TreeNodeEntity): Long

    @Update
    suspend fun update(node: TreeNodeEntity)

    @Query("UPDATE tree_nodes SET name = :newName WHERE id = :id")
    suspend fun updateName(id: Long, newName: String)

    @Delete
    suspend fun delete(node: TreeNodeEntity)

    @Query("DELETE FROM tree_nodes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 删除指定父节点下的所有子节点（用于级联删除） */
    @Query("DELETE FROM tree_nodes WHERE parentId = :parentId")
    suspend fun deleteByParentId(parentId: Long)

    /** 获取同一 parent 下的最大 sortOrder */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM tree_nodes WHERE parentId IS NULL AND notebookId = :notebookId")
    suspend fun getMaxSortOrderForRoot(notebookId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM tree_nodes WHERE parentId = :parentId")
    suspend fun getMaxSortOrderForChildren(parentId: Long): Int

    /** 更新节点的父节点和排序值（用于移动节点） */
    @Query("UPDATE tree_nodes SET parentId = :newParentId, sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateParent(id: Long, newParentId: Long?, sortOrder: Int)

    /** 仅更新排序值（用于同层级重新排序） */
    @Query("UPDATE tree_nodes SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    /** 获取所有已收藏的节点（实时 Flow） */
    @Query("SELECT * FROM tree_nodes WHERE isFavorite = 1 ORDER BY sortOrder")
    fun getFavoriteNodes(): Flow<List<TreeNodeEntity>>

    /** 一次性获取所有已收藏的节点（快照，用于切回收藏页时刷新） */
    @Query("SELECT * FROM tree_nodes WHERE isFavorite = 1 ORDER BY sortOrder")
    suspend fun getFavoriteNodesOnce(): List<TreeNodeEntity>

    /** 更新节点的收藏状态 */
    @Query("UPDATE tree_nodes SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)
}
