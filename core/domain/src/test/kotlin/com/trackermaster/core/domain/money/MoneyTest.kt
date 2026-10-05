package com.trackermaster.core.domain.money

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class MoneyTest {
    @Test fun decimalArithmeticIsExact() {
        assertEquals(Money(30, "USD"), Money.fromDecimal("0.10", "USD") + Money.fromDecimal("0.20", "USD"))
    }
    @Test fun copUsesExplicitMinorUnits() { assertEquals(123456L, Money.fromDecimal("1234.56", "COP").minorUnits) }
    @Test fun newEntryRejectsExtraPrecision() { assertThrows(ArithmeticException::class.java) { Money.fromDecimal("1.001", "USD") } }
    @Test fun currenciesCannotBeAdded() { assertThrows(IllegalArgumentException::class.java) { Money(100, "COP") + Money(100, "USD") } }
    @Test fun overflowIsRejected() { assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE, "COP") + Money(1, "COP") } }
    @Test fun legacyRoundingIsHalfEven() {
        assertEquals(100L, Money.fromLegacy(1.005, "USD").minorUnits)
        assertEquals(102L, Money.fromLegacy(1.015, "USD").minorUnits)
        assertEquals(-102L, Money.fromLegacy(-1.015, "USD").minorUnits)
    }
    @Test fun nonFiniteLegacyAmountIsRejected() { assertThrows(IllegalArgumentException::class.java) { Money.fromLegacy(Double.NaN, "COP") } }
    @Test fun conversionOverflowIsRejected() { assertThrows(ArithmeticException::class.java) { Money.fromLegacy(Double.MAX_VALUE, "USD") } }
    @Test fun zeroDecimalCurrenciesKeepTheirScale() { assertEquals(1200L, Money.fromDecimal("1200", "JPY").minorUnits) }
    @Test fun currencyIsVisibleInEveryLocale() {
        listOf(Locale.US, Locale.forLanguageTag("es-CO"), Locale.SIMPLIFIED_CHINESE).forEach {
            assertTrue(Money(12345, "USD").format(it).startsWith("USD "))
        }
    }
}
