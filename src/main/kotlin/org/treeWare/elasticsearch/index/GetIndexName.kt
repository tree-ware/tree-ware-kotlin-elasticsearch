package org.treeWare.elasticsearch.index

import org.treeWare.metaModel.getFullName
import org.treeWare.model.core.EntityModel

/**
 * Returns the index name for the specified entity meta-model.
 *
 * The index name is the meta-model full-name (`/<package>/<entity>`) with the
 * leading `/` dropped and `/` replaced by `__`, e.g.
 * `org.tree_ware.test.address_book.main__address_book_person`.
 */
fun getIndexName(entityMeta: EntityModel): String {
    val fullName = getFullName(entityMeta)
        ?: throw IllegalStateException("Entity meta-model is not resolved")
    return fullName.removePrefix("/").replace("/", "__")
}
