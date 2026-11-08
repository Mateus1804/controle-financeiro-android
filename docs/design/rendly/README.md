# Ícone Rendly

O arquivo `rendly-icone-curva.svg` é a cópia integral do SVG aprovado e fornecido pelo usuário. SHA-256: `a883a48aab7e09965f13cfcfb4bad7da3950a494de9358b41a56e5b96a88d2e0`. A curva, as três barras, as proporções e as cores `#014030`, `#20CD91` e `#F7FBFC` são originais. Nenhuma imagem foi gerada por IA.

Os quatro caminhos do monograma foram copiados literalmente para os VectorDrawables. O `fundo-squircle` permanece no SVG de origem e no PNG legado padrão; o foreground adaptativo contém somente o monograma. O background adaptativo é uma camada retangular independente com a cor branco-gelo original. A máscara final é aplicada pelo launcher.

O viewport adaptativo mede 108 × 108 dp. O grupo do monograma usa escala uniforme `0.70` e translação `(19.0875, 19.0875)`, centralizando os limites originais `(15.5, 21)..(84.25, 78.75)` em `(54, 54)`. Até os cantos da caixa delimitadora ficam dentro de um círculo de raio `31.4253 dp`, menor que os `33 dp` da área segura circular de 66 dp. O enquadramento central usado nas prévias mede 72 × 72 dp.

API 26+ usa `mipmap-anydpi-v26`; API 33+ adiciona a camada `monochrome`, composta pelos mesmos caminhos com uma única cor opaca para o sistema aplicar o tema. `icon` e `roundIcon` apontam para recursos próprios, preservando nome, pacote e publicação do aplicativo. Os PNGs padrão e redondos de 48, 72, 96, 144 e 192 px oferecem fallback para consumidores de ícones legados. O padrão reproduz o SVG integral; o redondo substitui somente o fundo por um círculo, com o monograma original intacto. O antigo `ic_app.xml` não possuía outros consumidores e foi removido.

Para validar os recursos:

```sh
python -m pip install -r scripts/requirements-rendly.txt
python scripts/verify-rendly.py
```

Para reproduzir os PNGs e as prévias a partir da geometria original:

```sh
python scripts/verify-rendly.py --render
```

O CairoSVG usa a biblioteca Cairo do sistema. A rasterização ocorre com supersampling de 8× e redução Lanczos; os PNGs não contêm datas nem metadados variáveis. A validação sem `--render` precisa apenas de Pillow e svgpathtools. Versões fixadas estão em `scripts/requirements-rendly.txt`.

As [prévias](../../imagens/rendly/launcher-masks.png) mostram o original, máscaras circular, squircle, quadrado arredondado e quadrado, além de exemplos de tema claro e escuro. Cada ícone aparece em 32 px reais e ampliado para 128 px com pixels preservados. As cores dos dois exemplos temáticos são demonstrativas; o launcher escolhe as cores de `monochrome`. O [guia de área segura](../../imagens/rendly/adaptive-safe-area.png) mostra a camada de 108 dp, o enquadramento de 72 dp e o círculo seguro de 66 dp. São prévias determinísticas, não capturas de um dispositivo. A inspeção final no launcher do celular deve confirmar o resultado com as máscaras e o tema do fabricante.
