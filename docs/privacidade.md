# Como trato os dados

Versão 0.1.0 · 24/10/2026

Desenvolvo o Controle Financeiro como um aplicativo de registro financeiro local. Nesta versão, processo o conteúdo no aparelho e não mantenho servidor, cadastro de usuário, anúncios ou serviços de análise de uso.

## Dados que utilizo

Armazeno os lançamentos que forem confirmados: valor, data, movimentação, meio de pagamento, categoria, descrição, conta de origem e, quando aplicável, destino.

Mantenho também os nomes das contas cadastradas e sua ordem de uso, a última origem escolhida manualmente, o tema da interface e as escolhas de apresentação inicial e fechamento dos avisos. Esses nomes funcionam como apelidos locais; não solicito número de conta nem senha bancária para cadastrá-los.

Com consentimento e captura ativa, acesso o nome do aplicativo, identificador, horário, título e texto das notificações das origens selecionadas. O Android concede uma autorização ampla; aplico o filtro de origens antes de ler o texto.

A seleção mostra nomes de aplicativos com ícone de abertura disponíveis no perfil atual do Android. Guardo somente os pacotes selecionados nas preferências locais.

## Finalidade e retenção

Uso notificações para sugerir pendências que ainda dependem de confirmação. Descarto mensagens identificadas como senha, código ou autenticação. Após confirmar ou ignorar, apago o título e o texto originais. Mantenho identificadores locais e o estado da revisão para evitar o reprocessamento do evento.

Mantenho lançamentos confirmados até sua exclusão. A caixa pode conter informações financeiras e pessoais presentes em notificações pendentes; desligar a captura interrompe novas leituras, mas preserva o histórico já salvo. A opção de apagar todos os dados remove registros, pendências, contas e preferências e desativa a captura local.

## Compartilhamento e exportação

Não envio dados bancários, inventário de aplicativos ou credenciais para serviços externos. A exportação CSV é iniciada pelo usuário para um destino escolhido por ele e contém os lançamentos confirmados. Esse arquivo fica sob o controle do destino escolhido.

## Proteção e limitações

Uso o armazenamento privado do Android. O banco Room não recebe uma camada adicional de criptografia própria nesta versão. Desativo os backups automáticos de nuvem e transferência de dispositivo para evitar cópias não planejadas.

Ocultar totais mascara os valores do resumo e do histórico. Ao abrir um formulário de revisão ou edição, os campos continuam visíveis para conferência. Esse controle visual não substitui o bloqueio do aparelho.

A restauração de backup ainda está em desenvolvimento. Os dados locais podem ser perdidos ao desinstalar o aplicativo ou apagar seus dados no Android. Ainda preciso validar a proteção, recuperação e informações exigidas antes de disponibilizar uma versão pública de produção.

## Controle da captura

Ofereço a captura como opção. Recusar ou revogar a autorização preserva a possibilidade de lançar manualmente. O acesso às notificações e a permissão para enviar alertas são autorizações distintas do Android. A seleção de bancos pode ser modificada nos ajustes.
