# Meu ambiente de desenvolvimento

Uso Android Studio estável, Git e o JDK 17 para compilar este projeto. No SDK Manager mantenho Android SDK Platform 36, Android SDK Build-Tools 36.0.0 e Android SDK Platform-Tools. O Gradle também pode instalar a versão de Build Tools exigida pelo plugin quando a licença já estiver aceita.

## Minha cópia local

No terminal do Windows, faço a primeira cópia com:

```powershell
git clone https://github.com/Mateus1804/controle-financeiro-android.git
cd controle-financeiro-android
```

Abro essa pasta pelo botão **Open** do Android Studio e aguardo a sincronização do Gradle. Uso o projeto existente, incluindo os módulos `app` e `core` e o Gradle Wrapper versionado. Configuro o Gradle JDK como 17; o caminho do SDK fica no arquivo local `local.properties`, fora do Git.

## Minha execução no celular

Ativo as Opções do desenvolvedor e a Depuração USB no aparelho, conecto um cabo de dados e autorizo meu computador. Seleciono o dispositivo e executo o módulo `app` pelo Android Studio.

Para testar primeiro a interface sem conceder acesso a notificações, uso o botão **+**. Na versão de depuração, também posso abrir **Ajustes → Criar notificações fictícias**. Esses exemplos testam o preenchimento e a revisão; não validam o serviço Android nem a compatibilidade com bancos reais.

## Minha captura real

1. Abro **Ajustes** e ativo a captura.
2. Leio a explicação e decido se concordo.
3. Seleciono os aplicativos bancários instalados que desejo acompanhar.
4. Autorizo o Controle Financeiro no acesso às notificações do Android.
5. Permito os alertas de revisão, se desejar recebê-los fora do aplicativo.
6. Confiro uma nova notificação legítima já recebida do banco, sem realizar uma operação financeira só para testar o aplicativo.

O serviço acompanha eventos futuros. O acesso concedido pelo Android não fornece um extrato histórico. Caso eu recuse os alertas do aplicativo, as pendências capturadas continuam disponíveis na caixa de revisão.

## Minhas verificações

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

Executo o segundo comando com um aparelho ou emulador conectado. A primeira execução baixa as dependências. O projeto usa Gradle 8.13, AGP 8.13.2 e Kotlin 2.2.21, com versões fixas para reproduzir a compilação.

## Minhas atualizações

Configurei o GitHub Actions para executar testes de regras, Android Lint e a compilação em cada push para `main` e pull request. Um segundo job instala os APKs em um emulador Android 15 e executa os testes instrumentados de persistência e interface. Mantenho os resultados de cada execução separados dos testes bancários no meu aparelho.

O artefato `controle-financeiro-debug` contém o APK de depuração e fica disponível por 14 dias na execução correspondente. Trato esse APK como uma versão de desenvolvimento. A publicação na Google Play, a assinatura definitiva e as atualizações pela loja são etapas posteriores.

Mantenho bases reais, exportações, arquivos de assinatura e configurações locais fora do repositório. Antes de instalar uma versão que exija desinstalação, considero que a restauração de dados ainda está pendente nesta versão.
