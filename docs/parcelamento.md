# Parcelamento — regras do core

## Escopo da primeira fase

O módulo `core` calcula as parcelas abertas de uma compra, a partir do valor total ou do valor de cada parcela. O cálculo contempla compras novas e compras iniciadas antes do uso do aplicativo.

A entrega desta fase é uma API Kotlin sem dependência de Android. Cadastro na interface, revisão de notificações, persistência, renda mensal, indicador de comprometimento, gráfico e importação de contracheque permanecem nas etapas seguintes. Os esquemas Room, o formato do backup e os lançamentos existentes foram preservados.

## Entradas e resultado

A implementação fica em `core/src/main/kotlin/com/mateuswilliam/financas/core/Installments.kt`.

| Entrada | Significado |
| --- | --- |
| `Installments.fromTotal(totalCents, installmentCount, nextDueDate, paidInstallments, dayOfMonth)` | Divide o valor original da compra em parcelas mensais |
| `Installments.fromInstallment(installmentCents, installmentCount, nextDueDate, paidInstallments, dayOfMonth)` | Calcula o valor original da compra considerando parcelas de valor igual |
| `nextDueDate` | Data da próxima parcela em aberto, e não a data da compra |
| `paidInstallments` | Quantidade de parcelas informadas como já pagas; o padrão é zero |
| `dayOfMonth` | Dia original de vencimento; o padrão é o dia de `nextDueDate` |

As duas funções retornam `InstallmentSchedule`, com o total original, a quantidade total de parcelas, as parcelas já pagas, o dia de vencimento e a lista `pending`. Cada item dessa lista informa o número original da parcela, o vencimento e o valor em centavos. `remainingCents` soma somente os valores ainda abertos.

As datas são civis (`LocalDate`), sem conversão implícita de horário ou fuso. O código reutiliza `Recurrences.followingDate` para manter a mesma regra mensal das recorrências existentes.

## Valores e centavos

Os valores utilizam `Long` em centavos inteiros. O total deve estar entre um centavo e `Money.MAX_CENTS`, e cada parcela precisa permanecer positiva.

Na divisão pelo total, os centavos restantes são distribuídos pelas primeiras parcelas da compra original. Por exemplo, 10.000 centavos em três parcelas resultam em 3.334, 3.333 e 3.333 centavos. Se a primeira já foi paga, permanecem as parcelas 2 e 3, de 3.333 centavos cada: o saldo previsto é 6.666 centavos. O centavo da primeira parcela não é redistribuído.

Na entrada por valor da parcela, o total é esse valor multiplicado pela quantidade total, incluindo as parcelas informadas como já pagas. O produto é validado antes da multiplicação para respeitar o limite monetário e evitar estouro aritmético.

As duas bases de cálculo têm significados diferentes: três parcelas iguais de 3.333 centavos totalizam 9.999 centavos; uma compra de 10.000 centavos exige o ajuste acima. A integração do formulário deverá preservar a base escolhida e apresentar a distribuição antes da confirmação. Não são inferidos juros, tarifas ou condições de uma fatura bancária.

## Compras em andamento e vencimentos

As parcelas pagas são apenas uma informação de entrada. O resultado não cria lançamentos antigos, não confirma despesas e não modifica o extrato. Uma compra com dez parcelas, das quais quatro foram pagas, produz somente as parcelas 5 a 10, começando em `nextDueDate`.

A próxima data deve corresponder ao dia de vencimento ajustado ao mês. Quando o vencimento original é dia 31 e a próxima parcela cai em fevereiro, `dayOfMonth = 31` conserva esse dia para março; informar somente a data de fevereiro adotaria seu dia como padrão. A numeração e a passagem de dezembro para janeiro são preservadas.

## Validação e limites

- De uma a 600 parcelas. O teto é uma proteção técnica para a geração em memória, sem representar condições oferecidas por instituições financeiras.
- Pelo menos uma parcela deve estar aberta; compras totalmente pagas ficam fora deste cadastro de compromissos.
- Quantidade paga não negativa e menor que a quantidade total.
- Vencimento mensal entre os dias 1 e 31, com ajuste para meses curtos e anos bissextos.
- Total suficiente para que cada parcela tenha pelo menos um centavo.
- Datas que ultrapassem o intervalo de `LocalDate` são recusadas, sem retornar uma programação parcial.

As entradas inválidas produzem `IllegalArgumentException` com mensagens em português. O cálculo não acessa banco de dados, relógio do sistema, rede ou preferências.

## Testes

`InstallmentsTest.kt` cobre divisão exata e inexata, preservação de centavos em compras iniciadas, cálculo a partir da parcela, virada de ano, fevereiro, ano bissexto, vencimento original, limites monetários, contagens inválidas e datas extremas.

A verificação pelo ambiente padrão do projeto utiliza:

```bash
./gradlew --no-daemon --max-workers=2 :core:test
```

Esta fase não comprova a integração do parcelamento com a interface, o banco ou o backup, que ainda não foi implementada. Os resultados de execução e as limitações do ambiente ficam no pull request correspondente.
