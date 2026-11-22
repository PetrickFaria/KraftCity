# KraftCity 1.1.24

Minecraft Java 1.19.2, Forge 43.5.0 e Java 17. Base 1.1.23, commit `cb1cf2d5a4c4e81b193f9bd1eba03b3d94d82c18`, na branch `feature/kraftcity-builder`.

## Defesa territorial em Guarnecer

A seleção de alvos só usava a região militar inteira no modo Patrulhar. Em Guarnecer, a seleção examinava uma caixa menor centrada no Soldado e descartava ameaças ocultas antes de iniciar o combate. A busca ocorria na rotina militar do servidor, antes do estado de guarda. A prioridade de retorno ao posto entrava em ação porque a busca local devolvia uma lista mais restrita.

Patrulhar e Guarnecer agora compartilham a seleção territorial na caixa de 101 × 31 × 101 blocos, consultando as entidades carregadas. A ameaça pode ser identificada a até dez blocos de um residente ou trabalhador, ou cinco blocos além das áreas protegidas. As mesmas regras de prioridade, lista de inimigos, fogo amigo e defesa pessoal são aplicadas. O modo Seguir conserva sua busca local.

A perda de visão aciona a mesma busca pela última posição conhecida, com duração padrão de 100 ticks. O Guarda pode usar sua rotina existente de saída do posto durante a aproximação sem visão. Obstáculos continuam restringindo a trajetória e o ataque. Depois de perder o alvo ou resolver a ameaça, a rotina de guarda volta a centralizar o Soldado sobre o bloco e orientá-lo para a face frontal.

## Armaduras inferiores do aldeão

O código anterior reconstruía o movimento das pernas ao ler a hierarquia depois de desenhar o aldeão. No Fresh Animations, as pernas visíveis usam uma cadeia de partes aninhadas com animações próprias. Uma leitura posterior depende de o renderizador ainda manter os mesmos valores e reproduzir todas as transformações aplicadas no desenho.

A renderização agora registra, para cada perna, as matrizes de posição e normais entregues à geometria visível naquele quadro. Isso preserva pivôs, rotações, escalas e transformações de renderização executadas pelo modelo. As peças são desenhadas separadamente com essas matrizes, usando o mesmo alinhamento e ajuste vertical local da 1.1.23. A leitura da hierarquia serve como alternativa para renderizadores que não passam pela compilação normal do cubo. A captura dura somente a renderização do Soldado atual. A cintura, o peitoral, o capacete e o modelo Steve continuam com suas rotinas anteriores.

## Validação

A comparação em servidor inclui ameaças territoriais carregadas e distantes, uma parede sólida e um cenário de defesa residencial em que um patrulheiro e um guarda abandonam suas posições para alcançar uma linha de tiro livre ao redor de uma cerca. O teste exige que o guarda retorne ao centro do bloco e encare sua frente quando o alvo desaparece. A busca temporária continua coberta pela suíte militar.

A inspeção complementar usa o renderizador vanilla do aldeão, além de Fresh Animations 1.8 com Entity Model Features 2.0.2 e Entity Texture Features 6.0.1. OptiFine não foi incluído nessa combinação. EMF, ETF e o pacote de recursos são opcionais.
