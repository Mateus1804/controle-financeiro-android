# Validação do planejador, ciclos e reserva — 09/11/2026

## Mudanças e regras verificadas

O fluxo principal passou a usar previsões por ciclo, com salário mensal cadastrado uma vez, todos os gastos no percentual, consulta de períodos encerrados, reserva separada e simulação adotada explicitamente. Orçamento e Projeções compartilham as regras em centavos inteiros. Vencimentos e fechamento não registram pagamentos ou recebimentos.

Room 9→10 acrescenta regras versionadas, ajustes por ocorrência, ciclos e reserva, preservando as tabelas anteriores. Backup V7 conserva leitura V1–V6, origem dos itens aproveitados e revisão manual das rendas de referência antigas. As regras de uso e o checklist estão em [Planejador, ciclos e reserva](planejador-ciclos-reserva.md).

## Verificações executadas

- `:core:test`: **203 testes, zero falhas, erros ou ignorados**, conforme os XMLs em `core/build/test-results/test/`. Incluem salário único e ajuste de ocorrência, todos os gastos, fatura sem duplicação, valores acima de 100%, estornos, centavos de parcelas, meses curtos, mudança de dia, histórico e reserva sem cobertura automática de déficit.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:assemblePreview` e `:app:lintDebug`: **sucesso**, no build final de produção em **2 min 13 s**. O relatório de lint registra **zero erros e 18 avisos**.
- Após ajustes somente nos testes de regressão, `:app:assembleDebugAndroidTest`: **sucesso em 26 s**.
- Instrumentação direcionada no emulador **API 26**: **24 cenários distintos aprovados**, sendo **15 novos** do planejador e **9 regressões preservadas**. O cenário principal de salário foi repetido no lote final e é contado uma vez.

| Lote | Resultado observado | Evidência local |
| --- | --- | --- |
| Dados e migração novos | 6 aprovados | `/workspace/scratch/forecast-resume-data-tests.log` |
| Editor e jornadas novos | 9 aprovados | `/workspace/scratch/forecast-resume-ui-tests.log` |
| Regressões anteriores | 6 aprovados no primeiro lote; 2 falhas corrigidas e repetidas no lote final | `/workspace/scratch/forecast-legacy-regressions.log` |
| Lote final direcionado | 4 aprovados em 106,544 s: captura, aparência, transferência e repetição do salário | `/workspace/scratch/forecast-final-targeted-tests.log` |

Os builds estão registrados em `/workspace/scratch/forecast-ready-build.log` e `/workspace/scratch/forecast-regression-final-build.log`. Os 18 avisos foram conferidos em `app/build/reports/lint-results-debug.xml`; essa contagem não é uma execução de lint release.

A [captura final com dados fictícios](imagens/planejador/ciclo-exemplo-ficticio.png) foi inspecionada em 320 × 640: ciclo de 05/11/2026 a 04/12/2026, renda ajustada de R$ 2.850,00, gastos de R$ 2.200,00, percentual de 77,19% e margem de R$ 650,00 visíveis sem rolagem. Essa inspeção não substitui a conferência manual com fonte ampliada ou TalkBack.

## Cobertura integrada

As seis verificações de dados e migração cobrem o aproveitamento idempotente dos registros anteriores, preservação de snapshots e vínculos, recorrências arquivadas, limites de textos legados, destinação atômica de reserva, mudança de ciclo com destinações anteriores, serialização/restauração do snapshot V7 e leitura de V6 antes da inicialização do importador. A migração isolada 9→10 foi conferida com preservação dos registros e reabertura da base.

As cinco verificações do editor cobrem salário mensal sem conta obrigatória, valor estimado, valor/chave de uma parcela individual, ativação importada preservada em edição de ocorrência, ocultação de campos com valores monetários e bloqueio de retirada maior que a reserva. As quatro jornadas usam a entrada principal real do aplicativo, sem o host de interface antiga:

1. Salário de **R$ 3.000,00** alimenta o orçamento e as projeções, com **R$ 2.200,00** em gastos, **73,33%** e margem de **R$ 800,00**. Ajustar somente um ciclo para **R$ 2.850,00** preserva o salário futuro.
2. Destinar **R$ 500,00** de uma margem de **R$ 800,00** a uma reserva de **R$ 2.000,00** resulta em **R$ 2.500,00** na reserva e **R$ 300,00** ainda não destinados, sem mudar gastos ou percentual, inclusive após recriar a Activity.
3. Simular não grava a previsão; adotar grava explicitamente, e o término das parcelas reduz o gasto nos ciclos seguintes.
4. A prévia e a configuração persistida usam as mesmas fronteiras ao mudar o dia do ciclo, preservando o período vigente e a transição.

As nove regressões preservadas cobrem identidade de origem/destino, texto Unicode no snapshot, migração de uma base de compatibilidade isolada, atualização do acesso a notificações ao retornar à Activity, ocultação de valores e semântica, último lançamento acima da ação fixa, captura com dados fictícios, aparência após recriação e destino obrigatório em transferência. Os testes do fluxo antigo usam um host disponível somente em debug; essa cobertura não substitui as jornadas da entrada principal.

## Correções e revisão

O primeiro lote de oito regressões terminou com seis aprovações e duas falhas: captura de tela na API 26 e tentativa de rolar um botão fixo sem ancestral rolável. A captura nessa API passou a usar o `screencap -p` nativo, compatível com as dimensões físicas do emulador e com a proteção de janelas seguras. O teste de transferência passou a acionar o botão fixo pela semântica adequada. Ambos passaram no lote final; aparência e a jornada de salário também passaram nesse lote.

Duas revisões independentes de código encerraram sem problemas acionáveis nos recortes revistos: correções financeiras em `/workspace/scratch/forecast-financial-fixes-review.md` e correções de interface em `/workspace/scratch/forecast-app-fixes-review.md`. A revisão financeira conferiu fronteiras históricas, mudança do dia das parcelas, arquivamento de fontes antigas, limites de origem/texto e preservação de destinações. A revisão de interface conferiu ativação de ocorrência importada, renda estimada, privacidade dos campos e semântica do controle. Essas revisões foram inspeções de código; a evidência de execução é a dos testes acima.

## Limites e conferência no celular

A validação local utilizou dados fictícios e cenários direcionados na API 26. Não reexecutou toda a suíte em todas as APIs nem o fluxo completo de instalar o APK anterior, exportar seu backup criptografado e recuperá-lo após a atualização. Esse cenário é lento no emulador local e permanece para o job de atualização do CI na API 35, sem resultado novo presumido. A migração isolada e a serialização/restauração dos snapshots foram verificadas localmente; elas não equivalem à atualização completa por APK.

TalkBack manual, biometria em aparelho, notificações bancárias reais e o checklist manual com fonte ampliada ainda exigem conferência específica. Não houve integração bancária, consulta de saldo real ou conciliação automática. A margem é estimada e a reserva é informada pelo usuário.

O APK **preview** usa `com.mateuswilliam.financas.teste`, com base própria e instalação separada. O [checklist curto no celular](planejador-ciclos-reserva.md#checklist-curto-no-celular) permanece disponível para salário, ciclos, reserva, simulação, persistência e recuperação em uma instalação isolada. Atualizar uma instalação principal exige seu identificador e assinatura originais; não desinstale a versão com dados para contornar uma assinatura diferente.
