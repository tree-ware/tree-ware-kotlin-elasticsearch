package org.treeWare.elasticsearch.operator

import org.treeWare.elasticsearch.testutil.DocumentTestUtils
import org.treeWare.metaModel.addressBookRootEntityMeta
import org.treeWare.model.core.MutableEntityModel
import org.treeWare.model.decodeJsonFileIntoEntity
import org.treeWare.model.decoder.stateMachine.MultiAuxDecodingStateMachineFactory
import org.treeWare.model.operator.set.aux.SET_AUX_NAME
import org.treeWare.model.operator.set.aux.SetAuxStateMachine
import kotlin.test.Test
import kotlin.test.assertEquals

/** Set-model inputs (in `src/test/resources/model/`) covered by document-operation golden files. */
internal val SET_INPUT_FILES = listOf(
    "es_set_create.json",
    "es_set_update.json",
    "es_set_delete.json",
    "es_set_create_keyless.json"
)

class CreateDocumentRequestsTests {
    private val auxDecodingFactory =
        MultiAuxDecodingStateMachineFactory(SET_AUX_NAME to { SetAuxStateMachine(it) })

    @Test
    fun `Document operations match golden files`() {
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
    fun `Model without set aux produces no operations`() {
        val model = MutableEntityModel(addressBookRootEntityMeta, null)
        decodeJsonFileIntoEntity("model/address_book_1.json", entity = model)
        assertEquals(emptyList(), createDocumentRequests(model))
    }

    private fun generateFor(inputFile: String): List<DocumentOperation> {
        val model = MutableEntityModel(addressBookRootEntityMeta, null)
        decodeJsonFileIntoEntity(
            "model/$inputFile",
            multiAuxDecodingStateMachineFactory = auxDecodingFactory,
            entity = model
        )
        return createDocumentRequests(model)
    }
}
