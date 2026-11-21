# KraftCity 1.1.15

A versão parte da 1.1.14, commit `be2b4db6d7a753014e2f00dff435f2867600b954`, na branch `feature/kraftcity-builder`. Requer Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Ferramentas nas costas

A montagem anterior usava as transformações de empunhadura sobre um deslocamento fixo relativo ao tronco. A rotação deixava a ferramenta de perfil ou encurtada em algumas vistas. Parte do item girava para dentro da roupa, ocultando mais da sua silhueta.

A montagem agora conserva os três fatores de escala da mão direita e aplica uma rotação no plano das costas, com a ponta voltada para baixo. A profundidade usa a superfície externa de cada tronco, incluindo a roupa do aldeão e a camada externa de Steve quando visível. A espessura do item fica do lado de fora dessa superfície. O tridente também usa o próprio centro geométrico, pois tem um modelo tridimensional com pivô diferente dos itens comuns. O ponto de montagem acompanha as rotações do tronco.

O cliente verifica os vértices produzidos pelos renderizadores de mão e costas. O teste compara o comprimento das arestas, a profundidade de montagem e a exclusão do item nas costas enquanto o trabalhador o segura. As capturas incluem itens de materiais e formatos diferentes, os modelos Aldeão e Steve e vistas frontal, lateral e traseira em poses de repouso, caminhada e corrida.

## Fazendeiros durante tempestades

A rotina de tempestade suspendia as ações agrícolas corretamente. Ao atingir um local coberto, porém, apagava o destino e chamava `Navigation.stop()`. A rotina residencial assumia a navegação nesse momento e a atividade vanilla não fornecia outro destino.

O Fazendeiro agora mantém a rota válida até terminá-la e escolhe outro destino acessível quando a navegação fica livre. Cada trecho da rota precisa permanecer dentro da área residencial. Destinos cobertos têm prioridade. Caso estejam inacessíveis, o controlador procura outro destino dentro dos limites. Chuva sem trovões permite que o trabalho prossiga.

Ao anoitecer, o controle segue para a rotina residencial que reserva a cama, valida a aproximação e inicia o sono. Ao amanhecer com trovões, o Fazendeiro volta a circular pela residência. O expediente retoma quando os trovões cessam durante o dia. Fuga, sono, regeneração e recuperação do retorno residencial usam as rotinas já existentes.

## Validação

O novo GameTest acompanha três Fazendeiros com IA ativa em residências separadas. Os três colhem na chuva comum, conservam as lavouras intactas durante a tempestade, caminham sob cobertura depois de chegar à residência, dormem nas respectivas camas, voltam a circular ao amanhecer e retomam a colheita após cessarem os trovões. A suíte completa do GitHub Actions também valida a colheita seletiva, as demais profissões e a reabertura do mesmo mundo em outra JVM.
