package com.trackermaster.core.domain.money

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    @Test fun outgoingNequiExtractsAmountDateAndReference() {
        val a=ReceiptParser.parse("Nequi\nEnviaste\nValor\n$ 50.000,50\nFecha: 04/10/2026\nReferencia: M123\nPara: Ana")
        assertEquals(Money(5000050,"COP"),a.amount); assertEquals(LocalDate.of(2026,10,4),a.date)
        assertEquals(MoneyDirection.EXPENSE,a.direction); assertEquals("M123",a.reference); assertEquals("Ana",a.recipient)
    }
    @Test fun receivedNequiIsIncome() { assertEquals(MoneyDirection.INCOME,ReceiptParser.parse("NEQUI\nRecibiste\nMonto $20.000").direction) }
    @Test fun genericTransferNeverGuessesDirection() {
        val a=ReceiptParser.parse("Nequi\nTransferencia exitosa\nMonto: $10.000")
        assertNull(a.direction); assertEquals(ReceiptBankStatus.SUCCESS,a.status)
    }
    @Test fun transferRequiresEvidenceOfOwnAccounts() {
        assertEquals(ReceiptEntryKind.TRANSFER, ReceiptDraft.from(ReceiptParser.parse("Transferencia entre mis cuentas")).kind)
        assertNull(ReceiptDraft.from(ReceiptParser.parse("Transferencia exitosa a Maria")).kind)
    }
    @Test fun labelledAmountBeatsBalanceAndPhone() {
        val a=ReceiptParser.parse("Nequi\nMonto: $3.200\nSaldo disponible\n$ 987.000\nCelular 3001234567\nReferencia 1234567890")
        assertEquals(Money(320000,"COP"),a.amount)
    }
    @Test fun dollarWithoutCurrencyOrProviderNeedsReview() { assertNull(ReceiptParser.parse("Amount: $500.00").amount) }
    @Test fun usdSupportsUsDecimalAndGrouping() { assertEquals(Money(123450,"USD"),ReceiptParser.parse("Amount: USD 1,234.50\nYou received").amount) }
    @Test fun conflictingCurrenciesOrAmountsRemainMissing() {
        assertNull(ReceiptParser.parse("Nequi\nMonto COP 1.000\nUSD 10").amount)
        assertNull(ReceiptParser.parse("Nequi\nMonto: $1.000\nValor: $2.000").amount)
    }
    @Test fun pendingAndFailedOverrideSuccessWords() {
        assertEquals(ReceiptBankStatus.PENDING,ReceiptParser.parse("Nequi\nEnvío exitoso\nPendiente").status)
        assertEquals(ReceiptBankStatus.FAILED,ReceiptParser.parse("Nequi\nNo fue exitoso\nEn proceso").status)
    }
    @Test fun generatedDatesAndAmbiguousNumericDatesAreNotAssumed() {
        assertNull(ReceiptParser.parse("Fecha generada: 2026-11-03").date)
        assertNull(ReceiptParser.parse("Fecha: 04/10/2026").date)
        assertEquals(LocalDate.of(2026, 10, 13), ReceiptParser.parse("Fecha: 13/10/2026").date)
    }
    @Test fun timeIsOptionalAndGenerationTimeIsIgnored() {
        assertNull(ReceiptParser.parse("Fecha 2026-11-03").time)
        assertEquals(java.time.LocalTime.of(14, 32), ReceiptParser.parse("Fecha 2026-11-03\nHora 14:32").time)
        assertNull(ReceiptParser.parse("Generado 2026-11-03 15:00").time)
        assertNull(ReceiptParser.parse("Hora 14:32\nHora 15:00").time)
    }
    @Test fun aFeeAloneIsNotSuggestedAsTransactionAmount() {
        assertNull(ReceiptParser.parse("Nequi\nComisión\n$ 1.000,00").amount)
    }
    @Test fun datesUseCalendarDayAndRejectInvalidOrConflicting() {
        assertEquals(LocalDate.of(2026,10,4),ReceiptParser.parse("4 de octubre de 2026").date)
        assertNull(ReceiptParser.parse("Fecha 31/02/2026").date)
        assertNull(ReceiptParser.parse("04/10/2026\n05/10/2026").date)
    }
    @Test fun unknownDirectionAndDateHaveNoDefaultInDraft() {
        val d=ReceiptDraft.from(ReceiptParser.parse("Nequi\nMonto $50.000"))
        assertNull(d.kind); assertEquals("",d.date); assertNull(d.accountId); assertFalse(d.confirmed)
    }
    @Test fun overflowAndAmbiguousUsdPrecisionNeverBecomeAmounts() {
        assertNull(ReceiptParser.parse("Amount: USD 10.005").amount)
        assertNull(ReceiptParser.parse("Nequi\nMonto 999999999999999999999999").amount)
    }
    @Test fun duplicateAcknowledgementIsStableAndDistinct() {
        val a=ReceiptDuplicate(2,ReceiptDuplicateReason.FILE); val b=ReceiptDuplicate(1,ReceiptDuplicateReason.SIMILAR)
        assertEquals(listOf(a,b,a).fingerprint(),listOf(b,a).fingerprint())
        assertNotEquals(listOf(a).fingerprint(),listOf(a,b).fingerprint())
    }
    @Test fun genericReceiptHeaderIsNotAReferenceAndManualAmountDoesNotAcceptGrouping() {
        assertNull(ReceiptParser.parse("Nequi\nComprobante de transferencia\nMonto COP 50.000").reference)
        assertThrows(IllegalArgumentException::class.java){ReceiptValidation.amount("50.000","COP")}
        assertEquals(Money(5000000,"COP"),ReceiptValidation.amount("50000","COP"))
    }
}
