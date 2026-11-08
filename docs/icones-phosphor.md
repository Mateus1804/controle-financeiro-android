# Ícones internos Phosphor

O aplicativo distribui **Phosphor Icons Regular e Fill** oficiais como recursos Android locais. A fonte é o pacote publicado [`@phosphor-icons/core` **2.1.1**](https://www.npmjs.com/package/@phosphor-icons/core/v/2.1.1), mantido no [repositório oficial](https://github.com/phosphor-icons/phosphor-core). A licença MIT integral está em [licenses/phosphor-icons-LICENSE.txt](../licenses/phosphor-icons-LICENSE.txt), com Copyright (c) 2023 Phosphor Icons.

O [inventário JSON](icones-phosphor.json) contém os 68 recursos, os links de cada SVG na versão fixada, os caminhos dentro do pacote e os SHA-256 do SVG e do VectorDrawable. O arquivo `.tgz` oficial foi verificado contra a integridade SHA-512 publicada nos [metadados do npm](https://registry.npmjs.org/@phosphor-icons/core/2.1.1):

```text
https://registry.npmjs.org/@phosphor-icons/core/-/core-2.1.1.tgz
sha512-v4ARvrip4qBCImOE5rmPUylOEK4iiED9ZyKjcvzuezqMaiRASCHKcRIuvvxL/twvLpkfnEODCOJp5dM4eZilxQ==
```

Cada SVG escolhido contém somente elementos `path`, sem strokes, transformações ou máscaras. A conversão preserva literalmente cada `d` como `android:pathData` e o `viewBox="0 0 256 256"` como viewport Android de 256 × 256. `currentColor` vira preto opaco, permitindo que `Icon`/`painterResource` aplique o tint do tema. O tamanho intrínseco é 24 dp. Nenhuma geometria foi redesenhada e nenhuma dependência de execução foi adicionada. Esses VectorDrawable usam somente recursos compatíveis com a API mínima 26.

Os arquivos `ic_*.xml` normais são Regular; cada um tem a variante `ic_*_fill.xml` correspondente. `PhosphorIcons.kt` centraliza `selectedIcon(icon, selected)`: recebe o recurso Regular e seleciona Fill quando `selected` é verdadeiro. Ícones desconhecidos são preservados. O componente que desenha o ícone continua definindo tint, tamanho e descrição acessível.

| Recurso Regular | Nome oficial Phosphor | Uso |
| --- | --- | --- |
| `ic_overview` | `squares-four` | Resumo |
| `ic_pending` | `clock-countdown` | Pendências e revisão |
| `ic_records` | `receipt` | Extrato e lançamentos |
| `ic_settings` | `gear-six` | Ajustes |
| `ic_add` | `plus` | Adicionar |
| `ic_income` | `arrow-down` | Receita |
| `ic_expense` | `arrow-up` | Despesa |
| `ic_transfer` | `arrows-left-right` | Transferência |
| `ic_refund` | `arrow-u-up-left` | Estorno |
| `ic_chevron_left` | `caret-left` | Voltar e mês anterior |
| `ic_chevron_right` | `caret-right` | Avançar e mês seguinte |
| `ic_chevron_down` | `caret-down` | Expandir e selecionar |
| `ic_wallet` | `wallet` | Carteira e saldo |
| `ic_calendar` | `calendar-blank` | Data e planejamento |
| `ic_food` | `fork-knife` | Alimentação |
| `ic_home` | `house` | Moradia |
| `ic_transport` | `bus` | Transporte |
| `ic_health` | `first-aid` | Saúde |
| `ic_education` | `graduation-cap` | Educação |
| `ic_leisure` | `ticket` | Lazer |
| `ic_category` | `shapes` | Outras categorias |
| `ic_bank` | `bank` | Contas e bancos |
| `ic_card` | `credit-card` | Meio de pagamento |
| `ic_lock` | `lock-key` | Proteção e senha |
| `ic_backup` | `archive` | Backup |
| `ic_restore` | `clock-counter-clockwise` | Restaurar |
| `ic_visibility` | `eye` | Mostrar valores |
| `ic_visibility_off` | `eye-slash` | Ocultar valores |
| `ic_close` | `x` | Fechar |
| `ic_search` | `magnifying-glass` | Buscar |
| `ic_check` | `check` | Confirmar e seleção |
| `ic_notification` | `bell` | Notificações |
| `ic_more` | `dots-three-vertical` | Menu contextual |
| `ic_filter` | `funnel-simple` | Filtros |

A licença, o aviso e uma cópia integral do inventário também são empacotados em `app/src/main/assets/licenses/phosphor-icons-*`. Os recursos de launcher e da marca Rendly têm procedência própria e não fazem parte deste conjunto. Os antigos vetores Material foram substituídos, mas as licenças e a [procedência históricas](icones-material.json) continuam preservadas para as capturas anteriores em `docs/imagens/`. As dependências AndroidX/Material 3 conservam suas próprias licenças.
