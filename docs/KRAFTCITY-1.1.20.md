# KraftCity 1.1.20

Base validada na 1.1.19, commit `e37c498d020ad4d09543ce194e4ccf40b33e02f6`, na branch `feature/kraftcity-builder`. Minecraft Java 1.19.2, Forge 43.5.0 e Java 17.

## Interface e armaduras

Os botões de turno formam um grupo centralizado pela largura do painel, com tamanhos iguais e espaço de três unidades de GUI. O restante da organização das páginas segue o menu anterior. A segunda página indica que a vinculação se destina ao baú especial.

O alinhamento do aldeão nativo eleva as calças e botas nos pontos de articulação das pernas. O capacete vanilla usa o movimento da cabeça nativa, com pivô elevado e escala vertical reduzida para encaixar no topo e deixar o rosto visível. A implementação usa a camada do Forge e as poses existentes do KraftCity. Guard Villagers serviu de referência conceitual. As transformações específicas não se aplicam ao modelo Steve.

## Arco, besta e progressão

O arco grava na estação o próximo tick permitido após um disparo confirmado. O intervalo mínimo é de 100 ticks, inclusive em troca de alvo ou após reabertura do mundo. A preparação pode começar nos últimos 20 ticks. A besta conserva seus próprios tempos. Durabilidade e munição são debitadas após o lançamento.

Arco e besta priorizam o alvo autorizado mais próximo, com uma pequena margem para manter a mira quando as distâncias são quase iguais. O recuo considera ameaças locais de ambos os lados, inclusive inimigos desmarcados que representem perigo imediato. A Blacklist continua limitando quem pode ser atacado. O recuo procura rotas dentro da região e rejeita a entrada em áreas protegidas e posições perigosas em fluidos ou com quedas acentuadas.

A regra anterior usava 75.000 unidades internas de XP por nível. Cada mob concedia XP diferente e podia dividir o crédito. Os totais de eliminação exatos nunca foram armazenados e não são inferidos a partir do XP antigo. Os níveis e o XP profissional já obtidos são preservados. O novo registro acumula apenas baixas finais atribuídas ao Soldado com participação válida. O avanço passa a usar **50, 75, 100 e 125** eliminações. A barra acompanha o contador e a vida máxima por nível segue em 14, 18, 22, 26 e 30 HP.

## Limites, coleta e descarte

O contorno vermelho é acompanhado por seis faces externas de baixa opacidade. As faces são desenhadas sem escrever na profundidade da cena. O volume continua em 201 × 31 × 201, com as cores das áreas residenciais e industriais preservadas.

O descarte procura recipientes comuns carregados até cinco blocos em cada eixo a partir do posto, incluindo baús duplos, barris e automação compatível. A escolha usa distância ao posto e coordenadas, verificando espaço, acesso e chegada física. Baús privados identificados pelo sistema, Baú do Ender e Baú Arcano (Fim) permanecem vinculados explicitamente.

Guarnecer procura itens no raio espacial de 25 blocos do posto. Patrulhar procura em todo o volume militar carregado. A navegação de coleta evita volumes residenciais e profissionais e o movimento bloqueia entrada nesses volumes durante a tarefa. Ao ficar cheio ou concluir a coleta, o Soldado procura um descarte acessível e volta à guarda ou à patrulha. A rotina de Seguir mantém a coleta em alcance físico. O combate mantém prioridade em todos os modos.


## Cenários de validação

O Forge testa, em um mesmo mundo, dois Soldados equipados, o alcance de Guarnecer e de Patrulhar, a seleção do zumbi mais próximo, o descarte em barris, a exclusão do Baú do Ender sem vínculo e os itens preservados na residência e na fazenda. Um segundo percurso percorre as distâncias de 10 e 25 blocos, verifica o item além do raio e exige o retorno ao centro do posto. Testes específicos cobrem a troca de alvo e o intervalo de 100 ticks.

O cliente valida o alinhamento da interface em quatro resoluções, os disparos de arco e besta e a persistência da estação, dos equipamentos, do inventário, da contagem de eliminações, do nível e do intervalo do arco em outra JVM.
