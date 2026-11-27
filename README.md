# Rendly

Controle financeiro pessoal para Android. O registro funciona como a planilha Rendly, com uma lista de lançamentos e um resumo calculado a partir dela.

## Como usar

- **Lançamentos** reúne receitas, despesas, transferências e saldos iniciais. Use **+** para cadastrar. Toque em uma linha para abrir seus detalhes.
- **Resumo** mostra receitas, despesas, resultado, patrimônio, saldos por conta e a distribuição de gastos por categoria. O seletor de período vale para as duas abas.
- **Ajustes** fica no topo. Permite escolher o dia de início do ciclo, aparência, nome de exibição, proteção da tela e backup criptografado.

Salário, freelancer e renda extra são categorias de receita. Para guardar dinheiro, transfira de uma conta de uso para **Reserva de emergência**. Para resgatar, inverta as contas. Essas transferências só movimentam os saldos.

Cada conta começa em zero. Se já havia dinheiro nela, registre um **Saldo inicial** com o valor no começo da data escolhida. Movimentações anteriores incluídas nesse valor permanecem no histórico. Receitas e despesas são calculadas dentro do ciclo. Saldos acumulam movimentos até o fim do ciclo consultado ou até hoje.

O dia 5, por exemplo, organiza o período de 5 de novembro a 4 de dezembro. O gráfico usa valores e percentuais de despesas brutas. Estornos registrados anteriormente continuam no extrato e ajustam a receita ou a despesa correspondente.

## Dados e privacidade

A instalação mantém o banco e os backups do Rendly v1. Os dados anteriores de planejamento e sugestões são preservados no armazenamento e no arquivo de backup. O app atual registra movimentos manualmente.

O tema claro tem fundo branco, o escuro tem fundo preto e ambos usam a paleta ameixa e sálvia. Os valores podem ser ocultados. A autenticação e a proteção da tela são opcionais. Os backups automáticos do Android estão desativados. [Backup e recuperação](docs/backup.md) · [Privacidade](docs/privacidade.md)

## Desenvolvimento

Kotlin, Jetpack Compose, Material 3, Room e Java 17. O módulo `core` concentra as regras de cálculo. O módulo `app` reúne a interface e a persistência.

```bash
./gradlew :app:assembleDebug
./gradlew :core:test :app:lintDebug :app:assembleDebugAndroidTest
```

O APK de desenvolvimento fica em `app/build/outputs/apk/debug/app-debug.apk`. A variante `preview` pode ser instalada ao lado do app principal. O CI executa os testes de interface, reset legado e atualização instalada no Android 15 (API 35). [Atualizações](docs/atualizacoes.md)
