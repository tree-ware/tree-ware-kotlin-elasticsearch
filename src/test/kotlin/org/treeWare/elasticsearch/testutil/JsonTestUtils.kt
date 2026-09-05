package org.treeWare.elasticsearch.testutil

import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.json.JsonpMapper
import co.elastic.clients.json.JsonpUtils
import co.elastic.clients.json.SimpleJsonpMapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

internal object JsonTestUtils {
    private val mapper: JsonpMapper = SimpleJsonpMapper()

    private val objectMapper = ObjectMapper()
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

    fun serializeRequestBodyToJson(request: CreateIndexRequest): String {
        // Uses the ES client utility to serialize using its default mapper/provider
        return JsonpUtils.toJsonString(request, mapper)
    }

    fun normalizeJson(json: String): String {
        val node = objectMapper.readTree(json)
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)
    }
}