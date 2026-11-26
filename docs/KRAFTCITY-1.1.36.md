# KraftCity 1.1.36 — Lenhador

Compatível com Minecraft 1.19.2, Forge 43.5.0 e Java 17. A revisão parte da versão 1.1.35 validada no commit `6ac8384`, que inclui o Padeiro.

## Detecção e corte

Em um cliente Forge, a versão 1.1.35 cortou um carvalho isolado. Reproduzi uma falha distinta com dois carvalhos gerados pela configuração vanilla, um antes e outro depois da contratação. Um bloco de pedra tocando a lateral inferior bastou para o filtro rejeitar ambos antes de o trabalhador procurar um caminho. O cenário foi montado em um mundo de teste. O mundo do jogador não foi fornecido nesta tarefa.

`LumberjackTrees.java` admite rocha da tag vanilla de terreno nas laterais da camada inferior. A validação de copa natural, raízes, troncos registrados como colocados, construção adjacente nas outras camadas e autorização de quebra continua ativa. `LumberjackWork.java` busca raízes nas seções de chunks carregados que contêm troncos. Quando um tronco está fora de alcance, a IA usa uma posição acessível nas proximidades. O trabalho preserva os tempos individuais e corta os troncos registrados de baixo para cima, inclusive acima da região quando pertencem à árvore selecionada.

A opção de servidor `-Dkraftcity.lumberjackDebug=true` mostra estado de trabalho, ferramenta, área, quantidade de raízes candidatas, rejeições, deslocamento, início do temporizador e quebra confirmada.

## Áreas

| Nível necessário | Região | Raio horizontal |
| --- | --- | --- |
| 1 | 15×15×15 | 7 |
| 2 | 25×25×15 | 12 |
| 3 | 35×35×15 | 17 |
| 4 | 45×45×15 | 22 |
| 5 | 55×55×15 | 27 |

As 15 camadas são Y−7, o Y do posto e Y+7 inclusive. `IndustrialRules.java` migra a opção antiga pela posição na lista de cinco tamanhos e salva a escolha no posto. A progressão libera o botão correspondente, mantendo a escolha atual. `WorkScreen.java` mostra as novas dimensões. O cálculo de limites compartilhado serve à visualização, à detecção e ao replantio. A distância de sete blocos do replantio continua vigente.

## Verificações

`IndustryGameTests.java` cobre os cinco tamanhos e suas migrações, o centro e as bordas da região, corte acima do teto a partir de uma raiz interna, árvores após crescimento e antes da contratação, redução da área durante o corte e proteção de madeira. Os testes mantêm as verificações anteriores de tempos, ferramentas, inventário e replantio.
