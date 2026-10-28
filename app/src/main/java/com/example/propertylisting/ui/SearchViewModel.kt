package com.example.propertylisting.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.propertylisting.BuildConfig
import com.example.propertylisting.data.HttpListingsService
import com.example.propertylisting.data.Listing
import com.example.propertylisting.data.ListingsService
import com.example.propertylisting.data.SearchException
import com.example.propertylisting.data.SearchQuery
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val form: SearchForm = SearchForm(),
    val formErrors: Map<FormField, String> = emptyMap(),
    val results: ResultsState = ResultsState.Idle,
    val mergeDuplicates: Boolean = true,
)

sealed interface ResultsState {
    /** No search has been run yet. */
    data object Idle : ResultsState

    /** First page of a new search is in flight. */
    data object Loading : ResultsState

    /** The first page failed; nothing to show. */
    data class Error(val message: String) : ResultsState

    data class Loaded(
        val query: SearchQuery,
        val listings: List<Listing>,
        val totalResults: Int,
        val totalPages: Int,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : ResultsState {
        val canLoadMore: Boolean
            get() = query.page < totalPages
    }
}

class SearchViewModel(
    private val service: ListingsService,
    private val serverDescription: String,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    /** The last query submitted, so the error state can offer a retry. */
    private var lastQuery: SearchQuery? = null

    fun updateForm(transform: (SearchForm) -> SearchForm) {
        _state.update { it.copy(form = transform(it.form), formErrors = emptyMap()) }
    }

    fun setMergeDuplicates(enabled: Boolean) {
        _state.update { it.copy(mergeDuplicates = enabled) }
    }

    fun clearForm() {
        _state.update { it.copy(form = SearchForm(), formErrors = emptyMap()) }
    }

    fun search() {
        when (val validation = _state.value.form.validate()) {
            is FormValidation.Invalid -> _state.update { it.copy(formErrors = validation.errors) }
            is FormValidation.Valid -> runSearch(validation.query)
        }
    }

    fun retry() {
        lastQuery?.let(::runSearch)
    }

    fun loadNextPage() {
        val current = _state.value.results as? ResultsState.Loaded ?: return
        if (!current.canLoadMore || current.isLoadingMore || searchJob?.isActive == true) return

        val nextQuery = current.query.copy(page = current.query.page + 1)
        setResults(current.copy(isLoadingMore = true, loadMoreError = null))

        searchJob = viewModelScope.launch {
            try {
                val response = service.search(nextQuery)
                setResults(
                    current.copy(
                        query = nextQuery,
                        // Guards against the same listing appearing twice if the data shifts between pages.
                        listings = (current.listings + response.results).distinctBy { it.id },
                        totalResults = response.totalResults,
                        totalPages = response.totalPages,
                        isLoadingMore = false,
                    ),
                )
            } catch (e: SearchException) {
                setResults(current.copy(isLoadingMore = false, loadMoreError = e.toUserMessage()))
            }
        }
    }

    private fun runSearch(query: SearchQuery) {
        searchJob?.cancel()
        lastQuery = query
        setResults(ResultsState.Loading)

        searchJob = viewModelScope.launch {
            try {
                val response = service.search(query)
                setResults(
                    ResultsState.Loaded(
                        query = query,
                        listings = response.results,
                        totalResults = response.totalResults,
                        totalPages = response.totalPages,
                    ),
                )
            } catch (e: SearchException) {
                setResults(ResultsState.Error(e.toUserMessage()))
            }
        }
    }

    private fun setResults(results: ResultsState) {
        _state.update { it.copy(results = results) }
    }

    private fun SearchException.toUserMessage(): String = when (this) {
        is SearchException.InvalidRequest -> message ?: "Invalid search."
        is SearchException.Network ->
            "Couldn't reach the server at $serverDescription. Make sure it's running and reachable from this device."
        is SearchException.Http -> "The server had a problem (HTTP $code). Please try again."
        is SearchException.MalformedResponse -> "The server sent a response the app couldn't read."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    service = HttpListingsService(BuildConfig.API_BASE_URL),
                    serverDescription = BuildConfig.API_BASE_URL,
                )
            }
        }
    }
}
