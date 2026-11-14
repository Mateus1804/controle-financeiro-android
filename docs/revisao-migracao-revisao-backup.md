# Migração dos testes de revisão, proteção e backup

Esta alteração usa a abertura normal de `MainActivity`, que entrega `ForecastApp`. Os testes não enviam extras para selecionar uma interface alternativa. A preparação compartilhada limpa somente o cadastro fictício do emulador dedicado, inicializa o armazenamento atual de previsões, dispensa os avisos iniciais e espera a interface atual ficar disponível.

Não há alteração em produção, banco, migrações, backup, criptografia, regras monetárias ou permissões. Permanecem **12 testes**: nove em `OptionalFeaturesUiTest`, um em `BackupDocumentFlowTest`, um em `IntegratedFinanceUiTest` e um em `ReviewBackupAccountsComponentTest`, no mesmo arquivo do teste integrado. Nenhum teste foi ignorado.

| Cobertura anterior | Cobertura na interface atual |
| --- | --- |
| Criar limite mensal e recuperar após recriação | Criar previsão mensal no `ForecastEditor`, conferir centavos e frequência no armazenamento real, recriar a Activity, reabrir a previsão e conferir os campos; capturas clara e escura. |
| Confirmar previsão recorrente, data efetiva e prevenção de repetição | Conferir que a previsão atual não cria lançamento; registrar despesa explicitamente pelo atalho real; conferir data efetiva, preservação da previsão e ausência de repetição após recriar a Activity. |
| Aviso de possível duplicidade | Abrir uma pendência pela caixa real, manter o registro manual e confirmar um segundo lançamento apenas por ação explícita. |
| Nova chegada durante revisão e sequência | Inserir uma nova pendência enquanto valor e descrição estão editados; conferir preservação dos campos, salvar os centavos editados e conferir a identidade da próxima pendência da fila inicial. A nova chegada só é aberta depois dessa fila; sua abertura não a confirma. |
| Atualização da mesma notificação | Conferir botão de confirmação bloqueado até reconhecer a nova revisão, preservação dos campos e persistência do valor e da descrição editados. |
| Bloqueio e link de notificação | Conferir ocultação da interface atual, `FLAG_SECURE`, recriação e link para uma pendência fictícia existente enquanto bloqueado. |
| Ciclo de vida da senha e cancelamento | Conferir senha curta recusada, ambos os campos vazios após recriação e registro local preservado depois de cancelar. |
| Proteção do calendário nativo | Abrir a despesa real e seu calendário; conferir `FLAG_SECURE` nas janelas nativas. O ramo compatível com APIs anteriores foi conservado, sem ampliar a matriz executada. |
| Valores ocultos, fonte e orientação | Exercitar o planejador atual com fonte em 150%, temas claro e escuro, sem corte/reticências na navegação; conferir ocultação visual e semântica, entrada do editor em paisagem e proteção do campo de valor. Fonte e orientação anteriores são restauradas. |
| Backup pelo seletor de documentos | Manter o seletor real, senha, criação do arquivo criptografado, abertura e prévia; conferir um lançamento e uma previsão no retorno. Um registro local permanece intacto até marcar a confirmação da substituição e é então substituído pelo backup. |
| Projeção e referência mensal de renda | Usar previsões do armazenamento atual para conferir renda, despesas e margem do ciclo e o valor apresentado em Projeções; conservar a referência mensal no resumo atual sem convertê-la em previsão nem lançamento. |
| Saldo inicial zero e acesso ao extrato da conta | Testar diretamente o componente compartilhado `AccountsManager` com `createComposeRule`, conectado ao repositório real. Recusar campo vazio, persistir zero e sua referência sem gerar receita/despesa e conferir a conta entregue pela ação de extrato. |

## Diferenças de produto registradas

O planejador atual não oferece a antiga tela de limites por categoria nem o comando de converter uma previsão em pagamento. Uma previsão mensal não é um limite de categoria: a primeira migração conserva os contratos de edição, centavos, persistência e recriação, sem alegar que esses produtos são equivalentes. A segunda conserva a separação entre planejamento e lançamento, a data efetiva e a ausência de gravação repetida. Os testes de domínio e repositório existentes continuam responsáveis pelos cadastros históricos de limites e recorrências.

`ForecastApp` não repassa ao editor os antigos textos de progresso e aviso de nova chegada. O teste observa a identidade das pendências, a edição e a ordem efetiva: primeiro os itens da fila inicial, depois as pendências ainda disponíveis. Não passa a exigir textos removidos do produto.

O resumo mensal e a projeção do ciclo têm bases diferentes no produto atual: a referência manual pode aparecer no resultado do mês, enquanto a projeção usa as previsões cadastradas. O teste integrado verifica essa diferença e a ausência de lançamentos fictícios; não impõe a igualdade antiga entre dois cartões que já não existem na mesma forma.

O gestor de contas ainda não tem uma entrada na interface principal. O teste focado confere o componente e seu callback de extrato; **não certifica uma jornada de navegação da tela principal até um extrato filtrado por conta**. Essa integração pertence ao item de produto correspondente. Nenhuma raiz alternativa foi recriada para esconder a ausência desse acesso.

## Verificação desta alteração

A preparação desta migração realizou somente inspeção estática e `git diff --check`. Compilação e instrumentação não foram executadas nesta tarefa; a validação serial do responsável deve executar os 12 testes na API 35, além das verificações exigidas pelo repositório. Capturas e notificações usam dados fictícios; esta migração não constitui validação de notificações bancárias reais ou avaliação humana de TalkBack.
