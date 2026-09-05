package org.treeWare.elasticsearch.testutil

import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse
import co.elastic.clients.json.JsonpMapper
import co.elastic.clients.json.SimpleJsonpMapper
import co.elastic.clients.transport.ElasticsearchTransport
import co.elastic.clients.transport.Endpoint
import co.elastic.clients.transport.TransportOptions
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.function.Function

/**
 * In-memory fake of [co.elastic.clients.transport.ElasticsearchTransport] for unit tests.
 *
 * Records every request passed to [performRequest] in [requests] and answers
 * create-index requests with an acknowledged [co.elastic.clients.elasticsearch.indices.CreateIndexResponse]. Requests of
 * any other type get a best-effort answer: only create-index responses are
 * supported, anything else throws [UnsupportedOperationException].
 *
 * Build a client over it with `ElasticsearchClient(fakeTransport)`.
 *
 * @param failure When non-null, [performRequest] throws it instead of recording
 * and answering. Used to test error propagation.
 */
class FakeElasticsearchTransport(
    var failure: IOException? = null
) : ElasticsearchTransport {
    val requests = mutableListOf<Any?>()

    override fun <RequestT, ResponseT, ErrorT> performRequest(
        request: RequestT,
        endpoint: Endpoint<RequestT, ResponseT, ErrorT>,
        options: TransportOptions?
    ): ResponseT {
        failure?.let { throw it }
        requests.add(request)
        if (request is CreateIndexRequest) {
            @Suppress("UNCHECKED_CAST")
            return CreateIndexResponse.of {
                it.index(request.index()).acknowledged(true).shardsAcknowledged(true)
            } as ResponseT
        }
        throw UnsupportedOperationException("FakeElasticsearchTransport supports only CreateIndexRequest, got: $request")
    }

    override fun <RequestT, ResponseT, ErrorT> performRequestAsync(
        request: RequestT,
        endpoint: Endpoint<RequestT, ResponseT, ErrorT>,
        options: TransportOptions?
    ): CompletableFuture<ResponseT> {
        return try {
            CompletableFuture.completedFuture(performRequest(request, endpoint, options))
        } catch (e: Exception) {
            CompletableFuture.failedFuture(e)
        }
    }

    override fun jsonpMapper(): JsonpMapper = SimpleJsonpMapper()

    override fun options(): TransportOptions = object : TransportOptions {
        override fun headers(): Collection<Map.Entry<String, String>> = emptyList()
        override fun queryParameters(): Map<String, String> = emptyMap()
        override fun onWarnings(): Function<List<String>, Boolean>? = null
        override fun updateToken(token: String?) {}
        override fun keepResponseBodyOnException(): Boolean = false
        override fun toBuilder(): TransportOptions.Builder = throw UnsupportedOperationException()
    }

    override fun close() {}
}