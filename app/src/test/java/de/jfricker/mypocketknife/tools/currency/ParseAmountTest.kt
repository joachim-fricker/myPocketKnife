package de.jfricker.mypocketknife.tools.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseAmountTest {
    @Test fun komma() = assertEquals(1234.56, parseAmount("1234,56")!!, 1e-9)
    @Test fun deutsch() = assertEquals(1234.56, parseAmount("1.234,56")!!, 1e-9)
    @Test fun schweiz() = assertEquals(1234.56, parseAmount("1'234.56")!!, 1e-9)
    @Test fun englisch() = assertEquals(1234.56, parseAmount("1,234.56")!!, 1e-9)
    @Test fun leer() = assertNull(parseAmount(""))
    @Test fun unsinn() = assertNull(parseAmount("abc"))
}
