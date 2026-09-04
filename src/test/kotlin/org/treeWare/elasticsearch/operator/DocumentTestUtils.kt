package org.treeWare.elasticsearch.operator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.treeWare.elasticsearch.operator.DocumentOperation.Delete
import org.treeWare.elasticsearch.operator.DocumentOperation.Index

/** Set-model inputs (in `src/test/resources/model/`) covered by document-operation goldens. */
internal val SET_INPUT_FILES = listOf(
    "es_set_create.json",
    "es_set_update.json",
    "es_set_delete.json",
    "es_set_create_keyless.json"
)

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
        is Index -> mapOf(
            "op" to "index",
            "index" to index,
            "field_path_" to fieldPath,
            "source" to source
        )
        is Delete -> mapOf(
            "op" to "delete",
            "index" to index,
            "field_path_" to fieldPath
        )
    }
}
