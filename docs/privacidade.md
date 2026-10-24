# Privacidade e tratamento dos dados

Versão 0.1.0 · 24/10/2026

O Controle Financeiro é um aplicativo de registro financeiro local. Nesta versão, o processamento ocorre no aparelho, sem servidor, cadastro de usuário, anúncios ou serviços de análise de uso.

## Dados utilizados

São armazenados os lançamentos confirmados: valor, data, movimentação, meio de pagamento, categoria, descrição, conta de origem e, quando aplicável, destino.

Também são mantidos os nomes das contas cadastradas e sua ordem de uso, a última origem escolhida manualmente, o tema da interface e as escolhas de apresentação inicial e fechamento dos avisos. Esses nomes funcionam como apelidos locais; o cadastro não solicita número de conta nem senha bancária.

O planejamento armazena limites mensais por categoria, previsões recorrentes, vencimentos tratados e estado dos lembretes. As preferências de autenticação e proteção de tela permanecem neste aparelho. Biometria, PIN, padrão e senha do Android são tratados pelo sistema; o aplicativo recebe somente o resultado da autenticação.

Com consentimento e captura ativa, o aplicativo acessa o nome da origem, identificador, horário, título e texto das notificações selecionadas. O Android concede uma autorização ampla; o filtro de origens é aplicado antes da leitura do texto.

A seleção mostra nomes de aplicativos com ícone de abertura disponíveis no perfil atual do Android. Somente os pacotes selecionados são guardados nas preferências locais.

## Finalidade e retenção

As notificações geram sugestões pendentes de confirmação. Mensagens identificadas como senha, código ou autenticação são descartadas. Após a confirmação ou o descarte da pendência, o título e o texto originais são apagados. Identificadores locais e o estado da revisão são mantidos para evitar o reprocessamento do evento.

Lançamentos confirmados permanecem armazenados até sua exclusão. A caixa pode conter informações financeiras e pessoais presentes em notificações pendentes; desligar a captura interrompe novas leituras, mas preserva o histórico já salvo. A opção de apagar todos os dados remove registros, pendências, contas, orçamentos, recorrências, vencimentos tratados e preferências e desativa a captura local. Arquivos já exportados permanecem no destino escolhido.

## Compartilhamento e exportação

O aplicativo não envia dados bancários, inventário de aplicativos ou credenciais para serviços externos. A exportação CSV é iniciada pelo usuário para um destino escolhido por ele e contém os lançamentos confirmados. Esse arquivo fica sob o controle do destino escolhido.

O backup também depende de uma ação explícita e é entregue já criptografado ao seletor de arquivos. Inclui pendências, que podem conter texto bancário ainda não revisado, além de lançamentos e planejamento. O provedor de arquivos escolhido pode oferecer armazenamento local ou externo. A senha e a segurança desse destino ficam sob responsabilidade do usuário; o aplicativo não guarda uma cópia da senha.

Durante a conferência de uma restauração, os dados são decifrados em memória e inseridos em um banco temporário privado. A base temporária é excluída ao cancelar, concluir ou falhar a operação; arquivos abandonados após encerramento do processo são removidos na próxima inicialização. A exclusão lógica não garante apagamento físico de memória ou armazenamento flash. A restauração preserva as autorizações e a proteção locais, mantendo captura e lembretes pausados para conferência.

## Proteção e limitações

Os dados ficam no armazenamento privado do Android. O banco Room não recebe uma camada adicional de criptografia própria nesta versão. Os backups automáticos de nuvem e transferência de dispositivo estão desativados para evitar cópias não planejadas.

Ocultar totais mascara valores do resumo, histórico, gráficos e planejamento. Ao abrir um formulário de revisão ou edição, os campos continuam visíveis para conferência. Esse controle visual não substitui o bloqueio do aparelho.

O bloqueio opcional utiliza biometria forte ou credencial do aparelho, conforme as APIs disponíveis. A sessão não é salva como autenticação permanente. A proteção da imagem utiliza `FLAG_SECURE`, inclusive nos formulários e calendários nativos; seu comportamento pode variar por versão e fabricante. Esse recurso não criptografa o banco Room.

Os dados locais podem ser perdidos ao desinstalar o aplicativo ou apagar seus dados no Android. A recuperação exige um backup válido e sua senha original. O formato e os limites estão descritos em [Backup e recuperação](backup.md). A proteção e a recuperação em aparelhos reais e as informações exigidas antes da disponibilização de uma versão pública de produção ainda precisam de validação.

## Controle da captura

A captura é opcional. Recusar ou revogar a autorização preserva a possibilidade de lançar manualmente. O acesso às notificações e a permissão para enviar alertas são autorizações distintas do Android. A seleção de bancos pode ser modificada nos ajustes.

Os lembretes de vencimentos têm um controle próprio. Recusar alertas não interrompe uma captura já autorizada nem impede a revisão ou o planejamento dentro do aplicativo. Os alertas de captura e de vencimento não exibem valor, descrição ou conta da transação.
