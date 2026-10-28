package com.example.propertylisting.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ListingsService {
    /** Throws [SearchException] on any failure. */
    suspend fun search(query: SearchQuery): SearchResponse
}

sealed class SearchException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The server rejected the request (HTTP 400); [message] is the server's explanation. */
    class InvalidRequest(message: String) : SearchException(message)

    class Http(val code: Int) : SearchException("Server returned HTTP $code")

    class Network(cause: IOException) : SearchException(cause.message ?: "Network error", cause)

    class MalformedResponse(cause: Throwable) : SearchException("Unexpected response from server", cause)
}

class HttpListingsService(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson,
) : ListingsService {

    private val searchUrl = baseUrl.trimEnd('/').toHttpUrl().newBuilder()
        .addPathSegments("listings/search")
        .build()

    override suspend fun search(query: SearchQuery): SearchResponse = withContext(Dispatchers.IO) {
        val url = searchUrl.newBuilder().apply {
            query.toQueryParams().forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val request = Request.Builder().url(url).get().build()

        // Reading the body is blocking I/O, so it must stay on the IO dispatcher, not the caller's.
        client.newCall(request).await().use { response ->
            val body = try {
                response.body?.string().orEmpty()
            } catch (e: IOException) {
                throw SearchException.Network(e)
            }
            when {
                response.isSuccessful -> decode<SearchResponse>(body)
                response.code == 400 -> throw SearchException.InvalidRequest(
                    runCatching { json.decodeFromString<ErrorBody>(body).error }
                        .getOrDefault("The server rejected the search parameters."),
                )
                else -> throw SearchException.Http(response.code)
            }
        }
    }

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (e: SerializationException) {
        throw SearchException.MalformedResponse(e)
    } catch (e: IllegalArgumentException) {
        throw SearchException.MalformedResponse(e)
    }

    /** Enqueues the call and cancels the underlying HTTP request if the coroutine is cancelled. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, _, _ -> response.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(SearchException.Network(e))
            }
        })
    }

    companion object {
        val defaultJson = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
