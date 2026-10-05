package com.trackermaster.core.domain.money

import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/** Conservative local rules, independent of UI language. No phone, balance or reference amounts. */
object ReceiptParser {
    const val MAX_TEXT = 20000
    fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
    fun parse(input: String): ReceiptAnalysis {
        val lines = input.take(MAX_TEXT).lines().map(String::trim).filter(String::isNotEmpty)
        val text = normalize(lines.joinToString("\n"))
        val provider = if (Regex("\\bnequi\\b").containsMatchIn(text)) "NEQUI" else null
        val status = when {
            Regex("rechazad|fallid|cancelad|no se pudo|no fue exitos|failed|declined|cancelled|unsuccessful").containsMatchIn(text) -> ReceiptBankStatus.FAILED
            Regex("pendiente|en proceso|procesando|pending|processing").containsMatchIn(text) -> ReceiptBankStatus.PENDING
            Regex("exitos|successful|completed|realizado|recibiste|enviaste|pagaste|has recibido").containsMatchIn(text) -> ReceiptBankStatus.SUCCESS
            else -> ReceiptBankStatus.UNKNOWN
        }
        val income = Regex("\\b(recibiste|recibido de|has recibido|received from|you received)\\b").containsMatchIn(text)
        val expense = Regex("\\b(enviaste|pagaste|sacaste|you sent|you paid|sent to)\\b").containsMatchIn(text)
        val direction = when { income && !expense -> MoneyDirection.INCOME; expense && !income -> MoneyDirection.EXPENSE; else -> null }
        val currencies = buildSet {
            if (Regex("\\bCOP\\b", RegexOption.IGNORE_CASE).containsMatchIn(input)) add("COP")
            if (Regex("\\bUSD\\b|US\\$", RegexOption.IGNORE_CASE).containsMatchIn(input)) add("USD")
        }
        val code = currencies.singleOrNull() ?: if (currencies.isEmpty() && provider == "NEQUI") "COP" else null
        val labelled = mutableListOf<BigDecimal>(); val standalone = mutableListOf<BigDecimal>()
        if (code != null) lines.forEachIndexed { i, line ->
            val n = normalize(line)
            if (Regex("saldo|disponible|balance|telefono|celular|phone|referencia|reference|comprobante|fecha|date|tasa|rate").containsMatchIn(n)) return@forEachIndexed
            if (Regex("^(valor|monto|cuanto|amount|total(?: pagado)?)(?:\\s|:|$)").containsMatchIn(n)) {
                val suffix = line.replace(Regex("^(valor|monto|cuánto|cuanto|amount|total(?: pagado)?)\\s*:?[\\s]*", RegexOption.IGNORE_CASE), "")
                number(suffix, code)?.let(labelled::add)
                if (suffix.isBlank()) lines.getOrNull(i + 1)?.let { number(it, code)?.let(labelled::add) }
            } else if (Regex("^(?:COP|USD|US\\$|\\$)\\s*[\\d.,\\s]+$", RegexOption.IGNORE_CASE).matches(line) &&
                !Regex("saldo|balance|disponible").containsMatchIn(normalize(lines.getOrNull(i - 1).orEmpty()))) number(line, code)?.let(standalone::add)
        }
        val values = (if (labelled.isNotEmpty()) labelled else standalone).distinct()
        val amount = values.singleOrNull()?.let { runCatching { Money.fromDecimal(it, requireNotNull(code)) }.getOrNull() }?.takeIf { it.minorUnits > 0 }
        return ReceiptAnalysis(amount, lines.mapNotNull(::date).distinct().singleOrNull(), direction, status,
            field(lines, "referencia|reference|numero de comprobante|numero de transaccion|transaction (?:id|number)|comprobante")?.takeIf { it.any(Char::isDigit) },
            provider, field(lines, "de|from|origen|nombre de quien envia"), field(lines, "para|to|destino|nombre de quien recibe"))
    }
    private fun field(lines: List<String>, labels: String): String? {
        val regex = Regex("^(?:$labels)(?:\\s*:\\s*|\\s+|$)(.*)$")
        for ((i, line) in lines.withIndex()) {
            val match = regex.matchEntire(normalize(line)) ?: continue
            val suffix = match.groupValues[1]
            if (suffix.isNotBlank()) return line.takeLast(suffix.length).take(160)
            lines.getOrNull(i + 1)?.let { return it.take(160) }
        }
        return null
    }
    private fun number(value: String, code: String): BigDecimal? {
        var n = value.replace(Regex("(?i)COP|USD|US\\$|\\$|\\s"), "")
        if (!Regex("[0-9]+(?:[.,][0-9]+)*").matches(n)) return null
        val dot = n.lastIndexOf('.'); val comma = n.lastIndexOf(',')
        if (dot >= 0 && comma >= 0) {
            val decimal = if (dot > comma) '.' else ','; val group = if (decimal == '.') ',' else '.'
            val parts = n.split(decimal)
            if (parts.size != 2 || parts[1].length !in 1..2 || !Regex("\\d{1,3}(?:${Regex.escape(group.toString())}\\d{3})+").matches(parts[0])) return null
            n = parts[0].replace(group.toString(), "") + "." + parts[1]
        } else if (dot >= 0 || comma >= 0) {
            val sep = if (dot >= 0) '.' else ','; val parts = n.split(sep)
            n = when {
                parts.size == 2 && parts.last().length in 1..2 -> parts.joinToString(".")
                parts.first().length in 1..3 && parts.drop(1).all { it.length == 3 } && (code == "COP" || sep == ',') -> parts.joinToString("")
                else -> return null
            }
        }
        return runCatching { BigDecimal(n) }.getOrNull()
    }
    private fun date(value: String): LocalDate? {
        val text = normalize(value)
        Regex("(?<!\\d)(20\\d{2})[-/年](\\d{1,2})[-/月](\\d{1,2})(?:日)?(?!\\d)").find(text)?.let {
            return validDate(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }
        Regex("(?<!\\d)(\\d{1,2})[/.-](\\d{1,2})[/.-](20\\d{2})(?!\\d)").find(text)?.let {
            return validDate(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        val months = listOf("ene|jan", "feb", "mar", "abr|apr", "may", "jun", "jul", "ago|aug", "sep", "oct", "nov", "dic|dec")
        Regex("(?<!\\d)(\\d{1,2})\\s*(?:de\\s*)?([a-z]+)\\.?\\s*(?:de\\s*)?(20\\d{2})(?!\\d)").find(text)?.let {
            val month = months.indexOfFirst { p -> Regex("^(?:$p)").containsMatchIn(it.groupValues[2]) } + 1
            return validDate(it.groupValues[3].toInt(), month, it.groupValues[1].toInt()) }
        return null
    }
    private fun validDate(y: Int, m: Int, d: Int) = runCatching { LocalDate.of(y, m, d) }.getOrNull()
}
