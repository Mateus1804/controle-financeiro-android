# Uma única entrada do aplicativo

Os69 testes antes ligados à FinanceApp foram migrados em grupos nos PRs24–28, com correspondência documentada. Os testes atuais usam a MainActivity normal ou componentes compartilhados; não existe uma segunda tela principal montada só para testes.

Este PR remove FinanceApp, a flag de seleção da MainActivity e os auxiliares de lançamento/navegação antigos. FinanceApplication, FinanceViewModel, repositórios, backup e os links de revisão/planejamento continuam presentes. A rotina de lembretes usada no aplicativo normal não muda.

Os próximos PRs podem limpar componentes sem chamadas e separar o estado do painel. Banco, centavos e regras financeiras permanecem inalterados. Instrumentação apenas API35; TalkBack manual permanece pendente.

Também são removidos os antigos painéis, introdução de captura, formulários de gestão e cartões sem chamadas após a migração. MonthPicker foi extraído sem mudar o comportamento, pois continua compartilhado. Os modelos/compatibilidade de movimentos e o editor compartilhado permanecem: não se exclui código de regras financeiras nesta limpeza.
