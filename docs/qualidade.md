# Minha validação técnica

## Escopo

Organizo a verificação em regras Kotlin, persistência Android, fluxos de interface e compatibilidade bancária real. Uso dados fictícios nos testes e nas imagens.

## Verificações executadas em 24/10/2026

- Executei `:core:test`: 31 testes aprovados, sem falhas, erros ou testes pulados.
- Executei `:app:lintDebug`: sem erros. Identifiquei um aviso sobre uma versão mais recente de `activity-compose`; mantenho o conjunto de dependências fixado e compilado nesta revisão.
- Executei `:app:assembleDebug` e `:app:assembleDebugAndroidTest`: ambos os APKs foram gerados.
- Conferi o SHA-256 do Gradle Wrapper e as permissões do manifesto mesclado. O aplicativo pede somente alertas e a permissão interna de receptor não exportado do AndroidX; o serviço de captura é protegido pela permissão do sistema.

Preparei testes instrumentados de consentimento, persistência, confirmação simultânea, revisão desatualizada e interface. A execução em emulador está em andamento; ainda não registro esses testes como aprovados.

## Limitações que mantenho explícitas

- Ainda preciso executar o teste no aparelho com Inter, PicPay, Mercado Pago, PagBank/PagSeguro e Nubank. Os textos dos testes são sintéticos.
- A captura não fornece extrato histórico nem garante que todos os bancos publiquem uma notificação legível de cada movimentação.
- A identificação de um evento usa origem, chave e horário disponíveis. A reutilização desses campos precisa de avaliação por aplicativo.
- Ainda não faço conciliação automática entre notificações de duas origens.
- Ainda preciso validar um APK assinado de produção, atualização sem perda de dados, backup restaurável, leitor de tela e escala de fonte ampliada.
- A análise das políticas e a implementação de consentimento não representam aprovação de publicação na Google Play.
