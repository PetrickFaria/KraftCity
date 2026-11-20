# KraftCity 1.1.09

Base 1.1.08, commit `fd3c01f5deffc8eae800f285b09d1e1ab72f9b12`, branch `feature/kraftcity-builder`, Actions 44767083301. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Colheita e canteiros

A investigação separa as mutações de colheita, plantio e preparo do solo das alterações indiretas de suporte, pisoteamento e notificação de vizinhos. A correção da 1.1.08 que apresenta o replantio como proposta durante o evento Forge permanece ativa. A regressão correspondente usa um observador controlado e reproduzia a escrita antecipada na 1.1.07. Essa reprodução anterior tem escopo diferente da destruição relatada após instalar a 1.1.08.

A colheita revalida alvo, suporte e autorização antes da escrita. Na cana, os dois segmentos superiores são os únicos blocos alterados. As duas escritas sincronizam o cliente sem propagar atualizações de forma às colunas laterais. O caule de um fruto volta ao estado maduro. Os produtos são entidades no mundo e a próxima ação de alcance válido pode recolhê-los. A reserva de espaço protege a cultura quando o posto está cheio.

Os testes registram posição do trabalhador e mudanças do alvo, acompanham o produto gerado e comparam os blocos vizinhos até depois da coleta. Os casos de cana comparam todas as alturas e os suportes, incluindo 2/3/2 e 1/2/3/2/1. Um cenário inicial de teste perdeu raízes antes de qualquer colheita porque o suporte de areia podia cair. A versão com suporte firme distingue essa queda do efeito provocado pelo trabalhador. Na 1.1.08, nove testes direcionados passaram com a areia firme, incluindo as duas configurações de cana, seis canteiros densos e a autorização Forge. No checkpoint inicial, ainda faltava reproduzir a perda relatada na 1.1.08. A confirmação veio com o mundo e a lista de mods descritos abaixo.

## Rotina e movimento

O descarte profissional usa somente a transição para o encerramento. O trabalhador coleta itens acessíveis, calcula o excedente sobre a reserva de dez, deposita e libera a navegação. Durante o dia, chega ao trabalho e verifica se há espaço para a colheita.

A coleta observa a caixa da entidade, a distância e a linha de alcance. Quando o centro fica fora da malha de navegação, busca uma posição seca próxima e acompanha o objeto em movimento. Na água, a recuperação usa o controlador de salto e busca uma margem nos dois níveis acima do aldeão.

O zigue-zague inicia no noroeste e alterna oeste–leste e leste–oeste. Terras já aradas e terrenos que precisam de arado são considerados na mesma passagem. O plantio inicial de cana junto à água continua tendo prioridade. A fuga limitada aceita caminhos cujos nós permanecem dentro da área industrial durante o trabalho ou da residência à noite. Em trânsito, o aldeão pode continuar atravessando áreas externas.

## Recuperação do posto e Baú do Ender

Ao remover o posto de Fazendeiro, o sistema encerra a atribuição, remove a identidade antiga, derruba os itens do posto e devolve um Bloco Industrial. O histórico permanece no registro do trabalhador. A recuperação por item vinculado permanece para as outras profissões.

Para autorizar um Baú do Ender vanilla, fique agachado e clique com um Olho do Ender na mão principal. O mesmo gesto do proprietário revoga a autorização. Para mudar de proprietário, revogue a vinculação atual e peça ao novo proprietário para autorizar o baú. O jogo guarda o UUID de autorização no bloco. O descarte só usa a API do inventário do proprietário real enquanto ele está conectado e tem permissão de interação. O Olho do Ender é reutilizável. A integração aceita somente inserções. Recipientes de mods seguem as APIs de automação autorizada do Forge e do KraftCity.

## Diagnóstico do conflito de colheita com o Quark

O arquivo `KraftCity.rar` contém um mundo do Minecraft 1.19.2 salvo com Forge 43.5.0, KraftCity 1.1.08 e Quark 3.4-418. A lista de versões veio de `level.dat → fml → LoadingModList`. O mundo tem quatro postos ativos em `(0, -43, 9)`, `(0, -43, 15)`, `(0, -43, 21)` e `(-1, -43, 33)`. A inspeção trabalhou em cópias.

O KraftCity publicava um `BlockEvent.BreakEvent` para consultar proteções antes da colheita. O ator Forge era um `FakePlayer` com uma cópia da enxada. O módulo `HoeHarvestingModule` do Quark 3.4-418 ouve o mesmo evento, reconhece a enxada e tenta colher a vizinhança. Sua regra de seleção não consulta a maturidade das plantas. O módulo chama `playerDestroy` para gerar itens, depois `destroyBlock` para remover as plantas. A execução acontece dentro da consulta do KraftCity e precede o replantio do alvo, tudo no mesmo tick.

Uma execução com a 1.1.08, o Quark 3.4-418 e o AutoRegLib 1.8.2-55 reproduziu o defeito com a IA completa. No tick 42, sete trigos de idade 1 viraram ar e sete sementes apareceram. A pilha de origem passou por `Quark → HoeHarvestingModule.onBlockBroken → Level.destroyBlock`. O trigo escolhido passou posteriormente de idade 7 para idade 0. O ensaio registrou essas quebras com terra arada hidratada e suporte estável.

A permissão de quebra continua usando o evento cancelável do Forge. Durante a consulta do Fazendeiro, a mão principal do ator sintético fica vazia e é restaurada ao sair do evento. A enxada real permanece no posto e continua determinando os itens gerados, o rendimento e o desgaste. A verificação seletiva testa a integração com um assinante de área e também pode executar o Quark real quando ele está instalado.

### Rastreamento de desenvolvimento

Em um ambiente de desenvolvimento ForgeGradle, execute `bash gradlew -Dkraftcity.harvestTrace=true -Dkraftcity.harvestTrace.post=-1,-43,33 runServer`. O filtro de posto é opcional e a dimensão padrão é `minecraft:overworld`. Para outra dimensão, use `-Dkraftcity.harvestTrace.dimension=<id>`.

Procure `KC_HARVEST_TICK` e `KC_HARVEST_ENTRY` em `run/logs/latest.log`. O diagnóstico registra as escritas de blocos carregados e a entrada de entidades de item no mesmo tick do alvo selecionado. Cada registro traz posição, estado anterior e posterior ou pilha de item, e origem da chamada. O rastreamento fica desativado por padrão e em produção.

### Reprodução no mundo anexado

Foram usadas duas cópias independentes do mundo anexado, uma com a lógica da 1.1.08 e outra com a correção. O ensaio isolou KraftCity, Quark 3.4-418 e AutoRegLib 1.8.2-55. Nas cópias de teste, os metadados de outras dimensões e pacotes foram ajustados para permitir a abertura sem os demais mods. Os blocos do canteiro, o posto, o inventário e o aldeão vieram do save. Uma única cenoura foi amadurecida em `(-2, -42, 32)` para repetir a mesma ação no posto `(-1, -43, 33)`.

Na 1.1.08, sete cenouras vizinhas desapareceram na colheita, com a terra arada intacta. Com a correção, o alvo foi replantado e o total de alterações nos blocos adjacentes monitorados foi zero. O teste dirigido com o Quark oficial também passou para trigo, cana em 2/3/2 e 1/2/3/2/1 e autorização de replantio.

[Código do módulo Hoe Harvesting do Quark 3.4-418](https://github.com/VazkiiMods/Quark/blob/release-1.19.2-3.4-418/src/main/java/vazkii/quark/content/tweaks/module/HoeHarvestingModule.java)
