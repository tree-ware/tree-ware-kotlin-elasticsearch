package org.treeWare.elasticsearch.index

import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import org.treeWare.metaModel.getMetaName
import org.treeWare.metaModel.traversal.AbstractLeader1MetaModelVisitor
import org.treeWare.metaModel.traversal.metaModelForEach
import org.treeWare.model.core.EntityModel
import org.treeWare.model.traversal.TraversalAction

/**
 * Generates a list of CreateIndexRequest instances for all entities in the meta-model.
 * The index name for each entity is a concatenation of the package name and entity name with "__" as separator.
 *
 * @param metaModel The meta-model to process.
 * @return A list of CreateIndexRequest instances, one for each entity in the meta-model.
 */
fun createIndexRequests(metaModel: EntityModel): List<CreateIndexRequest> {
    val visitor = CreateIndexRequestsVisitor()
    metaModelForEach(metaModel, visitor)
    return visitor.indexRequests
}

private class CreateIndexRequestsVisitor : AbstractLeader1MetaModelVisitor<TraversalAction>(TraversalAction.CONTINUE) {
    val indexRequests = mutableListOf<CreateIndexRequest>()
    private var currentPackageName = ""

    override fun visitPackageMeta(leaderPackageMeta1: EntityModel): TraversalAction {
        currentPackageName = getMetaName(leaderPackageMeta1)
        return TraversalAction.CONTINUE
    }

    override fun visitEntityMeta(leaderEntityMeta1: EntityModel): TraversalAction {
        val entityName = getMetaName(leaderEntityMeta1)
        val indexName = "${currentPackageName}__${entityName}"
        
        // Create a new CreateIndexRequest with the index name
        val request = CreateIndexRequest.Builder()
            .index(indexName)
            .build()
        
        indexRequests.add(request)
        return TraversalAction.CONTINUE
    }
}