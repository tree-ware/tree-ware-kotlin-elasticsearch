package org.treeWare.elasticsearch.operator

import org.treeWare.elasticsearch.index.ENTITY_PATH_FIELD_NAME
import org.treeWare.elasticsearch.operator.delegate.ElasticsearchSetDelegate
import org.treeWare.model.core.*
import org.treeWare.model.operator.Response

/**
 * A write operation for a single entity instance.
 *
 * `_id` of the Elasticsearch document is the [entityPath]; [ENTITY_PATH_FIELD_NAME] is also
 * stored in the document `_source` (for [Index]) so that `get` can group hits
 * by path.
 *
 * Note: the path stored here is the *entity* path (with key values), not the
 * key-less field path MySQL stores in its `field_path_` column. The entity path
 * uniquely identifies an instance (e.g. `/persons/<id>`), which `_id`
 * requires; MySQL identifies rows by field-path plus key columns instead.
 */
sealed interface DocumentOperation {
    val index: String
    val entityPath: String

    /** Creates or fully replaces the document for an entity instance. */
    data class Index(
        override val index: String,
        override val entityPath: String,
        val source: Map<String, Any?>
    ) : DocumentOperation

    /** Deletes the document for an entity instance. */
    data class Delete(
        override val index: String,
        override val entityPath: String
    ) : DocumentOperation
}

/**
 * Creates per-entity-instance document operations from a set-model, one per
 * entity in traversal order. This is the Elasticsearch analog of MySQL DML
 * generation: pure request generation with no client interaction.
 *
 * - `CREATE` and `UPDATE` entities produce [DocumentOperation.Index] with a
 * source map containing [ENTITY_PATH_FIELD_NAME], key fields and all other single-valued
 * fields of the entity. Composition fields are skipped: each entity instance
 * is stored as its own document in its entity's dedicated index.
 * - `DELETE` entities produce [DocumentOperation.Delete].
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
