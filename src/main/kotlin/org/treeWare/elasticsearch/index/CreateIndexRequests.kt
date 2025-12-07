package org.treeWare.elasticsearch.index

import co.elastic.clients.elasticsearch._types.mapping.Property
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import org.treeWare.metaModel.FieldType
import org.treeWare.metaModel.getFieldTypeMeta
import org.treeWare.metaModel.getFieldsMeta
import org.treeWare.metaModel.getMetaName
import org.treeWare.metaModel.getMultiplicityMeta
import org.treeWare.metaModel.traversal.AbstractLeader1MetaModelVisitor
import org.treeWare.metaModel.traversal.metaModelForEach
import org.treeWare.model.core.ElementModel
import org.treeWare.model.core.EntityModel
import org.treeWare.model.core.FieldModel
import org.treeWare.model.core.PrimitiveModel
import org.treeWare.model.core.SetFieldModel
import org.treeWare.model.traversal.TraversalAction

/**
 * Generates a list of CreateIndexRequest instances for all entities in the meta-model.
 * The index name for each entity is a concatenation of the package name and entity name with "__" as separator.
 * Each field in the entity is added as a property in the index mappings with a type derived from the
 * meta-model field type.
 *
 * @param metaModel The meta-model to process.
 * @return A list of CreateIndexRequest instances, one for each entity in the meta-model.
 */
fun createIndexRequests(metaModel: EntityModel): List<CreateIndexRequest> {
    val visitor = CreateIndexRequestsVisitor()
    metaModelForEach(metaModel, visitor)
    return visitor.indexRequests
}

private class CreateIndexRequestsVisitor : AbstractLeader1MetaModelVisitor<TraversalAction>(TraversalAction.CONTINUE) {
    val indexRequests = mutableListOf<CreateIndexRequest>()
    private var currentPackageName = ""

    override fun visitPackageMeta(leaderPackageMeta1: EntityModel): TraversalAction {
        currentPackageName = getMetaName(leaderPackageMeta1)
        return TraversalAction.CONTINUE
    }

    override fun visitEntityMeta(leaderEntityMeta1: EntityModel): TraversalAction {
        val entityName = getMetaName(leaderEntityMeta1)
        val indexName = "${currentPackageName}__${entityName}"

        // Build mappings: properties for each field in the entity
        val typeMappingBuilder = TypeMapping.Builder()
        val fields = getFieldsMeta(leaderEntityMeta1).values
        fields.forEach { fieldElement: ElementModel ->
            val fieldMeta = fieldElement as? EntityModel ?: return@forEach
            val fieldName = getMetaName(fieldMeta)
            val fieldType = getFieldTypeMeta(fieldMeta)
            if (fieldType == null) return@forEach
            val multiplicity = getMultiplicityMeta(fieldMeta)

            typeMappingBuilder.properties(fieldName) { p: Property.Builder ->
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
                    FieldType.STRING -> p.keyword { it }
                    FieldType.UUID -> p.keyword { it }
                    FieldType.BLOB -> p.binary { it }
                    FieldType.PASSWORD1WAY -> p.keyword { it }
                    FieldType.PASSWORD2WAY -> p.keyword { it }
                    FieldType.ALIAS -> p.keyword { it }
                    FieldType.ENUMERATION -> p.integer { it }
                    FieldType.ASSOCIATION -> p.keyword { it }
                    FieldType.COMPOSITION -> {
                        // Map compositions to nested for simplicity (both set and single)
                        // This allows querying inner fields if needed.
                        p.nested { it }
                    }
                }
            }
        }

        val request = CreateIndexRequest.Builder()
            .index(indexName)
            .mappings(typeMappingBuilder.build())
            .build()

        indexRequests.add(request)
        return TraversalAction.CONTINUE
    }
}