# Backup e recuperação

Esta versão aceita somente o formato novo do Rendly: JSON `format=rendly`, versão 1, envelope criptográfico 2. Os formatos anteriores são incompatíveis, por decisão do proprietário de começar vazio. Não há conversão ou importador antigo. Leia [Armazenamento novo](armazenamento-rendly.md).

Em **Mais → Ajustes**, escolha **Criar backup criptografado**, informe e repita uma senha de pelo menos 12 caracteres e selecione um destino pelo Android. Guarde a senha separadamente: ela não fica salva e não pode ser recuperada.

O arquivo contém lançamentos, sugestões locais, contas e referências de saldo, regras e exceções de previsões, ciclos, reserva e preferências de apresentação. Nome de exibição, consentimento, permissões, biometria, categorias lembradas por estabelecimento e histórico de entrega de lembretes permanecem locais ao aparelho.

Para restaurar, use **Mais → Ajustes → Restaurar backup**, escolha o arquivo e informe a senha. O aplicativo confere formato, valores inteiros, limites, identificadores, vínculos e integridade em banco temporário. A prévia informa a quantidade de lançamentos, sugestões, contas e previsões. A substituição dos dados locais exige confirmação e ocorre em uma transação. Erros antes dessa substituição preservam a base atual. Captura e lembretes ficam pausados após restaurar.

A proteção usa AES-256-GCM, salt e nonce aleatórios e PBKDF2-HMAC-SHA256 com 600 mil iterações. O cabeçalho é autenticado. A senha incorreta e arquivos adulterados não fornecem dados em texto claro. Há limite de 16 MiB e de 50 mil itens.

Backups exportados não são apagados pelo aplicativo. Um CSV é um relatório e não substitui um backup. A perda da senha, a exclusão do arquivo ou um backup feito antes de registros recentes impedem recuperar esses registros. A versão antiga do banco é descartada na primeira abertura desta versão, conforme autorização registrada.
