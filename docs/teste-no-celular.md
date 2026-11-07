# Teste no celular — comprometimento da renda

O APK `controle-financeiro-teste.apk` fica no artefato `apks-compatibilidade` da execução de **Qualidade Android**. Se o arquivo vier em um ZIP, extraia só esse APK. Ao abrir o APK no celular, o nome exibido deve ser **Controle Financeiro (teste)**. Se o Android pedir permissão para instalar a partir do aplicativo que abriu o arquivo, autorize somente essa origem durante a instalação.

O pacote de teste `com.mateuswilliam.financas.teste` tem dados próprios e pode coexistir com a instalação usual. A chave do CI é descartável. Uma compilação futura pode não atualizar o APK de teste anterior; use dados fictícios e preserve o backup de teste caso queira reutilizá-los. Não é necessário desinstalar o aplicativo de uso pessoal.

## Roteiro com valores fictícios

Comece com **Usar manualmente**. Em **Planejamento → Informar renda e limite**, informe renda prevista de **R$ 1.000,00** e limite de **36%** no mês atual. Cadastre também **R$ 1.000,00** e limite de **30%** no mês seguinte. A renda prevista não deve aparecer como receita no extrato.

1. Em **Registrar → Despesa**, ative **Compra parcelada no cartão de crédito**. Salve uma compra em andamento de **R$ 600,00**, em quatro parcelas, com uma já paga e próximo vencimento no mês atual. Devem aparecer as parcelas 2 a 4, de **R$ 150,00** cada, sem lançamento retroativo.
2. Crie outra compra, de **R$ 600,00 em três parcelas**, sem pagamentos anteriores e com primeiro vencimento no mesmo mês. Para testar a revisão, use **Ajustes → Criar notificações fictícias**, revise o exemplo de compra no cartão e ajuste seu total para **R$ 600,00**. As previsões ainda não devem entrar no extrato.
3. Em **Planejamento → Parcelas do mês**, abra **Conferir e registrar parcela** na compra de três parcelas. Confirme com o valor pago de **R$ 220,00**. O extrato deve conter somente esse pagamento.
4. No mês seguinte, use **Editar** para ajustar a segunda parcela dessa mesma compra para **R$ 225,00**. Depois, em **Cancelar parcelas em aberto**, confira que a confirmação se refere às duas restantes. Cancele e confira que o pagamento anterior continua no extrato.

| Momento | Comprometido | Percentual | Livre |
| --- | ---: | ---: | ---: |
| Mês atual, com as duas previsões | R$ 350,00 | 35% | R$ 650,00 |
| Mês atual, após registrar R$ 220,00 | R$ 370,00 | 37% | R$ 630,00 |
| Mês seguinte, após ajustar a parcela | R$ 375,00 | 37,5% | R$ 625,00 |
| Mês seguinte, após cancelar as restantes | R$ 150,00 | 15% | R$ 850,00 |

Em **Ajustes**, crie um **backup criptografado** com senha de teste. Mude a renda do mês seguinte, depois use **Restaurar backup**, confira a prévia e confirme. Os valores da tabela devem voltar, com o pagamento preservado e as previsões canceladas fora do Planejamento.

Por fim, amplie a fonte e confira os botões e rótulos. Se usar TalkBack, confira a descrição da rosca, que indica o percentual e a maior despesa. Ao ocultar os valores no **Resumo**, o Planejamento deve esconder a rosca, os montantes e os percentuais, inclusive nas descrições de acessibilidade.

O teste com notificações fictícias funciona sem conceder acesso às notificações. O [roteiro bancário](validacao-bancaria.md) trata separadamente da entrega real, sujeita ao Android, ao aparelho e aos aplicativos selecionados.
