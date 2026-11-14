# Entrega incremental da revisão Rendly

As mudanças foram preparadas em PRs separados. A main foi preservada em `469d16f`; não houve merge, force-push, publicação ou alteração de banco, backup ou regras financeiras. O PR 22 foi encerrado e sua branch preservada.

## Ordem de revisão

| Pedido | PRs | O que muda |
| --- | --- | --- |
| 1. Remover a segunda tela principal | [24](https://github.com/Mateus1804/controle-financeiro-android/pull/24), [25](https://github.com/Mateus1804/controle-financeiro-android/pull/25), [26](https://github.com/Mateus1804/controle-financeiro-android/pull/26), [27](https://github.com/Mateus1804/controle-financeiro-android/pull/27), [28](https://github.com/Mateus1804/controle-financeiro-android/pull/28), [29](https://github.com/Mateus1804/controle-financeiro-android/pull/29) | Migra os 69 cenários antigos e remove o host duplicado. |
| 2. Organizar ForecastApp | [30](https://github.com/Mateus1804/controle-financeiro-android/pull/30) | Separa navegação, editores, revisão e cartões. Testa recriação. |
| 3. Cadastro de saldo acessível | [31](https://github.com/Mateus1804/controle-financeiro-android/pull/31) | Abre contas e correção de saldo pelo painel; extrato filtrado inclui contas arquivadas. |
| 4. Explicar previsão até salário | [32](https://github.com/Mateus1804/controle-financeiro-android/pull/32) | Explica variáveis excluídas e o desconto atual da meta total de reserva. |
| 5. Categorias lembradas | [33](https://github.com/Mateus1804/controle-financeiro-android/pull/33) | Lista, corrige e apaga lembranças; reconhece novos rótulos explícitos em textos fictícios. |
| 6. Revisar PR 22 | [34](https://github.com/Mateus1804/controle-financeiro-android/pull/34) | Registra riscos e fechamento sem merge. |
| 7. Corrigir cobertura documentada | [35](https://github.com/Mateus1804/controle-financeiro-android/pull/35) | Informa CI somente API 35 e Room 10, preservando resultados históricos. |
| 8. Preparar assinatura | [36](https://github.com/Mateus1804/controle-financeiro-android/pull/36) | Documenta custódia e recuperação e ignora formatos privados de chave no Git. Operação real pendente. |
| 9. Gráfico | [37](https://github.com/Mateus1804/controle-financeiro-android/pull/37) | Percentuais com cor e contraste; fatias pequenas visíveis, com aviso e percentuais reais. |
| 10. Acessibilidade | [38](https://github.com/Mateus1804/controle-financeiro-android/pull/38) | Legenda adaptável, fonte 200%, semântica e roteiro manual. |

Os PRs de interface são dependentes: 24 → 25 → 26 → 27 → 28 → 29 → 30 → 31 → 32 → 33 → 37 → 38. O 35 depende do 29; o 36 depende do 35. O 34 é independente e aponta à main. As bases intermediárias mantêm os diffs pequenos; a integração deve respeitar essa ordem e conferir a base de cada PR após integrar seu antecessor.

## Validação

Cada descrição de PR informa os testes e o resultado real. Os testes instrumentais usam somente Android 15/API 35. Os testes puros do core e da geometria usam JVM. Compilação local não equivale a teste Android executado. Os jobs do CI preservam o registro de falhas e das correções; nenhum cenário foi ignorado para obter aprovação.

## Pendências que exigem participação do proprietário

- Identificar o certificado da instalação atual, escolher cofre principal e cópia independente e verificar recuperação da chave. Nenhuma chave definitiva foi gerada neste workspace.
- Fornecer amostras reais anonimizadas e verificar entrega no aparelho, se desejar evidência além dos casos sintéticos. O registro atual possui zero amostras reais.
- Executar o roteiro manual de TalkBack, foco, teclado e aparelho em [Acessibilidade](acessibilidade.md).
- Decidir a regra de reserva: hoje a meta total é descontada até o salário. Separar meta total de aporte do período exige confirmação financeira antes de implementar.

Metas, limites por categoria, resumo semanal e Open Finance não foram implementados nesta revisão. A exportação CSV já existente foi preservada; não foi iniciada uma nova integração ou formato financeiro.
