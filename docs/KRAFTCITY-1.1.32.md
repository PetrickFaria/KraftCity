# KraftCity 1.1.32 — Lenhador

Minecraft Java 1.19.2, Forge 43.5.0 e Java 17. Base da Etapa 1 em `60b4b8bbdd1a65da920252f37dd0d980405ab4a6`.

O Bloco Industrial oferece o contrato de Lenhador por 10 esmeraldas. O trabalhador usa o vínculo residencial, o estoque de 27 espaços, a progressão profissional, o descarte, a demissão e as rotinas de segurança existentes. Seu posto tem um espaço de machado e um de mudas. A segunda página dá acesso ao vínculo do baú, à indicação da área e à demissão.

Cada nível desbloqueia mais cinco blocos de largura, até 25. A seleção manual permanece independente do nível. A projeção começa em `-floor(largura/2)` e tem largura exata. A área vertical vai de dois blocos abaixo do posto a dois acima, inclusive. A marcação gráfica usa a mesma geometria do servidor.

A IA reconhece um tronco enraizado com continuidade e folhas naturais. Ela verifica a vizinhança, limita a estrutura a 160 troncos, 32 blocos de altura e quatro blocos horizontais a partir da base. Troncos colocados por eventos de construção são registrados para proteção. A identificação conservadora pode recusar árvores encostadas em estruturas, árvores com troncos interligados ou geometrias incomuns.

O Lenhador se aproxima no chão e usa a base como referência para o alcance horizontal. Os golpes mais altos exigem linha livre até um tronco já identificado na árvore. Cada golpe remove um tronco, em ordem crescente de altura. Os tempos por bloco são 300, 250, 200, 150, 100, 50 e 40 ticks para madeira, pedra, ferro, ouro, esmeralda, diamante e netherita. O corte usa os itens da tabela de saque, o inventário compartilhado e a durabilidade do machado. Com o estoque cheio, o tronco fica no mundo. A lista dos troncos restantes persiste no posto. Um golpe interrompido volta a exigir seu tempo integral.

As folhas permanecem sujeitas à queda natural. O trabalhador recolhe recursos elegíveis do chão quando a coleta pode ser feita com segurança. Após verificar as árvores acessíveis, procura um local válido a sete blocos horizontais de obstáculos e dá preferência à espécie recém-cortada. O solo, a luz e o espaço da espécie precisam permitir o plantio. O carvalho escuro exige quatro mudas em um quadrado 2×2. Estoque e espaço insuficientes adiam o plantio.
