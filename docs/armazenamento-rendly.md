# Armazenamento novo do Rendly

A troca foi autorizada pelo proprietário para começar com o aplicativo vazio. Na primeira abertura desta versão, o arquivo `financas.db` é removido, sem conversão. O banco passa a ser `rendly-v1.db`, esquema Room 1. O applicationId e a assinatura não mudam; continua sendo a mesma instalação. As preferências de aparência, nome e ocultação são preservadas. Captura e lembretes são pausados quando o banco antigo é descartado.

Fixas, receitas mensais (inclusive salário), parcelas e exceções por mês usam exclusivamente regras e exceções de previsão. Não existem tabelas de recorrências antigas, compras legadas, confirmações de pagamento ou referências mensais de salário. Um salário diferente em um mês é editado na ocorrência da previsão mensal.

O backup novo usa JSON `format=rendly`, versão 1, dentro do envelope criptografado versão 2 com identificador próprio. Mantém AES-GCM, salt e nonce aleatórios e PBKDF2 com 600 mil iterações. Backups anteriores são incompatíveis e não são convertidos. Arquivos externos não são apagados. A importação confere o arquivo em um banco temporário antes de substituir os dados em uma única transação.

Um lançamento pode ser relacionado explicitamente com uma ocorrência em Mais detalhes. A associação permite retirar a mesma previsão dos valores pendentes, sem depender de um controle de pagamento ou de correspondências arriscadas por texto e valor. Sem vínculo, lançamento e previsão permanecem independentes; o aplicativo não presume que são a mesma conta.

A validação do PR #48 passou no CI: 171 testes do core, 174 testes da suíte na API 35 e um teste de reset intencional na API 35, sem ignorados. O conjunto inclui gravação, exportação e restauração do formato novo, preservação dos vínculos, contas e reserva, recusa do formato antigo, senha incorreta e arquivo adulterado. Os resultados e limites estão no [Estado atual](estado-atual.md). TalkBack manual, biometria e notificações reais continuam pendentes em aparelho.
