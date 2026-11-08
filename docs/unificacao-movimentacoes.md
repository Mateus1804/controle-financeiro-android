# Movimentações unificadas

## Objetivo e decisões

Receitas, despesas, recorrências mensais e parcelas são gerenciadas na tela principal e no extrato pelos mesmos componentes e pelo mesmo cadastro. Planejamento mantém somente renda prevista, gráficos, projeções e orçamentos. A periodicidade será somente mensal, conforme resposta do usuário; um mês pode ser pulado ou ter valor/vencimento próprio.

O cadastro oferece Única, Recorrente e Parcelada conforme o tipo. O início da recorrência é seu primeiro vencimento; a data define o dia habitual, conservando a regra de último dia dos meses curtos. Parcelamentos continuam limitados às despesas, conforme a regra atual. Recorrências e parcelas são previsões até confirmação explícita.

A lista mensal combina pagamentos efetivos e previsões, deduplicando pelo vínculo recordId. Os totais, gráficos e CSV continuam usando exclusivamente os lançamentos confirmados. Uma previsão paga nunca gera uma segunda linha pendente. Valores ocultos e alvos de toque permanecem acessíveis.

Editar uma ocorrência altera só seu snapshot; editar esta e as futuras atualiza somente as previsões pendentes a partir da competência escolhida, preservando pagamentos, itens pulados e competências anteriores. Pausar, pular e excluir usam os mesmos controles da lista principal. O formulário antigo de recorrências e os controles de parcelas deixam Planejamento.

## Implementação

- Reutilizar RecordEditor, InstallmentFields, MonthlyIncomeEditor, menus, seletores e APIs de confirmação/revisão existentes.
- Acrescentar composição compartilhada da lista mensal e um controlador único dos diálogos de recorrência.
- Salvar configuração recorrente e classificação fixa em transação única; edição explícita de todas as futuras pode substituir ajustes pendentes posteriores.
- Reutilizar Room 8 e backup V5: nenhuma migração estrutural necessária.

## Validações essenciais

- Cadastro recorrente pela ação Receita/Despesa, confirmação única e totais sem previsão duplicada.
- Edição mensal isolada, edição futura preservando histórico e estado após recriação.
- Planejamento sem formulários/ações duplicados; parcelamento e lançamentos únicos continuam disponíveis.
- Compilação, lint e testes dirigidos de integridade e interface; sem repetir a suíte integral demorada.

## Resultado da validação

- 177 testes do núcleo Kotlin aprovados, sem falhas.
- 10 testes dirigidos de domínio/interface aprovados: receitas e despesas recorrentes pela tela principal, confirmação única, alteração/ignoração mensal, filtros de pagamento entre meses, parcela paga, Planejamento sem gestão duplicada, concorrência e preservação do histórico ao alterar futuras.
- 3 testes adicionais de compatibilidade aprovados: menu de parcela, exclusão de orçamento e edição mensal sobrevivendo à recriação da tela.
- Compilação debug, APK de testes e preview aprovados; lint debug sem erros (16 avisos anteriores/documentados).
- Room permanece na versão 8, com o mesmo esquema exportado, e o backup continua V5. Nenhuma migração ou exclusão de dados foi necessária.

Quando um compromisso de novembro é pago em dezembro, novembro mostra uma referência explícita à quitação; ela não participa do filtro Realizadas de novembro. O pagamento entra nos totais e no filtro Realizadas de dezembro. Alterações futuras substituem ajustes pendentes posteriores, mas nunca pagamentos ou ocorrências já ignoradas.

A revisão independente identificou e corrigiu dois riscos: reclassificação indevida de ocorrências pagas/ignoradas durante edição futura e ação de editar parcelamento indisponível numa parcela paga. A parcela paga oferece edição do pagamento; a gestão do parcelamento continua disponível pelas parcelas em aberto.

A suíte integral não foi repetida, conforme pedido de limitar as validações às essenciais.
