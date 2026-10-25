# Qualidade e validação

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

Os avisos de Lint restantes são um de SDK alvo, dez de atualização de dependências e três `UseKtx` nas gravações verificadas de `CapturePreferences`. Os três avisos `UseKtx` permanecem justificados: a extensão KTX `edit` retorna `Unit` e não expõe o resultado de `commit()`, que precisa ser verificado antes de substituir dados ou afirmar que a proteção foi salva. A API padrão é intencional nesses pontos. Os avisos de coleção mutável em estado Compose e conversão de URI foram corrigidos. Não houve supressão de regras. Entre os avisos de compilação estão duas APIs de compatibilidade obsoletas: a alternativa de credencial para versões anteriores e `isStatusBarContrastEnforced` nas janelas de diálogo. As versões da stack permaneceram fixadas.

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
