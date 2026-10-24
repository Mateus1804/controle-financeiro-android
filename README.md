# Controle Financeiro Android

Desenvolvo este aplicativo para acompanhar minhas receitas e despesas com menos digitação e mais clareza. Meu objetivo é manter um controle financeiro útil no dia a dia, com dados locais, revisão de movimentações e uma interface simples em português.

## O que já construí

- Registro manual de receitas, despesas, transferências e estornos.
- Edição e exclusão de lançamentos confirmados.
- Caixa de revisão para notificações, com valor, origem, movimentação, meio de pagamento e categoria editáveis.
- Captura opcional dos aplicativos que eu selecionar, depois do consentimento e da autorização do Android.
- Resumo mensal, comparação de receitas e despesas e barras de gastos por categoria.
- Busca e filtros no histórico, navegação por mês e exportação CSV.
- Temas claro e escuro e opção de ocultar totais.

## Como trato as notificações

Construí a captura sem uma lista fechada de bancos. Posso selecionar aplicativos com ícone de abertura disponíveis no mesmo perfil do Android. Uso a origem informada pelo sistema para identificar o aplicativo e interpreto o texto disponível para sugerir os demais campos.

Minha primeira implementação reconhece padrões genéricos em português e valores em reais. O reconhecimento é uma sugestão: mensagens ambíguas continuam pendentes, e nenhuma notificação entra nos totais sem confirmação. Diferencio o tipo de movimentação (receita, despesa, transferência ou estorno), o meio de pagamento (Pix, cartão, boleto etc.) e a categoria (alimentação, moradia etc.).

Com o aplicativo aberto, apresento um formulário de revisão. Fora dele, envio um alerta que abre a pendência ao ser tocado. Mantenho o lançamento manual disponível quando uma notificação não chega ou não contém informações suficientes.

## Privacidade

Processo e armazeno os dados neste aparelho. Filtro as origens selecionadas antes de ler o conteúdo, descarto mensagens identificadas como autenticação e apago o texto original ao confirmar ou ignorar a pendência. Mantenho identificadores locais para evitar reprocessar o mesmo evento.

Não solicito credenciais bancárias. A exportação CSV ocorre somente para um destino escolhido por mim. Desativo backups automáticos do sistema nesta versão e disponibilizo a exclusão dos dados locais.

Detalho o tratamento dos dados em [Privacidade](docs/privacidade.md).

## Organização técnica

Uso Kotlin, Jetpack Compose, Material 3, Room/SQLite, Coroutines e StateFlow. Separo as regras de dinheiro, interpretação e totais no módulo `core`; mantenho interface, persistência e integração com o Android no módulo `app`.

Armazeno dinheiro em centavos inteiros. Transferências ficam fora do resultado mensal, e estornos reduzem as despesas líquidas. A confirmação de uma pendência e a criação do lançamento ocorrem na mesma transação do banco de dados.

## Qualidade

Validei a base com **31 testes de regras Kotlin** e **13 testes instrumentados no Android 15**. Cobri valores monetários, interpretação, totais, CSV, consentimento, persistência, confirmação simultânea e revisão de mensagens alteradas. Os testes de interface incluem cadastro manual e recusa da captura.

Também compilei o APK de depuração, executei Android Lint sem erros e conferi as permissões do manifesto mesclado. Mantenho os resultados e as limitações em [Qualidade e validação](docs/qualidade.md).

## Estágio do projeto

Estou desenvolvendo a versão **0.1.0**, com a base de registro e revisão. Valido os padrões com exemplos sintéticos: ainda preciso conferir as notificações reais de cada aplicativo bancário e o comportamento dos aparelhos.

A captura não equivale a uma integração oficial com os bancos ou ao acesso ao extrato completo. Ainda estou planejando contas com saldo inicial, conciliação, faturas, parcelamentos, orçamentos, recorrências e backup restaurável. Registro essas etapas no [planejamento](docs/planejamento.md).

Mantenho os resultados efetivamente executados em [Qualidade e validação](docs/qualidade.md), as [decisões técnicas](docs/decisoes.md) e as [referências de estudo](docs/referencias.md). Reservei as instruções do ambiente e os comandos para o [guia de desenvolvimento](docs/desenvolvimento.md).
