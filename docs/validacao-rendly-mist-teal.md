# Validação da identidade Rendly

A branch parte da main 594705b, que integrou o PR #17 de contas, saldos e projeções. Nenhum PR estava aberto na inspeção e a árvore local estava limpa. O redesenho reutiliza o planejador, seus intervalos e editores.

A marca Mist Teal utiliza uma curva aberta e três barras separadas. Os recursos adaptativo, monocromático e PNGs por densidade foram atualizados. A verificação compara vetor e fonte SVG, cores e pontos de controle na área segura circular; uma prévia de 48 px foi inspecionada. O script de contraste aprovou 16 pares de texto/fundo com mínimo 4,5:1.

O nome de exibição é opcional, local e limitado a 40 pontos de código. Espaços externos são removidos; controles de linha são rejeitados. A preferência não integra o backup. Room, applicationId, namespace, assinatura e o formato de backup não foram alterados.

A tela inicial apresenta resultado previsto, receitas, despesas, distribuição por categoria e vencimentos do ciclo. Resultado do mês é a diferença entre receitas e despesas previstas, e não saldo bancário. Não há curva ou crescimento simulado.

Core, builds e lint são executados localmente; os resultados finais acompanham o PR. A instrumentação e as capturas novas não são executadas no emulador local API 26. Foram preparados testes para API 35 com nome vazio, acentos, edição, remoção, persistência, limite, temas e capturas com dados fictícios. Os testes existentes de movimentações, planejamento e ocultação de valores continuam na suíte da API 35.

A conferência manual de TalkBack, fonte ampliada, teclado e aparelho físico não foi realizada. As capturas dos temas claro/escuro ficam nos artefatos screenshots do CI quando a execução da API 35 terminar. Não se declara aprovação antecipada desses testes.

A renomeação do repositório está documentada separadamente e aguarda autorização posterior à revisão. Não foi realizada renomeação, merge ou publicação do aplicativo.

Resultados locais: 203 testes do core, zero falhas/erros/ignorados; assembleDebug, assemblePreview, assembleDebugAndroidTest e lintDebug concluídos em 2 min 09 s. Instrumentação API 35 e capturas pendentes do CI.

Lint debug: zero erros e nove avisos.
