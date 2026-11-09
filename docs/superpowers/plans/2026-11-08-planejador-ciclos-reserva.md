# Planejador por ciclos e reserva — implementação

**Spec:** `docs/superpowers/specs/2026-11-08-planejador-ciclos-reserva.md`.

**Objetivo:** previsões como fluxo principal, salário cadastrado uma vez, todos os gastos no percentual, ciclo configurável e reserva separada. Sem confirmação de pagamento/recebimento. Kotlin/Compose/Room existentes, centavos inteiros, nenhum serviço bancário novo.

## Arquitetura e decisões

Uma base de regras de previsão versionadas substitui as fontes paralelas do orçamento. O histórico legado permanece intacto nas tabelas atuais. Recorrências e parcelas já existentes são aproveitadas com procedência estável; registros vinculados não são importados novamente. Rendas de referência antigas continuam preservadas e disponíveis para reaproveitamento explícito, sem gerar receitas automaticamente. A nova interface não edita as fontes antigas em paralelo.

Datas usam intervalos `[start, endExclusive)`. Primeiro ajuste do dia define o ciclo inicial; mudanças posteriores entram na próxima fronteira, com transição explícita, sem mudar ciclos anteriores. Dias 29–31 são ajustados ao mês. Encerramento é temporal/lógico: nenhuma transação financeira é gerada. Edição somente da ocorrência usa override com chave estável; edição futura cria uma regra com vigência, preservando as anteriores.

Reserva usa eventos SET/ADD/WITHDRAW e meta opcional. Somente ADD explicitamente vinculado à sobra de um ciclo reduz sua margem ainda não destinada; não altera gasto ou percentual. Retirada geral não recria renda nem desfaz automaticamente destinações passadas. Não há aportes ou cobertura de déficit automáticos.

## Contratos compartilhados (nomes vinculantes)

Criar no core `ForecastModels.kt`:

```
enum ForecastKind { INCOME, EXPENSE }
enum ForecastFrequency { ONCE, MONTHLY, INSTALLMENTS }
enum ForecastGroup { COMMITMENT, VARIABLE }
data class ForecastRule(itemId:String, effectiveFrom:LocalDate, title:String,
 kind:ForecastKind, amountCents:Long, category:Category, frequency:ForecastFrequency,
 startDate:LocalDate, dayOfMonth:Int=startDate.dayOfMonth, installmentCount:Int=1,
 cycleAnchored:Boolean=false, group:ForecastGroup=COMMITMENT, estimated:Boolean=false,
 active:Boolean=true, statementKey:String="", statementTotal:Boolean=false,
 account:String="", legacySource:String?=null)
data class ForecastOverride(itemId:String, occurrenceKey:String, amountCents:Long?=null,
 date:LocalDate?=null, ignored:Boolean=false, active:Boolean?=null,
 title:String?=null, category:Category?=null)
data class CycleRule(effectiveStart:LocalDate, day:Int)
data class BudgetPeriod(start:LocalDate,endExclusive:LocalDate)
enum ReserveEventKind { SET, ADD, WITHDRAW }
data class ReserveEvent(id:Long,kind:ReserveEventKind,amountCents:Long,createdAt:Long,
 reason:String,cycleStart:LocalDate?=null)
data class ForecastDataset(rules:List<ForecastRule>=emptyList(),
 overrides:List<ForecastOverride>=emptyList(),cycleRules:List<CycleRule>=emptyList(),
 reserveEvents:List<ReserveEvent>=emptyList(),reserveGoalCents:Long?=null)
```

`ForecastRule.amountCents` é total da compra para INSTALLMENTS (distribuir centavos exatamente), e valor de cada ocorrência para MONTHLY. Valores negativos somente em ajustes legados explícitos de estorno, mantendo a natureza. Campos de conta são opcionais e nunca exigidos no fluxo principal.

Chaves de ocorrência: `once`, `month:YYYY-MM`, `installment:N`, `cycle:YYYY-MM-DD` (monthly cycleAnchored). Regras e overrides de um mesmo item não duplicam ocorrências. Mensal cycleAnchored representa estimativa sem data e gera uma linha por ciclo; datas calendarizadas são filtradas pelo intervalo. Ao alterar configuração de ciclo, estimativas sem data do período anterior continuam vinculadas ao seu período.

Core produz:
```
CycleCalendar.periodContaining(date:LocalDate,rules:List<CycleRule>):BudgetPeriod
ForecastCalculator.report(dataset:ForecastDataset,period:BudgetPeriod):ForecastReport
ForecastCalculator.reserveBalance(events:List<ReserveEvent>):Long?
ForecastValidation.error(rule:ForecastRule):String?
ForecastLine(itemId,occurrenceKey,title,date,amountCents,kind,category,group,
 frequency,estimated,ignored,included,statementKey,statementTotal,rule)
ForecastReport(period,lines,incomeCents,commitmentsCents,variableCents,totalExpensesCents,
 marginCents,expensePercentage:BigDecimal?,allocatedCents,unallocatedCents,reserveBalanceCents:Long?)
```

`included=false` identifica ignoradas e componentes já incluídos numa fatura agregada. Ambas podem ser vistas com motivo, mas não somadas. Somente vínculo explícito `statementKey` permite excluir componentes; não inferir fatura pelo banco/Pix. Não criar fatia negativa. Percentage sem clamp; sem renda positiva retorna null.

Repositório separado `ForecastRepository(database)` (mesmo Room e mesma transação) expõe:
```
val dataset:Flow<ForecastDataset>
suspend initialize() // aproveitamento legado idempotente
suspend saveRule(rule:ForecastRule,replaceFuture:Boolean=true)
suspend saveOverride(override:ForecastOverride)
suspend setCycleDay(day:Int,today:LocalDate)
suspend addReserveEvent(kind:ReserveEventKind,cents:Long,reason:String,
 cycleStart:LocalDate?=null)
suspend setReserveGoal(cents:Long?)
```

Reservas: SET inicial/correção explícita; WITHDRAW não pode superar saldo. ADD vinculado ao ciclo não pode superar sua margem positiva ainda não destinada; validar atomicamente e recomputar relatório. Saldo desconhecido é null, zero precisa ser explícito. Dados do repositório devem formar um snapshot atômico.

## Tarefas e donos

- [x] Core: implementar modelos, calendário, motor puro, cálculo da reserva, validação e testes de fronteiras/centavos/percentuais, histórico, faturas, salário e simulação por dataset temporário.
- [x] Persistência: entidades/DAO Room10, migração9→10, repositório, importador legado idempotente, backupV7 compatível1–6 e wiring de migrações. Preservar fontes, snapshots e vínculos originais. Testes essenciais de migração/importação/backup/reabertura.
- [x] Editor: formulário único para regra/ocorrência, conta opcional, frequência e tipo corretos, valor estimado, consumo sem data, fatura explícita e alcance mensal/futuro. Dialogs de ciclo e reserva reutilizando MoneyAmountField/ChoiceField. Templates sem valores.
- [x] Integração: ViewModel/entrada principal, resumo e gráfico total, lista contextual sem quitação, histórico e próximos períodos, reserva/destinação, simulador sem mutação, ajustes/backup/dados legados acessíveis. Lembretes informativos.
- [x] Validar localmente: core, assemble/lint e cenários instrumentados essenciais; revisão independente; corrigir falhas e atualizar documentação.
- [ ] Entrega: atualizar PR17 e disponibilizar o APK preview e os artefatos finais; conferir o resultado remoto da suíte e da atualização completa por APK/backup no CI.

## Verificações essenciais

300000 renda;120000 contas;40000 parcelas;60000 consumo →220000 gastos,73.33%,80000 margem. Ajuste renda285000 somente no ciclo →65000 margem e próximo300000. Dia5 inclui05/11 e02/12, exclui05/12; virada/fevereiro/29–31/transição sem gaps. Vencimento passado não altera total. Parcela termina e reduz futuro. Fatura total e componentes não duplicam. Reserva SET200000,ADD50000 do ciclo80000 →250000 reserva,30000 não destinado;gastos e percentual inalterados. Retirada/SET não criam renda, déficit não consome reserva. Snapshot/backup sem perder fontes históricas.

## Registro de execução

Base: `a433c56`, PR17 aberto. Não fazer merge. A instrumentação ampla anterior teve falhas de UI; consultar evidência remota somente quando afetar a mudança, sem repetir baterias amplas sem motivo. Este plano é execução do pedido detalhado autorizado, sem nova rodada de aprovação de design.

Validação local de 09/11/2026: 203 testes do core sem falhas/erros/ignorados; 24 cenários instrumentados distintos aprovados na API 26 (15 novos e 9 regressões). O lote final passou os quatro cenários selecionados, incluindo repetição do salário que não aumenta a contagem distinta. Debug/AndroidTest/preview/lint debug concluíram com sucesso em 2m13s, zero erros e 18 avisos de lint; o rebuild posterior somente de testes concluiu em 26s. As revisões independentes financeiras e de interface encerraram sem problemas acionáveis nos recortes revistos.

A migração 9→10 isolada, reabertura e serialização/restauração de snapshots passaram. A atualização completa pelo APK anterior com backup criptografado não foi repetida localmente; permanece para o CI. O host antigo de interface em debug cobre somente regressões preservadas; as jornadas novas usam a entrada principal real. Resultados e limites: [Validação do planejador](../../validacao-planejador-ciclos-reserva.md).
