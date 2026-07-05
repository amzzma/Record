package com.yutaca.record.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yutaca.record.data.entity.NotebookEntity
import com.yutaca.record.data.repository.NotebookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val notebooks: List<NotebookEntity> = emptyList(),
    val isLoading: Boolean = true
)

class HomeViewModel(
    private val notebookRepository: NotebookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            notebookRepository.allNotebooks.collect { notebooks ->
                _uiState.value = HomeUiState(
                    notebooks = notebooks,
                    isLoading = false
                )
            }
        }
    }

    fun createNotebook(name: String, description: String = "") {
        viewModelScope.launch {
            notebookRepository.createNotebook(name, description)
        }
    }

    fun deleteNotebook(id: Long) {
        viewModelScope.launch {
            notebookRepository.deleteNotebook(id)
        }
    }

    class Factory(private val notebookRepository: NotebookRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(notebookRepository) as T
        }
    }
}