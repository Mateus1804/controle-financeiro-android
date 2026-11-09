# Backup e recuperação

## Escopo

O backup é um arquivo de dados do Controle Financeiro, protegido por senha. Inclui as previsões versionadas de renda e gastos, ajustes por ocorrência, regras e transições de ciclo, eventos da reserva, destinações de sobras, meta opcional e metadados de procedência do importador. Uma simulação ainda não adotada é temporária e não integra o arquivo.

Também preserva os dados do fluxo anterior: lançamentos confirmados, pendências com seus estados de revisão, identificadores para impedir reprocessamento, contas cadastradas, orçamentos mensais, recorrências e histórico dos vencimentos tratados, compras parceladas, compromissos, marcações de despesas fixas e renda/limite de cada mês. Inclui o histórico de referências de saldo, contas arquivadas, natureza/vínculo dos estornos, competências recorrentes e configurações com vigência mensal, além de aparência, ocultação de valores e última origem utilizada manualmente.

O CSV do planejador é um relatório das previsões do ciclo selecionado e não é aceito como arquivo de restauração. O backup não inclui arquivos de assinatura, senhas bancárias, autorizações do Android, seleção de aplicativos, consentimento da captura ou configurações de bloqueio do aparelho. Os valores da reserva são informados pelo usuário; o backup não consulta nem certifica saldos bancários.

## Operação pela interface

Em **Ajustes → Criar backup criptografado**, a criação exige uma senha com 12 a 256 caracteres e sua repetição. O processamento ocorre fora da thread principal. O seletor de arquivos do Android recebe somente o conteúdo já criptografado, com extensão `.cfbackup`. O destino deve ser escolhido de acordo com a privacidade necessária; provedores de arquivos podem oferecer armazenamento local ou serviços externos sob controle do usuário.

Em **Ajustes → Restaurar backup**, a senha original abre o arquivo para conferência. A prévia informa data, quantidade de lançamentos, pendências, contas cadastradas, orçamentos e recorrências. Quando presentes, também informa as configurações mensais, compras, compromissos e marcações de recorrências fixas. A substituição exige a marcação explícita da opção correspondente e o toque em **Restaurar backup**. Cancelar a prévia mantém a base atual.

A senha não é guardada em preferências, estado salvo da tela, Intent, banco ou logs. Sem a senha original, não existe recuperação do arquivo. A criação de um backup atual e o teste de recuperação em uma instalação de desenvolvimento isolada são necessários antes de depender do arquivo para dados de uso diário.

## Formato versionado

O envelope binário versão 1 utiliza inteiros de 32 bits em ordem big-endian:

| Posição | Conteúdo |
| --- | --- |
| 0–7 | Assinatura `CFABKP` seguida de CR/LF. |
| 8–11 | Versão do envelope: 1. |
| 12–15 | Iterações da derivação: 600.000. |
| 16–31 | Salt aleatório de 16 bytes. |
| 32–43 | Nonce aleatório de 12 bytes. |
| A partir de 44 | Conteúdo criptografado e tag GCM de 128 bits ao final. |

A chave de 256 bits é derivada com `PBKDF2WithHmacSHA256`; a criptografia utiliza `AES/GCM/NoPadding`. Salt e nonce novos são gerados com `SecureRandom` a cada criação. O cabeçalho inteiro é autenticado como AAD. Versões e parâmetros diferentes dos suportados são recusados antes da derivação para impedir custos arbitrários definidos pelo arquivo recebido.

O conteúdo autenticado é JSON UTF-8 com identificação `controle-financeiro` e versão lógica 7. O leitor aceita também as versões 1, 2, 3, 4, 5 e 6. As versões anteriores preservam o estado que registravam e não criam exclusões novas. O arquivo não contém comandos SQL nem classes a serem instanciadas dinamicamente. Os centavos são números inteiros. Números fracionários, identificadores repetidos, vínculos inconsistentes, estados desconhecidos e campos fora dos limites são recusados. As regras de ciclo e os vínculos das destinações à reserva também são validados.

O limite é de 16 MiB para o conteúdo e 50 mil itens no conjunto de tabelas. O envelope acrescenta 60 bytes ao tamanho do conteúdo. Versões futuras precisam de conversão explícita; arquivos desconhecidos não são abertos por tentativa ou recriação destrutiva.

## Conferência e substituição

1. A leitura limita o tamanho do arquivo antes da abertura do conteúdo.
2. A tag GCM autentica o arquivo; senha incorreta e adulteração não produzem uma prévia utilizável.
3. Os dados são validados e inseridos em um banco Room temporário no armazenamento privado do aplicativo.
4. A estrutura Room, `PRAGMA integrity_check`, `PRAGMA foreign_key_check`, os valores e os vínculos são conferidos. A releitura das tabelas precisa corresponder ao conteúdo decodificado.
5. Após a confirmação da prévia, a pausa da captura e dos lembretes precisa ser persistida com sucesso. Uma falha nessa gravação impede a substituição da base. A substituição das tabelas financeiras ocorre em uma única transação, protegida pela mesma exclusão de escrita usada nas confirmações.
6. Uma falha da transação restaura os dados anteriores por rollback e restabelece a configuração anterior da captura. Após sucesso, captura e lembretes permanecem pausados para conferência. Os alertas anteriores são cancelados.

A restauração não troca o arquivo SQLite ativo enquanto ele está aberto. As preferências de apresentação são aplicadas após a transação financeira. Consentimento, seleção e proteção já configurados neste aparelho permanecem locais; um backup não concede essas autorizações.

As previsões, ajustes, ciclos, eventos e metadados da reserva participam da mesma conferência e substituição transacional que os dados anteriores. A restauração substitui o conjunto; não mescla o arquivo com as previsões locais. Cancelar mantém tanto o planejador quanto o histórico original da instalação.

## Retenção e limitações

O banco temporário é excluído ao cancelar, concluir ou falhar a operação. Bancos temporários abandonados após encerramento do processo são removidos na inicialização seguinte, antes de uma nova importação. O conteúdo decifrado existe em memória e no banco temporário privado durante a conferência. Limpar buffers e excluir arquivos não equivale a apagamento físico garantido de memória ou armazenamento flash.

A criptografia do arquivo não criptografa o banco Room de uso diário. O bloqueio por biometria ou credencial protege a interface, e `FLAG_SECURE` depende do comportamento do Android e do fabricante. Arquivos exportados permanecem no destino escolhido mesmo após apagar os dados do aplicativo.

A portabilidade do envelope tem um vetor fixo com texto e senha fictícios, produzido por uma implementação independente e exercitado em Kotlin/JVM e no Android. Isso complementa os testes de ida e volta; não representa uma auditoria criptográfica independente ou validação de todos os aparelhos.

## Recuperação em desenvolvimento

Uma recuperação de teste utiliza emulador dedicado e dados fictícios. A instalação original com dados reais deve permanecer preservada. A substituição de APK exige o mesmo identificador e a mesma chave de assinatura; desinstalar para contornar uma assinatura diferente apaga a base local.

O APK **preview** usa `com.mateuswilliam.financas.teste`, possui base própria e pode coexistir com a instalação principal. Restaurar nele é uma forma de conferir dados fictícios sem substituir a base da instalação principal. O procedimento ainda exige o arquivo e sua senha; a instalação separada não compartilha dados automaticamente.

Os resultados efetivamente executados ficam em [Qualidade e validação](qualidade.md), e os comandos do ambiente ficam em [Desenvolvimento](desenvolvimento.md).

## Compatibilidade entre versões

O envelope criptográfico continua na versão 1. O conteúdo de dados utiliza a versão 7, separado do esquema Room 10. O formato 2 introduziu as tabelas de comprometimento, o 3 acrescentou revisão, cancelamento e ajuste de parcelas, o 4 registra exclusões de parcelas individuais e de previsões recorrentes, e o 5 acrescenta snapshots por competência e revisões da configuração recorrente. O formato 6 acrescenta referências de saldo auditáveis, arquivamento de contas e vínculos/natureza dos estornos. O formato 7 acrescenta regras versionadas de previsão, ajustes por ocorrência, regras de ciclo, eventos de reserva e metadados de importação e meta. Aplicativos que só leem até V6 precisam ser atualizados para restaurar V7.

Arquivos V1 a V5 não presumem saldos de contas; estornos antigos conservam a natureza de reembolso de despesa. Arquivos V1 a V6 não contêm o novo planejador. Depois da restauração, a inicialização do planejador aproveita os dados anteriores uma vez, preservando originais e chaves de procedência. Vínculos conhecidos evitam contar o lançamento e sua ocorrência novamente. Rendas de referência antigas exigem revisão manual antes de virar previsão, e nenhuma reserva é inventada. Um V7 já inicializado conserva o marcador de importação e não repete esse aproveitamento a cada abertura. Consulte [Planejador, ciclos e reserva](planejador-ciclos-reserva.md).

A prévia apresenta as contagens dos dados anteriores, inclusive seus vínculos históricos; as previsões excluídas não voltam ao Planejamento. Um vetor congelado em `app/src/androidTest/assets/compatibility/` é restaurado numa base temporária antes de qualquer substituição.

O vetor usa dados fictícios e criptografia gerada independentemente em Python. Não representa uma exportação bancária real. O teste compara todos os registros, vínculos, revisões, contas, orçamentos, recorrências e ocorrências, além da aparência. O cadastro local existente precisa permanecer intacto até a confirmação da restauração. O arquivo congelado não é regenerado durante o teste.

A migração do banco instalado e a compatibilidade do arquivo de backup são verificações diferentes. O CI também executa a atualização do APK anterior sobre a mesma instalação; o procedimento está descrito em `atualizacoes.md`. Formatos futuros desconhecidos continuam recusados sem substituir dados.

O cenário de atualização possui ainda duas fases: exportação pelo código do APK anterior e recuperação pelo APK atualizado. O artefato de teste é inteiramente fictício e usa senha pública exclusiva da suíte. O resultado dessas fases depende dos jobs efetivamente concluídos, registrados em Qualidade.

O leitor exige a programação completa das parcelas, mesmo se as previsões abertas tiverem sido canceladas. Identidades, numeração, vínculos, origens e valores inteiros continuam conferidos. Somente parcelas explicitamente ajustadas podem ter valor ou data diferentes da programação original. Campos novos disfarçados como uma versão anterior são recusados para evitar perda silenciosa de informação. O limite de 50 mil itens considera também as tabelas novas. A restauração substitui o conjunto completo em uma única transação.
