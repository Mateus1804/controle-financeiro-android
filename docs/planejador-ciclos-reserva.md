# Planejador, ciclos e reserva

O fluxo principal organiza previsões de renda e gastos. **Orçamento** permite cadastrar e corrigir itens no ciclo; **Projeções** consulta os próximos seis ciclos e compara uma simulação; **Reserva** registra o valor informado pelo usuário. A configuração inicial é opcional e dispensa contas bancárias, saldos reais e controle de quitação.

## Renda cadastrada uma vez

Cadastre o salário líquido previsto como receita mensal, com valor, data inicial, dia habitual e conta opcional. Esse mesmo item alimenta Orçamento e Projeções; não existe outro formulário de renda para o gráfico. Outras receitas podem ser únicas ou mensais, com indicação de valor estimado.

Ao editar uma ocorrência, escolha **Só neste ciclo** ou **Deste ciclo em diante**. Um salário de R$ 3.000,00 ajustado para R$ 2.850,00 somente em um ciclo mantém R$ 3.000,00 nos seguintes. Descontos já considerados no salário líquido não devem ser cadastrados novamente como gastos.

## Gastos e indicadores

O mesmo cadastro admite gasto único, mensal ou parcelado. O grupo indica **Compromisso** ou **Consumo variável**; a frequência e a indicação **Valor estimado** são independentes. Assim, energia pode ser mensal e estimada, enquanto aluguel é mensal com valor estável. Estimativas mensais sem vencimento específico podem ser vinculadas diretamente ao ciclo. Os modelos opcionais abrem com valor em branco e só criam uma previsão ao salvar.

Os indicadores usam estas regras:

- **Renda prevista:** soma das receitas incluídas no ciclo.
- **Todos os gastos:** compromissos + consumo variável, incluindo recorrências, parcelas e itens únicos uma vez em seus respectivos grupos.
- **Percentual de gastos:** todos os gastos ÷ renda prevista × 100, arredondado para duas casas decimais.
- **Margem prevista:** renda prevista − todos os gastos.
- **Ainda não destinado:** maior valor entre zero e margem prevista − destinações explícitas à reserva.

| Exemplo de ciclo | Valor |
| --- | ---: |
| Renda prevista | R$ 3.000,00 |
| Contas recorrentes | R$ 1.200,00 |
| Parcelas | R$ 400,00 |
| Consumo variável | R$ 600,00 |
| Todos os gastos | R$ 2.200,00 |
| Percentual da renda em gastos | 73,33% |
| Margem prevista | R$ 800,00 |

O gráfico apresenta compromissos e consumo com a renda como denominador e uma marca de 100%. O percentual principal pode ultrapassar 100%; o déficit continua visível e não vira uma fatia negativa. Sem renda positiva, o percentual fica indisponível e a tela informa **Renda não informada**. A margem continua sendo uma estimativa, sem afirmar saldo bancário.

Para evitar somar uma fatura e suas compras, os detalhes permitem identificar itens da mesma fatura e marcar seu total. Quando há total incluído no ciclo, os componentes com o mesmo vínculo ficam identificados como já incluídos e não são somados novamente. Esse vínculo é informado pelo usuário, sem conciliação automática. Estornos importados conservam a natureza e o sinal negativo; a barra mostra somente valores positivos, com aviso quando houver ajustes negativos nos totais.

## Datas do ciclo

O padrão é dia 1, correspondente ao mês calendário. Em **Orçamento → Configurar ciclo**, escolha um dia de 1 a 31 e confira a prévia das datas completas. O intervalo interno inclui o início e exclui a próxima fronteira: `[início, próximo início)`. A tela mostra o último dia incluído.

Com início no dia 5, **05/11/2026 a 04/12/2026** corresponde a `[05/11/2026, 05/12/2026)`. Uma conta prevista para **02/12/2026** pertence a esse ciclo; uma conta em **05/12/2026** pertence ao próximo. Renda, gastos datados, parcelas, gráfico, projeções e consulta dos ciclos seguem o mesmo intervalo. Estimativas sem vencimento pertencem diretamente ao ciclo selecionado.

Para dias 29, 30 e 31, cada fronteira usa o último dia disponível se o mês for mais curto. Por exemplo, a fronteira do dia 31 em fevereiro de 2027 é **28/02/2027**; em fevereiro de 2028 é **29/02/2028**. Vencimentos mensais e parcelas também ajustam o dia aos meses curtos.

A primeira configuração define o calendário inicial quando ainda não há configuração nem destinação à reserva vinculada a ciclo. Depois disso, uma mudança entra na próxima fronteira do ciclo vigente, preservando os períodos anteriores e as destinações registradas. Pode haver um ciclo de transição com duração diferente, sem lacunas nem sobreposições. Exemplo de mudança do dia 5 para o dia 10 durante novembro: preserva **05/11/2026 a 04/12/2026**, cria a transição **05/12/2026 a 09/12/2026** e segue com **10/12/2026 a 09/01/2027**.

Ao abrir ou retornar ao aplicativo, o ciclo atual é recalculado pela data do aparelho. Não é necessário manter o aplicativo aberto na virada.

## Vencimento, fechamento e histórico

Vencimentos organizam as previsões e os lembretes opcionais. Passar da data não registra pagamento ou recebimento, não libera o valor previsto, não declara inadimplência e não retira o gasto do orçamento. O fluxo principal dispensa **Marcar como pago** e **Confirmar recebimento**. Os lembretes dependem das permissões e da execução pelo Android, sem garantia de horário exato.

Ao terminar o intervalo, o ciclo aparece como **Ciclo encerrado · valores estimados**. Navegue pelas setas para consultar renda, gastos, grupos, percentual e sobra ou déficit. Correções explícitas recalculam o ciclo; escolher uma alteração somente naquele ciclo preserva as regras futuras. Um déficit continua no histórico estimado e não gera dívida, nova despesa ou retirada da reserva.

## Reserva de emergência

Antes de informar a reserva, seu estado é **Reserva ainda não informada**, diferente de R$ 0,00. Em **Reserva**, informe o saldo atual, inclusive zero. Depois, é possível adicionar, retirar ou corrigir o saldo com motivo e histórico. Uma retirada não pode ultrapassar o saldo informado. A meta é opcional e pode ser alterada ou removida sem mudar a reserva.

A reserva fica separada da renda e da margem do orçamento. Aportes não são despesas, retiradas não são receitas e nenhuma sobra ou déficit a altera automaticamente.

Em um ciclo encerrado positivo, **Guardar parte da sobra** permite vincular explicitamente um aporte àquele ciclo. Se a reserva ainda for desconhecida, informe seu saldo primeiro. A destinação não pode superar a sobra ainda não destinada.

Exemplo: reserva informada de R$ 2.000,00, margem de R$ 800,00 e destinação de R$ 500,00 resultam em reserva de **R$ 2.500,00** e **R$ 300,00 ainda não destinados**. Os gastos permanecem R$ 2.200,00 e o percentual permanece 73,33%. A margem original é preservada como resultado do orçamento; a parcela destinada deixa de aparecer como livre. Uma retirada posterior não desfaz essa destinação histórica.

## Projeções e simulação

**Projeções** mostra seis ciclos a partir do selecionado, usando as mesmas receitas, contas, ajustes e parcelas do orçamento. Quando o parcelamento termina, suas parcelas deixam de compor os ciclos seguintes. Os centavos restantes de uma divisão ficam nas primeiras parcelas.

Em **Simular uma nova parcela**, informe a compra para comparar percentual e margem com o planejamento atual. A identificação **Simulação · ainda fora do orçamento** permanece visível. Calcular, editar ou descartar a simulação não grava previsões, pagamentos ou eventos de reserva. Somente **Adotar no planejamento** salva a nova previsão; a simulação temporária não integra o backup.

## Preservação dos dados anteriores

O banco Room 10 acrescenta regras versionadas de previsão, ajustes por ocorrência, regras de ciclo, eventos e metadados de reserva. As tabelas anteriores permanecem preservadas. O importador mantém chaves de origem e um marcador de execução para não reaproveitar os mesmos dados a cada abertura.

Recorrências, parcelas e registros existentes são aproveitados respeitando vínculos conhecidos: um lançamento já relacionado a uma ocorrência não vira outra previsão independente. Transferências ficam fora da renda e dos gastos. As rendas de referência do cadastro mensal anterior não são somadas automaticamente, porque podem representar o mesmo salário já cadastrado. Em **Projeções → Rendas de referência antigas**, confira cada valor e se já existe uma previsão correspondente antes de **Revisar como previsão única** e salvar. Relações ausentes nos dados antigos exigem conferência manual.

Os originais ficam disponíveis somente para consulta no fluxo principal, em **Orçamento → Histórico e dados anteriores → Consultar lançamentos anteriores**. Ajustar uma previsão não reescreve o lançamento original.

O [backup](backup.md) versão 7 inclui o planejador e os dados anteriores, lendo também os formatos 1 a 6. Restaurar um formato antigo não inventa reserva nem reaproveita automaticamente renda de referência. O CSV atual exporta as previsões do ciclo e não substitui o backup.

## Checklist curto no celular

Use o APK **preview**, com identificador separado `com.mateuswilliam.financas.teste`, e dados fictícios. Ele tem uma base própria e pode coexistir com a instalação principal.

- [ ] Cadastre salário mensal de R$ 3.000,00 uma vez e os gastos do exemplo; confira R$ 2.200,00, 73,33% e margem de R$ 800,00 em Orçamento e Projeções.
- [ ] Ajuste o salário de um ciclo para R$ 2.850,00; confira R$ 3.000,00 no seguinte. Experimente um déficit e um ciclo sem renda.
- [ ] Configure dia 5; confira a conta de 02/12 no ciclo de 05/11 a 04/12 e a transição para outro dia. Consulte também fevereiro com dia 31.
- [ ] Consulte um ciclo encerrado e confira que nenhum vencimento exige ou registra quitação; corrija apenas uma ocorrência e reabra o aplicativo.
- [ ] Informe reserva de R$ 2.000,00 e destine R$ 500,00 da sobra de R$ 800,00; confira R$ 2.500,00 na reserva, R$ 300,00 livres e o mesmo percentual. Teste uma retirada e a meta opcional.
- [ ] Simule uma parcela e descarte; confira o orçamento preservado. Simule de novo, adote e confira o término das parcelas nas projeções.
- [ ] Reinicie o aplicativo, consulte os originais e confira ocultação de valores e fonte ampliada. Teste backup e recuperação somente na instalação isolada.

Este checklist descreve os cenários manuais a conferir; não registra sua aprovação. Os testes locais executados e seus limites estão em [Validação do planejador, ciclos e reserva](validacao-planejador-ciclos-reserva.md); os registros anteriores permanecem em [Qualidade e validação](qualidade.md). Não há integração bancária, consulta de saldo real, conciliação automática, gestão completa de dívidas ou sincronização em nuvem nesta etapa.
