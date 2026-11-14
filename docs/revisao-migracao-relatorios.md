# Revisão da migração dos testes de previsões e relatórios

Escopo: 13 testes instrumentados em `CommitmentEntryUiTest` (5),
`CommitmentReportUiTest` (5), `CommitmentJourneyTest` (1) e
`CompactUiInteractionTest` (2). Todos passam a abrir `MainActivity` pela regra
`createAndroidComposeRule<MainActivity>()`, sem extras ou opção de interface.
`ForecastReportTestDriver` apenas aciona a interface que a Activity entrega;
não monta um host alternativo nem replica o `ForecastApp`.

## Contratos atuais

Previsões pertencem ao orçamento por ciclos. Criar uma previsão parcelada não
cria compra antiga, obrigação ou lançamento realizado. O editor recebe o total
e a quantidade e distribui os centavos restantes nas primeiras parcelas.
Receitas mensais podem receber uma alteração só no ciclo selecionado; os ciclos
seguintes mantêm a regra original. Ignorar/restaurar afeta uma ocorrência;
interromper futuras preserva o histórico anterior.

Uma notificação é revisada como lançamento realizado. Sua versão precisa ser
aceita antes de confirmar a edição, e a confirmação elimina o texto de origem.
Um pagamento realizado permanece separado da previsão: ele não confirma uma
parcela nem muda automaticamente o orçamento previsto.

A interface antiga de parcelas já pagas, renda com limite saudável e limites
mensais por categoria foi abandonada. Os testes usam os contratos acima e não
reintroduzem essas telas. No produto atual, o percentual fica nas projeções,
com duas casas decimais, e o orçamento agrupa gastos por categoria. Não há
rosquinha, agrupamento especial de parcelas pequenas em “Outros”, legenda de
renda livre nem rótulos de limite saudável. A margem em centavos permanece
exata, inclusive quando o percentual arredonda um excesso de um centavo.

## Mapeamento dos 13 cenários

| Origem | Substituição exercitada na interface atual |
| --- | --- |
| Entrada: compra em andamento com seis parcelas e duas pagas | Nova previsão parcelada de 60001 centavos em seis ocorrências, chaves estáveis, distribuição 10001 + 5 × 10000, término, nenhuma linha antiga/lançamento criado e estado reaberto. As parcelas antigas já pagas continuam cobertas na camada de dados. |
| Entrada: revisão de notificação e base por parcela | Revisão real de notificação exige aceitar a nova versão; valor editado de 13500 centavos persiste, texto de origem é apagado e nenhuma previsão é criada. A distribuição do total está no cenário anterior. |
| Entrada: renda e limite fracionário restritos ao mês | Alteração de receita para 325019 centavos só no ciclo selecionado, preservando 300000 no seguinte, inclusive após recriar a Activity. O limite fracionário antigo permanece nos testes de formulários e repositório. |
| Entrada: incluir/remover apenas recorrências de despesa | Ignorar e restaurar despesa mensal de 65000 centavos só nesta ocorrência, preservando receita de 100000 e a despesa do ciclo seguinte; nenhum lançamento realizado. |
| Entrada: renda oculta até revelar no editor | Valor de previsão de receita ausente dos nós e descrições; só aparece no campo após revelar explicitamente e é salvo sem perder os 325019 centavos. |
| Relatório: mês selecionado, contas pequenas e parcela vinculada | Categorias atuais, despesas previstas e percentuais do ciclo; ajuste de ocorrência muda só a previsão. Lançamento avulso realizado permanece separado. Temas claro e escuro. |
| Relatório: gasto acima da renda | Percentual real de 125,00% e margem negativa exata de 25000 centavos nas projeções, sem limitar a 100%. |
| Relatório: privacidade de valores/geometria | Percentuais, valores e semântica de progresso somem ao ocultar; categorias do orçamento deixam de revelar geometria. |
| Relatório: ausência de renda | Despesa de 9500 centavos continua prevista, projeção informa renda ausente e a ação atual permite prever receita. |
| Relatório: fonte ampliada e excesso exato | Escala 1,6: margem negativa de um centavo continua legível e sem overflow; o percentual segue o arredondamento atual de duas casas. |
| Jornada: renda, pagamento, ajuste, cancelamento e restauração | Receita e parcelas atuais; despesa realizada independente de 22000; alteração de ocorrência para 22500; privacidade; interrupção das próximas parcelas com histórico preservado; backup criptografado restaura regras/ajustes/ciclo/lançamento e estado reaberto. |
| Interação compacta: menu de parcelas | Menu atual tem descrição acessível e alvo de 48 dp, abre edição de previsão; cancelar não escreve nem perde os 10000 centavos da ocorrência. |
| Interação compacta: confirmação para remover limite | Dispensar o menu atual preserva 25000 centavos; escolher “Interromper futuras” retira ciclo atual e próximos, preserva os 25000 históricos e persiste após recriar a Activity. O produto atual executa essa ação pelo menu, sem diálogo de confirmação. |

## Compatibilidade anterior preservada

A retirada das telas antigas dos testes de UI não remove testes dos dados:

- `CommitmentRepositoryTest`: compras em andamento, confirmação única com valor
  real, vínculos e exclusão/conversão de registros, inclusão de recorrência
  fixa, revisão de notificação, renda/limite por mês e fechamento/reabertura do
  banco.
- `CommitmentBackupTest` e `CommitmentMigrationTest`: formatos antigos,
  parcelas pagas/ajustadas/canceladas, integridade dos vínculos, rollback e
  preservação de tabelas anteriores.
- `ForecastRepositoryDataTest` e `ForecastMigrationTest`: importação única,
  histórico/vínculos/ajustes preservados, referências de renda sem soma
  automática, reabertura e restauração dos dados atuais e antigos.
- `ForecastRemovalRepositoryTest`: exclusões e confirmações concorrentes,
  histórico realizado e compatibilidade dos backups antigos.
- `CommitmentEntryTest`, `CommitmentPlanningTest`, `CommitmentReportsTest` e
  `CommitmentReportTextTest` no módulo core: centavos, parcelas já pagas,
  limites fracionários e precisão do cálculo e dos textos antigos.

Nenhum teste foi ignorado ou removido da contagem. Nenhuma alteração em
produção, core, banco, backup ou regras financeiras faz parte desta migração.

## Verificação e riscos

A revisão estática confere os seletores contra `ForecastApp`, `ForecastEditor`,
`ForecastOverview`, `ForecastProjectionScreen` e `RendlyCategories`; confere
nomes e campos contra os modelos atuais. A conferência de categoria associa
o valor exato ao rótulo pelas posições reais na mesma linha, evitando que a
árvore semântica achatada aceite o valor de outra categoria. `git diff --check` é a verificação
local autorizada neste trabalho. Gradle, emulador e testes instrumentados não
foram executados por este agente; a execução serial fica com a integração.

Pontos a confirmar nessa execução: rolagem dos diálogos de seleção, atualização
assíncrona do Room/ViewModel após restore, restauração da escala de fonte em
`finally` e semântica de medição com fonte ampliada. Screenshots e ausência de
overflow são evidência de execução somente quando o teste realmente rodar.
