package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.internal.getStackTraceAsString
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.ByteSequence
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.amqp.UnsignedByte
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.util.*

data class ObjectAndEnvelope<out T>(val obj: T, val envelope: Envelope)

/**
 * Main entry point for deserializing an AMQP encoded object.
 *
 * @param serializerFactory This is the factory for [AMQPSerializer] instances and can be shared across multiple
 * instances and threads.
 */
class DeserializationInput(private val serializerFactory: SerializerFactory) {
    // TODO: we're not supporting object refs yet
    private val objectHistory: MutableList<Any> = ArrayList()

    internal companion object {
        private val BYTES_NEEDED_TO_PEEK: Int = 23

        fun peekSize(bytes: ByteArray): Int {
            // There's an 8 byte header, and then a 0 byte plus descriptor followed by constructor
            val eighth = bytes[8].toInt()
            check(eighth == 0x0) { "Expected to find a descriptor in the AMQP stream" }
            // We should always have an Envelope, so the descriptor should be a 64-bit long (0x80)
            val ninth = UnsignedByte.valueOf(bytes[9]).toInt()
            check(ninth == 0x80) { "Expected to find a ulong in the AMQP stream" }
            // Skip 8 bytes
            val eighteenth = UnsignedByte.valueOf(bytes[18]).toInt()
            check(eighteenth == 0xd0 || eighteenth == 0xc0) { "Expected to find a list8 or list32 in the AMQP stream" }
            val size = if (eighteenth == 0xc0) {
                // Next byte is size
                UnsignedByte.valueOf(bytes[19]).toInt() - 3 // Minus three as PEEK_SIZE assumes 4 byte unsigned integer.
            } else {
                // Next 4 bytes is size
                UnsignedByte.valueOf(bytes[19]).toInt().shl(24) + UnsignedByte.valueOf(bytes[20]).toInt().shl(16) + UnsignedByte.valueOf(bytes[21]).toInt().shl(8) + UnsignedByte.valueOf(bytes[22]).toInt()
            }
            return size + BYTES_NEEDED_TO_PEEK
        }
    }

    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> deserialize(bytes: SerializedBytes<T>): T =
            deserialize(bytes, T::class.java)


    @Throws(NotSerializableException::class)
    inline fun <reified T : Any> deserializeAndReturnEnvelope(bytes: SerializedBytes<T>): ObjectAndEnvelope<T> =
            deserializeAndReturnEnvelope(bytes, T::class.java)

    @Throws(NotSerializableException::class)
    private fun getEnvelope(bytes: ByteSequence): Envelope {
        // Check that the lead bytes match expected header
        val headerSize = AmqpHeaderV1_0.size
        if (bytes.take(headerSize) != AmqpHeaderV1_0) {
            throw NotSerializableException("Serialization header does not match.")
        }

        val data = Data.Factory.create()
        val size = data.decode(ByteBuffer.wrap(bytes.bytes, bytes.offset + headerSize, bytes.size - headerSize))
        if (size.toInt() != bytes.size - headerSize) {
            throw NotSerializableException("Unexpected size of data")
        }

        return Envelope.get(data)
    }

    @Throws(NotSerializableException::class)
    private fun <R> des(generator: () -> R): R {
        try {
            return generator()
        } catch (nse: NotSerializableException) {
            throw nse
        } catch (t: Throwable) {
            throw NotSerializableException("Unexpected throwable: ${t.message} ${t.getStackTraceAsString()}")
        } finally {
            objectHistory.clear()
        }
    }

    /**
     * This is the main entry point for deserialization of AMQP payloads, and expects a byte sequence involving a header
     * indicating what version of Corda serialization was used, followed by an [Envelope] which carries the object to
     * be deserialized and a schema describing the types of the objects.
     */
    @Throws(NotSerializableException::class)
    fun <T : Any> deserialize(bytes: ByteSequence, clazz: Class<T>): T = des {
        val envelope = getEnvelope(bytes)
        clazz.cast(readObjectOrNull(envelope.obj, envelope.schema, clazz))
    }

    @Throws(NotSerializableException::class)
    fun <T : Any> deserializeAndReturnEnvelope(bytes: SerializedBytes<T>, clazz: Class<T>): ObjectAndEnvelope<T> = des {
        val envelope = getEnvelope(bytes)
        // Now pick out the obj and schema from the envelope.
        ObjectAndEnvelope(clazz.cast(readObjectOrNull(envelope.obj, envelope.schema, clazz)), envelope)
    }

    internal fun readObjectOrNull(obj: Any?, schema: Schema, type: Type): Any? {
        return if (obj == null) null else readObject(obj, schema, type)
    }

    internal fun readObject(obj: Any, schema: Schema, type: Type) = when (obj) {
        is DescribedType -> {
            // Look up serializer in factory by descriptor
            val serializer = serializerFactory.get(obj.descriptor, schema)
            if (serializer.type != type && with(serializer.type) { !isSubClassOf(type) && !materiallyEquivalentTo(type) })
                throw NotSerializableException("Described type with descriptor ${obj.descriptor} was " +
                        "expected to be of type $type but was ${serializer.type}")
            serializer.readObject(obj.described, schema, this)
        }
        is Binary -> obj.array
        else -> obj
    }

    /**
     * TODO: Currently performs rather basic checks aimed in particular at [java.util.List<Command<?>>] and
     * [java.lang.Class<? extends net.corda.core.contracts.Contract>]
     * In the future tighter control might be needed
     */
    private fun Type.materiallyEquivalentTo(that: Type): Boolean =
            asClass() == that.asClass() && that is ParameterizedType
}
