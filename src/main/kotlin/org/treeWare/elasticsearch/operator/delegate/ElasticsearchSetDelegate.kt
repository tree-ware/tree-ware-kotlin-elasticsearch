package org.treeWare.elasticsearch.operator.delegate

import com.fasterxml.jackson.databind.ObjectMapper
import okio.Buffer
import org.treeWare.elasticsearch.index.ENTITY_PATH_FIELD_NAME
import org.treeWare.elasticsearch.index.getIndexName
import org.treeWare.elasticsearch.operator.DocumentOperation
import org.treeWare.metaModel.FieldType
import org.treeWare.model.core.AssociationModel
import org.treeWare.model.core.ElementModel
import org.treeWare.model.core.EntityModel
import org.treeWare.model.core.EnumerationModel
import org.treeWare.model.core.FieldModel
import org.treeWare.model.core.Keys
import org.treeWare.model.core.PrimitiveModel
import org.treeWare.model.core.SingleFieldModel
import org.treeWare.model.core.getFieldName
import org.treeWare.model.core.getFieldType
import org.treeWare.model.encoder.EncodePasswords
import org.treeWare.model.encoder.encodeJson
import org.treeWare.model.operator.Response
import org.treeWare.model.operator.set.SetDelegate
import org.treeWare.model.operator.set.aux.SetAux
import org.treeWare.util.encodeBase64

internal class ElasticsearchSetDelegate : SetDelegate {
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
                source[ENTITY_PATH_FIELD_NAME] = entityPath
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
        FieldType.UINT64 -> toJsonLong((fieldValue as PrimitiveModel).value as ULong)
        // The mapping uses `date` with `epoch_millis` format, so store epoch milliseconds.
        FieldType.TIMESTAMP -> toJsonLong((fieldValue as PrimitiveModel).value as ULong)
        FieldType.UUID -> (fieldValue as PrimitiveModel).value.toString()
        // The mapping uses `binary`, which expects base-64-encoded strings.
        FieldType.BLOB -> encodeBase64((fieldValue as PrimitiveModel).value as ByteArray)
        // Store the hashed/encrypted form as a nested JSON object.
        FieldType.PASSWORD1WAY,
        FieldType.PASSWORD2WAY -> encodeElementToNestedJson(fieldValue)
        FieldType.ALIAS -> throw IllegalStateException("Alias fields are not supported in entity $entityPath")
        // Store the symbolic name (the mapping is `keyword`).
        FieldType.ENUMERATION -> (fieldValue as EnumerationModel).value
        // Store the association (target keys) as a nested JSON object.
        FieldType.ASSOCIATION -> encodeElementToNestedJson(fieldValue as AssociationModel)
        // Compositions are stored as their own documents, never inline. A composition
        // reaches here only if a single-value entity delegate is registered for it;
        // entity delegates are not yet supported (see work-plan Step 5).
        FieldType.COMPOSITION -> throw IllegalStateException("Composition fields are not supported in entity $entityPath")
    }
}

private fun toJsonLong(value: ULong): Number =
    if (value <= Long.MAX_VALUE.toULong()) value.toLong() else java.math.BigInteger(value.toString())

private val jsonMapper = ObjectMapper()

/**
 * Encodes a password or association value as a nested JSON object (parsed into
 * maps and lists) for the document `_source`. Returns null when there is
 * nothing to store (a password without a hashed/encrypted form encodes to
 * nothing); the caller omits nulls from the document.
 */
private fun encodeElementToNestedJson(element: ElementModel): Map<String, Any?>? {
    val buffer = Buffer()
    if (!encodeJson(element, buffer, encodePasswords = EncodePasswords.HASHED_AND_ENCRYPTED)) {
        throw IllegalStateException("Unable to encode element to JSON")
    }
    val json = buffer.readUtf8()
    if (json.isBlank()) return null
    @Suppress("UNCHECKED_CAST")
    return jsonMapper.readValue(json, Map::class.java) as Map<String, Any?>
}
