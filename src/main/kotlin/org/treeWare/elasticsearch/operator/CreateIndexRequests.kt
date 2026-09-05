package org.treeWare.elasticsearch.operator

import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import org.treeWare.elasticsearch.index.CreateIndexRequestsVisitor
import org.treeWare.metaModel.traversal.metaModelForEach
import org.treeWare.model.core.EntityModel

/**
 * Generates a list of CreateIndexRequest instances for all entities in the meta-model.
 * The index name for each entity is a concatenation of the package name and entity name with "__" as separator.
 * Each non-composition field in the entity is added as a property in the index mappings with a type derived
 * from the meta-model field type. Composition fields are skipped: every entity instance is stored as its own
 * document in its entity's dedicated index, so there is no need for nested composition mappings.
 * Every mapping also contains a `entity_path_` keyword field identifying the entity instance's tree-ware path.
 *
 * String fields are mapped to `text` (with a `keyword` multi-field for exact matches) so that any text in any
 * field can be found with full-text search. All other field types keep exact-match mappings (`keyword`,
 * numbers, `boolean`, `date`, `binary`).
 *
 * @param metaModel The meta-model to process.
 * @return A list of CreateIndexRequest instances, one for each entity in the meta-model.
 */
fun createIndexRequests(metaModel: EntityModel): List<CreateIndexRequest> {
    val visitor = CreateIndexRequestsVisitor()
    metaModelForEach(metaModel, visitor)
    return visitor.indexRequests
}
