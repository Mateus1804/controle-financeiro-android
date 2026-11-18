# Rendly

Controle financeiro pessoal para Android, com dados locais e valores em centavos. A tela principal funciona como uma planilha mensal. O planejamento orienta os gastos sem exigir confirmação de pagamento.

## Organização

- **Mês:** receitas, despesas, resultado do período e patrimônio. A lista reúne lançamentos registrados, agendados, previsões e referências de saldo agrupados por dia, filtros por categoria e conta e botão +. Transferências entre contas próprias não alteram receitas ou despesas.
- **Gráficos:** rosca de gastos brutos, legenda por valor e comparação mensal de despesas líquidas. Estornos são identificados para explicar as diferenças.
- **Fixas e parcelas:** lista única de previsões, com Pular este mês, Alterar só este mês e Aplicar deste mês em diante.
- **Mais:** Planejador, Reserva de emergência, Comprometimento e Ajustes. Captura e revisão de notificações, contas e saldos, backup e aparência ficam em Ajustes.

O formulário começa com valor, categoria adequada à natureza, descrição e data. Conta, meio de pagamento e vínculo opcional com previsão ficam em Mais detalhes. Transferências mostram origem, destino e prévia do efeito nas contas.

O salário é uma receita. No botão + da aba Mês, selecione Receita e escolha Única, Mensal ou Parcelada. Descontos ou alterações em um mês usam o ajuste da ocorrência. Até o próximo salário mostra saldo conhecido de contas ativas menos compromissos até a data prevista, sem descontar a reserva. Gastos variáveis aparecem em cenário separado; contas sem saldo geram o aviso Saldo parcial.

Mês, Gráficos e Planejador usam o mesmo período financeiro. Por exemplo, novembro pode abranger 5 de novembro a 4 de dezembro. Os saldos acumulam o histórico até o fim do período ou até hoje. A conta Reserva de emergência recebe aportes e resgates por transferência. Os eventos da reserva de versões anteriores continuam disponíveis em seu histórico. Para começar a movimentar essa conta, informe o saldo de referência e sua data. A passagem de uma data não registra pagamento ou recebimento.

## Armazenamento novo

A instalação que já usa o banco Rendly v1 preserva seus dados nesta atualização. A transição anterior, do antigo Controle Financeiro para esse banco, tinha um reset autorizado e um novo formato de backup. O mesmo applicationId e assinatura são preservados. Aparência, nome e ocultação permanecem; captura e lembretes são pausados ao descartar o banco anterior.

O banco `rendly-v1.db` usa Room 1 e somente a estrutura atual. Fixas e parcelas usam previsões, sem sistema paralelo de recorrências. O backup novo usa formato Rendly 1/envelope 2, com AES-GCM/PBKDF2, senha e conferência em base temporária antes da restauração transacional. Leia [Armazenamento](docs/armazenamento-rendly.md) e [Backup](docs/backup.md).

## Privacidade e aparência

Não exige conta, login ou credenciais bancárias. A captura é opcional, local e depende de consentimento, escolha dos aplicativos e permissão do Android. Notificações geram sugestões revisáveis; não se tornam lançamentos sem confirmação. O vínculo com uma previsão é explícito, sem tentar adivinhar correspondências por texto ou valor.

A identidade Ameixa e Sálvia oferece Sistema, Claro e Escuro, sem substituição por cores dinâmicas. Valores podem ser ocultados. O nome de exibição é opcional; a saudação usa o nome informado e 👋🏼. Backups automáticos do Android estão desativados. Há bloqueio opcional pelo aparelho e proteção de capturas de tela; o banco Room não recebe criptografia adicional por esse bloqueio.

## Desenvolvimento

Kotlin, Jetpack Compose, Material 3, Room, Coroutines e StateFlow. `core` contém cálculos e regras; `app` reúne interface, persistência e integração Android. Java 17 e SDK definidos pelo Gradle são necessários.

```bash
./gradlew :app:assembleDebug
./gradlew :core:test :app:lintDebug :app:assembleDebugAndroidTest
```

O APK de desenvolvimento fica em `app/build/outputs/apk/debug/app-debug.apk`. Uma atualização da instalação exige a mesma assinatura. A chave definitiva deve permanecer fora do Git; sua criação e custódia continuam fora do escopo desta etapa. Consulte [Atualizações](docs/atualizacoes.md).

Instrumentação e cenários do CI usam somente Android 15/API 35: suíte funcional e reset intencional do armazenamento anterior. TalkBack manual, biometria e notificações reais exigem conferência em aparelho. [Estado atual](docs/estado-atual.md) registra resultados efetivamente obtidos; [Mapa de telas](docs/novo-mapa-de-telas.md) descreve a reorganização. Relatos mais antigos em `docs` registram versões anteriores, sem garantir compatibilidade com o banco e o backup novos.
