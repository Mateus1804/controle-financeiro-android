# Comprometimento mensal — regras do core

## Escopo da segunda fase

O módulo `core` calcula quanto da renda líquida mensal prevista está comprometido com obrigações de despesa fornecidas ao relatório. A renda é uma entrada explícita por mês; não é inferida de notificações nem somada automaticamente a partir das receitas confirmadas.

A entrega reúne cálculos e contratos Kotlin puros. Persistência da renda e do limite, montagem dos dados a partir do banco, formulários, card, desenho da rosca e importação de PDF permanecem nas próximas fases. Não há alteração de Room, backup, consentimento, segurança ou lançamentos.

## Entradas e resultado

A implementação está em `core/src/main/kotlin/com/mateuswilliam/financas/core/CommitmentReports.kt`.

- `FixedCommitment(id, label, amountCents, dueDate)`: uma obrigação de despesa identificada, com valor inteiro em centavos e vencimento civil.
- `CommitmentReports.month(commitments, month, incomeCents, healthyLimitBasisPoints = 3000)`: seleciona os compromissos do mês e produz totais, estados, itens, fatias e posição do limite.
- `CommitmentReports.fromInstallments(purchaseId, label, schedule)`: adapta as parcelas abertas produzidas pela [primeira fase](parcelamento.md), mantendo número, valor e vencimento. Cada parcela recebe uma identidade estável derivada da compra e de seu número.

A renda usa o limite monetário já existente e pode ser zero. Cada compromisso deve ter identificador e nome não vazios e valor positivo até `Money.MAX_CENTS`. Identidades repetidas no mesmo mês são recusadas; nomes ou valores iguais com identidades diferentes continuam separados. O mesmo identificador pode reaparecer em outro mês.

`CommitmentReport` mantém a renda usada como base, o total comprometido, o valor livre, o limite, o percentual e o estado do mês. `entries` conserva os itens individuais, seus valores e percentuais da renda; `largestExpense` refere-se ao maior item individual, mesmo quando o grupo Outros é maior. `slices` informa os grupos e suas associações aos itens por `entryIds`.

A montagem dos dados nas fases seguintes deve incluir cada obrigação apenas uma vez, mesmo depois de confirmada. Não se deve somar uma previsão e seu lançamento vinculado como despesas diferentes. O adaptador de parcelamento fornece somente previsões abertas; compromissos confirmados do mês também precisarão ser incluídos pela integração com a persistência. Esta API não interpreta receitas, transferências, estornos ou o pagamento de uma fatura para produzir obrigações automaticamente.

## Limite e estados

O limite usa pontos-base: 3.000 representam 30%, e o intervalo aceito é de 0 a 10.000, equivalente a 0% a 100%. O valor inicial é apenas uma referência editável, não uma regra universal nem orientação financeira individual.

| Condição | Estado |
| --- | --- |
| Renda zero | Informe a renda mensal |
| Renda positiva e nenhum compromisso | Confortável |
| Comprometimento abaixo de 80% do limite escolhido | Confortável |
| Comprometimento de 80% do limite até o limite, inclusive | Atenção |
| Comprometimento maior que o limite | Acima do limite |

A faixa de atenção representa proximidade do limite definido pelo usuário. Não é um segundo percentual da renda configurado independentemente. Com limite zero, qualquer compromisso positivo fica acima do limite; a ausência de compromissos permanece confortável quando há renda.

Os estados são comparados por produtos inteiros exatos, sem depender de um percentual arredondado. Um centavo acima do limite é identificado como excesso mesmo quando a apresentação com duas casas decimais ocultaria essa diferença.

Com renda zero, os valores das obrigações são mantidos, mas percentuais e marcador são nulos e não há fatias. Não são produzidos infinito, NaN ou uma renda substituta.

## Agrupamento e ordenação

O corte de 3% compara o valor de cada item com a renda, antes de arredondamentos. Somente os itens estritamente menores entram em Outros; exatamente 3% permanece individual.

Depois do agrupamento, as fatias de despesa são ordenadas do maior para o menor valor, com desempate por chave estável. Outros pode aparecer antes de uma despesa individual se seu total for maior. Os membros permanecem acessíveis em `entries` e `entryIds`. A fatia Livre, quando positiva, fecha a sequência.

As chaves distinguem despesas individuais, Outros e Livre; não dependem da ordem de chegada. A seleção das cores e os componentes de legenda serão implementados na fase de interface.

## Ângulos e escala

Os ângulos são inteiros em micrograus: 1.000.000 de unidades representam um grau. A primeira fatia começa em -90.000.000, no topo, e as próximas avançam no sentido horário.

O círculo completo tem 360.000.000 de unidades. Os restos da divisão são distribuídos pelas maiores frações, com desempate pela ordem das fatias. A soma dos arcos fecha exatamente o círculo; somente a interface converterá essas unidades para a API de desenho.

| Situação | Escala da rosca |
| --- | --- |
| Gastos menores que a renda | Renda mensal; Livre representa a diferença |
| Gastos iguais à renda | Círculo preenchido, sem Livre |
| Gastos maiores que a renda | Total dos gastos; fatias proporcionais entre si, sem Livre |

Os percentuais dos itens e do centro sempre usam a renda, inclusive acima de 100%. O marcador representa o limite escolhido na mesma escala usada pelos arcos. Quando os gastos ultrapassam a renda, sua posição é ajustada para continuar representando o valor correto, em vez de marcar uma fração incorreta do total dos gastos.

`limitCents` apresenta a parte inteira, em centavos, do limite aplicado à renda. O marcador e a classificação usam o produto exato, inclusive quando o limite equivale a uma fração de centavo. Sua projeção angular é truncada somente na unidade de micrograu.

Percentuais usam `BigDecimal` com precisão DECIMAL128, sem teto de 100%; centavos continuam em `Long`, com soma verificada. Produtos auxiliares usam `BigInteger` para evitar estouro em proporções elevadas. Nenhum cálculo financeiro utiliza `Float` ou `Double`. As conversões dos quocientes usam os limites demonstrados do círculo e da renda, sem chamadas a `BigInteger.intValueExact` ou `longValueExact`, que exigem [API 31](https://developer.android.com/reference/java/math/BigInteger#intValueExact()). Isso preserva a API mínima 26 do projeto sem novas dependências.

Itens menores que a resolução angular podem ter arco zero, mas seus centavos, percentual e identificação continuam no relatório textual. Ao integrar a interface, percentuais mínimos e excessos muito pequenos devem ser apresentados sem sugerir zero ou igualdade: `exceedsIncome` identifica inclusive um centavo acima da renda. A descrição para leitores de tela e a ocultação de valores serão verificadas na fase da interface.

## Testes e limites da evidência

`CommitmentReportsTest.kt` cobre mês e ano, alteração do limite, estados nos limites exatos, renda zero, agrupamento estrito em 3%, ordenação, Outros maior que um item, fechamento de 360°, marcador nas duas escalas, excesso de renda, frações de centavo, valores elevados, duplicidades e integração com as parcelas abertas.

O comando padrão é:

```bash
./gradlew --no-daemon --max-workers=2 :core:test
```

As regras não produzem telas nem escrevem dados. Os testes de core não comprovam migração, restauração, TalkBack, aparência do gráfico ou edição de preferências: essas integrações ainda não foram implementadas. Resultados executados, limitações do ambiente e estado do CI ficam registrados no pull request da fase.
