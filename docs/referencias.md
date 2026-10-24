# Referências de estudo

Estas fontes foram consultadas em 24/10/2026 para definir a captura, a revisão e a apresentação do projeto.

| Referência | Escopo consultado |
| --- | --- |
| [Cashew](https://github.com/jameskokoska/Cashew) | Organização de orçamento, categorias e transações em um aplicativo financeiro. |
| [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) | Uso de Kotlin e Compose e organização de um aplicativo Android. O repositório está arquivado. |
| [Peace](https://github.com/ahmedsaed/Peace) | Revisão antes de registrar, tratamento de transferências e captura persistente. A consulta incluiu o código de `NotificationCaptureService.kt`, que prioriza `EXTRA_BIG_TEXT` e acompanha notificações do aplicativo de SMS. |
| [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) | Serviço nativo e ciclo de conexão. |
| [Permissão de notificações](https://developer.android.com/develop/ui/compose/notifications/notification-permission) | Autorização para os alertas do próprio aplicativo. |
| [Restrições de atividades em segundo plano](https://developer.android.com/guide/components/activities/secure-bal) | Uso de alerta com ação do usuário para abrir a revisão. |
| [Android 15: conteúdo de autenticação](https://developer.android.com/about/versions/15/behavior-changes-all) | Limitações para notificações com OTP. |
| [Google Play: dados do usuário](https://support.google.com/googleplay/android-developer/answer/10144311) | Explicação e consentimento para tratar dados pessoais e financeiros. |
| [Google Play: visibilidade de aplicativos](https://support.google.com/googleplay/android-developer/answer/10158779) | Escopo da seleção e restrições a `QUERY_ALL_PACKAGES`. |

Esses projetos servem como referências de comportamento e organização. A base de regras e a interface foram implementadas neste repositório, sem incorporação de código ou recursos visuais de Cashew, Ivy Wallet ou Peace. As descrições desses projetos não constituem evidência de que seus formatos funcionam com todos os bancos.

O projeto inclui o Gradle Wrapper oficial 8.13 com sua licença Apache 2.0 e verificação do SHA-256 do JAR e da distribuição. Os componentes de terceiros estão registrados em `THIRD_PARTY_NOTICES.md`.

## Evolução da interface — 24/10/2026

- [Expense Manager](https://github.com/nkuppan/expensemanager): estudo da organização de contas, histórico e relatórios em Compose; licença Apache-2.0.
- [Cashew](https://github.com/jameskokoska/Cashew): referência de hierarquia visual, temas e relatórios; projeto Flutter sob GPL-3.0, sem incorporação de código.
- [GreenStash](https://github.com/Pool-Of-Tears/GreenStash): referência de simplicidade Material 3; licença MIT.
- [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet): referência histórica de experiência; repositório arquivado e licença GPL-3.0, sem incorporação de código.
- [ComposeCharts](https://github.com/ehsannarmani/ComposeCharts): biblioteca Apache-2.0 avaliada como alternativa; esta revisão utiliza a rosca em Canvas.
- [Campos e transformações de texto](https://developer.android.com/develop/ui/compose/text/user-input): entrada numérica e apresentação com cursor controlado pelo Compose.
- [Padrões de acessibilidade do Compose](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): semântica e áreas de toque.

As referências orientam as decisões técnicas, e os resultados executados estão registrados em `qualidade.md`. Esses projetos não são apresentados como dependências nem como código auditado nesta revisão.

## Backup, planejamento e proteção — 24/10/2026

- [Criptografia no Android](https://developer.android.com/privacy-and-security/cryptography): algoritmos da plataforma e compatibilidade de provedores.
- [OWASP: armazenamento criptográfico](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html): criptografia autenticada, geração aleatória e tratamento de chaves.
- [OWASP: derivação de senhas](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html): referência de custo para PBKDF2-HMAC-SHA256. O formato deste projeto utiliza 600 mil iterações e não guarda a senha.
- [Biometria e credencial do aparelho](https://developer.android.com/identity/sign-in/biometric-auth): autenticação local e diferenças entre versões.
- [KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager): alternativa de credencial para versões anteriores e consulta da proteção do aparelho.
- [Proteção de atividades e FLAG_SECURE](https://developer.android.com/security/fraud-prevention/activities): limites da proteção de imagem e capturas.
- [JobInfo.Builder](https://developer.android.com/reference/android/app/job/JobInfo.Builder): trabalho periódico, persistência e condições de execução pelo sistema.

A implementação permanece própria e não acrescenta bibliotecas de produção. A utilização dessas referências não constitui auditoria independente nem aprovação para publicação.
