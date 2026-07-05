package com.yutaca.record.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tree_nodes",
    foreignKeys = [
        ForeignKey(
            entity = TreeNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentId"), Index("notebookId")]
)
data class TreeNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val parentId: Long? = null,
    val name: String,
    val isLeaf: Boolean,
    val recordId: Long? = null,
    val isFavorite: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)