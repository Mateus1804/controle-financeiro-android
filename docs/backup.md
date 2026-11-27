# Backup e recuperação

Esta versão aceita somente o formato novo do Rendly: JSON `format=rendly`, versão 1, envelope criptográfico 2. Os formatos anteriores são incompatíveis, por decisão do proprietário de começar vazio. Não há conversão ou importador antigo. Leia [Armazenamento novo](armazenamento-rendly.md).

Em **Ajustes**, escolha **Criar backup criptografado**, informe e repita uma senha de pelo menos 12 caracteres e selecione um destino pelo Android. Guarde a senha separadamente: ela não fica salva e não pode ser recuperada.

O arquivo contém lançamentos, contas, referências de saldo, ciclos e preferências de apresentação. Sugestões, previsões e eventos de reserva de versões anteriores do Rendly v1 também são preservados. Nome de exibição, consentimento, permissões, biometria, categorias lembradas por estabelecimento e histórico de entrega de lembretes permanecem locais ao aparelho.

Caracteres nulos nos textos visíveis das notificações são removidos durante a exportação e a leitura de backups Rendly v1. Os demais caracteres do texto são preservados.

Para restaurar, use **Ajustes → Restaurar backup**, escolha o arquivo e informe a senha. O aplicativo confere formato, valores inteiros, limites, identificadores, vínculos e integridade em banco temporário. A prévia informa a quantidade de lançamentos, sugestões, contas e previsões. A substituição dos dados locais exige confirmação e ocorre em uma transação. Erros antes dessa substituição preservam a base atual.

Se a gravação das preferências de exibição falhar após a substituição, o aplicativo informa que os dados financeiros foram restaurados e avisa sobre a falha nas preferências.

A limpeza do banco temporário privado é conferida após a restauração, o cancelamento ou uma falha. Se a limpeza ficar pendente, o aplicativo exibe um aviso e tenta novamente na próxima abertura ou operação de backup. O resultado da restauração é informado separadamente.

A proteção usa AES-256-GCM, salt e nonce aleatórios e PBKDF2-HMAC-SHA256 com 600 mil iterações. O cabeçalho é autenticado. A senha incorreta e arquivos adulterados não fornecem dados em texto claro. Há limite de 16 MiB e de 50 mil itens.

Backups exportados não são apagados pelo aplicativo. A perda da senha, a exclusão do arquivo ou um backup feito antes de registros recentes impedem recuperar esses registros. O reset do banco Controle Financeiro anterior ao Rendly v1 continua documentado no histórico da migração.
