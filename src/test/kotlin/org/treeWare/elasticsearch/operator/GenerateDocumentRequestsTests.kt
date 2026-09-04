package org.treeWare.elasticsearch.operator

import org.treeWare.elasticsearch.index.FIELD_PATH
import org.treeWare.metaModel.addressBookRootEntityMeta
import org.treeWare.model.core.MutableEntityModel
import org.treeWare.model.decodeJsonFileIntoEntity
import org.treeWare.model.decoder.stateMachine.MultiAuxDecodingStateMachineFactory
import org.treeWare.model.operator.set.aux.SET_AUX_NAME
import org.treeWare.model.operator.set.aux.SetAuxStateMachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateDocumentRequestsTests {
    private val auxDecodingFactory =
        MultiAuxDecodingStateMachineFactory(SET_AUX_NAME to { SetAuxStateMachine(it) })

    @Test
    fun `Document operations match goldens`() {
        SET_INPUT_FILES.forEach { inputFile ->
            val operations = generateFor(inputFile)
            val actual = DocumentTestUtils.toNormalizedJson(operations)
            val resourcePath = "elasticsearch/documents/" + inputFile.replace(".json", "_documents.json")
            val expected = this::class.java.classLoader.getResource(resourcePath)?.readText()
                ?: throw IllegalStateException("Missing golden resource: $resourcePath")
            assertEquals(expected, actual, "Mismatch in document operations for input: $inputFile")
        }
    }

    @Test
    fun `Create covers singleton and keyed entities with field_path_ in every source`() {
        val operations = generateFor("es_set_create.json")
        assertTrue(operations.isNotEmpty())
        // Singleton entities (root, settings) have no keys but still produce documents.
        val indices = operations.map { it.index }.toSet()
        assertTrue(indices.contains("org.tree_ware.test.address_book.main__address_book_root"))
        assertTrue(indices.contains("org.tree_ware.test.address_book.main__address_book_settings"))
        assertTrue(indices.contains("org.tree_ware.test.address_book.main__advanced_settings"))
        assertTrue(indices.contains("org.tree_ware.test.address_book.main__address_book_person"))
        // Composition-key entities are flattened: city info lands in its own index.
        assertTrue(indices.contains("org.tree_ware.test.address_book.city__address_book_city_info"))
        assertTrue(indices.contains("org.tree_ware.meta_model.geo__point"))
        // Every index operation embeds field_path_ in its source.
        operations.filterIsInstance<DocumentOperation.Index>().forEach { op ->
            assertEquals(op.fieldPath, op.source[FIELD_PATH])
        }
        // Root document uses "/" as its path.
        val root = operations.filterIsInstance<DocumentOperation.Index>()
            .single { it.index.endsWith("__address_book_root") }
        assertEquals("/", root.fieldPath)
    }

    @Test
    fun `Update produces index operations only`() {
        val operations = generateFor("es_set_update.json")
        assertTrue(operations.isNotEmpty())
        assertTrue(operations.all { it is DocumentOperation.Index })
        assertTrue(operations.map { it.index }.contains("org.tree_ware.test.address_book.main__address_book_settings"))
    }

    @Test
    fun `Delete produces delete operations only`() {
        val operations = generateFor("es_set_delete.json")
        assertTrue(operations.isNotEmpty())
        assertTrue(operations.all { it is DocumentOperation.Delete })
        val fieldPaths = operations.map { it.fieldPath }.toSet()
        assertTrue(fieldPaths.contains("/"))
        assertTrue(fieldPaths.contains("/settings"))
        assertTrue(fieldPaths.any { it.startsWith("/persons/") })
    }

    @Test
    fun `Create covers keyless entities`() {
        val operations = generateFor("es_set_create_keyless.json")
        val indices = operations.map { it.index }.toSet()
        assertTrue(indices.contains("org.tree_ware.test.address_book.keyless__keyless"))
        assertTrue(indices.contains("org.tree_ware.test.address_book.keyless__keyless_child"))
        assertTrue(indices.contains("org.tree_ware.test.address_book.keyless__keyed_child"))
        // Keyed child of a keyless parent carries its own key fields.
        val keyedChild = operations.filterIsInstance<DocumentOperation.Index>()
            .single { it.index.endsWith("__keyed_child") }
        assertEquals("keyed grandchild", keyedChild.source["name"])
        assertEquals(42, (keyedChild.source["other"] as Number).toInt())
    }

    @Test
    fun `Model without set aux produces no operations`() {
        val model = MutableEntityModel(addressBookRootEntityMeta, null)
        decodeJsonFileIntoEntity("model/address_book_1.json", entity = model)
        assertEquals(emptyList(), generateDocumentRequests(model))
    }

    private fun generateFor(inputFile: String): List<DocumentOperation> {
        val model = MutableEntityModel(addressBookRootEntityMeta, null)
        decodeJsonFileIntoEntity(
            "model/$inputFile",
            multiAuxDecodingStateMachineFactory = auxDecodingFactory,
            entity = model
        )
        return generateDocumentRequests(model)
    }
}
