# Estado da tela principal

A ForecastApp permanece como única entrada. A navegação, os editores e a fila de revisão têm estados separados em ForecastUiStateViewModel. Os cartões mensais ficam em MonthlyDashboardCards; o exportador CSV existente foi apenas extraído, sem alterar seu conteúdo.

O SavedStateHandle guarda seleções, identificadores e quais diálogos estão abertos. Os campos dos formulários continuam nos componentes existentes. Senhas não são guardadas nele. A ordem da fila de revisão passa a sobreviver à recriação da atividade; notificações novas não passam na frente das que já estavam nessa fila.

Uma simulação não adotada fica apenas na memória do ViewModel: sobrevive à rotação e continua sem gravar previsões. O encerramento do processo descarta essa simulação temporária. Essa distinção evita criar um novo formato de persistência financeira.

A revisão aberta por notificação aguarda o fechamento da pergunta inicial de nome, para não sobrepor os dois diálogos.

## Verificação

- Compilação do aplicativo, APK de testes e lint locais; execução e resultado registrados no PR.
- Testes existentes de revisão e simulação ampliados com recriação da atividade, mantendo as verificações de valores e de ausência de gravações antecipadas. Execução instrumental somente no CI Android 15/API 35.
- Não há mudança em Room, backup, cálculo em centavos ou regras financeiras.

## Limites

A morte do processo durante um formulário depende também dos estados internos dos componentes existentes. O teste de recriação da atividade não substitui testes manuais de encerramento pelo Android, TalkBack ou autenticação no aparelho.
