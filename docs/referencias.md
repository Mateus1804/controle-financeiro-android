# Minhas referências de estudo

Consultei estas fontes em 24/10/2026 para definir a captura, a revisão e a apresentação do projeto.

| Referência | O que estudei |
| --- | --- |
| [Cashew](https://github.com/jameskokoska/Cashew) | Organização de orçamento, categorias e transações em um aplicativo financeiro. |
| [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) | Uso de Kotlin e Compose e organização de um aplicativo Android. O repositório está arquivado. |
| [Peace](https://github.com/ahmedsaed/Peace) | Revisão antes de registrar, tratamento de transferências e captura persistente. Consultei o código de `NotificationCaptureService.kt`, que prioriza `EXTRA_BIG_TEXT` e acompanha notificações do aplicativo de SMS. |
| [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) | Serviço nativo e ciclo de conexão. |
| [Permissão de notificações](https://developer.android.com/develop/ui/compose/notifications/notification-permission) | Autorização para os alertas do próprio aplicativo. |
| [Restrições de atividades em segundo plano](https://developer.android.com/guide/components/activities/secure-bal) | Uso de alerta com ação do usuário para abrir a revisão. |
| [Android 15: conteúdo de autenticação](https://developer.android.com/about/versions/15/behavior-changes-all) | Limitações para notificações com OTP. |
| [Google Play: dados do usuário](https://support.google.com/googleplay/android-developer/answer/10144311) | Explicação e consentimento para tratar dados pessoais e financeiros. |
| [Google Play: visibilidade de aplicativos](https://support.google.com/googleplay/android-developer/answer/10158779) | Escopo da seleção e restrições a `QUERY_ALL_PACKAGES`. |

Uso esses projetos como referências de comportamento e organização. Minha base de regras e interface foi implementada neste repositório; não incorporei código ou recursos visuais de Cashew, Ivy Wallet ou Peace. Não tratei as descrições desses projetos como evidência de que seus formatos funcionam com todos os bancos.

Mantenho o Gradle Wrapper oficial 8.13 com sua licença Apache 2.0 e verifico o SHA-256 do JAR e da distribuição. Registro os componentes de terceiros em `THIRD_PARTY_NOTICES.md`.

## Evolução da interface — 24/10/2026

- [Expense Manager](https://github.com/nkuppan/expensemanager): estudo da organização de contas, histórico e relatórios em Compose; licença Apache-2.0.
- [Cashew](https://github.com/jameskokoska/Cashew): referência de hierarquia visual, temas e relatórios; projeto Flutter sob GPL-3.0, sem incorporação de código.
- [GreenStash](https://github.com/Pool-Of-Tears/GreenStash): referência de simplicidade Material 3; licença MIT.
- [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet): referência histórica de experiência; repositório arquivado e licença GPL-3.0, sem incorporação de código.
- [ComposeCharts](https://github.com/ehsannarmani/ComposeCharts): biblioteca Apache-2.0 avaliada como alternativa; mantenho a rosca em Canvas nesta revisão.
- [Campos e transformações de texto](https://developer.android.com/develop/ui/compose/text/user-input): entrada numérica e apresentação com cursor controlado pelo Compose.
- [Padrões de acessibilidade do Compose](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): semântica e áreas de toque.

Uso as referências para orientar minhas decisões e registro os resultados executados em `qualidade.md`. Não apresento esses projetos como dependências ou como código auditado por mim.
