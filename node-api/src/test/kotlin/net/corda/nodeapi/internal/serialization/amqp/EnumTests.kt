package net.corda.nodeapi.internal.serialization.amqp

import org.junit.Test

class EnumTests {
    enum class Bras {
        TSHIRT, UNDERWIRE, PUSHUP, BRALETTE, STRAPLESS, SPORTS, BACKLESS, PADDED
    }

    val sf1 = testDefaultFactory()

    @Test
    fun serialiseSimpleTest() {
        data class C(val c: Bras)

        val serialised = SerializationOutput(sf1).serialize(C(Bras.UNDERWIRE))
    }
}