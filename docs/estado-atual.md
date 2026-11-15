O Rendly foi reorganizado em quatro abas: Mês, Gráficos, Fixas e parcelas e Mais. A tela Mês reúne os três totais, lançamentos por dia, filtros e botão +. Salário, planejador, reserva e comprometimento ficam em Mais; contas, captura, backup e aparência ficam em Ajustes.

Fixas, parcelas e salário usam previsões e ajustes mensais, com alteração de uma ocorrência ou das futuras. O vínculo opcional em Mais detalhes evita repetir uma previsão nas projeções. Até o próximo salário considera saldos conhecidos menos compromissos, sem descontar a reserva, e apresenta os gastos variáveis separadamente.

Esta versão começa vazia, conforme autorizado: descarta o banco financeiro anterior e não aceita backups antigos. Mantém a identidade do aplicativo, aparência, nome e ocultação de valores. O formato novo de backup preserva a proteção por senha e a restauração transacional.

Validação aprovada: 171 testes do core, 174 testes instrumentados da suíte e um teste de reset, ambos somente na API 35 e sem ignorados. Build, lint e compilação dos APKs passaram. O [CI do PR #48](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/43674522445) contém os relatórios, APKs de teste e capturas com dados fictícios nos dois temas.

O PR #48 foi integrado com merge commit após todos os checks verdes. O #47 foi fechado sem merge porque suas correções já foram incorporadas, com crédito preservado. As branches foram mantidas. O #36 permanece fechado, sem merge, porque a assinatura está fora do escopo. O mapa de telas registra a redução de 8.184 para 5.992 linhas de produção.

Não foram feitos testes manuais de TalkBack, biometria ou notificações reais em aparelho físico. Permanecem as limitações de reconhecimento dos textos das notificações e a assinatura definitiva fora do escopo. As capturas usam somente dados fictícios.
