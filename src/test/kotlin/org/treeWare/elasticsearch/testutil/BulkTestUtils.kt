package org.treeWare.elasticsearch.testutil

import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.BulkResponse
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.BulkOperationBase
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation
import co.elastic.clients.elasticsearch.core.bulk.OperationType
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation
import co.elastic.clients.json.JsonData

/** The bulk operation type of a [BulkOperation] (index, create, update or delete). */
internal fun BulkOperation.operationType(): OperationType = when (_kind()) {
    BulkOperation.Kind.Index -> OperationType.Index
    BulkOperation.Kind.Create -> OperationType.Create
    BulkOperation.Kind.Update -> OperationType.Update
    BulkOperation.Kind.Delete -> OperationType.Delete
}

/** The target index of a [BulkOperation]. */
internal fun BulkOperation.operationIndex(): String =
    (_get() as BulkOperationBase).index() ?: error("Bulk operation without index")

/** The document `_id` (the tree-ware entity path) of a [BulkOperation]. */
internal fun BulkOperation.operationId(): String =
    (_get() as BulkOperationBase).id() ?: error("Bulk operation without id")

/** The full document of an `index` [BulkOperation]. */
internal fun BulkOperation.indexDocument(): JsonData {
    require(_kind() == BulkOperation.Kind.Index) { "Not an index operation: ${_kind()}" }
    return (_get() as IndexOperation<*>).document() as? JsonData ?: error("Index operation without document")
}

/** The partial document of an `update` [BulkOperation]. */
internal fun BulkOperation.updateDocument(): JsonData {
    require(_kind() == BulkOperation.Kind.Update) { "Not an update operation: ${_kind()}" }
    val action = (_get() as UpdateOperation<*, *>).action() ?: error("Update operation without action")
    return action.doc() as? JsonData ?: error("Update operation without partial document")
}

/** A success bulk response: one `200` item per operation, mirroring its type, index and id. */
internal fun bulkSuccessResponse(request: BulkRequest): BulkResponse = BulkResponse.of { builder ->
    builder.errors(false).took(1L)
    builder.items(request.operations().map { it.toSuccessItem() })
    builder
}

internal fun BulkOperation.toSuccessItem(): BulkResponseItem = BulkResponseItem.of { builder ->
    builder.operationType(operationType()).index(operationIndex()).id(operationId()).status(200)
    builder
}
