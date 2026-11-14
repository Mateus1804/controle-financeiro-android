# Revisão incremental do Rendly

**Objetivo:** executar os itens solicitados em ordem, com PRs pequenos, preservando contratos financeiros.
**Base:** main469d16f. Árvore inicial limpa. PR22 conflitante; auditoria identificou mudanças financeiras não autorizadas.
**Tecnologias:** Kotlin, Compose, Material3, Room e core existentes.
**Escopo autorizado:** pedido do usuário de14/11/2026 nesta conversa.

Checklist de implementação preparada em PR, sem indicar merge ou aprovação antecipada do CI. A ordem de integração e as pendências estão em [Entrega incremental](../../revisao-incremental.md).

## Restrições

- Não alterar Room, backup, centavos ou regras financeiras sem confirmação prévia.
- Instrumentação somente Android15/API35. Testes JVM/core não utilizam API Android.
- Amostras sintéticas não comprovam compatibilidade com notificações reais.
- Não gerar chave definitiva sem custódia e backup independentes definidos pelo proprietário.
- Não copiar código dos projetos de referência. Novas ideias ficam para depois das prioridades.

## Revisão e implementação

- [x] Inspecionar main, PR22 e referências; baseline core aprovado.
- [x] 1a: migrar entrada de receita/despesa de RedesignInteractionTest para RealizedEntryUiTest, usando MainActivity real e tags add-realized-income/expense; verificar movimento e123456/1234 centavos.
- [x] 1b: migrar entrada monetária, edição, navegação e privacidade para a tela real, sem simular uma segunda tela principal.
- [x] 1c: migrar revisão de notificações, proteção e seletor de backup; preservar testes financeiros e de restauração.
- [x] 1d: substituir trajetos de planejamento abandonados por testes dos componentes atuais e manter a compatibilidade de dados antigos nos testes do repositório. Documentar correspondência; não ignorar testes.
- [x] 1e: após zero dependências, remover LegacyFinanceRule, PlanningTestActions e FinanceApp, extra/ramificação da MainActivity e componentes comprovadamente mortos. Preservar FinanceApplication, FinanceViewModel, editores compartilhados e deep links.
- [x] 2: separar estado de revisão, editores e painel da ForecastApp; testar recriação sem mudança de regra.
- [x] 3: abrir AccountsManager existente por CTA no painel e no cartão até salário; manter contas/saldos e filtros existentes.
- [x] 4: informar que o orçamento até salário desconta compromissos, sem alterar cálculo para incluir variáveis.
- [x] 5: listar e editar/apagar categorias lembradas em SharedPreferences; validar reconhecimento conservador com exemplos fictícios; reais dependem de amostras anonimizadas.
- [x] 6: documentar e fechar PR22 sem apagar a branch; não introduzir precedência/datas artificiais de salário.
- [x] 7: corrigir APIs e esquema no documento de atualizações, distinguindo histórico do CI atual.
- [ ] 8: documentar custódia/recuperação; chave e cópia reais dependem de destino seguro confirmado.
- [x] 9: garantir arco visível para fatias pequenas e cor identificável na legenda, mantendo percentuais/centavos originais.
- [x] 10: verificar fonte200%, tela estreita, rótulos/privacidade; documentar limites de TalkBack e testes manuais.

## Riscos e verificações

69 testes do host antigo não equivalem a69 fluxos atuais: confirmação de pagamentos e orçamentos antigos não são contratos do planejador atual. A migração deve explicitar substituições sem perder testes de persistência histórica. Alterações de captura durante revisão, rotação com editor aberto, nomes grandes, valores ocultos e contas sem saldo conhecido precisam continuar seguros.

Cada PR informará alterações, testes/API, pendências e riscos. PRs dependentes serão identificados; nenhuma migração financeira será presumida autorizada.
