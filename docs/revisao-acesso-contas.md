# Cadastro de saldos pela tela principal

O painel reutiliza AccountsOverview e AccountsManager. O cartão “Até o próximo salário” oferece “Configurar saldo das contas” quando faltam saldos e “Gerenciar contas” quando já há um saldo conhecido. O painel de contas continua mostrando o total consolidado existente, com aviso quando há contas sem saldo configurado.

O gerenciador permite cadastrar, corrigir a referência de saldo e arquivar contas pelas ações já disponíveis. “Ver movimentações” abre o extrato filtrado pela conta escolhida. O filtro sobrevive à recriação da atividade e pode ser removido no extrato. Os atalhos gerais de extrato e gráfico abrem sem esse filtro anterior.

A data de referência, o tratamento das movimentações anteriores e a distinção entre saldo inicial e receita continuam sob as regras existentes. Nenhum cálculo, tabela ou formato de backup foi modificado. O orçamento até salário usa os saldos conhecidos de contas ativas, conforme já fazia; o painel consolidado informa que inclui contas arquivadas.

## Validação

O teste antes isolado em ReviewBackupAccountsComponentTest passa para MainAccountsUiTest e abre o gerenciador pelo cartão real. Confere a recusa de saldo vazio, a gravação explícita de zero, ausência de receita/despesa criada, referência preservada e extrato filtrado após recriar a atividade.

Compilação e lint locais e testes instrumentais API 35 têm o resultado informado no PR. TalkBack e autenticação no aparelho permanecem verificações manuais. Não há novas migrações.
