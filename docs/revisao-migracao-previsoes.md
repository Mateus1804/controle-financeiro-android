# Migração dos testes de edição de previsões

Base: `main` em `469d16f`. Esta revisão migra 16 testes dos quatro arquivos abaixo para `createAndroidComposeRule<MainActivity>()`, sem o extra de interface legada e sem montar outra aplicação de testes. `CurrentForecastMigrationActions.kt` apenas dirige os controles existentes de `ForecastApp` e consulta os repositórios reais para verificar centavos, regras, exceções e lançamentos.

Não há alteração de produção, cálculo, persistência, esquema, importação ou backup neste conjunto. Em particular, ele não incorpora a unificação de salário proposta no PR #22.

## Correspondência dos 16 casos

### MonthlyRecurringUiTest — 5 → 5

| Caso anterior | Caso atual | Contrato verificado |
| --- | --- | --- |
| `editingThisMonthSurvivesRecreationAndKeepsTheFollowingMonth` | `editingThisCycleSurvivesRecreationAndKeepsTheFollowingMonth` | Edição de ocorrência sobrevive à recriação da Activity; R$ 75,00 somente no ciclo selecionado, regra e mês seguinte em R$ 50,00; nenhum lançamento criado. |
| `recordedMonthKeepsItsStatusAndCannotBeConfirmedAgain` | `aRecordedExpenseStaysSeparateFromTheRecurringForecast` | Despesa realizada de R$ 62,50 permanece no extrato, previsão independente de R$ 50,00 permanece no planejamento, e o menu da previsão não oferece registro/confirmação de pagamento. |
| `skippedMonthDoesNotOfferAnActivityToggle` | `anIgnoredCycleOffersRestoreAndKeepsTheFollowingMonth` | O contrato atual é ignorar/restaurar a ocorrência. Ignorar zera somente o ciclo atual, mantém R$ 50,00 no próximo e restaurar recupera o valor sem criar lançamento. |
| `pausingAnEarlierMonthPreservesItsOverrideAndLaterConfiguration` | `stoppingFromTheNextCyclePreservesTheEarlierOccurrenceOverride` | A ação atual “Interromper futuras” a partir do próximo ciclo preserva a exceção anterior de R$ 62,50 e sua descrição, sem despesas previstas nos dois ciclos seguintes. A preservação de configurações futuras após pausar uma regra antiga continua coberta no repositório legado. |
| `configurationEditorLoadsTheSelectedRuleAndReplacesFuturePendingRules` | `futureScopeLoadsTheRuleAmountAndReplacesLaterRulesWithoutLosingTheOccurrence` | Trocar de ocorrência para regra carrega R$ 50,00, sem copiar a exceção de R$ 62,50 ou a regra futura de R$ 90,00. Salvar R$ 80,00 substitui regras posteriores, preserva a exceção do ciclo e não cria lançamento. |

### PurchaseManagementUiTest — 3 → 3

| Caso anterior | Caso atual | Contrato verificado |
| --- | --- | --- |
| `unconfirmedPurchaseCanBeCorrectedFromPlanning` | `theWholeInstallmentForecastCanBeCorrectedFromTheDashboard` | Editar a previsão inteira altera título, total de R$ 200,00 para R$ 210,00 e quantidade de duas para três parcelas de R$ 70,00; termina após a terceira e não cria compra/lançamento legado. |
| `recordedHistorySurvivesEditingAndCancellingTheRemainingForecasts` | `recordedHistorySurvivesAdjustingAndStoppingTheRemainingForecasts` | Ajustar a segunda ocorrência para R$ 220,01 não altera a terceira de R$ 200,00. Interromper a partir dela preserva a previsão anterior e a despesa histórica realizada de R$ 190,00, zerando as previsões restantes. |
| `purchaseEditorRequiresExplicitRevealWhenValuesAreHidden` | `installmentEditorRequiresExplicitRevealWhenValuesAreHidden` | O editor atual não expõe total nem parcela com valores ocultos. Revelar explicitamente mostra a parcela de R$ 200,00 sem salvar ou alterar o total de R$ 600,00. |

### ForecastRemovalUiTest — 2 → 2

| Caso anterior | Caso atual | Contrato verificado |
| --- | --- | --- |
| `excludingAnInstallmentUpdatesOnlyTheSelectedForecast` | `ignoringAndRestoringAnInstallmentChangesOnlyTheSelectedForecast` | Ignorar a primeira parcela usa sua chave estável, mantém a segunda em R$ 100,00 e deixa a ocorrência visível como ignorada. Restaurar recupera somente essa parcela; o cronograma termina após a segunda. |
| `excludingARecurringForecastRemovesItsCardAndItsAmountFromPlanning` | `stoppingARecurringForecastPreservesEarlierForecastsAndRecordedExpenses` | Interromper a recorrência retira o cartão e os R$ 50,00 do ciclo atual e futuro, preservando a previsão anterior e a despesa realizada anterior de R$ 45,00. |

### UnifiedMovementsUiTest — 6 → 6

| Caso anterior | Caso atual | Contrato verificado |
| --- | --- | --- |
| `recurringExpenseIsCreatedAndPaidOnceFromTheMainScreen` | `aRealizedExpenseIsEnteredOnceWithoutPayingTheRecurringForecast` | O atalho real registra uma única despesa de R$ 50,00; a previsão recorrente fica independente, sem exceção nem recorrência legada criada. Recriação mantém exatamente um lançamento. |
| `recurringIncomeIsCreatedAndReceivedOnceFromTheMainScreen` | `aRealizedIncomeIsEnteredOnceWithoutReceivingTheRecurringForecast` | O atalho real registra uma única receita de R$ 50,00; a previsão permanece independente, e recriação não duplica o lançamento. |
| `oneMonthCanBeEditedAndIgnoredWithoutChangingTheNext` | `oneCycleCanBeEditedAndIgnoredWithoutChangingTheNext` | Edição para R$ 75,00 e posterior ignorar afetam apenas a ocorrência selecionada; regra e ciclo seguinte continuam em R$ 50,00 e o histórico permanece vazio. |
| `crossMonthPaymentIsAClosedReferenceInsteadOfASecondRealizedMovement` | `aCrossMonthExpenseAppearsOnlyInTheActualStatementMonth` | O extrato real não mostra a previsão do mês anterior nem uma referência de quitação adicional. O lançamento de R$ 50,00 aparece apenas no mês em que ocorreu; o planejamento anterior permanece independente. |
| `paidInstallmentOffersItsPaymentWithoutAnUnavailablePurchaseAction` | `editingAnActualInstallmentExpenseDoesNotEditItsForecastSchedule` | Pelo extrato atual, editar um lançamento de R$ 50,00 para R$ 55,00 mantém seu ID e a previsão de duas parcelas de R$ 50,00; o editor não oferece alteração da compra prevista. |
| `planningKeepsAnalysisWithoutDuplicatedMovementManagement` | `projectionsKeepAnalysisSeparateFromActualMovementManagement` | A tela atual de projeções mostra margem de −R$ 50,00 da previsão e não oferece pagamento de previsão. A despesa realizada de R$ 25,00 permanece somente no extrato. |

## Limites e compatibilidade financeira anterior

Os fluxos legados “Registrar pagamento/recebimento”, exclusão definitiva de parcela, cancelamento de compra e pausar/retomar recorrência não são oferecidos pelo planejamento atual. Os novos testes exercitam as ações existentes: editar ocorrência ou regra, ignorar/restaurar, interromper futuras e registrar/editar lançamentos realizados independentemente. Não se reproduziu a aplicação antiga para manter esses fluxos como contrato de interface.

A compatibilidade de dados anteriores continua nos testes existentes e não modificados:

- `MonthlyRecurringRepositoryTest`: valores/datas por competência, regras futuras, estados pagos e ignorados, pausa de regra anterior com configuração posterior preservada, confirmação única e restauração de backup.
- `PurchaseManagementRepositoryTest`: reconstrução da compra antes de confirmação, movimentação de parcela em aberto, preservação de pagamentos ao cancelar e ações concorrentes/revisões obsoletas.
- `ForecastRemovalRepositoryTest`: exclusão de uma parcela sem renumerar as demais, histórico pago preservado, não reativação depois de restaurar backup e validação de exclusões.
- `ForecastRepositoryDataTest` e `ForecastMigrationTest`: importação idempotente, vínculos com lançamentos, regras arquivadas, referências antigas, round-trip de snapshots e abertura do banco anterior sem alterar as linhas legadas.

## Verificação desta revisão

Conferência estática: 16 métodos `@Test`, os quatro arquivos usam a Activity de produção, nenhuma referência a `createLegacyFinanceRule`, nenhum teste ignorado e `git diff --check` sem erros. Compilação e execução instrumentada ficam para a verificação serial da revisão; não foram executados Gradle nem emulador durante esta migração.
