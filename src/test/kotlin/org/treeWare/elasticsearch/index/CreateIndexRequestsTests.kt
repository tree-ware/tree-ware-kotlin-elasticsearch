package org.treeWare.elasticsearch.index

import co.elastic.clients.elasticsearch._types.mapping.Property
import org.treeWare.metaModel.FieldType
import org.treeWare.metaModel.addressBookMetaModel
import org.treeWare.metaModel.getFieldTypeMeta
import org.treeWare.metaModel.getMetaName
import org.treeWare.metaModel.traversal.AbstractLeader1MetaModelVisitor
import org.treeWare.metaModel.traversal.metaModelForEach
import org.treeWare.model.core.EntityModel
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateIndexRequestsTests {
    @Test
    fun `Create index requests for all entities in meta-model`() {
        // Generate index requests
        val indexRequests = createIndexRequests(addressBookMetaModel)

        // Hard-coded list of expected index names based on the JSON files used by addressBookMetaModel
        val expectedIndexNames = listOf(
            // From org.tree_ware.test.address_book.main package
            "org.tree_ware.test.address_book.main__address_book_root",
            "org.tree_ware.test.address_book.main__address_book_settings",
            "org.tree_ware.test.address_book.main__advanced_settings",
            "org.tree_ware.test.address_book.main__address_book_person",
            "org.tree_ware.test.address_book.main__group",
            "org.tree_ware.test.address_book.main__address_book_relation",
            "org.tree_ware.test.address_book.main__hero_details",

            // From org.tree_ware.test.address_book.city package
            "org.tree_ware.test.address_book.city__address_book_city",
            "org.tree_ware.test.address_book.city__address_book_city_info",

            // From org.tree_ware.test.address_book.club package
            "org.tree_ware.test.address_book.club__address_book_club",

            // From org.tree_ware.test.address_book.keyless package
            "org.tree_ware.test.address_book.keyless__keyless",
            "org.tree_ware.test.address_book.keyless__keyless_child",
            "org.tree_ware.test.address_book.keyless__keyed_child",

            // From org.tree_ware.meta_model.geo package
            "org.tree_ware.meta_model.geo__point"
        )

        // Verify the index names
        val actualIndexNames = indexRequests.map { it.index() }
        expectedIndexNames.forEach { expectedIndexName ->
            assert(actualIndexNames.contains(expectedIndexName)) {
                "Expected index name $expectedIndexName not found in actual index names"
            }
        }

        // Verify that every actual index name can be found in the expected index names
        actualIndexNames.forEach { actualIndexName ->
            assert(expectedIndexNames.contains(actualIndexName)) {
                "Actual index name $actualIndexName not found in expected index names"
            }
        }

        // Build a lookup from index name to request for mapping verification
        val requestByIndex = indexRequests.associateBy { it.index() }

        // Traverse the meta-model again and verify field mappings per entity/index
        metaModelForEach(addressBookMetaModel, MappingVerificationVisitor(requestByIndex))
    }
}

private class MappingVerificationVisitor(
    private val requestByIndex: Map<String, co.elastic.clients.elasticsearch.indices.CreateIndexRequest>
) : AbstractLeader1MetaModelVisitor<org.treeWare.model.traversal.TraversalAction>(org.treeWare.model.traversal.TraversalAction.CONTINUE) {
    private var currentPackageName: String = ""
    private var currentEntityName: String? = null

    override fun visitPackageMeta(leaderPackageMeta1: EntityModel) : org.treeWare.model.traversal.TraversalAction {
        currentPackageName = getMetaName(leaderPackageMeta1)
        return org.treeWare.model.traversal.TraversalAction.CONTINUE
    }

    override fun visitEntityMeta(leaderEntityMeta1: EntityModel) : org.treeWare.model.traversal.TraversalAction {
        currentEntityName = getMetaName(leaderEntityMeta1)
        return org.treeWare.model.traversal.TraversalAction.CONTINUE
    }

    override fun visitFieldMeta(leaderFieldMeta1: EntityModel) : org.treeWare.model.traversal.TraversalAction {
        val entityName = currentEntityName ?: return org.treeWare.model.traversal.TraversalAction.CONTINUE
        val indexName = "${currentPackageName}__${entityName}"
        val request = requestByIndex[indexName]
            ?: throw AssertionError("CreateIndexRequest not found for index $indexName")
        val props: Map<String, Property> = request.mappings()?.properties() ?: emptyMap()

        val fieldName = getMetaName(leaderFieldMeta1)
        val fieldType = getFieldTypeMeta(leaderFieldMeta1) ?: return org.treeWare.model.traversal.TraversalAction.CONTINUE
        val prop = props[fieldName] ?: throw AssertionError("Property '$fieldName' not found in index '$indexName'")

        // Verify the ES property type based on the meta-model field type mapping rules
        when (fieldType) {
            FieldType.BOOLEAN -> assert(prop.boolean_() != null) { failMsg(indexName, fieldName, "boolean") }
            FieldType.UINT8 -> assert(prop.short_() != null) { failMsg(indexName, fieldName, "short") }
            FieldType.UINT16 -> assert(prop.integer() != null) { failMsg(indexName, fieldName, "integer") }
            FieldType.UINT32 -> assert(prop.long_() != null) { failMsg(indexName, fieldName, "long") }
            FieldType.UINT64 -> assert(prop.unsignedLong() != null) { failMsg(indexName, fieldName, "unsigned_long") }
            FieldType.INT8 -> assert(prop.byte_() != null) { failMsg(indexName, fieldName, "byte") }
            FieldType.INT16 -> assert(prop.short_() != null) { failMsg(indexName, fieldName, "short") }
            FieldType.INT32 -> assert(prop.integer() != null) { failMsg(indexName, fieldName, "integer") }
            FieldType.INT64 -> assert(prop.long_() != null) { failMsg(indexName, fieldName, "long") }
            FieldType.FLOAT -> assert(prop.float_() != null) { failMsg(indexName, fieldName, "float") }
            FieldType.DOUBLE -> assert(prop.double_() != null) { failMsg(indexName, fieldName, "double") }
            FieldType.BIG_INTEGER -> assert(prop.keyword() != null) { failMsg(indexName, fieldName, "keyword (big_integer)") }
            FieldType.BIG_DECIMAL -> assert(prop.keyword() != null) { failMsg(indexName, fieldName, "keyword (big_decimal)") }
            FieldType.TIMESTAMP -> {
                val dateProp = prop.date() ?: throw AssertionError(failMsg(indexName, fieldName, "date"))
                val format = dateProp.format()
                assert(format == "epoch_millis") {
                    "Incorrect date format for '$fieldName' in index '$indexName': expected 'epoch_millis', actual '$format'"
                }
            }
            FieldType.STRING -> assert(prop.keyword() != null) { failMsg(indexName, fieldName, "keyword (string)") }
            FieldType.UUID -> assert(prop.keyword() != null) { failMsg(indexName, fieldName, "keyword (uuid)") }
            FieldType.BLOB -> assert(prop.binary() != null) { failMsg(indexName, fieldName, "binary") }
            FieldType.PASSWORD1WAY -> assert(prop.keyword() != null) { failMsg(indexName, fieldName, "keyword (password1way)") }
            FieldType.PASSWORD2WAY -> assert(prop.keyword() != null) { failMsg(indexName, fieldName, "keyword (password2way)") }
            FieldType.ALIAS -> assert(prop.keyword() != null) { failMsg(indexName, fieldName, "keyword (alias)") }
            FieldType.ENUMERATION -> assert(prop.integer() != null) { failMsg(indexName, fieldName, "integer (enum)") }
            FieldType.ASSOCIATION -> assert(prop.keyword() != null) { failMsg(indexName, fieldName, "keyword (association)") }
            FieldType.COMPOSITION -> assert(prop.nested() != null) { failMsg(indexName, fieldName, "nested (composition)") }
        }
        return org.treeWare.model.traversal.TraversalAction.CONTINUE
    }

    override fun leaveEntityMeta(leaderEntityMeta1: EntityModel) {
        currentEntityName = null
    }

    private fun failMsg(indexName: String, fieldName: String, expected: String) =
        "Incorrect mapping for '$fieldName' in index '$indexName' (expected $expected)"
}