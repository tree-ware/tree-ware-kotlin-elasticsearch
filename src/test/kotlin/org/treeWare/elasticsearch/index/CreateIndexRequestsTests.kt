package org.treeWare.elasticsearch.index

import org.treeWare.metaModel.addressBookMetaModel
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
                "Index name $expectedIndexName not found in generated index requests"
            }
        }
        
        // Verify that every actual index name can be found in the expected index names
        actualIndexNames.forEach { actualIndexName ->
            assert(expectedIndexNames.contains(actualIndexName)) {
                "Actual index name $actualIndexName not found in expected index names"
            }
        }
    }
}