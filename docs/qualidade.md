# Qualidade e validação

## Resultado consolidado — 25/10/2026

A revisão funcional `395029a078b1052b7aa564e4ee5755eeff786b61` concluiu a execução [40202162302](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40202162302) com todos os jobs aprovados. Os resultados abaixo distinguem testes únicos de repetições da mesma suíte em versões diferentes do Android.

| Verificação | Resultado efetivo |
| --- | --- |
| Testes Kotlin do core | 104 aprovados; zero falhas, erros ou testes ignorados. |
| Procedência das notificações | Cinco regressões Python aprovadas; 25 casos sintéticos conferidos; zero amostras reais. |
| Android API 26 | 81 testes aprovados; nenhum teste ignorado. |
| Android API 33 | 81 testes aprovados; nenhum teste ignorado. |
| Android API 35 | 81 testes aprovados; nenhum teste ignorado. |
| Android API 36 | 81 testes aprovados; nenhum teste ignorado. |
| Atualização instalada e backup entre APKs | Duas fases aprovadas: exportação no APK anterior e recuperação no APK atualizado. |
| Android Lint debug e release | Zero erros ou falhas fatais; 14 avisos existentes em cada variante. |
| Compilação | APK debug, APK de testes, APK release e AAB concluídos. |
| Assinatura de teste | Ausência de configuração recusada; certificado, identidade, versões, checksums e ausência de depuração do release conferidos. |
| Migração e histórico | Esquemas 1/2/3 preservados até o 4, índices conferidos pelo plano SQL e cenário com cinco mil registros aprovado. |

As 81 verificações Android são repetidas em quatro ambientes, e não apresentadas como 324 comportamentos diferentes. Os casos de recuperação utilizam dados fictícios. A chave de CI é descartável; a chave definitiva não foi disponibilizada nesta revisão, e o workflow protegido de preparação não foi executado.

Foram inspecionadas capturas do resumo claro e do planejamento escuro da API 26. A inspeção de imagens e os testes automatizados não equivalem a uso em aparelho físico, TalkBack, autenticação biométrica real ou entrega efetiva de notificações bancárias. A interpretação real de Inter, Nubank, PicPay, Mercado Pago e PagBank continua pendente de amostras anonimizadas e avaliação no aparelho.

A consolidação posterior da infraestrutura de teste usa callbacks de ciclo de vida para a rotação, marcadores de etapa e captura com prazo próprio. O [pull request de confiabilidade](https://github.com/Mateus1804/controle-financeiro-android/pull/4) mantém a execução correspondente a essa revisão, separada da referência funcional acima.

## Referência e falhas anteriores

A primeira entrega teve 31 testes Kotlin e 13 testes Android aprovados na execução [40100369119](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40100369119). A revisão anterior da interface teve 51 testes Kotlin e 41 testes Android aprovados em [40124797196](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40124797196).

Antes dos opcionais, a revisão editorial apresentou uma falha na execução [40129480372](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40129480372): o teste encontrava tanto o título quanto a aba chamados Ajustes. A compilação e o Lint haviam passado. O seletor foi corrigido para identificar a aba clicável, preservando as verificações do tema e da recriação.

A tentativa local de executar Gradle falhou no download da distribuição com `Network is unreachable`, antes do início dos testes. A validação executável ocorreu no GitHub Actions; a indisponibilidade local não foi registrada como teste aprovado.

## Opcionais — 24/10/2026

O código do commit `72c822d93d5a732236dce0c90517d070d28d35f7` foi validado na execução [40145275584](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40145275584). O commit posterior de documentação e imagens não altera o código validado.

| Comando ou verificação | Resultado |
| --- | --- |
| `:core:test` | 102 testes aprovados; zero falhas, erros ou testes pulados. |
| `bash scripts/run-device-tests.sh` | 76 testes instrumentados aprovados no Android 15, API 35, x86_64; zero testes pulados. |
| `:app:lintDebug` | Zero erros; 14 avisos registrados abaixo. |
| `:app:assembleDebug` | APK de depuração gerado. |
| `:app:assembleDebugAndroidTest` | APK da suíte instrumentada gerado. |
| `python3 scripts/verify-wrapper.py` | SHA-256 do Gradle Wrapper conferido. |
| `python3 scripts/verify-manifest.py` | Permissões limitadas e serviços protegidos; backup automático do Android desativado. |
| `python3 scripts/verify-contrast.py` | Dez pares explícitos de texto e fundo acima de 4,5:1. |
| `python3 scripts/verify-bank-fixtures.py` | 25 casos sintéticos; zero casos reais anonimizados. |
| `git diff --check` | Sem problemas de espaços no diff local. |

O resultado consolidado é de **178 testes aprovados**. Os testes instrumentados utilizaram emulador Pixel 2 com 720 × 1600 pixels e densidade 320, aproximadamente 360 × 800 dp. A suíte usa `IsolatedAndroidTestRunner`, que bloqueia aparelhos físicos para impedir a limpeza acidental de uma instalação com dados pessoais.

Os avisos de Lint restantes são um de SDK alvo, dez de atualização de dependências e três `UseKtx` nas gravações verificadas de preferências e da atividade. Os três avisos `UseKtx` permanecem justificados: a extensão KTX `edit` retorna `Unit` e não expõe o resultado de `commit()`, que precisa ser verificado antes de substituir dados ou afirmar que a proteção foi salva. A API padrão é intencional nesses pontos. Os avisos de coleção mutável em estado Compose e conversão de URI foram corrigidos. Não houve supressão de regras. Entre os avisos de compilação estão duas APIs de compatibilidade obsoletas: a alternativa de credencial para versões anteriores e `isStatusBarContrastEnforced` nas janelas de diálogo. As versões da stack permaneceram fixadas.

## Critérios exercitados

- **Dinheiro e contas:** centavos automáticos, colagem, limites, edição, cursor, nomes normalizados, pesquisa, contas recentes e preservação de campos durante cadastro e recriação.
- **Banco e migrações:** abertura das versões 1 e 2 pelo Room atual, preservação de histórico e nomes antigos, novas tabelas do esquema 3 e vínculos das pendências.
- **Criptografia e formato:** ida e volta, senha incorreta, adulteração do conteúdo e do cabeçalho autenticado, versões desconhecidas, custo de derivação fixo, limites de tamanho e senha Unicode. Um vetor portátil com dados fictícios é conferido no Kotlin/JVM e no Android.
- **Restauração:** conferência em base temporária, comparação das tabelas, confirmação da prévia, cancelamento, entrada fracionária em centavos, identificadores repetidos, vínculos inválidos, fluxo limitado de leitura e preservação da base após falha simulada da transação.
- **Persistência e exclusão:** uma pausa que não pode ser persistida impede a restauração ou a exclusão. Falha da exclusão preserva registros e proteção. Falha posterior na limpeza das preferências é distinguida de uma exclusão financeira já concluída. A configuração de bloqueio não é apresentada como salva após uma falha simulada de gravação.
- **Seletor de arquivos:** criação do backup pelo seletor real do Android, abertura posterior do arquivo, senha, prévia, confirmação explícita e recuperação do mesmo identificador e valor após apagar a base fictícia de teste.
- **Duplicidade:** correspondência por valor, tipo, data, origem e descrição, contas distintas do mesmo banco, limites da janela, ordem estável e preservação dos lançamentos manuais após confirmar uma sugestão.
- **Revisão em sequência:** progresso, próxima pendência por ação explícita, novas notificações fora da sessão, preservação de edição e conferência da atualização da própria notificação.
- **Planejamento:** limites por mês e categoria, despesas líquidas, estornos, períodos vazios e fuso; previsões fora dos totais e do CSV até confirmação; meses curtos, ano bissexto, vencimento pulado, pausa e confirmação simultânea sem lançamento duplicado.
- **Alertas e captura:** concessão e revogação do acesso real ao serviço no emulador, atualização ao retomar a atividade, recusa de alertas sem bloquear captura ou revisão e prevenção de consumir um lembrete quando o envio é recusado. O agendamento periódico persistente também é conferido.
- **Proteção e interface:** política de sessão, preferências persistentes, ocultação das telas e da entrada de links pelo handler real com atividade bloqueada, flags de proteção incluindo calendário nativo, temas, cor renderizada das barras do Android, fonte em 150%, teclado e orientação horizontal.

O teste de links utiliza a instrumentação do Android para entregar o Intent ao handler real da atividade. A limpeza restaura o Intent de abertura exigido pelo monitor do `ActivityScenario`; isso não altera o fluxo de produção nem dispensa as verificações do bloqueio.

O validador de procedência também passou por quatro verificações locais negativas com dados fictícios: identificador repetido, exemplo sintético sem identificação, caso real sem metadados e possível documento pessoal. Esses testes do validador não foram contados como notificações reais nem somados aos testes JUnit da tabela.

## Inspeção visual e evidências

As capturas de resumo, ajustes, relatórios, revisão, possíveis duplicidades e planejamento foram inspecionadas nos temas claro e escuro. Os cenários também incluem fonte ampliada, teclado e orientação horizontal. A inspeção identificou fundo claro nas barras do Android com o tema escuro; o fundo da janela foi alinhado ao tema e recebeu verificação de cor renderizada. A navegação em telas estreitas com fonte ampliada foi adaptada para impedir a quebra dos rótulos, mantendo o botão de novo lançamento em espaço reservado. O cenário em 150% confere que Resumo permanece em uma linha, sem abreviação, e as capturas aguardam a renderização dos ícones do sistema.

As [imagens publicadas](imagens/) utilizam somente dados fictícios e foram copiadas sem edição dos PNGs da execução 40145275584. As capturas do planejamento aguardam o encerramento da mensagem temporária de confirmação. Os diálogos de senha e prévia usam proteção explícita contra capturas; seu funcionamento é verificado pelo teste de interface e pelos resultados da restauração.

As evidências completas, os APKs e os relatórios permanecem nos artefatos do GitHub Actions por 14 dias. As imagens selecionadas permanecem versionadas. A conferência dos dez pares de cores e os testes de semântica complementam a inspeção; não representam auditoria completa de acessibilidade.

## Limitações e pendências

- Não foram fornecidas nem validadas notificações reais de Inter, Nubank, PicPay, Mercado Pago, PagBank ou outras origens. Os 25 casos são sintéticos. A entrega e os formatos reais dependem de [coleta anonimizada e evidências do aparelho](validacao-bancaria.md).
- A conclusão de autenticação por biometria ou PIN, o comportamento com mudanças da credencial e a proteção no seletor de aplicativos de fabricantes exigem teste manual no aparelho. Os testes automatizados de bloqueio e sessão não substituem essa avaliação.
- O agendamento e as regras de elegibilidade e repetição dos lembretes foram testados; entrega após ciclos reais de reinício, Doze, parada forçada e restrições do fabricante permanece pendente. Não há garantia de horário exato ou entrega exatamente uma vez em caso de interrupção entre publicação e persistência.
- TalkBack manual, outras versões do Android e outros provedores do seletor de arquivos não foram avaliados. O caminho específico do Android 8.0 ainda exige execução própria. A inspeção automatizada das janelas nativas requer API 29 ou superior e foi executada na API 35.
- A recuperação com dados pessoais e a atualização do APK no aparelho com a mesma chave ainda exigem conferência própria. APKs de depuração de ambientes diferentes podem ter assinaturas distintas; desinstalar para contornar a assinatura remove os dados locais.
- A criptografia do backup e o bloqueio da interface não criptografam o banco Room. A senha não é recuperável. Limpar buffers e excluir arquivos não garante apagamento físico. O formato não recebeu auditoria criptográfica independente.
- Contas com saldo inicial, conciliação, faturas, parcelas, metas, nuvem, OCR e integração oficial com Open Finance continuam fora desta entrega. O resultado mensal não é apresentado como saldo bancário conciliado.
- Assinatura de produção, informações públicas exigidas pela loja e revisão para publicação permanecem pendentes. A análise técnica não representa aprovação da Google Play.

## Revisão de compatibilidade e consultas — 25/10/2026

A referência inicial mantém a suíte anterior de 102 testes do core e 76 instrumentados. A execução Gradle local ficou impedida antes da compilação pela indisponibilidade de acesso a `services.gradle.org`; isso não foi classificado como teste aprovado.

As verificações Python de procedência (cinco regressões), os 25 casos sintéticos, o checksum do Wrapper, os dez pares de contraste, a geração da base fictícia do esquema 3 e a sintaxe dos scripts foram aprovados localmente. Nenhuma notificação bancária real nem chave definitiva foi fornecida.

A execução completa das alterações — testes Kotlin, Android Lint, compilação, assinatura descartável, APIs 26/33/35, restauração do vetor congelado e atualização instalada — deve ser conferida nos jobs do pull request. Esta anotação não antecipa sucesso de tarefas ainda não concluídas.

### Primeira passagem da matriz ampliada

A execução [40196079192](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40196079192) aprovou 104 testes do core, compilação debug/release, assinatura de teste e lint debug sem erros, conservando os 14 avisos da referência. A API 33 aprovou 80 testes, e o cenário independente de atualização instalada na API 35 aprovou a preservação dos dados.

Na API 26, a primeira passagem executou 79 testes e encontrou quatro falhas: interação com o seletor de documentos, sincronização do cadastro de conta, uso de um comando de acesso às notificações ausente nessa versão e uma espera por pixels de ícones fixos na barra do sistema em paisagem. O calendário tinha ainda um teste restrito a API 29 por depender de `WindowInspector`, portanto não integrava aqueles 79 casos. Esses resultados não foram classificados como aprovação da API 26.

A revisão acrescenta identificação estável e espera pelo campo da conta, interação com controles habilitados no seletor e evidências em caso de falha. O teste de revogação utiliza a configuração disponível no Android antigo e bloqueia alertas separadamente por AppOps. A captura de paisagem aguarda a configuração da barra em vez de presumir ícones em coordenadas fixas. A janela do calendário pode ser conferida diretamente na API 26 pelo componente real, mantendo a inspeção completa das janelas nas versões recentes. A nova passagem do CI determina o resultado final dessas correções.

A matriz final também inclui a API 36, correspondente ao SDK alvo do projeto. O resultado fica separado das APIs 26, 33 e 35; a imagem dessa API usa Google APIs, enquanto as demais conservam a imagem padrão já utilizada.

A segunda passagem, [40197319760](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40197319760), aprovou a preparação de APK/AAB release, identidade, certificado e recusa de assinatura ausente. Na API 26, 80 testes passaram a ser executados, restando duas falhas no teste de interação: os itens da grade do DocumentsUI não anunciavam ancestral clicável e a verificação de alertas ainda pressupunha a permissão runtime da API 33. O ajuste usa toque real no item habilitado da grade e verifica a ação de alertas conforme o modelo efetivo de permissão da versão. A recusa runtime de alertas é exercitada nas APIs 33 e superiores; a API 26 verifica o estado global disponível, sem fingir uma permissão inexistente.

Na terceira passagem, [40198183641](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40198183641), as APIs 26 e 33 aprovaram os 80 testes, sem testes ignorados. A API 35 revelou uma corrida no agendamento nativo: uma solicitação atrasada podia recriar o trabalho após o cancelamento. O agendador foi ajustado para ler a preferência vigente dentro de uma operação sincronizada; uma emissão antiga não determina mais o estado aplicado. Um teste com solicitação deliberadamente atrasada verifica que a desativação continua prevalecendo. O resultado consolidado dessa correção está registrado no início deste documento.

A mesma terceira passagem aprovou os 80 testes na API 36. O cenário dedicado comprovou a exportação pelo APK anterior (versão 1, esquema 3), a atualização por instalação preservando dados e a restauração daquele arquivo pelo APK atual (versão 2, esquema 4), com uma execução de teste em cada fase. Esses resultados são separados da falha do agendador encontrada na suíte da API 35.

A quarta passagem, [40199499682](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40199499682), aprovou 81 testes nas APIs 26, 33 e 35 após a correção do agendador. Na API 36, a única falha foi um toque antecipado no cadastro de conta durante o reposicionamento provocado pelo teclado: a hierarquia mostrava que o formulário havia sido fechado. O teste passou a aguardar a estabilidade da geometria antes de rolar e tocar o botão. O toque continua sendo físico, e a preservação do valor, a conta escolhida e a confirmação no banco continuam verificadas; não há repetição automática do teste inteiro nem supressão do caso.

A sincronização utiliza espera limitada por condição, conforme a [orientação de testes Compose](https://developer.android.com/develop/ui/compose/testing/synchronization). O relógio Compose não controla as medições externas do teclado do Android. O resultado consolidado consta na tabela inicial.

A quinta passagem, [40201039082](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40201039082), aprovou as APIs 33 e 35. Na API 26, o cadastro de conta concluiu, e a execução permaneceu no último cenário de fonte ampliada e orientação, sem resumo final, até a substituição da revisão. Esse cenário não foi contado como aprovado. A sincronização de orientação passou a usar `ActivityScenario.onActivity` diretamente a partir da thread do teste, evitando consultar a atividade novamente dentro de `runOnIdle` durante uma recriação. Foram acrescentados marcadores de etapa e limites de execução para distinguir bloqueios de captura, layout e limpeza do ambiente.
