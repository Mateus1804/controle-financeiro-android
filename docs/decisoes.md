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

O esquema Room 2 introduziu a tabela `accounts`, com migração aditiva a partir da versão 1. O cadastro é combinado com os nomes encontrados no histórico, com normalização de espaços e caixa apenas para pesquisa e comparação. Os nomes gravados nos lançamentos antigos são preservados. Nomes como Inter pessoal e Inter trabalho continuam distintos. A mesma comparação nas transferências impede origem e destino iguais por diferenças de espaços ou maiúsculas.

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

## Backup portátil e restauração conferida

O backup é um snapshot lógico das tabelas, com formato versionado e criptografia autenticada. O envelope utiliza AES-256-GCM, salt e nonce aleatórios e PBKDF2-HMAC-SHA256 com 600 mil iterações. As APIs criptográficas da plataforma atendem ao formato sem nova dependência de produção. O cabeçalho é autenticado; valores de versão ou custo não suportados são recusados antes da derivação.

A importação limita tamanho e quantidade de itens e exige centavos inteiros, enums conhecidos, identificadores únicos e vínculos válidos. O conteúdo passa por um banco Room temporário privado, conferência de integridade e releitura. A substituição financeira ocorre em uma transação da base ativa, sob a mesma exclusão de escrita das confirmações; não há troca de arquivo SQLite em uso. Cancelamento e falhas preservam os dados existentes. A limpeza da base temporária permanece executável mesmo durante o cancelamento da operação.

Preferências de apresentação são restauráveis. Consentimento, seleção de origens, permissões e proteção continuam pertencendo ao aparelho. Captura e lembretes ficam pausados após sucesso. O arquivo pode conter textos de pendências, e a senha não é recuperável. Os detalhes do formato e da retenção ficam em [Backup e recuperação](backup.md).

## Migração do planejamento

O esquema Room 3 acrescenta `budgets`, `recurring` e `occurrences`. A migração 2→3 cria somente as tabelas e o índice necessários; as migrações 1→2 e 2→3 podem ser encadeadas. Registros, pendências e nomes históricos não são reescritos. Os esquemas 1, 2 e 3 permanecem versionados.

Os orçamentos usam mês e categoria como chave e acompanham despesas líquidas confirmadas. Quando os estornos superam as despesas da categoria, o consumo exibido é zero, sem criar crédito artificial para o orçamento. Proporções de desenho não alteram os centavos.

As recorrências são previsões mensais de receita ou despesa. A confirmação de um vencimento insere o lançamento, registra o vínculo e avança a previsão na mesma transação. A chave de recorrência e data impede confirmar duas vezes o mesmo vencimento. Pular avança a previsão com um registro próprio, sem criar movimentação financeira. O dia original é preservado nos meses curtos. A data efetiva sugerida no formulário é a data atual e continua editável.

## Possíveis duplicidades e revisão em sequência

O índice de candidatos agrupa lançamentos manuais por valor e movimentação. A comparação considera até 48 horas de diferença e exige conta equivalente, indício do mesmo banco ou descrição com termos significativos em comum. A apresentação limita os candidatos a três, com motivos visíveis e ordenação estável. Um banco semelhante não identifica qual conta foi usada. A decisão permanece na revisão; não há união nem exclusão automática.

A sessão de revisão utiliza uma lista estável de identificadores obtida ao abrir o primeiro item. Novas pendências aparecem na contagem, sem substituir a edição ou entrar silenciosamente na sessão. O próximo item só abre por ação explícita. A confirmação usa a revisão esperada da pendência; quando o texto de origem muda, os campos editados permanecem, e a nova versão exige conferência explícita antes do salvamento.

## Lembretes sem horário exato

O `JobScheduler` nativo realiza verificações periódicas, complementadas pela abertura do aplicativo. A escolha evita uma dependência adicional para um único trabalho periódico e não solicita alarmes exatos. A permissão normal `RECEIVE_BOOT_COMPLETED` permite persistir o agendamento; o serviço permanece protegido por `BIND_JOB_SERVICE`. O Android ainda pode adiar ou impedir uma execução conforme bateria, parada forçada e condições do aparelho.

Os lembretes têm controle próprio e dependem da permissão e do canal de alertas. A recusa não desativa a captura autorizada. O vencimento só é marcado como avisado depois da publicação aceita; o identificador estável e `setOnlyAlertOnce` reduzem alertas repetidos se houver interrupção entre publicação e persistência. Um encerramento nesse intervalo não permite prometer entrega exatamente uma vez. Confirmar, pular, pausar ou mudar o vencimento cancela o alerta anterior. Valor, conta e descrição não aparecem no alerta.

## Proteção da interface

A proteção começa desativada. Ativar ou desativar exige autenticação do Android. O Android 11 ou superior utiliza `BiometricPrompt` com biometria forte ou credencial; versões anteriores utilizam os caminhos nativos compatíveis, com credencial do aparelho como alternativa. A sessão vive apenas em memória e exige autenticação ao iniciar, após 30 segundos em segundo plano e quando a tela é desligada ou o aparelho é observado bloqueado. Uma simples recriação por configuração não concede nem remove autenticação já obtida.

Links de notificação passam pela mesma tela de bloqueio. O estado editável da interface é preservado pelo contêiner de estado ao bloquear; uma autenticação em andamento pode ser cancelada durante a recriação, mantendo o acesso protegido e permitindo nova tentativa. A proteção da imagem usa `FLAG_SECURE` na atividade, nos formulários e nos calendários nativos. Os diálogos de senha e restauração têm proteção explícita. A escolha de ocultação pode ser alterada separadamente.

O banco Room permanece no armazenamento privado do Android, sem nova camada própria de criptografia. Biometria e proteção de tela não substituem criptografia do banco, proteção do sistema ou avaliação em aparelhos reais. Não há coleta de dados biométricos pelo aplicativo.

A ativação ou desativação do bloqueio só é apresentada como salva após confirmação da persistência. Antes de restaurar ou apagar dados, a pausa da captura e dos lembretes também precisa ser persistida. Na exclusão, a proteção permanece configurada até a transação financeira terminar; falha do banco preserva os registros e o bloqueio. Falha posterior na limpeza das preferências é informada separadamente da exclusão já concluída.

## Navegação com fonte ampliada

Em telas estreitas com fonte ampliada, a barra inferior mantém quatro destinos e o botão de novo lançamento passa para uma área reservada acima dela. O conteúdo recebe espaço próprio para impedir sobreposição de valores e ações. A navegação usual mantém o botão central nas demais configurações. Os rótulos permanecem em uma linha, com texto completo na semântica, e o teste em 150% confere que Resumo não foi quebrado nem abreviado. Os ícones das barras do Android também são sincronizados na retomada e no retorno do foco da janela.

## Histórico crescente e recuperação

O esquema 4 utiliza índices compostos que correspondem à ordenação do histórico e ao filtro de pendências. A migração 3→4 somente cria índices. O identificador desempata datas iguais de maneira estável. A validação inspeciona `EXPLAIN QUERY PLAN` e usa cinco mil registros fictícios; não promete um limite de desempenho para qualquer aparelho.

O estado da interface recebe o histórico e os candidatos manuais na mesma emissão. Isso elimina uma observação completa independente. O catálogo de contas consulta apenas nomes e datas agregados por SQL e continua normalizando espaços e caixa em Kotlin, sem alterar os nomes históricos ou confundir contas distintas do mesmo banco. As contas cadastradas mantêm preferência de apresentação.

A lista de candidatos à duplicidade continua cobrindo o histórico manual, inclusive quando a proposta é editada. Não foi introduzido um filtro SQL pelo valor original da notificação, pois esse filtro perderia correspondências após edição. Paginação completa e busca por período ficam para uma etapa orientada por medições; as telas e a exportação ainda podem carregar o histórico inteiro.

As transformações do histórico e a normalização do catálogo utilizam `Dispatchers.Default`; as consultas Room continuam fora da thread principal. Essa separação evita executar o agrupamento dos nomes e a conversão do histórico na thread da interface.

O agendamento nativo dos lembretes consulta a preferência vigente dentro da operação sincronizada. O coletor apenas solicita uma atualização; ele não carrega um booleano antigo até o `JobScheduler`. Essa escolha evita recriar um trabalho após desativação, limpeza ou pausa da recuperação, sem alterar a elegibilidade das previsões nem criar lançamentos automaticamente.
