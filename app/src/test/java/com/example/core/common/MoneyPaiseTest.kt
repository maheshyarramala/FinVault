package com.example.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyPaiseTest {

    @Test
    fun `formatInr formats Indian numbering groups accurately`() {
        assertEquals("₹0.00", MoneyPaise.ZERO.formatInr())
        assertEquals("₹999.05", MoneyPaise(99905L).formatInr())
        assertEquals("₹12,345.67", MoneyPaise(1234567L).formatInr())
        assertEquals("₹1,25,450.75", MoneyPaise(12545075L).formatInr())
        assertEquals("₹10,00,000.00", MoneyPaise(100000000L).formatInr())
        assertEquals("-₹5,000.50", MoneyPaise(-500050L).formatInr())
        assertEquals("+₹500.00", MoneyPaise(50000L).formatInr(showPlusSign = true))
    }

    @Test
    fun `parseFromRupeeInput avoids floating point drift`() {
        assertEquals(MoneyPaise(1999L), MoneyPaise.parseFromRupeeInput("19.99"))
        assertEquals(MoneyPaise(12545075L), MoneyPaise.parseFromRupeeInput("₹1,25,450.75"))
        assertEquals(MoneyPaise(10000L), MoneyPaise.parseFromRupeeInput("100"))
        assertNull(MoneyPaise.parseFromRupeeInput(""))
        assertNull(MoneyPaise.parseFromRupeeInput("abc"))
    }

    @Test
    fun `arithmetic preserves exact integer paise`() {
        val a = MoneyPaise(1050L)
        val b = MoneyPaise(250L)
        assertEquals(MoneyPaise(1300L), a + b)
        assertEquals(MoneyPaise(800L), a - b)
    }
}
