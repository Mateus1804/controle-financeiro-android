# Planejamento

## Etapa 1 — Base de registro e revisão

A base inclui cadastro manual, histórico com filtros, revisão de notificações, seleção de aplicativos, consentimento, totais mensais e exportação CSV. A evolução da interface acrescentou centavos automáticos, contas reutilizáveis, relatórios de despesas, apresentação inicial e seleção de tema. A validação executada está registrada em `qualidade.md`.

## Etapa 2 — Validação em aparelho

A validação prevista abrange Inter, PicPay, Mercado Pago, PagBank/PagSeguro e Nubank. O registro de cada caso deve identificar o aplicativo, sua versão, a versão do Android, o texto anonimizado, os campos extraídos e o resultado da revisão. Outros bancos poderão ser selecionados e entrar no mesmo processo.

Critérios de verificação:

- Receitas, compras, Pix enviados e recebidos e mensagens sem transação.
- Uso com a tela do aplicativo aberta, em segundo plano e após reinício.
- Permissões concedidas e revogadas e notificações com conteúdo oculto.
- Atualização e reutilização de identificadores pelo aplicativo bancário.
- Duas compras legítimas do mesmo valor e uma compra anunciada por duas origens.

Os testes não devem produzir notificações que simulem a identidade de bancos nem registrar credenciais. Nos testes automatizados, os exemplos sintéticos são identificados como fictícios.

A estrutura reproduzível, os 25 casos sintéticos e a situação de cada origem ficam em [Validação bancária](validacao-bancaria.md). A coleta real continua pendente; os casos sintéticos não são apresentados como validação dos bancos.

## Etapa 3 — Uso pessoal contínuo

O aplicativo já inclui backup criptografado, restauração conferida em base temporária, candidatos a duplicidade e revisão em sequência. A recuperação com dados reais e a proteção opcional no aparelho ainda exigem conferência manual própria, além dos testes automatizados.

Contas com saldo inicial e conciliação permanecem para uma etapa própria. Faturas e parcelas exigem uma regra única para evitar que a compra e o pagamento da fatura sejam contados como duas despesas.

## Etapa 4 — Planejamento financeiro

Limites mensais por categoria, previsões recorrentes e lembretes opcionais estão implementados. Cada vencimento exige confirmação para gerar um lançamento. Metas, intervalos diferentes do mensal e regras mais amplas de projeção permanecem para avaliação posterior.

## Etapa 5 — Distribuição

A distribuição depende da preparação de assinatura e versionamento, dos testes de atualização sem perda de dados, da revisão de acessibilidade e desempenho e do preenchimento das informações exigidas pela loja. Bancos e aparelhos só poderão ser apresentados como validados após o registro de evidências.

Sincronização em nuvem, classificação remota, OCR, investimentos, múltiplas moedas e integração oficial com Open Finance continuam fora desta etapa.
