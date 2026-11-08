# Validação de contas, saldos e projeções — 08/11/2026

## Mudanças

Contas próprias com referências de saldo auditáveis, arquivamento e extrato filtrado; transferência com prévia e recálculo atômico; categorias por natureza e estornos vinculados; projeção compartilhada entre Resumo e Planejamento; modelos opcionais sem valores e detalhes progressivos no editor comum.

Room 8→9 adiciona arquivamento, referências de saldo e natureza/vínculo de estornos. Backup V6 conserva leitura V1–V5; contas antigas continuam sem saldo configurado e estornos antigos mantêm a natureza de reembolso de despesa. Nenhuma migração destrutiva ou receita fictícia foi utilizada.

## Verificações executadas

- `:core:test`: **189 testes, zero falhas/erros/ignorados**, incluindo saldo desconhecido/negativo, corte da referência, transferências editadas/excluídas, estornos de ambas as naturezas e projeções sem duplicação.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:assemblePreview`: **sucesso**.
- `:app:lintDebug`: **zero erros, 16 avisos**.
- **20 cenários instrumentados distintos aprovados**, direcionados à mudança, no emulador API 26: contas/dados (3), prévia de transferência (5), natureza/modelos do editor (3), destino/categoria legados (3), integração de projeção/conta (2), preservação de recorrências e leitura de backup V4 (2), cadastro/confirmação única de receita e despesa recorrente (2).

As evidências locais estão em `/workspace/scratch/integrated-essential-tests.log`, `integrated-compatibility-tests.log`, `integrated-verified-ui-tests.log` e `integrated-income-category-build.log`. A primeira execução das jornadas antigas teve falha de sincronização e pressupunha a conta padrão Carteira. A preparação passou a cadastrar uma conta própria e aguardar sua disponibilidade no ViewModel; ambas as jornadas passaram na repetição final. Os demais casos não precisaram ser ampliados para a suíte completa.

Duas revisões independentes cobriram persistência e integração. A revisão final identificou lançamentos futuros tratados como realizados e a prévia incompleta ao converter despesa/receita em transferência; ambos foram corrigidos e conferidos. Lançamentos futuros entram na projeção até sua data; a prévia remove o efeito anterior antes de aplicar a transferência.

## Limites e conferência manual

A validação utilizou dados fictícios e cenários essenciais. Não reexecutou toda a instrumentação em todas as APIs, TalkBack manual ou notificações bancárias reais. Saldos dependem das referências e dos lançamentos informados; não há conciliação bancária automática. Limites de cartão não são saldo.

O checklist de cinco passos e as regras de data de referência estão em [Contas, saldos e projeções](contas-saldos-projecoes.md). O APK preview instala como aplicativo separado de teste. Atualizar uma instalação pessoal exige o mesmo identificador, assinatura original e versionamento adequado; não desinstale a versão com dados para contornar uma assinatura diferente.
