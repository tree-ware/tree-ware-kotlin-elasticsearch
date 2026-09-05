package org.treeWare.elasticsearch.index

import co.elastic.clients.elasticsearch._types.mapping.Property
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import org.treeWare.metaModel.FieldType
import org.treeWare.metaModel.getFieldTypeMeta
import org.treeWare.metaModel.getMetaName
import org.treeWare.metaModel.traversal.AbstractLeader1MetaModelVisitor
import org.treeWare.model.core.EntityModel
import org.treeWare.model.traversal.TraversalAction

/**
 * Name of the document field that stores the tree-ware path of the entity instance.
 * Every index mapping contains this field as a `keyword` so that documents can be
 * addressed by path (`_id` = `entity_path_`) and grouped by path when reading.
 */
const val ENTITY_PATH_FIELD_NAME = "entity_path_"

class CreateIndexRequestsVisitor : AbstractLeader1MetaModelVisitor<TraversalAction>(TraversalAction.CONTINUE) {
    val indexRequests = mutableListOf<CreateIndexRequest>()
    private var currentEntityName: String? = null
    private var currentTypeMappingBuilder: TypeMapping.Builder? = null

    override fun visitEntityMeta(leaderEntityMeta1: EntityModel): TraversalAction {
        currentEntityName = getMetaName(leaderEntityMeta1)
        currentTypeMappingBuilder = TypeMapping.Builder()
        // Path of the entity instance; also used as the document `_id` (see work plan).
        currentTypeMappingBuilder?.properties(ENTITY_PATH_FIELD_NAME) { p: Property.Builder -> p.keyword { it } }
        return TraversalAction.CONTINUE
    }

    override fun visitFieldMeta(leaderFieldMeta1: EntityModel): TraversalAction {
        val builder = currentTypeMappingBuilder ?: return TraversalAction.CONTINUE
        val fieldName = getMetaName(leaderFieldMeta1)
        val fieldType = getFieldTypeMeta(leaderFieldMeta1) ?: return TraversalAction.CONTINUE
        // Compositions are skipped: each entity instance is stored as its own
        // document in its entity's dedicated index.
        if (fieldType == FieldType.COMPOSITION) return TraversalAction.CONTINUE

        builder.properties(fieldName) { p: Property.Builder ->
            when (fieldType) {
                FieldType.BOOLEAN -> p.boolean_ { it }
                FieldType.UINT8 -> p.short_ { it }
                FieldType.UINT16 -> p.integer { it }
                FieldType.UINT32 -> p.long_ { it }
                FieldType.UINT64 -> p.unsignedLong { it }
                FieldType.INT8 -> p.byte_ { it }
                FieldType.INT16 -> p.short_ { it }
                FieldType.INT32 -> p.integer { it }
                FieldType.INT64 -> p.long_ { it }
                FieldType.FLOAT -> p.float_ { it }
                FieldType.DOUBLE -> p.double_ { it }
                FieldType.BIG_INTEGER -> p.keyword { it }
                FieldType.BIG_DECIMAL -> p.keyword { it }
                FieldType.TIMESTAMP -> p.date { it.format("epoch_millis") }
                // Full-text searchable, with a keyword multi-field for exact matches.
                FieldType.STRING -> p.text { t -> t.fields("keyword") { f -> f.keyword { it } } }
                FieldType.UUID -> p.keyword { it }
                FieldType.BLOB -> p.binary { it }
                FieldType.PASSWORD1WAY -> p.keyword { it }
                FieldType.PASSWORD2WAY -> p.keyword { it }
                FieldType.ALIAS -> p.keyword { it }
                FieldType.ENUMERATION -> p.keyword { it }
                FieldType.ASSOCIATION -> p.keyword { it }
                // Unreachable: compositions are skipped before mapping (see above).
                FieldType.COMPOSITION -> error("Composition fields must be skipped, not mapped")
            }
        }
        return TraversalAction.CONTINUE
    }

    override fun leaveEntityMeta(leaderEntityMeta1: EntityModel) {
        if (currentEntityName == null) return
        val typeMappingBuilder = currentTypeMappingBuilder ?: return
        val indexName = getIndexName(leaderEntityMeta1)
        val request = CreateIndexRequest.Builder()
            .index(indexName)
            .mappings(typeMappingBuilder.build())
            .build()
        indexRequests.add(request)
        // reset state
        currentEntityName = null
        currentTypeMappingBuilder = null
    }
}