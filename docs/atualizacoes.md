# Atualizações assinadas e compatibilidade

> Estado vigente: esta versão usa banco Rendly novo e descarta o armazenamento financeiro anterior, conforme autorizado. Backups antigos são incompatíveis. Veja [Armazenamento](armazenamento-rendly.md) e [Estado atual](estado-atual.md). O CI instrumental usa somente API 35, com suíte e reset intencional. As notas abaixo registram a política de preservação de versões anteriores e não se aplicam ao reset atual.


## Identidade e versões

O identificador permanece `com.mateuswilliam.financas`. A próxima versão preparada utiliza `versionCode=2` e `versionName=0.2.0`, com substituição por `-PappVersionCode` e `-PappVersionName`. O código inteiro precisa aumentar a cada distribuição. A assinatura de uma atualização deve ser compatível com a instalação anterior; uma chave debug aleatória de outro computador não atende a esse requisito.

O projeto não contém chave definitiva nem senha. A compilação debug funciona sem configuração de assinatura. Tarefas release recusam configuração ausente, parcial ou arquivo indisponível. Os valores vêm das variáveis de ambiente `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`.

## Preparação manual protegida

O workflow **Preparar versão assinada** só executa na branch principal e utiliza o ambiente GitHub `assinatura`. A configuração desse ambiente comporta proteção de acesso e aprovação conforme a política do responsável pelo repositório.

Os segredos necessários são `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` e `ANDROID_KEY_PASSWORD`. A variável pública `ANDROID_SIGNING_CERT_SHA256` contém a impressão digital SHA-256 esperada do certificado, sem os dois-pontos. O keystore é carregado em arquivo temporário com permissão restrita e removido ao encerrar o job. Base64 transporta o arquivo; não substitui criptografia nem proteção de segredos.

O workflow executa testes, Android Lint release, APK e AAB. A conferência verifica assinatura do APK, impressão digital esperada, identificador, versão e ausência da marca depurável. Os artefatos e seus checksums ficam disponíveis por sete dias. Não há criação de release, envio ao Google Play ou instalação em aparelho pessoal. Uma chave definitiva e a assinatura da instalação atual precisam ser identificadas antes da primeira distribuição.

Uma cópia protegida e recuperável da chave e das senhas é necessária fora do Git. A atualização pessoal por APK e o uso futuro de Play App Signing exigem decisões próprias sobre custódia da chave. A mudança de chave não deve ser resolvida orientando a desinstalação do aplicativo, pois isso pode apagar dados.

## Compatibilidade automatizada

O CI confere a recusa da preparação sem assinatura e usa uma chave descartável, disponível somente no runner, para assinar os APKs dos cenários de teste. O build release verifica a assinatura e o certificado do APK e do AAB. A versão e a ausência de depuração também são verificadas no APK.

Todos os testes instrumentados do workflow `.github/workflows/android.yml` rodam no Android 15 (API 35). A suíte completa e os dois cenários de atualização usam emuladores dedicados.

- **Reset legado.** O CI instala o APK da revisão `7aec1bd102fcfbfb18eed493ceeb9ca0ac89fefe`, prepara um `financas.db` fictício identificado como esquema 10 e habilita captura e lembretes nas preferências de teste. Depois instala o APK atual com `adb install -r`. O teste confere a remoção do banco legado autorizada pelo proprietário, a criação do banco Rendly no esquema 1, a pausa da captura e dos lembretes e a preservação da aparência e da ocultação de valores.
- **Atualização do Rendly v1.** O CI grava dados fictícios na versão `815528f` antes de instalar o APK atual com `adb install -r`. O teste confere o mesmo identificador de usuário do app e verifica o lançamento, o vínculo com a previsão, a recorrência, o ajuste mensal, a referência de saldo e as preferências.

Os relatórios recusam execuções vazias, falhas, interrupções e testes ignorados. A suíte só executa em emulador dedicado, pois seus testes limpam bases de desenvolvimento. Não deve ser usada em aparelho com dados pessoais.

A matriz não substitui avaliação de fabricantes, bateria, leitura de tela, sensores de biometria ou entrega real de notificações bancárias. Resultados efetivos ficam registrados em `qualidade.md` e nos jobs do pull request.

## Fontes

- [Assinatura de aplicativos](https://developer.android.com/studio/publish/app-signing).
- [Versionamento de aplicativos](https://developer.android.com/studio/publish/versioning).
- [Índices Room](https://developer.android.com/reference/androidx/room/Index).

A proteção das janelas do calendário é verificada na API 35 com `WindowInspector`. O código de compatibilidade para Android antigo não significa que testes nessas APIs tenham sido executados no CI atual. Um teste instrumental de permissões usa reflexão para acessar `revokePostNotificationPermissionWithoutKillForTest` no emulador; esse recurso de teste não faz parte do aplicativo distribuído.

O cenário dedicado também executa o exportador carregado do APK anterior antes da atualização. Depois da instalação da nova versão, o arquivo produzido é aberto pelo código atual, conferido em base temporária e restaurado após confirmação no teste. Um registro fictício acrescentado após a atualização permanece intacto até a restauração e só então é substituído. Isso complementa o vetor criptográfico independente com um arquivo efetivamente produzido pela versão anterior.

A inicialização do emulador tem limite de 180 segundos, e cada execução instrumentada tem limite de oito minutos. Interrupções reprovam a execução; os registros já produzidos permanecem no artefato. A coleta de capturas também possui prazo próprio, inclusive quando há falha. Não há repetição automática da suíte para obter aprovação.

As capturas do planejamento possuem limite interno de 15 segundos, além do limite da execução instrumentada. Falhas de captura continuam reprovando o cenário, e os marcadores de etapa permitem identificar se a interrupção ocorreu em fonte, orientação, imagem ou restauração das configurações do emulador.
