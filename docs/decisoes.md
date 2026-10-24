# Decisões técnicas

## Primeiro um controle utilizável

A base prioriza lançamentos locais, revisão e cálculos verificáveis. Receita, despesa, transferência e estorno são tipos de movimentação separados do meio de pagamento e da categoria, pois esses campos respondem a perguntas diferentes.

## Captura ampla por seleção, interpretação progressiva

A captura utiliza `NotificationListenerService` e permite escolher as origens disponíveis no mesmo perfil do aparelho. A lista de bancos não fica fixada no código. A interpretação inicial trabalha com português e BRL; a ampliação dos formatos depende de exemplos reais anonimizados e testes de regressão.

Mensagens sem valor ou com mais de um valor permanecem para revisão. Uma notificação promocional pode aparecer como tipo indefinido e ser ignorada. A leitura de código de autenticação é descartada pelo filtro. A reutilização de chaves e horários das notificações pelos bancos ainda exige validação.

## Confirmação explícita

A notificação permanece como pendência até a confirmação. A combinação de transação Room, exclusividade por origem e trava de escrita impede que dois toques criem dois lançamentos da mesma pendência. Atualizações de um mesmo evento identificado alteram a pendência; eventos diferentes com o mesmo valor continuam separados.

Não há deduplicação automática entre bancos e carteiras: uma mesma compra pode aparecer em duas origens, e duas compras legítimas também podem ter valores iguais. Essa conciliação precisa de uma regra própria e validação.

## Interrupções controladas

A revisão ocorre dentro do aplicativo. Fora dele, uma notificação comum aguarda o toque do usuário para abrir a pendência. Sobreposição sobre outros aplicativos e abertura forçada em segundo plano ficam fora do projeto inicial.

## Dados locais

A persistência utiliza Room. Consentimento, seleção e ocultação de totais ficam em preferências privadas pequenas. A base não inclui rede, login obrigatório, publicidade, rastreamento, leitura de SMS ou serviço de acessibilidade.

Somente o texto necessário é mantido enquanto a notificação está pendente. Após a revisão, o texto original é apagado e os identificadores são preservados para evitar reprocessamento. Os dados ficam excluídos dos backups automáticos do Android. A exportação CSV é um relatório legível e ainda não oferece restauração.

## Dependências proporcionais

Os gráficos de barras utilizam componentes Compose nesta etapa. A adoção de uma biblioteca de gráficos depende de uma visualização que justifique essa dependência. A separação em dois módulos permite testar as regras sem depender do ciclo de vida Android.

## Entrada monetária em centavos

A sequência numérica do formulário representa centavos: `1234` corresponde a `R$ 12,34`. Essa digitação é diferenciada da colagem de um valor formatado em reais, como `1.234,56`. O limite monetário e os valores existentes ou sugeridos por notificações são preservados.

A conversão fica em `MoneyInput`, no módulo `core`. A interface utiliza `TextFieldState`, `InputTransformation` e `OutputTransformation` para inserir separadores sem armazená-los no campo e permitir que o Compose acompanhe o cursor e a seleção. As notificações continuam utilizando o interpretador `Money.parse`, com os mesmos centavos inteiros na persistência e na exportação.

## Aparência independente do sistema

A escolha entre Sistema, Claro e Escuro fica nas preferências locais, com Sistema como padrão. A leitura dessa preferência ocorre antes do carregamento dos lançamentos, sem depender da consulta ao banco para definir as cores. A escolha atualiza a interface, os ícones das barras do sistema e o tema do calendário. Uma preferência ausente ou desconhecida acompanha o sistema.

Consentimento e aplicativos selecionados são preservados ao mudar a aparência. A exclusão de todos os dados também restaura a opção Sistema. Essa alteração não modifica o esquema Room nem acrescenta dependências.

## Recursos adiados

- Registro automático sem revisão: depende da comprovação dos formatos e da deduplicação.
- Classificação por serviço remoto: a etapa inicial prioriza regras locais explicáveis e testáveis.
- Nuvem e contas de usuário: a prioridade é um controle local com recuperação de dados validada.
- Integração Open Finance: exige uma avaliação específica de parceiros, consentimento e operação.
- OCR de comprovantes e voz: a prioridade é um único fluxo de captura e revisão bem resolvido.

## Regras de publicação

As regras de dados do Google Play foram consultadas em 24/10/2026. A configuração da captura é precedida de explicação e consentimento. A seleção usa aplicativos com atividade de abertura visível, sem `QUERY_ALL_PACKAGES`. Essa listagem fica restrita à escolha dentro da interface, sem exportação de um inventário de aplicativos.

Antes de uma publicação, permanecem pendentes a validação do APK assinado, a política pública, a declaração de segurança de dados, os requisitos aplicáveis a recursos financeiros e o processo de revisão da loja. A análise técnica não representa aprovação do Google Play.

## Contas reutilizáveis e compatibilidade

A tabela `accounts` integra o esquema Room 2, com migração aditiva a partir da versão 1. O cadastro é combinado com os nomes encontrados no histórico, com normalização de espaços e caixa apenas para pesquisa e comparação. Os nomes gravados nos lançamentos antigos são preservados. Nomes como Inter pessoal e Inter trabalho continuam distintos. A mesma comparação nas transferências impede origem e destino iguais por diferenças de espaços ou maiúsculas.

A ordem de uso é atualizada junto com a gravação dos lançamentos, dentro da mesma transação. A última origem utilizada manualmente é armazenada separadamente. A conta escolhida na revisão não modifica o pacote ou a identificação original da notificação. Contas com saldo inicial e conciliação continuam em planejamento.

## Distribuição das despesas

O relatório utiliza uma rosca em Compose Canvas acompanhada de legenda textual e acesso aos lançamentos. As despesas confirmadas do período são agregadas em uma passagem, e o resultado é reutilizado enquanto os dados e o agrupamento permanecerem iguais. A visualização exibe até cinco grupos e uma fatia agregada para os demais, com uma lista completa acessível.

A distribuição apresenta despesas brutas antes dos estornos. Os estornos ficam separados, com preservação do cálculo líquido do resumo. Os resíduos de arredondamento são distribuídos pelos maiores restos para a legenda somar 100,00%. A divisão utiliza inteiros e `BigInteger`; as proporções são convertidas somente para o desenho. A opção de ocultar valores também alcança totais, legendas e percentuais do gráfico.

## Primeira utilização e avisos

O recurso de captura é apresentado na primeira utilização, com uma alternativa de uso manual. A apresentação não concede consentimento: a divulgação específica continua anterior à seleção de aplicativos e às configurações do Android.

O estado da captura diferencia falta de consentimento, captura pausada, seleção vazia, acesso ausente e serviço desconectado. O fechamento da orientação é salvo por condição, com nova orientação após uma mudança relevante. A autorização do componente do serviço é consultada no Android, e o estado é atualizado ao retomar a atividade ou mudar a conexão. O envio de alertas continua independente da captura; ajustes de permissão não bloqueiam a caixa de revisão. A escolha de uso manual também pausa uma captura que já estava habilitada.

## Referências da interface

A organização por contas e relatórios tem como referência o Expense Manager; a hierarquia visual e as opções de aparência, o Cashew; e a simplicidade dos componentes Material 3, o GreenStash. Ivy Wallet permanece como referência histórica, pois seu repositório está arquivado. Após a avaliação do ComposeCharts, a implementação utiliza o Canvas existente na stack para uma única rosca com legenda acessível, sem acrescentar uma dependência de gráficos.

O estudo de padrões de experiência não envolve cópia de marcas, imagens ou código desses aplicativos. As licenças e os créditos já presentes no projeto são preservados.
