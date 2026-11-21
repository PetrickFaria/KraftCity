# KraftCity 1.1.12

Base validada `v1.1.11`, commit `a2d4ae7eab008161f183c41b77ce2cf1100b6650`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Baú Arcano (Fim)

O repositório Baús Arcanos ainda não tem commits. A análise usou o JAR original 1.0.5 fornecido pelo autor. O mod é `bausarcanos`, o bloco é `bausarcanos:bau_ender` e o tipo de entidade de bloco é `bausarcanos:bau_arcano`. As classes são `ArcaneBlock` e `ArcaneChest`. O `IItemHandler` do Fim retorna zero slots e recusa inserções. O menu normal bloqueia a abertura sob bloco condutor e obtém o estoque por `EnderStorage.get(server).inventory(uuid, canal)`. O inventário persistente tem 81 posições por combinação de jogador e canal, com cinco canais. Dois terminais no mesmo canal apresentam o mesmo estoque para o mesmo jogador.

O KraftCity usa os membros públicos da versão 1.0.5 por meio de integração opcional. O clique em **Vincular Baú** registra o UUID do jogador real e o canal configurado no bloco. A seleção mantém o alcance de cinco blocos além da área, as verificações de proteção e a identidade do bloco. O descarte insere no estoque original somente enquanto o proprietário está conectado. A origem é debitada pela quantidade efetivamente aceita. Se o canal mudar ou o bloco for substituído, o vínculo é invalidado. O arquivo original do Baús Arcanos permanece intacto e fora do JAR do KraftCity. O SHA-256 do JAR 1.0.5 fornecido é `4f1853c4eeb76964e5ee9e80d951674b585963d3fd200f5712076f53f22ee902`.

## Descarte, interface e agricultura

Baús vanilla simples e duplos e Baús do Ender usam um evento de tampa separado da contagem de jogadores. Nas duas metades de um baú duplo o efeito termina junto. No Baús Arcanos, os métodos públicos `open` e `close` contabilizam o uso temporário do trabalhador junto dos usos reais dos jogadores. Transferências próximas estendem o prazo da animação. Recipientes sem contrato conhecido de tampa mantêm a transferência normal.

A interface do Fazendeiro tem três slots alinhados à esquerda, com o botão de vínculo à direita. O antigo slot Insumo continua serializado para ler mundos antigos. A migração move sua pilha ao estoque quando toda a pilha cabe e preserva o slot original enquanto o estoque estiver cheio. A confirmação de desvinculação continua disponível.

Caminhos de terra vanilla são ignorados na escolha de posições agrícolas. A navegação e a ordem do zigue-zague continuam disponíveis. A proteção da colheita seletiva, o veto do Forge e o tratamento de culturas especiais permanecem no fluxo anterior.

O preenchimento da barra de experiência existente do Fazendeiro é ciano. Onde os demais menus mostram XP em texto, o valor foi preservado. Os indicadores de operação, combustível, tinta e carga conservam suas cores.

## Retorno e apresentação do trabalhador

Para o retorno longo, o aldeão consulta a âncora vinculada na dimensão atual. O KraftCity amplia o alcance e o orçamento de busca do navegador para essa consulta, usando apenas os chunks disponíveis. Ao entrar na área residencial, a rotina solta o caminho de retorno antes de delegar a aproximação final e o sono ao sistema de camas. O descarte mantém a liberação da navegação existente.

A fuga profissional do Fazendeiro usa multiplicador 0,95 em vez de 1,25. A caminhada profissional permanece em 0,8. O valor mantém a fuga acima da caminhada de trabalho e aproxima a velocidade da referência vanilla de pânico, 0,75.

Durante o expediente, o servidor sinaliza aos clientes que acompanham o trabalhador quando a pose profissional se aplica. Os braços têm balanço oposto durante o deslocamento. No Fazendeiro, a inclinação do tronco e o movimento do braço direito acompanham o balanço disparado pela ação agrícola confirmada. O modelo Steve usa seus braços e mangas próprios, e o modelo de aldeão usa braços separados e a textura aplicada ao morador. O construtor conserva a pose da sua tarefa de construção. O sono tem precedência sobre a postura de trabalho.

## Validação com o JAR fornecido

Em uma execução local com o Baús Arcanos instalado, o menu do Fazendeiro aceitou o vínculo. O descarte abriu a tampa e inseriu quatro itens uma única vez. O outro terminal do mesmo canal exibiu a pilha, e o estoque de outro UUID continuou vazio. Após o fechamento da tampa, a desvinculação e uma nova vinculação, outra JVM reabriu o mundo e verificou a associação e a pilha original. A execução padrão do GitHub não redistribui o JAR do Baús Arcanos.
