package com.mateuswilliam.financas.core

object ForecastValidation {
    fun error(rule: ForecastRule): String? = when {
        rule.itemId.isBlank() || rule.itemId.length > (if (rule.legacySource != null) 320 else 200) || '\u0000' in rule.itemId -> "Identificador inválido."
        rule.title.isBlank() || rule.title.length > (if (rule.legacySource != null) 256 else 200) || '\u0000' in rule.title -> "Informe uma descrição de até 200 caracteres."
        rule.amountCents == 0L || rule.amountCents !in -Money.MAX_CENTS..Money.MAX_CENTS -> "Informe um valor maior que zero e dentro do limite."
        rule.amountCents < 0 && rule.legacySource.isNullOrBlank() -> "Valores negativos são reservados a estornos legados."
        rule.dayOfMonth !in 1..31 -> "Informe um dia entre 1 e 31."
        rule.frequency == ForecastFrequency.INSTALLMENTS && (rule.installmentCount !in 1..Installments.MAX_INSTALLMENTS || rule.amountCents < rule.installmentCount) -> "Informe parcelas válidas com pelo menos um centavo cada."
        rule.cycleAnchored && rule.frequency != ForecastFrequency.MONTHLY -> "Consumo sem vencimento deve ser mensal."
        rule.statementKey.length > 200 || '\u0000' in rule.statementKey -> "Vínculo da fatura inválido."
        rule.legacySource != null && (rule.legacySource.length > 512 || '\u0000' in rule.legacySource) -> "Origem legada inválida."
        rule.statementTotal && rule.statementKey.isBlank() -> "Informe o vínculo da fatura."
        rule.account.length > 80 || '\u0000' in rule.account -> "Use até 80 caracteres para a conta."
        else -> null
    }

    fun reserveEventError(kind: ReserveEventKind, cents: Long, balance: Long?, availableCents: Long? = null): String? = when {
        cents !in 0..Money.MAX_CENTS || (kind != ReserveEventKind.SET && cents == 0L) -> "Informe um valor válido."
        kind == ReserveEventKind.ADD && balance == null -> "Informe primeiro o saldo inicial da reserva."
        kind == ReserveEventKind.WITHDRAW && (balance == null || cents > balance) -> "A retirada não pode superar o saldo da reserva."
        kind == ReserveEventKind.ADD && availableCents != null && cents > maxOf(0L, availableCents) -> "O aporte não pode superar a margem ainda não destinada."
        kind == ReserveEventKind.ADD && balance != null && cents > Money.MAX_CENTS - balance -> "O saldo ultrapassa o limite monetário."
        else -> null
    }
}
