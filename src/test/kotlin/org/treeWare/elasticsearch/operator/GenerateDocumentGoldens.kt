package org.treeWare.elasticsearch.operator

import org.treeWare.metaModel.addressBookRootEntityMeta
import org.treeWare.model.core.MutableEntityModel
import org.treeWare.model.decodeJsonFileIntoEntity
import org.treeWare.model.decoder.stateMachine.MultiAuxDecodingStateMachineFactory
import org.treeWare.model.operator.set.aux.SET_AUX_NAME
import org.treeWare.model.operator.set.aux.SetAuxStateMachine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.Test

class GenerateDocumentGoldens {
    @Ignore
    @Test
    fun `Generate golden JSON files for document operations`() {
        val auxDecodingFactory = MultiAuxDecodingStateMachineFactory(SET_AUX_NAME to { SetAuxStateMachine(it) })
        val base: Path = Path.of("src/test/resources/elasticsearch/documents")
        Files.createDirectories(base)
        SET_INPUT_FILES.forEach { inputFile ->
            val model = MutableEntityModel(addressBookRootEntityMeta, null)
            decodeJsonFileIntoEntity(
                "model/$inputFile",
                multiAuxDecodingStateMachineFactory = auxDecodingFactory,
                entity = model
            )
            val operations = generateDocumentRequests(model)
            val pretty = DocumentTestUtils.toNormalizedJson(operations)
            Files.writeString(base.resolve(inputFile.replace(".json", "_documents.json")), pretty)
        }
        // This is a generator test; it will always pass. Manually inspect/commit generated files.
        assert(true)
    }
}
