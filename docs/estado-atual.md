A main foi conferida no commit `0f73677` e está com CI verde. A migração solicitada já foi integrada no PR #48. O PR #47 está fechado, sem merge próprio, porque suas duas correções foram incorporadas com crédito preservado. Sua branch foi mantida.

Os testes usam a tela atual: a preparação espera `records-list` e `add-movement`; o auxiliar abre o botão + e escolhe o tipo de lançamento. Os testes de planejamento acessam Mais → Planejador. Não há uma segunda interface exclusiva para testes.

O aplicativo tem quatro abas: Mês, Gráficos, Fixas e parcelas e Mais. Planejador, reserva e comprometimento ficam em Mais; não existe mais tela de Salário. A limpeza do código antigo e dos testes sem uso foi concluída; o mapa de telas registra a redução de 8.184 para 5.992 linhas de produção. A paleta atual é Ameixa & Sálvia, com fundo branco no claro e preto no escuro.

Validação aprovada: build, lint, testes do core, 174 testes da suíte completa na API 35 e um teste de reset intencional na API 35, sem ignorados. O [CI do PR #51](https://github.com/Mateus1804/controle-financeiro-android/pull/51/checks) contém os resultados. O CI também instala o APK novo sobre o APK anterior, sem desinstalar, confere a manutenção do identificador da instalação e verifica o reset autorizado, preservando aparência e ocultação. A leitura de bancos e backups antigos continua incompatível.

Esta versão começa vazia e não aceita backups antigos, conforme autorizado. O formato novo de backup mantém proteção por senha e restauração completa. Não foram feitos testes manuais de TalkBack, biometria ou notificações reais em aparelho físico. Permanecem as limitações de reconhecimento das notificações e a assinatura definitiva fora do escopo.

## Receita única e Conta total (PR #52)

Salário é uma receita comum (categoria Salário), lançada pelo mesmo formulário e lista de qualquer receita. A Conta total é a soma dos saldos de todos os bancos e aparece no topo da aba Mês; receita aumenta, despesa diminui e transferência não muda o total (testado).

| Onde | Antes | Agora |
|---|---|---|
| Mais → Salário (tela e cartão "resultado do mês") | tela própria com resumo de salário | removida |
| Mês | só receitas, despesas e saldo do mês | Conta total + receitas, despesas e saldo do mês |
| Planejador | sem saldo total | cartão "Até o próximo salário" usa a Conta total |
| Receita prevista (regra) e receita real (lançamento) | mesmo modelo de previsão e de registro | sem mudança (já era um modelo só) |

Linhas removidas: SalaryScreen, MonthlyResultCard e três testes do cartão (cerca de 136 linhas; 88 adicionadas, incluindo Conta total e seus testes).

Testes executados no CI do PR #52: build, lint, testes do core (incluindo AccountTotalTest) e suíte completa na API 35, mais o teste de reset. Todos verdes.

Não foi feito: remover SalarySummary do core (ainda usado por testes e pelo cálculo do orçamento), unificar a exibição de receitas previstas em Comprometimento e Fixas, e mover a lista de contas só para Mais (hoje o toque na Conta total também abre o ajuste de saldos). Sem teste manual em aparelho físico.
