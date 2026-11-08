# Componentes de terceiros

A implementação deste projeto é distribuída sob a licença MIT registrada em `LICENSE`. As licenças dos componentes de terceiros são preservadas separadamente.

## Gradle Wrapper

Os arquivos `gradlew`, `gradlew.bat` e `gradle/wrapper/gradle-wrapper.jar` pertencem à distribuição oficial [Gradle 8.13](https://github.com/gradle/gradle/tree/v8.13.0). São preservados os avisos dos scripts, a licença em `licenses/gradle-APACHE-2.0.txt` e o aviso original em `licenses/gradle-NOTICE.txt`.

## Dependências

Kotlin, Coroutines, AndroidX/Compose e Room são utilizados por meio de dependências declaradas no Gradle. Suas licenças permanecem aplicáveis. A documentação registra os projetos consultados como referência de produto e de uso de APIs.

## Phosphor Icons

Os ícones internos utilizam os SVGs oficiais **Phosphor Icons Regular e Fill**, do pacote [`@phosphor-icons/core` 2.1.1](https://www.npmjs.com/package/@phosphor-icons/core/v/2.1.1), sob a licença MIT, Copyright (c) 2023 Phosphor Icons. Os caminhos foram copiados sem redesenho para VectorDrawable, preservando o viewport de 256 × 256. Regular representa os estados normais; Fill representa os selecionados.

A licença integral e o aviso estão em `licenses/phosphor-icons-LICENSE.txt` e `licenses/phosphor-icons-NOTICE.txt`. A [documentação dos ícones](docs/icones-phosphor.md) e o [inventário](docs/icones-phosphor.json) registram a versão, o arquivo oficial de origem e o SHA-256 de cada recurso. A licença, o aviso e a procedência também integram os APKs em `assets/licenses/`. O símbolo Rendly do aplicativo tem origem independente desses ícones.

## Material Icons Round — capturas históricas

As imagens anteriores em `docs/imagens/` contêm renderizações dos antigos ícones Google Material Icons Round, sob Apache-2.0. A interface atual utiliza Phosphor. A licença do conjunto histórico é preservada em `licenses/material-icons-LICENSE.txt` e `assets/licenses/material-icons-LICENSE.txt`; a [procedência histórica](docs/icones-material.json) registra a revisão oficial `10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7` e os arquivos de origem. O aviso em `assets/licenses/material-icons-NOTICE.txt` identifica esse escopo histórico.
