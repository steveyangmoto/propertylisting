package com.example.propertylisting.ui

import com.example.propertylisting.data.Listing
import com.example.propertylisting.data.ListingsService
import com.example.propertylisting.data.SearchException
import com.example.propertylisting.data.SearchQuery
import com.example.propertylisting.data.SearchResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private class FakeService : ListingsService {
        val requests = mutableListOf<SearchQuery>()
        var next: CompletableDeferred<SearchResponse> = CompletableDeferred()

        override suspend fun search(query: SearchQuery): SearchResponse {
            requests += query
            return next.await()
        }
    }

    private val service = FakeService()
    private lateinit var viewModel: SearchViewModel

    private fun listing(id: String) =
        Listing(id = id, address = "$id St", city = "Reston", price = 1.0, bedrooms = 1, bathrooms = 1.0)

    private fun page(page: Int, totalPages: Int, vararg ids: String) =
        SearchResponse(ids.map(::listing), page, 10, totalResults = totalPages * 10, totalPages = totalPages)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SearchViewModel(service, serverDescription = "http://test")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invalid form shows field errors without calling the server`() = runTest {
        viewModel.updateForm { it.copy(minPrice = "9", maxPrice = "1") }
        viewModel.search()

        assertTrue(FormField.MaxPrice in viewModel.state.value.formErrors)
        assertEquals(ResultsState.Idle, viewModel.state.value.results)
        assertTrue(service.requests.isEmpty())
    }

    @Test
    fun `search moves through loading to loaded`() = runTest {
        viewModel.updateForm { it.copy(city = "Reston") }
        viewModel.search()
        assertEquals(ResultsState.Loading, viewModel.state.value.results)
        assertEquals("Reston", service.requests.single().city)

        service.next.complete(page(1, 1, "A1", "A2"))

        val loaded = viewModel.state.value.results as ResultsState.Loaded
        assertEquals(listOf("A1", "A2"), loaded.listings.map { it.id })
        assertEquals(false, loaded.canLoadMore)
    }

    @Test
    fun `failure shows an error that can be retried`() = runTest {
        viewModel.search()
        service.next.completeExceptionally(SearchException.Network(IOException("refused")))
        assertTrue(viewModel.state.value.results is ResultsState.Error)

        service.next = CompletableDeferred()
        viewModel.retry()
        assertEquals(ResultsState.Loading, viewModel.state.value.results)
        assertEquals(2, service.requests.size)
    }

    @Test
    fun `loading the next page appends results`() = runTest {
        viewModel.search()
        service.next.complete(page(1, 2, "A1", "A2"))

        service.next = CompletableDeferred()
        viewModel.loadNextPage()
        assertTrue((viewModel.state.value.results as ResultsState.Loaded).isLoadingMore)
        assertEquals(2, service.requests.last().page)

        service.next.complete(page(2, 2, "A2", "A3"))
        val loaded = viewModel.state.value.results as ResultsState.Loaded
        assertEquals(listOf("A1", "A2", "A3"), loaded.listings.map { it.id })
        assertEquals(false, loaded.canLoadMore)
    }

    @Test
    fun `a failed next page keeps existing results`() = runTest {
        viewModel.search()
        service.next.complete(page(1, 2, "A1"))

        service.next = CompletableDeferred()
        viewModel.loadNextPage()
        service.next.completeExceptionally(SearchException.Http(500))

        val loaded = viewModel.state.value.results as ResultsState.Loaded
        assertEquals(listOf("A1"), loaded.listings.map { it.id })
        assertTrue(loaded.loadMoreError != null)
        assertEquals(1, loaded.query.page)
    }
}
