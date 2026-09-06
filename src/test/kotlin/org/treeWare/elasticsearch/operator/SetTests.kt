package org.treeWare.elasticsearch.operator

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.BulkResponse
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem
import co.elastic.clients.elasticsearch.core.bulk.OperationType
import org.treeWare.elasticsearch.index.ENTITY_PATH_FIELD_NAME
import org.treeWare.elasticsearch.testutil.FakeElasticsearchTransport
import org.treeWare.elasticsearch.testutil.indexDocument
import org.treeWare.elasticsearch.testutil.operationId
import org.treeWare.elasticsearch.testutil.operationIndex
import org.treeWare.elasticsearch.testutil.operationType
import org.treeWare.elasticsearch.testutil.toSuccessItem
import org.treeWare.elasticsearch.testutil.updateDocument
import org.treeWare.metaModel.addressBookRootEntityMeta
import org.treeWare.model.core.EntityModel
import org.treeWare.model.core.MutableEntityModel
import org.treeWare.model.decodeJsonFileIntoEntity
import org.treeWare.model.decoder.stateMachine.MultiAuxDecodingStateMachineFactory
import org.treeWare.model.operator.ErrorCode
import org.treeWare.model.operator.Response
import org.treeWare.model.operator.set.aux.SET_AUX_NAME
import org.treeWare.model.operator.set.aux.SetAuxStateMachine
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val PERSON_INDEX = "org.tree_ware.test.address_book.main__address_book_person"
private const val ROOT_INDEX = "org.tree_ware.test.address_book.main__address_book_root"
private const val CREATED_PERSON_PATH = "/persons/d5f9b1a2-0000-4000-8000-000000000001"
private const val DELETED_PERSON_PATH = "/persons/a8aacf55-7810-4b43-afe5-4344f25435fd"
private const val UPDATED_PERSON_PATH = "/persons/cc477201-48ec-4367-83a4-7fdbd92f8a6f"

class SetTests {
    private val auxDecodingFactory =
        MultiAuxDecodingStateMachineFactory(SET_AUX_NAME to { SetAuxStateMachine(it) })

    @Test
    fun `set issues one bulk request with deletes before creates and updates`() {
        val transport = FakeElasticsearchTransport()
        val client = ElasticsearchClient(transport)

        val response = set(decodeSetModel("es_set_mixed.json"), client)

        assertEquals(Response.Success, response)
        // One `_bulk` request per `set()` call.
        assertEquals(1, transport.requests.size)
        val bulkRequest = assertIs<BulkRequest>(transport.requests.single())
        val operations = bulkRequest.operations()
        assertEquals(4, operations.size)
        // Model-traversal order is update, create, delete, update; deletes go first.
        assertEquals(OperationType.Delete, operations[0].operationType())
        assertEquals(PERSON_INDEX, operations[0].operationIndex())
        assertEquals(DELETED_PERSON_PATH, operations[0].operationId())
        assertEquals(OperationType.Update, operations[1].operationType())
        assertEquals(ROOT_INDEX, operations[1].operationIndex())
        assertEquals("/", operations[1].operationId())
        assertEquals(OperationType.Index, operations[2].operationType())
        assertEquals(PERSON_INDEX, operations[2].operationIndex())
        assertEquals(CREATED_PERSON_PATH, operations[2].operationId())
        assertEquals(OperationType.Update, operations[3].operationType())
        assertEquals(PERSON_INDEX, operations[3].operationIndex())
        assertEquals(UPDATED_PERSON_PATH, operations[3].operationId())
    }

    @Test
    fun `set stores entity_path_ in created documents and partial sources in updates`() {
        val transport = FakeElasticsearchTransport()
        val client = ElasticsearchClient(transport)

        assertEquals(Response.Success, set(decodeSetModel("es_set_mixed.json"), client))

        val bulkRequest = assertIs<BulkRequest>(transport.requests.single())
        val create = bulkRequest.operations().first { it.operationType() == OperationType.Index }
        val createdSource = create.indexDocument().toJson().toString()
        assertTrue(
            createdSource.contains("\"$ENTITY_PATH_FIELD_NAME\":\"$CREATED_PERSON_PATH\""),
            "Missing $ENTITY_PATH_FIELD_NAME: $createdSource"
        )
        assertTrue(createdSource.contains("\"first_name\":\"Diana\""), "Missing fields: $createdSource")
        val update = bulkRequest.operations().first {
            it.operationType() == OperationType.Update && it.operationId() == UPDATED_PERSON_PATH
        }
        val partialSource = update.updateDocument().toJson().toString()
        assertTrue(partialSource.contains("\"first_name\":\"Clark Joseph\""), "Missing update: $partialSource")
        // Keys and the entity path address the document via `_id`; they are not part of the update.
        assertTrue(!partialSource.contains(ENTITY_PATH_FIELD_NAME), "Update must be partial: $partialSource")
    }

    @Test
    fun `set with model without set aux issues no requests`() {
        val transport = FakeElasticsearchTransport()
        val client = ElasticsearchClient(transport)
        val model = MutableEntityModel(addressBookRootEntityMeta, null)
        decodeJsonFileIntoEntity("model/address_book_1.json", entity = model)

        assertEquals(Response.Success, set(model, client))

        assertEquals(emptyList(), transport.requests)
    }

    @Test
    fun `set maps per-item failures to error list`() {
        val transport = FakeElasticsearchTransport()
        val client = ElasticsearchClient(transport)
        transport.bulkHandler = { request ->
            BulkResponse.of { builder ->
                builder.errors(true).took(1L)
                builder.items(request.operations().map { operation ->
                    if (operation.operationId() == UPDATED_PERSON_PATH) BulkResponseItem.of { item ->
                        item.operationType(OperationType.Update)
                            .index(operation.operationIndex())
                            .id(operation.operationId())
                            .status(400)
                            .error { error ->
                                error.type("document_missing_exception").reason("document missing")
                            }
                        item
                    } else operation.toSuccessItem()
                })
                builder
            }
        }

        val response = set(decodeSetModel("es_set_mixed.json"), client)

        val errorList = assertIs<Response.ErrorList>(response)
        assertEquals(ErrorCode.CLIENT_ERROR, errorList.errorCode)
        assertEquals(1, errorList.errorList.size)
        val error = errorList.errorList.single()
        assertEquals(UPDATED_PERSON_PATH, error.path)
        assertTrue(error.error.contains("update"), "Missing action: ${error.error}")
        assertTrue(error.error.contains("document missing"), "Missing reason: ${error.error}")
    }

    @Test
    fun `set treats bulk response without per-item errors as success`() {
        val transport = FakeElasticsearchTransport()
        val client = ElasticsearchClient(transport)
        // `errors=true` but no failed items: nothing to report.
        transport.bulkHandler = { request ->
            BulkResponse.of { builder ->
                builder.errors(true).took(1L)
                builder.items(request.operations().map { it.toSuccessItem() })
                builder
            }
        }

        assertEquals(Response.Success, set(decodeSetModel("es_set_mixed.json"), client))
    }

    @Test
    fun `set propagates transport failures`() {
        val transport = FakeElasticsearchTransport(failure = IOException("connection refused"))
        val client = ElasticsearchClient(transport)

        assertFailsWith<IOException> {
            set(decodeSetModel("es_set_mixed.json"), client)
        }
        assertEquals(emptyList(), transport.requests)
    }

    @Test
    fun `set logs bulk operations only when logRequests is true`() {
        val loggedPaths = captureOutput {
            val transport = FakeElasticsearchTransport()
            set(decodeSetModel("es_set_mixed.json"), ElasticsearchClient(transport), logRequests = true)
        }
        assertTrue(loggedPaths.contains(DELETED_PERSON_PATH), "Missing delete log: $loggedPaths")
        assertTrue(loggedPaths.contains(CREATED_PERSON_PATH), "Missing create log: $loggedPaths")
        assertTrue(loggedPaths.contains(UPDATED_PERSON_PATH), "Missing update log: $loggedPaths")

        val unloggedPaths = captureOutput {
            val transport = FakeElasticsearchTransport()
            set(decodeSetModel("es_set_mixed.json"), ElasticsearchClient(transport))
        }
        assertTrue(!unloggedPaths.contains("Bulk operation"), "Must not log by default: $unloggedPaths")
    }

    @Test
    fun `toBulkRequest preserves operation order`() {
        val operations = createDocumentRequests(decodeSetModel("es_set_delete.json"))
        assertTrue(operations.isNotEmpty())

        val bulkRequest = toBulkRequest(operations)

        val requestTypes = bulkRequest.operations().map { it.operationType() }
        assertEquals(List(operations.size) { OperationType.Delete }, requestTypes)
        val requestIds = bulkRequest.operations().map { it.operationId() }
        assertEquals(operations.map { it.entityPath }, requestIds)
    }

    private fun decodeSetModel(inputFile: String): EntityModel {
        val model = MutableEntityModel(addressBookRootEntityMeta, null)
        decodeJsonFileIntoEntity(
            "model/$inputFile",
            multiAuxDecodingStateMachineFactory = auxDecodingFactory,
            entity = model
        )
        return model
    }
}

/** Runs [block] with stdout and stderr captured, returning everything written. */
private fun captureOutput(block: () -> Unit): String {
    val originalOut = System.out
    val originalErr = System.err
    val buffer = ByteArrayOutputStream()
    val stream = PrintStream(buffer)
    System.setOut(stream)
    System.setErr(stream)
    try {
        block()
    } finally {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }
    return buffer.toString(Charsets.UTF_8)
}
