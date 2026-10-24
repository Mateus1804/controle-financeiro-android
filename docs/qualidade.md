# Qualidade e validação

## Referência inicial

Uso a execução [40100369119](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40100369119), com 31 testes Kotlin e 13 testes Android aprovados, como referência da primeira entrega.

Antes desta revisão, tentei executar as tarefas Gradle localmente. O download da distribuição falhou com `Network is unreachable`, antes do início dos testes. Registro essa limitação do ambiente separadamente das falhas de código e utilizo o GitHub Actions para compilar e executar a suíte.

## Revisão da interface — 24/10/2026

Validei o código do commit `36e9849e0a225d5c39b0db104609e73d27de5443` na execução [40124797196](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40124797196).

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

Obtive **92 testes aprovados**, sem testes pulados nessa execução. Os jobs de compilação e emulador concluíram com sucesso. A compilação também registra um aviso de API obsoleta em `isStatusBarContrastEnforced`, usada no ajuste de compatibilidade das janelas de diálogo; mantenho sua substituição na revisão de SDK.

Mantenho as versões de ferramentas e bibliotecas fixadas nesta revisão. Os avisos de atualização e SDK permanecem visíveis; não suprimi regras para ocultá-los.

## Critérios que cobri

- **Dinheiro:** digitação em centavos, colagem formatada, vazio e zero, limites, valores existentes, cursor, seleção, substituição, exclusão e rejeição de entradas inválidas.
- **Contas:** pesquisa e cadastro, persistência, normalização de nomes, ordem estável, reaproveitamento do histórico e independência entre origem da notificação e conta escolhida. Uso a mesma identidade normalizada na validação de transferências.
- **Migração:** reconstruí uma base isolada a partir do esquema 1 exportado e a abri pelo Room atual. Conferi valores, nomes antigos, identificação da pendência e criação do cadastro de contas na versão 2.
- **Relatórios:** despesas brutas, estornos separados, receitas e transferências excluídas, períodos, fuso, arredondamento exato dos percentuais e mil grupos com valores altos. Conferi o filtro aberto pelo gráfico e a ocultação de valores e percentuais na interface e na semântica.
- **Captura:** recusa na apresentação inicial, consentimento específico, fechamento persistente do aviso e escolha manual com captura anteriormente habilitada. No emulador, concedi e revoguei o acesso real ao serviço e conferi a atualização após retomar a atividade, inclusive sem permissão para enviar alertas.
- **Interface:** cadastro, edição, temas persistidos, recriação da atividade e do formulário, fonte em 150%, teclado aberto e orientação horizontal.

Restrinjo a suíte que reinicia a base ao emulador dedicado por meio de `IsolatedAndroidTestRunner`. Reservo o aparelho de uso pessoal para validações manuais, preservando uma instalação com dados reais.

## Inspeção visual e evidências

Inspecionei capturas de resumo, ajustes, relatório, revisão e formulário nos cenários de tema, fonte ampliada e orientação horizontal. Ajustei a ação de novo lançamento para a barra inferior, evitando sobreposição sobre a legenda, e adaptei a rolagem do formulário em telas de pouca altura.

Preservei quatro [capturas com dados fictícios](imagens/) da execução [40123613917](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40123613917), cuja interface corresponde à revisão atual. Copiei os PNGs sem alterações. As evidências completas e os relatórios de cada execução permanecem nos artefatos do GitHub Actions, com retenção de 14 dias.

A conferência dos dez pares de cores e os testes de semântica complementam a inspeção; não representam uma auditoria completa de acessibilidade.

## Limitações e pendências

- Ainda preciso validar notificações reais de Inter, PicPay, Mercado Pago, PagBank/PagSeguro, Nubank e demais origens selecionadas. Uso exemplos sintéticos nos testes de interpretação.
- O teste de acesso ao serviço no emulador não homologa formatos bancários, entrega de notificações pelos fabricantes ou reconhecimento de todas as transações.
- Não executei TalkBack manualmente nem a suíte em outras versões do Android. A compatibilidade do caminho específico do Android 8.0 ainda exige avaliação própria.
- Ainda preciso validar o APK assinado de produção e uma atualização no aparelho com a mesma chave. APKs de depuração de ambientes diferentes podem ter assinaturas distintas.
- Backup restaurável, conciliação, saldos iniciais, faturas, parcelas, orçamentos, recorrências e biometria continuam fora desta entrega.
- A análise de consentimento e privacidade não representa aprovação de publicação na Google Play.
