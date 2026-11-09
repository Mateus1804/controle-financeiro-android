Atue como engenheiro de software principal e desenvolvedor Android sênior, com excelência em arquitetura, Kotlin, Jetpack Compose, Material Design 3, Room, testes e regras financeiras.

Trabalhe com rigor profissional: investigue a implementação existente, elimine as causas das inconsistências, reaproveite componentes e entregue fluxos completos e verificáveis. Resolva escolhas rotineiras com autonomia, evitando refatorações desnecessárias e ampliação do escopo.

Repositório: https://github.com/Mateus1804/controle-financeiro-android
Objetivo do produto
Transformar o aplicativo em um planejador financeiro pessoal baseado em previsões, capaz de responder:
- Quanto espero receber neste período?
- Quanto da minha renda está comprometido com todos os gastos previstos?
- Qual é minha margem estimada?
- Como os próximos períodos ficarão?
- Quanto tenho na reserva de emergência?
O usuário deve cadastrar renda e gastos uma única vez. O aplicativo deve calcular automaticamente os indicadores, sem exigir confirmação de pagamento de contas ou recebimento de salário.

Prioridades obrigatórias: unificar a origem da renda, considerar todos os gastos no gráfico, permitir ciclo financeiro configurável e incluir reserva de emergência.
1. Diagnóstico e fonte única dos dados
Antes de implementar, identifique brevemente:
- Onde renda, salário, recorrências e planejamento estão duplicados.
- Quais componentes e cálculos podem ser reaproveitados.
- Como preservar os registros e vínculos existentes.
Centralize as regras financeiras em uma camada testável. Tela inicial, Planejamento, gráficos e histórico devem utilizar a mesma origem de dados e as mesmas regras de período.

Elimine formulários independentes que obriguem o usuário a informar novamente a renda para alimentar outra tela.
2. Salário e outras receitas previstas
O salário deve ser cadastrado uma única vez como receita prevista recorrente mensal, contendo valor líquido estimado, data habitual, conta opcional e início da recorrência.

Esse cadastro deve alimentar automaticamente o orçamento e as projeções.

Permita:
- Ajustar o valor somente naquele período.
- Alterar daquele período em diante.
- Acrescentar outras fontes de renda.
- Identificar receitas variáveis ou estimadas.
Exemplo obrigatório: salário previsto de R$ 3.000, ajustado para R$ 2.850 em determinado período devido a descontos. Aquele período utiliza R$ 2.850; os seguintes continuam com R$ 3.000, salvo alteração permanente.

Salário bruto, descontos e adicionais podem existir em detalhes opcionais. Descontos já considerados no valor líquido não devem ser contabilizados novamente como despesas.

A passagem da data prevista não deve registrar automaticamente um recebimento realizado.
3. Cadastro único dos gastos previstos
Reúna na mesma interface:
- Contas recorrentes, como aluguel, energia, água, internet e telefone.
- Parcelas de compras e outras obrigações.
- Gastos únicos previstos.
- Estimativas de consumo, como alimentação e transporte.
Diferencie frequência e comportamento do valor: energia pode ser uma obrigação mensal com valor estimado; aluguel pode ser mensal com valor estável.

Ofereça lançamento único, recorrente mensal ou parcelado, conforme o caso. Mantenha apenas recorrência mensal nesta etapa.

Permita alterar somente uma ocorrência, ignorá-la naquele período ou modificar as futuras. Preserve os períodos anteriores.

Os modelos opcionais devem abrir com valores em branco. Nenhuma escolha de modelo deve criar movimentações automaticamente.
4. Ciclo financeiro configurável
Permita escolher o dia de início do ciclo financeiro, conforme a organização do usuário.

Exemplo: com início no dia 5, o período vai de 05/11 a 04/12. Em 05/12 começa o próximo ciclo e o anterior é encerrado.

Regras obrigatórias:
- Dia 1 corresponde ao mês calendário.
- Para dias 29, 30 ou 31, utilize o último dia disponível nos meses mais curtos.
- Mostre sempre as datas completas do período.
- Utilize o mesmo intervalo em renda, gastos, parcelas, gráficos, projeções e histórico.
- Uma conta com vencimento em 02/12 pertence ao ciclo de 05/11 a 04/12.
- Estimativas sem vencimento específico pertencem diretamente ao ciclo selecionado.
- Mudanças na configuração devem preservar períodos encerrados e tratar a transição sem lacunas ou sobreposições.
Agrupe ocorrências por suas datas e intervalos, sem depender apenas do nome do mês calendário.

O aplicativo deve reconhecer o ciclo correto ao abrir ou retornar à tela, sem depender de permanecer aberto na data de fechamento.
5. Indicadores e gráfico de gastos totais
Apresente:
- Renda prevista: total esperado no período.
- Compromissos previstos: contas e parcelas assumidas.
- Gastos variáveis estimados: orçamento de consumo informado.
- Gastos totais previstos: soma de todos os gastos do período.
- Margem estimada: renda prevista menos gastos totais previstos.
O indicador principal do gráfico deve ser:

Percentual de gastos totais = gastos totais previstos ÷ renda prevista × 100.

O gráfico deve considerar todos os gastos, incluindo recorrentes, parcelas, variáveis e únicos. Os gastos fixos podem continuar identificáveis no detalhamento, mas não podem ser a única base do percentual principal.

Utilize grupos sem sobreposição e informe claramente o denominador dos percentuais. Evite contar uma parcela dentro de dois grupos ou somar uma fatura às compras já incluídas nela.

Quando os gastos ultrapassarem a renda:
- Mostre o percentual verdadeiro, inclusive acima de 100%.
- Destaque o déficit.
- Não limite artificialmente o indicador a 100%.
- Não represente a sobra como uma fatia negativa.
Sem renda suficiente para calcular o percentual, apresente essa condição explicitamente. Não mostre uma classificação financeira favorável baseada em dados ausentes.

Exemplo obrigatório de validação:
Item	Valor
Renda prevista	R$ 3.000
Contas recorrentes	R$ 1.200
Parcelas	R$ 400
Gastos variáveis	R$ 600
Gastos totais previstos	R$ 2.200
Percentual total da renda	73,33%
Margem estimada	R$ 800


Essa margem representa o orçamento estimado, sem afirmar que corresponde ao saldo bancário real.
6. Vencimentos e fechamento sem controle de quitação
Utilize os vencimentos para organização e lembretes opcionais, por exemplo:
“O vencimento previsto da internet é hoje.”

O fluxo principal não deve exigir “Marcar como pago” ou “Confirmar recebimento”.

A passagem do vencimento não deve:
- Liberar o valor comprometido.
- Registrar pagamento ou recebimento.
- Classificar automaticamente uma conta como inadimplente.
- Retirar o gasto do orçamento daquele período.
No fechamento, preserve um resumo com renda, gastos, distribuição por grupo, percentual comprometido e sobra ou déficit estimado.

Permita correções explícitas dos valores de períodos anteriores, recalculando seus indicadores sem modificar as configurações futuras.

Déficits estimados devem permanecer identificados no histórico. Não os transforme automaticamente em dívida, nova despesa ou retirada da reserva.
7. Reserva de emergência
Inclua uma área simples para informar quanto o usuário possui atualmente na reserva de emergência.

Permita:
- Informar o saldo inicial da reserva.
- Registrar acréscimos e retiradas.
- Corrigir o valor com histórico da alteração.
- Definir uma meta opcional.
A reserva deve permanecer separada da renda mensal e da margem livre para novos gastos.

Ao encerrar um ciclo positivo, ofereça a opção de informar quanto o usuário decidiu guardar. Não acrescente automaticamente toda a sobra estimada à reserva.

Diferencie o resultado do orçamento da destinação desse resultado:
- Guardar parte da sobra não gera outra despesa.
- O aporte não altera o percentual de gastos totais.
- O valor destinado à reserva deixa de aparecer como margem ainda livre para destinação.
- O mesmo dinheiro não pode aparecer simultaneamente como reserva e valor livre.
Não utilize a reserva automaticamente para cobrir um período negativo.
8. Projeções e simulações úteis
Permita consultar os próximos períodos considerando:
- Repetição das contas mensais.
- Ajustes específicos de renda e gastos.
- Início e término de parcelamentos.
- Alterações futuras dos valores.
- Redução do comprometimento quando parcelas terminarem.
Após consolidar os cálculos principais, ofereça uma simulação simples:
“Se eu assumir esta nova parcela, como ficará meu orçamento?”

A simulação deve permanecer identificada e só alterar o planejamento definitivo quando o usuário decidir adotá-la.
9. UI/UX e simplificação
Priorize uma interface mensal com renda, gastos, percentual total e margem estimada. Permita cadastrar e editar os itens diretamente nesse contexto.

Utilize:
- Cadastro rápido.
- Sugestões editáveis compatíveis com o tipo de lançamento.
- Categorias próprias para receitas e despesas.
- Detalhes progressivos.
- Ações contextuais.
- Feedback claro.
- Acessibilidade e suporte a fontes ampliadas.
Evite repetir formulários, listas completas e cartões com o mesmo propósito em várias telas.

O Planejamento deve utilizar os dados já cadastrados para análises, limites e simulações, sem outro cadastro de renda.

A configuração inicial deve ser opcional. Não exija contas bancárias, saldos reais ou acompanhamento de pagamentos para utilizar o planejador.

Não acrescente nesta etapa integrações bancárias, conciliação automática, gestão completa de dívidas ou outras funcionalidades que desviem do objetivo principal.
10. Integridade, migração e validação
Preserve todos os dados e históricos existentes. Utilize migrações aditivas somente quando necessárias, mantendo compatibilidade com backups.

Ao aproveitar registros antigos:
- Identifique possíveis duplicações entre renda de referência e receitas.
- Preserve os valores originais.
- Não crie renda ou gastos automaticamente sem uma regra clara de aproveitamento.
- Não some uma previsão aos registros correspondentes da mesma origem.
Armazene valores monetários em centavos inteiros e proteja os cálculos contra duplicações, arredondamentos indevidos e divisão por zero.

Execute validações essenciais que comprovem:
1. Renda cadastrada uma única vez alimenta todas as telas.
2. Ajuste de salário em um período preserva os demais.
3. Todos os gastos entram uma única vez no percentual principal.
4. Ciclos funcionam entre meses, anos e meses curtos.
5. Mudanças no dia do ciclo preservam o histórico.
6. Fechamento e vencimentos não geram pagamentos fictícios.
7. Percentuais acima de 100% e déficits aparecem corretamente.
8. Término das parcelas reduz os compromissos futuros.
9. Reserva e margem livre não contabilizam o mesmo dinheiro.
10. Dados e resultados permanecem corretos após reiniciar.
Critérios bloqueantes da entrega
A tarefa somente estará concluída quando:
- O usuário informar sua renda uma única vez.
- O gráfico principal considerar todos os gastos.
- O dia de início escolhido controlar todos os cálculos do período.
- Sobras e déficits permanecerem consultáveis no histórico.
- A reserva de emergência estiver disponível e separada do orçamento livre.
- O funcionamento principal dispensar confirmação de pagamentos e recebimentos.
Ao finalizar, apresente resumidamente as mudanças, as regras de cálculo, os testes executados, as limitações e um checklist para validação no celular. Não declare a entrega concluída apenas porque o projeto compilou: demonstre que os fluxos e cálculos acima funcionam de forma integrada.
