package org.treeWare.elasticsearch.testutil

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.treeWare.elasticsearch.document.DocumentOperation
import org.treeWare.elasticsearch.document.DocumentOperation.Delete
import org.treeWare.elasticsearch.document.DocumentOperation.Create
import org.treeWare.elasticsearch.document.DocumentOperation.Update

internal object DocumentTestUtils {
    private val objectMapper = ObjectMapper()
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

    /** Serializes document operations to normalized (key-sorted, pretty-printed) JSON. */
    fun toNormalizedJson(operations: List<DocumentOperation>): String {
        val json = objectMapper.writeValueAsString(operations.map { it.toMap() })
        val node = objectMapper.readTree(json)
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
    }

    private fun DocumentOperation.toMap(): Map<String, Any?> = when (this) {
        is Create -> mapOf(
            "op" to "create",
            "index" to index,
            "entity_path_" to entityPath,
            "source" to source
        )
        is Update -> mapOf(
            "op" to "update",
            "index" to index,
            "entity_path_" to entityPath,
            "source" to partialSource
        )
        is Delete -> mapOf(
            "op" to "delete",
            "index" to index,
            "entity_path_" to entityPath
        )
    }
}
