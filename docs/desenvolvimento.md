# Ambiente de desenvolvimento

O ambiente de desenvolvimento utiliza Android Studio estável, Git e JDK 17. O SDK Manager deve incluir Android SDK Platform 36, Android SDK Build-Tools 36.0.0 e Android SDK Platform-Tools. O Gradle também pode instalar a versão de Build Tools exigida pelo plugin quando a licença já estiver aceita.

## Cópia local

A criação de uma cópia local utiliza os seguintes comandos no terminal do Windows:

```powershell
git clone https://github.com/Mateus1804/controle-financeiro-android.git
cd controle-financeiro-android
```

Após a clonagem, a pasta pode ser aberta no Android Studio pelo botão **Open**. O projeto já reúne os módulos `app` e `core` e o Gradle Wrapper versionado. A configuração requer Gradle JDK 17 e sincronização das dependências.

O caminho do SDK fica no arquivo local `local.properties`, fora do Git.

## Execução no aparelho

Nos testes manuais locais, o módulo `app` pode ser executado em um celular conectado ao Android Studio. A conexão utiliza um cabo de dados e depende da ativação das Opções do desenvolvedor e da Depuração USB, além da autorização do computador no aparelho.

A avaliação inicial da interface pode começar pelo cadastro manual, disponível no botão **+**. Na versão de depuração, **Ajustes → Criar notificações fictícias** permite conferir o preenchimento e a revisão. Esses exemplos não validam o serviço Android nem a compatibilidade com bancos reais.

## Captura de notificações

A captura é opcional, com ativação organizada em três etapas:

1. **Consentimento e seleção:** em **Ajustes**, a ativação da captura apresenta a explicação sobre o acesso. A continuidade depende da concordância com o tratamento descrito e da seleção dos aplicativos bancários instalados.
2. **Autorização do Android:** o Controle Financeiro precisa receber acesso às notificações nas configurações do sistema.
3. **Alertas de revisão:** a permissão para enviar alertas permite receber avisos fora do aplicativo. Ao recusar esses alertas, as pendências capturadas continuam disponíveis na caixa de revisão.

Os testes de captura devem conferir os dados sugeridos a partir de novas notificações legítimas dos bancos. O serviço acompanha eventos futuros; a autorização do Android não fornece um extrato histórico.

## Testes e compilação

Os comandos abaixo verificam as regras, a análise estática e a geração dos APKs.

No Windows:

```powershell
.\gradlew.bat :core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

No Linux ou macOS:

```bash
./gradlew :core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

Os testes instrumentados do segundo comando devem ser executados somente em um emulador dedicado, sem dados reais. A suíte reinicia a base de desenvolvimento, e o runner bloqueia aparelhos físicos para preservar uma instalação de uso pessoal. O celular fica reservado aos testes manuais do aplicativo. A primeira execução baixa as dependências. Gradle 8.13, AGP 8.13.2 e Kotlin 2.2.21 têm versões fixas para reproduzir a compilação.

## Integração contínua

Os resultados ficam disponíveis no GitHub Actions após cada push para `main` ou pull request. O fluxo executa testes de regras, Android Lint e a compilação. Um segundo job instala os APKs em um emulador Android 15 e executa os testes instrumentados de persistência e interface.

Os resultados da integração contínua são registrados separadamente dos testes bancários realizados no aparelho.

## APK de desenvolvimento e dados locais

O APK de depuração fica disponível no artefato `controle-financeiro-debug` da execução correspondente do GitHub Actions, com retenção de 14 dias. Esse pacote serve aos testes de desenvolvimento. A publicação na Google Play, a assinatura definitiva e as atualizações pela loja são etapas posteriores.

Bases reais, exportações, arquivos de assinatura e configurações locais devem permanecer fora do repositório. A restauração de dados ainda está pendente nesta versão, e a desinstalação remove os dados locais do aplicativo.

## Atualizações dos dados e da interface

O esquema Room fica em `app/schemas`, e a migração 1→2, em `FinanceDatabase.kt`. Os testes de migração reconstroem uma base isolada a partir do esquema 1 exportado e abrem essa base pelo Room atual, verificando registros, pendências e o novo cadastro de contas.

O comando `python3 scripts/verify-contrast.py` confere os dez pares principais de texto e fundo. Esse resultado complementa a inspeção visual; não substitui a avaliação com leitor de tela.

A instalação de uma atualização sobre uma base que deve ser preservada exige o mesmo identificador do aplicativo e a mesma chave de assinatura. Os APKs de depuração produzidos em ambientes diferentes podem usar chaves diferentes. Para o uso pessoal, a chave local do Android Studio deve ser preservada. A desinstalação de uma instalação com dados reais não deve ser utilizada para contornar uma incompatibilidade de assinatura.

## Backup e planejamento

O esquema atual é Room 3, com migrações aditivas 1→2 e 2→3. Os testes de migração abrem bases isoladas reconstruídas dos esquemas anteriores exportados e conferem os dados existentes antes de utilizar as novas tabelas. `BackupStoreTest` cobre o envelope criptográfico no Android, a base temporária, o cancelamento, entradas inválidas e o rollback de uma restauração com falha simulada.

A recuperação usa **Ajustes → Restaurar backup**, senha original e confirmação da prévia. Um teste manual utiliza emulador dedicado e arquivo com dados fictícios; a instalação de uso pessoal permanece preservada. Os arquivos `.cfbackup` e a pasta `casos-locais/` ficam fora do Git. O formato e os limites estão descritos em [Backup e recuperação](backup.md).

O planejamento fica disponível no resumo e em Ajustes. Limites pertencem a um mês e uma categoria. Recorrências são previsões e não aparecem no CSV ou nos totais antes da confirmação de um vencimento. Os lembretes são periódicos, sem alarme exato; a execução em segundo plano ainda depende das condições do Android.

## Casos bancários

```bash
python3 scripts/verify-bank-fixtures.py
./gradlew :core:test --tests '*BankNotificationFixturesTest'
```

Os recursos de teste contêm exemplos sintéticos identificados. Notificações reais exigem anonimização revisada e metadados de versão, aparelho e coleta. O procedimento fica em [Validação bancária](validacao-bancaria.md); a execução dos casos de texto não comprova entrega pelo serviço em um aparelho.

## Avaliação da proteção

Os testes automatizados conferem política de sessão, persistência, bloqueio da interface, links de notificação e flags das janelas. O teste de inspeção das janelas nativas exige API 29 ou superior; a execução de referência utiliza Android 15. A confirmação biométrica ou por PIN, a revogação da credencial e o comportamento do seletor precisam de testes manuais em aparelho configurado.

A proteção pode ser avaliada em **Ajustes → Autenticar ao abrir**. O recurso depende de PIN, padrão ou senha no Android. A opção de ocultar a imagem também impede capturas e compartilhamento de tela; capturas públicas de evidência utilizam emulador e dados fictícios, com essa opção desativada. A documentação não deve apresentar uma tela capturada nessas condições como comprovação de proteção contra capturas.
