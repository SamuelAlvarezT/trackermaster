package com.trackermaster.core.domain.money

/** Only a branded, second-person statement can suggest an existing funding account. */
object ReceiptSuggestions {
    fun account(analysis: ReceiptAnalysis, text: String, workspace: MoneyWorkspaceData, draft: ReceiptDraft): Long? {
        val direction = analysis.direction ?: return null
        if (draft.accountId != null || analysis.provider == null || analysis.amount?.currencyCode != draft.currency) return null
        val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
        val brand = ReceiptParser.normalize(lines.firstOrNull().orEmpty())
        val branded = when (analysis.provider) {
            "NEQUI" -> brand == "nequi"
            "NU" -> brand in setOf("nu", "nu colombia", "banco nu")
            else -> false
        }
        if (!branded) return null
        val wording = ReceiptParser.normalize(text)
        val holderEvidence = when (direction) {
            MoneyDirection.EXPENSE -> Regex("\\b(enviaste|pagaste|sacaste|you sent|you paid)\\b")
            MoneyDirection.INCOME -> Regex("\\b(recibiste|has recibido|you received)\\b")
        }
        if (!holderEvidence.containsMatchIn(wording)) return null
        val candidates = workspace.ledger.snapshot.accounts.map { it.account }.filter { account ->
            val name = ReceiptParser.normalize(account.name)
            !account.archived && account.initialBalance.currencyCode == draft.currency && when (analysis.provider) {
                "NEQUI" -> Regex("\\bnequi\\b").containsMatchIn(name)
                "NU" -> Regex("\\bnu\\b").containsMatchIn(name)
                else -> false
            }
        }
        return candidates.singleOrNull()?.id
    }
}
