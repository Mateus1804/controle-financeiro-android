# Migração dos testes de entrada, privacidade e navegação

Escopo: somente testes e este documento, a partir de `469d16f`. `FinanceUiTest` (20 casos) e os seis casos restantes de `RedesignInteractionTest` passam a abrir `MainActivity` com `createAndroidComposeRule<MainActivity>()`, sem extra de seleção de interface. São 26 casos na entrada real `ForecastApp`. Os dois casos de receita/despesa rápida ficam em `RealizedEntryUiTest`, no PR anterior da sequência; não foram duplicados.

A preparação limpa os dados fictícios pelo repositório existente, inicializa o planejador e dispensa o nome opcional, exceto nos casos que exercitam esse diálogo. Os controles de gravação aguardam o estado habilitado. A navegação usa os controles reais `add-realized-expense`, `forecast-settings`, `forecast-dashboard`, `dashboard-notification-review`, `open-notification-review` e `Ver extrato do mês`. Gravações novas de despesa usam `Registrar despesa`; edição e transferência usam `Salvar lançamento`.

Não há alterações em produção, banco, migrações, formato/implementação de backup, cálculos ou regras de centavos. Os testes continuam criando registros e sugestões fictícios pelas APIs já existentes.

## FinanceUiTest: 20 casos

| Caso anterior | Destino e verificações preservadas |
| --- | --- |
| `manualEntryWorksWithoutNotificationAccess` | Mesmo nome. Despesa pelo botão atual, extrato com a descrição e valor exato `4290L`; captura e consentimento continuam desligados. |
| `formattedPasteKeepsItsValueInReais` | Mesmo nome. Colagem `R$ 1.234,56` resulta em `123456L`. |
| `cursorSelectionReplacementAndBackspaceKeepCents` | Mesmo nome. Mesmas operações de seleção, substituição e Backspace; resultado `103L`. |
| `clearingTheAmountDoesNotSaveZeroOrReuseTheOldValue` | Mesmo nome. Apagar não grava, mostra erro e mantém o editor; depois grava `1L`. |
| `existingAmountIsNotScaledAgainWhenEditing` | Mesmo nome. Edição aberta pelo extrato; `4290L` permanece `4290L`. |
| `appearancePersistsAfterActivityRecreationAndUpdatesSystemBars` | Mesmo nome. Claro/escuro/sistema, seleção persistida, ícones das barras e captura de pixels. A aba de ajustes restaurada não é fechada por um segundo toque. |
| `decliningDisclosureKeepsCaptureDisabled` | Mesmo nome. Recusar o diálogo aberto pelo controle de captura mantém o cadastro manual. |
| `screenshotsUseOnlyFictionalRecords` | Mesmo nome. Capturas do resultado mensal, extrato, pendências e editor em ambos os temas. A receita fictícia usa `Category.SALARY` para o resumo mensal atual mostrar `2.811,60`; os valores dos três registros não mudam. A antiga seção de recentes na home não existe; sua leitura fica nas capturas do extrato. IDs dos três registros e a sugestão ainda não confirmada são verificados ao final. |
| `firstUseCanContinueManuallyWithoutConsent` | `optionalNameCanBeSkippedBeforeManualEntryWithoutConsent`: dispensar o nome opcional permite abrir o editor, sem consentir, ativar captura ou gravar registro. |
| `introductionDoesNotReplaceDisclosureConsent` | `savingOptionalNameDoesNotReplaceDisclosureConsent`: salvar o nome atualiza a saudação; ativar captura ainda exige o diálogo específico; recusá-lo mantém captura e consentimento desligados. |
| `closedCaptureNoticeStaysClosedAfterRecreation` | `skippedOptionalNameStaysClosedAfterRecreationWithoutEnablingCapture`: o aviso de captura dispensável não existe na home atual. O diálogo opcional atual permanece dispensado após recriação, com saudação sem nome e sem ativar captura. |
| `accountCanBeAddedWithoutLosingTheAmount` | Mesmo nome. Cadastro de conta dentro do editor mantém `1234L` e a conta `Inter pessoal`; preserva a espera de geometria estável e o diagnóstico da árvore. |
| `amountAndDescriptionSurviveFormRecreation` | Mesmo nome. Recriação preserva o formulário; depois de salvar, confere `1234L` e `Formulário de exemplo`. |
| `chartOpensOnlyTheSelectedExpenseGroup` | Mesmo nome. O mapa de gastos abre o extrato filtrado; a receita com a mesma categoria continua excluída. |
| `rejectedPasteShowsAnErrorAndCannotSaveThePreviousAmount` | Mesmo nome. Colagem inválida `1,234` mantém erro e editor, sem gravar o valor anterior. |
| `largeTextAndOpenKeyboardAllowManualEntry` | Mesmo nome. Fonte 150%, teclado, rolagem, captura e gravação exata de `1234L`; restaura a escala anterior. |
| `landscapeLayoutKeepsTheFormUsable` | Mesmo nome. Paisagem, formulário, captura, confirmação e `1234L`; restaura a orientação solicitada anterior. |
| `hiddenAmountsAlsoHideChartLabelsAndSemantics` | Mesmo nome. Usa o controle atual `Ocultar valores`; o valor e o percentual não aparecem em texto nem na descrição acessível. |
| `notificationAccessRefreshesAfterResumeAndRevocationAcrossAlertPermissions` | Mesmo nome. Preserva concessão/revogação real do listener, permissão runtime de alertas, retorno à Activity e restauração da autorização anterior no `finally`. O controle de recuperação atual fica em Ajustes (`Autorizar no Android`), também acessível pela revisão da home; não depende do antigo aviso da home. |
| `choosingManualPausesPreviouslyEnabledCapture` | `pausingCaptureKeepsConsentAndManualEntryAvailableAfterRecreation`: o controle atual de captura permite pausar, mantendo consentimento. A pausa persiste na recriação e o editor manual continua acessível. |

## RedesignInteractionTest: seis casos, mais dois já movidos

| Caso anterior | Destino e verificações preservadas |
| --- | --- |
| `quickIncomeStartsTheCorrectMovementAndKeepsIntegerCents` | Movido ao PR anterior em `RealizedEntryUiTest`; removido daqui para não duplicar. |
| `quickExpenseStartsTheCorrectMovement` | Movido ao PR anterior em `RealizedEntryUiTest`, com valor e tipo verificados; removido daqui para não duplicar. |
| `quickTransferShowsDestinationAndCannotSaveWithoutOne` | `transferSelectedInTheEditorShowsDestinationAndCannotSaveWithoutOne`: abre o editor real e escolhe `Transferência` em `Movimentação`. A conta de destino é exibida; sem ela, a tentativa mostra erro e não grava. Não recria o atalho antigo. |
| `pendingAndEditorConcealCurrencyFromTheOriginalNotification` | Mesmo nome. Texto da sugestão e editor ocultam `42,90` em texto e descrição acessível; o campo exige revelação explícita e nenhum registro é criado. |
| `navigationKeepsFourReadableDestinationsAndTouchTargets` | `plannerNavigationAndReviewSettingsKeepReadableTouchTargets`: exercita `Orçamento`, `Projeções`, `Reserva`, revisão e Ajustes reais, preservando alvos de ao menos 48dp. |
| `narrowScreenWithDoubleFontScaleKeepsActionsAndNavigationReachable` | Mesmo nome. Tela de 320dp, fonte 200%, navegação atual, revisão/Ajustes, botões de receita/despesa, temas e teclado. Gravação verifica `1234L`, tipo e descrição; restaura tamanho, densidade e fonte anteriores. |
| `introductionHasManualChoiceAndKeepsExplicitConsentInBothThemes` | `optionalNameCanBeSkippedAndCaptureStillNeedsConsentInBothThemes`: captura o nome opcional e o diálogo específico de consentimento em ambos os temas; dispensar o nome e recusar a captura não consentem nem ativam captura. |
| `lastTransactionCanBeReadAboveTheFixedActionWithoutOcclusion` | Mesmo nome. Trinta registros fictícios, último item visível e acima do botão atual `add-realized-expense`; captura do extrato ao final. |

## Verificação e limites

Verificação estática deste recorte: contagem dos 26 métodos `@Test`, ausência de `@Ignore`, do host legado e de seletores da navegação antiga; revisão dos seletores e ações contra os componentes atuais; `git diff --check`. Esta etapa não executou Gradle, emulador ou rede. Não é evidência de compilação nem de aprovação de testes.

A validação executável fica concentrada na API 35, serializada pelo responsável pelo PR. Pontos que precisam dessa execução: geometria e toque com fonte 150%/200%, teclado e rotação; árvores de semântica após recriação; atualização assíncrona dos dados; permissão runtime real e reconexão do listener; pixels e capturas. Se houver falhas, os casos e suas verificações devem ser corrigidos sem desativá-los, sem recriar a UI antiga e sem mudar regras financeiras para acomodar o teste.
