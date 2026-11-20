# KraftCity 1.1.09

Base 1.1.08, commit `fd3c01f5deffc8eae800f285b09d1e1ab72f9b12`, branch `feature/kraftcity-builder`, Actions 44767083301. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Colheita e canteiros

A investigação separa as mutações de colheita, plantio e preparo do solo das alterações indiretas de suporte, pisoteamento e notificação de vizinhos. A correção da 1.1.08 que apresenta o replantio como proposta durante o evento Forge permanece ativa. A regressão correspondente usa um observador controlado e reproduzia a escrita antecipada na 1.1.07. Essa reprodução anterior tem escopo diferente da destruição relatada após instalar a 1.1.08.

A colheita revalida alvo, suporte e autorização antes da escrita. Na cana, os dois segmentos superiores são os únicos blocos alterados. As duas escritas sincronizam o cliente sem propagar atualizações de forma às colunas laterais. O caule de um fruto volta ao estado maduro. Os produtos são entidades no mundo e a próxima ação de alcance válido pode recolhê-los. A reserva de espaço protege a cultura quando o posto está cheio.

Os testes registram posição do trabalhador e mudanças do alvo, acompanham o produto gerado e comparam os blocos vizinhos até depois da coleta. Os casos de cana comparam todas as alturas e os suportes, incluindo 2/3/2 e 1/2/3/2/1. Um cenário inicial de teste perdeu raízes antes de qualquer colheita porque o suporte de areia podia cair. A versão com suporte firme distingue essa queda do efeito provocado pelo trabalhador. Na 1.1.08, nove testes direcionados passaram com a areia firme, incluindo as duas configurações de cana, seis canteiros densos e a autorização Forge. A perda de plantas relatada na 1.1.08 ainda precisa de uma reprodução independente para confirmar a causa e liberar a publicação.

## Rotina e movimento

O descarte profissional usa somente a transição para o encerramento. O trabalhador coleta itens acessíveis, calcula o excedente sobre a reserva de dez, deposita e libera a navegação. Durante o dia, chega ao trabalho e verifica se há espaço para a colheita.

A coleta observa a caixa da entidade, a distância e a linha de alcance. Quando o centro fica fora da malha de navegação, busca uma posição seca próxima e acompanha o objeto em movimento. Na água, a recuperação usa o controlador de salto e busca uma margem nos dois níveis acima do aldeão.

O zigue-zague inicia no noroeste e alterna oeste–leste e leste–oeste. Terras já aradas e terrenos que precisam de arado são considerados na mesma passagem. O plantio inicial de cana junto à água continua tendo prioridade. A fuga limitada aceita caminhos cujos nós permanecem dentro da área industrial durante o trabalho ou da residência à noite. Em trânsito, o aldeão pode continuar atravessando áreas externas.

## Recuperação do posto e Baú do Ender

Ao remover o posto de Fazendeiro, o sistema encerra a atribuição, remove a identidade antiga, derruba os itens do posto e devolve um Bloco Industrial. O histórico permanece no registro do trabalhador. A recuperação por item vinculado permanece para as outras profissões.

Para autorizar um Baú do Ender vanilla, fique agachado e clique com um Olho do Ender na mão principal. O mesmo gesto do proprietário revoga a autorização. Para mudar de proprietário, revogue a vinculação atual e peça ao novo proprietário para autorizar o baú. O jogo guarda o UUID de autorização no bloco. O descarte só usa a API do inventário do proprietário real enquanto ele está conectado e tem permissão de interação. O Olho do Ender é reutilizável. A integração aceita somente inserções. Recipientes de mods seguem as APIs de automação autorizada do Forge e do KraftCity.
