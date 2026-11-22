# KraftCity 1.1.23

Minecraft Java 1.19.2, Forge 43.5.0 e Java 17. Base publicada 1.1.22 no commit `f94d3548e2249efa44300b3d7e29685c7e839450`, branch `feature/kraftcity-builder`.

## Descanso militar

Durante o dia, o começo dos trovões transferia o Soldado noturno para a rotina residencial geral dos trabalhadores. Essa rotina encerrava o sono diurno e acionava a circulação de abrigo. O turno militar agora determina quando a residência deve conduzir o Soldado à cama, com uma única regra sob tempo limpo, chuva ou tempestade. Durante o sono validado pela residência, a rotina vanilla do aldeão cede o controle ao descanso militar. A residência continua verificando a cama, seu vínculo e sua disponibilidade. O início do expediente libera o estado de repouso. A cura residencial usa os 600 ticks de sono efetivo já previstos.

## Armaduras inferiores

O ajuste anterior acompanhava a altura da geometria animada, mas retinha a rotação do osso pai. Em modelos CEM como Fresh Animations 1.8, a geometria desenhada pode estar em uma perna filha. Cada calça e bota agora é desenhada separadamente com a cadeia final de matrizes da perna correspondente. A ancoragem usa o topo da caixa da perna. O perfil de armadura recebe espaço lateral de 6% para cobrir a superfície da perna e evitar a sobreposição da pele. O Forge continua renderizando as peças, texturas e encantamentos. A cintura, o peitoral, o capacete e o modelo Steve seguem seus respectivos caminhos de renderização.

## Cadência de ataque

| Arma | Cadência | Referência |
| --- | --- | --- |
| Arco | 120 ticks entre flechas efetivas, dos quais 20 podem ser usados na preparação | Fases separadas de carregamento, mira, disparo e recuperação da besta do Pillager. A 1.1.22 usava 100 ticks no arco |
| Besta | 60 ticks por disparo ou salva, com carregamento de 25 ticks para uma besta comum e redução por Carga Rápida | No Minecraft 1.19.2, o objetivo de besta carrega a arma e escolhe uma espera de 20 a 39 ticks antes do disparo |
| Espada | 28 ticks entre tentativas de golpe | O objetivo corpo a corpo vanilla usa 20 ticks. O Soldado tem uma pausa deliberadamente mais longa |

O arco aplica a espera na entrada da flecha no servidor, vinculada à identidade do Soldado. A besta retém a carga e a salva legítima de Multidisparo. A espada registra o tick do dano efetivo e usa o mesmo relógio ao trocar de alvo. Os testes de cadência imprimem os ticks aceitos pelo servidor.

## Ameaças e trajetória

Patrulhar usa a posição de ameaças territoriais dentro dos limites e dos chunks carregados no momento da descoberta. Uma cerca pode exigir um desvio. Quando o Soldado já viu o adversário, registra a última posição visível e a mantém por até 100 ticks após perder visão. O parâmetro `buscaAposPerderAlvoTicks` do arquivo `kraftcity-military-server.toml` controla essa duração. Um contato territorial inicial tem uma janela de aproximação própria e limitada. Um inimigo morto ou fora do território deixa de ser elegível. Quando a visão retorna, a informação é atualizada. A posição invisível de um inimigo conhecido não é seguida ao vivo.

A 1.1.22 acrescentava uma elevação proporcional à distância em toda flecha solta e, com a besta pré-carregada, usava os olhos do inimigo. A mira agora usa pontos da caixa de colisão e calcula a trajetória de menor elevação compatível com a velocidade, a gravidade e o arrasto da flecha. A trajetória candidata é consultada contra blocos, e sua inclinação alimenta a pose e a direção do projétil. A licença de ataque territorial é independente da autorização do disparo através da rota calculada.
