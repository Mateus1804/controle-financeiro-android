A fila foi integrada na main com merge commits e branches mantidas: #24, #25, #26, #27, #28, #29, #35, #30, #31, #32, #33, #37, #38 e #34. Os PRs #39 e #40 também foram integrados. O #36 foi fechado sem merge; a assinatura ficou fora do escopo. Não restam PRs abertos.

A main reúne a interface principal única, testes migrados, estados separados, acesso aos saldos das contas, edição das categorias lembradas e melhorias no gráfico e na acessibilidade. O cálculo até o salário agora desconta os compromissos sem descontar a reserva, separa os gastos variáveis previstos e avisa quando o saldo das contas está incompleto.

A pedido do usuário, os merges foram concluídos sem executar novos testes nem esperar o CI. Houve uma validação anterior aprovada na API 35, no commit e333598, mas ela não comprova a versão final após os últimos merges. Não foram feitos testes manuais de TalkBack, biometria ou notificações reais em aparelho físico.

O Prompt 2 está parcialmente implementado: o mapa de telas e a correção do salário entraram; faltam as quatro abas, a tela Mês simplificada, a organização de Gráficos e Fixas e parcelas, o armazenamento novo e a limpeza. O banco atual e os backups continuam preservados. O início vazio, já autorizado, ainda não foi aplicado.

Riscos restantes: possíveis regressões sem validação final, formatos de notificações não reconhecidos e ausência de assinatura definitiva. A futura troca de armazenamento descartará os dados atuais e não aceitará backups antigos, conforme autorizado.
