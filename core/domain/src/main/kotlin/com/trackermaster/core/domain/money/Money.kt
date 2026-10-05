package com.trackermaster.core.domain.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Canonical money: units smaller than the currency unit, never a floating-point sum. */
data class Money(val minorUnits: Long, val currencyCode: String) {
    init {
        require(currencyCode == currencyCode.uppercase(Locale.ROOT))
        require(Currency.getInstance(currencyCode).defaultFractionDigits in 0..4)
    }

    val decimal: BigDecimal get() = BigDecimal.valueOf(minorUnits, scale(currencyCode))

    operator fun plus(other: Money): Money {
        require(currencyCode == other.currencyCode)
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: Money): Money {
        require(currencyCode == other.currencyCode)
        return copy(minorUnits = Math.subtractExact(minorUnits, other.minorUnits))
    }

    fun format(locale: Locale): String {
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = scale(currencyCode)
            maximumFractionDigits = scale(currencyCode)
        }
        return "$currencyCode ${number.format(decimal)}"
    }

    companion object {
        fun scale(currencyCode: String): Int = Currency.getInstance(currencyCode).defaultFractionDigits.also {
            require(it in 0..4)
        }

        /** New entries reject excess fractional precision instead of silently changing the amount. */
        fun fromDecimal(value: BigDecimal, currencyCode: String): Money {
            val code = currencyCode.uppercase(Locale.ROOT)
            return Money(value.movePointRight(scale(code)).setScale(0, RoundingMode.UNNECESSARY).longValueExact(), code)
        }

        fun fromDecimal(value: String, currencyCode: String): Money = fromDecimal(BigDecimal(value), currencyCode)

        /** Only for the legacy database/adapters. Original REAL values remain in the database. */
        fun fromLegacy(value: Double, currencyCode: String): Money {
            require(value.isFinite())
            val code = currencyCode.uppercase(Locale.ROOT)
            return Money(BigDecimal.valueOf(value).movePointRight(scale(code))
                .setScale(0, RoundingMode.HALF_EVEN).longValueExact(), code)
        }
    }
}

fun List<Money>.formatBalances(locale: Locale): String = joinToString(" · ") { it.format(locale) }.ifEmpty { "—" }
