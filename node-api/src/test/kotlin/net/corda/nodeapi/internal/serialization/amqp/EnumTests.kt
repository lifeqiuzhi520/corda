package net.corda.nodeapi.internal.serialization.amqp

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EnumTests {
    enum class Bras {
        TSHIRT, UNDERWIRE, PUSHUP, BRALETTE, STRAPLESS, SPORTS, BACKLESS, PADDED
    }

    private val brasTestName = "${this.javaClass.name}\$Bras"

    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = true
    }

    @Suppress("NOTHING_TO_INLINE")
    inline private fun classTestName(clazz: String) = "${this.javaClass.name}\$${testName()}\$$clazz"


    private val sf1 = testDefaultFactory()

    @Test
    fun serialiseSimpleTest() {
        data class C(val c: Bras)

        val schema = TestSerializationOutput(VERBOSE, sf1).serializeAndReturnSchema(C(Bras.UNDERWIRE)).schema

        assertEquals(2, schema.types.size)
        val schema_c = schema.types.find { it.name == classTestName("C") } as CompositeType
        val schema_bras = schema.types.find { it.name == brasTestName } as RestrictedType

        assertNotNull(schema_c)
        assertNotNull(schema_bras)

        assertEquals(1, schema_c.fields.size)
        assertEquals("c", schema_c.fields.first().name)
        assertEquals(brasTestName, schema_c.fields.first().type)


    }


}