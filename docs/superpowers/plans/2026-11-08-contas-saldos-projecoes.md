# Contas, saldos e projeções — implementação

**Objetivo:** integrar contas próprias, transferências, estornos e projeções ao cadastro único existente, preservando os dados.

**Arquitetura:** manter uma transferência como um único lançamento atômico com origem e destino. Derivar saldos de lançamentos realizados e de referências de saldo auditáveis, sem criar receitas fictícias. Referências representam o saldo no início da data escolhida; lançamentos anteriores já estão contemplados. Contas antigas ficam com saldo desconhecido até configuração explícita.

**Tecnologias:** Kotlin, Compose Material 3, Room, módulos atuais; sem novas bibliotecas.

**Restrições:** recorrência apenas mensal; histórico preservado; migração aditiva; gastos fixos opcionais sem valores; renda de referência e orçamento não criam movimentações; somente verificações essenciais.

## Tarefas

- [x] Contas: criar `AccountBalances.kt`, referências de saldo auditáveis, arquivamento, Room 9, backup 6, fluxos e APIs do repositório. Validar referência, transferência, correção e preservação de dados.
- [x] Natureza financeira: categorias próprias de receitas, `originalRecordId` e `reversalMovement` nos estornos; centralizar contribuições de receitas/despesas em Ledger, relatórios e orçamentos. Preservar categorias antigas.
- [x] Interface: painel compacto de contas, cadastro/correção opcional, extrato por conta; prévia de transferência; categorias e estorno no editor comum; configuração opcional de gastos fixos sem valores e campos progressivos.
- [x] Resumos: mesma projeção na tela inicial e Planejamento, realizada + previsões pendentes, sem contabilizar novamente pagamentos, ignoradas ou pausadas.
- [x] Validar: testes de core; migração 8→9, roundtrip de backup e principais fluxos de UI. Compilar APK e lint. Revisão independente do conjunto.
- [x] Entregar: commit, PR sem merge, APK de teste e checklist curto.

## Pontos de revisão

Saldo inicial não altera resultado; transferências conservam total; saldo parcial é identificado quando há contas não configuradas; correções não apagam referências; arquivamento não perde dinheiro/histórico; estorno de receita reduz receita; previsão paga deixa de ser pendência; edição de recorrência identifica mês/futuro.

## Decisões de implementação

Usar nomes normalizados existentes como identidade das contas para evitar uma migração desnecessária de todos os vínculos. Arquivar em vez de excluir contas com histórico. Estorno guarda natureza própria mesmo que a origem deixe de existir. Nenhum saldo real será deduzido de um catálogo antigo de bancos.
