# Alcance do cálculo até o salário

O cartão informa que desconta compromissos previstos e deixa de fora gastos variáveis previstos. A opção escolhida foi esclarecer a regra existente. Não foi acrescentado um novo desconto nem uma preferência que altere o cálculo.

A regra permanece: saldo conhecido de contas ativas menos compromissos incluídos, não ignorados, do grupo Compromisso e com data entre hoje e o próximo salário (sem incluir o dia desse salário), menos o valor de reserva já fornecido ao cálculo. Lançamentos representados são excluídos pelo mecanismo existente.

## Problema financeiro encontrado na revisão

A ForecastApp passa reserveGoalCents como savingsCents para PaydayBudgets.compute. Esse campo é a meta total da reserva de emergência, não um aporte planejado para o período. Assim, uma meta de R$ 10.000 pode descontar R$ 10.000 do cartão, mesmo que a intenção seja guardar apenas uma pequena parte neste mês.

O rótulo passa de “Valor a guardar” para “Meta total da reserva” e uma observação explica o desconto real. A regra não foi corrigida silenciosamente: depende de confirmação do proprietário, conforme a restrição desta tarefa. A correção proposta para uma etapa posterior é separar meta total de aporte planejado e definir qual deles deve reduzir a disponibilidade. Este PR não aprova nenhuma dessas regras.

## Verificação

Compilação do app/testes e lint locais; resultado no PR. O teste visual existente mantém os valores esperados em centavos e confere as novas explicações. Instrumentação somente Android 15/API 35. Não foram alterados core financeiro, Room ou backup.
