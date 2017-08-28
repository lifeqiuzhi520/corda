package net.corda.nodeapi.internal.serialization.amqp

import org.junit.Test

class EnumTests {
    enum class Bras {
        TSHIRT, UNDERWIRE, PUSHUP, BRALETTE, STRAPLESS, SPORTS, BACKLESS, PADDED
    }

    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = true
    }


    private val sf1 = testDefaultFactory()

    @Test
    fun serialiseSimpleTest() {
        data class C(val c: Bras)

        val serialised = TestSerializationOutput(VERBOSE, sf1).serialize(C(Bras.UNDERWIRE))
    }
}