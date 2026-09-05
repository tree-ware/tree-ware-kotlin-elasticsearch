package org.treeWare.elasticsearch.operator

import co.elastic.clients.elasticsearch.ElasticsearchClient
import org.lighthousegames.logging.logging
import org.treeWare.model.core.EntityModel

private val logger = logging()

/**
 * Creates one index per meta-model entity, mirroring MySQL `createDatabase`.
 *
 * Index names and mappings come from [createIndexRequests]; this function only
 * issues them to Elasticsearch. Every mapping contains an [ENTITY_PATH_FIELD_NAME] keyword
 * field identifying the entity instance's tree-ware path.
 *
 * @param client Elasticsearch client used to issue the create-index requests.
 * @param metaModel The meta-model to create indices for.
 * @param logRequests When true, each create-index request is logged before it is issued.
 */
fun createIndices(
    client: ElasticsearchClient,
    metaModel: EntityModel,
    logRequests: Boolean = false
) {
    val requests = createIndexRequests(metaModel)
    requests.forEach { request ->
        if (logRequests) logger.info { "Create index: ${request.index()}" }
        try {
            client.indices().create(request)
        } catch (e: Exception) {
            logger.error { "Exception for create-index request: ${request.index()}" }
            throw e
        }
    }
}
