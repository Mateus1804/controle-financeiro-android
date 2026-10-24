# Privacidade e tratamento dos dados

Versão 0.1.0 · 24/10/2026

O Controle Financeiro é um aplicativo de registro financeiro local. Nesta versão, o processamento ocorre no aparelho, sem servidor, cadastro de usuário, anúncios ou serviços de análise de uso.

## Dados utilizados

São armazenados os lançamentos confirmados: valor, data, movimentação, meio de pagamento, categoria, descrição, conta de origem e, quando aplicável, destino.

Também são mantidos os nomes das contas cadastradas e sua ordem de uso, a última origem escolhida manualmente, o tema da interface e as escolhas de apresentação inicial e fechamento dos avisos. Esses nomes funcionam como apelidos locais; o cadastro não solicita número de conta nem senha bancária.

Com consentimento e captura ativa, o aplicativo acessa o nome da origem, identificador, horário, título e texto das notificações selecionadas. O Android concede uma autorização ampla; o filtro de origens é aplicado antes da leitura do texto.

A seleção mostra nomes de aplicativos com ícone de abertura disponíveis no perfil atual do Android. Somente os pacotes selecionados são guardados nas preferências locais.

## Finalidade e retenção

As notificações geram sugestões pendentes de confirmação. Mensagens identificadas como senha, código ou autenticação são descartadas. Após a confirmação ou o descarte da pendência, o título e o texto originais são apagados. Identificadores locais e o estado da revisão são mantidos para evitar o reprocessamento do evento.

Lançamentos confirmados permanecem armazenados até sua exclusão. A caixa pode conter informações financeiras e pessoais presentes em notificações pendentes; desligar a captura interrompe novas leituras, mas preserva o histórico já salvo. A opção de apagar todos os dados remove registros, pendências, contas e preferências e desativa a captura local.

## Compartilhamento e exportação

O aplicativo não envia dados bancários, inventário de aplicativos ou credenciais para serviços externos. A exportação CSV é iniciada pelo usuário para um destino escolhido por ele e contém os lançamentos confirmados. Esse arquivo fica sob o controle do destino escolhido.

## Proteção e limitações

Os dados ficam no armazenamento privado do Android. O banco Room não recebe uma camada adicional de criptografia própria nesta versão. Os backups automáticos de nuvem e transferência de dispositivo estão desativados para evitar cópias não planejadas.

Ocultar totais mascara os valores do resumo e do histórico. Ao abrir um formulário de revisão ou edição, os campos continuam visíveis para conferência. Esse controle visual não substitui o bloqueio do aparelho.

A restauração de backup ainda está em desenvolvimento. Os dados locais podem ser perdidos ao desinstalar o aplicativo ou apagar seus dados no Android. A proteção, a recuperação e as informações exigidas antes da disponibilização de uma versão pública de produção ainda precisam de validação.

## Controle da captura

A captura é opcional. Recusar ou revogar a autorização preserva a possibilidade de lançar manualmente. O acesso às notificações e a permissão para enviar alertas são autorizações distintas do Android. A seleção de bancos pode ser modificada nos ajustes.
