# Cálculo até o próximo salário

O valor disponível é o saldo conhecido das contas ativas menos os compromissos previstos desde hoje até antes da data do próximo salário. A reserva e sua meta não são descontadas. Não há confirmação de pagamento para usar esse planejamento.

Uma linha separada, “Se os gastos variáveis previstos acontecerem”, mostra a disponibilidade depois desses gastos. Ela não altera o valor principal, os saldos das contas ou os lançamentos. Ocorrências repetidas são contadas uma vez; previsões ignoradas e fora do intervalo ficam de fora.

Quando existe conta ativa sem saldo informado, o cartão avisa “Saldo parcial”. Uma conta com zero informado é conhecida; uma conta arquivada não torna esse aviso parcial. O cabeçalho informa “Cálculo até dd/mm (próximo salário previsto)”.

## Verificação

O caso de aceitação usa saldo de R$ 1.000, compromisso de R$ 300 e dez dias: total de R$ 700 e média semanal de R$ 490. O cenário com R$ 200 de gastos variáveis retorna R$ 500 sem mudar o principal. Casos adicionais cobrem déficit, pagamento hoje, previsões ignoradas e deduplicação. Os novos testes reproduziram o desconto indevido antes da correção. A suíte instrumental confere data, saldo parcial, valores e ocultação; somente Android 15 (API 35).

O PR #32 havia apenas explicado a regra anterior, que descontava a meta total da reserva. O Prompt 2 autorizou sua correção. Este cálculo não altera banco ou backup. Uma previsão futura sem vínculo com um lançamento realizado continua sendo uma projeção a revisar; não há reconhecimento bancário universal nem confirmação automática de pagamentos.
