# Ambiente de desenvolvimento

Utilizo Android Studio estável, Git e o JDK 17 como base para o desenvolvimento deste projeto. No SDK Manager, mantenho Android SDK Platform 36, Android SDK Build-Tools 36.0.0 e Android SDK Platform-Tools. O Gradle também pode instalar a versão de Build Tools exigida pelo plugin quando a licença já estiver aceita.

## Cópia local

Para trabalhar em uma cópia local, utilizo os seguintes comandos no terminal do Windows:

```powershell
git clone https://github.com/Mateus1804/controle-financeiro-android.git
cd controle-financeiro-android
```

Com o repositório clonado, abro a pasta no Android Studio pelo botão **Open**. O projeto já reúne os módulos `app` e `core` e o Gradle Wrapper versionado. Durante a configuração, defino o Gradle JDK como 17 e aguardo a sincronização das dependências.

Mantenho o caminho do SDK no arquivo local `local.properties`, que fica fora do Git.

## Execução no aparelho

Nos testes locais, executo o módulo `app` em um celular conectado ao Android Studio. A conexão utiliza um cabo de dados e depende da ativação das Opções do desenvolvedor e da Depuração USB, além da autorização do computador no aparelho.

Começo a avaliação da interface pelo cadastro manual, disponível no botão **+**. Na versão de depuração, também utilizo **Ajustes → Criar notificações fictícias** para conferir o preenchimento e a revisão. Esses exemplos não validam o serviço Android nem a compatibilidade com bancos reais.

## Captura de notificações

Trato a captura como um recurso opcional e organizo sua ativação em três etapas:

1. **Consentimento e seleção:** em **Ajustes**, a ativação da captura apresenta a explicação sobre o acesso. A continuidade depende da concordância com o tratamento descrito e da seleção dos aplicativos bancários instalados.
2. **Autorização do Android:** o Controle Financeiro precisa receber acesso às notificações nas configurações do sistema.
3. **Alertas de revisão:** a permissão para enviar alertas permite receber avisos fora do aplicativo. Ao recusar esses alertas, as pendências capturadas continuam disponíveis na caixa de revisão.

Nos testes de captura, confiro os dados sugeridos a partir de novas notificações legítimas dos bancos. O serviço acompanha eventos futuros; a autorização do Android não fornece um extrato histórico.

## Testes e compilação

Verifico as regras, a análise estática e a geração dos APKs com os comandos abaixo.

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

Utilizo um aparelho ou emulador conectado para os testes instrumentados do segundo comando. A primeira execução baixa as dependências. Mantenho Gradle 8.13, AGP 8.13.2 e Kotlin 2.2.21 com versões fixas para reproduzir a compilação.

## Integração contínua

Acompanho os resultados pelo GitHub Actions após cada push para `main` ou pull request. O fluxo executa testes de regras, Android Lint e a compilação. Um segundo job instala os APKs em um emulador Android 15 e executa os testes instrumentados de persistência e interface.

Registro os resultados da integração contínua separadamente dos testes bancários realizados no aparelho.

## APK de desenvolvimento e dados locais

Disponibilizo o APK de depuração no artefato `controle-financeiro-debug` da execução correspondente do GitHub Actions, com retenção de 14 dias. Esse pacote serve aos testes de desenvolvimento. A publicação na Google Play, a assinatura definitiva e as atualizações pela loja são etapas posteriores.

Preservo bases reais, exportações, arquivos de assinatura e configurações locais fora do repositório. A restauração de dados ainda está pendente nesta versão, e a desinstalação remove os dados locais do aplicativo.
