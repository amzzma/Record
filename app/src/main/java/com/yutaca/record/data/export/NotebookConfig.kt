package com.yutaca.record.data.export

/**
 * 记录本配置文件 — 用于导入/导出整个记录本的全部数据。
 *
 * 包含一个 Notebook、其目录树节点（TreeNode）、以及每个节点关联的记录（Record），
 * 每条记录又包含附件（Attachment）、自定义元数据（CustomMetaData）和修改历史（ModificationHistory）。
 *
 * 所有实体间通过 refId（UUID）建立引用关系，避免导出数据库内部主键。
 */
data class NotebookConfig(
    /** 配置格式版本号，用于向前兼容 */
    val version: Int = 1,
    /** 导出时间戳 (epoch millis) */
    val exportedAt: Long,
    /** 笔记本元数据 */
    val notebook: NotebookItem,
    /** 目录树节点列表 */
    val treeNodes: List<TreeNodeItem> = emptyList(),
    /** 关联的记录列表 */
    val records: List<RecordItem> = emptyList()
)

// ───── Notebook ─────

data class NotebookItem(
    val name: String,
    val description: String = "",
    val coverColor: Long = 0xFFF5F5F5,
    val coverImageUri: String = ""
)

// ───── TreeNode ─────

data class TreeNodeItem(
    /** 导出时的临时引用 ID (UUID)，供 parentRefId 和 recordRefId 引用 */
    val refId: String,
    /** 父节点的 refId，null 表示根节点 */
    val parentRefId: String? = null,
    val name: String,
    val isLeaf: Boolean,
    /** 关联记录的 refId（仅叶子节点） */
    val recordRefId: String? = null,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0
)

// ───── Record ─────

data class RecordItem(
    val refId: String,
    val title: String,
    val content: String = "",
    val version: Int = 1,
    /** 附件列表 */
    val attachments: List<AttachmentItem> = emptyList(),
    /** 自定义键值对元数据 */
    val metaData: List<MetaDataItem> = emptyList(),
    /** 修改历史记录 */
    val modificationHistory: List<ModificationHistoryItem> = emptyList()
)

// ───── Attachment ─────

data class AttachmentItem(
    val refId: String,
    val fileName: String,
    /** 文件 URI（导出时的原始路径，导入后需用户重新选择文件） */
    val fileUri: String,
    val fileType: String,
    val fileSize: Long = 0
)

// ───── CustomMetaData ─────

data class MetaDataItem(
    val key: String,
    val value: String
)

// ───── ModificationHistory ─────

data class ModificationHistoryItem(
    val timestamp: Long,
    val message: String
)