# Minha validação técnica

## Escopo

Organizo a verificação em regras Kotlin, persistência Android, fluxos de interface e compatibilidade bancária real. Uso dados fictícios nos testes e nas imagens.

## Verificações executadas em 24/10/2026

- Executei `:core:test`: 31 testes aprovados, sem falhas, erros ou testes pulados.
- Executei `:app:lintDebug`: sem erros. Identifiquei um aviso sobre uma versão mais recente de `activity-compose`; mantenho o conjunto de dependências fixado e compilado nesta revisão.
- Executei `:app:assembleDebug` e `:app:assembleDebugAndroidTest`: ambos os APKs foram gerados.
- Conferi o SHA-256 do Gradle Wrapper e as permissões do manifesto mesclado. O aplicativo pede somente alertas e a permissão interna de receptor não exportado do AndroidX; o serviço de captura é protegido pela permissão do sistema.

Executei **13 testes instrumentados no Android 15 (API 35, x86_64)** pelo GitHub Actions, todos aprovados: 10 de persistência e revisão e 3 de interface. Validei consentimento e seleção de origem, descarte de autenticação, atualização de pendência, compras distintas com valores iguais, confirmação simultânea, limpeza do texto, edição, exclusão dos dados e bloqueio de confirmação de uma mensagem alterada depois da abertura do formulário. Na interface, validei o lançamento manual sem acesso às notificações, a recusa do consentimento e a geração de capturas com dados fictícios.

A execução [40100369119](https://github.com/Mateus1804/controle-financeiro-android/actions/runs/40100369119) concluiu os jobs de compilação e emulador com sucesso sobre o commit `151d05b07fd02ad19241fe52215f5720de843828`. Somando as regras Kotlin e os testes instrumentados, obtive **44 testes aprovados**.

Disponibilizei o APK de depuração, o APK dos testes, os relatórios e as capturas nos artefatos dessa execução, com retenção de 14 dias. A execução local dos testes instrumentados foi interrompida pela indisponibilidade do ambiente; concluí a validação no emulador da integração contínua. Ainda mantenho a inspeção visual manual das capturas como pendente.

## Limitações que mantenho explícitas

- Ainda preciso executar o teste no aparelho com Inter, PicPay, Mercado Pago, PagBank/PagSeguro e Nubank. Os textos dos testes são sintéticos.
- A captura não fornece extrato histórico nem garante que todos os bancos publiquem uma notificação legível de cada movimentação.
- A identificação de um evento usa origem, chave e horário disponíveis. A reutilização desses campos precisa de avaliação por aplicativo.
- Ainda não faço conciliação automática entre notificações de duas origens.
- Ainda preciso conferir a interface de edição e exportação pelo seletor de arquivos no aparelho e validar um APK assinado de produção, atualização sem perda de dados, backup restaurável, leitor de tela e escala de fonte ampliada.
- A análise das políticas e a implementação de consentimento não representam aprovação de publicação na Google Play.
