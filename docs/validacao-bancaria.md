# Validação bancária por casos reproduzíveis

## Situação da evidência

O interpretador utiliza regras genéricas em português e BRL. A seleção de uma origem não comprova compatibilidade com todos os seus formatos. Nesta etapa foram preparados **25 casos sintéticos**, cinco para cada origem de referência. **Nenhuma notificação real anonimizada foi fornecida ou validada no aparelho nesta entrega.**

| Origem de referência | Despesa, receita, ambiguidade, autenticação e promoção | Evidência real de versão e aparelho |
| --- | --- | --- |
| Inter | Casos sintéticos reproduzíveis. | Pendente. |
| Nubank | Casos sintéticos reproduzíveis. | Pendente. |
| PicPay | Casos sintéticos reproduzíveis. | Pendente. |
| Mercado Pago | Casos sintéticos reproduzíveis. | Pendente. |
| PagBank/PagSeguro | Casos sintéticos reproduzíveis sob o nome PagBank. | Pendente. |

Os nomes identificam o planejamento da avaliação. Os textos sintéticos não foram copiados de notificações dos bancos nem são apresentados como formatos oficiais. Os testes não criam notificações que se passem por aplicativos bancários.

## Formato dos casos

Os casos ficam em [`core/src/test/resources/bank-notifications.tsv`](../core/src/test/resources/bank-notifications.tsv), separados por tabulação, com as colunas:

`origem`, `banco`, `caso`, `titulo`, `texto`, `centavos`, `movimentacao`, `meio`, `categoria`, `sensivel`, `evidencia`.

- A procedência é `SYNTHETIC` ou `REAL_ANONYMIZED`.
- O valor esperado é um inteiro em centavos ou `null` quando não há valor inequívoco.
- Movimentação, meio e categoria usam os nomes dos enums do módulo `core`.
- A coluna `sensivel` indica conteúdo que o filtro deve descartar antes de gerar pendência.
- Casos sintéticos usam a referência `synthetic` e título explicitamente fictício.
- Casos reais exigem uma referência aos metadados em [`evidencias-bancarias.json`](evidencias-bancarias.json). A lista está vazia enquanto não houver coleta real revisada.

`BankNotificationFixturesTest` executa cada linha contra o interpretador real. O script `scripts/verify-bank-fixtures.py` confere a procedência e os metadados e participa do CI. O filtro de possíveis dados pessoais é complementar e não substitui revisão humana.

O TSV utiliza uma linha por caso. Mensagens com quebras de linha devem ser preservadas integralmente em um teste Kotlin específico, sem achatar o texto apenas para obter reconhecimento. A inclusão de um novo formato depende da evidência correspondente e de uma expectativa revisada.

## Coleta e anonimização

A avaliação utiliza notificações legítimas do uso normal, sem necessidade de criar operações bancárias apenas para o teste. O registro local deve conter nome e pacote do aplicativo, versão do aplicativo, versão do Android, modelo do aparelho, data de coleta, conexão do serviço, entrega da notificação e resultado da revisão.

Nomes, documentos, telefones, e-mails, números de cartão, contas, chaves Pix, identificadores de transação e outros dados pessoais devem ser substituídos por valores fictícios. A estrutura da mensagem, os separadores do valor e os termos relevantes para a interpretação devem ser preservados. Valores também podem ser substituídos, com atualização dos centavos esperados. Códigos de autenticação nunca devem ser compartilhados, mesmo quando o objetivo for testar seu descarte.

Arquivos em preparação ficam em `casos-locais/`, fora do Git. O [modelo de metadados](modelo-evidencia-bancaria.json) começa com a anonimização não revisada e os testes de entrega pendentes. Campos de modelo não constituem evidências.

```bash
python3 scripts/verify-bank-fixtures.py
python3 scripts/verify-bank-fixtures.py --fixtures casos-locais/notificacoes.tsv --evidence casos-locais/evidencias.json
./gradlew :core:test --tests '*BankNotificationFixturesTest'
```

O primeiro comando confere os casos publicados. O segundo confere uma preparação local, sem publicá-la. O teste Kotlin utiliza o arquivo de recursos versionado; a inclusão de um caso real nesse arquivo depende da revisão da anonimização e do registro dos metadados correspondentes.

## Critérios no aparelho

A interpretação e a entrega são avaliações distintas. Um caso de texto reconhecido não comprova que o Android permitirá a captura em segundo plano. A matriz de avaliação abrange:

- Receitas, despesas, Pix enviados e recebidos, cartões e transferências entre contas próprias.
- Mensagens com valor e saldo, promoções, autenticação, texto oculto ou informações insuficientes.
- Aplicativo aberto, em segundo plano, após reinício e após parada forçada.
- Consentimento, seleção, acesso concedido ou revogado, serviço desconectado e recusa de alertas.
- Atualização de uma mesma notificação e reutilização de identificadores pelo banco.
- Compras legítimas do mesmo valor e uma operação anunciada por duas origens.
- Diferenças de perfis do Android, otimizações de bateria e conteúdo restrito pelo sistema ou fabricante.

O nome da origem permanece separado da conta escolhida. Uma notificação do banco pode corresponder a mais de uma conta do usuário. Situações ambíguas ficam para revisão, e nenhuma indicação de possível duplicidade remove ou une lançamentos automaticamente.
