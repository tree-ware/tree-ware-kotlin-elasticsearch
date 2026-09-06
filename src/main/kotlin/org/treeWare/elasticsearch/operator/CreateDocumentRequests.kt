package org.treeWare.elasticsearch.operator

import org.treeWare.elasticsearch.document.DocumentOperation
import org.treeWare.elasticsearch.index.ENTITY_PATH_FIELD_NAME
import org.treeWare.elasticsearch.operator.delegate.ElasticsearchSetDelegate
import org.treeWare.model.core.*
import org.treeWare.model.operator.Response

/**
 * Creates per-entity-instance document operations from a set-model, one per
 * entity in traversal order. This is the Elasticsearch analog of MySQL DML
 * generation: pure request generation with no client interaction.
 *
 * - `CREATE` entities produce [org.treeWare.elasticsearch.document.DocumentOperation.Create] with a source map
 * containing [ENTITY_PATH_FIELD_NAME], key fields and all other single-valued
 * fields of the entity. Composition fields are skipped: each entity instance
 * is stored as its own document in its entity's dedicated index.
 * - `UPDATE` entities produce [org.treeWare.elasticsearch.document.DocumentOperation.Update] with a partial source
 * map containing only the fields being updated, so the existing document is
 * merged rather than replaced.
 * - `DELETE` entities produce [org.treeWare.elasticsearch.document.DocumentOperation.Delete].
 * - Fields with null values are omitted from the source map.
 *
 * @param model The set-model (with `set` aux values) to be stored in Elasticsearch.
 * @return The document operations in model-traversal order.
 * @throws IllegalStateException if the set traversal reports errors.
 */
fun createDocumentRequests(model: EntityModel): List<DocumentOperation> {
    val setDelegate = ElasticsearchSetDelegate()
    when (val response = org.treeWare.model.operator.set(model, setDelegate, null)) {
        Response.Success -> Unit
        is Response.ErrorList -> throw IllegalStateException(
            "Unable to generate document requests: " +
                response.errorList.joinToString("; ") { it.toString() }
        )
        is Response.ErrorModel -> throw IllegalStateException("Unable to generate document requests")
        is Response.Model -> throw IllegalStateException("Unable to generate document requests")
    }
    return setDelegate.operations
}
