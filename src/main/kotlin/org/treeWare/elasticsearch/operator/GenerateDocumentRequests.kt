package org.treeWare.elasticsearch.operator

import okio.Buffer
import org.treeWare.elasticsearch.index.FIELD_PATH
import org.treeWare.elasticsearch.index.getIndexName
import org.treeWare.metaModel.FieldType
import org.treeWare.model.core.*
import org.treeWare.model.encoder.EncodePasswords
import org.treeWare.model.encoder.encodeJson
import org.treeWare.model.operator.Response
import org.treeWare.model.operator.set.SetDelegate
import org.treeWare.model.operator.set.aux.SetAux
import org.treeWare.util.encodeBase64

/**
 * A write operation for a single entity instance.
 *
 * `_id` of the Elasticsearch document is the [fieldPath]; [FIELD_PATH] is also
 * stored in the document `_source` (for [Index]) so that `get` can group hits
 * by path.
 *
 * Note: the path stored here is the *entity* path (with key values), not the
 * key-less field path MySQL stores in its `field_path_` column. The entity path
 * uniquely identifies an instance (e.g. `/persons/<id>`), which `_id`
 * requires; MySQL identifies rows by field-path plus key columns instead.
 */
sealed interface DocumentOperation {
    val index: String
    val fieldPath: String

    /** Creates or fully replaces the document for an entity instance. */
    data class Index(
        override val index: String,
        override val fieldPath: String,
        val source: Map<String, Any?>
    ) : DocumentOperation

    /** Deletes the document for an entity instance. */
    data class Delete(
        override val index: String,
        override val fieldPath: String
    ) : DocumentOperation
}

/**
 * Flattens a set-model into per-entity-instance document operations, one per
 * entity in traversal order. This is the Elasticsearch analog of MySQL DML
 * generation: pure request generation with no client interaction.
 *
 * - `CREATE` and `UPDATE` entities produce [DocumentOperation.Index] with a
 * source map containing [FIELD_PATH], key fields and all other single-valued
 * fields of the entity. Composition fields are skipped: each entity instance
 * is stored as its own document in its entity's dedicated index.
 * - `DELETE` entities produce [DocumentOperation.Delete].
 * - Fields with null values are omitted from the source map.
 *
 * @param model The set-model (with `set` aux values) to flatten.
 * @return The document operations in model-traversal order.
 * @throws IllegalStateException if the set traversal reports errors.
 */
fun generateDocumentRequests(model: EntityModel): List<DocumentOperation> {
    val recorder = DocumentRequestRecorder()
    when (val response = org.treeWare.model.operator.set(model, recorder, null)) {
        Response.Success -> Unit
        is Response.ErrorList -> throw IllegalStateException(
            "Unable to generate document requests: " +
                response.errorList.joinToString("; ") { it.toString() }
        )
        is Response.ErrorModel -> throw IllegalStateException("Unable to generate document requests")
        is Response.Model -> throw IllegalStateException("Unable to generate document requests")
    }
    return recorder.operations
}

private class DocumentRequestRecorder : SetDelegate {
    val operations = mutableListOf<DocumentOperation>()

    override fun begin(): Response = Response.Success

    override fun setEntity(
        setAux: SetAux,
        entity: EntityModel,
        fieldPath: String,
        entityPath: String,
        ancestorKeys: List<Keys>,
        keys: List<SingleFieldModel>,
        associations: List<FieldModel>,
        other: List<FieldModel>
    ): Response {
        val entityMeta = entity.meta ?: throw IllegalStateException("Entity at $entityPath does not have meta")
        val index = getIndexName(entityMeta)
        // Use the entity path (with key values): unlike the field path, it
        // uniquely identifies the instance, which the document `_id` requires.
        when (setAux) {
            SetAux.CREATE, SetAux.UPDATE -> {
                val source = mutableMapOf<String, Any?>()
                source[FIELD_PATH] = entityPath
                keys.forEach { addField(source, entityPath, it) }
                associations.forEach { addField(source, entityPath, it) }
                other.forEach { addField(source, entityPath, it) }
                operations.add(DocumentOperation.Index(index, entityPath, source))
            }
            SetAux.DELETE -> operations.add(DocumentOperation.Delete(index, entityPath))
        }
        return Response.Success
    }

    override fun end(): Response = Response.Success
}

private fun addField(source: MutableMap<String, Any?>, entityPath: String, field: FieldModel) {
    val singleField = field as? SingleFieldModel
        ?: throw IllegalStateException("Multi-valued field in entity $entityPath is not supported")
    val value = encodeFieldValue(entityPath, singleField) ?: return
    source[getFieldName(singleField)] = value
}

/** Encodes a single field value into a JSON-compatible document value. Returns null for null values. */
private fun encodeFieldValue(entityPath: String, field: SingleFieldModel): Any? {
    val fieldValue = field.value ?: return null
    return when (getFieldType(field)) {
        FieldType.BOOLEAN,
        FieldType.INT8,
        FieldType.INT16,
        FieldType.INT32,
        FieldType.INT64,
        FieldType.FLOAT,
        FieldType.DOUBLE,
        FieldType.BIG_INTEGER,
        FieldType.BIG_DECIMAL,
        FieldType.STRING -> (fieldValue as PrimitiveModel).value
        FieldType.UINT8 -> (fieldValue as PrimitiveModel).value.let { (it as UByte).toShort() }
        FieldType.UINT16 -> (fieldValue as PrimitiveModel).value.let { (it as UShort).toInt() }
        FieldType.UINT32 -> (fieldValue as PrimitiveModel).value.let { (it as UInt).toLong() }
        FieldType.UINT64 -> (fieldValue as PrimitiveModel).value.let { toJsonLong(it as ULong) }
        // The mapping uses `date` with `epoch_millis` format, so store epoch milliseconds.
        FieldType.TIMESTAMP -> (fieldValue as PrimitiveModel).value.let { toJsonLong(it as ULong) }
        FieldType.UUID -> (fieldValue as PrimitiveModel).value.toString()
        // The mapping uses `binary`, which expects base-64-encoded strings.
        FieldType.BLOB -> encodeBase64((fieldValue as PrimitiveModel).value as ByteArray)
        // Store the hashed/encrypted JSON form, same as the MySQL JSON columns.
        FieldType.PASSWORD1WAY,
        FieldType.PASSWORD2WAY -> encodeElementToJson(fieldValue)
        FieldType.ALIAS -> throw IllegalStateException("Alias fields are not supported in entity $entityPath")
        // Stored as a number (the mapping is `keyword`, which coerces numbers), same as MySQL.
        FieldType.ENUMERATION -> (fieldValue as EnumerationModel).number.toLong()
        // Store the JSON form of the association (target keys), same as the MySQL JSON column.
        FieldType.ASSOCIATION -> encodeElementToJson(fieldValue as AssociationModel)
        // Compositions are stored as their own documents, never inline. A composition
        // reaches here only if a single-value entity delegate is registered for it;
        // entity delegates are not yet supported (see work-plan Step 5).
        FieldType.COMPOSITION -> throw IllegalStateException("Composition fields are not supported in entity $entityPath")
    }
}

private fun toJsonLong(value: ULong): Number =
    if (value <= Long.MAX_VALUE.toULong()) value.toLong() else java.math.BigInteger(value.toString())

private fun encodeElementToJson(element: ElementModel): String {
    val buffer = Buffer()
    // ALL (rather than HASHED_AND_ENCRYPTED as in MySQL): this stack has no
    // hasher/cipher configured, so unhashed/unencrypted values would otherwise
    // be silently dropped from the document.
    if (!encodeJson(element, buffer, encodePasswords = EncodePasswords.ALL)) {
        throw IllegalStateException("Unable to encode element to JSON")
    }
    return buffer.readUtf8()
}
