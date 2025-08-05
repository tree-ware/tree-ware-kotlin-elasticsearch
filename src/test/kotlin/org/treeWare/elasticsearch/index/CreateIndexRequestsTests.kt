package org.treeWare.elasticsearch.index

import org.treeWare.metaModel.addressBookMetaModel
import org.treeWare.metaModel.getEntitiesMeta
import org.treeWare.metaModel.getMetaName
import org.treeWare.metaModel.getPackagesMeta
import org.treeWare.model.core.EntityModel
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateIndexRequestsTests {
    @Test
    fun `Create index requests for all entities in meta-model`() {
        // Generate index requests
        val indexRequests = createIndexRequests(addressBookMetaModel)
        
        // Collect expected index names
        val expectedIndexNames = mutableListOf<String>()
        val packagesMeta = getPackagesMeta(addressBookMetaModel)
        packagesMeta.values.forEach { packageElement ->
            val packageMeta = packageElement as EntityModel
            val packageName = getMetaName(packageMeta)
            val entitiesMeta = getEntitiesMeta(packageMeta)
            entitiesMeta?.values?.forEach { entityElement ->
                val entityMeta = entityElement as EntityModel
                val entityName = getMetaName(entityMeta)
                expectedIndexNames.add("${packageName}__${entityName}")
            }
        }
        
        // Verify the number of index requests
        assertEquals(expectedIndexNames.size, indexRequests.size, "Number of index requests")
        
        // Verify the index names
        val actualIndexNames = indexRequests.map { it.index() }
        expectedIndexNames.forEach { expectedIndexName ->
            assert(actualIndexNames.contains(expectedIndexName)) {
                "Index name $expectedIndexName not found in generated index requests"
            }
        }
    }
}