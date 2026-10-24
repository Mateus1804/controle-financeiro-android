# Qualidade e validação

## Referência inicial

A execução [40100369119](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40100369119), com 31 testes Kotlin e 13 testes Android aprovados, constitui a referência da primeira entrega.

Antes da revisão da interface, houve uma tentativa de executar as tarefas Gradle localmente. O download da distribuição falhou com `Network is unreachable`, antes do início dos testes. Essa limitação do ambiente é registrada separadamente das falhas de código. A compilação e a execução da suíte ocorreram no GitHub Actions.

## Revisão da interface — 24/10/2026

O código do commit `36e9849e0a225d5c39b0db104609e73d27de5443` foi validado na execução [40124797196](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40124797196).

| Verificação | Resultado |
| --- | --- |
| `:core:test` | 51 testes aprovados; zero falhas, erros ou testes pulados. |
| Testes instrumentados | 41 testes aprovados no Android 15, API 35, x86_64. |
| `:app:lintDebug` | Zero erros e 11 avisos: um de SDK alvo e dez de versões de dependências. |
| `:app:assembleDebug` | APK de depuração gerado. |
| `:app:assembleDebugAndroidTest` | APK da suíte instrumentada gerado. |
| `python3 scripts/verify-wrapper.py` | SHA-256 do Gradle Wrapper conferido. |
| `python3 scripts/verify-manifest.py` | Permissões do manifesto mesclado conferidas. |
| `python3 scripts/verify-contrast.py` | Dez pares explícitos de texto e fundo acima de 4,5:1. |
| `git diff --check` | Sem problemas de espaços no diff local. |

O resultado foi de **92 testes aprovados**, sem testes pulados nessa execução. Os jobs de compilação e emulador concluíram com sucesso. A compilação também registra um aviso de API obsoleta em `isStatusBarContrastEnforced`, usada no ajuste de compatibilidade das janelas de diálogo; sua substituição permanece prevista para a revisão de SDK.

As versões de ferramentas e bibliotecas permanecem fixadas nesta revisão. Os avisos de atualização e SDK continuam visíveis, sem supressão de regras para ocultá-los.

## Critérios cobertos

- **Dinheiro:** digitação em centavos, colagem formatada, vazio e zero, limites, valores existentes, cursor, seleção, substituição, exclusão e rejeição de entradas inválidas.
- **Contas:** pesquisa e cadastro, persistência, normalização de nomes, ordem estável, reaproveitamento do histórico e independência entre origem da notificação e conta escolhida. A validação de transferências utiliza a mesma identidade normalizada.
- **Migração:** reconstrução de uma base isolada a partir do esquema 1 exportado e abertura pelo Room atual, com conferência de valores, nomes antigos, identificação da pendência e criação do cadastro de contas na versão 2.
- **Relatórios:** despesas brutas, estornos separados, receitas e transferências excluídas, períodos, fuso, arredondamento exato dos percentuais e mil grupos com valores altos. A conferência inclui o filtro aberto pelo gráfico e a ocultação de valores e percentuais na interface e na semântica.
- **Captura:** recusa na apresentação inicial, consentimento específico, fechamento persistente do aviso e escolha manual com captura anteriormente habilitada. No emulador, foram verificados a concessão e a revogação do acesso real ao serviço e a atualização após a retomada da atividade, inclusive sem permissão para enviar alertas.
- **Interface:** cadastro, edição, temas persistidos, recriação da atividade e do formulário, fonte em 150%, teclado aberto e orientação horizontal.

A suíte que reinicia a base fica restrita ao emulador dedicado por meio de `IsolatedAndroidTestRunner`. O aparelho de uso pessoal fica reservado às validações manuais, com preservação da instalação que contém dados reais.

## Inspeção visual e evidências

Foram inspecionadas capturas de resumo, ajustes, relatório, revisão e formulário nos cenários de tema, fonte ampliada e orientação horizontal. A ação de novo lançamento foi posicionada na barra inferior para evitar sobreposição sobre a legenda, e a rolagem do formulário foi adaptada a telas de pouca altura.

Foram preservadas quatro [capturas com dados fictícios](imagens/) da execução [40123613917](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40123613917). Os PNGs foram copiados sem alterações e documentam a interface antes da revisão editorial dos títulos e textos. As evidências completas e os relatórios de cada execução permanecem nos artefatos do GitHub Actions, com retenção de 14 dias.

A conferência dos dez pares de cores e os testes de semântica complementam a inspeção; não representam uma auditoria completa de acessibilidade.

## Limitações e pendências

- A validação de notificações reais de Inter, PicPay, Mercado Pago, PagBank/PagSeguro, Nubank e demais origens selecionadas permanece pendente. Os testes de interpretação utilizam exemplos sintéticos.
- O teste de acesso ao serviço no emulador não homologa formatos bancários, entrega de notificações pelos fabricantes ou reconhecimento de todas as transações.
- TalkBack manual e execução da suíte em outras versões do Android não foram realizados. A compatibilidade do caminho específico do Android 8.0 ainda exige avaliação própria.
- Permanecem pendentes a validação do APK assinado de produção e de uma atualização no aparelho com a mesma chave. APKs de depuração de ambientes diferentes podem ter assinaturas distintas.
- Backup restaurável, conciliação, saldos iniciais, faturas, parcelas, orçamentos, recorrências e biometria continuam fora desta entrega.
- A análise de consentimento e privacidade não representa aprovação de publicação na Google Play.
