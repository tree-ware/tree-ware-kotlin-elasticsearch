package org.treeWare.elasticsearch.operator

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.BulkResponse
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation
import co.elastic.clients.json.JsonData
import com.fasterxml.jackson.databind.ObjectMapper
import org.lighthousegames.logging.logging
import org.treeWare.elasticsearch.document.DocumentOperation
import org.treeWare.elasticsearch.document.DocumentOperation.Create
import org.treeWare.elasticsearch.document.DocumentOperation.Delete
import org.treeWare.elasticsearch.document.DocumentOperation.Update
import org.treeWare.model.core.EntityModel
import org.treeWare.model.operator.ElementModelError
import org.treeWare.model.operator.ErrorCode
import org.treeWare.model.operator.Response

private val logger = logging()
private val sourceMapper = ObjectMapper()

/**
 * Writes a set-model to Elasticsearch with a single `_bulk` request,
 * mirroring MySQL `set` (which issues all set-commands over one connection).
 *
 * Document operations come from [createDocumentRequests]: one per entity
 * instance, with the document `_id` set to the entity path. Deletes are issued
 * before creates and updates (the Elasticsearch analog of
 * `MySqlSetDelegate` command ordering); creates and updates keep
 * model-traversal order.
 *
 * - A model with no set-aux values produces no operations: returns
 * [Response.Success] without issuing any request.
 * - Per-item failures are reported as [Response.ErrorList] with
 * [ErrorCode.CLIENT_ERROR] (the Elasticsearch analog of `issueCommands`
 * error mapping). There is no commit/rollback analog: successful items stay
 * indexed even when other items fail.
 * - Transport-level failures are logged and rethrown (same as [createIndices]).
 *
 * @param model The set-model (with `set` aux values) to be stored in Elasticsearch.
 * @param client Elasticsearch client used to issue the bulk request.
 * @param logRequests When true, each bulk operation is logged before it is issued.
 * @throws IllegalStateException if document-request generation fails.
 */
fun set(
    model: EntityModel,
    client: ElasticsearchClient,
    logRequests: Boolean = false
): Response {
    val operations = createDocumentRequests(model)
    if (operations.isEmpty()) return Response.Success
    // Deletes first so that stale documents cannot shadow recreated ones;
    // creates and updates keep traversal order after them.
    val ordered = operations.filterIsInstance<Delete>() + operations.filter { it !is Delete }
    val bulkRequest = toBulkRequest(ordered)
    if (logRequests) ordered.forEach { logger.info { "Bulk operation: ${describeOperation(it)}" } }
    try {
        val bulkResponse = client.bulk(bulkRequest)
        return toSetResponse(ordered, bulkResponse)
    } catch (e: Exception) {
        logger.error { "Exception for bulk request with ${ordered.size} operations" }
        throw e
    }
}

/**
 * Builds a `_bulk` request from document operations, preserving their order.
 * Pure request generation with no client interaction (unit-testable).
 */
internal fun toBulkRequest(operations: List<DocumentOperation>): BulkRequest =
    BulkRequest.of { builder ->
        builder.operations(operations.map { toBulkOperation(it) })
        builder
    }

/** Maps a single document operation to its `_bulk` operation (`_id` is always the entity path). */
internal fun toBulkOperation(operation: DocumentOperation): BulkOperation = when (operation) {
    // `index` (not `create`) so that rewrites are idempotent: it creates or fully replaces the document.
    is Create -> BulkOperation.of { builder ->
        builder.index { index ->
            index.index(operation.index).id(operation.entityPath).document(toJsonData(operation.source))
        }
    }
    // Partial source is merged into the existing document, never replacing it.
    is Update -> BulkOperation.of { builder ->
        builder.update(
            UpdateOperation.of<JsonData, JsonData> { update ->
                update.index(operation.index).id(operation.entityPath)
                    .action { action -> action.doc(toJsonData(operation.partialSource)) }
            }
        )
    }
    is Delete -> BulkOperation.of { builder ->
        builder.delete { delete -> delete.index(operation.index).id(operation.entityPath) }
    }
}

private fun toSetResponse(operations: List<DocumentOperation>, bulkResponse: BulkResponse): Response {
    if (!bulkResponse.errors()) return Response.Success
    val errors = bulkResponse.items().mapIndexedNotNull { index, item ->
        val cause = item.error()
        // A failed item always carries an error cause; the status check is a backstop.
        if (cause == null && item.status() < 400) return@mapIndexedNotNull null
        val reason = cause?.reason()?.takeIf { it.isNotBlank() } ?: "status ${item.status()}"
        // `_id` is the entity path, so item ids already identify the failed instances.
        val path = item.id() ?: operations.getOrNull(index)?.entityPath.orEmpty()
        val action = item.operationType()?.jsonValue() ?: "bulk"
        ElementModelError(path, "unable to $action: $reason")
    }
    return if (errors.isEmpty()) Response.Success
    else Response.ErrorList(ErrorCode.CLIENT_ERROR, errors)
}

/** Serializes a document source map to client-native JSON (mapper-independent at issue time). */
private fun toJsonData(source: Map<String, Any?>): JsonData =
    JsonData.fromJson(sourceMapper.writeValueAsString(source))

private fun describeOperation(operation: DocumentOperation): String = when (operation) {
    is Create -> "index index=${operation.index} id=${operation.entityPath}"
    is Update -> "update index=${operation.index} id=${operation.entityPath}"
    is Delete -> "delete index=${operation.index} id=${operation.entityPath}"
}
