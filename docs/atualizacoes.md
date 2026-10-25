# Atualizações assinadas e compatibilidade

## Identidade e versões

O identificador permanece `com.mateuswilliam.financas`. A próxima versão preparada utiliza `versionCode=2` e `versionName=0.2.0`, com substituição por `-PappVersionCode` e `-PappVersionName`. O código inteiro precisa aumentar a cada distribuição. A assinatura de uma atualização deve ser compatível com a instalação anterior; uma chave debug aleatória de outro computador não atende a esse requisito.

O projeto não contém chave definitiva nem senha. A compilação debug funciona sem configuração de assinatura. Tarefas release recusam configuração ausente, parcial ou arquivo indisponível. Os valores vêm das variáveis de ambiente `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`.

## Preparação manual protegida

O workflow **Preparar versão assinada** só executa na branch principal e utiliza o ambiente GitHub `assinatura`. A configuração desse ambiente comporta proteção de acesso e aprovação conforme a política do responsável pelo repositório.

Os segredos necessários são `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`. A variável pública `ANDROID_SIGNING_CERT_SHA256` contém a impressão digital SHA-256 esperada do certificado, sem os dois-pontos. O keystore é carregado em arquivo temporário com permissão restrita e removido ao encerrar o job. Base64 transporta o arquivo; não substitui criptografia nem proteção de segredos.

O workflow executa testes, Android Lint release, APK e AAB. A conferência verifica assinatura do APK, impressão digital esperada, identificador, versão e ausência da marca depurável. Os artefatos e seus checksums ficam disponíveis por sete dias. Não há criação de release, envio ao Google Play ou instalação em aparelho pessoal. Uma chave definitiva e a assinatura da instalação atual precisam ser identificadas antes da primeira distribuição.

Uma cópia protegida e recuperável da chave e das senhas é necessária fora do Git. A atualização pessoal por APK e o uso futuro de Play App Signing exigem decisões próprias sobre custódia da chave. A mudança de chave não deve ser resolvida orientando a desinstalação do aplicativo, pois isso pode apagar dados.

## Compatibilidade automatizada

O CI primeiro confere a recusa explícita da preparação sem assinatura. Depois prepara uma chave descartável de dois dias exclusivamente no runner. A versão anterior fica congelada na revisão `7aec1bd102fcfbfb18eed493ceeb9ca0ac89fefe`, com versão 1 e esquema Room 3. Os APKs anterior, atual e de testes são assinados com a mesma chave de teste; o caminho Gradle release também compila APK e AAB, executa lint e confere assinatura, certificado, versão e ausência de depuração. Essa chave não é incluída nos artefatos e não serve para atualizações definitivas.

A matriz executa a suíte em emuladores das APIs 26, 33 e 35. Um quarto cenário, na API 35, instala o APK anterior, prepara dados fictícios no esquema 3 e preferências, e instala o APK atual com `adb install -r`, sem desinstalar entre essas duas versões. O teste confere valores, identificadores, vínculos, revisão das pendências, contas, orçamento, recorrência, ocorrências, aparência e ocultação de valores após a migração para o esquema 4.

Os relatórios recusam execuções vazias, falhas, interrupções e testes ignorados. A suíte só executa em emulador dedicado, pois seus testes limpam bases de desenvolvimento. Não deve ser usada em aparelho com dados pessoais.

A matriz não substitui avaliação de fabricantes, bateria, leitura de tela, sensores de biometria ou entrega real de notificações bancárias. Resultados efetivos ficam registrados em `qualidade.md` e nos jobs do pull request.

## Fontes

- [Assinatura de aplicativos](https://developer.android.com/studio/publish/app-signing).
- [Versionamento de aplicativos](https://developer.android.com/studio/publish/versioning).
- [Índices Room](https://developer.android.com/reference/androidx/room/Index).

Na API 26, a proteção do calendário é conferida diretamente na janela nativa retornada pelo componente, pois `WindowInspector` só existe a partir da API 29. Nas APIs 33 e 35, a verificação percorre as janelas abertas pelo formulário. Nenhuma reflexão em API privada é utilizada.
