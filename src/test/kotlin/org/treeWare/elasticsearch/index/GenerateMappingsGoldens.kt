package org.treeWare.elasticsearch.index

import org.treeWare.metaModel.addressBookMetaModel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.Test

class GenerateMappingsGoldens {
    @Ignore
    @Test
    fun `Generate golden JSON files for CreateIndexRequest bodies`(){
        val indexRequests = createIndexRequests(addressBookMetaModel)
        val base: Path = Path.of("src/test/resources/elasticsearch/mappings")
        Files.createDirectories(base)
        indexRequests.forEach { req ->
            val index = req.index()
            val body = JsonTestUtils.serializeRequestBodyToJson(req)
            val pretty = JsonTestUtils.normalizeJson(body)
            val path = base.resolve("$index.json")
            Files.writeString(path, pretty)
        }
        // This is a generator test; it will always pass. Manually inspect/commit generated files.
        assert(true)
    }
}