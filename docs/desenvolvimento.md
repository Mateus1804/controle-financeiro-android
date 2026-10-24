# Ambiente de desenvolvimento

Use Android Studio estável, Git e o JDK 17 para compilar este projeto. No SDK Manager, instale Android SDK Platform 36, Android SDK Build-Tools 36.0.0 e Android SDK Platform-Tools. O Gradle também pode instalar a versão de Build Tools exigida pelo plugin quando a licença já estiver aceita.

## Clone o repositório

No terminal do Windows, execute:

```powershell
git clone https://github.com/Mateus1804/controle-financeiro-android.git
cd controle-financeiro-android
```

Abra essa pasta pelo botão **Open** do Android Studio e aguarde a sincronização do Gradle. Use o projeto existente, incluindo os módulos `app` e `core` e o Gradle Wrapper versionado. Configure o Gradle JDK como 17 e mantenha o arquivo local `local.properties`, que contém o caminho do SDK, fora do Git.

## Execute no celular

Ative as Opções do desenvolvedor e a Depuração USB no aparelho, conecte um cabo de dados e autorize o computador. Selecione o dispositivo e execute o módulo `app` pelo Android Studio.

Para testar primeiro a interface sem conceder acesso a notificações, use o botão **+**. Na versão de depuração, abra também **Ajustes → Criar notificações fictícias** para experimentar o preenchimento e a revisão. Esses exemplos não validam o serviço Android nem a compatibilidade com bancos reais.

## Configure a captura real

1. Abra **Ajustes** e ative a captura.
2. Leia a explicação e decida se concorda com o acesso descrito.
3. Se concordar, prossiga com a configuração e selecione os aplicativos bancários instalados que deseja acompanhar.
4. Autorize o Controle Financeiro no acesso às notificações do Android.
5. Permita os alertas de revisão, se desejar recebê-los fora do aplicativo.
6. Aguarde uma nova notificação legítima do banco e confira os dados sugeridos no aplicativo.

O serviço acompanha eventos futuros. O acesso concedido pelo Android não fornece um extrato histórico. Ao recusar os alertas do aplicativo, as pendências capturadas continuam disponíveis na caixa de revisão.

## Execute as verificações

No Windows, execute:

```powershell
.\gradlew.bat :core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

No Linux ou macOS, execute:

```bash
./gradlew :core:test :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

Conecte um aparelho ou emulador antes de executar o segundo comando. A primeira execução baixa as dependências. O projeto usa Gradle 8.13, AGP 8.13.2 e Kotlin 2.2.21, com versões fixas para reproduzir a compilação.

## Acompanhe as atualizações

Acompanhe os resultados no GitHub Actions após cada push para `main` ou pull request. O fluxo executa testes de regras, Android Lint e a compilação. Um segundo job instala os APKs em um emulador Android 15 e executa os testes instrumentados de persistência e interface. Separe os resultados de cada execução dos testes bancários realizados no aparelho.

Baixe o artefato `controle-financeiro-debug` na execução correspondente para obter o APK de depuração. O artefato fica disponível por 14 dias. Trate esse APK como uma versão de desenvolvimento. A publicação na Google Play, a assinatura definitiva e as atualizações pela loja são etapas posteriores.

Mantenha bases reais, exportações, arquivos de assinatura e configurações locais fora do repositório. Antes de instalar uma versão que exija desinstalação, considere que a restauração de dados ainda está pendente nesta versão.
