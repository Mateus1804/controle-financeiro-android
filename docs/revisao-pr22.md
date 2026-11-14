# Revisão do PR 22

Base revisada: main `469d16f94e31a3de00e0e26d673ca85856c066d5`. Proposta: `d741a4a29ceed7972e23a9986a3f79708f48a683`, branch `feat/salario-unificado-painel`.

## Decisão

Encerrar o PR 22 sem merge e preservar sua branch. O pedido permite concluir ou fechar o PR, mas exige confirmação antes de mudar regras financeiras. Não foi copiado código financeiro dessa proposta.

A simulação de merge com `git merge-tree --write-tree origin/main origin/feat/salario-unificado-painel` identifica conflitos na interface. Resolver os marcadores não resolve os problemas de comportamento abaixo.

## Motivos

- `SalaryForecasts.rule` fixa a data do salário no primeiro dia do mês. Isso pode deslocar a renda de quem recebe no dia 5 e mudar a previsão até o próximo salário.
- `SalaryForecasts.forMonth` transforma referências mensais antigas em previsões automaticamente. Hoje essa passagem exige revisão explícita.
- `ForecastCalculator.report` remove as receitas da categoria Salário quando existe a referência gerenciada e acrescenta uma linha sintética. Essa linha não passa pelo mesmo tratamento de exclusões, ignorados e alterações de ocorrência das demais previsões.
- A ocorrência sintética pode ter identificador sem regra persistida correspondente. Nesse caso, a ação de ignorar pode falhar na validação de `ForecastRepository.saveOverride`.

Os testes verdes da branch antiga não comprovam compatibilidade com a main atual nem autorizam essas mudanças. Uma futura unificação de salário precisa definir data real de recebimento, precedência entre fontes e preservação de exceções/histórico antes da implementação.

## Validação e limites

Foram lidos os diffs do cálculo, dos modelos e do repositório, e simulada a combinação das branches sem alterar a main. Não foram executados testes instrumentais nem aplicada migração para esta decisão documental. Nenhum banco, backup ou cálculo em centavos foi modificado. A proposta pode ser retomada pela branch preservada após decisão explícita sobre as regras.
