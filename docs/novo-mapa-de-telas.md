# Novo mapa de telas — Rendly

Objetivo: uma planilha de gastos que se preenche com captura local, com visual verde-água, claro/escuro e valores ocultáveis. O orçamento continua independente de confirmar pagamentos. Kotlin, Compose, Material 3 e cálculo em centavos serão mantidos.

| Aba | Conteúdo |
| --- | --- |
| Mês | Seletor de mês; receitas, despesas e saldo do mês; filtros de categoria/conta; linhas agrupadas por dia; botão +. Sem cartões aninhados ou painéis de planejamento. |
| Gráficos | Rosca de gastos, legenda com valores e comparação com o mês anterior. |
| Fixas e parcelas | Lista única de previsões de despesas mensais e parcelas. Ações: Pular este mês, Alterar só este mês e Aplicar deste mês em diante. |
| Mais | Acessos a Salário, Planejador, Reserva e Comprometimento, cada um em seu destino. Ajustes reúne captura/revisão de notificações, contas, backup e aparência. |

O + permite escolher a natureza do lançamento; o editor começa com valor, categoria adequada, data e descrição. Conta e meio de pagamento ficam em Mais detalhes. Receitas, transferências próprias e estornos continuam disponíveis. Os totais de Mês usam os registros do mês; transferências próprias não geram receita/despesa. Previsões continuam identificadas como previsões, em seus destinos, para evitar dupla contagem.

Salário: disponível conhecido de contas ativas menos compromissos até o próximo salário, sem descontar a reserva. Exibir “Saldo parcial” quando faltar saldo de conta ativa, “Cálculo até dd/mm (próximo salário previsto)” e cenário separado “Se os gastos variáveis previstos acontecerem”. Aceitação: R$ 1.000 − R$ 300 = R$ 700; em 10 dias, média semanal R$ 490.

Armazenamento: o proprietário autorizou começar vazio e descartar os dados atuais. A estrutura nova terá apenas previsões para fixas/parcelas, banco novo e backup novo, sem importador legado, migrações anteriores ou leitor de backups antigos. Preservar preferências de aparência/ocultação. Documentar a perda e testar gravação, exportação, senha incorreta e restauração do formato novo.

Entrega: um PR por item (mapa, Mês/editor, Gráficos, Fixas/parcelas, Salário, dados novos, limpeza, relatório final). Após a retomada da exigência pelo proprietário, novos merges dependem de CI verde. Instrumentação somente Android 15/API 35. Reusar componentes existentes; substituir os trajetos dos testes junto de cada mudança, sem manter uma segunda interface só para testes.

Medição inicial em e333598: 8.184 linhas Kotlin de produção em app/src/main/java e core/src/main; seis composables com nome terminado em Screen e um painel principal inline, total de sete superfícies de tela (diálogos excluídos). Depois da reorganização e limpeza: 5.989 linhas Kotlin de produção; onze composables terminados em Screen e um painel inline de Planejador, total de doze superfícies pela mesma métrica (inclui bloqueio e destinos secundários; diálogos excluídos). A redução é de 2.195 linhas; as quatro abas permanecem, enquanto blocos antes misturados no painel ganharam destinos próprios. Os números podem variar caso as correções da validação acrescentem código; a contagem final será atualizada na entrega.
