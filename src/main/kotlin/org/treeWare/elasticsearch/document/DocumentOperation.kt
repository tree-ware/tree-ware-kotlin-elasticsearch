package org.treeWare.elasticsearch.document

/**
 * A write operation for a single entity instance.
 *
 * `_id` of the Elasticsearch document is the [entityPath]; [org.treeWare.elasticsearch.index.ENTITY_PATH_FIELD_NAME] is also
 * stored in the document `_source` (for [Create]) so that `get` can group hits
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
    data class Create(
        override val index: String,
        override val entityPath: String,
        val source: Map<String, Any?>
    ) : DocumentOperation

    /**
     * Merges a partial document into the existing document for an entity
     * instance (Elasticsearch Update API). Only the fields being updated are
     * present in [partialSource]; all other stored fields are left untouched.
     * Unlike [Create], this never replaces the existing document.
     */
    data class Update(
        override val index: String,
        override val entityPath: String,
        val partialSource: Map<String, Any?>
    ) : DocumentOperation

    /** Deletes the document for an entity instance. */
    data class Delete(
        override val index: String,
        override val entityPath: String
    ) : DocumentOperation
}