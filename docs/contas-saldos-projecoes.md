# Contas, saldos e projeções

Em **Resumo → Contas**, cadastre suas contas próprias ou carteira e informe saldo e data de referência. Zero precisa ser informado explicitamente; uma conta antiga sem referência fica com saldo desconhecido. O total é identificado como parcial enquanto houver contas sem configuração.

A referência representa o saldo **no início da data**, antes das movimentações desse dia. Lançamentos anteriores já estão contemplados e não são somados novamente. A referência mais recente aplicável é utilizada; referências anteriores continuam no histórico. Saldo negativo é permitido. Correções exigem motivo e acrescentam uma referência auditável, sem gerar receita ou despesa.

Arquivar remove a conta das opções de novos lançamentos, preservando o saldo no consolidado, os vínculos e o extrato. É possível reativá-la. O nome normalizado continua sendo a identificação existente da conta; não há alteração automática de nomes históricos.

## Transferências

Selecione duas contas próprias cadastradas e diferentes. Uma única movimentação representa o débito da origem e o crédito do destino, gravados juntos. Editar ou excluir recalcula ambos os lados. A prévia mostra os efeitos da edição e da eventual remoção de um lançamento anterior. Transferências não compõem receita/despesa; tarifas devem ser despesas separadas.

Pix e TED são meios de pagamento e não classificam automaticamente uma movimentação como transferência interna. Limites de cartão não são dinheiro e não devem ser cadastrados como saldo.

## Três valores distintos

- **Disponível:** referências de saldo + movimentações até agora; não inclui previsões.
- **Realizado do período:** receitas líquidas menos despesas líquidas com data já alcançada.
- **Projetado do período:** realizado + previsões ativas pendentes e lançamentos com data futura. Pagamentos vinculados substituem previsões; ignoradas e pausadas não entram.

Resumo e Planejamento compartilham a mesma projeção. Gráficos de despesas e orçamentos usam os realizados. A renda e os limites informados no Planejamento são parâmetros analíticos e não criam receitas. Um estorno de despesa reduz despesas; uma devolução de receita reduz receitas. O vínculo original é opcional e a natureza é conservada mesmo se a origem for excluída.

## Cadastro rápido e recorrências

O mesmo formulário oferece lançamento único, mensal ou parcelado conforme o tipo. Categorias mudam com a movimentação, o meio de pagamento fica nos detalhes e sugestões do histórico permanecem editáveis. Estornos vinculados usam a categoria original.

A configuração inicial é opcional. Os modelos de aluguel, energia, água, internet e telefone abrem o formulário mensal com valor vazio; só a confirmação cria a previsão. Não há valores de exemplo nem lançamentos automáticos.

Uma recorrência permite editar/ignorar somente o mês ou alterar desta competência em diante, preservando pagamentos e meses anteriores. O Planejamento mantém análises, projeções e orçamentos; o gerenciamento continua nas movimentações.

## Validação no celular

1. Configure duas contas e confira o saldo individual e total; reinicie e confira a persistência.
2. Faça, edite e exclua uma transferência; confira as duas contas e a conservação do total.
3. Crie uma recorrência, altere só um mês e registre o pagamento; confira projeção, resultado e ausência de duplicação.
4. Confira receita com categoria própria, estorno de despesa e devolução de receita.
5. Corrija e arquive uma conta; confira motivo, referências e histórico preservado.

Os saldos dependem dos dados informados. Não há sincronização ou conciliação automática com bancos. A migração aditiva é Room 8→9; o backup V6 mantém leitura V1–V5. O APK `preview` instala como aplicativo de teste separado e não substitui uma instalação pessoal.
