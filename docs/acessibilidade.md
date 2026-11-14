# Verificação de acessibilidade

A automação instrumental desta revisão utiliza somente Android 15/API 35. Os testes puros do core e da geometria rodam em JVM, sem versão de Android. Não foi realizada uma auditoria manual de TalkBack em aparelho físico.

## Cobertura automática

| Situação | Verificação |
| --- | --- |
| Tela real em 320 dp e fonte 200% | RedesignInteractionTest percorre navegação, revisão, ajustes e cadastro; confere alcance, tamanho de toque e ausência de corte nos rótulos da navegação. Configuração do emulador restaurada no finally. |
| Rótulos para leitura e ação | Ícones de Ajustes e ocultação de valores têm nome e ação; revisão tem texto acessível. Menus de previsões identificam o lançamento e mantêm alvo de 48 dp. |
| Fonte ampliada, teclado e rotação | FinanceUiTest usa o editor real, preserva valores e confere gravação explícita; a revisão e a simulação também passam por recriação da atividade. |
| Gráfico com fonte 200% e nome longo | LargeFontChartUiTest usa largura de 320 dp, valor máximo e agrupamento por conta; confere textos sem corte, ordem vertical e ausência de sobreposição. |
| Valores ocultos | Valores e percentuais não aparecem nos textos ou descrições semânticas do gráfico; o gráfico informa que os dados estão ocultos. |
| Contraste e categoria pequena | Percentual com fundo da mesma cor da fatia e texto contrastante nos dois temas. A ampliação visual é explicada sem alterar os percentuais reais. |

A legenda usa linhas separadas para nome, valor e percentual quando a fonte cresce ou a tela é estreita. O valor da legenda ampliada e o total do gráfico podem ocupar as linhas necessárias, preservando a string monetária completa na leitura acessível; o limite anterior de duas linhas cortava o maior valor válido. O alvo inteiro mantém a ação de abrir as movimentações daquele grupo. A navegação ampliada usa a largura da tela, preservando a fonte definida pelo Android. Nos atalhos fixos de receita/despesa, fonte ampliada usa menos espaço interno e dispensa os ícones decorativos, para manter cada palavra inteira sem reduzir a fonte. A necessidade foi identificada nas capturas reais do CI API 35.

A compilação e o lint locais não significam que testes instrumentais tenham passado. Os resultados efetivos de cada PR ficam nos seus jobs do CI. Os testes automatizados de semântica e geometria não simulam as falas e os gestos reais de um leitor de tela.

## Roteiro manual pendente em Android 15/API 35

1. Ativar TalkBack no aparelho e percorrer marca, saudação, mês, resultado, contas, gráficos, previsões e navegação. Conferir ordem útil, indicação da aba selecionada e ausência de nomes duplicados para ícones decorativos.
2. Ocultar valores e repetir a leitura. Nenhuma quantia ou percentual oculto deve ser pronunciado. Conferir o nome da ação que permite mostrar valores novamente.
3. Abrir cadastro de receita/despesa, contas, edição de previsão e categorias lembradas. Conferir nome dos campos, mensagem de erro, foco ao abrir/fechar diálogos e retorno ao ponto de origem.
4. Usar tamanho de fonte de 200%, maior tamanho de exibição e a menor tela disponível. Abrir o teclado, rolar até todas as ações e girar o aparelho sem perder a edição. Conferir também o seletor de arquivo e o calendário nativos.
5. Revisar duas notificações fictícias, alterar valor/categoria, girar a tela e concluir cada uma explicitamente. Conferir atualização do foco e se uma notificação nova fica depois da fila em revisão.
6. Conferir contraste e diferenciação de categorias sem depender só da cor, em claro e escuro. Registrar modelo do aparelho, API, tamanho de fonte, resultado e eventuais falhas.

Biometria, permissões do fabricante e entrega de notificações bancárias reais também exigem aparelho e dados autorizados. Não foram certificados por esta revisão.

## Referência

[Guia oficial de testes de acessibilidade no Compose](https://developer.android.com/develop/ui/compose/accessibility/testing). A documentação orienta combinar inspeção semântica/checagens automatizadas com uso manual das tecnologias assistivas. Não foram acrescentadas dependências de auditoria apenas para ampliar artificialmente o escopo.
