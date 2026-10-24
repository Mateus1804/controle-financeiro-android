# Minhas decisões técnicas

## Primeiro um controle utilizável

Começo por lançamentos locais, revisão e cálculos verificáveis. Registro a distinção entre receita/despesa/transferência/estorno, meio de pagamento e categoria, pois esses campos respondem a perguntas diferentes.

## Captura ampla por seleção, interpretação progressiva

Uso `NotificationListenerService` e permito escolher as origens disponíveis no mesmo perfil do aparelho. A lista de bancos não fica fixada no código. A interpretação inicial trabalha com português e BRL; ampliarei os formatos conforme exemplos reais anonimizados e testes de regressão.

Deixo mensagens sem valor ou com mais de um valor para revisão. Uma notificação promocional pode aparecer como tipo indefinido e ser ignorada. A leitura de código de autenticação é descartada pelo filtro. Ainda preciso validar como os bancos reutilizam chaves e horários de suas notificações.

## Confirmação explícita

Mantenho a notificação como pendência até confirmar. Uso transação Room, exclusividade por origem e trava de escrita para impedir que dois toques criem dois lançamentos da mesma pendência. Atualizações de um mesmo evento identificado alteram a pendência; eventos diferentes com o mesmo valor continuam separados.

Não faço deduplicação automática entre bancos e carteiras: uma mesma compra pode aparecer em duas origens, e duas compras legítimas também podem ter valores iguais. Essa conciliação precisa de uma regra própria e validação.

## Interrupções controladas

Apresento a revisão dentro do aplicativo. Fora dele, uso uma notificação comum e aguardo o toque do usuário. Deixo sobreposição sobre outros aplicativos e abertura forçada em segundo plano fora do projeto inicial.

## Dados locais

Uso Room para persistência. Guardo consentimento, seleção e ocultação de totais em preferências privadas pequenas. Não incluo rede, login obrigatório, publicidade, rastreamento, leitura de SMS ou serviço de acessibilidade.

Mantenho somente o texto necessário enquanto a notificação está pendente. Após a revisão, limpo o texto original e preservo identificadores para evitar reprocessamento. Excluo os dados dos backups automáticos do Android. A exportação CSV é um relatório legível e ainda não oferece restauração.

## Dependências proporcionais

Desenho os gráficos de barras com componentes Compose nesta etapa. Adotarei uma biblioteca de gráficos quando houver uma visualização que justifique a dependência. Uso dois módulos para testar as regras sem depender do ciclo de vida Android.

## Entrada monetária em centavos

Interpreto a sequência numérica do formulário como centavos: `1234` corresponde a `R$ 12,34`. Diferencio essa digitação da colagem de um valor formatado em reais, como `1.234,56`. Preservo o limite monetário e os valores que já existem ou foram sugeridos por notificações.

Mantenho a conversão em `MoneyInput`, no módulo `core`. Uso `TextFieldState`, `InputTransformation` e `OutputTransformation` na interface para inserir separadores sem armazená-los no campo e deixar o Compose acompanhar o cursor e a seleção. Continuo usando o interpretador `Money.parse` nas notificações e os mesmos centavos inteiros na persistência e na exportação.

## Aparência independente do sistema

Guardo a escolha entre Sistema, Claro e Escuro nas preferências locais, com Sistema como padrão. Leio essa preferência antes de carregar os lançamentos para evitar depender da consulta ao banco para escolher as cores. Atualizo a interface, os ícones das barras do sistema e o tema do calendário. Uma preferência ausente ou desconhecida acompanha o sistema.

Preservo consentimento e aplicativos selecionados ao mudar a aparência. A exclusão de todos os dados também restaura a opção Sistema. Essa alteração não modifica o esquema Room nem acrescenta dependências.

## Recursos que adiei

- Registro automático sem revisão: primeiro preciso comprovar os formatos e a deduplicação.
- Classificação por serviço remoto: começo com regras locais que consigo explicar e testar.
- Nuvem e contas de usuário: primeiro entrego um controle local com recuperação de dados validada.
- Integração Open Finance: exige uma avaliação específica de parceiros, consentimento e operação.
- OCR de comprovantes e voz: priorizo um único fluxo de captura e revisão bem resolvido.

## Regras de publicação

Consultei as regras de dados do Google Play em 24/10/2026. Incluí explicação e consentimento antes da configuração da captura. A seleção usa aplicativos com atividade de abertura visível, sem `QUERY_ALL_PACKAGES`. Restrinjo essa listagem à escolha feita dentro da interface; não exporto um inventário de aplicativos.

Ainda preciso validar o APK assinado, a política pública, a declaração de segurança de dados, os requisitos aplicáveis a recursos financeiros e o processo de revisão da loja antes de uma publicação. A análise técnica não representa aprovação do Google Play.

## Contas reutilizáveis e compatibilidade

Acrescento a tabela `accounts` no esquema Room 2 e mantenho uma migração aditiva a partir da versão 1. Combino o cadastro com os nomes encontrados no histórico, normalizando espaços e caixa apenas para pesquisa e comparação. Preservo os nomes gravados nos lançamentos antigos. Nomes como Inter pessoal e Inter trabalho continuam distintos.

Atualizo a ordem de uso junto com a gravação dos lançamentos, dentro da mesma transação. Guardo separadamente a última origem utilizada manualmente. A conta escolhida na revisão não modifica o pacote ou a identificação original da notificação. Contas com saldo inicial e conciliação continuam em planejamento.

## Distribuição das despesas

Uso uma rosca em Compose Canvas acompanhada de legenda textual e acesso aos lançamentos. Agrego as despesas confirmadas do período em uma passagem e reutilizo o resultado enquanto os dados e o agrupamento permanecerem iguais. Exibo até cinco grupos e uma fatia agregada para os demais, com uma lista completa acessível.

Apresento despesas brutas antes dos estornos. Mantenho estornos separados e preservo o cálculo líquido do resumo. Distribuo os resíduos de arredondamento pelos maiores restos para a legenda somar 100,00%. Uso inteiros e `BigInteger` nessa divisão; converto proporções somente para desenhar. A opção de ocultar valores também alcança totais, legendas e percentuais do gráfico.

## Primeira utilização e avisos

Apresento o recurso de captura na primeira utilização e mantenho uma alternativa de uso manual. A apresentação não concede consentimento: a divulgação específica continua anterior à seleção de aplicativos e às configurações do Android.

Distingo falta de consentimento, captura pausada, seleção vazia, acesso ausente e serviço desconectado. Salvo o fechamento da orientação por condição e volto a orientar quando observo uma mudança relevante. O envio de alertas continua independente da captura; ajustes de permissão não bloqueiam a caixa de revisão.

## Referências da interface

Aproveito do Expense Manager a organização por contas e relatórios, do Cashew a hierarquia visual e as opções de aparência e do GreenStash a simplicidade dos componentes Material 3. Mantenho Ivy Wallet como referência histórica, pois seu repositório está arquivado. Avaliei ComposeCharts e optei pelo Canvas existente na stack para uma única rosca com legenda acessível, sem acrescentar uma dependência de gráficos.

Estudo padrões de experiência sem copiar marcas, imagens ou código desses aplicativos. Mantenho as licenças e os créditos já presentes no projeto.
