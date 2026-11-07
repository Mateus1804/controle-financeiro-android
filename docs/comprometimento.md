# Comprometimento mensal — regras do core

## Escopo da segunda fase

O módulo `core` calcula quanto da renda líquida mensal prevista está comprometido com obrigações de despesa fornecidas ao relatório. A renda é uma entrada explícita por mês; não é inferida de notificações nem somada automaticamente a partir das receitas confirmadas.

A segunda fase reuniu cálculos e contratos Kotlin puros. A terceira acrescentou a persistência e a quarta conectou os cadastros à interface. A quinta apresenta o indicador e a rosca em Planejamento. A renda líquida prevista é cadastrada manualmente por mês.

## Entradas e resultado

A implementação está em `core/src/main/kotlin/com/mateuswilliam/financas/core/CommitmentReports.kt`.

- `FixedCommitment(id, label, amountCents, dueDate)`: uma obrigação de despesa identificada, com valor inteiro em centavos e vencimento civil.
- `CommitmentReports.month(commitments, month, incomeCents, healthyLimitBasisPoints = 3000)`: seleciona os compromissos do mês e produz totais, estados, itens, fatias e posição do limite.
- `CommitmentReports.fromInstallments(purchaseId, label, schedule)`: adapta as parcelas abertas produzidas pela [primeira fase](parcelamento.md), mantendo número, valor e vencimento. Cada parcela recebe uma identidade estável derivada da compra e de seu número.

A renda usa o limite monetário já existente e pode ser zero. Cada compromisso deve ter identificador e nome não vazios e valor positivo até `Money.MAX_CENTS`. Identidades repetidas no mesmo mês são recusadas; nomes ou valores iguais com identidades diferentes continuam separados. O mesmo identificador pode reaparecer em outro mês.

`CommitmentReport` mantém a renda usada como base, o total comprometido, o valor livre, o limite, o percentual e o estado do mês. `entries` conserva os itens individuais, seus valores e percentuais da renda; `largestExpense` refere-se ao maior item individual, mesmo quando o grupo Outros é maior. `slices` informa os grupos e suas associações aos itens por `entryIds`.

A montagem dos dados inclui cada obrigação apenas uma vez, mesmo depois de confirmada. Não se deve somar uma previsão e seu lançamento vinculado como despesas diferentes. O adaptador de parcelamento fornece somente previsões abertas; a integração persistida também inclui os compromissos confirmados do mês. Esta API não interpreta receitas, transferências, estornos ou o pagamento de uma fatura para produzir obrigações automaticamente.

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

As chaves distinguem despesas individuais, Outros e Livre; não dependem da ordem de chegada. A legenda segue a ordem da rosca e dá acesso aos itens agrupados em Outros.

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

Itens menores que a resolução angular podem ter arco zero, mas seus centavos, percentual e identificação continuam no relatório textual. Na interface, percentuais positivos inferiores a 0,01% aparecem como `< 0,01%`. A apresentação amplia a precisão quando o arredondamento ocultaria uma diferença para o limite ou para 100%. `exceedsIncome` identifica inclusive um centavo acima da renda.

## Testes e limites da evidência

`CommitmentReportsTest.kt` cobre mês e ano, alteração do limite, estados nos limites exatos, renda zero, agrupamento estrito em 3%, ordenação, Outros maior que um item, fechamento de 360°, marcador nas duas escalas, excesso de renda, frações de centavo, valores elevados, duplicidades e integração com as parcelas abertas.

O comando padrão é:

```bash
./gradlew --no-daemon --max-workers=2 :core:test
```

Os testes de core verificam cálculos e regras de entrada. Migração, restauração e interação com os cadastros possuem testes instrumentados próprios. Resultados executados, limitações do ambiente e estado do CI ficam registrados no pull request de cada fase.


## Terceira fase — persistência e recuperação

O esquema Room 5 acrescenta `monthly_plans`, `purchases`, `fixed_recurring` e `commitments`. A migração 4→5 cria essas tabelas vazias, com índices e chaves estrangeiras, preservando registros, pendências, contas, orçamentos, recorrências e ocorrências existentes. Não há recriação destrutiva ou classificação automática do histórico.

`FinanceRepository.saveMonthlyPlan` grava renda e limite pela chave do mês. Atualizar novembro não altera dezembro ou meses anteriores, e meses sem configuração permanecem sem renda informada. `createPurchase` valida a programação original e insere somente parcelas em aberto, sem criar despesas confirmadas. Uma compra de notificação exige a revisão atual; sua origem é consumida uma única vez, mantendo o pacote original separado da conta escolhida e removendo o texto bruto após a confirmação.

`confirmInstallment` cria a despesa e vincula a parcela na mesma transação. Confirmações repetidas não criam outro lançamento. O relatório usa o valor efetivamente confirmado uma única vez, mantendo o compromisso no mês do vencimento mesmo quando o pagamento ocorreu em outro mês. A edição de uma despesa vinculada atualiza o valor refletido no relatório. Excluir o lançamento ou convertê-lo em receita/transferência desfaz o vínculo: a obrigação volta à previsão original, sem cancelar a compra.

`setFixedRecurring` marca explicitamente uma recorrência de despesa a partir do mês escolhido. Não marca receitas e não altera as recorrências legadas por padrão. Vencimentos já confirmados são reaproveitados sem criar outro registro. Vencimentos pulados não entram no comprometimento. Confirmados mantêm uma fotografia do compromisso, preservada após mudanças no plano; desmarcar a opção interrompe as projeções futuras e conserva os compromissos confirmados anteriores.

`commitmentReport` lê configuração, obrigações e lançamentos na mesma transação. O carregamento dos registros vinculados utiliza consultas em lotes limitados, sem varrer o extrato inteiro para cada parcela. Cálculos e acesso ao banco ocorrem fora da thread principal.

Os contratos persistidos usam anos de 1 a 9999, mantendo datas civis canônicas com ano de quatro dígitos para as consultas indexadas. As funções matemáticas de baixo nível das fases anteriores continuam independentes desse formato de armazenamento.

O [backup de dados versão 2](backup.md) inclui todas as estruturas e vínculos novos, aceita o formato anterior e confere o conteúdo em banco temporário antes da substituição. A migração do banco instalado e a leitura de backups são verificações separadas. Os testes `CommitmentMigrationTest`, `CommitmentRepositoryTest` e `CommitmentBackupTest` utilizam somente dados fictícios, na API de teste configurada no CI.

Na terceira fase, o ajuste visual ficou restrito às contagens novas da prévia protegida de restauração. A quarta fase utiliza as tabelas e o formato de backup já existentes.

Datas legadas de recorrência são preservadas no mês de `nextDate`, mesmo quando seu dia difere do dia mensal configurado; os meses seguintes usam o dia configurado. A correção de uma movimentação recorrente de receita para despesa recompõe seu vínculo financeiro na mesma transação, mantendo o relatório e o backup consistentes.

## Quarta fase — cadastros no aplicativo

- Em **Planejamento**, selecione o mês e use **Informar renda e limite**. O valor corresponde à renda líquida esperada após descontos. O limite de 30% é apenas a referência inicial, aceita ajuste com até duas casas decimais e fica salvo naquele mês. Informar a renda não registra uma receita no extrato.
- No editor de uma despesa nova ou na revisão de uma notificação, ative **Compra parcelada no cartão de crédito**. A revisão começa pelo valor total sugerido, que é editável. A base de cálculo pode ser o total original ou o valor de cada parcela. Para uma compra em andamento, informe a quantidade já paga e confira o próximo vencimento; em meses curtos, o dia habitual pode ser preservado. A prévia mostra os valores e o intervalo das parcelas em aberto.
- Em **Parcelas do mês**, confira os vencimentos da competência selecionada. **Conferir e registrar parcela** permite revisar o valor e a data efetiva e criar uma despesa vinculada. Salvar a compra cria apenas as previsões; não cria pagamentos retroativos nem soma valores ao extrato.
- Nas recorrências de despesa, **Incluir como despesa fixa** usa o mês selecionado como início. Desmarcar interrompe as projeções e conserva o que já foi confirmado. Se a previsão mudar para receita, a marcação é retirada; uma futura volta a despesa exige nova inclusão explícita.

A ocultação de valores alcança renda, limite e valores das parcelas. É possível revelar os campos temporariamente para edição. Nenhuma sugestão é consumida se a revisão estiver desatualizada ou for cancelada. A quarta fase foi dedicada aos cadastros; edição e cancelamento de compras já cadastradas e faturas bancárias ficaram fora dessa entrega.

## Quinta fase — indicador em Planejamento

O card **Comprometimento da renda** acompanha o mês selecionado. Mostra o percentual comprometido, o valor das parcelas e despesas fixas, a renda líquida prevista, a parte ainda não comprometida ou o valor acima da renda e o estado em relação ao limite pessoal. A parte livre corresponde apenas à diferença para essas obrigações; outras despesas do extrato não entram nessa projeção.

A rosca usa os ângulos calculados pelo core. O traço indica o limite escolhido na mesma escala da rosca. A legenda apresenta nome, valor e percentual da renda em texto, com o mesmo identificador visual da fatia. **Ver itens de Outros** revela os compromissos agrupados. A descrição do gráfico informa a proporção comprometida e o maior gasto individual. Em telas estreitas, com fonte ampliada ou com números longos, o total e o percentual ficam abaixo do anel.

Sem renda positiva, o card mantém o valor dos compromissos e oferece o cadastro de renda e limite. A rosca e os percentuais ficam indisponíveis nesse estado. Ao ocultar valores, o card esconde os montantes, percentuais, fatias, posição do limite, estado e legenda, inclusive nas descrições de acessibilidade.

O relatório é recalculado ao trocar de mês ou quando o banco muda as informações usadas no cálculo. A consulta usa a mesma transação que reúne previsões e lançamentos vinculados, sem contar uma obrigação duas vezes. O esquema Room e o formato de backup permanecem os mesmos.
