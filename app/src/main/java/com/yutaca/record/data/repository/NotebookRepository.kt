package com.yutaca.record.data.repository

import com.yutaca.record.data.dao.NotebookDao
import com.yutaca.record.data.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow

class NotebookRepository(private val notebookDao: NotebookDao) {

    val allNotebooks: Flow<List<NotebookEntity>> = notebookDao.getAllNotebooks()

    suspend fun getNotebookById(id: Long): NotebookEntity? {
        return notebookDao.getNotebookById(id)
    }

    suspend fun createNotebook(name: String, description: String = ""): Long {
        val notebook = NotebookEntity(
            name = name,
            description = description
        )
        return notebookDao.insert(notebook)
    }

    suspend fun updateNotebook(notebook: NotebookEntity) {
        notebookDao.update(notebook)
    }

    suspend fun updateCoverImage(id: Long, uri: String) {
        notebookDao.updateCoverImage(id, uri)
    }

    suspend fun deleteNotebook(id: Long) {
        notebookDao.deleteById(id)
    }
}