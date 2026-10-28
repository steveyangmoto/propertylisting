package com.example.propertylisting.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HttpListingsServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: HttpListingsService

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        service = HttpListingsService(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses a successful response and sends only set parameters`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results": [{"id": "A5", "source": "MLS_A", "address": "55 Elm Ct", "city": "Vienna",
                  "state": "VA", "zip": "22180", "price": 470000, "bedrooms": 3, "bathrooms": 2.0,
                  "sqft": 1300, "status": "active", "amenities": {"petFriendly": false, "parking": false},
                  "duplicateGroupId": null, "nearDuplicate": {"ofId": "B11", "confidence": 0.86},
                  "score": 0.38, "someNewField": 1}],
                 "page": 1, "pageSize": 10, "totalResults": 1, "totalPages": 1}
                """.trimIndent(),
            ),
        )

        val response = service.search(SearchQuery(minPrice = 400_000, city = "Vienna"))

        val request = server.takeRequest()
        assertEquals("/listings/search", request.requestUrl!!.encodedPath)
        assertEquals("400000", request.requestUrl!!.queryParameter("minPrice"))
        assertEquals("Vienna", request.requestUrl!!.queryParameter("city"))
        assertNull(request.requestUrl!!.queryParameter("maxPrice"))

        assertEquals(1, response.totalResults)
        val listing = response.results.single()
        assertEquals("55 Elm Ct", listing.address)
        assertEquals(470_000.0, listing.price, 0.0)
        assertEquals("B11", listing.nearDuplicate?.ofId)
    }

    @Test
    fun `empty results are a normal response`() = runTest {
        server.enqueue(MockResponse().setBody("""{"results": [], "page": 1, "pageSize": 10, "totalResults": 0, "totalPages": 0}"""))
        assertTrue(service.search(SearchQuery()).results.isEmpty())
    }

    @Test
    fun `400 surfaces the server's error message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error": "minPrice must be <= maxPrice"}"""))
        try {
            service.search(SearchQuery())
            fail("Expected InvalidRequest")
        } catch (e: SearchException.InvalidRequest) {
            assertEquals("minPrice must be <= maxPrice", e.message)
        }
    }

    @Test
    fun `5xx maps to Http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            service.search(SearchQuery())
            fail("Expected Http")
        } catch (e: SearchException.Http) {
            assertEquals(503, e.code)
        }
    }

    @Test
    fun `garbage body maps to MalformedResponse`() = runTest {
        server.enqueue(MockResponse().setBody("<html>not json</html>"))
        try {
            service.search(SearchQuery())
            fail("Expected MalformedResponse")
        } catch (e: SearchException.MalformedResponse) {
            // expected
        }
    }

    @Test
    fun `unreachable server maps to Network error`() = runTest {
        server.shutdown()
        try {
            service.search(SearchQuery())
            fail("Expected Network")
        } catch (e: SearchException.Network) {
            // expected
        }
    }
}
