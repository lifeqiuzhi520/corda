package net.corda.nodeapi.internal.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class EnumSerializer(declaredType: ParameterizedType, factory: SerializerFactory) : AMQPSerializer<Any> {
    override val type: Type = declaredType as? DeserializedParameterizedType ?: DeserializedParameterizedType.make(declaredType.toString())
    override val typeDescriptor = "$DESCRIPTOR_DOMAIN:${fingerprintForType(type, factory)}"

    override fun writeClassInfo(output: SerializationOutput) {
        throw NotImplementedError()
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any {
        throw NotImplementedError()
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        throw NotImplementedError()
    }
}