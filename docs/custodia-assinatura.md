# Custódia da assinatura definitiva

## Situação atual

A chave definitiva, seu responsável e o certificado da instalação já utilizada ainda não foram identificados nesta tarefa. Não foi criada uma chave nem uma cópia de reserva neste ambiente temporário. A chave descartável do CI serve apenas aos testes e não pode ser usada para distribuir atualizações.

Antes de distribuir um APK, o proprietário precisa registrar os três pontos abaixo, fora do repositório:

| Dado | Destino recomendado | Situação |
| --- | --- | --- |
| Chave principal e senhas | Cofre criptografado sob controle do proprietário, com recuperação e acesso restrito | Destino ainda não informado |
| Cópia de reserva | Mídia ou cofre criptografado independente do principal; deve continuar acessível se a conta ou máquina principal for perdida | Destino ainda não informado |
| Identidade de assinatura | Responsável, alias, validade e impressão digital pública SHA-256 do certificado | Certificado atual ainda não identificado |

Duas pastas na mesma máquina, duas cópias neste workspace e uma chave anexada ao GitHub não constituem uma reserva independente. As senhas e o arquivo privado não devem ser enviados por conversa, PR, issue ou commit.

## Procedimento após a definição dos destinos

1. Identificar a assinatura de um APK já instalado/distribuído com `apksigner verify --print-certs caminho/app-atual.apk`. O SHA-256 do certificado é público; o APK pode conter dados de configuração e deve ser tratado pelo proprietário.
2. Localizar a chave compatível e conferir certificado, alias e validade com `keytool -list -v -keystore caminho/assinatura.p12`. Informar a senha no prompt, sem incluí-la na linha de comando ou no histórico do terminal.
3. Se ainda não houver distribuição com assinatura definitiva, definir a estratégia para distribuição direta e eventual Play App Signing antes de criar a chave em máquina controlada pelo proprietário. Não gerar outra chave para substituir uma existente sem analisar a compatibilidade.
4. Guardar a chave e os dados necessários à recuperação no destino principal escolhido. Criar a cópia independente e verificar que pode ser lida; registrar somente o resultado da conferência, sem publicar o arquivo privado.
5. Fazer uma recuperação de ensaio a partir da cópia, em ambiente controlado, e comparar o SHA-256 do certificado. Confirmar que é possível assinar e verificar um APK de teste. Uma cópia não verificada permanece uma pendência.
6. Configurar os segredos do ambiente GitHub `assinatura` conforme [Atualizações assinadas](atualizacoes.md). Restringir o acesso ao ambiente e configurar o certificado público esperado. Os segredos do CI não substituem a cópia independente.
7. Testar atualização sobre a instalação anterior em Android 15/API 35, sem desinstalar e com backup recuperável. Só distribuir quando certificado, versão e preservação dos dados estiverem conferidos.

Se a chave da instalação anterior não estiver disponível, interromper a preparação da atualização. Não recomendar desinstalação como atalho: essa ação pode apagar os dados locais.

## Verificação desta entrega

Foi revisada a configuração já existente de Gradle, o workflow de assinatura e as exclusões do Git. Não foram lidos arquivos privados nem valores de segredos. Nenhuma chave real foi criada, copiada ou enviada. A etapa operacional permanece pendente da escolha de custódia pelo proprietário e da identificação do certificado atual; este documento não comprova que exista uma cópia recuperável.
