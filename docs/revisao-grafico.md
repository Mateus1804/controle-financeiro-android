# Legenda e fatias pequenas

O percentual da legenda usa um pequeno fundo da mesma cor da fatia. O texto é preto ou branco conforme a luminância, mantendo contraste mínimo de 4,5:1 nas cores dos dois temas. Não depende apenas de cor: os nomes e percentuais continuam escritos.

O desenho reserva pelo menos 4 graus por fatia antes do vão de 1,6 grau. A diferença é retirada proporcionalmente do espaço excedente das fatias maiores, mantendo o anel em 360 graus. Quando há ampliação, a tela explica que os percentuais da legenda continuam reais. O ajuste é de geometria no módulo app; os centavos, agrupamentos, total, estornos e percentuais do core não foram alterados. Valores ocultos não revelam a distribuição nem os percentuais.

## Verificação

- O teste da geometria reproduziu o espaço insuficiente para fatias pequenas antes do ajuste: dois testes executados, uma falha esperada.
- Depois do ajuste, os dois testes puros Kotlin/JUnit passaram em JVM, sem APIs Android. Também integram a suíte instrumental da API 35.
- App, APK de testes e lint locais aprovados. Os testes visuais acrescentados verificam percentual inferior a 0,01%, aviso de ampliação e contraste das cores em ambos os temas; execução instrumental somente Android 15/API 35, resultado no PR.
- Room, backup e regras financeiras preservados. O teste de contraste não substitui a inspeção manual com leitor de tela ou em diferentes aparelhos.
