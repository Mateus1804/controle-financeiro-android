# Controle Financeiro: competências, interface e Rendly

## Objetivo e autorização

Implementar a solicitação detalhada fornecida pelo usuário em 07/11/2026: corrigir gastos fixos e vencimentos, modernizar e compactar a interface Compose, padronizar Phosphor e integrar o SVG Rendly original. O usuário pediu implementação efetiva; decisões rotineiras seguem a arquitetura existente.

## Restrições

- Preservar `Controle Financeiro`, `com.mateuswilliam.financas`, assinatura e publicação.
- Preservar lançamentos, vínculos e históricos. Nenhuma migração destrutiva.
- Dinheiro em centavos inteiros; previsões só viram lançamentos com confirmação explícita.
- Manter Kotlin, Compose, Material 3, Room, temas dinâmicos e suporte API 26+.
- Phosphor Regular para estados normais e Fill para estados selecionados.
- Rendly deve usar os caminhos do SVG anexado, sem recriação por IA: `#014030`, `#20CD91`, `#F7FBFC`.

## Diagnóstico e abordagem

O projeto já tem um sistema visual Material 3 reutilizável. A maior densidade desnecessária está nos cartões de planejamento, com ações secundárias sempre abertas. A abordagem escolhida pelo implementador é aprimorar esses componentes, preservando navegação e regras existentes; uma reescrita completa traria risco sem benefício proporcional.

As competências pendentes atualmente dependem de uma recorrência global mutável. O novo modelo deve identificar a ocorrência mensal por recorrência e competência, persistindo valores, vencimento, estado e vínculo do mês. Alterar o vencimento dentro do mês não muda sua identidade. A edição mensal e a edição da configuração recorrente terão escopos explícitos. Migração e backup devem conservar todos os registros anteriores e continuar lendo formatos antigos. Não é possível reconstruir valores históricos já sobrescritos antes desta alteração.

A interface terá cartões com valores menores, metadados essenciais, menus para ações secundárias, explicações expansíveis, alvos de toque de 48 dp e adaptação a fonte ampliada. Confirmações de exclusão e mensagens existentes serão preservadas e lacunas serão corrigidas.

Ícones internos usarão vetores oficiais Phosphor, convertidos em recursos Android locais, sem dependência de execução adicional. O launcher terá background independente, foreground sem o squircle original, monochrome para Android 13+, recursos legados e prévias de 32 px. As máscaras devem preservar todo o monograma.

## Alternativas consideradas

1. Corrigir somente a apresentação: insuficiente, pois a perda de competência nasce na persistência.
2. Reescrever o planejamento inteiro: risco alto para migração, backup e pagamentos existentes.
3. Adicionar identidade mensal persistente e evoluir os componentes atuais: escolhida por atender ao pedido com mudanças delimitadas.

## Verificação

Regressões para isolamento mensal, idempotência, vencimentos de fevereiro/ano bissexto/virada de ano, estados pagos e pulados, reabertura do banco, migração e backup. Executar suíte core, build Android, lint e testes instrumentados quando o ambiente permitir. Conferir caminhos/cores/camadas e área segura do ícone, contraste, privacidade e interface com fonte ampliada. Registrar resultados reais e limitações, sem presumir validação no aparelho.
