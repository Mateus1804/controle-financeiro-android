# Estado atual do Rendly

A fila foi integrada na main por merge commit, mantendo as branches: #24, #25, #26, #27, #28, #29, #35, #30, #31, #32, #33, #37, #38 e #34. A integração terminou em e333598. O #36 foi fechado sem merge, com comentário de que assinatura está fora do escopo.

A main contém a tela principal única, testes migrados para a interface real, estados de navegação/edição/revisão separados, acesso ao cadastro de saldos, gestão de categorias lembradas e melhorias no gráfico e na acessibilidade. O banco e o backup ainda são os existentes; o reset solicitado para a próxima etapa ainda não foi aplicado.

Após a instrução de acelerar os merges, não foram aguardados novos testes por PR a partir do #26. Não se apresenta essa integração como retestada individualmente. Depois da integração, o comando local de core, build do app/APK de testes e lint passou; o Gradle reaproveitou tarefas sem mudança. O CI automático da main está executando a validação na API 35. O último conjunto de código anterior à integração tinha 226 testes JVM do core e 240 testes instrumentados aprovados; isso não substitui o resultado do novo CI da main.

Não foram realizados testes manuais de TalkBack, biometria ou notificações reais em aparelho físico. Os exemplos de notificações disponíveis são sintéticos.

Riscos: a assinatura definitiva está fora do escopo; a previsão até salário ainda desconta a meta total da reserva até a alteração do Prompt 2; a compatibilidade de notificações depende dos textos recebidos. O proprietário confirmou que a próxima estrutura começará vazia, descartando os dados atuais e sem leitura dos backups antigos. Essa perda e a incompatibilidade serão documentadas antes da alteração do armazenamento.
