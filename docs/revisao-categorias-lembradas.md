# Categorias lembradas por estabelecimento

Em Ajustes, “Categorias lembradas por estabelecimento” abre a lista local. É possível corrigir a categoria, esquecer uma lembrança ou apagar todas após confirmação. A edição usa as categorias de despesa existentes e informa que só afeta sugestões futuras. Os lançamentos anteriores não são reescritos.

Os nomes da lista são as chaves normalizadas já guardadas pelo aplicativo; o formato anterior não preservava a grafia original com acentos. Não foi criado outro cadastro de estabelecimentos, banco ou formato de backup. A preferência continua local, fora de Room e do backup exportado, com o limite existente de 500 lembranças.

O reconhecimento acrescenta os rótulos explícitos “Estabelecimento:” e “Local:” quando há contexto de compra aprovada ou pagamento. Sem esse contexto, os novos rótulos não são aceitos. As regras anteriores continuam disponíveis. O leitor de valores, movimentos e meios de pagamento não foi alterado.

## Evidências

- O novo teste falhou antes da mudança: seis casos executados, uma falha no reconhecimento do rótulo explícito.
- Após a mudança, os 226 testes JVM do core passaram, sem falhas, erros ou ignorados.
- Compilação do app/APK de testes e lint locais aprovados. Cinco testes dos scripts de procedência passaram.
- O novo teste de interface abre a lista por Ajustes, corrige a categoria, reabre a preferência e a esquece, conferindo que o registro histórico permanece igual. Instrumentação somente Android 15/API 35; resultado no CI do PR.

## Limites

Há 25 fixtures sintéticas e zero amostras reais anonimizadas no registro de evidências. Os novos textos que citam vários emissores são fictícios, não exemplos capturados desses bancos. Não há promessa de compatibilidade com um banco específico. A entrega pelo serviço Android, variações reais de texto e TalkBack precisam de verificação no aparelho. O usuário deve conferir cada sugestão antes de confirmá-la.
