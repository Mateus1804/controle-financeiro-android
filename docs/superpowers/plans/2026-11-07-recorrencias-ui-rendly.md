# Controle Financeiro Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir competências financeiras e entregar interface compacta, Phosphor e launcher Rendly fiel.

**Architecture:** Evoluir `core` e a persistência Room com ocorrências mensais identificadas por competência. Reutilizar os componentes Compose atuais, com recursos vetoriais locais e mudanças de apresentação independentes do cálculo financeiro.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room, Gradle 8.13, JDK 17, Android API 26–36.

**Spec:** `docs/superpowers/specs/2026-11-07-recorrencias-ui-rendly-design.md`

## Global Constraints

- Não renomear `Controle Financeiro` nem `com.mateuswilliam.financas`; não alterar publicação/assinatura.
- Não apagar histórico nem usar migração destrutiva. Manter leitura dos backups anteriores.
- Phosphor Regular normal/inativo; Fill selecionado/ativo; alvos de toque de 48 dp.
- Rendly usa o SVG original e as cores `#014030`, `#20CD91`, `#F7FBFC`.
- Testes instrumentados apenas em emulador isolado com dados fictícios.

## Review Focus

- Editar vencimento dentro da competência conserva uma única obrigação e seus estados.
- Editar configuração futura não muda competências anteriores já existentes.
- Migração e backup preservam pagamentos, exclusões e vínculos legados.
- Fonte ampliada e valores ocultos continuam utilizáveis e acessíveis.
- Máscaras adaptativas e ícones temáticos não cortam o monograma Rendly.

### Task 1: Competências mensais e integridade

**Files:** `core/.../Planning.kt`, `app/.../data/FinanceDatabase.kt`, `FinanceRepository.kt`, `BackupSnapshot.kt`, novas entidades/regras mensais, esquemas e testes correspondentes.

**Interfaces:** Produzir consultas mensais, edição de uma competência e confirmação/pulo com identidade estável e revisão esperada; publicar assinaturas para integração no ViewModel/UI.

- [ ] Reproduzir perda de mês e duplicação após mudança de dia em testes.
- [ ] Adicionar persistência mensal com migração segura, snapshots e proteção contra ações obsoletas.
- [ ] Integrar relatórios, lembretes e operações sem duplicar lançamentos.
- [ ] Evoluir backup e testar formatos antigos, reabertura, fevereiro e virada de ano.

### Task 2: Auditoria financeira complementar

**Files:** `core/.../Models.kt`, `AccountNames.kt`, `app/.../ui/RecordEditor.kt`, testes de validação e backup.

- [ ] Reproduzir texto NUL/identificadores não recuperáveis por backup e destino residual ao converter transferência.
- [ ] Corrigir validação de novas entradas e limpar destino fora de transferências.
- [ ] Executar regressões de valores, parcelamentos, totais e validação.

### Task 3: Interface compacta e integração mensal

**Files:** `app/.../ui/Theme.kt`, `Components.kt`, `PlanningScreen.kt`, `CommitmentPlanningFields.kt`, `FinanceApp.kt`, telas e testes Compose afetados, `FinanceViewModel.kt`.

- [ ] Compactar cartões e espaçamento; manter adaptação de valores e fonte ampliada.
- [ ] Mover ações secundárias para menus, adicionar descrições e seções expansíveis.
- [ ] Diferenciar editar este mês e configuração recorrente; integrar operações da Task 1.
- [ ] Atualizar testes de jornadas e confirmar exclusão de orçamentos.

### Task 4: Phosphor

**Files:** `app/src/main/res/drawable/ic_*.xml`, mapeamento Regular/Fill, licença e documentação de procedência.

- [ ] Importar vetores oficiais Regular/Fill e converter sem misturar bibliotecas.
- [ ] Integrar Fill em navegação e seleções; validar inventário e compilação.

### Task 5: Launcher Rendly

**Files:** SVG original versionado, `drawable`, `mipmap-anydpi-v26`, `mipmap-anydpi-v33`, densidades legadas, `AndroidManifest.xml`, script de validação e prévias.

- [ ] Preservar caminhos originais e separar foreground/background.
- [ ] Adicionar adaptive, round e monochrome com área segura e fallback.
- [ ] Validar XML, cores, máscaras e legibilidade de 32 px.

### Task 6: Integração, revisão e entrega

- [ ] Executar core, compilação, lint e testes instrumentados disponíveis.
- [ ] Revisar mudanças e corrigir regressões identificadas.
- [ ] Documentar resultados, limitações e checklist manual no celular.
