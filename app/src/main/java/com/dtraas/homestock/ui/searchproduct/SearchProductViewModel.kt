package com.dtraas.homestock.ui.searchproduct

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtraas.homestock.data.repository.ProductRepository
import com.dtraas.homestock.data.repository.ProductSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchProductUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<ProductSearchResult> = emptyList(),
    val hasError: Boolean = false,
)

class SearchProductViewModel(
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchProductUiState())
    val uiState: StateFlow<SearchProductUiState> = _uiState

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            productRepository.searchByName(query)
                .onSuccess { results ->
                    _uiState.update { it.copy(isLoading = false, hasSearched = true, results = results, hasError = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, hasSearched = true, results = emptyList(), hasError = true) }
                }
        }
    }
}
