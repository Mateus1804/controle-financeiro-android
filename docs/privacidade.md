# Privacidade e tratamento dos dados

O Rendly registra lançamentos, contas, saldos iniciais e preferências no armazenamento privado do Android. Os nomes das contas funcionam como apelidos. O cadastro pede valor, data e os detalhes necessários para a movimentação.

Instalações e backups do Rendly v1 podem conter previsões antigas e sugestões geradas por notificações em versões anteriores. Esses dados continuam preservados para compatibilidade. O formulário atual é manual e o app dispensa permissões de captura e envio de notificações.

## Exibição e proteção

A opção de ocultar valores mascara montantes e percentuais na interface e nos rótulos acessíveis. A edição de valor exige revelação explícita enquanto essa opção está ativa.

O bloqueio opcional usa a autenticação do aparelho. Biometria, PIN, padrão e senha são tratados pelo Android. O Rendly recebe o resultado da autenticação. A proteção da tela usa `FLAG_SECURE` para impedir capturas e ocultar a imagem nos aplicativos recentes.

## Backup

O app mantém o backup automático do Android desativado. O backup manual é criptografado antes de ser entregue ao seletor de arquivos e depende da senha escolhida pelo usuário. O destino pode ser o próprio aparelho ou um provedor de arquivos de sua escolha.

A restauração confere os dados em um banco temporário privado antes da confirmação. Se a limpeza dessa base ficar pendente, o app exibe um aviso em Ajustes. Para recuperar dados após apagar a instalação, use um backup válido e a senha correspondente. [Backup e recuperação](backup.md)
