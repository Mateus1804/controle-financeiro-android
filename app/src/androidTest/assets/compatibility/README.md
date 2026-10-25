# Vetores de compatibilidade

Os dados são inteiramente fictícios. O JSON conserva o formato de dados 1 e os campos do esquema Room 3 da revisão `7aec1bd102fcfbfb18eed493ceeb9ca0ac89fefe` (versão 0.1.0). O envelope foi gerado independentemente em Python com PBKDF2-HMAC-SHA256 e AES-256-GCM; não foi exportado por um banco nem coletado de um aparelho real.

A senha pública do vetor é `Compatibilidade fictícia 2026!`. Sal e nonce determinísticos servem exclusivamente para este vetor; a implementação de produção continua gerando valores aleatórios por backup.

O teste lê o arquivo congelado sem regenerá-lo. Uma mudança no formato exige preservar este teste ou implementar migração explícita; atualizar a expectativa para fazer o teste passar perde a evidência.

SHA-256 do envelope: `d88e0943755395293e4b9f3c29742db421262e9c77edd129f1358c7e04487220`.

O mesmo JSON alimenta a base SQLite 3 sintética usada no teste de atualização do APK anterior. Identificadores, espaços dos nomes, vínculos, revisão, orçamento, recorrência e preferências possuem expectativas explícitas.
