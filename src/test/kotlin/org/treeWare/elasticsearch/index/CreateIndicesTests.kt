package org.treeWare.elasticsearch.index

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import org.treeWare.elasticsearch.FakeElasticsearchTransport
import org.treeWare.metaModel.addressBookMetaModel
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CreateIndicesTests {
    @Test
    fun `createIndices issues one create call per entity`() {
        val transport = FakeElasticsearchTransport()
        val client = ElasticsearchClient(transport)

        createIndices(client, addressBookMetaModel)

        val expectedIndexNames = createIndexRequests(addressBookMetaModel).map { it.index() }
        val actualRequests = transport.requests.filterIsInstance<CreateIndexRequest>()
        // One recorded request per issued create call.
        assertEquals(expectedIndexNames.size, transport.requests.size)
        assertEquals(expectedIndexNames.sorted(), actualRequests.map { it.index() }.sorted())
    }

    @Test
    fun `createIndices issues full requests including field_path_ mapping`() {
        val transport = FakeElasticsearchTransport()
        val client = ElasticsearchClient(transport)

        createIndices(client, addressBookMetaModel, logRequests = true)

        val actualRequests = transport.requests.filterIsInstance<CreateIndexRequest>()
        assertTrue(actualRequests.isNotEmpty())
        actualRequests.forEach { request ->
            val properties = request.mappings()?.properties() ?: emptyMap()
            assertTrue(properties.containsKey(FIELD_PATH), "Missing $FIELD_PATH in index: ${request.index()}")
            assertTrue(
                properties[FIELD_PATH]?.isKeyword == true,
                "Field $FIELD_PATH must be a keyword in index: ${request.index()}"
            )
        }
    }

    @Test
    fun `createIndices propagates transport failures`() {
        val transport = FakeElasticsearchTransport(failure = IOException("connection refused"))
        val client = ElasticsearchClient(transport)

        assertFailsWith<IOException> {
            createIndices(client, addressBookMetaModel)
        }
        assertEquals(emptyList(), transport.requests)
    }
}
